/*
 * Copyright (c) 2016, Mazen Kotb, mazenkotb@gmail.com
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */
package xyz.mkotb.googram;

import com.google.gson.Gson;
import com.jtelegram.api.TelegramBot;
import com.jtelegram.api.TelegramBotRegistry;
import com.jtelegram.api.events.inline.InlineQueryEvent;
import com.jtelegram.api.inline.InlineQuery;
import com.jtelegram.api.inline.input.InputTextMessageContent;
import com.jtelegram.api.inline.result.InlineResultArticle;
import com.jtelegram.api.inline.result.framework.InlineResult;
import com.jtelegram.api.requests.inline.AnswerInlineQuery;
import com.jtelegram.api.requests.message.framework.ParseMode;
import com.jtelegram.api.update.PollingUpdateProvider;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.http.HttpHost;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class GoogramMain {
    private final ExecutorService service = Executors.newWorkStealingPool(3);
    private final Gson gson = new Gson();
    private List<String> keys;
    private TelegramBot bot;
    private RestHighLevelClient elasticClient;
    private AtomicInteger keyIndex = new AtomicInteger(-1);

    public GoogramMain(String apiKey) {
        try {
            keys = Files.readAllLines(Paths.get("gm_keys"));
        } catch (IOException ex) {
            ex.printStackTrace();
            System.out.println("Could not read keys!! Shutting down...");
            System.exit(0);
        }

        elasticClient = new RestHighLevelClient(
                RestClient.builder(
                        new HttpHost("elasticsearch", 9200, "http")
                )
        );

        TelegramBotRegistry.builder()
                .updateProvider(new PollingUpdateProvider())
                .build()
                .registerBot(apiKey, (bot, error) -> {
                    if (error != null) {
                        System.out.println("Could not log into Telegram, printing error");
                        error.printStackTrace();

                        System.exit(-1);
                        return;
                    }

                    this.bot = bot;
                    bot.getEventRegistry().registerEvent(InlineQueryEvent.class, this::onInlineQuery);

                    System.out.println("Logged in as @" + bot.getBotInfo().getUsername());
                });
    }

    public static void main(String[] args) {
        new GoogramMain(System.getenv("TELEGRAM_KEY"));
    }

    public List<GoogleResult> search(String query, int timeout) throws UnirestException, DailyLimitExceededException {
        List<GoogleResult> results = new ArrayList<>();
        JSONObject response = Unirest.get("https://www.googleapis.com/customsearch/v1")
                .queryString("q", query)
                .queryString("key", keys.get(nextKeyIndex()))
                .queryString("cx", "000917504380048684589:konlxv5xaaw")
                .asJson().getBody().getObject();
        JSONArray array = new JSONArray();

        if (response.has("items")) {
            array = response.getJSONArray("items");
        }

        if (response.has("error") && response.getJSONObject("error").getInt("code") == 403) {
            if (timeout < keys.size() - 1) {
                // we hit our daily limit, let's try another key
                return search(query, timeout + 1);
            } else {
                throw new DailyLimitExceededException();
            }
        }

        array.forEach((e) -> {
            if (e instanceof JSONObject) {
                results.add(gson.fromJson(e.toString(), GoogleResult.class));
            }
        });

        return results;
    }

    public int nextKeyIndex() {
        int next = keyIndex.incrementAndGet(); // threads lock here

        if (next == keys.size()) {
            keyIndex.set(0);
            next = 0;
        }

        return next;
    }

    private void sendError(InlineQuery query, Exception ex) {
        logQuery(new QueryLog(query, ex.getClass().getName() + " " + ex.getMessage()));
        bot.perform(AnswerInlineQuery.builder()
                .queryId(query.getId())
                .results(Collections.singletonList(
                        InlineResultArticle.builder()
                                .id("1")
                                .title("Search failed!")
                                .description("Please contact @MazenK for help")
                                .thumbUrl("https://i.imgur.com/4QLcKXj.jpg")
                                .thumbWidth(200).thumbHeight(200)
                                .inputMessageContent(InputTextMessageContent.builder()
                                        .messageText("Google Search for " + query.getQuery() + " failed, contact @MazenK")
                                        .parseMode(ParseMode.NONE)
                                        .build())
                                .build()
                ))
                .isPersonal(true)
                .build()
        );
    }

    private void sendExceededNotification(InlineQuery query) {
        logQuery(new QueryLog(query, "Daily limit exceeded"));

        bot.perform(AnswerInlineQuery.builder()
                .queryId(query.getId())
                .results(Collections.singletonList(
                        InlineResultArticle.builder()
                                .id("1")
                                .title("Daily limit exceeded")
                                .description("The bot has exceeded its maximum queries for today. Try again later!")
                                .thumbUrl("https://i.imgur.com/4QLcKXj.jpg")
                                .thumbWidth(200).thumbHeight(200)
                                .inputMessageContent(InputTextMessageContent.builder()
                                        .messageText("The bot has exceeded its maximum queries for today. Try again later!")
                                        .parseMode(ParseMode.NONE)
                                        .build())
                                .build()
                ))
                .isPersonal(true)
                .build()
        );
    }

    private void logQuery(QueryLog log) {
        service.execute(() -> {
            try {
                IndexRequest request = new IndexRequest("googram-queries", "doc");
                request.source(
                        "userId", log.userId,
                        "query", log.query,
                        "queryDate", log.queryDate,
                        "errorMessage", log.errorMessage
                );
                elasticClient.index(request, RequestOptions.DEFAULT);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });
    }

    public void onInlineQuery(InlineQueryEvent event) {
        service.execute(() -> {
            InlineQuery query = event.getQuery();
            List<GoogleResult> results;

            try {
                results = search(query.getQuery(), 0);
            } catch (DailyLimitExceededException ex) {
                sendExceededNotification(query);
                return;
            } catch (Exception ex) {
                ex.printStackTrace();
                sendError(query, ex);
                return;
            }

            // log query metadata in elasticsearch
            logQuery(new QueryLog(query, null));

            AnswerInlineQuery.AnswerInlineQueryBuilder responseBuilder =
                    AnswerInlineQuery.builder().queryId(query.getId()).cacheTime(6000);
            List<InlineResult> queryResults = new ArrayList<>();
            int id = 0;

            for (GoogleResult result : results) {
                queryResults.add(result.toArticle(++id));
            }

            bot.perform(responseBuilder.results(queryResults).build());
        });
    }
}
