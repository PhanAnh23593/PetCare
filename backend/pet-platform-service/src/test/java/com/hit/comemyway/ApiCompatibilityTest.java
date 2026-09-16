package com.hit.comemyway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hit.comemyway.config.FirebaseConfig;
import com.hit.comemyway.dto.response.*;
import com.hit.comemyway.entity.*;
import com.hit.comemyway.repository.UserRepository;
import com.hit.comemyway.security.JwtService;
import com.hit.comemyway.service.*;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiCompatibilityTest {
  @Autowired
  MockMvc mvc;
  @Autowired
  ObjectMapper json;
  @Autowired
  UserRepository users;
  @Autowired
  JwtService jwt;
  @Autowired
  RequestMappingHandlerMapping requestMappingHandlerMapping;
  @MockitoBean
  FirebaseConfig firebaseConfig;
  @MockitoBean
  AuthService auth;
  @MockitoBean
  UserService user;
  @MockitoBean
  ForgotPasswordService forgot;
  @MockitoBean
  ClinicService clinic;
  @MockitoBean
  AppointmentService appointments;
  @MockitoBean
  FriendshipService friends;
  @MockitoBean
  PetLocketService locket;
  @MockitoBean
  ImageUploadService images;
  @MockitoBean
  AIService ai;
  @MockitoBean(name = "redisTemplate")
  RedisTemplate<String, String> redis;
  ValueOperations<String, String> values;
  final Map<String, String> tokens = new HashMap<>();

  @BeforeEach
  @SuppressWarnings("unchecked")
  void prepare() {
    values = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);
    when(values.increment(anyString())).thenReturn(1L);
    for (Role role : Role.values()) {
      String name = "compat_" + role;
      User principal = users.findByUsername(name)
          .orElseGet(() -> users.save(
              User.builder().username(name).email(name + "@example.invalid").password("unused")
                  .homeAddress("test").role(role).status(AccountStatus.ACTIVE).build()));
      tokens.put(role.name(), jwt.generateToken(principal, false));
    }
    when(auth.login(any())).thenReturn(sample(LoginResponse.class));
    when(auth.refreshToken(any())).thenReturn(sample(LoginResponse.class));
    when(auth.verifyRegister(any())).thenReturn(sample(RegisterResponse.class));
    when(forgot.sendOtpForgotPassword(anyString()))
        .thenReturn(sample(ForgotPasswordResponse.class));
    when(forgot.verifyOtp(anyString(), anyString())).thenReturn("reset-token-fixture");
    when(user.getProfile()).thenReturn(sample(UpdateUserResponse.class));
    when(user.updateUserProfile(any(), anyLong())).thenReturn(sample(UpdateUserResponse.class));
    when(user.createClinicAccount(any())).thenReturn(sample(UserResponse.class));
    when(user.changeFirstTimePassword(any())).thenReturn(sample(UserResponse.class));
    when(user.getLocketLink()).thenReturn("https://petlocket.com/add?code=fixture");
    when(clinic.findClinics(nullable(String.class), nullable(Double.class), nullable(Double.class),
        nullable(Double.class), anyInt())).thenReturn(List.of(sample(ClinicSearchResponse.class)));
    when(clinic.getSuggestions(nullable(String.class), anyInt()))
        .thenReturn(List.of(sample(ClinicSuggestionResponse.class)));
    when(clinic.getClinicById(anyLong(), nullable(Double.class), nullable(Double.class)))
        .thenReturn(sample(ClinicDetailResponse.class));
    when(clinic.getClinicBookingById(anyLong())).thenReturn(sample(ClinicBookingResponse.class));
    when(clinic.getClinicSuggestions(any(), nullable(Double.class), nullable(Double.class), any(),
        any())).thenReturn(List.of(sample(DefaultSuggestClinicResponse.class)));
    when(clinic.completeClinicProfile(any()))
        .thenReturn(sample(CompleteClinicProfileResponse.class));
    when(clinic.updateClinicProfile(any())).thenReturn(sample(CompleteClinicProfileResponse.class));
    when(clinic.getClinicProfile()).thenReturn(sample(CompleteClinicProfileResponse.class));
    when(appointments.createAppointment(any())).thenReturn(sample(AppointmentResponse.class));
    when(appointments.updateAppointment(any(), anyLong()))
        .thenReturn(sample(AppointmentResponse.class));
    when(appointments.getAppointmentDetail(anyLong()))
        .thenReturn(sample(AppointmentResponse.class));
    when(appointments.cancelAppoinment(anyLong())).thenReturn(sample(AppointmentResponse.class));
    when(appointments.confirmAppointmentStatus(anyLong()))
        .thenReturn(sample(AppointmentResponse.class));
    when(appointments.rejectAppointmentStatus(anyLong(), nullable(String.class)))
        .thenReturn(sample(AppointmentResponse.class));
    when(appointments.getUserAppointment())
        .thenReturn(List.of(sample(AppointmentDisplayResponse.class)));
    when(appointments.getPendingStatusAppointment())
        .thenReturn(List.of(sample(AppointmentResponse.class)));
    when(appointments.getCofirmedStatusAppointment())
        .thenReturn(List.of(sample(AppointmentResponse.class)));
    when(appointments.getRejectedStatusAppointment())
        .thenReturn(List.of(sample(AppointmentResponse.class)));
    when(friends.addFriendRequest(any())).thenReturn(sample(FriendResponse.class));
    when(friends.findPendingFriendship()).thenReturn(List.of(sample(FriendResponse.class)));
    when(friends.getAcceptedFriends()).thenReturn(List.of(sample(FriendResponse.class)));
    when(friends.findFriend(any())).thenReturn(sample(FindFriendResponse.class));
    when(locket.createPost(any())).thenReturn(sample(PetLocketResponse.class));
    when(locket.getNewsFeed(nullable(Long.class), anyInt()))
        .thenReturn(new SliceImpl<>(List.of(sample(PetLocketResponse.class))));
    when(locket.getMyPosts(nullable(Long.class), anyInt()))
        .thenReturn(new SliceImpl<>(List.of(sample(PetLocketResponse.class))));
    try {
      when(images.uploadImage(any())).thenReturn("http://images.example.invalid/original.jpg");
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
    when(ai.processAIChat(anyString())).thenReturn(sample(AIChatResponse.class));
  }

  @TestFactory
  Stream<DynamicTest> everyPublicContractAndRole() throws Exception {
    List<JsonNode> cases = new ArrayList<>();
    json.readTree(getClass().getResourceAsStream("/compatibility/requests.json"))
        .forEach(cases::add);
    assertThat(cases).hasSize(48);
    return cases.stream().map(c -> DynamicTest
        .dynamicTest(c.path("method").asText() + " " + c.path("path").asText(), () -> {
          String role = c.path("role").asText();
          MvcResult success = mvc.perform(request(c, role.equals("PUBLIC") ? null
              : tokens.get(role.equals("AUTHENTICATED") ? "USER" : role))).andReturn();
          assertThat(success.getResponse().getStatus()).isEqualTo(c.path("status").asInt());
          ObjectNode snapshot = json.createObjectNode();
          snapshot.put("httpStatus", success.getResponse().getStatus());
          snapshot.put("contentType", success.getResponse().getContentType());
          JsonNode body = json.readTree(success.getResponse().getContentAsString());
          assertThat(body.has("timestamp")).isTrue();
          ((ObjectNode) body).remove("timestamp");
          snapshot.set("body", body);
          ObjectNode headers = snapshot.putObject("headers");
          for (String name : List.of("Cache-Control", "Pragma", "Expires", "X-Content-Type-Options",
              "X-Frame-Options", "X-XSS-Protection")) {
            headers.put(name, success.getResponse().getHeader(name));
          }
          Path baseline = Path.of("src/test/resources/compatibility/responses",
              c.path("id").asText() + ".json");
          assertThat(snapshot).isEqualTo(json.readTree(Files.readString(baseline)));
          if (!role.equals("PUBLIC")) {
            assertThat(mvc.perform(request(c, null)).andReturn().getResponse().getStatus())
                .isEqualTo(403);
            assertThat(mvc.perform(request(c, "not-a-jwt")).andReturn().getResponse().getStatus())
                .isEqualTo(403);
            for (Role actor : Role.values()) {
              if (!role.equals("AUTHENTICATED") && !actor.name().equals(role)) {
                assertThat(mvc.perform(request(c, tokens.get(actor.name()))).andReturn()
                    .getResponse().getStatus()).isEqualTo(403);
              }
            }
          }
        }));
  }

  @Test
  void inventoryContainsExactlyTheOriginalRoutes() throws Exception {
    List<String> actual = requestMappingHandlerMapping.getHandlerMethods().entrySet().stream()
        .filter(
            e -> e.getValue().getBeanType().getPackageName().equals("com.hit.comemyway.controller"))
        .flatMap(e -> e.getKey().getPatternValues().stream().flatMap(path -> e.getKey()
            .getMethodsCondition().getMethods().stream().map(method -> method + " " + path)))
        .sorted().toList();
    Path file = Path.of("src/test/resources/compatibility/routes.txt");
    assertThat(actual).hasSize(48).isEqualTo(Files.readAllLines(file));
  }

  @Test
  void validationMalformedJsonAndRateLimitKeepTheirErrorContracts() throws Exception {
    MvcResult invalid = mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();
    assertThat(invalid.getResponse().getStatus()).isEqualTo(400);
    JsonNode body = json.readTree(invalid.getResponse().getContentAsString());
    assertThat(body.path("data").path("username").asText())
        .isEqualTo("This field cannot be blank.");
    assertThat(body.path("data").path("password").asText())
        .isEqualTo("This field cannot be blank.");
    MvcResult malformed = mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON).content("{")).andReturn();
    assertThat(malformed.getResponse().getStatus()).isEqualTo(400);
    when(values.increment(anyString())).thenReturn(6L);
    MvcResult limited = mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();
    assertThat(limited.getResponse().getStatus()).isEqualTo(429);
    JsonNode rate = json.readTree(limited.getResponse().getContentAsString());
    assertThat(rate.path("message").asText())
        .isEqualTo("Too many request. Please try again after 1 minute.");
    assertThat(rate.has("timestamp")).isFalse();
  }

  private AbstractMockHttpServletRequestBuilder<?> request(JsonNode c, String token)
      throws Exception {
    AbstractMockHttpServletRequestBuilder<?> builder;
    if (c.path("multipart").asBoolean()) {
      builder = MockMvcRequestBuilders.multipart(c.path("path").asText())
          .file(new MockMultipartFile("file", "test.png", "image/png", new byte[] {0, 1, 2, -1}));
    } else {
      builder = MockMvcRequestBuilders.request(HttpMethod.valueOf(c.path("method").asText()),
          c.path("path").asText());
      if (c.has("body")) {
        builder.contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsBytes(c.get("body")));
      }
    }
    if (token != null) {
      builder.header("Authorization", "Bearer " + token);
    }
    return builder;
  }

  @SuppressWarnings("unchecked")
  private static <T> T sample(Class<T> type) {
    return (T) fixture(type);
  }

  private static Object fixture(Type type) {
    if (type instanceof ParameterizedType generic && generic.getRawType() == List.class) {
      return List.of(fixture(generic.getActualTypeArguments()[0]));
    }
    Class<?> cls = (Class<?>) type;
    if (cls == String.class)
      return "fixture";
    if (cls == Long.class)
      return 1L;
    if (cls == Integer.class)
      return 1;
    if (cls == Double.class)
      return 1.5;
    if (cls == Boolean.class)
      return true;
    if (cls == Instant.class)
      return Instant.parse("2026-01-01T00:00:00Z");
    if (cls == LocalDate.class)
      return LocalDate.of(2030, 1, 1);
    if (cls == LocalTime.class)
      return LocalTime.of(9, 30);
    if (cls.isEnum())
      return cls.getEnumConstants()[0];
    if (cls.isRecord()) {
      RecordComponent[] components = cls.getRecordComponents();
      Class<?>[] types = new Class<?>[components.length];
      Object[] arguments = new Object[components.length];
      for (int i = 0; i < components.length; i++) {
        types[i] = components[i].getType();
        arguments[i] = fixture(components[i].getGenericType());
      }
      try {
        return cls.getDeclaredConstructor(types).newInstance(arguments);
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException(e);
      }
    }
    throw new IllegalArgumentException("Missing fixture for " + type);
  }
}
