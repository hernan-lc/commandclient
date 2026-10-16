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
        boolean updateSucceeds = true;
        int applyCalls;
        int updateCalls;

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

        @Override
        public boolean updateLoginSummary(boolean enabled) {
            updateCalls++;
            if (updateSucceeds) {
                config = new ApiConfig(config.getHost(), config.getPort(), config.getToken(),
                        config.isAuthEnabled(), enabled);
            }
            return updateSucceeds;
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
        assertTrue(joined(handler.handle("help")).contains("/commandapi restart"));
    }

    @Test
    void statusReportsState() {
        String text = joined(handler.handle("status"));
        assertTrue(text.contains("Running"));
        assertTrue(text.contains("auth=on"));
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
        actions.config = new ApiConfig("127.0.0.1", 0, "", false);
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
        actions.config = new ApiConfig("127.0.0.1", 0, "", false);
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
    void helpMentionsLogin() {
        assertTrue(joined(handler.handle("")).contains("/commandapi login"));
    }

    @Test
    void loginOffDisablesSummaryWithoutRestart() {
        String text = joined(handler.handle("login off"));
        assertFalse(actions.config.isLoginSummary());
        assertEquals(1, actions.updateCalls);
        assertEquals(0, actions.applyCalls);
        assertTrue(text.contains("disabled"));
    }

    @Test
    void loginOnEnablesSummary() {
        handler.handle("login off");
        handler.handle("login on");
        assertTrue(actions.config.isLoginSummary());
        assertEquals(2, actions.updateCalls);
        assertEquals(0, actions.applyCalls);
    }

    @Test
    void loginRejectsGarbage() {
        assertTrue(joined(handler.handle("login maybe")).contains("Usage"));
        assertEquals(0, actions.updateCalls);
    }

    @Test
    void loginMissingArgShowsUsageAndState() {
        String text = joined(handler.handle("login"));
        assertTrue(text.contains("Usage"));
        assertTrue(text.contains("currently on"));
    }

    @Test
    void bindChangesPreserveLoginSummary() {
        handler.handle("login off");
        handler.handle("port 9123");
        handler.handle("host 0.0.0.0");
        handler.handle("token s3cret");
        assertFalse(actions.config.isLoginSummary());
        assertTrue(joined(handler.handle("status")).contains("login=off"));
    }

    @Test
    void statusShowsLoginState() {
        assertTrue(joined(handler.handle("status")).contains("login=on"));
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
                "login", "login off", "reload", "restart", "bogus", "port abc"}) {
            for (String line : handler.handle(args)) {
                assertFalse(line.contains("\n"), "line must be single-line: " + line);
            }
        }
    }
}
