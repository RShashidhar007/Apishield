package io.apishield.backend.common;

import java.util.Map;

public record ApiError(String code, String message, String correlationId, Map<String, String> details) {}
