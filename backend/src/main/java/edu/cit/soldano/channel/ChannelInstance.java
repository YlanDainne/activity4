package edu.cit.soldano.channel;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
class ChannelInstance implements ClientInstanceProvider {
    private final String instanceId = UUID.randomUUID().toString();
    private final Instant startedAt = Instant.now();
    private final AtomicBoolean callsAllowed = new AtomicBoolean();

    @Override
    public String instanceId() {
        return instanceId;
    }

    @Override
    public Instant startedAt() {
        return startedAt;
    }

    @Override
    public boolean callsAllowed() {
        return callsAllowed.get();
    }

    @Override
    public void allowCalls() {
        callsAllowed.set(true);
    }
}
