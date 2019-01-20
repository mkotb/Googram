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
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.json.JSONArray;
import org.json.JSONObject;
import pro.zackpollard.telegrambot.api.TelegramBot;
import pro.zackpollard.telegrambot.api.chat.inline.InlineQuery;
import pro.zackpollard.telegrambot.api.chat.inline.send.InlineQueryResponse;
import pro.zackpollard.telegrambot.api.chat.inline.send.content.InputTextMessageContent;
import pro.zackpollard.telegrambot.api.chat.inline.send.results.InlineQueryResult;
import pro.zackpollard.telegrambot.api.chat.inline.send.results.InlineQueryResultArticle;
import pro.zackpollard.telegrambot.api.chat.message.send.ParseMode;
import pro.zackpollard.telegrambot.api.event.Listener;
import pro.zackpollard.telegrambot.api.event.chat.inline.InlineQueryReceivedEvent;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class GoogramMain implements Listener {
    private final ExecutorService service = Executors.newWorkStealingPool(3);
    private final Gson gson = new Gson();
    private URL errorImageUrl;
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

        try {
            errorImageUrl = new URL("https://i.imgur.com/4QLcKXj.jpg");
        } catch (MalformedURLException no) {
        }

        bot = TelegramBot.login(apiKey);
        bot.getEventsManager().register(this);
        bot.startUpdates(false);
        System.out.println("Logged in.");
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
        query.answer(bot, InlineQueryResponse.builder()
                .results(InlineQueryResultArticle.builder()
                        .id("1")
                        .title("Search failed!")
                        .description("Please contact @MazenK for help")
                        .thumbUrl(errorImageUrl)
                        .thumbWidth(200).thumbHeight(200)
                        .inputMessageContent(InputTextMessageContent.builder()
                                .messageText("Google Search for " + query.getQuery() + " failed, contact @MazenK")
                                .parseMode(ParseMode.NONE)
                                .build())
                        .build())
                .is_personal(true)
                .build());
    }

    @Override
    public void onInlineQueryReceived(InlineQueryReceivedEvent event) {
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

            InlineQueryResponse.InlineQueryResponseBuilder responseBuilder =
                    InlineQueryResponse.builder().is_personal(true);
            List<InlineQueryResult> queryResults = new ArrayList<>();
            int id = 0;

            for (GoogleResult result : results) {
                InlineQueryResult queryResult;

                try {
                    queryResult = result.toArticle(++id);
                } catch (MalformedURLException ignored) {
                    continue;
                }

                queryResults.add(queryResult);
            }

            query.answer(bot, responseBuilder
                    .results(queryResults).build());
        });
    }
}
