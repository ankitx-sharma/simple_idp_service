package com.project.auth.dto;

import com.project.auth.entity.Role;
import com.project.auth.entity.UserEntity;

public class User {
	private long id;
	private String username;
	private String password;
	private Role role;
	
	public User() { super(); }
	
	public User(long id, String username, String password, Role role) {
		this.id = id;
		this.username = username;
		this.password = password;
		this.role = role;
	}

	public User(UserEntity entity) {
		this.id = entity.getId();
		this.username = entity.getUsername();
		this.password = entity.getPassword();
		this.role = entity.getRole();
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}
	
	public Role getRole() {
		return this.role;
	}
	
	public void setRole(Role role) {
		this.role = role;
	}
}
