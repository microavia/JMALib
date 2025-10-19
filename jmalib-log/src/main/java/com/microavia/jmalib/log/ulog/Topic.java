package com.microavia.jmalib.log.ulog;

class Topic {
    private final String name;
    private final String typeName;
    private final int id;

    Topic(String name, String structType, int id) {
        this.name = name;
        this.typeName = structType;
        this.id = id;
    }

    public String getName() {
        return name;
    }

    String getTypeName() {
        return typeName;
    }

    int getId() {
        return id;
    }
}
