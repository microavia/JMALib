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
    private static final byte MESSAGE_TYPE_FORMAT = (byte) 'F';
    private static final byte MESSAGE_TYPE_ADD = (byte) 'A';
    private static final byte MESSAGE_TYPE_DATA = (byte) 'D';
    private static final byte MESSAGE_TYPE_INFO = (byte) 'I';
    private static final byte MESSAGE_TYPE_PARAMETER = (byte) 'P';

    private String systemName = "";
    private String systemConfig = "";
    private long dataStart = 0;
    private Map<Integer, Message> messagesById = new HashMap<>();
    private Map<String, Message> messagesByName = new HashMap<>();
    private Map<String, String> fieldsList = null;
    private Map<Integer, List<Subscription>> subscriptions = new HashMap<>();
    private Set<Subscription> updatedSubscriptions = new HashSet<>();
    private long sizeUpdates = -1;
    private long sizeMicroseconds = -1;
    private long startMicroseconds = -1;
    private long timeLast = -1;
    private long utcTimeReference = -1;
    private Map<String, Object> version = new HashMap<>();
    private Map<String, Object> parameters = new HashMap<>();
    private List<Exception> errors = new ArrayList<>();
    private int logVersion = 0;
    private int headerSize = 2;
    private LogParserContext context = new LogParserContext();

    public ULogReader(String fileName) throws IOException, FormatErrorException {
        super(fileName);
        updateStatistics();
    }

    public static void main(String[] args) throws Exception {
        ULogReader reader = new ULogReader("test.ulg");
        reader.addSubscription("ATTITUDE_POSITION.alt_baro");
        long tStart = System.currentTimeMillis();
        try {
            while (true) {
                long t = reader.readUpdate();
                for (Subscription s : reader.updatedSubscriptions) {
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
        } else {
            throw new FormatErrorException("Unknown header");
        }
        startMicroseconds = -1;
        timeLast = -1;
        sizeUpdates = 0;
        fieldsList = new HashMap<>();
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
        Message msg = null;
        int multiIdFilter = -1;
        AbstractParser parser = null;
        for (String p : parts) {
            if (msg == null) {
                String[] pp = p.split("\\[");
                if (pp.length > 1) {
                    multiIdFilter = Integer.parseInt(pp[1].split("]")[0]);
                }
                msg = messagesByName.get(pp[0]);
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
        do {
            readMessage(this::handleDataMessage);
        } while (timeLast < 0);
        return timeLast;
    }

    @Override
    public Set<Subscription> getUpdatedSubscriptions() {
        return updatedSubscriptions;
    }

    @Override
    public boolean seek(long seekTime) throws IOException, FormatErrorException {
        position(dataStart);
        if (seekTime == 0) {      // Seek to start of log
            return true;
        }
        // Seek to specified timestamp without parsing all messages
        try {
            while (timeLast < seekTime) {
                readMessage((pos, msgType, msgSize) -> {
                    if (msgType == MESSAGE_TYPE_DATA) {
                        timeLast = buffer.getLong(buffer.position() + 3);
                        if (timeLast >= seekTime) {
                            return; // Dont consume message with timestamp > seekTime
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
     * Read next message from log
     *
     * @return log message
     * @throws IOException  on IO error
     * @throws EOFException on end of stream
     */
    public void readMessage(MessageHandler handler) throws IOException {
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
                long timestamp = buffer.getLong(buffer.position() + 2);
                if (startMicroseconds < 0) {
                    startMicroseconds = timestamp;
                }
                timeLast = timestamp;
                ++sizeUpdates;
                buffer.position(buffer.position() + msgSize);
                break;
            }
            case MESSAGE_TYPE_FORMAT: {
                String[] descr = getString(buffer, msgSize).split(":");
                String name = descr[0];
                if (descr.length > 1) {
                    StructParser struct = new StructParser(context, descr[1]);
                    System.out.printf("STRUCT %s: %s\n", name, struct);
                    context.getStructs().put(name, struct);
                }
                break;
            }
            case MESSAGE_TYPE_ADD: {
                int msgId = buffer.getShort() & 0xFFFF;
                String[] descr = getString(buffer, msgSize - 2).split(":");
                String name = descr[0];
                String structName = descr[1];
                System.out.printf("ADD: %s:%s\n", name, structName);
                StructParser struct = context.getStructs().get(structName);
                Message message = new Message(name, struct, msgId);
                messagesById.put(msgId, message);
                messagesByName.put(name, message);
                addFieldsToList(name, struct);
                break;
            }
            case MESSAGE_TYPE_INFO: {
                int keyLen = buffer.get() & 0xFF;
                String[] keyDescr = getString(buffer, keyLen).split(" ");
                String key = keyDescr[1];
                AbstractParser field = AbstractParser.createFromFormatString(context, keyDescr[0]);
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
                AbstractParser field = AbstractParser.createFromFormatString(context, keyDescr[0]);
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
            fieldsList.put(path, ((FieldParser) value).type);
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
        timeLast = -1;
        if (msgType == MESSAGE_TYPE_DATA) {
            int msgId = buffer.getShort() & 0xFFFF;
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

    public String getString(ByteBuffer buffer, int len) {
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
