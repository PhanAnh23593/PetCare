package com.hit.comemyway.security;

import com.hit.comemyway.integration.IdentityClient;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
public class RemoteAuthenticationFilter extends OncePerRequestFilter {
  private final IdentityClient identity;

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
      FilterChain chain) throws ServletException, IOException {
    String bearer = req.getHeader("Authorization");
    if (bearer != null && bearer.startsWith("Bearer ")) {
      try {
        var user = identity.authenticate(bearer);
        if (user != null && user.getRole() != null) {
          var auth = new UsernamePasswordAuthenticationToken(user.getUsername(), null,
              List.of(new SimpleGrantedAuthority(user.getRole().name())));
          auth.setDetails(user);
          SecurityContextHolder.getContext().setAuthentication(auth);
        }
      } catch (ResponseStatusException ex) {
        res.sendError(503, "Identity unavailable");
        return;
      }
    }
    chain.doFilter(req, res);
  }
}
