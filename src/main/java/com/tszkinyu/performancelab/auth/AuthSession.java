package com.tszkinyu.performancelab.auth;

import java.time.Instant;
import java.util.UUID;

record AuthSession(String clientId, UUID id, Instant expiresAt) {

	boolean isActiveAt(Instant instant) {
		return expiresAt.isAfter(instant);
	}
}
