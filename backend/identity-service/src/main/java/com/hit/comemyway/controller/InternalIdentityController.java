package com.hit.comemyway.controller;

import com.hit.comemyway.entity.*;
import com.hit.comemyway.repository.UserRepository;
import com.hit.comemyway.security.*;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/identity")
public class InternalIdentityController {
  private final UserRepository users;
  private final JwtService jwt;
  private final String clinicToken;
  private final String socialToken;

  public InternalIdentityController(UserRepository users, JwtService jwt,
      @Value("${internal.clinic.token}") String clinicToken,
      @Value("${internal.social.token}") String socialToken) {
    if (clinicToken.isBlank() || socialToken.isBlank())
      throw new IllegalArgumentException("Internal tokens required");
    this.users = users;
    this.jwt = jwt;
    this.clinicToken = clinicToken;
    this.socialToken = socialToken;
  }

 public record Profile(Long id,String username,String avatar,String deviceToken,String fullName,
     Role role,AccountStatus status) {
  static Profile from(User u, boolean clinic) { return new Profile(u.getId(),u.getUsername(),u.getAvatar(),
      clinic?u.getDeviceToken():null,u.getFullName(),u.getRole(),u.getStatus()); }
 }

  private boolean equal(String a, String b) {
    return a != null && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),
        b.getBytes(StandardCharsets.UTF_8));
  }

  private boolean authorize(HttpServletRequest request) {
    String token = request.getHeader("X-Service-Token");
    boolean clinic = equal(token, clinicToken);
    if (!clinic && !equal(token, socialToken))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    return clinic;
  }

  @GetMapping("/me")
  public Profile me(HttpServletRequest request,
      @RequestHeader(value = "Authorization", required = false) String bearer) {
    boolean clinic = authorize(request);
    if (bearer == null || !bearer.startsWith("Bearer "))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    try {
      String token = bearer.substring(7);
      User user = users.findByUsername(jwt.extractUsername(token)).orElseThrow();
      if (!jwt.isAccessToken(token) || !jwt.isTokenValid(token, new CustomUserDetails(user)))
        throw new IllegalArgumentException();
      return Profile.from(user, clinic);
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
  }

  @GetMapping("/users")
  public Profile lookup(HttpServletRequest request, @RequestParam(required = false) Long id,
      @RequestParam(required = false) String username,
      @RequestParam(required = false) String code) {
    boolean clinic = authorize(request);
    User user = (id != null ? users.findById(id)
        : username != null ? users.findByUsername(username) : users.findBylocketCode(code))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    return Profile.from(user, clinic);
  }

  /** Idempotent delivery from the clinic outbox. */
  @PostMapping("/clinics/{id}/activate")
  @Transactional
  public void activate(HttpServletRequest request, @PathVariable Long id) {
    if (!authorize(request))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    User user =
        users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (user.getRole() != Role.CLINIC)
      throw new ResponseStatusException(HttpStatus.CONFLICT);
    if (user.getStatus() == AccountStatus.ACTIVE)
      return;
    if (user.getStatus() != AccountStatus.PENDING_PROFILE)
      throw new ResponseStatusException(HttpStatus.CONFLICT);
    user.setStatus(AccountStatus.ACTIVE);
  }
}
