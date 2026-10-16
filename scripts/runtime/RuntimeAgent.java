import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.util.concurrent.*;
public final class RuntimeAgent {
    public static void agentmain(String action, Instrumentation instrumentation) throws Exception {
        Class<?> minecraft = null;
        for (Class<?> type : instrumentation.getAllLoadedClasses()) if (type.getName().equals("net.minecraft.client.Minecraft")) { minecraft = type; break; }
        if (minecraft == null) throw new IllegalStateException("Minecraft is not loaded");
        Class<?> mcClass = minecraft; ClassLoader loader = minecraft.getClassLoader();
        Object client = minecraft.getMethod("getInstance").invoke(null); CompletableFuture<Void> finished = new CompletableFuture<>();
        ((Executor)client).execute(() -> {
            try {
                Class<?> screen = loader.loadClass("net.minecraft.client.gui.screens.Screen");
                Object title = loader.loadClass("net.minecraft.client.gui.screens.TitleScreen").getConstructor().newInstance();
                if (action.startsWith("load:")) {
                    mcClass.getMethod("loadLevel",String.class).invoke(client, action.substring(5));
                } else if (action.equals("newworld")) {
                    Class<?> create = loader.loadClass("net.minecraft.client.gui.screens.worldselection.CreateWorldScreen");
                    Object created = null;
                    try { created = create.getConstructor(screen).newInstance(title); }
                    catch (NoSuchMethodException missing) {
                        try { created = create.getMethod("createFresh",screen).invoke(null,title); }
                        catch (NoSuchMethodException modern) {
                            for (Method method : create.getMethods()) if (method.getName().equals("openFresh") && method.getParameterCount()==2) {
                                Object back = method.getParameterTypes()[1].equals(Runnable.class) ? (Runnable)() -> {} : title;
                                method.invoke(null,client,back); break;
                            }
                        }
                    }
                    if (created != null) mcClass.getMethod("setScreen",screen).invoke(client,created);
                } else if (action.equals("create")) {
                    Field field = mcClass.getField("screen"); Object current = field.get(client);
                    Method method = current.getClass().getDeclaredMethod("onCreate"); method.setAccessible(true); method.invoke(current);
                } else if (action.equals("disconnect")) {
                    try { mcClass.getMethod("disconnect", screen, boolean.class).invoke(client,title,false); }
                    catch (NoSuchMethodException legacy) {
                        try { mcClass.getMethod("disconnect",screen).invoke(client,title); }
                        catch (NoSuchMethodException oldest) { mcClass.getMethod("clearLevel",screen).invoke(client,title); }
                    }
                } else {
                    String address = action.substring("connect:".length());
                    Class<?> addressType = loader.loadClass("net.minecraft.client.multiplayer.resolver.ServerAddress");
                    Object serverAddress = addressType.getMethod("parseString",String.class).invoke(null,address);
                    Class<?> data = loader.loadClass("net.minecraft.client.multiplayer.ServerData");
                    Class<?> kind = loader.loadClass("net.minecraft.client.multiplayer.ServerData$Type");
                    Object server = data.getConstructor(String.class,String.class,kind).newInstance("Isolated smoke server",address,Enum.valueOf((Class)kind,"OTHER"));
                    Class<?> connect = loader.loadClass("net.minecraft.client.gui.screens.ConnectScreen");
                    for (Method method : connect.getMethods()) if (method.getName().equals("startConnecting")) {
                        Object[] args = new Object[method.getParameterCount()]; args[0]=title; args[1]=client; args[2]=serverAddress; args[3]=server; args[4]=false;
                        method.invoke(null,args); break;
                    }
                }
                finished.complete(null);
            } catch (Throwable error) { finished.completeExceptionally(error); }
        });
        finished.get(15,TimeUnit.SECONDS);
    }
}
