package edu.cit.soldano.channel;

public record ChannelStatus(
        boolean online,
        String instanceId,
        long cursor,
        int listingCount,
        int decisionCount,
        int backorderCount,
        String lastError
) {}
