package com.zts.ztsaiassistant.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 旅游助手应用文档加载器
 */
@Component
@Slf4j
public class TravelAppDocumentLoader {

    private final ResourcePatternResolver resourcePatternResolver;

    public TravelAppDocumentLoader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    /**
     * 提取多篇 Markdown 文件
     * @return
     */
    public List<Document> loadMrakdowns() {

        List<Document> allDocuments = new ArrayList<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:documents/*.md");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                // 提取文档 “-” 后的内容作为标签
                String status = filename.replaceAll("\\.md$", "").replaceAll("^.*-\\s*", "").trim();
                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)
                        .withIncludeCodeBlock(false)
                        .withIncludeBlockquote(false)
                        .withAdditionalMetadata("filename", filename)
                        .withAdditionalMetadata("status", status)
                        .build();
                MarkdownDocumentReader markdownDocumentReader = new MarkdownDocumentReader(resource, config);
                // 修复重复堆积：给每个切分块生成稳定 ID
                List<Document> docs = markdownDocumentReader.get();
                for (int i = 0; i < docs.size(); i++) {
                    Document d = docs.get(i);
                    String stableId = UUID.nameUUIDFromBytes(
                            (filename + "#" + i + "#" + d.getText()).getBytes(StandardCharsets.UTF_8)).toString();
                    allDocuments.add(Document.builder()
                            .id(stableId)                 // 内容不变 -> id 不变 -> ON CONFLICT 命中并覆盖
                            .text(d.getText())
                            .metadata(d.getMetadata())
                            .build());
                }
            }
        } catch (IOException e) {
            log.error("Markdown 文档加载失败", e);
        }
        return allDocuments;
    }

}
