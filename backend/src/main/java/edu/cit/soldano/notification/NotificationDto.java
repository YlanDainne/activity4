package edu.cit.soldano.notification;

import java.time.LocalDateTime;

public record NotificationDto(Integer notificationId, String message, LocalDateTime createdAt) {}