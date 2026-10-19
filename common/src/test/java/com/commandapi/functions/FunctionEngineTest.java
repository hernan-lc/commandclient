package com.commandapi.functions;

import com.commandapi.security.CommandPolicy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionEngineTest {
    private FunctionCompiler compiler(FunctionRegistry registry) {
        return new FunctionCompiler(registry, FunctionLimits.defaults(),
                new CommandPolicy(Arrays.asList("*")), "1.20.1");
    }

    private void register(FunctionRegistry registry, String id, String json) throws Exception {
        registry.register(JsonFunctionParser.parse(id, json, "test"));
        FunctionValidator.validate(registry.get(id), FunctionLimits.defaults());
    }

    @Test void summonTitleSoundParticleCompileToExactCommands() throws Exception {
        FunctionRegistry registry = new FunctionRegistry();
        register(registry, "events:mob_attack", FunctionParsingTest.readExample("mob_attack.json"));
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("mob", "minecraft:zombie");
        args.put("count", 2L);
        args.put("title", "Attack!");
        FunctionExecutionPlan plan = compiler(registry).compile("events:mob_attack", args);
        assertEquals(Arrays.asList(
                "/summon minecraft:zombie ~ ~1 ~",
                "/summon minecraft:zombie ~ ~1 ~",
                "/title @a times 10 60 20",
                "/title @a title \"Attack!\"",
                "/title @a subtitle \"Prepare yourself!\"",
                "/playsound minecraft:entity.ender_dragon.growl master @a ~ ~ ~ 1 1"),
                plan.commands());
    }

    @Test void nestedFunctionsExpandWithCycleDetection() throws Exception {
        FunctionRegistry registry = new FunctionRegistry();
        register(registry, "util:inner",
                "{\"schemaVersion\":1,\"name\":\"util:inner\","
                        + "\"steps\":[{\"type\":\"command\",\"value\":\"say inner\"}]}");
        register(registry, "util:outer",
                "{\"schemaVersion\":1,\"name\":\"util:outer\","
                        + "\"steps\":[{\"type\":\"function\",\"name\":\"util:inner\"},"
                        + "{\"type\":\"command\",\"value\":\"say outer\"}]}");
        FunctionExecutionPlan plan = compiler(registry).compile("util:outer",
                Collections.<String, Object>emptyMap());
        assertEquals(Arrays.asList("/say inner", "/say outer"), plan.commands());
        register(registry, "util:a",
                "{\"schemaVersion\":1,\"name\":\"util:a\","
                        + "\"steps\":[{\"type\":\"function\",\"name\":\"util:b\"}]}");
        register(registry, "util:b",
                "{\"schemaVersion\":1,\"name\":\"util:b\","
                        + "\"steps\":[{\"type\":\"function\",\"name\":\"util:a\"}]}");
        assertThrows(FunctionException.class, () -> compiler(registry).compile("util:a",
                Collections.<String, Object>emptyMap()));
        // Direct self-recursion is also rejected.
        register(registry, "util:self",
                "{\"schemaVersion\":1,\"name\":\"util:self\","
                        + "\"steps\":[{\"type\":\"function\",\"name\":\"util:self\"}]}");
        assertThrows(FunctionException.class, () -> compiler(registry).compile("util:self",
                Collections.<String, Object>emptyMap()));
    }

    @Test void loopExpansionIsBounded() throws Exception {
        FunctionRegistry registry = new FunctionRegistry();
        register(registry, "ns:loop",
                "{\"schemaVersion\":1,\"name\":\"ns:loop\","
                        + "\"parameters\":{\"n\":{\"type\":\"integer\",\"default\":1}},"
                        + "\"steps\":[{\"type\":\"repeat\",\"times\":\"${n}\",\"steps\":["
                        + "{\"type\":\"command\",\"value\":\"say hi\"}]}]}");
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("n", 500L);
        assertThrows(FunctionException.class,
                () -> compiler(registry).compile("ns:loop", args));
        FunctionLimits tiny = new FunctionLimits(4, 100, 8, 1200, 120, 256 * 1024, 32);
        FunctionCompiler strict = new FunctionCompiler(registry, tiny,
                new CommandPolicy(Arrays.asList("*")), "1.20.1");
        Map<String, Object> small = new HashMap<String, Object>();
        small.put("n", 2L);
        assertEquals(2, strict.compile("ns:loop", small).commands().size());
        Map<String, Object> big = new HashMap<String, Object>();
        big.put("n", 3L);
        // Validator counts worst-case expansion: 3 commands against a limit of 4 items
        // passes validation but compile-time budget (4) still catches larger ones.
        big.put("n", 10L);
        assertThrows(FunctionException.class, () -> strict.compile("ns:loop", big));
    }

    @Test void conditionsBranchOnParameters() throws Exception {
        FunctionRegistry registry = new FunctionRegistry();
        register(registry, "ns:cond",
                "{\"schemaVersion\":1,\"name\":\"ns:cond\","
                        + "\"parameters\":{\"count\":{\"type\":\"integer\",\"default\":1}},"
                        + "\"steps\":[{\"type\":\"if\",\"condition\":{\"parameter\":\"count\","
                        + "\"operator\":\"gt\",\"value\":5},"
                        + "\"then\":[{\"type\":\"command\",\"value\":\"say large\"}],"
                        + "\"else\":[{\"type\":\"command\",\"value\":\"say small\"}]}]}");
        Map<String, Object> large = new HashMap<String, Object>();
        large.put("count", 9L);
        assertEquals(Collections.singletonList("/say large"),
                compiler(registry).compile("ns:cond", large).commands());
        Map<String, Object> small = new HashMap<String, Object>();
        small.put("count", 2L);
        assertEquals(Collections.singletonList("/say small"),
                compiler(registry).compile("ns:cond", small).commands());
    }

    @Test void invalidConditionsAreRejected() {
        assertThrows(FunctionException.class, () -> JsonFunctionParser.parse("ns:fn",
                "{\"schemaVersion\":1,\"name\":\"ns:fn\","
                        + "\"parameters\":{\"a\":{\"type\":\"string\",\"default\":\"x\"}},"
                        + "\"steps\":[{\"type\":\"if\","
                        + "\"condition\":{\"parameter\":\"a\",\"operator\":\"explode\",\"value\":\"x\"},"
                        + "\"then\":[{\"type\":\"command\",\"value\":\"say hi\"}]}]}", "test"));
        // Unknown condition parameter fails at validation.
        FunctionRegistry registry = new FunctionRegistry();
        try {
            FunctionDefinition definition = JsonFunctionParser.parse("ns:fn",
                    "{\"schemaVersion\":1,\"name\":\"ns:fn\","
                            + "\"steps\":[{\"type\":\"if\","
                            + "\"condition\":{\"parameter\":\"missing\",\"operator\":\"eq\",\"value\":\"x\"},"
                            + "\"then\":[{\"type\":\"command\",\"value\":\"say hi\"}]}]}", "test");
            FunctionValidator.validate(definition, FunctionLimits.defaults());
            throw new AssertionError("expected validation failure");
        } catch (FunctionException expected) {
            assertEquals("UNKNOWN_PARAMETER", expected.getCode());
        } catch (RuntimeException e) {
            throw new AssertionError(e);
        }
    }

    @Test void tickDelaysCompileToDelayItems() throws Exception {
        FunctionRegistry registry = new FunctionRegistry();
        register(registry, "ns:wait",
                "{\"schemaVersion\":1,\"name\":\"ns:wait\","
                        + "\"steps\":[{\"type\":\"command\",\"value\":\"say a\"},"
                        + "{\"type\":\"delay\",\"ticks\":40},"
                        + "{\"type\":\"command\",\"value\":\"say b\"}]}");
        FunctionExecutionPlan plan = compiler(registry).compile("ns:wait",
                Collections.<String, Object>emptyMap());
        assertEquals(3, plan.getItems().size());
        assertEquals(1, plan.delayCount());
        assertEquals(40, ((FunctionExecutionPlan.Delay) plan.getItems().get(1)).getTicks());
    }

    @Test void commandPolicyAppliesToExpandedCommands() throws Exception {
        FunctionRegistry registry = new FunctionRegistry();
        register(registry, "ns:fn",
                "{\"schemaVersion\":1,\"name\":\"ns:fn\","
                        + "\"steps\":[{\"type\":\"command\",\"value\":\"op Steve\"}]}");
        FunctionCompiler strict = new FunctionCompiler(registry, FunctionLimits.defaults(),
                new CommandPolicy(Arrays.asList("say")), "1.20.1");
        assertThrows(FunctionException.class, () -> strict.compile("ns:fn",
                Collections.<String, Object>emptyMap()));
    }

    @Test void versionsFormatTitleSoundSummonParticle() {
        for (String version : Arrays.asList("1.16.1", "1.18.2", "1.19.2", "1.20.1",
                "1.21.1", "26.1", "26.3")) {
            assertEquals("title @a times 10 60 20",
                    VersionCommandFormatter.titleTimes(version, "@a", 10, 60, 20));
            assertEquals("title @a title \"Hi\"",
                    VersionCommandFormatter.titleText(version, "@a", "title", "Hi"));
            assertTrue(VersionCommandFormatter.playSound(version, "minecraft:a.b", "master",
                    "@a", "~ ~ ~", 1, 1).startsWith("playsound minecraft:a.b master @a"));
            assertTrue(VersionCommandFormatter.summon(version, "minecraft:zombie", "~ ~ ~",
                    "").startsWith("summon minecraft:zombie"));
            assertTrue(VersionCommandFormatter.particle(version, "minecraft:flame", "~ ~ ~",
                    5).startsWith("particle minecraft:flame"));
            assertNotNull(VersionCommandFormatter.capabilities(version, "client-chat"));
        }
    }

    @Test void storageRejectsTraversalDuplicatesAndKeepsValid(@TempDir Path temp)
            throws Exception {
        Path configDir = temp.resolve("config");
        Path root = configDir.resolve("commandapi").resolve("functions");
        Files.createDirectories(root.resolve("events"));
        Files.write(root.resolve("events/ok.json"),
                ("{\"schemaVersion\":1,\"name\":\"events:ok\","
                        + "\"steps\":[{\"type\":\"command\",\"value\":\"say hi\"}]}")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("events/bad.json"), "{not json".getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("events/../evil.json"),
                ("{\"schemaVersion\":1,\"name\":\"events:evil\","
                        + "\"steps\":[{\"type\":\"command\",\"value\":\"say x\"}]}")
                        .getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve(".hidden.json"),
                ("{\"schemaVersion\":1,\"name\":\"x:y\","
                        + "\"steps\":[{\"type\":\"command\",\"value\":\"say x\"}]}")
                        .getBytes(StandardCharsets.UTF_8));
        FunctionRegistry registry = new FunctionRegistry();
        FunctionStorage.LoadResult result = FunctionStorage.loadAll(configDir, registry,
                FunctionLimits.defaults());
        assertNotNull(registry.get("events:ok"));
        assertNull(registry.get("events:bad"));
        assertTrue(result.failures.containsKey("events:bad"));
        // Reload preserves valid functions while reporting the failure.
        FunctionStorage.LoadResult reloaded = FunctionStorage.reload(configDir, registry,
                FunctionLimits.defaults());
        assertNotNull(registry.get("events:ok"));
        assertTrue(reloaded.failures.containsKey("events:bad"));
        // Duplicate ids are rejected.
        assertThrows(FunctionException.class, () -> registry.register(registry.get("events:ok")));
        // Malicious standalone names are never collected.
        assertTrue(FunctionStorage.isSafeName("ok.json"));
        assertTrue(!FunctionStorage.isSafeName("../evil.json"));
        assertTrue(!FunctionStorage.isSafeName(".hidden.json"));
        // Atomic write + delete round-trip.
        FunctionStorage.writeJson(configDir, "events:created", "{\"a\":1}");
        assertTrue(Files.isRegularFile(root.resolve("events/created.json")));
        assertTrue(FunctionStorage.delete(configDir, "events:created"));
    }

    @Test void storageRejectsOversizedFiles(@TempDir Path temp) throws Exception {
        Path configDir = temp.resolve("config");
        Path root = configDir.resolve("commandapi").resolve("functions");
        Files.createDirectories(root.resolve("ns"));
        byte[] big = new byte[300 * 1024];
        Arrays.fill(big, (byte) ' ');
        Files.write(root.resolve("ns/big.json"), big);
        FunctionRegistry registry = new FunctionRegistry();
        FunctionStorage.LoadResult result = FunctionStorage.loadAll(configDir, registry,
                FunctionLimits.defaults());
        assertTrue(result.failures.containsKey("ns:big"));
        assertNull(registry.get("ns:big"));
    }

    @Test void examplesAreValidAndCompileWithDefaults() throws Exception {
        java.io.File dir = new java.io.File("examples/functions");
        if (!dir.isDirectory()) {
            dir = new java.io.File("../examples/functions");
        }
        assertTrue(dir.isDirectory());
        int count = 0;
        for (java.io.File file : dir.listFiles()) {
            String name = file.getName();
            if (!name.endsWith(".json") && !name.endsWith(".mcfunction")) {
                continue;
            }
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            FunctionDefinition definition = FunctionParser.parse("example:tmp", name, text,
                    file.toString());
            FunctionValidator.validate(definition, FunctionLimits.defaults());
            // Compile with defaults only: every example must run with no arguments.
            FunctionRegistry registry = new FunctionRegistry();
            FunctionDefinition namespaced = new FunctionDefinition(
                    "example:" + name.replaceAll("\\..*$", ""), definition.getName(),
                    definition.getDescription(), definition.getParameters(),
                    definition.getSteps(), definition.getFormat(), definition.getOrigin());
            registry.register(namespaced);
            FunctionExecutionPlan plan = compiler(registry).compile(namespaced.getId(),
                    Collections.<String, Object>emptyMap());
            assertTrue(!plan.commands().isEmpty());
            count++;
        }
        assertTrue(count >= 10, "expected at least 10 examples, got " + count);
    }
}
