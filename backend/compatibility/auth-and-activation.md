# Authentication and clinic activation compatibility

## Findings and decision

The retained Phase 1 monolith (`pet-platform-service`) uses Spring Security's
default forbidden entry point. Clinic and social explicitly replaced it with
401. Both services now return 403 for unauthenticated protected requests, keeping
all route matchers and authority rules unchanged. Identity introspection still
uses its internal 401 contract; an unavailable Identity still produces 503.

The monolith saves the clinic and activates the account in one transaction.
The split service previously returned 200 after saving the profile/outbox, before
the scheduled worker activated Identity. Immediate login could therefore return
`accountStatus=PENDING_PROFILE`.

Completion now performs:

1. Commit the clinic, services and activation outbox in one local transaction.
2. Call the existing idempotent Identity activation endpoint synchronously.
3. Return the existing 200 response only after Identity acknowledges activation.

Identity's transactional controller commits ACTIVE before its HTTP response.
Login reads the current account from Identity's database, so an immediately
subsequent login observes ACTIVE after a successful completion.

If activation fails or times out, completion returns 503 in the existing
`ApiResponse` error structure and leaves the committed outbox for retry. A retry
while that outbox exists resumes activation and returns the original stored
profile; it does not overwrite it. Once delivery has completed and the outbox is
removed, repeated completion retains the existing duplicate-profile error.
Cleanup failure after acknowledged activation is safe: the worker can deliver
again because Identity activation is idempotent. A failed profile transaction
never activates Identity. This is not a distributed atomic transaction: a failed
HTTP request may leave a saved profile and pending or already applied activation.

Endpoints, request/response DTOs, role rules and frontend are unchanged.

## Reproducible checks

Run from `backend` (add `-Dmaven.repo.local=...` if your cache is elsewhere):

```powershell
.\mvnw.cmd -B -ntp -f pet-platform-service/pom.xml -Dformatter.skip=true '-Dtest=AuthErrorCompatibilityTest,TransactionAndJwtCompatibilityTest' test
.\mvnw.cmd -B -ntp -pl identity-service,clinic-service,social-service -Dformatter.skip=true test
```

`auth-errors.csv` is the shared OLD/MICROSERVICE status matrix: missing, invalid,
expired and wrong-role credentials on clinic completion and social feed. The old
backend test runs real JWT validation and security rules against the retained
monolith. The service tests run their real security chains with mocked Identity
introspection results. Identity's own test independently verifies rejection of
a correctly signed but expired access token and invalid/missing credentials.

The old workflow test calls complete-profile and immediately logs in through MVC,
expecting ACTIVE. Clinic tests verify commit-before-activation,
acknowledgement-before-success, HTTP 503 then successful request retry, durable
worker retries, rollback and duplicate handling. Identity tests call its actual
activation endpoint twice and immediately login through MVC, expecting ACTIVE.
These are compositional service integration tests using H2 and mocked external
dependencies, not a deployed multi-process end-to-end test or a production MySQL
failure-injection run.

Verified on 2026-09-16: 12 targeted monolith tests and all 29 tests across
Identity, clinic and social passed (zero failures/errors/skips). The project
formatter validated the changed microservice Java files; the two changed
monolith test files were formatted with the same project formatter.
