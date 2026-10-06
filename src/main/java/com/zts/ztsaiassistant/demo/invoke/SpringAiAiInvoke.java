package com.zts.ztsaiassistant.demo.invoke;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Spring AI 框架调用 AI 大模型（阿里）
 *
 * 这个类会在项目启动时真的去调一次模型：一旦模型/端点配置有问题，启动会直接失败。
 * 用 zts.ai.demo.invoke.enabled=false 可以跳过它（默认 true，保持原有行为）。
 */
//@Component
//@ConditionalOnProperty(name = "zts.ai.demo.invoke.enabled", havingValue = "true", matchIfMissing = true)
public class SpringAiAiInvoke implements CommandLineRunner {

    @Resource
    private ChatModel dashscopeChatModel;

    @Override
    public void run(String... args) throws Exception {
        AssistantMessage assistantMessage = dashscopeChatModel.call(new Prompt("你好，我是 ZTS"))
                .getResult()
                .getOutput();
        System.out.println(assistantMessage.getText());
    }
}
