package com.microavia.jmalib.log.ulog;

import java.nio.ByteBuffer;
import java.util.ArrayList;

class VectorParser implements Parser {
    private final Parser itemParser;

    VectorParser(Parser itemParser) {
        this.itemParser = itemParser;
    }

    @Override
    public Object parse(ByteBuffer buffer) {
        int size = buffer.getInt();
        ArrayList<Object> items = new ArrayList<Object>(size);
        for (int i = 0; i < size; i++) {
            items.add(itemParser.parse(buffer));
        }
        return items;
    }
}
