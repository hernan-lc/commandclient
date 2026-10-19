package com.commandapi.functions.steps;

import com.google.gson.JsonObject;

/**
 * Spawns a particle. Compiles to a {@code particle} command.
 * Count is a template so it accepts a literal or a ${parameter};
 * validated at compile time (1-10000).
 */
public final class ParticleStep implements FunctionStep {
    private final String particle;
    private final String position;
    private final String count;

    public ParticleStep(String particle, String position, String count) {
        if (particle == null || particle.isEmpty()) {
            throw new IllegalArgumentException("particle is required");
        }
        this.particle = particle;
        this.position = position == null || position.isEmpty() ? "~ ~ ~" : position;
        this.count = count == null || count.isEmpty() ? "20" : count;
    }

    /** Convenience constructor for a literal count. */
    public ParticleStep(String particle, String position, int count) {
        this(particle, position, String.valueOf(count));
    }

    public String getParticle() {
        return particle;
    }

    public String getPosition() {
        return position;
    }

    public String getCount() {
        return count;
    }

    @Override
    public String getType() {
        return "particle";
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "particle");
        json.addProperty("particle", particle);
        json.addProperty("position", position);
        json.addProperty("count", count);
        return json;
    }
}
