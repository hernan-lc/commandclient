import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.util.concurrent.*;
public final class ServerAgent {
    public static void agentmain(String action, Instrumentation instrumentation) throws Exception {
        Class<?> bukkit = null;
        for (Class<?> type : instrumentation.getAllLoadedClasses()) if (type.getName().equals("org.bukkit.Bukkit")) { bukkit = type; break; }
        if (bukkit == null) throw new IllegalStateException("Bukkit is not loaded");
        ClassLoader loader = bukkit.getClassLoader(); Class<?> pluginClass = loader.loadClass("org.bukkit.plugin.Plugin");
        Class<?> managerClass = loader.loadClass("org.bukkit.plugin.PluginManager");
        Object manager = bukkit.getMethod("getPluginManager").invoke(null);
        Object bridge = managerClass.getMethod("getPlugin",String.class).invoke(manager,"CommandApiBridge");
        Object fixture = managerClass.getMethod("getPlugin",String.class).invoke(manager,"BridgeSmokeFixture");
        Object scheduler = bukkit.getMethod("getScheduler").invoke(null); CompletableFuture<Void> finished = new CompletableFuture<>();
        loader.loadClass("org.bukkit.scheduler.BukkitScheduler").getMethod("runTask",pluginClass,Runnable.class).invoke(scheduler,fixture,(Runnable)() -> {
            try { managerClass.getMethod(action.equals("disable") ? "disablePlugin" : "enablePlugin",pluginClass).invoke(manager,bridge); finished.complete(null); }
            catch (Throwable error) { finished.completeExceptionally(error); }
        });
        finished.get(15,TimeUnit.SECONDS);
    }
}
