package com.commandapi.minecraft;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ExecutionFeedbackTest {
    @Test void noCallbackDoesNotClaimExecution() {
        ChatResult result = new ExecutionFeedback().result();
        assertEquals("unknown", result.getStage()); assertFalse(result.isSuccess());
    }
    @Test void zeroValueCanStillBeSuccessfulExecution() {
        ExecutionFeedback f = new ExecutionFeedback(); f.record(true, 0);
        assertTrue(f.result().isSuccess()); assertEquals("executed", f.result().getStage());
        assertEquals(0, f.result().getReturnValue());
    }
    @Test void anyFailedCallbackMakesOverallExecutionFailed() {
        ExecutionFeedback f = new ExecutionFeedback(); f.record(true, 3); f.record(false, 0);
        assertFalse(f.result().isSuccess()); assertEquals("failed", f.result().getStage());
        assertEquals(2, f.result().getCallbacks());
    }
}
