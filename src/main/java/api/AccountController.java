package api;

import app.AccountCommandHandler;
import app.IdempotencyService;
import domain.AccountCommand;
import domain.AccountEvent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import store.EventStore;
import store.StoredEvent;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    public record OpenAccountRequest(@NotBlank String name, @NotBlank String currency) {}
    public record TransactionRequest(@Positive long amountMinor, @NotBlank String category) {}
    public record ReverseRequest(@NotBlank String reason) {}

    public record EventView(int version, String type, AccountEvent data) {
        static EventView from(StoredEvent stored) {
            return new EventView(stored.version(), stored.event().getClass().getSimpleName(), stored.event());
        }
    }

    private final AccountCommandHandler handler;
    private final IdempotencyService idempotency;
    private final EventStore store;

    public AccountController(AccountCommandHandler handler, IdempotencyService idempotency, EventStore store) {
        this.handler = handler;
        this.idempotency = idempotency;
        this.store = store;
    }

    @PostMapping
    public ResponseEntity<Map<String, UUID>> open(@Valid @RequestBody OpenAccountRequest request) {
        UUID id = UUID.randomUUID();
        handler.handle(id, new AccountCommand.Open(id, request.name(), request.currency()));
        return ResponseEntity
                .created(URI.create("/accounts/" + id + "/events"))
                .body(Map.of("id", id));
    }

    @PostMapping("/{id}/deposits")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deposit(@PathVariable UUID id,
                        @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String key,
                        @Valid @RequestBody TransactionRequest request) {
        run(key, id, new AccountCommand.Deposit(request.amountMinor(), request.category()));
    }

    @PostMapping("/{id}/withdrawals")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@PathVariable UUID id,
                         @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String key,
                         @Valid @RequestBody TransactionRequest request) {
        run(key, id, new AccountCommand.Withdraw(request.amountMinor(), request.category()));
    }

    @PostMapping("/{id}/transactions/{version}/reversal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reverse(@PathVariable UUID id,
                        @PathVariable int version,
                        @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String key,
                        @Valid @RequestBody ReverseRequest request) {
        run(key, id, new AccountCommand.Reverse(version, request.reason()));
    }

    @GetMapping("/{id}/events")
    public List<EventView> events(@PathVariable UUID id) {
        List<StoredEvent> events = store.load(id);
        if (events.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        }
        return events.stream().map(EventView::from).toList();
    }

    private void run(String key, UUID accountId, AccountCommand command) {
        if (key == null || key.isBlank()) {
            handler.handle(accountId, command);
        } else {
            idempotency.execute(key, accountId, command, () -> handler.handle(accountId, command));
        }
    }
}