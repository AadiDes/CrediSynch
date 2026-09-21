package com.credisynch.api.cases;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LabelRequest(
        @NotNull @Pattern(regexp = "FRAUD|LEGITIMATE|UNCERTAIN") String label,
        @Size(max = 500) String note) {}
