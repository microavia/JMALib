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
    private static final byte MESSAGE_TYPE_STRUCT = (byte) 'S';
    private static final byte MESSAGE_TYPE_FORMAT = (byte) 'F';
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
    private Map<String, Object> version = new HashMap<String, Object>();
    private Map<String, Object> parameters = new HashMap<String, Object>();
    private List<Exception> errors = new ArrayList<Exception>();
    private int logVersion = 0;
    private int headerSize = 2;
    private LogParserContext context = new LogParserContext();

    public ULogReader(String fileName) throws IOException, FormatErrorException {
        super(fileName);
        updateStatistics();
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
            logVersion = 0;
            headerSize = 2;
            position(0);
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
        Type value = null;
        for (String p : parts) {
            if (msg == null) {
                msg = messagesByName.get(p);
                value = msg;
            } else {
                if (value instanceof Struct) {
                    String[] pp = p.split("\\[");
                    if (pp.length > 1) {
                        value = ((Struct) value).get(pp[0]);
                        try {
                            int idx = Integer.parseInt(pp[1].split("\\]")[0]);
                            if (value instanceof Array) {
                                value = ((Array)value).get(idx);
                            }
                        } catch (Exception ignored) {}
                    } else {
                        value = ((Struct) value).get(p);
                    }
                } else {
                    break;
                }
            }
        }
        if (msg != null && value != null) {
            Subscription subscription = new Subscription(path, value);
            subscriptions.putIfAbsent(msg.getMsgID(), new ArrayList<>());
            subscriptions.get(msg.getMsgID()).add(subscription);
            return subscription;
        } else {
            return new Subscription(path, null);
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
            readMessage((pos, msgType, msgSize) -> timeLast = handleDataMessage(pos, msgType, msgSize));
        } while (timeLast < 0);
        return timeLast;
    }

    @Override
    public boolean seek(long seekTime) throws IOException, FormatErrorException {
        position(dataStart);
        if (seekTime == 0) {      // Seek to start of log
            return true;
        }
        // Seek to specified timestamp without parsing all messages
        try {
            while (true) {
                fillBuffer(headerSize);
                long pos = position();
                if (logVersion > 0) {
                    byte sync = buffer.get();
                    if (sync != SYNC_BYTE) {
                        continue;
                    }
                }
                int msgType = buffer.get() & 0xFF;
                int msgSize;
                if (logVersion == 0) {
                    msgSize = buffer.get() & 0xFF;
                } else {
                    msgSize = buffer.getShort() & 0xFFFF;
                }
                fillBuffer(msgSize);
                if (msgType == MESSAGE_TYPE_DATA) {
                    buffer.get();   // MsgID
                    buffer.get();   // MultiID
                    long timestamp = buffer.getLong();
                    if (timestamp >= seekTime) {
                        // Time found
                        position(pos);
                        return true;
                    }
                    buffer.position(buffer.position() + msgSize - 10);
                } else {
                    fillBuffer(msgSize);
                    buffer.position(buffer.position() + msgSize);
                }
            }
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
            if (logVersion > 0) {
                byte sync = buffer.get();
                if (sync != SYNC_BYTE) {
                    errors.add(new FormatErrorException(pos, String.format("Wrong sync byte: 0x%02X (expected 0x%02X)", sync & 0xFF, SYNC_BYTE & 0xFF)));
                    continue;
                }
            }
            int msgType = buffer.get() & 0xFF;
            int msgSize;
            if (logVersion == 0) {
                msgSize = buffer.get() & 0xFF;
            } else {
                msgSize = buffer.getShort() & 0xFFFF;
            }
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
                int msgId = buffer.get() & 0xFF;
                int strLen = buffer.getShort() & 0xFFFF;
                String[] descr = getString(buffer, strLen).split(":");
                String name = descr[0];
                if (descr.length > 1) {
                    Message msg = new Message(context, descr[1], msgId);
                    messagesById.put(msgId, msg);
                    messagesByName.put(name, msg);
                    addFieldsToList(name, msg);
                }
                break;
            }
            case MESSAGE_TYPE_STRUCT: {
                int strLen = buffer.getShort() & 0xFFFF;
                String[] descr = getString(buffer, strLen).split(":");
                String name = descr[0];
                if (descr.length > 1) {
                    Struct struct = new Struct(context, descr[1]);
                    context.getStructs().put(name, struct);
                }
                break;
            }
            case MESSAGE_TYPE_INFO: {
                int keyLen = buffer.get() & 0xFF;
                String[] keyDescr = getString(buffer, keyLen).split(" ");
                String key = keyDescr[1];
                Type field = Type.createFromFormatString(context, keyDescr[0]);
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
                Type field = Type.createFromFormatString(context, keyDescr[0]);
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

    private void addFieldsToList(String path, Type value) {
        if (value instanceof Field) {
            fieldsList.put(path, ((Field) value).type);
        } else if (value instanceof Array) {
            Type[] items = ((Array) value).getItems();
            for (int i = 0; i < items.length; i++) {
                Type item = items[i];
                addFieldsToList(String.format("%s[%s]", path, i), item);
            }
        } else if (value instanceof Struct) {
            for (Map.Entry<String, Type> e : ((Struct) value).getFields().entrySet()) {
                if (!e.getKey().startsWith("_")) {
                    addFieldsToList(String.format("%s.%s", path, e.getKey()), e.getValue());
                }
            }
        }
    }

    private long handleDataMessage(long pos, int msgType, int msgSize) {
        int bp = buffer.position();
        if (msgType == MESSAGE_TYPE_DATA) {
            int msgID = buffer.get() & 0xFF;
            Struct msgStruct = messagesById.get(msgID);
            if (msgStruct == null) {
                errors.add(new FormatErrorException(pos, "Unknown DATA message ID: " + msgID));
            } else {
                List<Subscription> subs = subscriptions.get(msgID);
                if (subs != null) {
                    int multiID = buffer.get() & 0xFF;
                    long timestamp = buffer.getLong();
                    for (Subscription sub : subs) {
                        sub.update(buffer);
                        updatedSubscriptions.add(sub);
                    }
                    buffer.position(bp + msgSize);
                    return timestamp;
                }
            }
        } else {
            errors.add(new FormatErrorException("Unexpected message type: " + msgType));
        }
        buffer.position(bp + msgSize);
        return -1;
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

    public static void main(String[] args) throws Exception {
        ULogReader reader = new ULogReader("test.ulg");
        //Subscription s1 = reader.addSubscription("ATTITUDE_POSITION.vel[1]");
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
}
