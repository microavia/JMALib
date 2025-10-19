package com.microavia.jmalib.log.ulog.model;

public class ArrayType extends Type {
    final String elementType;
    final int size;

    public ArrayType(String elementType, int size) {
        super(elementType + "[" + size + "]", TypeClass.ARRAY);
        this.elementType = elementType;
        this.size = size;
    }

    public String getElementType() {
        return elementType;
    }

    public int getSize() {
        return size;
    }
}
