package com.microavia.jmalib.log.ulog;

import java.nio.ByteBuffer;

public class ArrayParser extends AbstractParser {
    private final LogParserContext context;
    private final AbstractParser[] items;

    public ArrayParser(LogParserContext context, AbstractParser itemType, int arraySize) {
        super(context);
        this.context = context;
        this.size = itemType.size() * arraySize;
        this.items = new AbstractParser[arraySize];
        for (int i = 0; i < arraySize; i++) {
            this.items[i] = itemType.clone();
        }
        setOffset(0);
    }

    public AbstractParser[] getItems() {
        return items;
    }

    public AbstractParser get(int idx) {
        return items[idx];
    }

    @Override
    public AbstractParser clone() {
        return new ArrayParser(context, items[0], items.length);
    }

    @Override
    public void setOffset(int offset) {
        for (AbstractParser item : items) {
            item.setOffset(offset);
            offset += item.size();
        }
    }

    @Override
    public Object parse(ByteBuffer buffer) {
        Object[] v = new Object[items.length];
        for (int i = 0; i < items.length; i++) {
            v[i] = items[i].parse(buffer);
        }
        return v;
    }

    @Override
    public String toString() {
        return String.format("%s[%s]", items[0].toString(), items.length);
    }
}
