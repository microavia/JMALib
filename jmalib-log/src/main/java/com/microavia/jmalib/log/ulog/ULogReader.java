package com.microavia.jmalib.log.ulog;

import com.microavia.jmalib.log.BinaryLogReader;
import com.microavia.jmalib.log.FormatErrorException;
import com.microavia.jmalib.log.ulog.model.ArrayType;
import com.microavia.jmalib.log.ulog.model.StructType;
import com.microavia.jmalib.log.ulog.model.Type;

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
    private final Map<String, Topic> topicByName = new HashMap<>();
    private Map<String, String> fieldsList = null;
    private final Map<Integer, Subscription> subscriptions = new HashMap<>();
    private final ArrayList<Subscription> updatedSubscriptions = new ArrayList<>();
    private long sizeUpdates = -1;
    private long sizeMicroseconds = -1;
    private long startMicroseconds = -1;
    private long timeLast = Long.MIN_VALUE;
    private long utcTimeReference = -1;
    private final Map<String, Object> version = new HashMap<>();
    private final Map<String, Object> parameters = new HashMap<>();
    private final List<Exception> errors = new ArrayList<>();
    private int logVersion = 0;
    private int headerSize = 4;
    private int msgDataTimestampOffset = 3;
    private final Codec codec = new Codec();

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

    public Subscription addSubscription(String topicName) {
        Topic topic = topicByName.get(topicName);
        if (topic == null) {
            throw new SubscriptionException("Topic not found: " + topicName);
        }
        Type topicType = codec.getTypeDescription(topic.getTypeName());
        var sub = subscriptions.get(topic.getId());
        if (sub == null) {
            sub = new Subscription(codec, topicName, topicType);
            subscriptions.put(topic.getId(), sub);
        }
        return sub;
    }

    @Override
    public void removeAllSubscriptions() {
        subscriptions.clear();
        updatedSubscriptions.clear();
    }

    @Override
    public long readUpdate() throws IOException {
        for (Subscription sub : updatedSubscriptions) {
            sub.clearUpdated();
        }
        updatedSubscriptions.clear();
        timeLast = Long.MIN_VALUE;
        do {
            readMessage(this::handleDataMessage);
        } while (timeLast == Long.MIN_VALUE);
        return timeLast;
    }

    @Override
    public List<Subscription> getUpdatedSubscriptions() {
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
                errors.add(new FormatErrorException(pos, "Error parsing message typeName: " + msgType, e));
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
                    String descrStr = getString(buffer, formatLen);
                    String[] descr = getString(buffer, formatLen).split(":");
                    if (descr.length <= 1) {
                        errors.add(new FormatErrorException(pos, String.format("Invalid struct description: %s", descrStr)));
                        break;
                    }
                    codec.addStructType(descr[0], descr[1]);
                } else {
                    String descrStr = getString(buffer, msgSize);
                    String[] descr = descrStr.split(":");
                    if (descr.length <= 1) {
                        errors.add(new FormatErrorException(pos, String.format("Invalid struct description: %s", descrStr)));
                        break;
                    }

                    codec.addStructType(descr[0], descr[1]);
                }
                break;
            }
            case MESSAGE_TYPE_TOPIC: {
                int msgId = buffer.getShort() & 0xFFFF;
                String[] descr = getString(buffer, msgSize - 2).split(":");
                String name = descr[0];
                String typeName = descr[1];
                Type typeDescr = codec.getTypeDescription(typeName);
                if (typeDescr == null) {
                    errors.add(new FormatErrorException(pos, String.format("Unknown topic struct typeName: %s", typeName)));
                    break;
                }
                Topic topic = new Topic(name, typeDescr.getTypeName(), msgId);
                topicByName.put(name, topic);
                addFieldsToList(pos, name, typeDescr);
                break;
            }
            case MESSAGE_TYPE_INFO: {
                int keyLen = buffer.get() & 0xFF;
                String[] keyDescr = getString(buffer, keyLen).split(" ");
                String key = keyDescr[1];
                Parser parser = codec.getValueParser(keyDescr[0]);
                if (parser == null) {
                    errors.add(new FormatErrorException(pos, "Error parsing info: " + key));
                    break;
                }
                Object value = parser.parse(buffer);
                switch (key) {
                    case "sys_name":
                        systemName = codec.objectToString(value);
                        break;
                    case "sys_config":
                        systemConfig = codec.objectToString(value);
                        break;
                    case "ver_hw":
                        version.put("HW", codec.objectToString(value));
                        break;
                    case "ver_sw":
                        version.put("FW", codec.objectToString(value));
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
                Parser parser = codec.getValueParser(keyDescr[0]);
                if (parser == null) {
                    errors.add(new FormatErrorException(pos, "Error parsing parameter: " + key));
                    break;
                }
                Object value = parser.parse(buffer);
                parameters.put(key, value);
                break;
            }
            default:
                buffer.position(buffer.position() + msgSize);
                errors.add(new FormatErrorException(pos, "Unknown message typeName: " + msgType));
                break;
        }
        int sizeParsed = (int) (position() - pos - headerSize);
        if (sizeParsed != msgSize) {
            errors.add(new FormatErrorException(pos, "Message size mismatch, parsed: " + sizeParsed + ", msg size: " + msgSize + ", msgType: " + msgType));
            buffer.position(buffer.position() + msgSize - sizeParsed);
        }
    }

    private void addFieldsToList(long pos, String path, Type typeDescr) {
        switch (typeDescr.getTypeClass()) {
            case STRUCT: {
                for (var field : ((StructType) typeDescr).getFields()) {
                    if (!field.name().startsWith("_")) {
                        var type = codec.getTypeDescription(field.typeName());
                        if (type == null) {
                            errors.add(new FormatErrorException(pos, "Invalid type of field " + field.typeName() + ": " + field.name()));
                            break;
                        }
                        addFieldsToList(pos, String.format("%s.%s", path, field.name()), type);
                    }
                }
                break;
            }
            case ARRAY: {
                var arrDescr = ((ArrayType) typeDescr);
                int size = arrDescr.getSize();
                for (int i = 0; i < size; i++) {
                    addFieldsToList(pos, String.format("%s[%s]", path, i), codec.getTypeDescription(arrDescr.getElementType()));
                }
                break;
            }
            default: {
                fieldsList.put(path, typeDescr.getTypeName());
                break;
            }
        }
    }

    private void handleDataMessage(long pos, int msgType, int msgSize) {
        int bp = buffer.position();
        if (msgType == MESSAGE_TYPE_DATA) {
            int msgId = logVersion >= 2 ? (buffer.getShort() & 0xFFFF) : (buffer.get() & 0xFF);
            Subscription sub = subscriptions.get(msgId);
            if (sub != null) {
                int multiId = buffer.get() & 0xFF;
                long timestamp = buffer.getLong();
                if (sub.update(buffer, multiId)) {
                    updatedSubscriptions.add(sub);
                    timeLast = timestamp;
                }
            }
        } else {
            errors.add(new FormatErrorException("Unexpected message typeName: " + msgType));
        }
        buffer.position(bp + msgSize);
    }

    private String getString(ByteBuffer buffer, int len) {
        byte[] strBuf = new byte[len];
        buffer.get(strBuf);
        String[] p = new String(strBuf, codec.getCharset()).split("\0");
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
