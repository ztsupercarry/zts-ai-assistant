package com.zts.ztsaiassistant.chatmemory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FileChatMemoryRepository implements ChatMemoryRepository {

    private final File baseDir;
    private final Kryo kryo;

    public FileChatMemoryRepository(String directory) {
        this.baseDir = new File(directory);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }

        this.kryo = new Kryo();
        // 关键配置：Spring AI 的 Message 实现类通常没有无参构造，且未注册
        kryo.setRegistrationRequired(false);
        kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
    }

    @Override
    public List<String> findConversationIds() {
        File[] files = baseDir.listFiles((dir, name) -> name.endsWith(".kryo"));
        if (files == null) {
            return new ArrayList<>();
        }
        return List.of(files).stream()
                .map(f -> f.getName().replace(".kryo", ""))
                .collect(Collectors.toList());
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        File file = getFile(conversationId);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        try (Input input = new Input(new FileInputStream(file))) {
            return kryo.readObject(input, ArrayList.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read chat memory for: " + conversationId, e);
        }
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        File file = getFile(conversationId);
        try (Output output = new Output(new FileOutputStream(file))) {
            kryo.writeObject(output, new ArrayList<>(messages));
        } catch (Exception e) {
            throw new RuntimeException("Failed to save chat memory for: " + conversationId, e);
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        File file = getFile(conversationId);
        if (file.exists()) {
            file.delete();
        }
    }

    private File getFile(String conversationId) {
        return new File(baseDir, conversationId + ".kryo");
    }
}
