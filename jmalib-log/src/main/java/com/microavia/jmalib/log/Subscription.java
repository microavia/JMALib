package com.microavia.jmalib.log;

import com.microavia.jmalib.log.ulog.Message;

import java.nio.ByteBuffer;

public class Subscription {
    private final String path;
    private final ValueParser parser;
    private final int multiIdFilter;
    private Object value;
    private boolean updated;

    public Subscription(String path, ValueParser parser, int multiIdFilter) {
        this.path = path;
        this.parser = parser;
        this.multiIdFilter = multiIdFilter;
    }

    public boolean update(ByteBuffer buffer, int multiId) {
        if (parser != null && ((multiIdFilter == -1 && ((multiId & 0x80) != 0)) || (multiId & 0x7F) == multiIdFilter)) {
            value = parser.parse(buffer);
            updated = true;
            return true;
        }
        return false;
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
