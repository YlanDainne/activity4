package edu.cit.soldano.channel;

import java.time.Instant;

public interface ClientInstanceProvider {
    String instanceId();
    Instant startedAt();
    boolean callsAllowed();
    void allowCalls();
}
