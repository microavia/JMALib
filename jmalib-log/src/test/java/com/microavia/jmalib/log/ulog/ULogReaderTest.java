package com.microavia.jmalib.log.ulog;

import com.microavia.jmalib.log.FormatErrorException;
import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.io.IOException;

class ULogReaderTest {
    @Test
    void reader() throws FormatErrorException, IOException {
        ULogReader reader = new ULogReader("test.ulg");
        for (var err : reader.getErrors()) {
            System.out.println(err.toString());
        }
        var sub = reader.addSubscription("ATTITUDE_POSITION");
        String altElPath = "alt_el";
        String altBaroPath = "alt_baro";
        var altElGetter = sub.createGetter(altElPath);
        var altBaroGetter = sub.createGetter(altBaroPath);
        reader.seek(0);
        long tStart = System.currentTimeMillis();
        try {
            while (true) {
                long t = reader.readUpdate();
                if (altElGetter.isUpdated() || altBaroGetter.isUpdated()) {
                    System.out.printf("%d alt_el=%s alt_baro=%s%n", t, altElGetter.get(), altBaroGetter.get());
                }
            }
        } catch (EOFException ignored) {
        }
        long tEnd = System.currentTimeMillis();
        for (Exception e : reader.getErrors()) {
            e.printStackTrace();
        }
        System.out.println(tEnd - tStart);
        reader.close();
    }
}
