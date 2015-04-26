package com.arborisfolium.powermeter;

import java.io.Serializable;

public class RavenMessage implements Serializable {
    private static final long serialVersionUID = 1000L;

    private long timestamp = 0L;
    private String topic = null;
    private double value = Double.NaN;

    public RavenMessage() {

    }

    public RavenMessage(final long timestamp, final String topic, final double value) {
        this.timestamp = timestamp;
        this.topic = topic;
        this.value = value;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(final long timestamp) {
        this.timestamp = timestamp;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(final String topic) {
        this.topic = topic;
    }

    public double getValue() {
        return value;
    }

    public void setValue(final double value) {
        this.value = value;
    }
}
