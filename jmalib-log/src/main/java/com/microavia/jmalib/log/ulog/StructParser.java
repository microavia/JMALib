package com.microavia.jmalib.log.ulog;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

public class StructParser extends AbstractParser {
    public final AbstractParser[] fields;
    public final LinkedHashMap<String, AbstractParser> fieldsMap;

    public StructParser(LogParserContext context, AbstractParser[] fields, LinkedHashMap<String, AbstractParser> fieldsMap) {
        super(context);
        this.fields = fields;
        this.fieldsMap = fieldsMap;
        setOffset(0);
    }

    public StructParser(LogParserContext context, String formatStr) {
        super(context);
        if (formatStr.length() > 1) {
            String[] fieldDescrs = formatStr.split(";");
            fields = new AbstractParser[fieldDescrs.length];
            fieldsMap = new LinkedHashMap<>(fieldDescrs.length);
            for (int i = 0; i < fieldDescrs.length; i++) {
                String fieldDescr = fieldDescrs[i];
                String[] p = fieldDescr.split(" ");
                String name = p[1];
                AbstractParser field = AbstractParser.createFromFormatString(context, p[0]);
                size += field.size();
                fields[i] = field;
                fieldsMap.put(name, field);
            }
        } else {
            fields = new FieldParser[0];
            fieldsMap = new LinkedHashMap<>();
        }
        setOffset(0);
    }

    public LinkedHashMap<String, AbstractParser> getFields() {
        return fieldsMap;
    }

    public AbstractParser get(String key) {
        return fieldsMap.get(key);
    }

    @Override
    public AbstractParser clone() {
        AbstractParser[] fieldsClone = new AbstractParser[fields.length];
        LinkedHashMap<String, AbstractParser> fieldsMapClone = new LinkedHashMap<>();
        int i = 0;
        for (Map.Entry<String, AbstractParser> e : fieldsMap.entrySet()) {
            AbstractParser t = fields[i].clone();
            fieldsClone[i] = t;
            fieldsMapClone.put(e.getKey(), t);
            ++i;
        }
        return new StructParser(context, fieldsClone, fieldsMapClone);
    }

    @Override
    public void setOffset(int offset) {
        size = 0;
        for (AbstractParser field : fields) {
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
        sb.append("{\n");
        for (Map.Entry<String, AbstractParser> e : fieldsMap.entrySet()) {
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
