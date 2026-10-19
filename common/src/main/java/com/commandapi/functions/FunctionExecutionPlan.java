package com.commandapi.functions;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable compiled execution plan: an ordered list of commands and bounded
 * tick delays plus a structured step view for preview responses.
 * Nothing here touches the network or the world.
 */
public final class FunctionExecutionPlan {
    /** One executable plan item. */
    public abstract static class Item {
        public abstract String kind();
    }

    /** A fully expanded, allowlisted command line. */
    public static final class Command extends Item {
        private final String command;

        public Command(String command) {
            this.command = command;
        }

        public String getCommand() {
            return command;
        }

        @Override
        public String kind() {
            return "command";
        }
    }

    /** A bounded tick delay. Consumes no queue capacity while waiting. */
    public static final class Delay extends Item {
        private final int ticks;

        public Delay(int ticks) {
            this.ticks = ticks;
        }

        public int getTicks() {
            return ticks;
        }

        @Override
        public String kind() {
            return "delay";
        }
    }

    private final String functionId;
    private final Map<String, Object> args;
    private final List<Item> items;
    private final List<JsonObject> structuredSteps;

    public FunctionExecutionPlan(String functionId, Map<String, Object> args,
            List<Item> items, List<JsonObject> structuredSteps) {
        this.functionId = functionId;
        this.args = Collections.unmodifiableMap(
                new java.util.LinkedHashMap<String, Object>(args));
        this.items = Collections.unmodifiableList(new ArrayList<Item>(items));
        this.structuredSteps = Collections.unmodifiableList(
                new ArrayList<JsonObject>(structuredSteps));
    }

    public String getFunctionId() {
        return functionId;
    }

    public Map<String, Object> getArgs() {
        return args;
    }

    public List<Item> getItems() {
        return items;
    }

    public List<JsonObject> getStructuredSteps() {
        return structuredSteps;
    }

    /** The command lines only, in execution order (delays excluded). */
    public List<String> commands() {
        List<String> commands = new ArrayList<String>();
        for (Item item : items) {
            if (item instanceof Command) {
                commands.add(((Command) item).getCommand());
            }
        }
        return commands;
    }

    public int delayCount() {
        int count = 0;
        for (Item item : items) {
            if (item instanceof Delay) {
                count++;
            }
        }
        return count;
    }
}
