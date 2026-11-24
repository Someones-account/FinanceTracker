package api;

import projection.AccountBalance;
import projection.BalanceProjection;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@RestController
public class BalanceController {

    private final BalanceProjection balances;

    public BalanceController(BalanceProjection balances) {
        this.balances = balances;
    }

    @GetMapping("/accounts/{id}")
    public AccountBalance balance(@PathVariable UUID id) {
        return balances.find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    @PostMapping("/admin/projections/balances/rebuild")
    public Map<String, Integer> rebuild() {
        return Map.of("eventsReplayed", balances.rebuild());
    }
}
