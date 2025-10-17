package com.microavia.jmalib.log.ulog;

import com.microavia.jmalib.log.ValueParser;

import java.nio.ByteBuffer;

/**
 * Created by ton on 26.10.15.
 */
class FieldParser extends AbstractParser {
    private final String type;
    private int offset = 0;
    private ValueParser valueParser;

    private FieldParser(LogParserContext context, String type, ValueParser parser, int size) {
        super(context);
        this.type = type;
        this.valueParser = parser;
        this.size = size;
    }

    static FieldParser create(LogParserContext context, String typeString) {
        ValueParser valueParser;
        int size;
        switch (typeString) {
            case "float":
            case "float32":
                valueParser = ByteBuffer::getFloat;
                size = 4;
                break;
            case "double":
            case "float64":
                valueParser = ByteBuffer::getDouble;
                size = 8;
                break;
            case "int8_t":
            case "int8":
            case "bool":
                valueParser = b -> (int) b.get();
                size = 1;
                break;
            case "uint8_t":
            case "uint8":
                valueParser = b -> b.get() & 0xFF;
                size = 1;
                break;
            case "int16_t":
            case "int16":
                valueParser = b -> (int) b.getShort();
                size = 2;
                break;
            case "uint16_t":
            case "uint16":
                valueParser = b -> b.getShort() & 0xFFFF;
                size = 2;
                break;
            case "int32_t":
            case "int32":
                valueParser = ByteBuffer::getInt;
                size = 4;
                break;
            case "uint32_t":
            case "uint32":
                valueParser = b -> b.getInt() & 0xFFFFFFFFL;
                size = 4;
                break;
            case "int64_t":
            case "int64":
                valueParser = ByteBuffer::getLong;
                size = 8;
                break;
            case "uint64_t":
            case "uint64":
                valueParser = ByteBuffer::getLong;
                size = 8;
                break;
            case "char":
                valueParser = ByteBuffer::get;
                size = 1;
                break;
            default:
                return null;
        }
        return new FieldParser(context, typeString, valueParser, size);
    }

    static FieldParser create(LogParserContext context, String typeString, int size) {
        ValueParser valueParser;
        switch (typeString) {
            case "char": {
                valueParser = buffer -> {
                    byte[] buf = new byte[size];
                    buffer.get(buf);
                    String[] p = new String(buf, context.getCharset()).split("\0");
                    return p.length > 0 ? p[0] : "";
                };
                break;
            }
            case "byte": {
                valueParser = buffer -> {
                    byte[] buf = new byte[size];
                    buffer.get(buf);
                    return buf;
                };
                break;
            }
            default:
                return null;
        }
        return new FieldParser(context, typeString + "[]", valueParser, size);
    }

    String getType() {
        return type;
    }

    @Override
    public AbstractParser clone() {
        return new FieldParser(context, type, valueParser, size);
    }

    @Override
    public void setOffset(int offset) {
        this.offset = offset;
    }

    @Override
    public Object parse(ByteBuffer buffer) {
        int pos = buffer.position();
        buffer.position(pos + offset);
        Object v = valueParser.parse(buffer);
        buffer.position(pos);
        return v;
    }

    @Override
    public String toString() {
        return String.format("%s (offset=%s size=%s)", type, offset, size);
    }
}
