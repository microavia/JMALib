package com.microavia.jmalib.log.ulog;

import com.microavia.jmalib.log.ulog.model.StructType;
import com.microavia.jmalib.log.ulog.model.TypeClass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CodecTest {
    static Codec createTestCodec() {
        Codec codec = new Codec();
        codec.addStructType("foo_struct", "bar_struct bar;uint32[2] arr;uint16[] vec;uint64 i64;");
        codec.addStructType("bar_struct", "uint32 a");
        return codec;
    }

    @Test
    void modelParse() {
        var codec = createTestCodec();
        var foo_type = codec.getTypeDescription("foo_struct");
        assertEquals(TypeClass.STRUCT, foo_type.getTypeClass());

        var fields = ((StructType) foo_type).getFields();
        assertEquals(4, fields.length);

        assertEquals("bar", fields[0].name());
        assertEquals("bar_struct", fields[0].typeName());
        assertEquals(TypeClass.STRUCT, codec.getTypeDescription(fields[0].typeName()).getTypeClass());

        assertEquals("arr", fields[1].name());
        assertEquals("uint32[2]", fields[1].typeName());
        assertEquals(TypeClass.ARRAY, codec.getTypeDescription(fields[1].typeName()).getTypeClass());

        assertEquals("vec", fields[2].name());
        assertEquals("uint16[]", fields[2].typeName());
        assertEquals(TypeClass.VECTOR, codec.getTypeDescription(fields[2].typeName()).getTypeClass());

        assertEquals("i64", fields[3].name());
        assertEquals("uint64", fields[3].typeName());
        assertEquals(TypeClass.SCALAR, codec.getTypeDescription(fields[3].typeName()).getTypeClass());
    }

    @Test
    void getter() {
        Codec codec = createTestCodec();
        var fooType = codec.getTypeDescription("foo_struct");
        var sub = new Subscription(codec, "FOO_TOPIC", fooType);
        var aGetter = sub.createGetter("bar.a");
        var arrGetter = sub.createGetter("arr[0]");
        var vecGetter = sub.createGetter("vec[1]");
        var fooObj = new Object[]{
                new Object[]{ // bar
                        123, // a
                },
                new Object[]{1, 2}, // arr
                new Object[]{3, 4, 5}, // vec
                100 // i64
        };
        sub.update(fooObj, -1);
        assertEquals(123, aGetter.get());
        assertEquals(1, arrGetter.get());
        assertEquals(4, vecGetter.get());
    }
}
