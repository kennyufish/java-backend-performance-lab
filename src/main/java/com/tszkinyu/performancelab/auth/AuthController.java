package com.tszkinyu.performancelab.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

	private final AuthenticationService authenticationService;

	AuthController(AuthenticationService authenticationService) {
		this.authenticationService = authenticationService;
	}

	@PostMapping("/baseline")
	AuthResponse authenticateEveryRequest(@RequestBody AuthRequest request) {
		return authenticationService.authenticateEveryRequest(requireClientId(request));
	}

	@PostMapping("/session-reuse")
	AuthResponse authenticateWithSessionReuse(@RequestBody AuthRequest request) {
		return authenticationService.authenticateWithSessionReuse(requireClientId(request));
	}

	private String requireClientId(AuthRequest request) {
		if (request.clientId() == null || request.clientId().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "clientId is required");
		}
		return request.clientId();
	}
}
