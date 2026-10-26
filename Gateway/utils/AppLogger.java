
package Gateway.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class AppLogger {
    public static final Logger LOGGER = Logger.getLogger("Gateway");

    static {
        try {
            Files.createDirectories(Path.of("logs"));

            FileHandler handler = new FileHandler(
                    "logs/gateway.log", true);

            handler.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(handler);
            LOGGER.setUseParentHandlers(true);
        } catch (IOException e) {
            throw new RuntimeException("Cannot initialize logging", e);
        }
    }

    private AppLogger() {
    }
}
