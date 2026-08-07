package com.tszkinyu.performancelab.auth;

import java.time.Instant;
import java.util.UUID;

public record AuthResponse(String clientId, UUID sessionId, Instant expiresAt, boolean reused) {
}
