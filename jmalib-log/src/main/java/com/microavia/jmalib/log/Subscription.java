package com.microavia.jmalib.log;

import java.nio.ByteBuffer;

public class Subscription {
    private final String path;
    private final ValueParser parser;
    private Object value;
    private boolean updated;

    public Subscription(String path, ValueParser parser) {
        this.path = path;
        this.parser = parser;
    }

    public void update(ByteBuffer buffer) {
        if (parser != null) {
            value = parser.parse(buffer);
            updated = true;
        }
    }

    public String getPath() {
        return path;
    }

    public boolean isUpdated() {
        return updated;
    }

    public Object getValue() {
        updated = false;
        return value;
    }
}
