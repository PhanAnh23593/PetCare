package com.hit.comemyway;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.hit.comemyway.entity.*;
import com.hit.comemyway.integration.*;
import com.hit.comemyway.repository.*;
import com.hit.comemyway.service.*;
import com.hit.comemyway.dto.request.*;
import java.util.*;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SocialBoundaryTest {
  @Test
  void swaggerIncludesSocialRoutes() throws Exception {
    mvc.perform(get("/v3/api-docs/social")).andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/user/locket/feed']").exists())
        .andExpect(jsonPath("$.paths['/api/v1/auth/login']").doesNotExist());
  }

  @Autowired
  PetLocketService posts;
  @Autowired
  FriendshipService friends;
  @Autowired
  EntityManagerFactory entities;
  @Autowired
  MockMvc mvc;
  @MockitoBean
  IdentityClient identity;

  UserRef signIn(long id) {
    var u = UserRef.builder().id(id).username("user" + id).avatar("avatar" + id).role(Role.USER)
        .build();
    when(identity.findById(id)).thenReturn(Optional.of(u));
    var auth = new UsernamePasswordAuthenticationToken(u.getUsername(), null, List.of());
    auth.setDetails(u);
    SecurityContextHolder.getContext().setAuthentication(auth);
    return u;
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void ownsOnlySocialData() {
    assertThat(entities.getMetamodel().getEntities()).extracting(e -> e.getName())
        .containsExactlyInAnyOrder("Friendship", "PetLocket");
  }

  @Test
  void feedIncludesAcceptedFriendsOnlyAndDeleteRequiresOwnership() {
    signIn(2);
    var post = posts.createPost(new PetLocketCreateRequest("image", "caption"));
    signIn(1);
    assertThat(posts.getNewsFeed(null, 10).getContent()).isEmpty();
    var request = friends.addFriendRequest(new FriendRequest(2L));
    signIn(2);
    friends.acceptFriendRequest(request.friendshipId());
    signIn(1);
    assertThat(posts.getNewsFeed(null, 10).getContent()).extracting(p -> p.id())
        .contains(post.id());
    assertThatThrownBy(() -> posts.deletePost(post.id())).isInstanceOf(RuntimeException.class);
    signIn(2);
    posts.deletePost(post.id());
    assertThat(posts.getMyPosts(null, 10).getContent()).isEmpty();
  }

  @Test
  void forgedOrMissingBearerCannotReadFeed() throws Exception {
    mvc.perform(get("/api/v1/user/locket/feed")).andExpect(status().isUnauthorized());

    mvc.perform(get("/api/v1/user/locket/feed").header("Authorization", "Bearer forged"))
        .andExpect(status().isUnauthorized());
  }
}
