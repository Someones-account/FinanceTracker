package config;

import app.AccountCommandHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import projection.BalanceProjection;
import store.PostgresEventStore;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;
import java.time.Clock;

@Configuration
@EnableScheduling
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public PostgresEventStore eventStore(DataSource dataSource, ObjectMapper objectMapper) {
        return new PostgresEventStore(dataSource, objectMapper);
    }

    @Bean
    public AccountCommandHandler accountCommandHandler(PostgresEventStore eventStore, Clock clock) {
        return new AccountCommandHandler(eventStore, clock);
    }

    @Bean
    public BalanceProjection balanceProjection(DataSource dataSource, PostgresEventStore eventStore) {
        return new BalanceProjection(dataSource, eventStore);
    }
}