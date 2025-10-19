package com.microavia.jmalib.log.ulog;

import java.nio.ByteBuffer;

class StringParser implements Parser {
    private final Codec codec;

    StringParser(Codec codec) {
        this.codec = codec;
    }

    @Override
    public Object parse(ByteBuffer buffer) {
        int size = buffer.getInt();
        byte[] strBuf = new byte[size];
        buffer.get(strBuf);
        return new String(strBuf, codec.getCharset());
    }
}
