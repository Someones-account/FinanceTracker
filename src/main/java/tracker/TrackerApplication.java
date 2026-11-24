package tracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "tracker",
        "api",
        "app",
        "config",
        "domain",
        "projection",
        "store"
})
public class TrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrackerApplication.class, args);
    }
}