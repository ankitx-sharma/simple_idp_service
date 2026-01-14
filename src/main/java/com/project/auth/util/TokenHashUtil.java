package com.project.auth.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

@Component
public class TokenHashUtil {
	
	private SecureRandom secureRandom = new SecureRandom();
	
	public String generateRefreshTokenPlain() {
		byte[] bytes = new byte[64];
		secureRandom.nextBytes(bytes);
		return base64Url(bytes);
	}
	
	public String sha256Hex(String input) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
			return toHex(digest);
		} catch (Exception ex) {
			throw new IllegalStateException("Unable to hash token: " + ex);
		}
	}
	
	private String base64Url(byte[] bytes) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
	
	private String toHex(byte[] bytes) {
		StringBuilder builder = new StringBuilder(bytes.length * 2);
		for(byte b: bytes) { builder.append(String.format("%02x", b)); }
		return builder.toString();
	}
}
