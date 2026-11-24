
package Gateway.handler;

import Gateway.forwarding.RequestForwarder;
import Gateway.loadbalancer.LoadBalancer;
import Gateway.model.BackendServer;
import Gateway.protocol.Request;
import Gateway.utils.AppLogger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final LoadBalancer loadBalancer;
    private final RequestForwarder forwarder;

    private static final int MAX_GET_ATTEMPTS = 3;

    public ClientHandler(
            Socket clientSocket,
            LoadBalancer loadBalancer,
            RequestForwarder forwarder) {

        this.clientSocket = clientSocket;
        this.loadBalancer = loadBalancer;
        this.forwarder = forwarder;
    }

    @Override
    public void run() {

        try (Socket client = clientSocket) {

            // Giới hạn thời gian client gửi HTTP request
            client.setSoTimeout(15000);

            OutputStream clientOut = client.getOutputStream();

            Request request;

            try {

                request = Request.readRequest(client);

            } catch (Request.HttpRequestException e) {

                // Request không hợp lệ: trả status tương ứng
                AppLogger.LOGGER.warning(
                        "event=INVALID_HTTP_REQUEST"
                        + " status=" + e.getStatusCode()
                        + " reason=" + e.getMessage()
                );

                sendErrorResponse(
                        clientOut,
                        e.getStatusCode(),
                        false
                );

                return;

            } catch (SocketTimeoutException e) {

                sendErrorResponse(clientOut, 408, false);
                return;
            }

            // Chỉ retry các phương thức đọc an toàn
            boolean canRetry = isRetryable(request.getMethod());

            // POST, PUT, PATCH, DELETE chỉ thực hiện 1 lần
            int maxAttempts = canRetry ? MAX_GET_ATTEMPTS : 1;

            boolean completed = false;
            boolean responseStarted = false;
            boolean clientWriteFailed = false;

            int failureStatus = 503;

            for (int attempt = 1; attempt <= maxAttempts; attempt++) {

                BackendServer backend =
                        loadBalancer.selectServerForRequest();

                if (backend == null) {
                    AppLogger.LOGGER.warning(
                            "event=NO_BACKEND_AVAILABLE"
                            + " attempt=" + attempt
                    );
                    break;
                }

                try {

                    forwarder.forward(backend, request, clientOut);

                    // Đã chuyển tiếp toàn bộ response thành công
                    completed = true;
                    break;

                } catch (RequestForwarder.ResponseStartedException e) {

                    // Đã gửi response headers hoặc body cho client.
                    // KHÔNG retry, KHÔNG ghi thêm response HTTP khác.
                    responseStarted = true;

                    AppLogger.LOGGER.log(
                            Level.WARNING,
                            "event=PARTIAL_RESPONSE"
                            + " backend=" + backend.getName()
                            + " retry=false",
                            e
                    );

                    break;

                } catch (RequestForwarder.ClientWriteException e) {

                    // Client ngắt kết nối hoặc không nhận dữ liệu.
                    // Retry lúc này không có ý nghĩa.
                    clientWriteFailed = true;

                    AppLogger.LOGGER.info(
                            "event=CLIENT_DISCONNECTED"
                            + " backend=" + backend.getName()
                    );

                    break;

                } catch (SocketTimeoutException e) {

                    // Chưa gửi response nào cho client.
                    // Chỉ retry nếu method được phép.
                    failureStatus = 504;

                    AppLogger.LOGGER.log(
                            Level.WARNING,
                            "event=BACKEND_TIMEOUT"
                            + " backend=" + backend.getName()
                            + " method=" + request.getMethod()
                            + " attempt=" + attempt
                            + " retry=" + (canRetry && attempt < maxAttempts),
                            e
                    );

                    if (!canRetry) {
                        break;
                    }

                } catch (IOException e) {

                    // Lỗi backend khi chưa bắt đầu trả response.
                    failureStatus = 502;

                    AppLogger.LOGGER.log(
                            Level.WARNING,
                            "event=BACKEND_ERROR"
                            + " backend=" + backend.getName()
                            + " method=" + request.getMethod()
                            + " attempt=" + attempt
                            + " retry=" + (canRetry && attempt < maxAttempts),
                            e
                    );

                    // Không đánh dấu DOWN với mọi IOException.
                    // HealthMonitor sẽ kiểm tra trạng thái server.
                    if (!canRetry) {
                        break;
                    }

                } finally {

                    // Hoàn trả số lượng request đang xử lý
                    backend.decreaseConnections();
                }
            }

            // Chỉ được gửi response lỗi khi chưa gửi response nào.
            if (!completed && !responseStarted && !clientWriteFailed) {
                sendErrorResponse(
                        clientOut,
                        failureStatus,
                        "HEAD".equalsIgnoreCase(request.getMethod())
                );
            }

            // try-with-resources sẽ đóng socket client.
        } catch (Exception e) {

            AppLogger.LOGGER.log(
                    Level.WARNING,
                    "event=CLIENT_HANDLER_ERROR",
                    e
            );
        }
    }

    // Chính sách retry bảo thủ cho website thi trực tuyến
    private boolean isRetryable(String method) {

        return "GET".equalsIgnoreCase(method)
                || "HEAD".equalsIgnoreCase(method);
    }

    private void sendErrorResponse(
        OutputStream out,
        int statusCode,
        boolean headRequest) {

        String statusText;

        switch (statusCode) {

            case 400:
                statusText = "Bad Request";
                break;

            case 408:
                statusText = "Request Timeout";
                break;

            case 413:
                statusText = "Content Too Large";
                break;

            case 414:
                statusText = "URI Too Long";
                break;

            case 417:
                statusText = "Expectation Failed";
                break;

            case 431:
                statusText = "Request Header Fields Too Large";
                break;

            case 501:
                statusText = "Not Implemented";
                break;

            case 505:
                statusText = "HTTP Version Not Supported";
                break;

            case 502:
                statusText = "Bad Gateway";
                break;

            case 504:
                statusText = "Gateway Timeout";
                break;

            default:
                statusCode = 503;
                statusText = "Service Unavailable";
                break;
        }

        try {

            byte[] body = statusText.getBytes(
                    StandardCharsets.UTF_8
            );

            String headers =
                    "HTTP/1.1 " + statusCode + " " + statusText + "\r\n"
                    + "Content-Type: text/plain; charset=UTF-8\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Connection: close\r\n"
                    + "\r\n";

            out.write(headers.getBytes(StandardCharsets.US_ASCII));

            if (!headRequest) {
                out.write(body);
            }

            out.flush();

        } catch (IOException e) {

            AppLogger.LOGGER.fine(
                    "event=ERROR_RESPONSE_WRITE_FAILED"
                    + " reason=" + e.getMessage()
            );
        }
    }
}
