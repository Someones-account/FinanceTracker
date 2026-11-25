package config;

import projection.BalanceProjection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import projection.CategoryReportProjection;

@Component
public class ProjectionScheduler {

    private static final Logger log = LoggerFactory.getLogger(ProjectionScheduler.class);

    private final BalanceProjection balances;
    private final CategoryReportProjection categories;

    public ProjectionScheduler(BalanceProjection balances, CategoryReportProjection categories) {
        this.balances = balances;
        this.categories = categories;
    }

    @Scheduled(fixedDelay = 500)
    public void run() {
        try {
            balances.catchUp();
        } catch (RuntimeException e) {
            log.warn("Balance projection failed, will retry", e);
        }
        try {
            categories.catchUp();
        } catch (RuntimeException e) {
            log.warn("Category projection failed, will retry", e);
        }
    }
}
