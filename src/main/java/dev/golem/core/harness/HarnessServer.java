package dev.golem.core.harness;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.golem.core.GolemCore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.minecraft.client.Minecraft;

/**
 * Loopback JSON-lines server. Each connection sends one request object per line and receives one
 * reply object per line, in order. Requests are executed on the client thread by {@link #drain}; a
 * command may answer at once or return a {@link Pending} that is re-polled every tick until it
 * completes or times out.
 */
final class HarnessServer {

    /**
     * A reply that is not ready yet. {@link #poll} returns the reply once it is, or null to wait.
     */
    interface Pending {
        JsonObject poll(Minecraft minecraft);

        /**
         * Wall-clock milliseconds to wait before replying with a timeout error.
         */
        default long timeoutMillis() {
            return 60_000L;
        }
    }

    private static final class Request {
        final JsonObject json;
        final CompletableFuture<JsonObject> reply = new CompletableFuture<>();
        Pending pending;
        long deadlineNanos;
        long timeoutMillis;

        Request(JsonObject json) {
            this.json = json;
        }
    }

    private static final ConcurrentLinkedQueue<Request> incoming = new ConcurrentLinkedQueue<>();
    private static final List<Request> waiting = new ArrayList<>();
    private static volatile boolean running;

    private HarnessServer() {
    }

    static void start(int port) {
        Thread thread = new Thread(() -> serve(port), "golem-harness-server");
        thread.setDaemon(true);
        thread.start();
    }

    private static void serve(int port) {
        running = true;
        // Explicit IPv4 loopback: the dev JVM may prefer IPv6, and the CLI must know where to dial.
        try (ServerSocket server = new ServerSocket(port, 4, InetAddress.getByName("127.0.0.1"))) {
            GolemCore.LOG.info("[harness] listening on {}", server.getLocalSocketAddress());
            while (running) {
                Socket socket = server.accept();
                Thread worker = new Thread(() -> handle(socket), "golem-harness-connection");
                worker.setDaemon(true);
                worker.start();
            }
        } catch (IOException e) {
            GolemCore.LOG.error("[harness] listener failed", e);
        }
    }

    private static void handle(Socket socket) {
        try (socket;
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             Writer out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) continue;
                JsonObject reply;
                try {
                    JsonObject json = JsonParser.parseString(line).getAsJsonObject();
                    Request request = new Request(json);
                    incoming.add(request);
                    long timeoutMs = json.has("timeout_ms") ? json.get("timeout_ms").getAsLong() : 120_000L;
                    reply = request.reply.get(timeoutMs + 5_000L, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    reply = Json.error("client thread did not answer in time");
                } catch (ExecutionException e) {
                    reply = Json.error(String.valueOf(e.getCause()));
                } catch (RuntimeException | InterruptedException e) {
                    reply = Json.error("bad request: " + e);
                }
                out.write(reply.toString());
                out.write('\n');
                out.flush();
            }
        } catch (IOException e) {
            GolemCore.LOG.debug("[harness] connection closed: {}", e.toString());
        }
    }

    /**
     * Runs on the client thread each tick: executes new requests and re-polls waiting ones.
     */
    static void drain(Minecraft minecraft) {
        Request request;
        while ((request = incoming.poll()) != null) {
            execute(minecraft, request);
        }
        Iterator<Request> it = waiting.iterator();
        while (it.hasNext()) {
            Request waitingRequest = it.next();
            JsonObject reply;
            try {
                reply = waitingRequest.pending.poll(minecraft);
            } catch (Throwable t) {
                GolemCore.LOG.error("[harness] pending command failed", t);
                reply = Json.error(String.valueOf(t));
            }
            if (reply == null && System.nanoTime() >= waitingRequest.deadlineNanos) {
                reply = Json.error("timeout after " + waitingRequest.timeoutMillis + " ms");
            }
            if (reply != null) {
                it.remove();
                waitingRequest.reply.complete(reply);
            }
        }
    }

    private static void execute(Minecraft minecraft, Request request) {
        String name = request.json.has("cmd") ? request.json.get("cmd").getAsString() : "";
        try {
            Object result = HarnessCommands.dispatch(minecraft, name, request.json);
            if (result instanceof Pending pending) {
                request.pending = pending;
                request.timeoutMillis = request.json.has("timeout_ms")
                        ? request.json.get("timeout_ms").getAsLong() : pending.timeoutMillis();
                request.deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(request.timeoutMillis);
                JsonObject first = pending.poll(minecraft);
                if (first != null) {
                    request.reply.complete(first);
                } else {
                    waiting.add(request);
                }
            } else if (result instanceof JsonObject json) {
                request.reply.complete(json);
            } else {
                request.reply.complete(Json.ok());
            }
        } catch (Throwable t) {
            GolemCore.LOG.error("[harness] command {} failed", name, t);
            request.reply.complete(Json.error(name + ": " + t));
        }
    }
}
