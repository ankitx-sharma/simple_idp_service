package com.project.auth.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.auth.entity.RefreshTokenEntity;
import com.project.auth.entity.UserEntity;
import com.project.auth.repository.RefreshTokenRepository;
import com.project.auth.repository.UserRepository;
import com.project.auth.util.JwtProperties;
import com.project.auth.util.JwtUtil;
import com.project.auth.util.TokenHashUtil;

@Service
public class RefreshTokenService {
	
	@Autowired
	private RefreshTokenRepository repository;
	
	@Autowired
	private UserRepository userRepository;
	
	@Autowired
	private TokenHashUtil hashUtil;
	
	@Autowired
	private JwtUtil jwtUtil;
	
	@Autowired
	private JwtProperties properties;
	
	@Transactional
	public String issueRefreshToken(UserEntity user, String sessionId) {
		String plain = hashUtil.generateRefreshTokenPlain();
		String hash = hashUtil.sha256Hex(plain);
		
		RefreshTokenEntity tokenEntity = new RefreshTokenEntity();
		tokenEntity.setUser(user);
		tokenEntity.setTokenHash(hash);
		tokenEntity.setSessionId(sessionId);
		tokenEntity.setExpiresAt(Instant.now().plus(properties.getRefreshTokenTtlDays(), ChronoUnit.DAYS));
		repository.save(tokenEntity);
		
		return plain;
	}
	
	@Transactional
	public String refreshToken(String refreshToken) {
		RefreshTokenEntity entity = validateRefreshTokenOrThrow(refreshToken);
		
		// Reload user to avoid lazy-loading issues
		Long userId = entity.getUser().getId();
		UserEntity user = userRepository.findById(userId)
				.orElseThrow(() -> new IllegalArgumentException("User not found."));
		
		return jwtUtil.generateAccessToken(user.getUsername(), user.getRole());
	}
	
	@Transactional
	public void revoke(String refreshTokenPlain) {
		String hash = hashUtil.sha256Hex(refreshTokenPlain);
		
		repository.findByTokenHash(hash).ifPresent(refreshEntity -> {
			refreshEntity.setRevoked(true);
			refreshEntity.setRevokedAt(Instant.now());
		});
	}
	
	private RefreshTokenEntity validateRefreshTokenOrThrow(String refreshTokenPlain) {
		String hash = hashUtil.sha256Hex(refreshTokenPlain);
		
		RefreshTokenEntity tokenEntity = repository.findByTokenHash(hash)
				.orElseThrow(() -> new IllegalArgumentException("Invalid refresh token."));
		
		if(tokenEntity.isRevoked()) { throw new IllegalArgumentException("Refresh token revoked"); }
		if(tokenEntity.getExpiresAt().isBefore(Instant.now())) { throw new IllegalArgumentException("Refresh token expired"); }
		
		tokenEntity.setLastUsedAt(Instant.now());
		return tokenEntity;
	}
}
