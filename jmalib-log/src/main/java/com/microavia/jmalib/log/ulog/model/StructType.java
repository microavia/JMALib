package com.microavia.jmalib.log.ulog.model;

public class StructType extends Type {
    public record Field(String name, String typeName) {
    }

    private final Field[] fields;

    public StructType(String typeName, Field[] fields) {
        super(typeName, TypeClass.STRUCT);
        this.fields = fields;
    }

    public Field[] getFields() {
        return fields;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("struct ").append(getTypeName()).append(":\n");
        for (Field field : fields) {
            sb.append(" - ").append(field.typeName).append(" ").append(field.name).append("\n");
        }
        return sb.toString();
    }
}
