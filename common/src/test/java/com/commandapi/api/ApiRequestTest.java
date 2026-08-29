package com.commandapi.api;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiRequestTest {

    @Test
    void parsesSingleTextMessage() {
        ApiRequest request = ApiRequest.parse("{\"text\":\"hello\"}");
        assertEquals(Arrays.asList("hello"), request.getMessages());
        assertFalse(request.isBatch());
    }

    @Test
    void parsesLegacyCommandField() {
        ApiRequest request = ApiRequest.parse("{\"command\":\"/time set day\"}");
        assertEquals(Arrays.asList("/time set day"), request.getMessages());
        assertFalse(request.isBatch());
    }

    @Test
    void textWinsOverCommand() {
        assertEquals(Arrays.asList("a"), ApiRequest.parse("{\"text\":\"a\",\"command\":\"b\"}").getMessages());
    }

    @Test
    void parsesMultipleMessages() {
        ApiRequest request = ApiRequest.parse("{\"messages\":[\"a\",\"/b\"]}");
        assertEquals(Arrays.asList("a", "/b"), request.getMessages());
        assertTrue(request.isBatch());
    }

    @Test
    void rejectsMissingFields() {
        ApiRequestException e = assertThrows(ApiRequestException.class,
                () -> ApiRequest.parse("{\"other\":1}"));
        assertTrue(e.getMessage().contains("Missing 'text' or 'messages'"));
    }

    @Test
    void rejectsMalformedJson() {
        assertThrows(ApiRequestException.class, () -> ApiRequest.parse("{not json"));
    }

    @Test
    void rejectsNonObjectBody() {
        assertThrows(ApiRequestException.class, () -> ApiRequest.parse("[1,2]"));
        assertThrows(ApiRequestException.class, () -> ApiRequest.parse(""));
    }

    @Test
    void rejectsEmptyAndNonStringMessages() {
        assertThrows(ApiRequestException.class, () -> ApiRequest.parse("{\"text\":\"\"}"));
        assertThrows(ApiRequestException.class, () -> ApiRequest.parse("{\"messages\":[]}"));
        assertThrows(ApiRequestException.class, () -> ApiRequest.parse("{\"messages\":\"a\"}"));
        assertThrows(ApiRequestException.class, () -> ApiRequest.parse("{\"messages\":[1]}"));
    }
}
