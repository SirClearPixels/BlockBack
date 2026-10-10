package us.ironcladnetwork.blockback;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class UpdateCheckerTest {
    static String release(String tag) {
        return "{\"tag_name\":\"" + tag + "\",\"draft\":false,\"prerelease\":false,"
                + "\"published_at\":\"2026-10-10T01:00:00Z\",\"html_url\":\"https://evil.invalid\"}";
    }

    @Test void publishedReleaseFlowsThroughCheckCacheAndRealJoinHandler() {
        List<String> logs = new ArrayList<>(), messages = new ArrayList<>();
        AtomicInteger requests = new AtomicInteger();
        ManualScheduler scheduler = new ManualScheduler();
        try (UpdateChecker checker = new UpdateChecker("1.5.0", () -> {
            requests.incrementAndGet();
            return new UpdateChecker.Response(200, release("v1.6.0"));
        }, scheduler, logger(logs))) {
            checker.start();
            scheduler.tasks.getFirst().run();
            checker.onJoin(new PlayerJoinEvent(player(true, messages), null));
            assertEquals(1, messages.size());
            assertTrue(messages.getFirst().contains("1.5.0"));
            assertTrue(messages.getFirst().contains("1.6.0"));
            assertTrue(messages.getFirst().contains("https://github.com/SirClearPixels/BlockBack/releases/tag/v1.6.0"));
            assertFalse(messages.getFirst().contains("evil"));
            int beforeJoin = requests.get();
            checker.onJoin(new PlayerJoinEvent(player(false, messages), null));
            assertEquals(beforeJoin, requests.get());
            assertEquals(1, messages.size());
            assertEquals(1, logs.size());
        }
    }

    @Test void numericVersionsAndMalformedPublicationAreConservative() {
        assertTrue(UpdateChecker.Version.parse("v1.10.0+build.7")
                .compareTo(UpdateChecker.Version.parse("1.9.0")) > 0);
        assertTrue(UpdateChecker.Version.parse("999999999999999999999999.0.0")
                .compareTo(UpdateChecker.Version.parse("2.0.0")) > 0);
        for (String bad : List.of("1.6", "1.6.0-rc.1", "1.6.0§a", "1.06.0", "1.6.0+", "1.6.0/x")) {
            assertNull(UpdateChecker.Version.parse(bad), bad);
        }
        assertNull(UpdateChecker.offer("1.5.0", release("v1.5.0")));
        assertNull(UpdateChecker.offer("1.7.0", release("v1.6.0")));
        assertNull(UpdateChecker.offer("1.5.0-SNAPSHOT", release("v1.6.0")));
        for (String bad : List.of("{}", "[]", "null", "broken",
                release("v1.6.0").replace("\"draft\":false", "\"draft\":true"),
                release("v1.6.0").replace("\"prerelease\":false", "\"prerelease\":true"),
                release("v1.6.0").replace("\"draft\":false", "\"draft\":\"false\""),
                release("v1.6.0").replace("2026-10-10T01:00:00Z", ""),
                release("v1.6.0").replace("2026-10-10T01:00:00Z", "bad"),
                release("v1.6.0").replace("\"tag_name\":\"v1.6.0\"", "\"tag_name\":123"))) {
            assertNull(UpdateChecker.offer("1.5.0", bad), bad);
        }
    }

    static Logger logger(List<String> messages) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            public void publish(LogRecord record) { messages.add(record.getMessage()); }
            public void flush() { }
            public void close() { }
        });
        return logger;
    }

    static class ManualScheduler extends ScheduledThreadPoolExecutor {
        final List<Runnable> tasks = new ArrayList<>();
        final List<ManualFuture> futures = new ArrayList<>();
        long initialDelay, interval;
        TimeUnit unit;
        ManualScheduler() { super(1); }
        @Override public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
            return capture(task, delay, 0, unit);
        }
        @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long delay, long interval, TimeUnit unit) {
            return capture(task, delay, interval, unit);
        }
        ScheduledFuture<?> capture(Runnable task, long delay, long interval, TimeUnit unit) {
            tasks.add(task);
            this.initialDelay = delay;
            this.interval = interval;
            this.unit = unit;
            ManualFuture future = new ManualFuture();
            futures.add(future);
            return future;
        }
    }

    static class ManualFuture extends FutureTask<Void> implements ScheduledFuture<Void> {
        ManualFuture() { super(() -> null); }
        public long getDelay(TimeUnit unit) { return 0; }
        public int compareTo(Delayed other) { return 0; }
    }

    static Player player(boolean permitted, List<String> messages) {
        Thread joinThread = Thread.currentThread();
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("hasPermission")) {
                        assertSame(joinThread, Thread.currentThread());
                        assertEquals("blockback.update", args[0]);
                        return permitted;
                    }
                    if (method.getName().equals("sendMessage")) {
                        assertSame(joinThread, Thread.currentThread());
                        messages.add((String) args[0]);
                        return null;
                    }
                    return CopperBackTest.defaultValue(method.getReturnType());
                });
    }
}
