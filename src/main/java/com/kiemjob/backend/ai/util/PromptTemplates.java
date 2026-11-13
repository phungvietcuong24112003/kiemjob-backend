package com.kiemjob.backend.ai.util;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tiện ích nạp và quản lý các template prompt (.st) từ classpath:prompts/
 * Được nạp 1 lần duy nhất lúc khởi động ứng dụng để tối ưu hiệu năng.
 * Hỗ trợ thay thế biến dạng $ten_bien$.
 */
@Slf4j
@Component
public class PromptTemplates {

    private final Map<String, String> templates = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:prompts/**/*.st");

            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename != null && !filename.isBlank()) {
                    String content = resource.getContentAsString(StandardCharsets.UTF_8);
                    templates.put(filename, content);

                    // Lưu thêm key không có đuôi .st để gọi tiện lợi hơn
                    if (filename.endsWith(".st")) {
                        String nameWithoutExt = filename.substring(0, filename.length() - 3);
                        templates.put(nameWithoutExt, content);
                    }
                }
            }
            log.info("PromptTemplates: Khởi tạo thành công, đã nạp {} template prompt từ classpath:prompts/", resources.length);
        } catch (IOException e) {
            log.error("PromptTemplates: Lỗi khi nạp template prompt từ classpath:prompts/", e);
        }
    }

    /**
     * Lấy nội dung thô của template.
     */
    public String getTemplate(String templateName) {
        if (templateName == null || templateName.isBlank()) {
            throw new IllegalArgumentException("Template name không được để trống");
        }
        String content = templates.get(templateName);
        if (content == null && !templateName.endsWith(".st")) {
            content = templates.get(templateName + ".st");
        }
        if (content == null) {
            throw new IllegalArgumentException("Không tìm thấy template prompt: " + templateName);
        }
        return content;
    }

    /**
     * Render template bằng cách thay thế các biến dạng $key$ bằng giá trị tương ứng trong Map.
     */
    public String render(String templateName, Map<String, ?> variables) {
        String content = getTemplate(templateName);
        if (variables == null || variables.isEmpty()) {
            return content;
        }

        String result = content;
        for (Map.Entry<String, ?> entry : variables.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String placeholder = "$" + key + "$";
            String replacement = value != null ? value.toString() : "";
            result = result.replace(placeholder, replacement);
        }
        return result;
    }

    /**
     * Render template tiện lợi bằng varargs các cặp key-value: render("name", "key1", val1, "key2", val2).
     */
    public String render(String templateName, Object... keyValues) {
        if (keyValues == null || keyValues.length == 0) {
            return getTemplate(templateName);
        }
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Số lượng tham số keyValues phải là số chẵn (dạng cặp key, value)");
        }

        String result = getTemplate(templateName);
        for (int i = 0; i < keyValues.length; i += 2) {
            String key = String.valueOf(keyValues[i]);
            Object value = keyValues[i + 1];
            String placeholder = "$" + key + "$";
            String replacement = value != null ? value.toString() : "";
            result = result.replace(placeholder, replacement);
        }
        return result;
    }
}

