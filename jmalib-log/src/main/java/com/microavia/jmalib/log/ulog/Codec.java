package com.microavia.jmalib.log.ulog;

import com.microavia.jmalib.log.ulog.model.*;
import org.json.JSONArray;
import org.json.JSONObject;

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

    // -------------------------------------------------------------------------
    // v1/v2: plain-text struct definition  "typeName:field1Type field1Name;..."
    // -------------------------------------------------------------------------
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

    // -------------------------------------------------------------------------
    // v3+: JSON schema definition
    //
    // Supported type_class values: "struct", "enum"
    //
    // Struct example:
    //   {"type":"pkg/MyStruct","type_class":"struct",
    //    "fields":[{"name":"min","type":"uint32","comment":null}, ...],
    //    "size":12}
    //
    // Enum example:
    //   {"type":"pkg/MySeverity","type_class":"enum","base_type":"uint8",
    //    "comment":null,
    //    "values":[{"name":"error","value":0,"comment":null}, ...],
    //    "size":1}
    // -------------------------------------------------------------------------
    public void addTypeFromJson(String json) {
        JSONObject root = new JSONObject(json);

        String typeName  = root.getString("type");
        String typeClass = root.getString("type_class");

        switch (typeClass) {
            case "struct" -> addStructTypeFromJson(typeName, root);
            case "enum"   -> addEnumTypeFromJson(typeName, root);
            case "bitset" -> addBitsetTypeFromJson(typeName, root);
            default       -> throw new RuntimeException("Unsupported type_class in JSON schema: " + typeClass);
        }
    }

    private void addStructTypeFromJson(String typeName, JSONObject root) {
        JSONArray fieldsNode = root.getJSONArray("fields");
        var fields = new StructType.Field[fieldsNode.length()];
        for (int i = 0; i < fieldsNode.length(); i++) {
            JSONObject f = fieldsNode.getJSONObject(i);
            String fieldName     = f.getString("name");
            String fieldTypeName = f.getString("type");
            // Normalize built-in type names (e.g. "uint32_t" → "uint32")
            Type builtIn = getBuildInTypeDescription(fieldTypeName);
            if (builtIn != null) {
                fieldTypeName = builtIn.getTypeName();
            }
            fields[i] = new StructType.Field(fieldName, fieldTypeName);
        }
        typeDescriptions.put(typeName, new StructType(typeName, fields));
    }

    private void addEnumTypeFromJson(String typeName, JSONObject root) {
        String baseType = root.getString("base_type");
        // size may be null for variable-length types — not needed for parsing
        int size = root.isNull("size") ? 0 : root.getInt("size");
        JSONArray valuesNode = root.getJSONArray("values");
        var values = new EnumType.Value[valuesNode.length()];
        for (int i = 0; i < valuesNode.length(); i++) {
            JSONObject v = valuesNode.getJSONObject(i);
            values[i] = new EnumType.Value(v.getString("name"), parseIntValue(v, "value"));
        }
        typeDescriptions.put(typeName, new EnumType(typeName, baseType, size, values));
    }

    private void addBitsetTypeFromJson(String typeName, JSONObject root) {
        String baseType = root.getString("base_type");
        int size = root.isNull("size") ? 0 : root.getInt("size");
        JSONArray bitsNode = root.getJSONArray("bits");
        var bits = new BitsetType.Bit[bitsNode.length()];
        for (int i = 0; i < bitsNode.length(); i++) {
            JSONObject b = bitsNode.getJSONObject(i);
            bits[i] = new BitsetType.Bit(b.getString("name"), b.getInt("offset"));
        }
        typeDescriptions.put(typeName, new BitsetType(typeName, baseType, size, bits));
    }

    /**
     * Parse an integer value that may be a decimal int or a hex string like "0x0A".
     */
    private static int parseIntValue(JSONObject obj, String key) {
        Object val = obj.get(key);
        if (val instanceof String s) {
            return Integer.decode(s); // handles "0x00", "0xFF", etc.
        }
        return obj.getInt(key);
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