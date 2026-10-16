import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
public final class BridgeSmokeFixture extends JavaPlugin implements Listener {
    private int attempts;
    public void onEnable() { getServer().getPluginManager().registerEvents(this, this); }
    @EventHandler public void joined(PlayerJoinEvent event) {
        if (event.getPlayer().getName().equals("BridgeTest")) event.getPlayer().addAttachment(this, "commandapi.execute", true);
    }
    @EventHandler public void command(PlayerCommandPreprocessEvent event) {
        if (!event.getPlayer().getName().equals("BridgeTest")) return;
        attempts++;
        getLogger().info("SMOKE attempt=" + attempts + " op=" + event.getPlayer().isOp() + " timePermission=" + event.getPlayer().hasPermission("minecraft.command.time"));
        if (event.getMessage().equals("/weather rain")) { event.setCancelled(true); getLogger().info("SMOKE cancelled weather command"); }
    }
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof ConsoleCommandSender)) return false;
        for (World world : getServer().getWorlds()) sender.sendMessage("SMOKE world=" + world.getName() + " time=" + world.getTime() + " attempts=" + attempts);
        return true;
    }
}
