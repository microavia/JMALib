package com.microavia.jmalib.log.ulog;

import java.nio.ByteBuffer;

class StructParser implements Parser {
    private final Parser[] fieldParsers;

    StructParser(Parser[] fieldParsers) {
        this.fieldParsers = fieldParsers;
    }

    @Override
    public Object parse(ByteBuffer buffer) {
        Object[] values = new Object[fieldParsers.length];
        for (int i = 0; i < fieldParsers.length; i++) {
            values[i] = fieldParsers[i].parse(buffer);
        }
        return values;
    }
}
