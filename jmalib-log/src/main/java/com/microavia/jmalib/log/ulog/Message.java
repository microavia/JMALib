package com.microavia.jmalib.log.ulog;

import java.util.LinkedHashMap;

public class Message extends Struct {
    private final int msgID;

    public Message(LogParserContext context, Type[] fields, LinkedHashMap<String, Type> fieldsMap, int msgID) {
        super(context, fields, fieldsMap);
        this.msgID = msgID;
    }

    public Message(LogParserContext context, String formatStr, int msgID) {
        super(context, formatStr);
        this.msgID = msgID;
    }

    @Override
    public Type clone() {
        Struct c = (Struct) super.clone();
        return new Message(context, c.fields, c.fieldsMap, msgID);
    }

    public int getMsgID() {
        return msgID;
    }
}
