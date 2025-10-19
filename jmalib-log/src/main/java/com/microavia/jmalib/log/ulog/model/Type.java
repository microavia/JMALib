package com.microavia.jmalib.log.ulog.model;

public class Type {
    final String typeName;
    final TypeClass typeClass;

    public Type(String typeName, TypeClass typeClass) {
        this.typeName = typeName;
        this.typeClass = typeClass;
    }

    public String getTypeName() {
        return typeName;
    }

    public TypeClass getTypeClass() {
        return typeClass;
    }
}
