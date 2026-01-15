package com.d3.client.models;

import java.util.function.Consumer;

public class PollStatusOptions extends StatusOptions {
    private long interval;
    private long timeout;
    private Consumer<StatusResponse> onUpdate;

    public long getInterval() {
        return interval;
    }

    public PollStatusOptions setInterval(long interval) {
        this.interval = interval;
        return this;
    }

    public long getTimeout() {
        return timeout;
    }

    public PollStatusOptions setTimeout(long timeout) {
        this.timeout = timeout;
        return this;
    }

    public Consumer<StatusResponse> getOnUpdate() {
        return onUpdate;
    }

    public PollStatusOptions setOnUpdate(Consumer<StatusResponse> onUpdate) {
        this.onUpdate = onUpdate;
        return this;
    }
}

