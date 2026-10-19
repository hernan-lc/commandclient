package com.commandapi.functions.steps;

import com.google.gson.JsonObject;

/**
 * Plays a sound. Compiles to a {@code playsound} command.
 * Volume/pitch are templates so they accept literals or ${parameters};
 * validated at compile time (volume 0-16, pitch 0-2).
 */
public final class SoundStep implements FunctionStep {
    private final String sound;
    private final String source;
    private final String target;
    private final String volume;
    private final String pitch;
    private final String position;

    public SoundStep(String sound, String source, String target,
            String volume, String pitch, String position) {
        if (sound == null || sound.isEmpty()) {
            throw new IllegalArgumentException("sound is required");
        }
        this.sound = sound;
        this.source = source == null || source.isEmpty() ? "master" : source;
        this.target = target == null || target.isEmpty() ? "@a" : target;
        this.volume = volume == null || volume.isEmpty() ? "1" : volume;
        this.pitch = pitch == null || pitch.isEmpty() ? "1" : pitch;
        this.position = position == null || position.isEmpty() ? "~ ~ ~" : position;
    }

    /** Convenience constructor for literal volume/pitch values. */
    public SoundStep(String sound, String source, String target,
            double volume, double pitch, String position) {
        this(sound, source, target, String.valueOf(volume), String.valueOf(pitch), position);
    }

    public String getSound() {
        return sound;
    }

    public String getSource() {
        return source;
    }

    public String getTarget() {
        return target;
    }

    public String getVolume() {
        return volume;
    }

    public String getPitch() {
        return pitch;
    }

    public String getPosition() {
        return position;
    }

    @Override
    public String getType() {
        return "sound";
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "sound");
        json.addProperty("sound", sound);
        json.addProperty("source", source);
        json.addProperty("target", target);
        json.addProperty("volume", volume);
        json.addProperty("pitch", pitch);
        json.addProperty("position", position);
        return json;
    }
}
