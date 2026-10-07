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

    @Test
    void doChatWithRag() {
        String chatId = UUID.randomUUID().toString();
        String message = "我想在十一和家人一起外出旅行，该如何规划？";
        String answer =  travelAPP.doChatWithRag(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithTools() {
        // 测试联网搜索问题的答案
        testMessage("周末想一个人去成都旅行，推荐几个适合独行的小众打卡地？");

        // 测试网页抓取：单人旅行案例分析
        testMessage("最近想一个人散散心，看看编程导航网站（codefather.cn）的其他驴友都去哪里放松心情？");

        // 测试资源下载：图片下载
        testMessage("直接下载一张适合做手机壁纸的旅途风景图为文件");

//        // 测试终端操作：执行代码
//        testMessage("执行 Python3 脚本来生成数据分析报告");

        // 测试文件操作：保存用户档案
        testMessage("保存我的旅行计划档案为文件");

        //测试 PDF 生成
        testMessage("我打算十一一个人去成都玩3天，预算3000元，想住在市中心，先直接帮我生成一份简要的‘十一旅行计划’PDF");

    }

    private void testMessage(String message) {
        String chatId = UUID.randomUUID().toString();
        String answer = travelAPP.doChatWithTools(message, chatId);
        Assertions.assertNotNull(answer);
    }

}