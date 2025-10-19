package com.microavia.jmalib.log.ulog;

import com.microavia.jmalib.log.ulog.model.*;

import java.nio.ByteBuffer;

public class Subscription {
    private final Codec codec;
    private final String topicName;
    private final Type topicType;
    private final StructParser structParser;
    private final int multiIdFilter;
    private Object value;
    private boolean updated;

    public Subscription() {
        this.codec = null;
        this.topicName = null;
        this.topicType = null;
        this.structParser = null;
        this.multiIdFilter = -1;
    }

    Subscription(Codec codec, String topicName, Type topicType, int multiIdFilter) {
        this.codec = codec;
        this.topicName = topicName;
        this.topicType = topicType;
        this.structParser = (StructParser) codec.getValueParser(topicType);
        this.multiIdFilter = multiIdFilter;
    }

    public Getter createGetter(String path) throws SubscriptionException {
        return createGetter(new Getter(this, null, topicType), path);
    }

    private Getter createGetter(Getter parent, String path) throws SubscriptionException {
        if (!parent.isValid()) {
            throw new SubscriptionException("Parent getter is not valid when creating getter '" + path + "'");
        }
        switch (parent.getType().getTypeClass()) {
            case TypeClass.STRUCT: {
                String[] pathParts = path.split("[\\[\\.]", 2);
                String fieldName = pathParts[0];
                Getter fieldGetter = createFieldValueGetter(fieldName, (StructType) parent.type);

                if (fieldGetter == null) {
                    throw new SubscriptionException("Could not find field '" + fieldName + "' in struct '" + parent.getType().getTypeName() + "'");
                }

                Getter getter;
                if (parent.getterFunction == null) {
                    getter = fieldGetter;
                } else {
                    // Chain getter with parent
                    getter = new Getter(this, obj -> fieldGetter.getterFunction.get(parent.getterFunction.get(obj)), fieldGetter.getType());
                }

                if (pathParts.length == 1) {
                    // Return final value getter
                    return getter;
                }

                // Chain to next field
                return createGetter(getter, pathParts[1]);
            }
            case TypeClass.ARRAY:
            case TypeClass.VECTOR: {
                String[] idxParts = path.split("\\]", 2);
                int idx = Integer.parseInt(idxParts[0]);
                if (idx < 0) {
                    throw new SubscriptionException("Array index out of bounds: idx=" + idx + " < 0");
                }
                String elTypeStr;
                if (parent.getType().getTypeClass() == TypeClass.ARRAY) {
                    var arrType = (ArrayType) parent.getType();
                    if (idx >= arrType.getSize()) {
                        throw new SubscriptionException("Array index out of bounds: idx=" + idx + " >= " + arrType.getSize());
                    }
                    elTypeStr = arrType.getElementType();
                } else {
                    var vecType = (VectorType) parent.getType();
                    elTypeStr = vecType.getElementType();
                }
                var elType = codec.getTypeDescription(elTypeStr);
                var idxGetter = new Getter(this, obj -> ((Object[]) obj)[idx], elType);

                Getter getter;
                if (parent.getterFunction == null) {
                    getter = idxGetter;
                } else {
                    // Chain getter with parent
                    getter = new Getter(this, obj -> idxGetter.getterFunction.get(parent.getterFunction.get(obj)), idxGetter.getType());
                }

                if (idxParts.length == 1 || idxParts[1].isEmpty()) {
                    // Return final value getter
                    return getter;
                }
                // Chain to next field
                return createGetter(getter, idxParts[1]);
            }
        }
        throw new SubscriptionException("Invalid parent type class '" + parent.getType().getTypeClass() + "' when creating getter for path '" + path + "'");
    }

    private Getter createFieldValueGetter(String fieldName, StructType structType) {
        StructType.Field[] fields = structType.getFields();
        for (int i = 0; i < fields.length; i++) {
            var f = fields[i];
            if (f.name().equals(fieldName)) {
                final int fieldIdx = i;
                Getter.GetterFunction g = (obj) -> {
                    if (obj instanceof Object[] arr) {
                        return arr[fieldIdx];
                    }
                    return null;
                };
                var fieldType = codec.getTypeDescription(f.typeName());
                return new Getter(this, g, fieldType);
            }
        }
        return null;    // Field not found
    }

    public boolean update(ByteBuffer buffer, int multiId) {
        if (structParser != null && ((multiIdFilter == -1 && ((multiId & 0x80) != 0)) || (multiId & 0x7F) == multiIdFilter)) {
            value = structParser.parse(buffer);
            updated = true;
            return true;
        }
        return false;
    }

    public void update(Object val) {
        value = val;
        updated = true;
    }

    public String getTopicName() {
        return topicName;
    }

    public boolean isUpdated() {
        return updated;
    }

    public boolean isValid() {
        return structParser != null;
    }

    public Object getValue() {
        return value;
    }

    void clearUpdated() {
        updated = false;
    }
}
