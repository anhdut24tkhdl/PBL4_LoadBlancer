package Gateway.forwarding;

import Gateway.connection.BackendConnectionPool;
import Gateway.connection.BackendConnectionPool.PooledConnection;
import Gateway.model.BackendServer;
import Gateway.protocol.Request;
import Gateway.utils.AppLogger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.util.logging.Level;

public class RequestForwarder {
    private final BackendConnectionPool connectionPool;

    // Nhận connectionPool từ GatewayServer
    public RequestForwarder(BackendConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
    }

    public void forward(BackendServer backend, Request request, OutputStream clientOut) throws Exception {
        long startTime = System.nanoTime();
        PooledConnection connection = null;
        boolean isConnectionFaulty = false;

        try {
            // 1. MƯỢN KẾT NỐI TỪ POOL (Ưu tiên Idle Queue -> nếu trống mới xin Semaphore permit để tạo mới)
            connection = connectionPool.acquire(backend);

            OutputStream backendOut = connection.getOutputStream();
            InputStream backendIn = connection.getInputStream();

            // 2. GỬI REQUEST SANG TOMCAT
            request.getHeaders().put("Connection", "keep-alive");
            request.writeTo(backendOut);
            backendOut.flush();

            // 3. ĐỌC DỮ LIỆU PHẢN HỒI VÀO BUFFER
            ByteArrayOutputStream responseBuffer = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;

            // Đọc dữ liệu phản hồi từ Tomcat
            // Chú ý: Với HTTP/1.1 Keep-Alive hoặc chunked, nếu Tomcat chưa đóng socket,
            // bạn có thể cấu hình connection: close từ client hoặc đọc theo Content-Length.
            while ((n = backendIn.read(buffer)) != -1) {
                responseBuffer.write(buffer, 0, n);
                // Nếu backend trả hết dữ liệu (hoặc không còn dữ liệu sẵn sàng trong stream)
                if (backendIn.available() == 0) {
                    break;
                }
            }

            // 4. TRẢ RESPONSE VỀ CLIENT
            try {
                responseBuffer.writeTo(clientOut);
                clientOut.flush();
            } catch (IOException e) {
                AppLogger.LOGGER.log(Level.WARNING,
                        "event=CLIENT_WRITE_ERROR backend=" + backend.getName(), e);
                throw new ClientWriteException("Cannot write response to client", e);
            }

            long latencyMs = (System.nanoTime() - startTime) / 1_000_000;
            AppLogger.LOGGER.info("event=REQUEST_COMPLETED backend=" + backend.getName()
                    + " latency_ms=" + latencyMs);

        } catch (SocketTimeoutException e) {
            isConnectionFaulty = true;
            AppLogger.LOGGER.log(Level.WARNING,
                    "event=BACKEND_TIMEOUT backend=" + backend.getName(), e);
            throw e;

        } catch (ClientWriteException e) {
            // Lỗi mạng từ phía Client -> Kết nối backend vẫn tốt, KHÔNG đánh dấu faulty
            throw e;

        } catch (IOException e) {
            isConnectionFaulty = true;
            AppLogger.LOGGER.log(Level.WARNING,
                    "event=BACKEND_CONNECTION_ERROR backend=" + backend.getName(), e);
            throw e;

        } finally {
            // 5. TRẢ KẾT NỐI VỀ POOL HOẶC HỦY KẾT NỐI HỎNG
            if (connection != null) {
                if (isConnectionFaulty) {
                    // Nếu lỗi đường truyền hoặc timeout -> hủy socket và trả permit về Semaphore
                    connectionPool.invalidate(backend, connection);
                } else {
                    // Xử lý thành công -> trả socket về Idle Queue (không tốn permit cho lần sau)
                    connectionPool.release(backend, connection);
                }
            }
        }
    }

    public static class ClientWriteException extends IOException {
        public ClientWriteException(String message, IOException cause) {
            super(message, cause);
        }
    }
}