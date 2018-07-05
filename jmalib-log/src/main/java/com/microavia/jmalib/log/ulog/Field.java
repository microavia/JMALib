package com.microavia.jmalib.log.ulog;

import com.microavia.jmalib.log.ValueParser;

import java.nio.ByteBuffer;

/**
 * Created by ton on 26.10.15.
 */
class Field extends Type {
    public final String type;
    protected int offset = 0;
    private ValueParser valueParser;

    public Field(LogParserContext context, String type, ValueParser parser, int size) {
        super(context);
        this.type = type;
        this.valueParser = parser;
        this.size = size;
    }

    public static Field create(LogParserContext context, String typeString) {
        ValueParser valueParser;
        int size;
        switch (typeString) {
            case "float":
                valueParser = ByteBuffer::getFloat;
                size = 4;
                break;
            case "double":
                valueParser = ByteBuffer::getDouble;
                size = 8;
                break;
            case "int8_t":
            case "bool":
                valueParser = b -> (int) b.get();
                size = 1;
                break;
            case "uint8_t":
                valueParser = b -> b.get() & 0xFF;
                size = 1;
                break;
            case "int16_t":
                valueParser = b -> (int) b.getShort();
                size = 2;
                break;
            case "uint16_t":
                valueParser = b -> b.getShort() & 0xFFFF;
                size = 2;
                break;
            case "int32_t":
                valueParser = ByteBuffer::getInt;
                size = 4;
                break;
            case "uint32_t":
                valueParser = b -> b.getInt() & 0xFFFFFFFFL;
                size = 4;
                break;
            case "int64_t":
                valueParser = ByteBuffer::getLong;
                size = 8;
                break;
            case "uint64_t":
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
        return new Field(context, typeString, valueParser, size);
    }

    public static Field create(LogParserContext context, String typeString, int size) {
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
        return new Field(context, typeString + "[]", valueParser, size);
    }

    @Override
    public Type clone() {
        return new Field(context, type, valueParser, size);
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
