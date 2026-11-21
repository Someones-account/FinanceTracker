package api;

import app.AccountCommandHandler;
import domain.AccountCommand;
import domain.AccountEvent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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

    public record OpenAccountRequest(@NotBlank String name, @NotBlank String currency) {}

    public record TransactionRequest(@Positive long amountMinor, @NotBlank String category) {}

    public record EventView(int version, String type, AccountEvent data) {
        static EventView from(StoredEvent stored) {
            return new EventView(stored.version(), stored.event().getClass().getSimpleName(), stored.event());
        }
    }

    private final AccountCommandHandler handler;
    private final EventStore store;

    public AccountController(AccountCommandHandler handler, EventStore store) {
        this.handler = handler;
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
    public void deposit(@PathVariable UUID id, @Valid @RequestBody TransactionRequest request) {
        handler.handle(id, new AccountCommand.Deposit(request.amountMinor(), request.category()));
    }

    @PostMapping("/{id}/withdrawals")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@PathVariable UUID id, @Valid @RequestBody TransactionRequest request) {
        handler.handle(id, new AccountCommand.Withdraw(request.amountMinor(), request.category()));
    }

    @GetMapping("/{id}/events")
    public List<EventView> events(@PathVariable UUID id) {
        List<StoredEvent> events = store.load(id);
        if (events.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        }
        return events.stream().map(EventView::from).toList();
    }
}