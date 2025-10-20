package com.microavia.jmalib.log.ulog.model;

public class EnumType extends Type {
    public record Value(String name, int value) {
    }

    final String baseType;
    final int size;
    final Value[] values;

    public EnumType(String typeName, String baseType, int size, Value[] values) {
        super(typeName, TypeClass.ENUM);
        this.baseType = baseType;
        this.size = size;
        this.values = values;
    }

    public String getBaseType() {
        return baseType;
    }

    public int getSize() {
        return size;
    }

    public Value[] getValues() {
        return values;
    }
}
