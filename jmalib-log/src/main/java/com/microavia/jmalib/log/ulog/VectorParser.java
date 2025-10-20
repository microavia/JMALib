package com.microavia.jmalib.log.ulog;

import java.nio.ByteBuffer;

class VectorParser implements Parser {
    private final Parser itemParser;

    VectorParser(Parser itemParser) {
        this.itemParser = itemParser;
    }

    @Override
    public Object parse(ByteBuffer buffer) {
        int size = buffer.getInt();
        Object[] items = new Object[size];
        for (int i = 0; i < size; i++) {
            items[i] = itemParser.parse(buffer);
        }
        return items;
    }
}
