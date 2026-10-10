package us.ironcladnetwork.blockback;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Owns release discovery only; workers never access Bukkit players or configuration. */
final class UpdateChecker implements Listener, AutoCloseable {
    static final URI ENDPOINT = URI.create("https://api.github.com/repos/SirClearPixels/BlockBack/releases/latest");
    static final Duration BUDGET = Duration.ofSeconds(10);
    static final int BODY_LIMIT = 64 * 1024;
    private final String installed;
    private final Transport transport;
    private final ScheduledExecutorService executor;
    private final Logger logger;
    private final Set<String> announced = new HashSet<>();
    private volatile Offer cached;
    private ScheduledFuture<?> task;
    private long generation;
    private boolean closed;

    UpdateChecker(String installed, Logger logger) {
        this(installed, new JdkTransport(), Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "BlockBack-update-check");
            thread.setDaemon(true);
            return thread;
        }), logger);
    }

    UpdateChecker(String installed, Transport transport, ScheduledExecutorService executor, Logger logger) {
        this.installed = installed;
        this.transport = transport;
        this.executor = executor;
        this.logger = logger;
    }

    synchronized void start() {
        applyEnabled(true);
    }

    synchronized void applyEnabled(boolean enabled) {
        if (closed) return;
        if (enabled) {
            if (task != null) return;
            long active = ++generation;
            task = executor.scheduleWithFixedDelay(() -> check(active), 0, 24, TimeUnit.HOURS);
        } else {
            ++generation;
            cached = null;
            if (task != null) task.cancel(true);
            task = null;
        }
    }

    void check(long active) {
        synchronized (this) {
            if (closed || task == null || active != generation) return;
        }
        Offer result = null;
        try {
            Response response = transport.fetch();
            if (response.status() == 200) result = offer(installed, response.body());
            else if (response.status() != 404) logger.fine("BlockBack update check: HTTP " + response.status());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            // Expected outages are diagnostics, never gameplay failures or repeated console warnings.
            logger.fine("BlockBack update check unavailable (" + exception.getClass().getSimpleName() + ").");
        }
        synchronized (this) {
            if (closed || task == null || active != generation) return;
            cached = result;
            if (result != null && announced.add(result.version())) logger.info(result.message());
        }
    }

    @EventHandler
    public synchronized void onJoin(PlayerJoinEvent event) {
        Offer offer = cached;
        if (offer != null && event.getPlayer().hasPermission("blockback.update")) {
            event.getPlayer().sendMessage(offer.message());
        }
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        ++generation;
        cached = null;
        if (task != null) task.cancel(true);
        executor.shutdownNow();
        transport.close();
    }

    record Response(int status, String body) { }
    interface Transport extends AutoCloseable {
        Response fetch() throws Exception;
        @Override default void close() { }
    }
    record Offer(String installed, String version, String url) {
        String message() {
            return "[BlockBack] Update available: " + installed + " -> " + version + ". " + url;
        }
    }

    static Offer offer(String installed, String json) {
        try {
            Version current = Version.parse(installed);
            if (current == null) return null;
            JsonObject release = JsonParser.parseString(json).getAsJsonObject();
            if (!explicitFalse(release.get("draft")) || !explicitFalse(release.get("prerelease"))) return null;
            String published = string(release.get("published_at"));
            if (published == null || Instant.parse(published).isAfter(Instant.now())) return null;
            String tag = string(release.get("tag_name"));
            Version available = Version.parse(tag);
            if (available == null || available.compareTo(current) <= 0) return null;
            return new Offer(installed, available.normalized(),
                    "https://github.com/SirClearPixels/BlockBack/releases/tag/" + tag);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static boolean explicitFalse(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && !value.getAsBoolean();
    }
    private static String string(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }

    record Version(BigInteger major, BigInteger minor, BigInteger patch) implements Comparable<Version> {
        private static final Pattern STABLE = Pattern.compile("v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?");
        static Version parse(String text) {
            if (text == null || text.length() > 256) return null;
            Matcher matcher = STABLE.matcher(text);
            return matcher.matches() ? new Version(new BigInteger(matcher.group(1)),
                    new BigInteger(matcher.group(2)), new BigInteger(matcher.group(3))) : null;
        }
        String normalized() { return major + "." + minor + "." + patch; }
        public int compareTo(Version other) {
            int order = major.compareTo(other.major);
            if (order == 0) order = minor.compareTo(other.minor);
            return order == 0 ? patch.compareTo(other.patch) : order;
        }
    }

    /** Full-body future completes only after bounded subscriber receives EOF. */
    static final class JdkTransport implements Transport {
        interface Exchange {
            CompletableFuture<HttpResponse<byte[]>> send(HttpRequest request, HttpResponse.BodyHandler<byte[]> handler);
        }
        private final Exchange exchange;
        private final Runnable shutdown;
        JdkTransport() {
            HttpClient client = HttpClient.newBuilder().connectTimeout(BUDGET)
                    .followRedirects(HttpClient.Redirect.NEVER).build();
            exchange = client::sendAsync;
            shutdown = client::shutdownNow;
        }
        JdkTransport(Exchange exchange, Runnable shutdown) {
            this.exchange = exchange;
            this.shutdown = shutdown;
        }
        public Response fetch() throws Exception {
            long started = System.nanoTime();
            HttpRequest request = HttpRequest.newBuilder(ENDPOINT).timeout(BUDGET)
                    .header("User-Agent", "BlockBack-update-checker")
                    .header("Accept", "application/vnd.github+json").GET().build();
            CompletableFuture<HttpResponse<byte[]>> future = exchange.send(request, info -> new LimitedBody());
            try {
                long remaining = BUDGET.toNanos() - (System.nanoTime() - started);
                if (remaining <= 0) throw new TimeoutException("Release request budget expired");
                HttpResponse<byte[]> response = future.get(remaining, TimeUnit.NANOSECONDS);
                return new Response(response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
            } finally {
                // Also cancels the underlying HTTP exchange if timeout, interrupt or body failure occurs.
                if (!future.isDone()) future.cancel(true);
            }
        }
        public void close() { shutdown.run(); }
    }

    static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody() { return body; }
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > BODY_LIMIT - bytes.size()) {
                    subscription.cancel();
                    body.completeExceptionally(new IOException("Release response exceeds 64 KiB"));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        public void onError(Throwable failure) { body.completeExceptionally(failure); }
        public void onComplete() { body.complete(bytes.toByteArray()); }
    }
}
