package com.microavia.jmalib.log.ulog;

public class Message {
    private final String name;
    private final StructParser struct;
    private final int id;

    public Message(String name, StructParser struct, int id) {
        this.name = name;
        this.struct = struct;
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public StructParser getStruct() {
        return struct;
    }

    public int getId() {
        return id;
    }
}
