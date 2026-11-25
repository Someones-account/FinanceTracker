package domain;

import domain.AccountCommand.Reverse;
import domain.AccountCommand.Withdraw;
import domain.AccountEvent.AccountOpened;
import domain.AccountEvent.MoneyDeposited;
import domain.AccountEvent.MoneyWithdrawn;
import domain.AccountEvent.TransactionReversed;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountReversalTest {

    private static final Instant T1 = Instant.parse("2026-01-10T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-02-10T10:00:00Z");
    private final UUID id = UUID.randomUUID();

    private static Account replay(AccountEvent... events) {
        Account state = Account.empty();
        for (AccountEvent e : events) {
            state = Account.evolve(state, e);
        }
        return state;
    }

    private Account standardAccount() {
        return replay(
                new AccountOpened(id, "Main", "PLN"),
                new MoneyDeposited(id, 10_000, "salary", T1),
                new MoneyWithdrawn(id, 2_500, "food", T1));
    }

    @Test
    void reversing_a_withdrawal_gives_the_money_back() {
        var state = standardAccount();

        var events = Account.decide(state, new Reverse(3, "mistake"), T2);
        var after = Account.evolve(state, events.get(0));

        assertThat(events).containsExactly(
                new TransactionReversed(id, 3, false, 2_500, "food", T1, "mistake", T2));
        assertThat(after.balanceMinor()).isEqualTo(10_000);
    }

    @Test
    void reversing_a_deposit_removes_the_money() {
        var state = replay(
                new AccountOpened(id, "Main", "PLN"),
                new MoneyDeposited(id, 10_000, "salary", T1));

        var events = Account.decide(state, new Reverse(2, "duplicate"), T2);
        var after = Account.evolve(state, events.get(0));

        assertThat(after.balanceMinor()).isZero();
    }

    @Test
    void unknown_version_is_rejected() {
        assertThatThrownBy(() -> Account.decide(standardAccount(), new Reverse(99, "x"), T2))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void opening_event_cannot_be_reversed() {
        assertThatThrownBy(() -> Account.decide(standardAccount(), new Reverse(1, "x"), T2))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void cannot_reverse_twice() {
        var state = standardAccount();
        var events = Account.decide(state, new Reverse(3, "mistake"), T2);
        var after = Account.evolve(state, events.get(0));

        assertThatThrownBy(() -> Account.decide(after, new Reverse(3, "again"), T2))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("already reversed");
    }

    @Test
    void reversing_a_deposit_that_was_already_spent_is_rejected() {
        var state = standardAccount();
        var spent = Account.evolve(state, Account.decide(state, new Withdraw(7_500, "rent"), T2).get(0));
        // balance is now 0, so the 10_000 deposit cannot be taken back

        assertThatThrownBy(() -> Account.decide(spent, new Reverse(2, "oops"), T2))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Insufficient");
    }
}
