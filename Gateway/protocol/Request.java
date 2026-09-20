package Gateway.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class Request {
    String method;
    String path;
    String query;
    String version;

    Map<String, String> headers;
    byte[] body;

    String clientIp;

    public Request(String method, String path, String version,
            Map<String, String> headers, byte[] body) {
        this.method = method;
        this.path = path;
        this.version = version;
        this.headers = headers;
        this.body = body;
    }

    public static Request readRequest(Socket clientSocket) throws IOException {
        InputStream in = clientSocket.getInputStream();

        // 1. Đọc toàn bộ header đến \r\n\r\n
        ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();

        int a = -1, b = -1, c = -1, current;

        while ((current = in.read()) != -1) {
            headerBytes.write(current);

            if (a == '\r' && b == '\n' && c == '\r' && current == '\n') {

                break;
            }

            a = b;
            b = c;
            c = current;
        }

        String headerText = headerBytes.toString(StandardCharsets.UTF_8);

        String[] lines = headerText.split("\r\n");

        if (lines.length == 0 || lines[0].isBlank()) {
            throw new IOException("HTTP request khong hop le");
        }

        // 2. Tách dòng đầu: GET /api/products?page=2 HTTP/1.1
        String[] firstLine = lines[0].split(" ");

        if (firstLine.length != 3) {
            throw new IOException("Request line khong hop le: " + lines[0]);
        }

        String method = firstLine[0];
        String url = firstLine[1];
        String version = firstLine[2];

        // 3. Tách path và query
        String path = url;
        String query = null;

        int questionMark = url.indexOf('?');

        if (questionMark >= 0) {
            path = url.substring(0, questionMark);
            query = url.substring(questionMark + 1);
        }

        // 4. Đọc các HTTP header
        Map<String, String> headers = new LinkedHashMap<>();

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];

            if (line.isEmpty()) {
                break;
            }

            int colonIndex = line.indexOf(':');

            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();

                headers.put(key, value);
            }
        }

        // 5. Đọc body theo Content-Length
        int contentLength = 0;

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase("Content-Length")) {
                contentLength = Integer.parseInt(entry.getValue());
                break;
            }
        }

        byte[] body = in.readNBytes(contentLength);

        if (body.length != contentLength) {
            throw new IOException("Body khong du byte");
        }

        // 6. Tạo object Request riêng cho client hiện tại
        Request request = new Request(method, path, version, headers, body);
        request.query = query;
        request.clientIp = clientSocket.getInetAddress().getHostAddress();

        return request;
    }

    public void writeTo(OutputStream out) throws IOException {
        // Ghép lại URL: /api/products?page=2
        String url = path;

        if (query != null && !query.isEmpty()) {
            url += "?" + query;
        }

        // Body luôn là byte[]; GET thường body.length = 0
        if (body == null) {
            body = new byte[0];
        }

        // Cập nhật header cần thiết trước khi gửi
        headers.put("Content-Length", String.valueOf(body.length));

        // Bản Gateway hiện tại: đóng kết nối sau một request/response
        headers.put("Connection", "close");

        // 1. Tạo request line + headers (phần text)
        StringBuilder headerText = new StringBuilder();

        headerText.append(method)
                .append(" ")
                .append(url)
                .append(" ")
                .append(version)
                .append("\r\n");

        for (Map.Entry<String, String> header : headers.entrySet()) {
            headerText.append(header.getKey())
                    .append(": ")
                    .append(header.getValue())
                    .append("\r\n");
        }

        headerText.append("\r\n"); // báo header kết thúc

        // 2. Gửi header đã đổi thành bytes
        out.write(headerText.toString().getBytes(StandardCharsets.UTF_8));

        // 3. Gửi body nguyên vẹn dưới dạng bytes
        out.write(body);

        out.flush();
    }
}