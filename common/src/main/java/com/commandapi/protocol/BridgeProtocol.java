package com.commandapi.protocol;
import com.commandapi.minecraft.ChatResult;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
/** Stable binary envelope. Strict UTF-8, bounded frames, version and replay-time validation. */
public final class BridgeProtocol {
    public static final String CHANNEL = "commandapi:bridge";
    public static final int VERSION = 1, MAX_BYTES = 8192, MAX_BODY_BYTES = 4096;
    public enum Kind { HELLO, CAPABILITIES, EXECUTE, EXECUTE_RESULT, JOB_STATUS, CANCEL, ERROR }
    public static final class Message {
        public final Kind kind; public final UUID id; public final long issuedAt; public final String body;
        public Message(Kind kind, UUID id, long issuedAt, String body) { this.kind = kind; this.id = id; this.issuedAt = issuedAt; this.body = body; }
        public static Message now(Kind kind, UUID id, String body) { return new Message(kind, id, System.currentTimeMillis(), body); }
    }
    public static byte[] encode(Message message) {
        try {
            byte[] body = message.body.getBytes(StandardCharsets.UTF_8);
            if (body.length > MAX_BODY_BYTES) throw new IllegalArgumentException("Payload body too large");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(0x43415049); out.writeShort(VERSION); out.writeByte(message.kind.ordinal());
            out.writeLong(message.id.getMostSignificantBits()); out.writeLong(message.id.getLeastSignificantBits());
            out.writeLong(message.issuedAt); out.writeShort(body.length); out.write(body);
            return bytes.toByteArray();
        } catch (IOException e) { throw new IllegalArgumentException("Cannot encode payload"); }
    }
    public static Message decode(byte[] bytes, long now) {
        if (bytes == null || bytes.length > MAX_BYTES || bytes.length < 33) throw new IllegalArgumentException("Invalid payload size");
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readInt() != 0x43415049 || in.readUnsignedShort() != VERSION) throw new IllegalArgumentException("Protocol mismatch");
            int kind = in.readUnsignedByte(); if (kind >= Kind.values().length) throw new IllegalArgumentException("Unknown message kind");
            UUID id = new UUID(in.readLong(), in.readLong()); long issuedAt = in.readLong();
            if (issuedAt < now - 30000 || issuedAt > now + 5000) throw new IllegalArgumentException("Expired message");
            int size = in.readUnsignedShort(); if (size > MAX_BODY_BYTES || size != in.available()) throw new IllegalArgumentException("Invalid body length");
            byte[] body = new byte[size]; in.readFully(body);
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(body)).toString();
            Kind type = Kind.values()[kind];
            if ((type == Kind.HELLO || type == Kind.CANCEL || type == Kind.JOB_STATUS) && !text.isEmpty()) throw new IllegalArgumentException("Unexpected body");
            if (type == Kind.EXECUTE && (!text.startsWith("/") || text.length() > 256 || text.contains("\n") || text.contains("\r") || text.indexOf('\0') >= 0)) throw new IllegalArgumentException("Invalid command body");
            if (type == Kind.CAPABILITIES && !text.equals("player") && !text.equals("denied")) throw new IllegalArgumentException("Unknown capabilities");
            if (type == Kind.ERROR && !text.matches("[A-Z_]{1,64}")) throw new IllegalArgumentException("Invalid error body");
            return new Message(type, id, issuedAt, text);
        } catch (IOException e) { throw new IllegalArgumentException("Malformed payload"); }
    }
    public static String resultBody(ChatResult result) {
        return result.getStage() + "|" + result.getCode() + "|" + result.isSuccess() + "|"
                + (result.getReturnValue() == null ? "" : result.getReturnValue());
    }
    public static ChatResult result(String body) {
        if (body == null || body.length() > 128) throw new IllegalArgumentException("Invalid execution result");
        String[] fields = body.split("\\|", -1);
        if (fields.length != 4 || !fields[1].matches("[A-Z_]{1,64}")) throw new IllegalArgumentException("Invalid execution result");
        String stage = fields[0], code = fields[1];
        if (stage.equals("dispatched") && fields[2].equals("true") && fields[3].isEmpty()) return ChatResult.ok("Server command dispatched; effect is not confirmed");
        if (stage.equals("unknown") && fields[2].equals("false") && fields[3].isEmpty()) return ChatResult.unknown("Server execution outcome unknown", code);
        if (stage.equals("failed") && fields[2].equals("false") && fields[3].isEmpty()) return ChatResult.failure("Server rejected command", code);
        if (stage.equals("executed") && fields[2].equals("true") && fields[3].matches("-?[0-9]{1,10}")) {
            try { return ChatResult.execution(true, Integer.parseInt(fields[3]), 1); }
            catch (NumberFormatException error) { throw new IllegalArgumentException("Invalid execution result"); }
        }
        throw new IllegalArgumentException("Invalid execution result");
    }
    private BridgeProtocol() { }
}
