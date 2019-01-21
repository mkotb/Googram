package xyz.mkotb.googram;

import com.jtelegram.api.inline.InlineQuery;

public class QueryLog {
    final long userId;
    final String query;
    final long queryDate;
    final String errorMessage;

    public QueryLog(InlineQuery query, String errorMessage) {
        this.userId = query.getFrom().getId();
        this.query = query.getQuery();
        this.queryDate = System.currentTimeMillis();
        this.errorMessage = errorMessage;
    }
}
