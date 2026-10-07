package com.zts.ztsaiassistant.tools;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;

/**
 * 网页抓取工具
 */
public class WebScrapingTool {

    @Tool(description = "Scrape the content of a webpage")
    public String scrapeWebpage(@ToolParam(description = "The URL of the webpage to scrape") String url) {
        try {
            Document document = Jsoup.connect(url).get();   // 连接到网页并获取内容
            return document.html();   // 返回网页内容的HTML字符串
        } catch (Exception e) {
            return "Failed to scrape webpage: " + e.getMessage();   // 如果抓取失败，返回错误信息
        }
    }

}
