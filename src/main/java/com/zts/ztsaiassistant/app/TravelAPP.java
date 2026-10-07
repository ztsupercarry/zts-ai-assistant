package com.zts.ztsaiassistant.app;

import com.zts.ztsaiassistant.advisor.MyLoggerAdvisor;
import com.zts.ztsaiassistant.rag.QueryRewriter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class TravelAPP {

    private final ChatClient chatClient;

    public static final String SYSTEM_PROMPT = "你是「小旅」，一位热情、专业、贴心的资深旅游规划顾问，拥有 10 年以上全球旅游定制经验。你熟悉国内外热门与小众目的地、签证政策、交通接驳、住宿选择、美食文化、季节气候、预算控制以及各类实用避坑技巧" +
            "你的目标不是一次性丢给用户一份攻略，而是像真实旅行社顾问一样，通过对话逐步了解用户，最终为其量身定制旅行方案。";

    /**
     * 初始化 ChatClient
     *
     * @param dashscopeChatModel
     */
    public TravelAPP(ChatModel dashscopeChatModel) {
        // 初始化基于文件的对话记忆
//        String fileDir = System.getProperty("user.dir") + "/tmp/chat_memory";
//        FileChatMemoryRepository repository = new FileChatMemoryRepository(fileDir);
        // 初始化基于内存的对话记忆
        ChatMemoryRepository repository = new InMemoryChatMemoryRepository();
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(10)
                .build();
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        // 自定义日志 Advisor，可按需开启
                        new MyLoggerAdvisor()
                )
                .build();
    }

    /**
     * AI 基础对话（支持多轮对话记忆）
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChat(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();

        String content = chatResponse.getResult().getOutput().getText();
//        log.info("content: {}", content);
        return content;
    }

    /**
     * 定义旅行报告类
     *
     * @param title
     * @param suggestions
     */
    record TravelReport(String title, List<String> suggestions) {
    }

    /**
     * AI 旅行报告功能（实现结构化输出）
     *
     * @param message
     * @param chatId
     * @return
     */
    public TravelReport doChatWithReport(String message, String chatId) {
        TravelReport travelReport = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .entity(TravelReport.class);

//        log.info("travelReport: {}", travelReport);
        return travelReport;
    }

    // AI 旅行规划知识库问答功能

    @Resource
    private VectorStore travelAppVectorStore;

    @Resource
    private VectorStore pgVectorVectorStore;

    @Resource
    private QueryRewriter queryRewriter;

    /**
     * 和 RAG 知识库进行对话
     *
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithRag(String message, String chatId) {
        // 查询重写
        String rewrittenMessage = queryRewriter.doQueryRewrite(message);
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(rewrittenMessage)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 应用 RAG 知识库问答
                .advisors(QuestionAnswerAdvisor.builder(travelAppVectorStore).build())
                // 应用 RAG 检索增强服务（基于 PgVector 向量存储）
//                .advisors(QuestionAnswerAdvisor.builder(pgVectorVectorStore).build())
                // 应用自定义的 RAG 检索增强服务（文档查询器 + 上下文增强器）
//                .advisors(TravelAppRagCustomAdvisorFactory.creatTravelAppRagCustomAdvisor(
//                        travelAppVectorStore, "自由行新手"))
                .call()
                .chatResponse();

        String content = chatResponse.getResult().getOutput().getText();
//        log.info("content: {}", content);
        return content;
    }


}











