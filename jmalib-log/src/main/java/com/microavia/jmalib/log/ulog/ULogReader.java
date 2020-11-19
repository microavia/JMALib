package com.microavia.jmalib.log.ulog;

import com.microavia.jmalib.log.BinaryLogReader;
import com.microavia.jmalib.log.FormatErrorException;
import com.microavia.jmalib.log.Subscription;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.*;

/**
 * User: ton Date: 03.06.13 Time: 14:18
 */
public class ULogReader extends BinaryLogReader {
    private static final byte SYNC_BYTE = (byte) '>';
    private static final byte MESSAGE_TYPE_STRUCT = (byte) 'F';
    private static final byte MESSAGE_TYPE_TOPIC = (byte) 'A';
    private static final byte MESSAGE_TYPE_DATA = (byte) 'D';
    private static final byte MESSAGE_TYPE_INFO = (byte) 'I';
    private static final byte MESSAGE_TYPE_PARAMETER = (byte) 'P';

    private String systemName = "";
    private String systemConfig = "";
    private long dataStart = 0;
    private Map<String, Topic> topicByName = new HashMap<>();
    private Map<String, String> fieldsList = null;
    private Map<Integer, List<Subscription>> subscriptions = new HashMap<>();
    private Set<Subscription> updatedSubscriptions = new HashSet<>();
    private long sizeUpdates = -1;
    private long sizeMicroseconds = -1;
    private long startMicroseconds = -1;
    private long timeLast = Long.MIN_VALUE;
    private long utcTimeReference = -1;
    private Map<String, Object> version = new HashMap<>();
    private Map<String, Object> parameters = new HashMap<>();
    private List<Exception> errors = new ArrayList<>();
    private int logVersion = 0;
    private int headerSize = 4;
    private int msgDataTimestampOffset = 3;
    private LogParserContext context = new LogParserContext();

    public ULogReader(String fileName) throws IOException, FormatErrorException {
        super(fileName);
        updateStatistics();
    }

    public static void main(String[] args) throws Exception {
        ULogReader reader = new ULogReader("test_long_v1.ulg");
        reader.addSubscription("ATTITUDE_POSITION.alt_baro");
        reader.seek(0);
        long tStart = System.currentTimeMillis();
        try {
            while (true) {
                long t = reader.readUpdate();
                for (Subscription s : reader.getUpdatedSubscriptions()) {
                    System.out.println(t + " " + s.getPath() + " " + s.getValue());
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

    @Override
    public String getFormat() {
        return "ULog v" + logVersion;
    }

    @Override
    public String getSystemName() {
        return systemName;
    }

    @Override
    public String getSystemConfig() {
        return systemConfig;
    }

    @Override
    public long getSizeUpdates() {
        return sizeUpdates;
    }

    @Override
    public long getStartMicroseconds() {
        return startMicroseconds;
    }

    @Override
    public long getSizeMicroseconds() {
        return sizeMicroseconds;
    }

    @Override
    public long getUTCTimeReferenceMicroseconds() {
        return utcTimeReference;
    }

    @Override
    public Map<String, Object> getVersion() {
        return version;
    }

    @Override
    public Map<String, Object> getParameters() {
        return parameters;
    }

    private void updateStatistics() throws IOException, FormatErrorException {
        position(0);
        fillBuffer(4);
        byte[] logVersionBytes = new byte[4];
        buffer.get(logVersionBytes);
        String logVersionStr = new String(logVersionBytes, Charset.forName("latin1"));
        if (logVersionStr.startsWith("ULG")) {
            logVersion = Integer.parseInt(logVersionStr.substring(3));
            headerSize = 4;
            if (logVersion >= 2) {
                msgDataTimestampOffset = 3;
            } else {
                msgDataTimestampOffset = 2;
            }
        } else {
            throw new FormatErrorException("Unsupported file format");
        }
        startMicroseconds = -1;
        sizeUpdates = 0;
        fieldsList = new HashMap<>();
        timeLast = Long.MIN_VALUE;
        try {
            while (true) {
                readMessage(this::handleHeaderMessage);
            }
        } catch (EOFException ignored) {
        }
        sizeMicroseconds = timeLast - startMicroseconds;
        seek(0);
    }

    public Subscription addSubscription(String path) {
        String[] parts = path.split("\\.");
        Topic msg = null;
        int multiIdFilter = -1;
        AbstractParser parser = null;
        for (String p : parts) {
            if (msg == null) {
                String[] pp = p.split("\\[");
                if (pp.length > 1) {
                    multiIdFilter = Integer.parseInt(pp[1].split("]")[0]);
                }
                msg = topicByName.get(pp[0]);
                parser = msg.getStruct();
            } else {
                if (parser instanceof StructParser) {
                    String[] pp = p.split("\\[");
                    if (pp.length > 1) {
                        parser = ((StructParser) parser).get(pp[0]);
                        try {
                            int idx = Integer.parseInt(pp[1].split("]")[0]);
                            if (parser instanceof ArrayParser) {
                                parser = ((ArrayParser) parser).get(idx);
                            }
                        } catch (Exception ignored) {
                        }
                    } else {
                        parser = ((StructParser) parser).get(p);
                    }
                } else {
                    break;
                }
            }
        }
        if (msg != null && parser != null) {
            Subscription subscription = new Subscription(path, parser, multiIdFilter);
            subscriptions.putIfAbsent(msg.getId(), new ArrayList<>());
            subscriptions.get(msg.getId()).add(subscription);
            return subscription;
        } else {
            return new Subscription(path, null, -1);
        }
    }

    @Override
    public void removeAllSubscriptions() {
        subscriptions.clear();
        updatedSubscriptions.clear();
    }

    @Override
    public long readUpdate() throws IOException {
        updatedSubscriptions.clear();
        timeLast = Long.MIN_VALUE;
        do {
            readMessage(this::handleDataMessage);
        } while (timeLast == Long.MIN_VALUE);
        return timeLast;
    }

    @Override
    public Set<Subscription> getUpdatedSubscriptions() {
        return updatedSubscriptions;
    }

    @Override
    public boolean seek(long seekTime) throws IOException {
        position(dataStart);
        timeLast = Long.MIN_VALUE;
        if (seekTime == 0) {      // Seek to start of log
            return true;
        }
        // Seek to specified timestamp without parsing all messages
        try {
            while (timeLast < seekTime) {
                readMessage((pos, msgType, msgSize) -> {
                    if (msgType == MESSAGE_TYPE_DATA) {
                        timeLast = buffer.getLong(buffer.position() + msgDataTimestampOffset);
                        if (timeLast >= seekTime) {
                            // Time found, reset buffer to start of the message
                            position(pos);
                            return;
                        }
                    }
                    buffer.position(buffer.position() + msgSize);
                });
            }
            return true;
        } catch (EOFException e) {
            return false;
        }
    }

    @Override
    public Map<String, String> getFields() {
        return fieldsList;
    }

    /**
     * Read and handle next message from log
     *
     * @throws IOException  on IO error
     * @throws EOFException on end of stream
     */
    private void readMessage(MessageHandler handler) throws IOException {
        while (true) {
            fillBuffer(headerSize);
            long pos = position();
            byte sync = buffer.get();
            if (sync != SYNC_BYTE) {
                errors.add(new FormatErrorException(pos, String.format("Wrong sync byte: 0x%02X (expected 0x%02X)", sync & 0xFF, SYNC_BYTE & 0xFF)));
                continue;
            }
            int msgType = buffer.get() & 0xFF;
            int msgSize = buffer.getShort() & 0xFFFF;
            try {
                fillBuffer(msgSize);
            } catch (EOFException e) {
                errors.add(new FormatErrorException(pos, "Unexpected end of file"));
                throw e;
            }
            try {
                handler.handleMessage(pos, msgType, msgSize);
            } catch (Exception e) {
                errors.add(new FormatErrorException(pos, "Error parsing message type: " + msgType, e));
            }
            return;
        }
    }

    private void handleHeaderMessage(long pos, int msgType, int msgSize) throws IOException {
        switch (msgType) {
            case MESSAGE_TYPE_DATA: {
                if (dataStart == 0) {
                    dataStart = pos;
                }
                long timestamp = buffer.getLong(buffer.position() + msgDataTimestampOffset);
                if (startMicroseconds < 0) {
                    startMicroseconds = timestamp;
                }
                timeLast = timestamp;
                ++sizeUpdates;
                buffer.position(buffer.position() + msgSize);
                break;
            }
            case MESSAGE_TYPE_STRUCT: {
                if (logVersion <= 1) {
                    int msgId = buffer.get() & 0xFF;
                    int formatLen = buffer.getShort() & 0xFFFF;
                    String[] descr = getString(buffer, formatLen).split(":");
                    String name = descr[0];
                    if (descr.length > 1) {
                        StructParser struct = null;
                        try {
                            struct = new StructParser(context, descr[1]);
                        } catch (FormatErrorException e) {
                            errors.add(new FormatErrorException(pos, String.format("Error parsing type definition: %s", e.toString()), e));
                            break;
                        }
                        context.getStructs().put(name, struct);
                        topicByName.put(name, new Topic(name, struct, msgId));
                        addFieldsToList(name, struct);
                    }
                } else {
                    String[] descr = getString(buffer, msgSize).split(":");
                    String name = descr[0];
                    if (descr.length > 1) {
                        StructParser struct = null;
                        try {
                            struct = new StructParser(context, descr[1]);
                        } catch (Exception e) {
                            errors.add(new FormatErrorException(pos, String.format("Error parsing struct: %s", e.toString()), e));
                            break;
                        }
                        context.getStructs().put(name, struct);
                    }
                }
                break;
            }
            case MESSAGE_TYPE_TOPIC: {
                int msgId = buffer.getShort() & 0xFFFF;
                String[] descr = getString(buffer, msgSize - 2).split(":");
                String name = descr[0];
                String structName = descr[1];
                StructParser struct = context.getStructs().get(structName);
                Topic topic = new Topic(name, struct, msgId);
                topicByName.put(name, topic);
                addFieldsToList(name, struct);
                break;
            }
            case MESSAGE_TYPE_INFO: {
                int keyLen = buffer.get() & 0xFF;
                String[] keyDescr = getString(buffer, keyLen).split(" ");
                String key = keyDescr[1];
                AbstractParser field = null;
                try {
                    field = AbstractParser.createFromFormatString(context, keyDescr[0]);
                } catch (FormatErrorException e) {
                    errors.add(new FormatErrorException(pos, "Error parsing info", e));
                    break;
                }
                Object value = field.parse(buffer);
                buffer.position(buffer.position() + field.size());
                switch (key) {
                    case "sys_name":
                        systemName = (String) value;
                        break;
                    case "sys_config":
                        systemConfig = (String) value;
                        break;
                    case "ver_hw":
                        version.put("HW", value);
                        break;
                    case "ver_sw":
                        version.put("FW", value);
                        break;
                    case "time_ref_utc":
                        utcTimeReference = ((Number) value).longValue();
                        break;
                }
                break;
            }
            case MESSAGE_TYPE_PARAMETER: {
                int keyLen = buffer.get() & 0xFF;
                String[] keyDescr = getString(buffer, keyLen).split(" ");
                String key = keyDescr[1];
                AbstractParser field = null;
                try {
                    field = AbstractParser.createFromFormatString(context, keyDescr[0]);
                } catch (FormatErrorException e) {
                    errors.add(new FormatErrorException(pos, "Error parsing parameter", e));
                    break;
                }
                Object value = field.parse(buffer);
                buffer.position(buffer.position() + field.size());
                parameters.put(key, value);
                break;
            }
            default:
                buffer.position(buffer.position() + msgSize);
                errors.add(new FormatErrorException(pos, "Unknown message type: " + msgType));
                break;
        }
        int sizeParsed = (int) (position() - pos - headerSize);
        if (sizeParsed != msgSize) {
            errors.add(new FormatErrorException(pos, "Message size mismatch, parsed: " + sizeParsed + ", msg size: " + msgSize + ", msgType: " + msgType));
            buffer.position(buffer.position() + msgSize - sizeParsed);
        }
    }

    private void addFieldsToList(String path, AbstractParser value) {
        if (value instanceof FieldParser) {
            fieldsList.put(path, ((FieldParser) value).getType());
        } else if (value instanceof ArrayParser) {
            AbstractParser[] items = ((ArrayParser) value).getItems();
            for (int i = 0; i < items.length; i++) {
                AbstractParser item = items[i];
                addFieldsToList(String.format("%s[%s]", path, i), item);
            }
        } else if (value instanceof StructParser) {
            for (Map.Entry<String, AbstractParser> e : ((StructParser) value).getFields().entrySet()) {
                if (!e.getKey().startsWith("_")) {
                    addFieldsToList(String.format("%s.%s", path, e.getKey()), e.getValue());
                }
            }
        }
    }

    private void handleDataMessage(long pos, int msgType, int msgSize) {
        int bp = buffer.position();
        if (msgType == MESSAGE_TYPE_DATA) {
            int msgId = logVersion >= 2 ? (buffer.getShort() & 0xFFFF) : (buffer.get() & 0xFF);
            List<Subscription> subs = subscriptions.get(msgId);
            if (subs != null) {
                int multiId = buffer.get() & 0xFF;
                long timestamp = buffer.getLong();
                for (Subscription sub : subs) {
                    if (sub.update(buffer, multiId)) {
                        updatedSubscriptions.add(sub);
                        timeLast = timestamp;
                    }
                }
            }
        } else {
            errors.add(new FormatErrorException("Unexpected message type: " + msgType));
        }
        buffer.position(bp + msgSize);
    }

    private String getString(ByteBuffer buffer, int len) {
        byte[] strBuf = new byte[len];
        buffer.get(strBuf);
        String[] p = new String(strBuf, context.getCharset()).split("\0");
        return p.length > 0 ? p[0] : "";
    }

    @Override
    public List<Exception> getErrors() {
        return errors;
    }

    @Override
    public void clearErrors() {
        errors.clear();
    }

    interface MessageHandler {
        void handleMessage(long pos, int msgType, int msgSize) throws IOException;
    }
}
