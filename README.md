# Virtual Threads Performance Demo 🚀

A practical demonstration comparing Java Virtual Threads (Project Loom) with traditional platform threads in Spring Boot + Tomcat, showing how virtual threads dramatically improve throughput for I/O-bound workloads.

## What This Project Demonstrates

This demo benchmarks three threading models handling blocking I/O operations:

- **PLATFORM** (50 threads): Traditional thread pool with 50 platform threads
- **CUSTOM** (16 threads): Small fixed thread pool showing bottleneck effects
- **VIRTUAL**: Java 21+ virtual threads with unlimited concurrency

## Key Findings

### I/O-Bound Workloads (`/sleep?ms=400`)
- **CUSTOM**: ~40 req/sec (bottlenecked by 16 threads)
- **PLATFORM**: ~125 req/sec (bottlenecked by 50 threads)
- **VIRTUAL**: 5,000+ req/sec (scales with load)

## Requirements

- **Java 21+** (for virtual threads support)
- **Maven** or **Gradle**
- **Spring Boot 3.2+**
- **hey** (load testing tool): `brew install hey` or download from [GitHub](https://github.com/rakyll/hey)

## Quick Start

### 1. Clone and Build

```bash
git clone https://github.com/PiotrJerzy13/java-virtual-threads-demo
cd boot-tomcat-loom
./mvn spring-boot:run
```

### 2. Run the Benchmark

```bash
chmod +x script.sh
./script.sh
```

## Project Structure

```
src/main/java/demo/
└── DemoApplication.java          # Main application with all logic
    ├── Threading Configuration   # Virtual threads setup
    ├── REST Endpoints            # /sleep, /cpu, /mode
    └── Metrics Collector         # Performance tracking

script.sh                         # Automated benchmark script
application.properties            # Tomcat configuration
```

## How It Works

### Virtual Threads Configuration

The magic happens here:

```java
@Bean
TomcatProtocolHandlerCustomizer<?> protocolHandlerVirtualThreads() {
    return protocolHandler -> 
        protocolHandler.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
}
```

This configures Tomcat to handle each HTTP request on a virtual thread instead of a platform thread.

### Threading Models

**PLATFORM Mode:**
```
HTTP Request → Virtual Thread (Tomcat) 
                    ↓
            Submit to 50-thread pool
                    ↓
            Platform thread sleeps
                    ↓
            Wait for result → Response
```

**CUSTOM Mode:**
```
HTTP Request → Virtual Thread (Tomcat)
                    ↓
            Submit to 16-thread pool
                    ↓
            Platform thread sleeps
                    ↓
            Wait for result → Response
```

**VIRTUAL Mode:**
```
HTTP Request → Virtual Thread (Tomcat)
                    ↓
            Sleep directly on virtual thread
                    ↓
            Response (no thread pool needed!)
```

## API Endpoints

### Control Endpoints

```bash
# Set threading mode
curl http://localhost:8080/mode/virtual
curl http://localhost:8080/mode/platform
curl http://localhost:8080/mode/custom

# Check current mode
curl http://localhost:8080/mode

# Reset metrics
curl http://localhost:8080/reset

# View current metrics
curl http://localhost:8080/metrics

# Thread info
curl http://localhost:8080/threadinfo
```

### Test Endpoints

```bash
# I/O-bound: simulate blocking operation (default 400ms)
curl "http://localhost:8080/sleep?ms=400"

# CPU-bound: busy-wait computation (default 50ms)
curl "http://localhost:8080/cpu?ms=50"
```

## Manual Testing

### Test Virtual Threads Performance

```bash
# Set mode
curl http://localhost:8080/mode/virtual

# Generate load: 2000 concurrent connections for 20 seconds
hey -z 20s -c 2000 "http://localhost:8080/sleep?ms=400"

# Check metrics
curl http://localhost:8080/metrics
```

### Compare with Platform Threads

```bash
# Switch to platform mode
curl http://localhost:8080/mode/platform
curl http://localhost:8080/reset

# Same load test
hey -z 20s -c 2000 "http://localhost:8080/sleep?ms=400"

# Check metrics
curl http://localhost:8080/metrics
```

## Understanding the Metrics

```
--- Server Performance Metrics ---
Total requests:     94710
Successful (OK):    94710
In-flight (running): 8
Throughput (RPS):   4649.8 requests/sec

Latency percentiles:
  p50 (median):     400.3 ms
  p95 (slow 5%):    402.1 ms
  p99 (slow 1%):    450.2 ms
```

- **Total requests**: Number of requests processed
- **Successful**: Requests completed without errors
- **In-flight**: Currently processing requests
- **Throughput**: Requests per second
- **Latency percentiles**: Response time distribution

## Configuration

### application.properties

```properties
# Async request timeout
spring.mvc.async.request-timeout=30s

# Tomcat thread pool (for PLATFORM mode comparison)
server.tomcat.threads.max=50
server.tomcat.accept-count=100
```

### Thread Pool Sizes

In `DemoApplication.java`:

```java
// Platform thread pool (simulating traditional Tomcat)
private final ExecutorService platformPool = Executors.newFixedThreadPool(50);

// Custom small pool (showing bottleneck)
private final ExecutorService smallPool = Executors.newFixedThreadPool(16);
```

Adjust these to experiment with different pool sizes.

## Why Virtual Threads Win for I/O

### Traditional Platform Threads

- **Heavy**: ~1MB memory per thread
- **Limited**: Typically 50-500 threads
- **Blocking = Waste**: Thread sits idle waiting for I/O

### Virtual Threads

- **Lightweight**: ~1KB memory per thread
- **Scalable**: Millions of threads possible
- **Smart**: Unmounts from carrier thread when blocked

### Example Scenario

**Handling 10,000 concurrent database queries (each takes 500ms):**

- **Platform threads (200 pool)**: 
  - Processes 200 at a time
  - Takes ~25 seconds total
  - Thread pool exhausted, requests queue up

- **Virtual threads**: 
  - Processes all 10,000 concurrently
  - Takes ~500ms total
  - No thread pool exhaustion

## When NOT to Use Virtual Threads

Virtual threads are **not** beneficial for:

- **CPU-intensive workloads** (image processing, calculations)
- **Short-lived requests** (simple CRUD without external calls)
- **Already non-blocking code** (reactive programming with WebFlux)

## Troubleshooting

### "Requests/sec is low for all modes"

Check if `hey` is installed correctly:
```bash
hey -version
```

### "Metrics show 0 requests"

Ensure the app is running:
```bash
curl http://localhost:8080/mode
```

### "Virtual mode not faster than platform"

Verify Java version (needs 21+):
```bash
java -version
```

Check that virtual threads bean is enabled in the code.

## Learn More

- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444)
- [Spring Boot Virtual Threads Support](https://spring.io/blog/2022/10/11/embracing-virtual-threads)
- [Java Concurrency Evolution](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html)
