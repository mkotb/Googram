package xyz.mkotb.googram;

import com.jtelegram.api.inline.InlineQuery;

import java.time.Instant;
import java.util.Date;

public class QueryLog {
    final long userId;
    final String query;
    final Date queryDate;
    final String errorMessage;

    public QueryLog(InlineQuery query, String errorMessage) {
        this.userId = query.getFrom().getId();
        this.query = query.getQuery();
        this.queryDate = Date.from(Instant.now());
        this.errorMessage = errorMessage;
    }
}
