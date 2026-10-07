package edu.cit.soldano.channel;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "channel_state")
class ChannelState {
    @Id
    private Integer id = 1;
    private long cursor;
    private String instanceId;
    private String startedAt;
    private boolean online;
    private int listingCount;
    private int decisionCount;
    private int backorderCount;
    private String lastError;

    public ChannelState() {}

    static ChannelState fresh(String instanceId, String startedAt) {
        ChannelState state = new ChannelState();
        state.instanceId = instanceId;
        state.startedAt = startedAt;
        return state;
    }

    public Integer getId() { return id; }
    public long getCursor() { return cursor; }
    public void setCursor(long cursor) { this.cursor = cursor; }
    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
    public String getStartedAt() { return startedAt; }
    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }
    public int getListingCount() { return listingCount; }
    public void setListingCount(int listingCount) { this.listingCount = listingCount; }
    public int getDecisionCount() { return decisionCount; }
    public void incrementDecisions() { this.decisionCount++; }
    public int getBackorderCount() { return backorderCount; }
    public void setBackorderCount(int backorderCount) { this.backorderCount = backorderCount; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
