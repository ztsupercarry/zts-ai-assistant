package com.zts.ztsaiassistant.agent;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ZtsManusTest {

    @Resource
    private ZtsManus ztsManus;

    @Test
    public void run() {
        String userPrompt = """
                我的另一半居住在郑州中原区，请帮我找到周边适合两人一日行的地点，
                并结合一些网络图片，制定一份详细的旅行计划，
                并以 PDF 格式输出""";
        String answer = ztsManus.run(userPrompt);
        Assertions.assertNotNull(answer);
    }
}