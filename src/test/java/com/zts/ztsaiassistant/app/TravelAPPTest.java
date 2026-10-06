package com.zts.ztsaiassistant.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TravelAPPTest {

    @Resource
    private TravelAPP travelAPP;

    @Test
    void doChat() {
        String chatId = UUID.randomUUID().toString();
        // 第一轮
        String message = "你好，我是 zts";
        String answer = travelAPP.doChat(message, chatId);
        Assertions.assertNotNull(answer);
        // 第二轮
        message = "十一假期我想去成都旅游";
        answer = travelAPP.doChat(message, chatId);
        Assertions.assertNotNull(answer);
        // 第三轮
        message = "我想去哪旅游来着？刚跟你说过，帮我回忆一下";
        answer = travelAPP.doChat(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithReport() {
        String chatId = UUID.randomUUID().toString();
        // 第一轮
        String message = "你好，我是 zts，我想在十一假期去成都旅游，但我不知道该怎么做";
        TravelAPP.TravelReport travelReport = travelAPP.doChatWithReport(message, chatId);
        Assertions.assertNotNull(travelReport);
    }
}