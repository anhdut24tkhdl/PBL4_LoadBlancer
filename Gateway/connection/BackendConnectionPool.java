package Gateway.connection;

import Gateway.model.BackendServer;
import Gateway.utils.AppLogger;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fixed-size TCP connection pool.
 * Each backend owns an independent pool and each leased connection is used by one request at a time.
 */
public final class BackendConnectionPool implements AutoCloseable {
    public static final int DEFAULT_MAX_CONNECTIONS_PER_BACKEND = 5;
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 3000;
    public static final int DEFAULT_ACQUIRE_TIMEOUT_MS = 3000;
    public static final int DEFAULT_IDLE_TIMEOUT_MS = 30000;
    public static final int DEFAULT_READ_TIMEOUT_MS = 10000;

    private final ConcurrentHashMap<String, BackendPool> pools = new ConcurrentHashMap<>();
    private final int maxConnections;
    private final int connectTimeoutMs;
    private final int acquireTimeoutMs;
    private final int idleTimeoutMs;
    private final int readTimeoutMs;
    private volatile boolean closed;

    public BackendConnectionPool(Collection<BackendServer> backends) {
        this(backends, DEFAULT_MAX_CONNECTIONS_PER_BACKEND,
                DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_ACQUIRE_TIMEOUT_MS,
                DEFAULT_IDLE_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS);
    }

    public BackendConnectionPool(Collection<BackendServer> backends, int maxConnections,
                                 int connectTimeoutMs, int acquireTimeoutMs,
                                 int idleTimeoutMs, int readTimeoutMs) {
        if (maxConnections < 1 || connectTimeoutMs < 1 || acquireTimeoutMs < 1
                || idleTimeoutMs < 1 || readTimeoutMs < 1) {
            throw new IllegalArgumentException("Pool limits and timeouts must be positive");
        }

        this.maxConnections = maxConnections;
        this.connectTimeoutMs = connectTimeoutMs;
        this.acquireTimeoutMs = acquireTimeoutMs;
        this.idleTimeoutMs = idleTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;

        for (BackendServer backend : backends) {
            pools.put(keyOf(backend), new BackendPool(backend));
        }
    }

    public PooledConnection acquire(BackendServer backend) throws IOException {
        if (closed) {
            throw new IOException("Connection pool is closed");
        }
        BackendPool pool = pools.computeIfAbsent(keyOf(backend), key -> new BackendPool(backend));
        return pool.acquire();
    }

    public void release(BackendServer backend, PooledConnection connection) {
        if (connection == null) return;
        BackendPool pool = pools.get(keyOf(backend));
        if (pool == null) {
            connection.closeQuietly();
            return;
        }
        pool.release(connection);
    }

    public void invalidate(BackendServer backend, PooledConnection connection) {
        if (connection == null) return;
        BackendPool pool = pools.get(keyOf(backend));
        if (pool == null) {
            connection.closeQuietly();
            return;
        }
        pool.invalidate(connection);
    }

    public int getConnectionCount(BackendServer backend) {
        BackendPool pool = pools.get(keyOf(backend));
        return pool == null ? 0 : pool.allConnections.size();
    }

    @Override
    public void close() {
        closed = true;
        for (BackendPool pool : pools.values()) {
            pool.close();
        }
    }

    private static String keyOf(BackendServer backend) {
        return backend.getHost() + ":" + backend.getPort();
    }

    public static final class PooledConnection {
        private final Socket socket;
        private final BufferedInputStream input;
        private final OutputStream output;
        private final long id;
        private volatile long lastUsedNanos = System.nanoTime();

        private PooledConnection(Socket socket, long id) throws IOException {
            this.socket = socket;
            this.input = new BufferedInputStream(socket.getInputStream());
            this.output = socket.getOutputStream();
            this.id = id;
        }

        public BufferedInputStream getInputStream() {
            return input;
        }

        public OutputStream getOutputStream() {
            return output;
        }

        public boolean isUsable() {
            return socket.isConnected() && !socket.isClosed()
                    && !socket.isInputShutdown() && !socket.isOutputShutdown();
        }

        private void closeQuietly() {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private final class BackendPool {
        private final BackendServer backend;
        private final Semaphore slots = new Semaphore(maxConnections, true);
        private final ArrayBlockingQueue<PooledConnection> idle = new ArrayBlockingQueue<>(maxConnections);
        private final Set<PooledConnection> allConnections = ConcurrentHashMap.newKeySet();
        private final AtomicLong nextConnectionId = new AtomicLong();
        private volatile boolean poolClosed;

        private BackendPool(BackendServer backend) {
            this.backend = backend;
        }

        private PooledConnection acquire() throws IOException {
            if (poolClosed || closed) throw new IOException("Pool is closed for " + backend.getName());

            try {
                if (!slots.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS)) {
                    throw new SocketTimeoutException("Timed out waiting for a connection from "
                            + backend.getName() + " (limit=" + maxConnections + ")");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for backend connection", e);
            }

            try {
                if (poolClosed || closed) throw new IOException("Pool is closed for " + backend.getName());

                PooledConnection connection;
                while ((connection = idle.poll()) != null) {
                    long idleMs = TimeUnit.NANOSECONDS.toMillis(
                            System.nanoTime() - connection.lastUsedNanos);
                    if (connection.isUsable() && idleMs <= idleTimeoutMs) {
                        AppLogger.LOGGER.info("event=BACKEND_CONNECTION_REUSED backend="
                                + backend.getName() + " connection_id=" + connection.id);
                        return connection;
                    }

                    discard(connection);
                    // The stale idle connection owned one slot; release that slot.
                    slots.release();
                }

                return createConnection();
            } catch (IOException | RuntimeException e) {
                slots.release();
                throw e;
            }
        }

        private PooledConnection createConnection() throws IOException {
            Socket socket = new Socket();
            try {
                socket.connect(new InetSocketAddress(backend.getHost(), backend.getPort()), connectTimeoutMs);
                socket.setSoTimeout(readTimeoutMs);
                socket.setTcpNoDelay(true);

                PooledConnection connection = new PooledConnection(socket, nextConnectionId.incrementAndGet());
                allConnections.add(connection);
                AppLogger.LOGGER.info("event=BACKEND_CONNECTION_CREATED backend=" + backend.getName()
                        + " connection_id=" + connection.id
                        + " pool_size=" + allConnections.size()
                        + " max_connections=" + maxConnections);
                return connection;
            } catch (IOException e) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
                throw e;
            }
        }

        private void release(PooledConnection connection) {
            if (poolClosed || closed || !connection.isUsable()) {
                discard(connection);
                slots.release();
                return;
            }

            connection.lastUsedNanos = System.nanoTime();
            if (!idle.offer(connection)) {
                discard(connection);
                slots.release();
                return;
            }

            AppLogger.LOGGER.fine("event=BACKEND_CONNECTION_RETURNED backend=" + backend.getName()
                    + " connection_id=" + connection.id
                    + " idle_connections=" + idle.size());
        }

        private void invalidate(PooledConnection connection) {
            discard(connection);
            slots.release();
        }

        private void discard(PooledConnection connection) {
            allConnections.remove(connection);
            connection.closeQuietly();
        }

        private void close() {
            poolClosed = true;
            for (PooledConnection connection : allConnections) {
                connection.closeQuietly();
            }
            idle.clear();
            allConnections.clear();
        }
    }
}
