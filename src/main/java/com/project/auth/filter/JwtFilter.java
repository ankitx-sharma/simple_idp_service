package com.project.auth.filter;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.project.auth.util.JwtUtil;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class JwtFilter implements Filter{

	@Autowired
	private JwtUtil jwtUtil;

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		
		HttpServletRequest httpRequest = (HttpServletRequest) request;
		String authHeader = httpRequest.getHeader("Authorization");
		
		try {
			if(authHeader != null && authHeader.startsWith("Bearer ")) {
				String token = authHeader.substring(7);
				Claims isTokenValid = jwtUtil.parseAndValidate(token);
				Boolean isexpired = isTokenValid.getExpiration().before(Date.from(Instant.now()));
				
				if(isTokenValid != null && SecurityContextHolder.getContext().getAuthentication() == null && !isexpired) {
					UsernamePasswordAuthenticationToken auth = 
							new UsernamePasswordAuthenticationToken(isTokenValid.getSubject(), null, Collections.emptyList());
					SecurityContextHolder.getContext().setAuthentication(auth);
				}
			}
		} catch (JwtException ex) {}
		
		chain.doFilter(request, response);
		
	}

	
}
