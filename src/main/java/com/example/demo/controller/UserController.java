package com.example.demo.controller;

import com.example.demo.model.SignupUser;
import com.example.demo.repository.SignupUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final String DEFAULT_ADMIN_EMAIL = "admin@gmail.com";
    private static final String DEFAULT_ADMIN_PASS = "admin@123";

    @Autowired
    private SignupUserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String getRoleTable(String role) {
        String normalized = role == null ? "" : role.trim().toLowerCase();
        if ("admin".equals(normalized)) return "admins";
        if ("manager".equals(normalized)) return "managers";
        if ("team".equals(normalized)) return "team_members";
        return "individual_users";
    }

    private void syncRoleTables(String name, String email, String password, String targetRole) {
        String cleanEmail = email.trim().toLowerCase();
        jdbcTemplate.update("DELETE FROM admins WHERE LOWER(email) = ?", cleanEmail);
        jdbcTemplate.update("DELETE FROM managers WHERE LOWER(email) = ?", cleanEmail);
        jdbcTemplate.update("DELETE FROM team_members WHERE LOWER(email) = ?", cleanEmail);
        jdbcTemplate.update("DELETE FROM individual_users WHERE LOWER(email) = ?", cleanEmail);

        String table = getRoleTable(targetRole);
        jdbcTemplate.update("INSERT INTO " + table + " (name, email, password) VALUES (?, ?, ?)",
                name, cleanEmail, password);
    }

    private void ensureDefaultAdmin() {
        try {
            Optional<SignupUser> opt = userRepository.findByEmailIgnoreCase(DEFAULT_ADMIN_EMAIL);
            if (opt.isEmpty()) {
                SignupUser admin = new SignupUser("Admin", "admin", DEFAULT_ADMIN_EMAIL, DEFAULT_ADMIN_PASS);
                userRepository.save(admin);
                syncRoleTables("Admin", DEFAULT_ADMIN_EMAIL, DEFAULT_ADMIN_PASS, "admin");
            } else {
                SignupUser admin = opt.get();
                if (!"admin".equals(admin.getRole()) || !DEFAULT_ADMIN_PASS.equals(admin.getPassword())) {
                    admin.setRole("admin");
                    admin.setPassword(DEFAULT_ADMIN_PASS);
                    userRepository.save(admin);
                    syncRoleTables("Admin", DEFAULT_ADMIN_EMAIL, DEFAULT_ADMIN_PASS, "admin");
                }
            }
        } catch (Exception e) {
            System.err.println("Error ensuring default admin: " + e.getMessage());
        }
    }

    // GET /api/users
    @GetMapping
    public ResponseEntity<List<SignupUser>> getUsers() {
        ensureDefaultAdmin();
        List<SignupUser> list = userRepository.findAll();
        return ResponseEntity.ok(list);
    }

    // POST /api/users (Signup / Create user)
    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody Map<String, Object> body) {
        ensureDefaultAdmin();
        String name = (String) body.get("name");
        String email = (String) body.get("email");
        String password = (String) body.get("password");
        String role = (String) body.get("role");

        if (name == null || name.trim().isEmpty() ||
            email == null || email.trim().isEmpty() ||
            password == null || password.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Name, email, and password are required"));
        }

        String cleanEmail = email.trim().toLowerCase();
        String cleanName = name.trim();

        if (DEFAULT_ADMIN_EMAIL.equals(cleanEmail)) {
            return ResponseEntity.badRequest().body(Map.of("error", "This email is reserved for the system administrator"));
        }

        if (userRepository.existsByEmailIgnoreCase(cleanEmail)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "User with this email already exists"));
        }

        String cleanRole = (role == null || role.trim().isEmpty()) ? "user" : role.trim().toLowerCase();
        if ("admin".equals(cleanRole)) {
            cleanRole = "user";
        }

        SignupUser user = new SignupUser(cleanName, cleanRole, cleanEmail, password);
        SignupUser saved = userRepository.save(user);

        syncRoleTables(cleanName, cleanEmail, password, cleanRole);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "User added successfully",
                "id", saved.getId(),
                "table", getRoleTable(cleanRole)
        ));
    }

    // PUT /api/users (Update user role)
    @PutMapping
    public ResponseEntity<?> updateRole(@RequestBody Map<String, Object> body) {
        ensureDefaultAdmin();
        String email = (String) body.get("email");
        String role = (String) body.get("role");

        if (email == null || email.trim().isEmpty() || role == null || role.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email and role are required"));
        }

        String cleanEmail = email.trim().toLowerCase();
        String cleanRole = role.trim().toLowerCase();

        // Protect admin role
        if (DEFAULT_ADMIN_EMAIL.equals(cleanEmail)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "The admin role cannot be changed by anyone."));
        }

        // Prevent setting admin role
        if ("admin".equals(cleanRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only one admin is permitted."));
        }

        Optional<SignupUser> opt = userRepository.findByEmailIgnoreCase(cleanEmail);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found"));
        }

        SignupUser user = opt.get();
        user.setRole(cleanRole);
        userRepository.save(user);

        syncRoleTables(user.getName(), cleanEmail, user.getPassword(), cleanRole);

        return ResponseEntity.ok(Map.of("message", "User role updated"));
    }

    // DELETE /api/users
    @DeleteMapping
    public ResponseEntity<?> deleteUser(@RequestBody Map<String, Object> body) {
        ensureDefaultAdmin();
        String email = (String) body.get("email");

        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is required to delete a user"));
        }

        String cleanEmail = email.trim().toLowerCase();

        if (DEFAULT_ADMIN_EMAIL.equals(cleanEmail)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "The admin account cannot be deleted."));
        }

        Optional<SignupUser> opt = userRepository.findByEmailIgnoreCase(cleanEmail);
        if (opt.isPresent()) {
            userRepository.delete(opt.get());
        }

        jdbcTemplate.update("DELETE FROM admins WHERE LOWER(email) = ?", cleanEmail);
        jdbcTemplate.update("DELETE FROM managers WHERE LOWER(email) = ?", cleanEmail);
        jdbcTemplate.update("DELETE FROM team_members WHERE LOWER(email) = ?", cleanEmail);
        jdbcTemplate.update("DELETE FROM individual_users WHERE LOWER(email) = ?", cleanEmail);

        return ResponseEntity.ok(Map.of("message", "User deleted"));
    }
}
