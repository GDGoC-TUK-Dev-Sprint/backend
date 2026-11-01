package com.tukorea.gdgoc.koready.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(@NotBlank String refreshToken) {}
