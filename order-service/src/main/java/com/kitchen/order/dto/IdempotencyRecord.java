package com.kitchen.order.dto;

import java.time.LocalDateTime;

public class IdempotencyRecord {

    public enum State {
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }

    private final String key;
    private State state;
    private int statusCode;
    private Object responseBody;
    private final LocalDateTime createdAt;

    public IdempotencyRecord(String key) {
        this.key = key;
        this.state = State.IN_PROGRESS;
        this.createdAt = LocalDateTime.now();
    }

    public String getKey() {
        return key;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public Object getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(Object responseBody) {
        this.responseBody = responseBody;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
