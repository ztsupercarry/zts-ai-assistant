package com.zts.ztsaiassistant.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TravelAPPTest {

    @Resource
    private TravelAPP travelAPP;

    /** MCP 客户端注册进来的工具（由 mcp-servers.json 里的 server 提供） */
    @Resource
    private ToolCallbackProvider mcpToolCallbackProvider;

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

    @Test
    void doChatWithMcp() {
        String chatId = UUID.randomUUID().toString();
        // 测试图片搜索 MCP
        String message = "帮我搜索一些成都著名景点的图片";
        String answer =  travelAPP.doChatWithMcp(message, chatId);
        Assertions.assertNotNull(answer);
    }

    /*
    AI 生成的用于测试是否能正确调用 MCP 工具的测试代码
    /**
     * 第 1 层：MCP 工具是否注册成功。
     * 工具数为 0 => MCP server 没连上（配置错误 / 子进程启动失败 / 首次 npx 下载超时），
     * 这种情况下后面问什么都"看起来正常"，其实模型根本无工具可用。
     * /
    @Test
    void mcpToolsRegistered() {
        ToolCallback[] callbacks = mcpToolCallbackProvider.getToolCallbacks();
        List<String> names = new ArrayList<>();
        for (ToolCallback cb : callbacks) {
            names.add(cb.getToolDefinition().name());
        }
        System.out.println("[MCP] 注册工具数 = " + callbacks.length + " -> " + names);

        assertTrue(callbacks.length > 0,
                "MCP 工具数为 0，MCP 服务没有被成功调用；请检查 mcp-servers.json 以及各 server 能否正常启动");
        // 高德地图 MCP 至少要有这几个核心工具
        assertTrue(names.contains("maps_weather"), "缺少 maps_weather，实际工具：" + names);
        assertTrue(names.stream().anyMatch(n -> n.startsWith("maps_")),
                "没有高德地图工具，实际工具：" + names);
    }

    /**
     * 第 2/3 层：判断 MCP 是否真的参与了一次对话。
     *
     * 核心手段是「地面真值比对」：
     * 先自己直接调用 MCP 工具拿到真实数据，再让 travelAPP.doChatWithMcp 回答同一个问题，
     * 最后断言回答里的关键字段和工具返回一致 —— 这是唯一能区分「真调用」和「模型自己编」的办法。
     *
     * 注意：本项目用的是 DashScope multi-model 端点，工具调用在服务端由 DashScope 完成，
     * 客户端拿到的 AssistantMessage.hasToolCalls() 恒为 false，因此不能靠它判断。
     * /
    @Test
    void doChatWithMcpAiTest() {
        // 0) 工具必须已注册，否则直接失败（比 assertNotNull 有意义）
        ToolCallback[] callbacks = mcpToolCallbackProvider.getToolCallbacks();
        assertTrue(callbacks.length > 0, "MCP 未注册任何工具，无法进行 MCP 调用验证");

        // 1) 地面真值：直接调用 maps_weather 拿上海天气
        String truth = callTool(callbacks, "maps_weather", "{\"city\":\"上海\"}");
        assertNotNull(truth, "maps_weather 工具调用失败，无法获取地面真值");
        System.out.println("[MCP] 地面真值 maps_weather(上海) = " + excerpt(truth, 200));

        Matcher t = Pattern.compile("\\\\?\"daytemp\\\\?\"\\s*:\\s*\\\\?\"(\\d+)").matcher(truth);
        assertTrue(t.find(), "地面真值里没有解析到 daytemp，原始返回：" + excerpt(truth, 200));
        String expectedTemp = t.group(1);

        // 2) 走 travelAPP 的 MCP 对话链路
        String chatId = UUID.randomUUID().toString();
        String question = "上海静安区今天天气怎么样？请务必使用地图工具查询后回答。";
        String answer = travelAPP.doChatWithMcp(question, chatId);
        System.out.println("[MCP] doChatWithMcp 回答 = " + excerpt(answer, 300));

        // 3) 判定：回答是否复现了只有 MCP 才拿得到的实时数据
        assertNotNull(answer);
        assertFalse(answer.isBlank(), "回答为空");
        assertTrue(answer.contains(expectedTemp),
                "回答中没有出现 MCP 返回的温度 " + expectedTemp + "℃，说明这次对话很可能没有真正调用 MCP 工具。"
                        + "回答=" + excerpt(answer, 300));
        System.out.println("[MCP] 判定通过：回答包含 MCP 工具返回的温度 " + expectedTemp + "℃");
    }

    /** 按名字调用 MCP 工具，返回原始 JSON；找不到或调用失败返回 null * /
    private static String callTool(ToolCallback[] callbacks, String toolName, String jsonArgs) {
        for (ToolCallback cb : callbacks) {
            if (toolName.equals(cb.getToolDefinition().name())) {
                try {
                    return cb.call(jsonArgs);
                } catch (Exception e) {
                    System.out.println("[MCP] 调用 " + toolName + " 失败: " + e.getMessage());
                    return null;
                }
            }
        }
        return null;
    }

    private static String excerpt(String s, int max) {
        if (s == null) {
            return "null";
        }
        return s.length() <= max ? s.replace("\n", " ") : s.substring(0, max).replace("\n", " ") + "...";
    }
*/
}
