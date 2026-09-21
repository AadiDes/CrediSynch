package com.credisynch.api.scoring;

public record ReasonCode(String code, String feature, double contribution, String direction) {}
