package com.hit.comemyway.config;

import com.hit.comemyway.entity.AccountStatus;
import com.hit.comemyway.entity.Role;
import com.hit.comemyway.entity.User;
import com.hit.comemyway.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DevelopmentAdminConfig {
  /** Opt-in bootstrap for a fresh database. Never changes an existing account. */
  @Bean
  ApplicationRunner developmentAdmin(UserRepository users, PasswordEncoder encoder,
      @Value("${BOOTSTRAP_ADMIN_PASSWORD:}") String password,
      @Value("${BOOTSTRAP_ADMIN_USERNAME:devadmin}") String username,
      @Value("${BOOTSTRAP_ADMIN_EMAIL:devadmin@example.invalid}") String email) {
    return args -> {
      if (password.isBlank() || users.existsByUsername(username))
        return;
      if (password.length() < 12)
        throw new IllegalArgumentException("Bootstrap password needs at least 12 characters");
      users.save(User.builder().username(username).email(email).password(encoder.encode(password))
          .homeAddress("").fullName("Development admin").role(Role.ADMIN)
          .status(AccountStatus.ACTIVE).build());
    };
  }
}
