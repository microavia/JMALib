package com.microavia.jmalib.log.ulog.model;

public class VectorType extends Type {
    final String elementType;

    public VectorType(String elementType) {
        super(elementType + "[]", TypeClass.VECTOR);
        this.elementType = elementType;
    }

    public String getElementType() {
        return elementType;
    }
}
