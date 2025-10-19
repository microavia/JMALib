package com.microavia.jmalib.log.ulog;

import java.nio.ByteBuffer;

interface Parser {
    Object parse(ByteBuffer buffer);
}
