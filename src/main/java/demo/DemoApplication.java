package demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.web.embedded.tomcat.TomcatProtocolHandlerCustomizer;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;

@SpringBootApplication
@RestController
public class DemoApplication {

    @Bean
    TomcatProtocolHandlerCustomizer<?> protocolHandlerVirtualThreads() {
        return protocolHandler -> protocolHandler.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }

    enum Mode { PLATFORM, VIRTUAL, CUSTOM }

    // Platform thread pool (simulating traditional Tomcat behavior)
    private final ExecutorService platformPool = Executors.newFixedThreadPool(50);
    private final ExecutorService smallPool = Executors.newFixedThreadPool(16);

    private volatile Mode mode = Mode.PLATFORM;
    private final Metrics metrics = new Metrics();

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    // ---- Mode control ----
    @GetMapping("/mode/{m}")
    public String setMode(@PathVariable String m) {
        mode = Mode.valueOf(m.toUpperCase());
        return "mode=" + mode;
    }

    @GetMapping("/mode")
    public String getMode() { return "mode=" + mode; }

    // Work simulation
    @GetMapping("/sleep")
    public Object sleep() throws Exception {
        int ms = 400;
        return switch (mode) {
            case PLATFORM -> {
                Future<String> future = platformPool.submit(() -> {
                    Thread.sleep(ms);
                    return ok("slept-platform", ms);
                });
                yield metrics.timed(() -> future.get());
            }

            case CUSTOM -> {
                Future<String> future = smallPool.submit(() -> {
                    Thread.sleep(ms);
                    return ok("slept-custom", ms);
                });
                yield metrics.timed(() -> future.get());
            }

            case VIRTUAL -> metrics.timed(() -> {
                Thread.sleep(ms);
                return ok("slept-virtual", ms);
            });
        };
    }

    // ---- Metrics endpoint ----
    @GetMapping("/metrics")
    public String metrics() {
        return metrics.snapshot();
    }

    @GetMapping("/reset")
    public String reset() {
        metrics.reset();
        return "ok";
    }

    @GetMapping("/threadinfo")
    public String threadInfo() {
        var bean = java.lang.management.ManagementFactory.getThreadMXBean();
        return "liveThreads=" + bean.getThreadCount();
    }

    // ---- Helpers ----
    static String ok(String kind, int ms) {
        return kind + " " + ms + " ms on " + Thread.currentThread();
    }

    static void busy(Duration d) {
        long end = System.nanoTime() + d.toNanos();
        while (System.nanoTime() < end) {}
    }

    // ---- Metrics collector ----
    static class Metrics {
        private final LongAdder inflight = new LongAdder();
        private final LongAdder ok = new LongAdder();
        private final LongAdder total = new LongAdder();

        private final int N = 4096;
        private final long[] lat = new long[N];
        private volatile int idx = 0;

        private volatile long lastTick = System.nanoTime();
        private volatile long lastCount = 0;
        private volatile double rps = 0.0;

        public String timed(Callable<String> call) throws Exception {
            long start = System.nanoTime();
            inflight.increment();
            try {
                String result = call.call();
                ok.increment();
                return result;
            } finally {
                inflight.decrement();
                total.increment();
                long dur = System.nanoTime() - start;
                lat[idx++ & (N - 1)] = dur;
                long now = System.nanoTime();
                if (now - lastTick >= TimeUnit.SECONDS.toNanos(1)) {
                    long count = total.sum();
                    long delta = count - lastCount;
                    rps = delta / ((now - lastTick) / 1_000_000_000.0);
                    lastCount = count;
                    lastTick = now;
                }
            }
        }

        public String snapshot() {
            long[] copy = Arrays.copyOf(lat, N);
            Arrays.sort(copy);
            long p50 = copy[N * 50 / 100];
            long p95 = copy[N * 95 / 100];
            long p99 = copy[N * 99 / 100];

            return """
    --- Server Performance Metrics ---
    Total requests:     %d
    Successful (OK):    %d
    In-flight (running): %d
    Throughput (RPS):   %.1f requests/sec

    Latency percentiles:
      p50 (median):     %.1f ms
      p95 (slow 5%%):    %.1f ms
      p99 (slow 1%%):    %.1f ms
    ---------------------------------
    """.formatted(
                    total.sum(),
                    ok.sum(),
                    inflight.sum(),
                    rps,
                    p50 / 1_000_000.0,
                    p95 / 1_000_000.0,
                    p99 / 1_000_000.0
            );
        }

        public void reset() {
            inflight.reset();
            ok.reset();
            total.reset();
            Arrays.fill(lat, 0);
            idx = 0;
            lastTick = System.nanoTime();
            lastCount = 0;
            rps = 0.0;
        }
    }
}