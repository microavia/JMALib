package com.microavia.jmalib.log;

import java.nio.ByteBuffer;

public interface ValueParser {
    Object parse(ByteBuffer buffer);
}
