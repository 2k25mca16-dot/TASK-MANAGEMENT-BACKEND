package com.example.demo.model;

import jakarta.persistence.*;

@Entity
@Table(name = "chats")
public class Chat {

    @Id
    private String id;

    @Column(nullable = false)
    private String sender;

    @Column(nullable = false)
    private String recipient;

    @Column(columnDefinition = "TEXT")
    private String text;

    @Column(name = "createdAt")
    private String createdAt;

    public Chat() {
    }

    public Chat(String id, String sender, String recipient, String text, String createdAt) {
        this.id = id;
        this.sender = sender;
        this.recipient = recipient;
        this.text = text;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}

