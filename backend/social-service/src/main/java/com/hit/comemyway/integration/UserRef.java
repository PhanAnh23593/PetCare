package com.hit.comemyway.integration;

import jakarta.persistence.*;
import lombok.*;
import com.hit.comemyway.entity.Role;
import com.hit.comemyway.entity.AccountStatus;

/** External identity reference. Only id is stored in this service's database. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRef {
  @Column(nullable = false)
  private Long id;
  @Transient
  private String username;
  @Transient
  private String avatar;
  @Transient
  private String deviceToken;
  @Transient
  private String fullName;
  @Transient
  private Role role;
  @Transient
  private AccountStatus status;
}
