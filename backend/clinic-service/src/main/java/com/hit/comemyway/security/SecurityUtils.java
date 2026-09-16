package com.hit.comemyway.security;

import com.hit.comemyway.integration.UserRef;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtils {
  public UserRef getCurrentUser() {
    return (UserRef) SecurityContextHolder.getContext().getAuthentication().getDetails();
  }
}
