package com.commandapi.functions;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParameterValidationTest {
    private FunctionParameter param(String name, String type, Object def,
            boolean hasDefault, Double min, Double max, java.util.List<String> values) {
        return new FunctionParameter(name, type, !hasDefault, def, hasDefault, min, max,
                values, "");
    }

    private Map<String, FunctionParameter> declared(FunctionParameter... params) {
        Map<String, FunctionParameter> map = new HashMap<String, FunctionParameter>();
        for (FunctionParameter param : params) {
            map.put(param.getName(), param);
        }
        return map;
    }

    @Test void integerRangeIsEnforced() {
        Map<String, FunctionParameter> params = declared(
                param("count", "integer", 1L, true, 1.0, 20.0, null));
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("count", 99L);
        assertThrows(FunctionException.class, () -> ParameterResolver.resolve(params, args));
    }

    @Test void malformedIntegerIsRejected() {
        Map<String, FunctionParameter> params = declared(
                param("count", "integer", null, false, null, null, null));
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("count", "many");
        assertThrows(FunctionException.class, () -> ParameterResolver.resolve(params, args));
    }

    @Test void enumMembershipIsEnforced() throws Exception {
        Map<String, FunctionParameter> params = declared(
                param("mode", "enum", "easy", true, null, null, Arrays.asList("easy", "hard")));
        Map<String, Object> ok = new HashMap<String, Object>();
        ok.put("mode", "hard");
        assertEquals("hard", ParameterResolver.resolve(params, ok).get("mode"));
        Map<String, Object> bad = new HashMap<String, Object>();
        bad.put("mode", "nightmare");
        assertThrows(FunctionException.class, () -> ParameterResolver.resolve(params, bad));
    }

    @Test void entityMustBeAResourceLocation() {
        Map<String, FunctionParameter> params = declared(
                param("mob", "entity", null, false, null, null, null));
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("mob", "not an entity!!");
        assertThrows(FunctionException.class, () -> ParameterResolver.resolve(params, args));
    }

    @Test void selectorGrammarIsEnforced() throws Exception {
        Map<String, FunctionParameter> params = declared(
                param("target", "selector", null, false, null, null, null));
        Map<String, Object> ok = new HashMap<String, Object>();
        ok.put("target", "@a");
        assertEquals("@a", ParameterResolver.resolve(params, ok).get("target"));
        Map<String, Object> bad = new HashMap<String, Object>();
        bad.put("target", "@a; say hacked");
        assertThrows(FunctionException.class, () -> ParameterResolver.resolve(params, bad));
    }

    @Test void playerNameIsConstrained() {
        Map<String, FunctionParameter> params = declared(
                param("player", "player", null, false, null, null, null));
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("player", "a-very-long-name-that-is-invalid!!");
        assertThrows(FunctionException.class, () -> ParameterResolver.resolve(params, args));
    }

    @Test void positionGrammarIsEnforced() throws Exception {
        Map<String, FunctionParameter> params = declared(
                param("pos", "position", null, false, null, null, null));
        Map<String, Object> ok = new HashMap<String, Object>();
        ok.put("pos", "~ ~1 ~");
        assertEquals("~ ~1 ~", ParameterResolver.resolve(params, ok).get("pos"));
        Map<String, Object> bad = new HashMap<String, Object>();
        bad.put("pos", "nowhere");
        assertThrows(FunctionException.class, () -> ParameterResolver.resolve(params, bad));
    }

    @Test void booleanParsing() throws Exception {
        Map<String, FunctionParameter> params = declared(
                param("flag", "boolean", null, false, null, null, null));
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("flag", "yes");
        assertThrows(FunctionException.class, () -> ParameterResolver.resolve(params, args));
        args.put("flag", true);
        assertEquals(Boolean.TRUE, ParameterResolver.resolve(params, args).get("flag"));
    }

    @Test void positionalBindingFollowsDeclarationOrder() throws Exception {
        Map<String, FunctionParameter> params = declared(
                param("a", "string", null, false, null, null, null),
                param("b", "integer", null, false, null, null, null));
        Map<String, Object> resolved = ParameterResolver.resolvePositional(params,
                Arrays.<Object>asList("hi", 5L));
        assertEquals("hi", resolved.get("a"));
        assertEquals(5L, resolved.get("b"));
        assertThrows(FunctionException.class, () -> ParameterResolver.resolvePositional(params,
                Arrays.<Object>asList("hi", 5L, "extra")));
    }

    @Test void unknownParameterTypeIsRejectedAtDeclaration() {
        assertThrows(IllegalArgumentException.class, () ->
                new FunctionParameter("x", "frobnicate", true, null, false, null, null,
                        null, ""));
    }

    @Test void interpolationRejectsNewlineInjection() throws Exception {
        Map<String, FunctionParameter> params = declared(
                param("title", "string", null, false, null, null, null));
        // Resolver itself bars newlines in strings.
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("title", "hi\n/say hacked");
        assertThrows(FunctionException.class, () -> ParameterResolver.resolve(params, args));
        // And the renderer bars separator injection even when reached directly.
        Map<String, Object> values = new HashMap<String, Object>();
        values.put("title", "a;b");
        assertThrows(FunctionException.class, () ->
                CommandTemplateRenderer.render("say ${title}", values, params));
    }

    @Test void missingPlaceholderIsNeverAnEmptyString() {
        Map<String, Object> values = Collections.<String, Object>emptyMap();
        Map<String, FunctionParameter> params = declared(
                param("title", "string", null, false, null, null, null));
        assertThrows(FunctionException.class, () ->
                CommandTemplateRenderer.render("say ${title}", values, params));
        assertThrows(FunctionException.class, () ->
                CommandTemplateRenderer.render("say ${unknown}", values,
                        Collections.<String, FunctionParameter>emptyMap()));
    }

    @Test void escapedPlaceholderRendersLiterally() throws Exception {
        assertEquals("say ${title}",
                CommandTemplateRenderer.render("say $${title}",
                        Collections.<String, Object>emptyMap(),
                        Collections.<String, FunctionParameter>emptyMap()));
    }

    @Test void jsonTextIsEscapedWithSerialization() {
        assertEquals("say \\\"hi\\\"", CommandTemplateRenderer.escapeJsonText("say \"hi\""));
        assertTrue(CommandTemplateRenderer.quoteJsonText("a\"b").startsWith("\""));
        assertEquals("\"a\\\"b\"", CommandTemplateRenderer.quoteJsonText("a\"b"));
    }

    @Test void titleCommandsUseSerializedText() throws Exception {
        FunctionRegistry registry = new FunctionRegistry();
        registry.register(JsonFunctionParser.parse("ns:fn",
                "{\"schemaVersion\":1,\"name\":\"ns:fn\","
                        + "\"parameters\":{\"t\":{\"type\":\"string\"}},"
                        + "\"steps\":[{\"type\":\"title\",\"target\":\"@a\",\"title\":\"${t}\","
                        + "\"subtitle\":\"\"}]}", "test"));
        com.commandapi.security.CommandPolicy policy =
                new com.commandapi.security.CommandPolicy(Arrays.asList("*"));
        FunctionCompiler compiler = new FunctionCompiler(registry, FunctionLimits.defaults(),
                policy, "1.20.1");
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("t", "A \"quoted\" title");
        FunctionExecutionPlan plan = compiler.compile("ns:fn", args);
        assertTrue(plan.commands().get(1).contains("\\\"quoted\\\""));
    }
}
