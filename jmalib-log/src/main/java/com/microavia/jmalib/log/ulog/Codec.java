package com.microavia.jmalib.log.ulog;

import com.microavia.jmalib.log.ulog.model.*;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

public class Codec {
    private static Charset charset = Charset.forName("latin1");
    private Map<String, Type> typeDescriptions = new HashMap<>();

    Charset getCharset() {
        return charset;
    }

    public void addStructType(String typeName, String fieldsListStr) {
        var fieldsStr = fieldsListStr.split(";");
        var fields = new StructType.Field[fieldsStr.length];
        for (int i = 0; i < fieldsStr.length; i++) {
            var fs = fieldsStr[i];
            var p = fs.split(" ", 2);
            if (p.length == 2) {
                var fieldTypeStr = p[0];
                var fieldType = getBuildInTypeDescription(fieldTypeStr);
                if (fieldType != null) {
                    // Normalize name of built-in type
                    fieldTypeStr = fieldType.getTypeName();
                }
                fields[i] = new StructType.Field(p[1], fieldTypeStr);
            } else {
                throw new RuntimeException("Error parsing struct field: " + fs);
            }
        }
        StructType structType = new StructType(typeName, fields);
        typeDescriptions.put(typeName, structType);
    }

    public Type getTypeDescription(String typeName) {
        if (typeName.endsWith("]")) {
            // Array or vector typeName
            int idx = typeName.indexOf('[');
            String elementType = typeName.substring(0, idx);
            String sizeStr = typeName.substring(idx + 1, typeName.length() - 1);
            if (sizeStr.isEmpty()) {
                return new VectorType(elementType);
            } else {
                int size = Integer.parseInt(sizeStr);
                return new ArrayType(elementType, size);
            }
        }

        // Built-in types
        Type descrBuiltin = getBuildInTypeDescription(typeName);
        if (descrBuiltin != null) {
            return descrBuiltin;
        }

        // User-defined types or null if not found
        return typeDescriptions.get(typeName);
    }

    public Parser getValueParser(String typeName) {
        Type descr = getTypeDescription(typeName);
        if (descr == null) {
            return null;
        }

        return getValueParser(descr);
    }

    public Parser getValueParser(Type descr) {
        return switch (descr.getTypeClass()) {
            case SCALAR -> getScalarParser(descr.getTypeName());
            case ENUM -> getScalarParser(((EnumType) descr).getBaseType());
            case BITSET -> getScalarParser(((BitsetType) descr).getBaseType());
            case BYTES -> new BytesParser();
            case STRING -> new StringParser(this);
            case ARRAY -> {
                ArrayType arrDescr = (ArrayType) descr;
                yield new ArrayParser(getValueParser(arrDescr.getElementType()), arrDescr.getSize());
            }
            case VECTOR -> {
                VectorType vecDescr = (VectorType) descr;
                yield new VectorParser(getValueParser(vecDescr.getElementType()));
            }

            case STRUCT -> {
                StructType structDescr = (StructType) descr;
                Parser[] fields = new Parser[structDescr.getFields().length];
                for (int i = 0; i < structDescr.getFields().length; i++) {
                    var field = structDescr.getFields()[i];
                    fields[i] = getValueParser(field.typeName());
                }
                yield new StructParser(fields);
            }
            default -> null;
        };
    }

    Type getBuildInTypeDescription(String typeName) {
        return switch (typeName) {
            case "float", "float32" -> new Type("float32", TypeClass.SCALAR);
            case "double", "float64" -> new Type("float64", TypeClass.SCALAR);
            case "char", "int8_t", "int8" -> new Type("int8", TypeClass.SCALAR);
            case "bool" -> new Type("bool", TypeClass.SCALAR);
            case "uint8_t", "uint8" -> new Type("uint8", TypeClass.SCALAR);
            case "int16_t", "int16" -> new Type("int16", TypeClass.SCALAR);
            case "uint16_t", "uint16" -> new Type("uint16", TypeClass.SCALAR);
            case "int32_t", "int32" -> new Type("int32", TypeClass.SCALAR);
            case "uint32_t", "uint32" -> new Type("uint32", TypeClass.SCALAR);
            case "int64_t", "int64" -> new Type("int64", TypeClass.SCALAR);
            case "uint64_t", "uint64" -> new Type("uint64", TypeClass.SCALAR);
            case "string" -> new Type("string", TypeClass.STRING);
            case "bytes" -> new Type("bytes", TypeClass.BYTES);
            default -> null;
        };
    }

    static Parser getScalarParser(String type) {
        return switch (type) {
            case "float32" -> ByteBuffer::getFloat;
            case "float64" -> ByteBuffer::getDouble;
            case "int8" -> b -> (int) b.get();
            case "bool" -> b -> b.get() != 0;
            case "uint8" -> b -> b.get() & 0xFF;
            case "int16" -> b -> (int) b.getShort();
            case "uint16" -> b -> b.getShort() & 0xFFFF;
            case "int32" -> ByteBuffer::getInt;
            case "uint32" -> b -> b.getInt() & 0xFFFFFFFFL;
            case "int64", "uint64" -> ByteBuffer::getLong;
            default -> (b) -> {
                throw new RuntimeException("Unsupported scalar type: " + type);
            };
        };
    }

    public String objectToString(Object obj) {
        if (obj instanceof String str) {
            return str;
        }
        if (obj instanceof Object[] arr) {
            byte[] bs = new byte[arr.length];
            int n;
            for (n = 0; n < arr.length; n++) {
                var b = (byte) ((Integer) arr[n]).intValue();
                if (b == 0) {
                    break;
                }
                bs[n] = b;
            }
            return new String(bs, 0, n, charset);
        }
        return obj.toString();
    }
}
