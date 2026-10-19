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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Structural validation of parsed definitions: unknown step types are rejected
 * at parse time, this validator checks field ranges, placeholder references,
 * nesting depth and static expansion budgets.
 */
public final class FunctionValidator {
    private static final Set<String> CONDITION_OPS = new HashSet<String>(Arrays.asList(
            "eq", "ne", "gt", "gte", "lt", "lte"));

    private FunctionValidator() {
    }

    public static void validate(FunctionDefinition definition, FunctionLimits limits)
            throws FunctionException {
        if (definition.getSteps().isEmpty()) {
            throw new FunctionException("Function " + definition.getId()
                    + " has no steps", "EMPTY_FUNCTION");
        }
        Map<String, FunctionParameter> params = definition.getParameters();
        FunctionLimits effective = limits == null ? FunctionLimits.defaults() : limits;
        int expanded = countExpanded(definition.getSteps(), params, 0, effective);
        if (expanded > effective.getMaxExpandedCommands()) {
            throw new FunctionException("Function " + definition.getId() + " expands to "
                    + expanded + " commands, above the limit of "
                    + effective.getMaxExpandedCommands(), "LIMIT_EXCEEDED");
        }
    }

    private static int countExpanded(List<FunctionStep> steps,
            Map<String, FunctionParameter> params, int depth, FunctionLimits limits)
            throws FunctionException {
        if (depth > limits.getMaxNestingDepth()) {
            throw new FunctionException("Maximum nesting depth exceeded", "LIMIT_EXCEEDED");
        }
        int total = 0;
        int index = 0;
        for (FunctionStep step : steps) {
            String where = "step " + index + " (" + step.getType() + ")";
            if (step instanceof CommandStep) {
                String value = ((CommandStep) step).getValue();
                if (value.length() > 512) {
                    throw new FunctionException("Command too long in " + where, "COMMAND_TOO_LONG");
                }
                CommandTemplateRenderer.checkReferences(value, params, where);
                total += 1;
            } else if (step instanceof SummonStep) {
                SummonStep summon = (SummonStep) step;
                CommandTemplateRenderer.checkReferences(summon.getEntity(), params, where);
                CommandTemplateRenderer.checkReferences(summon.getPosition(), params, where);
                total += 1;
            } else if (step instanceof TitleStep) {
                TitleStep title = (TitleStep) step;
                CommandTemplateRenderer.checkReferences(title.getTarget(), params, where);
                CommandTemplateRenderer.checkReferences(title.getTitle(), params, where);
                CommandTemplateRenderer.checkReferences(title.getSubtitle(), params, where);
                checkTicksTemplate(title.getFadeInTicks(), 1200, "fadeInTicks", params,
                        where);
                checkTicksTemplate(title.getStayTicks(), 12000, "stayTicks", params, where);
                checkTicksTemplate(title.getFadeOutTicks(), 1200, "fadeOutTicks", params,
                        where);
                total += titleCommandCount(title);
            } else if (step instanceof SoundStep) {
                SoundStep sound = (SoundStep) step;
                CommandTemplateRenderer.checkReferences(sound.getSound(), params, where);
                CommandTemplateRenderer.checkReferences(sound.getTarget(), params, where);
                checkNumericTemplate(sound.getVolume(), 0, 16, true, true, "volume",
                        params, where);
                checkNumericTemplate(sound.getPitch(), 0, 2, false, true, "pitch",
                        params, where);
                total += 1;
            } else if (step instanceof ParticleStep) {
                ParticleStep particle = (ParticleStep) step;
                CommandTemplateRenderer.checkReferences(particle.getParticle(), params, where);
                checkNumericTemplate(particle.getCount(), 1, 10000, true, true, "count",
                        params, where);
                total += 1;
            } else if (step instanceof DelayStep) {
                long ticks = staticLong(((DelayStep) step).getTicks(), -1L);
                if (ticks >= 0 && ticks > limits.getMaxDelayTicks()) {
                    throw new FunctionException("Delay too long in " + where
                            + ": max " + limits.getMaxDelayTicks() + " ticks", "LIMIT_EXCEEDED");
                }
                // Delays emit no commands.
            } else if (step instanceof RepeatStep) {
                RepeatStep repeat = (RepeatStep) step;
                long times = staticLong(repeat.getTimes(), -1L);
                if (times >= 0) {
                    if (times < 1 || times > limits.getMaxLoopIterations()) {
                        throw new FunctionException("Repeat count out of range in " + where
                                + ": max " + limits.getMaxLoopIterations(), "LIMIT_EXCEEDED");
                    }
                } else {
                    CommandTemplateRenderer.checkReferences(repeat.getTimes(), params, where);
                }
                int inner = countExpanded(repeat.getSteps(), params, depth + 1, limits);
                total += times >= 0 ? inner * (int) times : inner;
            } else if (step instanceof FunctionCallStep) {
                FunctionCallStep call = (FunctionCallStep) step;
                for (Map.Entry<String, String> arg : call.getArgs().entrySet()) {
                    CommandTemplateRenderer.checkReferences(arg.getValue(), params,
                            where + " arg " + arg.getKey());
                }
                total += 1; // Budgeted precisely at compile time with cycle detection.
            } else if (step instanceof ConditionalStep) {
                ConditionalStep cond = (ConditionalStep) step;
                if (!CONDITION_OPS.contains(cond.getOperator())) {
                    throw new FunctionException("Unknown condition operator '"
                            + cond.getOperator() + "' in " + where, "INVALID_CONDITION");
                }
                if (!params.containsKey(cond.getParameter())) {
                    throw new FunctionException("Unknown condition parameter '"
                            + cond.getParameter() + "' in " + where, "UNKNOWN_PARAMETER");
                }
                int thenCount = countExpanded(cond.getThenSteps(), params, depth + 1, limits);
                int elseCount = countExpanded(cond.getElseSteps(), params, depth + 1, limits);
                total += Math.max(thenCount, elseCount);
            } else {
                throw new FunctionException("Unknown step type in " + where, "UNKNOWN_STEP");
            }
            index++;
        }
        return total;
    }

    private static void checkTicksTemplate(String raw, long max, String field,
            Map<String, FunctionParameter> params, String where) throws FunctionException {
        Double literal = tryLiteral(raw);
        if (literal == null) {
            CommandTemplateRenderer.checkReferences(raw, params, where + " " + field);
            return;
        }
        if (literal < 0 || literal > max || literal != Math.floor(literal)) {
            throw new FunctionException("Title " + field + " out of range in " + where,
                    "OUT_OF_RANGE");
        }
    }

    private static void checkNumericTemplate(String raw, double min, double max,
            boolean minInclusive, boolean maxInclusive, String field,
            Map<String, FunctionParameter> params, String where) throws FunctionException {
        Double literal = tryLiteral(raw);
        if (literal == null) {
            CommandTemplateRenderer.checkReferences(raw, params, where + " " + field);
            return;
        }
        boolean lowOk = minInclusive ? literal >= min : literal > min;
        boolean highOk = maxInclusive ? literal <= max : literal < max;
        if (!lowOk || !highOk) {
            throw new FunctionException("Field '" + field + "' out of range in " + where,
                    "OUT_OF_RANGE");
        }
    }

    private static Double tryLiteral(String raw) {
        if (raw == null || raw.contains("${")) {
            return null;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Title compiles to up to 3 commands: times + title + subtitle. */
    static int titleCommandCount(TitleStep title) {
        int count = 1; // times always emitted for deterministic fades
        if (!title.getTitle().isEmpty()) {
            count++;
        }
        if (!title.getSubtitle().isEmpty()) {
            count++;
        }
        return count;
    }

    private static long staticLong(String raw, long fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Long.parseLong(raw.trim().replace("${", "").replace("}", ""));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
