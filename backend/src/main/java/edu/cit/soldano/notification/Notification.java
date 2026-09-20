package edu.cit.soldano.notification;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer notificationId;

    private String message;
    private LocalDateTime createdAt = LocalDateTime.now();

    public Notification() {}
    public Notification(String message) {
        this.message = message;
        this.createdAt = LocalDateTime.now();
    }

    public Integer getNotificationId() { return notificationId; }
    public String getMessage() { return message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}