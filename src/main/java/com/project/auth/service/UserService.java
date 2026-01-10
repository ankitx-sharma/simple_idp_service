package com.project.auth.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.project.auth.dto.User;
import com.project.auth.entity.UserEntity;
import com.project.auth.repository.UserRepository;

@Service
public class UserService {
	
	@Autowired
	private UserRepository userRepository;
	
	@Autowired
	private PasswordEncoder passwordEncoder;
	
	public void registerUser(User user) {
		String password = passwordEncoder.encode(user.getPassword());
		UserEntity userEntity = new UserEntity(user.getUsername(), password);
		userRepository.save(userEntity);
	}
	
	public Optional<User> findByUserName(String username){
		return userRepository.findByUsername(username).map(User::new);
	}
}
