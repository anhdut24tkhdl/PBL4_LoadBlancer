package Gateway.protocol;

import java.util.UUID;

public class Request {
    private final String command;    // PING, METRICS, TASK, CALCULATE,...
    private final String requestId;  // Định danh duy nhất để trace request
    private final String payload;    // Dữ liệu kèm theo

    public Request(String command, String payload) {
        this(command, UUID.randomUUID().toString().substring(0, 8), payload);
    }

    public Request(String command, String requestId, String payload) {
        this.command = command != null ? command.trim() : "";
        this.requestId = requestId != null ? requestId.trim() : "";
        this.payload = payload != null ? payload.trim() : "";
    }

    // Đóng gói thành chuỗi gửi qua Socket
    public String serialize() {
        return command + "|" + requestId + "|" + payload;
    }

    // Giải mã chuỗi nhận từ Socket thành đối tượng Request
    public static Request parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String[] parts = raw.split("\\|", 3);
        String cmd = parts.length > 0 ? parts[0] : "";
        String id = parts.length > 1 ? parts[1] : "";
        String data = parts.length > 2 ? parts[2] : "";
        return new Request(cmd, id, data);
    }

    public String getCommand() { return command; }
    public String getRequestId() { return requestId; }
    public String getPayload() { return payload; }

    @Override
    public String toString() {
        return "Request[" + command + ", ID=" + requestId + ", Payload=" + payload + "]";
    }
}