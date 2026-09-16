# Migration compatibility evidence

`source-sha256.tsv` records all 114 original production Java files, with CRLF normalized to LF before SHA-256 hashing. `monolith-pom.xml` and `monolith-application.properties` record the starting dependency and configuration definitions; neither contains credentials.

The platform test resources contain 48 request fixtures, the exact controller route inventory, and 48 response snapshots captured from the original monolith before source movement. The capture run passed 51 tests. Snapshot comparison excludes only the generated top-level `timestamp`; DTO fields and nested timestamps remain part of the comparison. Volatile HTTP transport headers (Date, transfer encoding, connection) are not snapshotted.

The API tests run the real MVC controllers, validation, exception handling, JWT filter, role rules and H2 repositories, with business service responses and Redis calls mocked. They verify interface compatibility, not complete business workflows or provider availability. The tests do not send email, push notifications, AI requests or upload files to a real account.

All database entities, repositories and transactional services remain owned by the platform during Phase 1. In particular, activating a clinic account and saving its clinic/services remain in one local transaction. No Saga, distributed transaction or database copy is introduced.

Cloudinary upload code and configuration move unchanged into the media process. The platform retains the public upload controller and uses a transport adapter. Network outages introduce a new possible failure, represented by the existing generic public 500 response; the upload is not retried automatically.

Local build logs are ignored by Git. Re-run the build commands in the root README to reproduce validation.

## Verified on 2026-09-15

- Root `mvnw clean test` and `mvnw clean package`: **BUILD SUCCESS**, 111 tests, zero failures/errors/skips.
- Platform: 58 tests, including the original 48-route/response fixtures, validation and authorization, source hashes, actual JPA commit/rollback/orphan removal, JWT signature/claims/expiry/revocation, and Feign over a local HTTP stub.
- Media: 3 tests exercising the original upload service with a mocked Cloudinary uploader; exact bytes, empty upload options, original `url` selection, internal authentication, and provider error translation.
- Gateway: 50 tests against a real HTTP upstream stub; all 48 API requests/responses, binary multipart bytes, original Host/Authorization/Content-Type/X-Forwarded-For, encoded and repeated query parameters, error statuses/bodies, and internal route blocking.
- Windows runtime: all three JARs started; platform connected to MySQL. Gateway and platform health returned 200; missing/malformed JWT returned 403; gateway `/internal/health` returned 404; media returned 401 without its token and 200 `UP` with its token.
- Three local Docker images built successfully from the verified JARs via `compose.local-build.yaml`. The source Docker build encountered interrupted downloads from Maven Central, so its container Maven test run did not complete. No tests were skipped or disabled to produce the host JARs.
- Docker runtime smoke test on port 18080: all three containers started, the platform connected to host MySQL, gateway health returned 200, protected profile returned 403 without authentication, gateway internal access returned 404, and an authenticated platform-container-to-media-container health request returned 200 `UP`. The temporary containers and network were removed after verification; Windows JAR processes remained running on ports 8080–8082.

The gateway explicitly preserves the original Host ([Spring Cloud documentation](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webmvc/filters/preservehostheader.html)). Its final HTTP interceptor also preserves the raw query string and rate-limit header because default proxy normalization differs from the original direct requests. These differences are covered by the gateway tests.

Real Brevo delivery, Firebase push, Cloudinary upload and Gemini responses remain unverified; their unit/contract tests use substitutes. H2 checks are not a substitute for exercising all application workflows against production-like MySQL data. Domain/database separation beyond Phase 1 is not included.
