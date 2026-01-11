package com.project.auth.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.project.auth.dto.User;
import com.project.auth.entity.Role;
import com.project.auth.entity.UserEntity;
import com.project.auth.repository.UserRepository;
import com.project.auth.util.JwtUtil;

@Service
public class UserService {
	
	@Autowired
	private JwtUtil jwtUtil;
	
	@Autowired
	private UserRepository userRepository;
	
	@Autowired
	private PasswordEncoder passwordEncoder;
	
	public void registerUser(User user) {
		String password = passwordEncoder.encode(user.getPassword());
		UserEntity userEntity = new UserEntity(user.getUsername(), password, Role.USER);
		userRepository.save(userEntity);
	}
	
	public void registerAdmin(User user) {
		String password = passwordEncoder.encode(user.getPassword());
		UserEntity userEntity = new UserEntity(user.getUsername(), password, Role.ADMIN);
		userRepository.save(userEntity);
	}
	
	public String loginUser(User loginRequest) {
		User user = findByUserName(loginRequest.getUsername())
				.orElseThrow(() -> new RuntimeException("Invalid credentials"));
		
		if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
		    throw new RuntimeException("Invalid credentials");
		}
		
		return jwtUtil.generateAccessToken(loginRequest.getUsername(), user.getRole());
	}
	
	public Optional<User> findByUserName(String username){
		return userRepository.findByUsername(username).map(User::new);
	}
}
