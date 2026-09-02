package com.commandapi;

import com.commandapi.config.ApiConfig;
import com.commandapi.config.ConfigLoader;
import com.commandapi.minecraft.ChatResult;
import com.commandapi.minecraft.MinecraftBridge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises restart, persistence and discovery against a real socket. */
class CommandApiServiceTest {

    private static final class FakeBridge implements MinecraftBridge {
        @Override
        public boolean isInWorld() {
            return true;
        }

        @Override
        public String getPlayerName() {
            return "Steve";
        }

        @Override
        public ChatResult sendChat(String text) {
            return ChatResult.ok("ok");
        }
    }

    private static String joined(List<String> lines) {
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            out.append(line).append('\n');
        }
        return out.toString();
    }

    @Test
    void ephemeralStartWritesAddressFile(@TempDir Path dir) throws Exception {
        CommandApiService service = new CommandApiService(dir, new FakeBridge(), "test", "test");
        service.start();
        try {
            assertTrue(service.isRunning());
            int port = service.getHttpServerManager().getPort();
            assertTrue(port > 0, "ephemeral port must resolve to a real port");
            Path address = dir.resolve(ConfigLoader.ADDRESS_FILE_NAME);
            assertTrue(Files.isRegularFile(address));
            String body = new String(Files.readAllBytes(address), StandardCharsets.UTF_8);
            assertTrue(body.contains(String.valueOf(port)));
        } finally {
            service.stop();
        }
        assertFalse(Files.exists(dir.resolve(ConfigLoader.ADDRESS_FILE_NAME)));
    }

    @Test
    void commandChangesPortAndPersists(@TempDir Path dir) {
        CommandApiService service = new CommandApiService(dir, new FakeBridge(), "test", "test");
        service.start();
        try {
            int first = service.getHttpServerManager().getPort();
            assertTrue(first > 0);

            String text = joined(service.runCommand("port 0"));
            assertTrue(text.contains("automatic") || text.contains("Serving at"));
            assertTrue(service.isRunning());
            int second = service.getHttpServerManager().getPort();
            assertTrue(second > 0);

            ApiConfig saved = ConfigLoader.load(dir);
            assertTrue(saved.isEphemeral());

            assertTrue(joined(service.runCommand("status")).contains("Running"));
        } finally {
            service.stop();
        }
    }

    @Test
    void occupiedExplicitPortFailsCleanly(@TempDir Path dir) throws Exception {
        CommandApiService first = new CommandApiService(dir, new FakeBridge(), "test", "test");
        first.start();
        int taken = 0;
        try {
            taken = first.getHttpServerManager().getPort();
            ConfigLoader.save(dir, new ApiConfig("127.0.0.1", taken, "", false));
            CommandApiService second = new CommandApiService(dir, new FakeBridge(), "test", "test");
            second.start();
            try {
                assertFalse(second.isRunning(), "second server on the same port must not run");
            } finally {
                second.stop();
            }
        } finally {
            first.stop();
        }
        assertTrue(taken > 0);
    }
}
