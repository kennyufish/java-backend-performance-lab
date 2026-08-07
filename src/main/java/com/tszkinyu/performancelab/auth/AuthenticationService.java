package com.tszkinyu.performancelab.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
class AuthenticationService {

	private final ConcurrentMap<String, AuthSession> sessions = new ConcurrentHashMap<>();
	private final Duration sessionTtl;

	AuthenticationService(@Value("${lab.auth.session-ttl}") Duration sessionTtl) {
		this.sessionTtl = sessionTtl;
	}

	AuthResponse authenticateEveryRequest(String clientId) {
		return toResponse(newSession(clientId), false);
	}

	AuthResponse authenticateWithSessionReuse(String clientId) {
		Instant now = Instant.now();
		AtomicBoolean reused = new AtomicBoolean(false);
		AuthSession session = sessions.compute(clientId, (key, existing) -> {
			if (existing != null && existing.isActiveAt(now)) {
				reused.set(true);
				return existing;
			}
			return newSession(key);
		});

		return toResponse(session, reused.get());
	}

	private AuthSession newSession(String clientId) {
		return new AuthSession(clientId, UUID.randomUUID(), Instant.now().plus(sessionTtl));
	}

	private AuthResponse toResponse(AuthSession session, boolean reused) {
		return new AuthResponse(session.clientId(), session.id(), session.expiresAt(), reused);
	}
}
