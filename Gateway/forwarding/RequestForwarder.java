
package Gateway.forwarding;

import Gateway.connection.BackendConnectionPool;
import Gateway.connection.BackendConnectionPool.PooledConnection;
import Gateway.model.BackendServer;
import Gateway.protocol.Request;
import Gateway.utils.AppLogger;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class RequestForwarder {

    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_HEADER_SIZE = 65536;
    private static final int MAX_CHUNK_LINE_SIZE = 8192;
    private static final double FAILURE_PENALTY_MS = 1000.0;

    private static final byte[] CRLF =
            "\r\n".getBytes(StandardCharsets.US_ASCII);

    private final BackendConnectionPool connectionPool;

    public RequestForwarder(BackendConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
    }

    public void forward(
            BackendServer backend,
            Request request,
            OutputStream clientOut) throws IOException {

        PooledConnection connection = null;

        boolean responseStarted = false;
        boolean reusable = false;

        long attemptStartNanos = System.nanoTime();
        boolean validHeadersReceived = false;

        try {
            // 1. Lấy kết nối từ pool
            connection = connectionPool.acquire(backend);

            InputStream backendIn = connection.getInputStream();
            OutputStream backendOut = connection.getOutputStream();

            // 2. Gửi request tới Tomcat
            request.writeTo(backendOut);
            backendOut.flush();

            // 3. Đọc đầy đủ response headers
            ResponseHeaders headers = readFinalHeaders(backendIn);

            boolean noBody =
                    request.getMethod().equalsIgnoreCase("HEAD")
                    || headers.statusCode == 204
                    || headers.statusCode == 205
                    || headers.statusCode == 304;

            // Không thể xác định ranh giới body trên
            // kết nối keep-alive nếu thiếu CL và chunked.
            if (!noBody
                    && !headers.chunked
                    && headers.contentLength < 0
                    && !headers.connectionClose) {
                throw new IOException(
                        "Response has no valid body framing"
                );
            }

            validHeadersReceived = true;

            double headerLatencyMs =
                    (System.nanoTime() - attemptStartNanos) / 1_000_000.0;

            backend.getMetrics().recordLatencySample(headerLatencyMs);

            boolean overloadedOrFailed =
                    headers.statusCode == 429 || headers.statusCode >= 500;

            double peakSampleMs = overloadedOrFailed
                    ? Math.max(headerLatencyMs, FAILURE_PENALTY_MS)
                    : headerLatencyMs;

            backend.getMetrics().recordPeakLatencySample(peakSampleMs);

            // Gateway chỉ xử lý một request trên mỗi
            // kết nối client, nên thông báo Connection: close.
            byte[] clientHeaders =
                    buildClientHeaders(headers.rawHeaders);

            // Từ đây tuyệt đối không được retry sang backend khác.
            responseStarted = true;

            writeClient(clientOut, clientHeaders);
            flushClient(clientOut);

            // 4. Streaming response body
            if (!noBody) {

                if (headers.chunked) {

                    // Đọc đúng cấu trúc HTTP chunked
                    forwardChunked(backendIn, clientOut);

                } else if (headers.contentLength >= 0) {

                    // Đọc đúng số byte Content-Length
                    copyExactly(
                            backendIn,
                            clientOut,
                            headers.contentLength
                    );

                } else {

                    // Body kết thúc khi backend đóng kết nối
                    copyUntilEof(backendIn, clientOut);
                }
            }

            flushClient(clientOut);

            // 5. Chỉ tái sử dụng khi response hoàn chỉnh
            // và backend không yêu cầu đóng socket.
            reusable = !headers.connectionClose;

            AppLogger.LOGGER.info(
                    "event=REQUEST_COMPLETED"
                    + " backend=" + backend.getName()
                    + " retry_needed=false"
            );

        } catch (ClientWriteException e) {
            // Client đóng kết nối trong khi Gateway đang gửi dữ liệu.
            throw e;

        } catch (IOException e) {
            if (!validHeadersReceived) {
                double elapsedMs =
                        (System.nanoTime() - attemptStartNanos) / 1_000_000.0;

                backend.getMetrics().recordPeakLatencySample(
                        Math.max(elapsedMs, FAILURE_PENALTY_MS)
                );
            } else {
                // Đã nhận header nhưng đọc body từ backend bị lỗi.
                backend.getMetrics().recordPeakLatencySample(FAILURE_PENALTY_MS);
            }

            if (responseStarted) {
                throw new ResponseStartedException(
                        "Backend failed after response started", e
                );
            }

            throw e;
        } finally {

            if (connection != null) {

                if (reusable) {
                    connectionPool.release(backend, connection);
                } else {
                    connectionPool.invalidate(backend, connection);
                }
            }
        }
    }

    // =====================================================
    // ĐỌC HTTP RESPONSE HEADERS
    // =====================================================

    private ResponseHeaders readFinalHeaders(InputStream in)
            throws IOException {

        // Cho phép bỏ qua tối đa 5 informational responses.
        // Chưa triển khai HTTP Upgrade (101).
        for (int i = 0; i < 6; i++) {

            byte[] raw = readHeaderBlock(in);
            ResponseHeaders headers = parseHeaders(raw);

            if (headers.statusCode == 101) {
                throw new IOException(
                        "HTTP protocol upgrade is not supported"
                );
            }

            if (headers.statusCode >= 100
                    && headers.statusCode < 200) {
                continue;
            }

            return headers;
        }

        throw new IOException("Too many informational responses");
    }

    private byte[] readHeaderBlock(InputStream in)
            throws IOException {

        ByteArrayOutputStream buffer =
                new ByteArrayOutputStream();

        int a = -1, b = -1, c = -1;
        int current;

        while ((current = in.read()) != -1) {

            buffer.write(current);

            if (buffer.size() > MAX_HEADER_SIZE) {
                throw new IOException("HTTP headers too large");
            }

            if (a == '\r' && b == '\n'
                    && c == '\r' && current == '\n') {
                return buffer.toByteArray();
            }

            a = b;
            b = c;
            c = current;
        }

        throw new EOFException("Incomplete HTTP response headers");
    }

    private ResponseHeaders parseHeaders(byte[] raw)
            throws IOException {

        String text = new String(
                raw, StandardCharsets.ISO_8859_1
        );

        String[] lines = text.split("\r\n");

        if (lines.length == 0) {
            throw new IOException("Empty HTTP response");
        }

        String[] statusParts = lines[0].split("\\s+", 3);

        if (statusParts.length < 2
                || !statusParts[0].startsWith("HTTP/")) {
            throw new IOException("Invalid HTTP status line");
        }

        ResponseHeaders result = new ResponseHeaders();
        result.rawHeaders = raw;

        try {
            result.statusCode = Integer.parseInt(statusParts[1]);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid HTTP status code", e);
        }

        result.connectionClose =
                statusParts[0].equalsIgnoreCase("HTTP/1.0");

        boolean hasTransferEncoding = false;
        String transferEncoding = "";

        for (int i = 1; i < lines.length; i++) {

            String line = lines[i];
            int colon = line.indexOf(':');

            if (colon < 0) {
                continue;
            }

            String key = line.substring(0, colon)
                    .trim()
                    .toLowerCase(Locale.ROOT);

            String value = line.substring(colon + 1).trim();

            switch (key) {

                case "content-length":
                    if (result.contentLength >= 0) {
                        throw new IOException(
                                "Duplicate Content-Length"
                        );
                    }

                    try {
                        result.contentLength =
                                Long.parseLong(value);

                        if (result.contentLength < 0) {
                            throw new NumberFormatException();
                        }
                    } catch (NumberFormatException e) {
                        throw new IOException(
                                "Invalid Content-Length", e
                        );
                    }
                    break;

                case "transfer-encoding":
                    hasTransferEncoding = true;
                    transferEncoding = value.toLowerCase(
                            Locale.ROOT
                    );
                    break;

                case "connection":
                    if (value.toLowerCase(Locale.ROOT)
                            .contains("close")) {
                        result.connectionClose = true;
                    }
                    break;

                default:
                    break;
            }
        }

        if (hasTransferEncoding) {

            // Bản này chỉ hỗ trợ framing kết thúc
            // bằng chunked, không hỗ trợ TE khác.
            if (!transferEncoding.trim().endsWith("chunked")) {
                throw new IOException(
                        "Unsupported Transfer-Encoding"
                );
            }

            if (result.contentLength >= 0) {
                throw new IOException(
                        "Conflicting TE and Content-Length"
                );
            }

            result.chunked = true;
        }

        return result;
    }

    // Thay Connection header trả client bằng close,
    // không làm mất Content-Length / Transfer-Encoding.
    private byte[] buildClientHeaders(byte[] raw) {

        String text = new String(
                raw, StandardCharsets.ISO_8859_1
        );

        StringBuilder result = new StringBuilder();
        String[] lines = text.split("\r\n");

        for (int i = 0; i < lines.length; i++) {

            String line = lines[i];

            if (line.isEmpty()) {
                break;
            }

            String lower = line.toLowerCase(Locale.ROOT);

            if (i > 0 && (
                    lower.startsWith("connection:")
                    || lower.startsWith("keep-alive:")
                    || lower.startsWith("proxy-connection:"))) {
                continue;
            }

            result.append(line).append("\r\n");
        }

        result.append("Connection: close\r\n");
        result.append("\r\n");

        return result.toString().getBytes(
                StandardCharsets.ISO_8859_1
        );
    }

    // =====================================================
    // STREAMING BODY VỚI CONTENT-LENGTH
    // =====================================================

    private void copyExactly(
            InputStream backendIn,
            OutputStream clientOut,
            long length) throws IOException {

        byte[] buffer = new byte[BUFFER_SIZE];
        long remaining = length;

        while (remaining > 0) {

            int toRead = (int) Math.min(
                    buffer.length, remaining
            );

            int n = backendIn.read(buffer, 0, toRead);

            if (n == -1) {
                throw new EOFException(
                        "Backend response truncated: missing "
                        + remaining + " bytes"
                );
            }

            writeClient(clientOut, buffer, 0, n);

            remaining -= n;
        }
    }

    // =====================================================
    // STREAMING BODY VỚI TRANSFER-ENCODING: CHUNKED
    // =====================================================

    private void forwardChunked(
            InputStream backendIn,
            OutputStream clientOut) throws IOException {

        while (true) {

            String sizeLine = readLine(
                    backendIn, MAX_CHUNK_LINE_SIZE
            );

            String sizeText = sizeLine.split(";", 2)[0].trim();
            long chunkSize;

            try {
                chunkSize = Long.parseLong(sizeText, 16);

                if (chunkSize < 0) {
                    throw new NumberFormatException();
                }

            } catch (NumberFormatException e) {
                throw new IOException(
                        "Invalid chunk size: " + sizeText, e
                );
            }

            // Giữ nguyên chunk framing khi gửi client
            writeClient(
                    clientOut,
                    (sizeLine + "\r\n").getBytes(
                            StandardCharsets.ISO_8859_1
                    )
            );

            if (chunkSize == 0) {

                // Chunk cuối có thể chứa HTTP trailers.
                // Phải đọc đến dòng trống cuối cùng.
                int trailerBytes = 0;

                while (true) {

                    String trailer = readLine(
                            backendIn, MAX_CHUNK_LINE_SIZE
                    );

                    trailerBytes += trailer.length() + 2;

                    if (trailerBytes > MAX_HEADER_SIZE) {
                        throw new IOException(
                                "HTTP trailers too large"
                        );
                    }

                    writeClient(
                            clientOut,
                            (trailer + "\r\n").getBytes(
                                    StandardCharsets.ISO_8859_1
                            )
                    );

                    if (trailer.isEmpty()) {
                        return;
                    }
                }
            }

            // Đọc chính xác chunkSize byte dữ liệu
            copyExactly(backendIn, clientOut, chunkSize);

            int cr = backendIn.read();
            int lf = backendIn.read();

            if (cr != '\r' || lf != '\n') {
                throw new IOException(
                        "Invalid chunk data terminator"
                );
            }

            writeClient(clientOut, CRLF);
        }
    }

    private String readLine(InputStream in, int maxLength)
            throws IOException {

        ByteArrayOutputStream buffer =
                new ByteArrayOutputStream();

        while (true) {

            int current = in.read();

            if (current == -1) {
                throw new EOFException(
                        "Unexpected EOF in chunked body"
                );
            }

            if (current == '\n') {

                byte[] bytes = buffer.toByteArray();

                if (bytes.length == 0
                        || bytes[bytes.length - 1] != '\r') {
                    throw new IOException(
                            "Invalid HTTP line ending"
                    );
                }

                return new String(
                        bytes,
                        0,
                        bytes.length - 1,
                        StandardCharsets.ISO_8859_1
                );
            }

            buffer.write(current);

            if (buffer.size() > maxLength) {
                throw new IOException("HTTP line too large");
            }
        }
    }

    // =====================================================
    // BODY KẾT THÚC BẰNG VIỆC ĐÓNG KẾT NỐI
    // =====================================================

    private void copyUntilEof(
            InputStream backendIn,
            OutputStream clientOut) throws IOException {

        byte[] buffer = new byte[BUFFER_SIZE];
        int n;

        while ((n = backendIn.read(buffer)) != -1) {
            writeClient(clientOut, buffer, 0, n);
        }
    }

    // =====================================================
    // GHI DỮ LIỆU SANG CLIENT
    // =====================================================

    private void writeClient(OutputStream out, byte[] data)
            throws ClientWriteException {

        writeClient(out, data, 0, data.length);
    }

    private void writeClient(
            OutputStream out,
            byte[] data,
            int offset,
            int length) throws ClientWriteException {

        try {
            out.write(data, offset, length);

        } catch (IOException e) {
            throw new ClientWriteException(
                    "Cannot write response to client", e
            );
        }
    }

    private void flushClient(OutputStream out)
            throws ClientWriteException {

        try {
            out.flush();

        } catch (IOException e) {
            throw new ClientWriteException(
                    "Cannot flush response to client", e
            );
        }
    }

    // =====================================================
    // CÁC EXCEPTION PHÂN BIỆT LOẠI LỖI
    // =====================================================

    public static class ClientWriteException extends IOException {

        public ClientWriteException(
                String message, IOException cause) {
            super(message, cause);
        }
    }

    public static class ResponseStartedException extends IOException {

        public ResponseStartedException(
                String message, IOException cause) {
            super(message, cause);
        }
    }

    private static class ResponseHeaders {

        byte[] rawHeaders;
        int statusCode;
        long contentLength = -1;
        boolean chunked;
        boolean connectionClose;
    }
}
