import java.io.*;
import java.util.Properties;

public class DBConfig {
    public static Properties loadConfig(String filename) {
        Properties config = new Properties();
        try (InputStream input = new FileInputStream(filename)) {
            config.load(input);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return config;
    }
}
