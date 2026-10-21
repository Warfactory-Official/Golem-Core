package dev.golem.harness.api;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Minimal Source RCON client for a dedicated dev server (enable-rcon=true in server.properties). Lets a
 * scenario run console commands such as {@code data get block} and read what the server itself holds.
 */
public final class Rcon implements AutoCloseable {

    private final Socket socket = new Socket();
    private final DataInputStream in;
    private final DataOutputStream out;
    private int nextId = 1;

    public Rcon(String host, int port, String password) throws IOException {
        socket.connect(new InetSocketAddress(host, port), 5_000);
        socket.setSoTimeout(15_000);
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());
        int id = write(3, password);
        int[] reply = read();
        if (reply[0] != id) throw new HarnessException("rcon login refused (check rcon.password)");
    }

    /** Runs one console command (no leading slash) and returns the server's reply text. */
    public synchronized String command(String text) {
        try {
            int id = write(2, text);
            return readBody(id);
        } catch (IOException e) {
            throw new HarnessException("rcon command '" + text + "' failed: " + e);
        }
    }

    private int write(int type, String body) throws IOException {
        int id = nextId++;
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ByteBuffer packet = ByteBuffer.allocate(14 + bytes.length).order(ByteOrder.LITTLE_ENDIAN);
        packet.putInt(10 + bytes.length).putInt(id).putInt(type).put(bytes).put((byte) 0).put((byte) 0);
        out.write(packet.array());
        out.flush();
        return id;
    }

    private int[] read() throws IOException {
        byte[] header = new byte[12];
        in.readFully(header);
        ByteBuffer b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int length = b.getInt();
        int id = b.getInt();
        int type = b.getInt();
        byte[] body = new byte[length - 8];
        in.readFully(body);
        lastBody = new String(body, 0, body.length - 2, StandardCharsets.UTF_8);
        return new int[] {id, type};
    }

    private String lastBody = "";

    private String readBody(int id) throws IOException {
        int[] reply = read();
        if (reply[0] != id) throw new IOException("unexpected rcon reply id " + reply[0]);
        return lastBody;
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // closing a socket that is already gone
        }
    }
}
