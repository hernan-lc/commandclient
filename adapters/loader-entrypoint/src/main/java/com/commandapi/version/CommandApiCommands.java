package com.commandapi.version;

import com.commandapi.config.ConfigCommandHandler;

/**
 * Entry point for the in-game {@code /commandapi} commands. Version modules
 * only intercept the outgoing chat line and cancel it when this class claims
 * it; all parsing and config work lives in version independent code.
 *
 * <p>Both methods return true when the line was a {@code /commandapi} command
 * (the caller must cancel the send) and false otherwise. Feedback is shown as
 * local chat lines, never sent to the server.</p>
 */
public final class CommandApiCommands {

    private CommandApiCommands() {
    }

    /**
     * A raw chat message as typed, leading slash included
     * (legacy {@code LocalPlayer.chat} passes the full text).
     */
    public static boolean dispatchChat(Object player, String message) {
        if (message == null) {
            return false;
        }
        String line = message.trim();
        if (!isOurs(line, "/")) {
            return false;
        }
        runAndTell(player, argsAfter(line.substring(1)));
        return true;
    }

    /**
     * A command without the slash ({@code commandSigned} and
     * {@code ClientPacketListener.sendCommand} already strip it).
     */
    public static boolean dispatchCommand(Object player, String command) {
        if (command == null) {
            return false;
        }
        String line = command.trim();
        if (!isOurs(line, "")) {
            return false;
        }
        runAndTell(player, argsAfter(line));
        return true;
    }

    private static boolean isOurs(String line, String prefix) {
        String name = prefix + ConfigCommandHandler.COMMAND_NAME;
        return line.equals(name) || line.startsWith(name + " ");
    }

    private static String argsAfter(String line) {
        int space = line.indexOf(' ');
        if (space < 0) {
            return "";
        }
        return line.substring(space + 1);
    }

    private static void runAndTell(Object player, String args) {
        CommandApiMod mod = CommandApiMod.getInstance();
        if (mod == null || mod.getService() == null) {
            CommandApiFeedback.tell(player, "[CommandAPI] Not ready yet - try again in a moment.");
            return;
        }
        for (String response : mod.getService().runCommand(args)) {
            CommandApiFeedback.tell(player, response);
        }
    }
}
