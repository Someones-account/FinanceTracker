package config;

import app.AccountCommandHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import store.EventStore;
import store.PostgresEventStore;

import javax.sql.DataSource;
import java.time.Clock;

@Configuration
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public EventStore eventStore(DataSource dataSource, ObjectMapper objectMapper) {
        return new PostgresEventStore(dataSource, objectMapper);
    }

    @Bean
    public AccountCommandHandler accountCommandHandler(EventStore eventStore, Clock clock) {
        return new AccountCommandHandler(eventStore, clock);
    }
}
