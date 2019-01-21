package xyz.mkotb.googram;

import com.jtelegram.api.inline.InlineQuery;

public class QueryLog {
    private final long userId;
    private final String query;
    private final long queryDate;
    private final String errorMessage;

    public QueryLog(InlineQuery query, String errorMessage) {
        this.userId = query.getFrom().getId();
        this.query = query.getQuery();
        this.queryDate = System.currentTimeMillis();
        this.errorMessage = errorMessage;
    }
}
