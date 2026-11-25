package config;

import app.AccountCommandHandler;
import app.BalanceQueryService;
import app.IdempotencyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import projection.BalanceProjection;
import projection.CategoryReportProjection;
import store.PostgresEventStore;

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
    public IdempotencyService idempotencyService(DataSource dataSource) {
        return new IdempotencyService(dataSource);
    }

    @Bean
    public BalanceQueryService balanceQueryService(PostgresEventStore eventStore) {
        return new BalanceQueryService(eventStore);
    }

    @Bean
    public BalanceProjection balanceProjection(DataSource dataSource, PostgresEventStore eventStore) {
        return new BalanceProjection(dataSource, eventStore);
    }

    @Bean
    public CategoryReportProjection categoryReportProjection(DataSource dataSource, PostgresEventStore eventStore) {
        return new CategoryReportProjection(dataSource, eventStore);
    }
}