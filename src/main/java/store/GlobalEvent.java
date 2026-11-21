package store;
import domain.AccountEvent;

import java.util.UUID;

public record GlobalEvent(long globalSeq, UUID aggregateId, int version, AccountEvent event) {}
