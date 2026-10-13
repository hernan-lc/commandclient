package com.commandapi.api;
import com.commandapi.protocol.BridgeProtocol;
import com.commandapi.protocol.BridgeProtocol.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class BridgeProtocolTest {
    @Test void roundTripPreservesIdentityAndUtf8() {
        Message m = Message.now(Kind.EXECUTE, UUID.randomUUID(), "/say café");
        Message decoded = BridgeProtocol.decode(BridgeProtocol.encode(m), System.currentTimeMillis());
        assertEquals(m.kind, decoded.kind); assertEquals(m.id, decoded.id); assertEquals(m.body, decoded.body);
    }
    @Test void rejectsMismatchExpiredTrailingAndOversizedPayloads() {
        Message m = Message.now(Kind.HELLO, UUID.randomUUID(), ""); byte[] bytes = BridgeProtocol.encode(m);
        assertThrows(IllegalArgumentException.class, () -> BridgeProtocol.decode(Arrays.copyOf(bytes, bytes.length + 1), System.currentTimeMillis()));
        bytes[5] = 2; assertThrows(IllegalArgumentException.class, () -> BridgeProtocol.decode(bytes, System.currentTimeMillis()));
        assertThrows(IllegalArgumentException.class, () -> BridgeProtocol.decode(BridgeProtocol.encode(new Message(Kind.HELLO, UUID.randomUUID(), 0, "")), System.currentTimeMillis()));
        assertThrows(IllegalArgumentException.class, () -> BridgeProtocol.decode(new byte[8193], System.currentTimeMillis()));
    }
    @Test void rejectsCommandNewlinesAndSpoofedCapabilities() {
        assertThrows(IllegalArgumentException.class, () -> BridgeProtocol.decode(BridgeProtocol.encode(Message.now(Kind.EXECUTE, UUID.randomUUID(), "/op x\n/stop")), System.currentTimeMillis()));
        assertThrows(IllegalArgumentException.class, () -> BridgeProtocol.decode(BridgeProtocol.encode(Message.now(Kind.CAPABILITIES, UUID.randomUUID(), "console")), System.currentTimeMillis()));
    }
    @Test void resultFramesRejectInconsistentOrNestedBodies() {
        assertThrows(IllegalArgumentException.class, () -> BridgeProtocol.result("executed|EXECUTED|false|1"));
        assertThrows(IllegalArgumentException.class, () -> BridgeProtocol.result("executed|EXECUTED|true|9999999999"));
        assertThrows(IllegalArgumentException.class, () -> BridgeProtocol.result("dispatched|DISPATCHED|true||"));
        assertThrows(IllegalArgumentException.class, () -> BridgeProtocol.result("{{{{{{{{{{{{{{"));
        assertEquals("executed", BridgeProtocol.result(BridgeProtocol.resultBody(com.commandapi.minecraft.ChatResult.execution(true, 0, 1))).getStage());
    }
}
