package us.ironcladnetwork.blockback;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.command.Command;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;

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
                release("v1.6.0").replace("\"draft\":false,", ""),
                release("v1.6.0").replace("\"prerelease\":false,", ""),
                release("v1.6.0").replace("\"published_at\":\"2026-10-10T01:00:00Z\",", ""),
                release("v1.6.0").replace("2026-10-10T01:00:00Z", ""),
                release("v1.6.0").replace("2026-10-10T01:00:00Z", "bad"),
                release("v1.6.0").replace("\"tag_name\":\"v1.6.0\"", "\"tag_name\":123"))) {
            assertNull(UpdateChecker.offer("1.5.0", bad), bad);
        }
    }

    @Test void dailyScheduleToggleDedupAndFailureRecovery() {
        List<String> logs = new ArrayList<>(), messages = new ArrayList<>();
        ManualScheduler scheduler = new ManualScheduler();
        AtomicInteger requests = new AtomicInteger();
        String[] body = {release("v1.6.0")};
        try (UpdateChecker checker = new UpdateChecker("1.5.0", () -> {
            requests.incrementAndGet();
            return new UpdateChecker.Response(200, body[0]);
        }, scheduler, logger(logs))) {
            checker.applyEnabled(false);
            assertTrue(scheduler.tasks.isEmpty());
            checker.applyEnabled(true);
            checker.applyEnabled(true);
            assertEquals(1, scheduler.tasks.size());
            assertEquals(0, scheduler.initialDelay);
            assertEquals(24, scheduler.interval);
            assertEquals(TimeUnit.HOURS, scheduler.unit);
            Runnable poll = scheduler.tasks.getFirst();
            poll.run();
            poll.run();
            assertEquals(1, logs.size());
            checker.onJoin(new PlayerJoinEvent(player(true, messages), null));
            checker.onJoin(new PlayerJoinEvent(player(true, messages), null));
            assertEquals(2, messages.size());
            body[0] = "broken";
            poll.run();
            checker.onJoin(new PlayerJoinEvent(player(true, messages), null));
            assertEquals(2, messages.size());
            body[0] = release("v1.7.0");
            poll.run();
            assertEquals(2, logs.size());
            checker.applyEnabled(false);
            assertTrue(scheduler.futures.getFirst().isCancelled());
            int before = requests.get();
            poll.run();
            checker.onJoin(new PlayerJoinEvent(player(true, messages), null));
            assertEquals(before, requests.get());
            assertEquals(2, messages.size());
            checker.applyEnabled(true);
            assertEquals(2, scheduler.tasks.size());
            scheduler.tasks.getLast().run();
            assertEquals(2, logs.size());
        }
        assertTrue(scheduler.isShutdown());
    }

    @Test void retiredInFlightChecksCannotPublishAfterDisableReenableOrClose() throws Exception {
        for (boolean shutdown : List.of(false, true)) {
            List<String> logs = new ArrayList<>(), messages = new ArrayList<>();
            ManualScheduler scheduler = new ManualScheduler();
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            AtomicInteger closed = new AtomicInteger();
            UpdateChecker.Transport transport = new UpdateChecker.Transport() {
                public UpdateChecker.Response fetch() throws Exception {
                    entered.countDown();
                    assertTrue(release.await(2, TimeUnit.SECONDS));
                    return new UpdateChecker.Response(200, release("v1.6.0"));
                }
                public void close() { closed.incrementAndGet(); }
            };
            try (UpdateChecker checker = new UpdateChecker("1.5.0", transport, scheduler, logger(logs));
                 ExecutorService worker = Executors.newSingleThreadExecutor()) {
                checker.start();
                Future<?> inFlight = worker.submit(scheduler.tasks.getFirst());
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                if (shutdown) checker.close();
                else {
                    checker.applyEnabled(false);
                    checker.applyEnabled(true);
                }
                release.countDown();
                inFlight.get(2, TimeUnit.SECONDS);
                checker.onJoin(new PlayerJoinEvent(player(true, messages), null));
                assertTrue(logs.isEmpty());
                assertTrue(messages.isEmpty());
                checker.close();
                checker.close();
                assertEquals(1, closed.get());
            } finally { release.countDown(); }
        }
    }

    @Test void failuresClearCacheAndNextPollRecovers() {
        for (int status : List.of(301, 302, 403, 404, 429, 500)) {
            failureThenRecovery(new UpdateChecker.Response(status, release("v1.6.0")), null);
        }
        for (Exception failure : List.of(new IOException("offline"), new TimeoutException(),
                new IllegalArgumentException("invalid"))) {
            failureThenRecovery(null, failure);
        }
    }

    void failureThenRecovery(UpdateChecker.Response failureResponse, Exception failure) {
        AtomicInteger calls = new AtomicInteger();
        List<String> logs = new ArrayList<>(), messages = new ArrayList<>();
        ManualScheduler scheduler = new ManualScheduler();
        try (UpdateChecker checker = new UpdateChecker("1.5.0", () -> {
            if (calls.incrementAndGet() == 2) {
                if (failure != null) throw failure;
                return failureResponse;
            }
            return new UpdateChecker.Response(200, release("v1.6.0"));
        }, scheduler, logger(logs))) {
            checker.start();
            Runnable poll = scheduler.tasks.getFirst();
            poll.run();
            poll.run();
            checker.onJoin(new PlayerJoinEvent(player(true, messages), null));
            assertTrue(messages.isEmpty());
            poll.run();
            checker.onJoin(new PlayerJoinEvent(player(true, messages), null));
            assertEquals(1, messages.size());
            assertEquals(1, logs.size());
        }
    }

    @Test void boundedSubscriberCountsBytesWithoutTrustingContentLength() throws Exception {
        AtomicInteger cancelled = new AtomicInteger();
        UpdateChecker.LimitedBody body = new UpdateChecker.LimitedBody();
        body.onSubscribe(subscription(cancelled));
        body.onNext(List.of(ByteBuffer.wrap(new byte[UpdateChecker.BODY_LIMIT])));
        assertFalse(body.getBody().toCompletableFuture().isDone());
        body.onComplete();
        assertEquals(UpdateChecker.BODY_LIMIT, body.getBody().toCompletableFuture().get().length);
        UpdateChecker.LimitedBody oversized = new UpdateChecker.LimitedBody();
        oversized.onSubscribe(subscription(cancelled));
        oversized.onNext(List.of(ByteBuffer.wrap(new byte[UpdateChecker.BODY_LIMIT - 1]), ByteBuffer.wrap(new byte[2])));
        assertEquals(1, cancelled.get());
        assertThrows(ExecutionException.class, () -> oversized.getBody().toCompletableFuture().get());
    }

    static Flow.Subscription subscription(AtomicInteger cancelled) {
        return new Flow.Subscription() {
            public void request(long count) { assertTrue(count > 0); }
            public void cancel() { cancelled.incrementAndGet(); }
        };
    }

    @Test void httpBudgetIncludesStalledBodyAndCancelsExchange() {
        CompletableFuture<HttpResponse<byte[]>> stalled = new CompletableFuture<>() {
            @Override public HttpResponse<byte[]> get(long timeout, TimeUnit unit) throws TimeoutException {
                assertTrue(timeout > 0);
                assertTrue(unit.toNanos(timeout) <= UpdateChecker.BUDGET.toNanos());
                throw new TimeoutException("body has not completed");
            }
        };
        AtomicInteger closed = new AtomicInteger();
        try (UpdateChecker.JdkTransport transport = new UpdateChecker.JdkTransport((request, handler) -> {
            assertEquals(UpdateChecker.ENDPOINT, request.uri());
            assertEquals("GET", request.method());
            assertEquals(UpdateChecker.BUDGET, request.timeout().orElseThrow());
            assertEquals("application/vnd.github+json", request.headers().firstValue("Accept").orElseThrow());
            assertTrue(request.headers().firstValue("User-Agent").orElseThrow().contains("BlockBack"));
            assertTrue(request.headers().firstValue("Authorization").isEmpty());
            assertInstanceOf(UpdateChecker.LimitedBody.class, handler.apply(null));
            return stalled;
        }, closed::incrementAndGet)) {
            assertThrows(TimeoutException.class, transport::fetch);
            assertTrue(stalled.isCancelled());
        }
        assertEquals(1, closed.get());
    }

    @Test void interruptCancelsExchangeAndRestoresWorkerInterruptStatus() {
        CompletableFuture<HttpResponse<byte[]>> stalled = new CompletableFuture<>() {
            @Override public HttpResponse<byte[]> get(long timeout, TimeUnit unit) throws InterruptedException {
                throw new InterruptedException();
            }
        };
        ManualScheduler scheduler = new ManualScheduler();
        try (UpdateChecker checker = new UpdateChecker("1.5.0",
                new UpdateChecker.JdkTransport((request, handler) -> stalled, () -> {}), scheduler, logger(new ArrayList<>()))) {
            checker.start();
            scheduler.tasks.getFirst().run();
            assertTrue(Thread.currentThread().isInterrupted());
            assertTrue(stalled.isCancelled());
        } finally { Thread.interrupted(); }
    }

    @Test void productionTransportWaitsForCompleteBodyAndReturnsStatus() throws Exception {
        try (UpdateChecker.JdkTransport transport = new UpdateChecker.JdkTransport((request, handler) -> {
            UpdateChecker.LimitedBody body = (UpdateChecker.LimitedBody) handler.apply(null);
            body.onSubscribe(subscription(new AtomicInteger()));
            byte[] json = release("v1.6.0").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            body.onNext(List.of(ByteBuffer.wrap(json)));
            CompletableFuture<HttpResponse<byte[]>> response = body.getBody().toCompletableFuture().thenApply(bytes ->
                    CopperBackTest.proxy(HttpResponse.class, (p, method, args) -> switch (method.getName()) {
                        case "statusCode" -> 200;
                        case "body" -> bytes;
                        default -> CopperBackTest.defaultValue(method.getReturnType());
                    }));
            assertFalse(response.isDone());
            body.onComplete();
            return response;
        }, () -> {})) {
            UpdateChecker.Response response = transport.fetch();
            assertEquals(200, response.status());
            assertNotNull(UpdateChecker.offer("1.5.0", response.body()));
        }
    }

    @Test void ownedWorkerFetchesOffCallerThreadAndNeverTouchesWaitingPlayer() throws Exception {
        CountDownLatch logged = new CountDownLatch(1);
        List<String> messages = new ArrayList<>();
        Thread caller = Thread.currentThread();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "UpdateCheckerTest-worker");
            thread.setDaemon(true);
            return thread;
        });
        Logger logger = logger(new ArrayList<>());
        logger.addHandler(new Handler() {
            public void publish(LogRecord record) { logged.countDown(); }
            public void flush() { }
            public void close() { }
        });
        CountDownLatch entered = new CountDownLatch(1), allowed = new CountDownLatch(1);
        try (UpdateChecker checker = new UpdateChecker("1.5.0", () -> {
            assertNotSame(caller, Thread.currentThread());
            entered.countDown();
            assertTrue(allowed.await(2, TimeUnit.SECONDS));
            return new UpdateChecker.Response(200, release("v1.6.0"));
        }, scheduler, logger)) {
            checker.start();
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            checker.onJoin(new PlayerJoinEvent(player(true, messages), null));
            assertTrue(messages.isEmpty());
            allowed.countDown();
            assertTrue(logged.await(2, TimeUnit.SECONDS));
            assertTrue(messages.isEmpty());
            checker.onJoin(new PlayerJoinEvent(player(true, messages), null));
            assertEquals(1, messages.size());
            checker.close();
            checker.onJoin(new PlayerJoinEvent(player(true, messages), null));
            assertEquals(1, messages.size());
        } finally { allowed.countDown(); }
    }

    @Test void bundledResourcesRetainOpPermissionAndEnabledDefault() {
        YamlConfiguration plugin = YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getResourceAsStream("/plugin.yml")));
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getResourceAsStream("/config.yml")));
        assertEquals("1.5.0", plugin.getString("version"));
        assertEquals("op", plugin.getString("permissions.blockback.update.default"));
        assertTrue(config.getBoolean("update-checker.enabled"));
    }

    @Test void actualReloadBranchCallsCallbackOnlyAfterPermission(@TempDir Path directory) throws Exception {
        if (org.bukkit.Bukkit.getServer() == null) CopperBackTest.installMinimalRegistry();
        PlayerDataManager settings = new PlayerDataManager(CopperBackTest.filePlugin(directory), false);
        SoundConfig sounds = new SoundConfig(CopperBackTest.filePlugin(directory));
        Field instance = SoundConfig.class.getDeclaredField("instance");
        instance.setAccessible(true);
        Object previous = instance.get(null);
        instance.set(null, sounds);
        AtomicInteger reloaded = new AtomicInteger();
        CommandManager commands = new CommandManager(() -> settings, reloaded::incrementAndGet);
        Command command = CopperBackTest.command("blockback");
        Thread caller = Thread.currentThread();
        boolean[] allowed = {false};
        List<String> messages = new ArrayList<>();
        Player player = CopperBackTest.proxy(Player.class, (p, method, args) -> {
            assertSame(caller, Thread.currentThread());
            if (method.getName().equals("hasPermission")) {
                assertEquals("blockback.reload", args[0]);
                return allowed[0];
            }
            if (method.getName().equals("sendMessage")) messages.add((String) args[0]);
            return CopperBackTest.defaultValue(method.getReturnType());
        });
        try {
            assertTrue(commands.onCommand(player, command, "blockback", new String[]{"reload"}));
            assertEquals(0, reloaded.get());
            allowed[0] = true;
            assertTrue(commands.onCommand(player, command, "blockback", new String[]{"reload"}));
            assertEquals(1, reloaded.get());
            assertTrue(messages.getLast().contains("reloaded successfully"));
        } finally { instance.set(null, previous); settings.shutdown(2); }
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
