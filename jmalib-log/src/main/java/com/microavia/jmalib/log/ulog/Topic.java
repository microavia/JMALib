package com.microavia.jmalib.log.ulog;

class Topic {
    private final String name;
    private final StructParser struct;
    private final int id;

    Topic(String name, StructParser struct, int id) {
        this.name = name;
        this.struct = struct;
        this.id = id;
    }

    public String getName() {
        return name;
    }

    StructParser getStruct() {
        return struct;
    }

    int getId() {
        return id;
    }
}
