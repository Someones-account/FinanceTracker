package store;

import java.util.List;

public interface EventLog {
    List<GlobalEvent> loadAfter(long globalSeq, int limit);
}
