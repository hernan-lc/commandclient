package com.commandapi.functions;

import com.commandapi.functions.steps.CommandStep;
import com.commandapi.functions.steps.ConditionalStep;
import com.commandapi.functions.steps.DelayStep;
import com.commandapi.functions.steps.FunctionCallStep;
import com.commandapi.functions.steps.FunctionStep;
import com.commandapi.functions.steps.ParticleStep;
import com.commandapi.functions.steps.RepeatStep;
import com.commandapi.functions.steps.SoundStep;
import com.commandapi.functions.steps.SummonStep;
import com.commandapi.functions.steps.TitleStep;
import com.commandapi.security.CommandPolicy;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compiles a definition plus caller arguments into an immutable
 * {@link FunctionExecutionPlan}: parameters resolved and validated,
 * control flow expanded (repeat/nested/conditions), typed steps rendered to
 * Minecraft commands via {@link VersionCommandFormatter}, every expanded
 * command checked against the {@link CommandPolicy} and length limits.
 */
public final class FunctionCompiler {
    private final FunctionRegistry registry;
    private final FunctionLimits limits;
    private final CommandPolicy policy;
    private final String minecraftVersion;

    public FunctionCompiler(FunctionRegistry registry, FunctionLimits limits,
            CommandPolicy policy, String minecraftVersion) {
        this.registry = registry;
        this.limits = limits == null ? FunctionLimits.defaults() : limits;
        this.policy = policy;
        this.minecraftVersion = minecraftVersion == null ? "unknown" : minecraftVersion;
    }

    public FunctionExecutionPlan compile(String functionId, Map<String, Object> args)
            throws FunctionException {
        return compile(functionId, args, new HashSet<String>(), 0);
    }

    private FunctionExecutionPlan compile(String functionId, Map<String, Object> rawArgs,
            Set<String> callStack, int depth) throws FunctionException {
        if (callStack.contains(functionId)) {
            throw new FunctionException("Recursive function call: " + functionId
                    + " (cycle " + callStack + ")", "RECURSION");
        }
        if (depth > limits.getMaxNestingDepth()) {
            throw new FunctionException("Maximum nesting depth exceeded at " + functionId,
                    "LIMIT_EXCEEDED");
        }
        FunctionDefinition definition = registry.get(functionId);
        if (definition == null) {
            throw new FunctionException("Unknown function: " + functionId,
                    "UNKNOWN_FUNCTION");
        }
        if (rawArgs != null && rawArgs.size() > limits.getMaxArgs()) {
            throw new FunctionException("Too many arguments", "TOO_MANY_ARGUMENTS");
        }
        Map<String, Object> resolved = ParameterResolver.resolve(
                definition.getParameters(), rawArgs);
        Set<String> stack = new HashSet<String>(callStack);
        stack.add(functionId);
        List<FunctionExecutionPlan.Item> items = new ArrayList<FunctionExecutionPlan.Item>();
        List<JsonObject> structured = new ArrayList<JsonObject>();
        expandInto(definition.getSteps(), definition, resolved, stack, depth, items,
                structured);
        if (items.size() > limits.getMaxExpandedCommands()) {
            throw new FunctionException("Function " + functionId + " expands to "
                    + items.size() + " items, above the limit of "
                    + limits.getMaxExpandedCommands(), "LIMIT_EXCEEDED");
        }
        // Authorization: every fully expanded command must pass the allowlist.
        for (FunctionExecutionPlan.Item item : items) {
            if (item instanceof FunctionExecutionPlan.Command) {
                String command = ((FunctionExecutionPlan.Command) item).getCommand();
                if (command.length() > 512) {
                    throw new FunctionException("Expanded command too long ("
                            + command.length() + " chars)", "COMMAND_TOO_LONG");
                }
                if (policy != null && !policy.allows(command)) {
                    throw new FunctionException("Command denied by policy: " + command,
                            "COMMAND_DENIED");
                }
            }
        }
        return new FunctionExecutionPlan(functionId, resolved, items, structured);
    }

    private void expandInto(List<FunctionStep> steps, FunctionDefinition owner,
            Map<String, Object> resolved, Set<String> stack, int depth,
            List<FunctionExecutionPlan.Item> items, List<JsonObject> structured)
            throws FunctionException {
        for (FunctionStep step : steps) {
            if (step instanceof CommandStep) {
                String rendered = CommandTemplateRenderer.render(
                        ((CommandStep) step).getValue(), resolved, owner.getParameters());
                String normalized = rendered.startsWith("/") ? rendered : "/" + rendered;
                items.add(new FunctionExecutionPlan.Command(normalized));
                structured.add(structuredCommand(normalized));
            } else if (step instanceof SummonStep) {
                SummonStep summon = (SummonStep) step;
                String entity = CommandTemplateRenderer.render(summon.getEntity(), resolved,
                        owner.getParameters());
                String position = CommandTemplateRenderer.render(summon.getPosition(),
                        resolved, owner.getParameters());
                String nbt = CommandTemplateRenderer.render(summon.getNbt(), resolved,
                        owner.getParameters());
                String command = "/" + VersionCommandFormatter.summon(minecraftVersion,
                        entity, position, nbt);
                items.add(new FunctionExecutionPlan.Command(command));
                structured.add(structuredCommand(command));
            } else if (step instanceof TitleStep) {
                TitleStep title = (TitleStep) step;
                String target = CommandTemplateRenderer.render(title.getTarget(), resolved,
                        owner.getParameters());
                String titleText = CommandTemplateRenderer.render(title.getTitle(), resolved,
                        owner.getParameters());
                String subtitleText = CommandTemplateRenderer.render(title.getSubtitle(),
                        resolved, owner.getParameters());
                int fadeIn = parseBoundedInt(CommandTemplateRenderer.render(
                        title.getFadeInTicks(), resolved, owner.getParameters()), 0, 1200,
                        "fadeInTicks");
                int stay = parseBoundedInt(CommandTemplateRenderer.render(
                        title.getStayTicks(), resolved, owner.getParameters()), 0, 12000,
                        "stayTicks");
                int fadeOut = parseBoundedInt(CommandTemplateRenderer.render(
                        title.getFadeOutTicks(), resolved, owner.getParameters()), 0, 1200,
                        "fadeOutTicks");
                items.add(new FunctionExecutionPlan.Command("/"
                        + VersionCommandFormatter.titleTimes(minecraftVersion, target,
                                fadeIn, stay, fadeOut)));
                structured.add(structuredCommand(
                        ((FunctionExecutionPlan.Command) items.get(items.size() - 1))
                                .getCommand()));
                if (!titleText.isEmpty()) {
                    items.add(new FunctionExecutionPlan.Command("/"
                            + VersionCommandFormatter.titleText(minecraftVersion, target,
                                    "title", titleText)));
                    structured.add(structuredCommand(
                            ((FunctionExecutionPlan.Command) items.get(items.size() - 1))
                                    .getCommand()));
                }
                if (!subtitleText.isEmpty()) {
                    items.add(new FunctionExecutionPlan.Command("/"
                            + VersionCommandFormatter.titleText(minecraftVersion, target,
                                    "subtitle", subtitleText)));
                    structured.add(structuredCommand(
                            ((FunctionExecutionPlan.Command) items.get(items.size() - 1))
                                    .getCommand()));
                }
            } else if (step instanceof SoundStep) {
                SoundStep sound = (SoundStep) step;
                String soundId = CommandTemplateRenderer.render(sound.getSound(), resolved,
                        owner.getParameters());
                String source = CommandTemplateRenderer.render(sound.getSource(), resolved,
                        owner.getParameters());
                String target = CommandTemplateRenderer.render(sound.getTarget(), resolved,
                        owner.getParameters());
                String position = CommandTemplateRenderer.render(sound.getPosition(),
                        resolved, owner.getParameters());
                double volume = parseBoundedDouble(CommandTemplateRenderer.render(
                        sound.getVolume(), resolved, owner.getParameters()), 0, 16,
                        true, true, "volume");
                double pitch = parseBoundedDouble(CommandTemplateRenderer.render(
                        sound.getPitch(), resolved, owner.getParameters()), 0, 2,
                        false, true, "pitch");
                String command = "/" + VersionCommandFormatter.playSound(minecraftVersion,
                        soundId, source, target, position, volume, pitch);
                items.add(new FunctionExecutionPlan.Command(command));
                structured.add(structuredCommand(command));
            } else if (step instanceof ParticleStep) {
                ParticleStep particle = (ParticleStep) step;
                String particleId = CommandTemplateRenderer.render(particle.getParticle(),
                        resolved, owner.getParameters());
                String position = CommandTemplateRenderer.render(particle.getPosition(),
                        resolved, owner.getParameters());
                int count = parseBoundedInt(CommandTemplateRenderer.render(
                        particle.getCount(), resolved, owner.getParameters()), 1, 10000,
                        "count");
                String command = "/" + VersionCommandFormatter.particle(minecraftVersion,
                        particleId, position, count);
                items.add(new FunctionExecutionPlan.Command(command));
                structured.add(structuredCommand(command));
            } else if (step instanceof DelayStep) {
                String rendered = CommandTemplateRenderer.render(
                        ((DelayStep) step).getTicks(), resolved, owner.getParameters());
                int ticks = parseNonNegativeInt(rendered, "delay ticks");
                if (ticks > limits.getMaxDelayTicks()) {
                    throw new FunctionException("Delay of " + ticks
                            + " ticks exceeds the limit of " + limits.getMaxDelayTicks(),
                            "LIMIT_EXCEEDED");
                }
                if (ticks > 0) {
                    items.add(new FunctionExecutionPlan.Delay(ticks));
                    JsonObject json = new JsonObject();
                    json.addProperty("type", "delay");
                    json.addProperty("ticks", ticks);
                    structured.add(json);
                }
            } else if (step instanceof RepeatStep) {
                RepeatStep repeat = (RepeatStep) step;
                String rendered = CommandTemplateRenderer.render(repeat.getTimes(),
                        resolved, owner.getParameters());
                int times = parseNonNegativeInt(rendered, "repeat times");
                if (times < 1 || times > limits.getMaxLoopIterations()) {
                    throw new FunctionException("Repeat count " + rendered + " out of range"
                            + " (max " + limits.getMaxLoopIterations() + ")",
                            "LIMIT_EXCEEDED");
                }
                for (int i = 0; i < times; i++) {
                    expandInto(repeat.getSteps(), owner, resolved, stack, depth + 1, items,
                            structured);
                }
            } else if (step instanceof FunctionCallStep) {
                FunctionCallStep call = (FunctionCallStep) step;
                Map<String, Object> childArgs = new java.util.LinkedHashMap<String, Object>();
                for (Map.Entry<String, String> arg : call.getArgs().entrySet()) {
                    childArgs.put(arg.getKey(), (Object) CommandTemplateRenderer.render(
                            arg.getValue(), resolved, owner.getParameters()));
                }
                FunctionExecutionPlan child = compile(call.getName(), childArgs, stack,
                        depth + 1);
                items.addAll(child.getItems());
                structured.addAll(child.getStructuredSteps());
            } else if (step instanceof ConditionalStep) {
                ConditionalStep cond = (ConditionalStep) step;
                if (!resolved.containsKey(cond.getParameter())) {
                    throw new FunctionException("Condition parameter '"
                            + cond.getParameter() + "' was not provided", "MISSING_PARAMETER");
                }
                boolean result = evaluate(cond.getOperator(),
                        String.valueOf(resolved.get(cond.getParameter())), cond.getValue(),
                        resolved);
                List<FunctionStep> branch = result ? cond.getThenSteps() : cond.getElseSteps();
                expandInto(branch, owner, resolved, stack, depth + 1, items, structured);
            } else {
                throw new FunctionException("Unknown step type: " + step.getType(),
                        "UNKNOWN_STEP");
            }
        }
    }

    private static JsonObject structuredCommand(String command) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "command");
        json.addProperty("value", command);
        return json;
    }

    static boolean evaluate(String operator, String left, String right,
            Map<String, Object> resolved) throws FunctionException {
        // Render the expected value too: it may itself reference a parameter.
        String expected = CommandTemplateRenderer.render(right, resolved);
        Double leftNum = tryNumber(left);
        Double rightNum = tryNumber(expected);
        if ("eq".equals(operator)) {
            return leftNum != null && rightNum != null
                    ? leftNum.doubleValue() == rightNum.doubleValue() : left.equals(expected);
        }
        if ("ne".equals(operator)) {
            return leftNum != null && rightNum != null
                    ? leftNum.doubleValue() != rightNum.doubleValue() : !left.equals(expected);
        }
        if (leftNum != null && rightNum != null) {
            if ("gt".equals(operator)) {
                return leftNum > rightNum;
            }
            if ("gte".equals(operator)) {
                return leftNum >= rightNum;
            }
            if ("lt".equals(operator)) {
                return leftNum < rightNum;
            }
            if ("lte".equals(operator)) {
                return leftNum <= rightNum;
            }
        } else if ("gt".equals(operator) || "gte".equals(operator) || "lt".equals(operator)
                || "lte".equals(operator)) {
            int comparison = left.compareTo(expected);
            if ("gt".equals(operator)) {
                return comparison > 0;
            }
            if ("gte".equals(operator)) {
                return comparison >= 0;
            }
            if ("lt".equals(operator)) {
                return comparison < 0;
            }
            return comparison <= 0;
        }
        throw new FunctionException("Unknown condition operator: " + operator,
                "INVALID_CONDITION");
    }

    private static Double tryNumber(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static int parseBoundedInt(String text, long min, long max, String what)
            throws FunctionException {
        long value;
        try {
            value = Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            throw new FunctionException("Invalid " + what + ": " + text, "INVALID_VALUE");
        }
        if (value < min || value > max) {
            throw new FunctionException("Field '" + what + "' out of range: " + text,
                    "OUT_OF_RANGE");
        }
        return (int) value;
    }

    private static double parseBoundedDouble(String text, double min, double max,
            boolean minInclusive, boolean maxInclusive, String what)
            throws FunctionException {
        double value;
        try {
            value = Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            throw new FunctionException("Invalid " + what + ": " + text, "INVALID_VALUE");
        }
        boolean lowOk = minInclusive ? value >= min : value > min;
        boolean highOk = maxInclusive ? value <= max : value < max;
        if (!lowOk || !highOk) {
            throw new FunctionException("Field '" + what + "' out of range: " + text,
                    "OUT_OF_RANGE");
        }
        return value;
    }

    private static int parseNonNegativeInt(String text, String what) throws FunctionException {
        try {
            long value = Long.parseLong(text.trim());
            if (value < 0 || value > Integer.MAX_VALUE) {
                throw new FunctionException("Invalid " + what + ": " + text,
                        "INVALID_VALUE");
            }
            return (int) value;
        } catch (NumberFormatException e) {
            throw new FunctionException("Invalid " + what + ": " + text, "INVALID_VALUE");
        }
    }
}
