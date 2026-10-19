package com.commandapi.functions.steps;

import com.google.gson.JsonObject;

/**
 * Title/subtitle announcement. Compiles to {@code title} commands.
 * Tick fields are templates so they accept literals or ${parameters}.
 */
public final class TitleStep implements FunctionStep {
    private final String target;
    private final String title;
    private final String subtitle;
    private final String fadeInTicks;
    private final String stayTicks;
    private final String fadeOutTicks;

    public TitleStep(String target, String title, String subtitle,
            String fadeInTicks, String stayTicks, String fadeOutTicks) {
        this.target = target == null || target.isEmpty() ? "@a" : target;
        this.title = title == null ? "" : title;
        this.subtitle = subtitle == null ? "" : subtitle;
        this.fadeInTicks = fadeInTicks == null || fadeInTicks.isEmpty() ? "10" : fadeInTicks;
        this.stayTicks = stayTicks == null || stayTicks.isEmpty() ? "60" : stayTicks;
        this.fadeOutTicks = fadeOutTicks == null || fadeOutTicks.isEmpty() ? "20" : fadeOutTicks;
    }

    /** Convenience constructor for literal tick values. */
    public TitleStep(String target, String title, String subtitle,
            int fadeInTicks, int stayTicks, int fadeOutTicks) {
        this(target, title, subtitle, String.valueOf(fadeInTicks), String.valueOf(stayTicks),
                String.valueOf(fadeOutTicks));
    }

    public String getTarget() {
        return target;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getFadeInTicks() {
        return fadeInTicks;
    }

    public String getStayTicks() {
        return stayTicks;
    }

    public String getFadeOutTicks() {
        return fadeOutTicks;
    }

    @Override
    public String getType() {
        return "title";
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "title");
        json.addProperty("target", target);
        json.addProperty("title", title);
        json.addProperty("subtitle", subtitle);
        json.addProperty("fadeInTicks", fadeInTicks);
        json.addProperty("stayTicks", stayTicks);
        json.addProperty("fadeOutTicks", fadeOutTicks);
        return json;
    }
}
