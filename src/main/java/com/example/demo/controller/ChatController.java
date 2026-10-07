package com.example.demo.controller;

import com.example.demo.model.Chat;
import com.example.demo.repository.ChatRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private ChatRepository chatRepository;

    // GET /api/chat
    @GetMapping
    public ResponseEntity<List<Chat>> getMessages() {
        List<Chat> messages = chatRepository.findAllByOrderByCreatedAtAsc();
        return ResponseEntity.ok(messages);
    }

    // POST /api/chat
    @PostMapping
    public ResponseEntity<?> sendMessage(@RequestBody Map<String, Object> body) {
        String sender = (String) body.get("sender");
        String recipient = (String) body.get("recipient");
        String text = (String) body.get("text");

        if (sender == null || sender.trim().isEmpty() ||
            recipient == null || recipient.trim().isEmpty() ||
            text == null || text.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Sender, recipient, and message text are required."));
        }

        String id = UUID.randomUUID().toString();
        String createdAt = Instant.now().toString();

        Chat chat = new Chat(id, sender.trim(), recipient.trim(), text.trim(), createdAt);
        Chat saved = chatRepository.save(chat);

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
}
