
package Gateway.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class Request {

    private static final int MAX_HEADER_BYTES = 16 * 1024;
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    private static final int MAX_REQUEST_LINE = 8192;

    private final String method;
    private final String path;
    private final String version;
    private final Map<String, String> headers;
    private final byte[] body;

    private String query;
    private String clientIp;

    public Request(
            String method,
            String path,
            String version,
            Map<String, String> headers,
            byte[] body) {

        this.method = method;
        this.path = path;
        this.version = version;
        this.headers = new LinkedHashMap<>(headers);
        this.body = body == null ? new byte[0] : body;
    }

    // Exception dành cho request HTTP không hợp lệ
    public static class HttpRequestException extends IOException {

        private final int statusCode;

        public HttpRequestException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }

    public static Request readRequest(Socket clientSocket)
            throws IOException {

        InputStream in = clientSocket.getInputStream();

        // BƯỚC 1: Đọc headers có giới hạn kích thước
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int a = -1, b = -1, c = -1;
        boolean complete = false;

        while (buffer.size() < MAX_HEADER_BYTES) {

            int current = in.read();

            if (current == -1) {
                throw new HttpRequestException(
                        400, "Incomplete HTTP headers"
                );
            }

            buffer.write(current);

            if (a == '\r' && b == '\n'
                    && c == '\r' && current == '\n') {
                complete = true;
                break;
            }

            a = b;
            b = c;
            c = current;
        }

        if (!complete) {
            throw new HttpRequestException(
                    431, "Request headers too large"
            );
        }

        String text = buffer.toString(
                StandardCharsets.ISO_8859_1
        );

        // Bỏ dấu CRLFCRLF cuối header block
        String headerText = text.substring(0, text.length() - 4);
        String[] lines = headerText.split("\r\n", -1);

        if (lines.length == 0 || lines[0].isBlank()) {
            throw new HttpRequestException(
                    400, "Missing request line"
            );
        }

        // BƯỚC 2: Kiểm tra request line
        if (lines[0].length() > MAX_REQUEST_LINE) {
            throw new HttpRequestException(
                    414, "Request target too long"
            );
        }

        String[] parts = lines[0].split(" ", -1);

        if (parts.length != 3) {
            throw new HttpRequestException(
                    400, "Invalid request line"
            );
        }

        String method = parts[0];
        String url = parts[1];
        String version = parts[2];

        if (!method.matches("[A-Z]+")) {
            throw new HttpRequestException(
                    400, "Invalid HTTP method"
            );
        }

        // Bản Gateway này chỉ hỗ trợ origin-form URI
        if (!url.startsWith("/")) {
            throw new HttpRequestException(
                    400, "Invalid request target"
            );
        }

        if (!version.equals("HTTP/1.1")
                && !version.equals("HTTP/1.0")) {
            throw new HttpRequestException(
                    505, "Unsupported HTTP version"
            );
        }

        // BƯỚC 3: Phân tích headers
        Map<String, String> headers = new LinkedHashMap<>();

        String contentLengthValue = null;
        boolean hasTransferEncoding = false;
        boolean hasExpect = false;
        int hostCount = 0;

        for (int i = 1; i < lines.length; i++) {

            String line = lines[i];

            // Không hỗ trợ folded headers
            if (line.isEmpty()
                    || line.startsWith(" ")
                    || line.startsWith("\t")) {
                throw new HttpRequestException(
                        400, "Invalid header line"
                );
            }

            int colon = line.indexOf(':');

            if (colon <= 0) {
                throw new HttpRequestException(
                        400, "Malformed HTTP header"
                );
            }

            String key = line.substring(0, colon);
            String value = line.substring(colon + 1).trim();

            if (!key.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) {
                throw new HttpRequestException(
                        400, "Invalid header name"
                );
            }

            for (int j = 0; j < value.length(); j++) {
                char ch = value.charAt(j);

                if ((ch < 32 && ch != '\t') || ch == 127) {
                    throw new HttpRequestException(
                            400, "Invalid header value"
                    );
                }
            }

            String lower = key.toLowerCase(Locale.ROOT);

            switch (lower) {

                case "host":
                    hostCount++;

                    if (hostCount > 1 || value.isBlank()) {
                        throw new HttpRequestException(
                                400, "Invalid Host header"
                        );
                    }
                    break;

                case "content-length":
                    if (contentLengthValue != null) {
                        throw new HttpRequestException(
                                400, "Duplicate Content-Length"
                        );
                    }

                    contentLengthValue = value;
                    break;

                case "transfer-encoding":
                    if (hasTransferEncoding) {
                        throw new HttpRequestException(
                                400, "Duplicate Transfer-Encoding"
                        );
                    }

                    hasTransferEncoding = true;
                    break;

                case "expect":
                    hasExpect = true;
                    break;

                default:
                    break;
            }

            headers.put(key, value);
        }

        if (version.equals("HTTP/1.1") && hostCount != 1) {
            throw new HttpRequestException(
                    400, "Host header is required"
            );
        }

        // Tránh request smuggling do cách hiểu
        // Content-Length / Transfer-Encoding khác nhau
        if (hasTransferEncoding && contentLengthValue != null) {
            throw new HttpRequestException(
                    400, "Conflicting HTTP body framing"
            );
        }

        // Chunked là HTTP hợp lệ nhưng parser này
        // chưa triển khai giải mã request body chunked.
        if (hasTransferEncoding) {
            throw new HttpRequestException(
                    501, "Transfer-Encoding not supported"
            );
        }

        // Không triển khai Expect: 100-continue
        if (hasExpect) {
            throw new HttpRequestException(
                    417, "Expect header not supported"
            );
        }

        // BƯỚC 4: Kiểm tra Content-Length
        int length = 0;

        if (contentLengthValue != null) {

            if (!contentLengthValue.matches("[0-9]+")) {
                throw new HttpRequestException(
                        400, "Invalid Content-Length"
                );
            }

            long parsedLength;

            try {
                parsedLength = Long.parseLong(contentLengthValue);
            } catch (NumberFormatException e) {
                throw new HttpRequestException(
                        413, "Request body too large"
                );
            }

            if (parsedLength > MAX_BODY_BYTES) {
                throw new HttpRequestException(
                        413, "Request body too large"
                );
            }

            length = (int) parsedLength;
        }

        // BƯỚC 5: Đọc chính xác body
        byte[] body = in.readNBytes(length);

        if (body.length != length) {
            throw new HttpRequestException(
                    400, "Incomplete request body"
            );
        }

        // BƯỚC 6: Tách path và query
        String path = url;
        String query = null;

        int questionMark = url.indexOf('?');

        if (questionMark >= 0) {
            path = url.substring(0, questionMark);
            query = url.substring(questionMark + 1);
        }

        Request request = new Request(
                method, path, version, headers, body
        );

        request.query = query;
        request.clientIp =
                clientSocket.getInetAddress().getHostAddress();

        return request;
    }

    // Ghi request sang Tomcat
    public void writeTo(OutputStream out) throws IOException {

        String url = path;

        if (query != null) {
            url += "?" + query;
        }

        // Tạo bản sao headers để không thay đổi
        // request gốc giữa các lần forward.
        Map<String, String> forwarded =
                new LinkedHashMap<>(headers);

        // Xóa các header được đánh dấu hop-by-hop
        for (Map.Entry<String, String> entry : headers.entrySet()) {

            if (entry.getKey().equalsIgnoreCase("Connection")) {

                for (String token : entry.getValue().split(",")) {
                    String name = token.trim();

                    forwarded.entrySet().removeIf(e ->
                            e.getKey().equalsIgnoreCase(name)
                    );
                }
            }
        }

        forwarded.entrySet().removeIf(entry -> {
            String key = entry.getKey();

            return key.equalsIgnoreCase("Content-Length")
                    || key.equalsIgnoreCase("Connection")
                    || key.equalsIgnoreCase("Proxy-Connection")
                    || key.equalsIgnoreCase("Keep-Alive")
                    || key.equalsIgnoreCase("TE")
                    || key.equalsIgnoreCase("Trailer")
                    || key.equalsIgnoreCase("Upgrade");
        });

        forwarded.put("Content-Length", String.valueOf(body.length));
        forwarded.put("Connection", "keep-alive");

        StringBuilder sb = new StringBuilder();

        sb.append(method)
                .append(" ")
                .append(url)
                .append(" ")
                .append(version)
                .append("\r\n");

        for (Map.Entry<String, String> header : forwarded.entrySet()) {

            sb.append(header.getKey())
                    .append(": ")
                    .append(header.getValue())
                    .append("\r\n");
        }

        sb.append("\r\n");

        out.write(sb.toString().getBytes(
                StandardCharsets.ISO_8859_1
        ));

        out.write(body);
        out.flush();
    }

    public String getMethod() {
        return method;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }
}
