package com.hit.comemyway.controller;

import com.hit.comemyway.base.ApiResponse;
import com.hit.comemyway.dto.request.ChangePasswordRequest;
import com.hit.comemyway.dto.response.UserResponse;
import com.hit.comemyway.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ClinicPasswordController {
  private final UserService users;

  @PostMapping("/api/v1/clinic/change-password")
  public ApiResponse<UserResponse> change(@Valid @RequestBody ChangePasswordRequest request) {
    return ApiResponse.ok(users.changeFirstTimePassword(request));
  }
}
