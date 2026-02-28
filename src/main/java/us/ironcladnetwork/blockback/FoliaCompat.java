package us.ironcladnetwork.blockback;

import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Centralized Folia compatibility utility.
 *
 * <p>Call {@link #init(Plugin)} once during {@code onEnable()} before any other manager
 * that reads {@link #IS_FOLIA}. This caches all reflection at startup so per-call
 * reflection overhead is eliminated and method-signature mismatches are caught early.</p>
 *
 * <p>If Folia is detected but reflection fails, a warning is logged and {@link #runAsync}
 * falls back to a daemon Thread. The plugin continues without crashing (graceful degradation).</p>
 */
public final class FoliaCompat {

    // SCHED-01: Single source of truth for Folia detection.
    // volatile guarantees visibility across threads without synchronization cost.
    public static volatile boolean IS_FOLIA = false;

    // Cached AsyncScheduler instance -- set once in init(), read-only afterwards.
    private static volatile Object asyncScheduler;

    // Cached runNow(Plugin, Consumer) method -- null if reflection failed.
    private static volatile Method methodRunNow;

    // Utility class -- no instantiation.
    private FoliaCompat() {}

    /**
     * Detects Folia and caches AsyncScheduler reflection. Must be called before any code
     * that reads {@link #IS_FOLIA} or calls {@link #runAsync}.
     *
     * @param plugin the owning plugin (used for server access and logging)
     */
    public static void init(Plugin plugin) {
        // SCHED-01: Detect Folia via its unique class; absent on Spigot/Paper.
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            IS_FOLIA = true;
        } catch (ClassNotFoundException e) {
            IS_FOLIA = false;
            return; // Not Folia -- nothing more to do.
        }

        // SCHED-02: Cache AsyncScheduler reflection once at startup.
        try {
            asyncScheduler = plugin.getServer().getClass()
                    .getMethod("getAsyncScheduler")
                    .invoke(plugin.getServer());

            methodRunNow = asyncScheduler.getClass()
                    .getMethod("runNow", Plugin.class, Consumer.class);

            plugin.getLogger().info("[BlockBack] Folia AsyncScheduler cached.");

        } catch (Exception e) {
            // SCHED-03: Graceful degradation -- warn and continue with Thread fallback.
            // Extract the real cause for a meaningful log message.
            Throwable cause = (e instanceof InvocationTargetException && e.getCause() != null)
                    ? e.getCause() : e;

            methodRunNow = null; // Ensure fallback path is used.
            plugin.getLogger().warning(
                    "[BlockBack] Folia detected but AsyncScheduler reflection failed ("
                    + cause.getMessage() + "). Async saves will use Thread fallback.");
            // Do NOT call disablePlugin() -- SAFE-05 constraint.
        }
    }

    /**
     * Runs {@code task} asynchronously using Folia's AsyncScheduler when available,
     * otherwise falls back to a daemon Thread named {@code threadName}.
     *
     * @param plugin     the owning plugin
     * @param task       the work to execute asynchronously
     * @param threadName name for the fallback daemon thread
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void runAsync(Plugin plugin, Runnable task, String threadName) {
        if (methodRunNow != null) {
            try {
                methodRunNow.invoke(asyncScheduler, plugin, (Consumer) ignored -> task.run());
                return;
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                plugin.getLogger().warning(
                        "[BlockBack] AsyncScheduler.runNow() failed (" + cause.getMessage()
                        + "). Falling back to Thread.");
            } catch (Exception e) {
                plugin.getLogger().warning(
                        "[BlockBack] AsyncScheduler.runNow() threw unexpected exception ("
                        + e.getMessage() + "). Falling back to Thread.");
            }
        }

        // Fallback: daemon thread so the JVM can exit even if the task hangs.
        Thread t = new Thread(task, threadName);
        t.setDaemon(true);
        t.start();
    }
}
