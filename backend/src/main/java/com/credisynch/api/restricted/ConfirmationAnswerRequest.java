package com.credisynch.api.restricted;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ConfirmationAnswerRequest(
        @NotNull @Pattern(regexp = "YES_IT_WAS_ME|NOT_ME") String answer) {}
