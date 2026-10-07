package edu.cit.soldano.notification;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = "http://localhost:5173")
class NotificationController {

    private final NotificationRepository repository;

    NotificationController(NotificationRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<NotificationDto> getNotifications() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(n -> new NotificationDto(n.getNotificationId(), n.getMessage(), n.getCreatedAt()))
                .toList();
    }
}