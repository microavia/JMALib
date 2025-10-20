package com.microavia.jmalib.log.ulog.model;

public class BitsetType extends Type {
    public record Bit(String name, int offset) {
    }

    final String baseType;
    final int size;
    final Bit[] bits;

    public BitsetType(String typeName, String baseType, int size, Bit[] bits) {
        super(typeName, TypeClass.BITSET);
        this.baseType = baseType;
        this.size = size;
        this.bits = bits;
    }

    public String getBaseType() {
        return baseType;
    }

    public int getSize() {
        return size;
    }

    public Bit[] getBits() {
        return bits;
    }
}
