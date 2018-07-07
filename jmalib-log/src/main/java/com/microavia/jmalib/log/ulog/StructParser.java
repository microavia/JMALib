package com.microavia.jmalib.log.ulog;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

class StructParser extends AbstractParser {
    private final LinkedHashMap<String, AbstractParser> fields;

    private StructParser(LogParserContext context, LinkedHashMap<String, AbstractParser> fields) {
        super(context);
        this.fields = fields;
        setOffset(0);
    }

    StructParser(LogParserContext context, String formatStr) {
        super(context);
        if (formatStr.length() > 1) {
            String[] fieldDescrs = formatStr.split(";");
            fields = new LinkedHashMap<>(fieldDescrs.length);
            for (String fieldDescr : fieldDescrs) {
                String[] p = fieldDescr.split(" ");
                String name = p[1];
                AbstractParser field = AbstractParser.createFromFormatString(context, p[0]);
                size += field.size();
                fields.put(name, field);
            }
        } else {
            fields = new LinkedHashMap<>(0);
        }
        setOffset(0);
    }

    LinkedHashMap<String, AbstractParser> getFields() {
        return fields;
    }

    AbstractParser get(String key) {
        return fields.get(key);
    }

    @Override
    public AbstractParser clone() {
        LinkedHashMap<String, AbstractParser> fieldsClone = new LinkedHashMap<>();
        for (Map.Entry<String, AbstractParser> e : fields.entrySet()) {
            fieldsClone.put(e.getKey(), e.getValue().clone());
        }
        return new StructParser(context, fieldsClone);
    }

    @Override
    public void setOffset(int offset) {
        size = 0;
        for (AbstractParser field : fields.values()) {
            field.setOffset(offset);
            offset += field.size();
            size += field.size();
        }
    }

    @Override
    public Object parse(ByteBuffer buffer) {
        LinkedHashMap<String, Object> res = new LinkedHashMap<>(fields.size());
        for (Map.Entry<String, AbstractParser> e : fields.entrySet()) {
            res.put(e.getKey(), e.getValue().parse(buffer));
        }
        return res;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        for (Map.Entry<String, AbstractParser> e : fields.entrySet()) {
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
