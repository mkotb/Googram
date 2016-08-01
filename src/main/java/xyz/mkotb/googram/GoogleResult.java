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

import pro.zackpollard.telegrambot.api.chat.inline.send.content.InputTextMessageContent;
import pro.zackpollard.telegrambot.api.chat.inline.send.results.InlineQueryResultArticle;
import pro.zackpollard.telegrambot.api.chat.message.send.ParseMode;

import java.net.MalformedURLException;
import java.net.URL;

public class GoogleResult {
    private String title;
    private String link;
    private String snippet;
    private GoogleResultImage image;

    public String title() {
        return title;
    }

    public String link() {
        return link;
    }

    public String snippet() {
        return snippet;
    }

    public GoogleResultImage image() {
        return image;
    }

    public InlineQueryResultArticle toArticle(int id) throws MalformedURLException {
        InlineQueryResultArticle.InlineQueryResultArticleBuilder builder =
                InlineQueryResultArticle.builder()
                .id(String.valueOf(id))
                .title(title)
                .url(new URL(link));

        if (snippet != null) {
            builder.description(snippet);
        }

        if (image != null) {
            builder.thumbUrl(new URL(image.link()))
                    .thumbHeight(image.height())
                    .thumbWidth(image.width());
        }

        builder.inputMessageContent(InputTextMessageContent.builder()
                .messageText("[" + title + "](" + link + ")")
                .parseMode(ParseMode.MARKDOWN)
                .build());
        return builder.build();
    }
}
