package config;

import projection.BalanceProjection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProjectionScheduler {

    private static final Logger log = LoggerFactory.getLogger(ProjectionScheduler.class);

    private final BalanceProjection balances;

    public ProjectionScheduler(BalanceProjection balances) {
        this.balances = balances;
    }

    @Scheduled(fixedDelay = 500)
    public void run() {
        try {
            balances.catchUp();
        } catch (RuntimeException e) {
            log.warn("Balance projection failed, will retry", e);
        }
    }
}
