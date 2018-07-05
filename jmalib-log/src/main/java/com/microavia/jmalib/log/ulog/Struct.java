package com.microavia.jmalib.log.ulog;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

public class Struct extends Type {
    public final Type[] fields;
    public final LinkedHashMap<String, Type> fieldsMap;

    public Struct(LogParserContext context, Type[] fields, LinkedHashMap<String, Type> fieldsMap) {
        super(context);
        this.fields = fields;
        this.fieldsMap = fieldsMap;
        setOffset(0);
    }

    public Struct(LogParserContext context, String formatStr) {
        super(context);
        if (formatStr.length() > 1) {
            String[] fieldDescrs = formatStr.split(";");
            fields = new Type[fieldDescrs.length];
            fieldsMap = new LinkedHashMap<>(fieldDescrs.length);
            for (int i = 0; i < fieldDescrs.length; i++) {
                String fieldDescr = fieldDescrs[i];
                String[] p = fieldDescr.split(" ");
                String name = p[1];
                Type field = Type.createFromFormatString(context, p[0]);
                size += field.size();
                fields[i] = field;
                fieldsMap.put(name, field);
            }
        } else {
            fields = new Field[0];
            fieldsMap = new LinkedHashMap<>();
        }
        setOffset(0);
    }

    public LinkedHashMap<String, Type> getFields() {
        return fieldsMap;
    }

    public Type get(String key) {
        return fieldsMap.get(key);
    }

    @Override
    public Type clone() {
        Type[] fieldsClone = new Type[fields.length];
        LinkedHashMap<String, Type> fieldsMapClone = new LinkedHashMap<>();
        int i = 0;
        for (Map.Entry<String, Type> e : fieldsMap.entrySet()) {
            Type t = fields[i].clone();
            fieldsClone[i] = t;
            fieldsMapClone.put(e.getKey(), t);
            ++i;
        }
        return new Struct(context, fieldsClone, fieldsMapClone);
    }

    @Override
    public void setOffset(int offset) {
        size = 0;
        for (Type field : fields) {
            field.setOffset(offset);
            offset += field.size();
            size += field.size();
        }
    }

    @Override
    public Object parse(ByteBuffer buffer) {
        Object[] data = new Object[fields.length];
        for (int i = 0; i < fields.length; ++i) {
            data[i] = fields[i].parse(buffer);
        }
        return data;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Struct {\n");
        for (Map.Entry<String, Type> e : fieldsMap.entrySet()) {
            sb.append("    ");
            sb.append(e.getValue().toString());
            sb.append(" ");
            sb.append(e.getKey());
            sb.append("\n");
        }
        sb.append("}");
        return sb.toString();
    }
}
