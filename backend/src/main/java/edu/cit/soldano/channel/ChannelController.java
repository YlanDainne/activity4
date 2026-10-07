package edu.cit.soldano.channel;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/channel")
@CrossOrigin(origins = "http://localhost:5173")
class ChannelController {
    private final MarketplaceChannel channel;

    ChannelController(MarketplaceChannel channel) {
        this.channel = channel;
    }

    @GetMapping("/status")
    ChannelStatus status() {
        return channel.status();
    }
}
