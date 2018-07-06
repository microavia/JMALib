package com.microavia.jmalib.log.ulog;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

class LogParserContext {
    private static Charset charset = Charset.forName("latin1");
    private Map<String, StructParser> structs = new HashMap<>();

    Charset getCharset() {
        return charset;
    }

    Map<String, StructParser> getStructs() {
        return structs;
    }
}
