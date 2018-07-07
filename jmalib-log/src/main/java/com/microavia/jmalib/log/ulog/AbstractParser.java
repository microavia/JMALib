package com.microavia.jmalib.log.ulog;

import com.microavia.jmalib.log.ValueParser;

abstract class AbstractParser implements ValueParser {
    final LogParserContext context;
    int size = 0;

    AbstractParser(LogParserContext context) {
        this.context = context;
    }

    private static AbstractParser createFromTypeString(LogParserContext context, String typeString) {
        FieldParser field = FieldParser.create(context, typeString);
        if (field != null) {
            return field;
        }
        StructParser struct = context.getStructs().get(typeString);
        if (struct != null) {
            return struct.clone();
        }
        throw new RuntimeException("Unsupported type: " + typeString);
    }

    static AbstractParser createFromFormatString(LogParserContext context, String formatStr) {
        if (formatStr.contains("[")) {
            // Array
            String[] q = formatStr.split("\\[");
            String typeString = q[0];
            int arraySize = Integer.parseInt(q[1].split("]")[0]);
            if (typeString.equals("char") || typeString.equals("byte")) {
                // Array that parsed as field
                return FieldParser.create(context, typeString, arraySize);
            } else {
                return new ArrayParser(context, AbstractParser.createFromTypeString(context, typeString), arraySize);
            }
        } else {
            // Single value
            return createFromTypeString(context, formatStr);
        }
    }

    abstract public AbstractParser clone();

    int size() {
        return size;
    }

    abstract public void setOffset(int offset);
}
