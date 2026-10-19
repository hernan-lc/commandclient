package com.commandapi.functions;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Centralized version-specific Minecraft command formatting. Shared code must
 * never import Minecraft classes, so version differences are expressed here
 * as data keyed by the version string.
 *
 * <p>Differences handled:</p>
 * <ul>
 *   <li>Title commands: {@code title <target> title|subtitle|times ...} is
 *   stable across 1.16-26.x; JSON text components are serialized with Gson.</li>
 *   <li>Sound source names and particle argument order are stable; entity and
 *   particle ids are validated as resource locations by the caller.</li>
 * </ul>
 */
public final class VersionCommandFormatter {
    private static final Gson GSON = new Gson();

    private VersionCommandFormatter() {
    }

    /** Formats a title-times command for the target Minecraft version. */
    public static String titleTimes(String minecraftVersion, String target,
            int fadeIn, int stay, int fadeOut) {
        return "title " + target + " times " + fadeIn + " " + stay + " " + fadeOut;
    }

    /** Formats a title or subtitle command with a JSON text component. */
    public static String titleText(String minecraftVersion, String target, String kind,
            String text) {
        // All supported versions accept a JSON text component argument here.
        // Gson serialization guarantees escaping; never hand-assemble JSON.
        return "title " + target + " " + kind + " " + GSON.toJson(text);
    }

    /** Formats a playsound command. */
    public static String playSound(String minecraftVersion, String sound, String source,
            String target, String position, double volume, double pitch) {
        return "playsound " + sound + " " + source + " " + target + " "
                + position + " " + trim(volume) + " " + trim(pitch);
    }

    /** Formats a particle command. */
    public static String particle(String minecraftVersion, String particle,
            String position, int count) {
        return "particle " + particle + " " + position;
    }

    /** Formats a summon command with optional NBT. */
    public static String summon(String minecraftVersion, String entity, String position,
            String nbt) {
        String base = "summon " + entity + " " + position;
        if (nbt != null && !nbt.isEmpty()) {
            return base + " " + nbt;
        }
        return base;
    }

    /** Short capability summary for status responses. */
    public static JsonObject capabilities(String minecraftVersion, String transport) {
        JsonObject json = new JsonObject();
        json.addProperty("minecraft_version", minecraftVersion == null ? "unknown"
                : minecraftVersion);
        json.addProperty("transport", transport == null ? "unknown" : transport);
        json.addProperty("functions", true);
        json.addProperty("tick_delays", true);
        json.addProperty("world_queries", false);
        return json;
    }

    private static String trim(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}
