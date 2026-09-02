package com.commandapi.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigCommandHandlerTest {

    private FakeActions actions;
    private ConfigCommandHandler handler;

    private static final class FakeActions implements ConfigCommandHandler.Actions {
        ApiConfig config = ApiConfig.defaults();
        boolean running = true;
        boolean applySucceeds = true;
        boolean reloadSucceeds = true;
        int applyCalls;

        @Override
        public ApiConfig currentConfig() {
            return config;
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public String runningAddress() {
            return "127.0.0.1:" + config.getPort();
        }

        @Override
        public boolean applyAndRestart(ApiConfig newConfig) {
            applyCalls++;
            if (applySucceeds) {
                config = newConfig;
            }
            return applySucceeds;
        }

        @Override
        public boolean reloadFromDisk() {
            return reloadSucceeds;
        }
    }

    @BeforeEach
    void setUp() {
        actions = new FakeActions();
        handler = new ConfigCommandHandler(actions);
    }

    private static String joined(List<String> lines) {
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            out.append(line).append('\n');
        }
        return out.toString();
    }

    @Test
    void emptyShowsHelp() {
        String text = joined(handler.handle(""));
        assertTrue(text.contains("/commandapi status"));
        assertTrue(text.contains("/commandapi port"));
    }

    @Test
    void helpKeywordShowsHelp() {
        assertEquals(handler.handle(""), handler.handle("help"));
    }

    @Test
    void statusReportsState() {
        String text = joined(handler.handle("status"));
        assertTrue(text.contains("Running"));
        assertTrue(text.contains("auth=off"));
    }

    @Test
    void portChangeApplies() {
        String text = joined(handler.handle("port 9123"));
        assertEquals(9123, actions.config.getPort());
        assertEquals(1, actions.applyCalls);
        assertTrue(text.contains("9123"));
    }

    @Test
    void portZeroMeansAutomatic() {
        handler.handle("port 0");
        assertTrue(actions.config.isEphemeral());
        assertTrue(joined(handler.handle("status")).contains("auto"));
    }

    @Test
    void portRejectsGarbage() {
        for (String bad : new String[]{"abc", "-1", "99999", "80x"}) {
            int before = actions.applyCalls;
            String text = joined(handler.handle("port " + bad)).toLowerCase();
            assertEquals(before, actions.applyCalls, "must not apply " + bad);
            assertTrue(text.contains("port"), "should show usage for " + bad);
        }
    }

    @Test
    void portMissingArgShowsUsage() {
        assertTrue(joined(handler.handle("port")).contains("Usage"));
    }

    @Test
    void portFailureKeepsOldConfig() {
        actions.config = new ApiConfig("127.0.0.1", 9111, "", false);
        actions.applySucceeds = false;
        String text = joined(handler.handle("port 9222"));
        assertTrue(text.contains("Could not bind"));
        assertEquals(9111, actions.config.getPort());
    }

    @Test
    void hostChangeApplies() {
        handler.handle("host 0.0.0.0");
        assertEquals("0.0.0.0", actions.config.getHost());
    }

    @Test
    void exposedHostWithoutAuthWarns() {
        String text = joined(handler.handle("host 0.0.0.0"));
        assertTrue(text.contains("WARNING"));
        assertTrue(text.contains("/commandapi token"));
    }

    @Test
    void hostRejectsBlank() {
        int before = actions.applyCalls;
        assertTrue(joined(handler.handle("host")).contains("Usage"));
        assertEquals(before, actions.applyCalls);
    }

    @Test
    void authOnWithoutTokenRefuses() {
        String text = joined(handler.handle("auth on"));
        assertTrue(text.contains("token"));
        assertFalse(actions.config.isAuthEnabled());
        assertEquals(0, actions.applyCalls);
    }

    @Test
    void tokenEnablesAuth() {
        handler.handle("token s3cret");
        assertEquals("s3cret", actions.config.getToken());
        assertTrue(actions.config.isAuthEnabled());
    }

    @Test
    void tokenClearDisablesAuth() {
        handler.handle("token s3cret");
        handler.handle("token clear");
        assertEquals("", actions.config.getToken());
        assertFalse(actions.config.isAuthEnabled());
    }

    @Test
    void authOffApplies() {
        actions.config = new ApiConfig("127.0.0.1", 0, "s3cret", true);
        handler.handle("auth off");
        assertFalse(actions.config.isAuthEnabled());
    }

    @Test
    void authRejectsGarbage() {
        assertTrue(joined(handler.handle("auth maybe")).contains("Usage"));
    }

    @Test
    void reloadReports() {
        assertTrue(joined(handler.handle("reload")).contains("reloaded"));
    }

    @Test
    void reloadFailureReports() {
        actions.reloadSucceeds = false;
        assertTrue(joined(handler.handle("reload")).contains("failed"));
    }

    @Test
    void unknownSubcommandHintsHelp() {
        assertTrue(joined(handler.handle("frobnicate")).contains("/commandapi help"));
    }

    @Test
    void everyLineHasNoEmbeddedNewline() {
        for (String args : new String[]{"", "status", "port 1", "host x", "auth on", "token t",
                "reload", "restart", "bogus", "port abc"}) {
            for (String line : handler.handle(args)) {
                assertFalse(line.contains("\n"), "line must be single-line: " + line);
            }
        }
    }
}
