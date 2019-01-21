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
    private AtomicInteger keyIndex = new AtomicInteger(-1);

    public GoogramMain(String apiKey) {
        try {
            keys = Files.readAllLines(Paths.get("gm_keys"));
        } catch (IOException ex) {
            ex.printStackTrace();
            System.out.println("Could not read keys!! Shutting down...");
            System.exit(0);
        }

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

    public List<GoogleResult> search(String query) throws UnirestException {
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

    private void sendError(InlineQuery query) {
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

    public void onInlineQuery(InlineQueryEvent event) {
        service.execute(() -> {
            InlineQuery query = event.getQuery();
            List<GoogleResult> results;

            try {
                results = search(query.getQuery());
            } catch (Exception ex) {
                ex.printStackTrace();
                sendError(query);
                return;
            }

            AnswerInlineQuery.AnswerInlineQueryBuilder responseBuilder =
                    AnswerInlineQuery.builder().queryId(query.getId()).isPersonal(true);
            List<InlineResult> queryResults = new ArrayList<>();
            int id = 0;

            for (GoogleResult result : results) {
                queryResults.add(result.toArticle(++id));
            }

            bot.perform(responseBuilder.results(queryResults).build());
        });
    }
}
