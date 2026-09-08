package Gateway;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigLoader {
    private static final String CONFIG_FILE = "config.properties";
    private final Properties properties = new Properties();

    public ConfigLoader() {
        try (InputStream input = new FileInputStream(CONFIG_FILE)) {
            properties.load(input);
            System.out.println("[ConfigLoader] Đã tải cấu hình thành công từ " + CONFIG_FILE);
        } catch (IOException ex) {
            System.err.println("[ConfigLoader] Cảnh báo: Không tìm thấy " + CONFIG_FILE + ", sẽ dùng giá trị mặc định.");
        }
    }

    public String getString(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        String val = properties.getProperty(key);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}