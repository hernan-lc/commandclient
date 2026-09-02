package com.commandapi.version;

/**
 * Shows a local-only chat line. Everything is reflective on purpose: the
 * factory method for a literal text component and the player method that
 * displays it both changed names across the supported generations
 * ({@code TextComponent} vs {@code Component.literal},
 * {@code displayClientMessage} vs {@code sendSystemMessage}), and this class
 * is compiled once per Minecraft version. Reflection keeps one source file
 * working from 1.16.x to 26.x; when nothing matches, the line falls back to
 * the log so feedback is never silently lost.
 */
public final class CommandApiFeedback {

    private CommandApiFeedback() {
    }

    public static void tell(Object player, String text) {
        if (text == null) {
            return;
        }
        if (!tellInGame(player, text)) {
            System.out.println("[CommandAPI] " + text);
        }
    }

    private static boolean tellInGame(Object player, String text) {
        if (player == null) {
            return false;
        }
        try {
            Class<?> componentType = Class.forName("net.minecraft.network.chat.Component");
            Object component = literal(componentType, text);
            if (component == null) {
                return false;
            }
            try {
                player.getClass()
                        .getMethod("displayClientMessage", componentType, boolean.class)
                        .invoke(player, component, Boolean.FALSE);
                return true;
            } catch (NoSuchMethodException e) {
                player.getClass()
                        .getMethod("sendSystemMessage", componentType)
                        .invoke(player, component);
                return true;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static Object literal(Class<?> componentType, String text) {
        try {
            return componentType.getMethod("literal", String.class).invoke(null, text);
        } catch (ReflectiveOperationException | RuntimeException e) {
            try {
                return Class.forName("net.minecraft.network.chat.TextComponent")
                        .getConstructor(String.class)
                        .newInstance(text);
            } catch (ReflectiveOperationException | RuntimeException e2) {
                return null;
            }
        }
    }
}
