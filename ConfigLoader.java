import java.io.*;
import java.util.Properties;

public class ConfigLoader {
    private Properties config;

    public ConfigLoader(String configFile) throws IOException {
        config = new Properties();
        try (InputStream inputStream = new FileInputStream(configFile)) {
            config.load(inputStream);
        }
    }

    public String getProperty(String key) {
        return config.getProperty(key);
    }
}
