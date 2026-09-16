package com.hit.media;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InternalTokenFilter extends OncePerRequestFilter {
  private final byte[] expected;

  public InternalTokenFilter(@Value("${media.internal.token}") String token) {
    if (token.isBlank()) {
      throw new IllegalArgumentException("MEDIA_INTERNAL_TOKEN must be configured");
    }
    expected = token.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    String token = request.getHeader("X-Media-Token");
    if (token == null || !MessageDigest.isEqual(expected, token.getBytes(StandardCharsets.UTF_8))) {
      response.setStatus(401);
      return;
    }
    chain.doFilter(request, response);
  }
}
