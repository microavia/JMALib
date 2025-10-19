package com.microavia.jmalib.log.ulog;

import java.nio.ByteBuffer;

class BytesParser implements Parser {
    @Override
    public Object parse(ByteBuffer buffer) {
        int size = buffer.getInt();
        byte[] strBuf = new byte[size];
        return buffer.get(strBuf);
    }
}
