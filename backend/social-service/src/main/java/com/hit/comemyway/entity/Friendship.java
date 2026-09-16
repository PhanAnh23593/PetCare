package com.hit.comemyway.entity;

import com.hit.comemyway.integration.UserRef;

import com.hit.comemyway.constant.CommonConstant;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "friendships",
    uniqueConstraints = {
        @UniqueConstraint(name = "unique_user_friend", columnNames = {"user_id", "friend_id"})},
    indexes = {@Index(name = "index_user_status", columnList = "user_id, status"),
        @Index(name = "index_friend_status", columnList = "friend_id, status")})
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class Friendship extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Embedded
  @AttributeOverride(name = "id", column = @Column(name = "user_id", nullable = false))
  private UserRef user;

  @Embedded
  @AttributeOverride(name = "id", column = @Column(name = "friend_id", nullable = false))
  private UserRef friend;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = CommonConstant.User.FRIENDSHIP_STATUS_LENGTH)
  private FriendshipStatus status;
}
