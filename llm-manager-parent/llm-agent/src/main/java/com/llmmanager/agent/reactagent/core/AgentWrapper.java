package com.llmmanager.agent.reactagent.core;

import com.alibaba.cloud.ai.graph.agent.Builder;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.skills.SkillsAgentHook;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.filesystem.FileSystemSkillRegistry;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ReactAgent 封装类
 * 
 * 提供对 Spring AI Alibaba ReactAgent 的封装，简化使用方式。
 * 
 * 核心功能：
 * - 封装 ReactAgent 的构建和执行
 * - 提供同步和流式调用方式
 * - 支持工具调用和自主推理
 * 
 * 使用示例：
 * <pre>{@code
 * AgentWrapper agent = AgentWrapper.builder()
 *     .name("research-agent")
 *     .chatModel(chatModel)
 *     .instruction("你是一个研究助手...")
 *     .tools(weatherTool, calculatorTool)
 *     .build();
 * 
 * // 同步调用
 * String result = agent.call("今天北京天气怎么样？");
 * 
 * // 流式调用
 * agent.stream("帮我分析这个问题...").subscribe(System.out::println);
 * }</pre>
 * 
 * @author LLM Manager
 */
@Slf4j
@Getter
public class AgentWrapper {

    private final String name;
    private final String description;
    private final ReactAgent reactAgent;
    private final String instruction;
    private final SkillsAgentHook skillsHook;

    private AgentWrapper(String name, String description, ReactAgent reactAgent, String instruction, SkillsAgentHook skillsHook) {
        this.name = name;
        this.description = description;
        this.reactAgent = reactAgent;
        this.instruction = instruction;
        this.skillsHook = skillsHook;
    }

    /**
     * 同步调用 Agent
     * 
     * @param input 用户输入
     * @return Agent 响应内容
     */
    public String call(String input) {
        log.info("[AgentWrapper] Agent '{}' 开始处理: {}", name, input);
        try {
            AssistantMessage result = reactAgent.call(input);
            String content = result.getText();
            log.info("[AgentWrapper] Agent '{}' 处理完成", name);
            return content;
        } catch (Exception e) {
            log.error("[AgentWrapper] Agent '{}' 处理失败: {}", name, e.getMessage(), e);
            throw new RuntimeException("Agent 执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 同步调用 Agent，返回完整状态
     *
     * @param input 用户输入
     * @return 完整的工作流状态（Optional）
     */
    public Optional<OverAllState> invoke(String input) {
        log.info("[AgentWrapper] Agent '{}' invoke: {}", name, input);
        try {
            return reactAgent.invoke(input);
        } catch (Exception e) {
            log.error("[AgentWrapper] Agent '{}' invoke 失败: {}", name, e.getMessage(), e);
            throw new RuntimeException("Agent invoke 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 同步调用 Agent，带配置
     * 
     * @param input 用户输入
     * @param config 运行配置
     * @return Agent 响应内容
     */
    public String call(String input, RunnableConfig config) {
        log.info("[AgentWrapper] Agent '{}' 开始处理(带配置): {}", name, input);
        try {
            AssistantMessage result = reactAgent.call(input, config);
            return result.getText();
        } catch (Exception e) {
            log.error("[AgentWrapper] Agent '{}' 处理失败: {}", name, e.getMessage(), e);
            throw new RuntimeException("Agent 执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取内部的 ReactAgent 实例
     * 用于高级场景，如作为工具使用
     * 
     * @return ReactAgent 实例
     */
    public ReactAgent getReactAgent() {
        return reactAgent;
    }

    /**
     * 获取 SkillsAgentHook（如果已启用 Skills）
     *
     * @return SkillsAgentHook，未启用时返回 null
     */
    public SkillsAgentHook getSkillsHook() {
        return skillsHook;
    }

    /**
     * 判断是否启用了 Skills
     */
    public boolean hasSkills() {
        return skillsHook != null && skillsHook.getSkillCount() > 0;
    }

    /**
     * 创建 Builder
     * 
     * @return AgentWrapperBuilder
     */
    public static AgentWrapperBuilder builder() {
        return new AgentWrapperBuilder();
    }

    /**
     * AgentWrapper 构建器
     */
    public static class AgentWrapperBuilder {
        private String name;
        private String description;
        private ChatModel chatModel;
        private String instruction;
        private String systemPrompt;
        private List<ToolCallback> tools;
        private Object[] methodTools;
        /** classpath 下的 skills 目录路径，例如 "skills"；为 null 表示不启用 Skills */
        private String skillsClasspathPath;
        /** 按技能名称分组的工具映射，用于技能绑定工具（可选） */
        private Map<String, List<ToolCallback>> skillGroupedTools;

        public AgentWrapperBuilder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * 设置 Agent 描述
         * 当 Agent 作为 Tool 被其他 Agent 调用时，这个描述会被用来决定是否调用此 Agent
         */
        public AgentWrapperBuilder description(String description) {
            this.description = description;
            return this;
        }

        public AgentWrapperBuilder chatModel(ChatModel chatModel) {
            this.chatModel = chatModel;
            return this;
        }

        public AgentWrapperBuilder instruction(String instruction) {
            this.instruction = instruction;
            return this;
        }

        public AgentWrapperBuilder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public AgentWrapperBuilder tools(List<ToolCallback> tools) {
            this.tools = tools;
            return this;
        }

        public AgentWrapperBuilder methodTools(Object... methodTools) {
            this.methodTools = methodTools;
            return this;
        }

        /**
         * 启用 Skills 功能（ClasspathSkillRegistry）
         *
         * @param classpathPath classpath 下的 skills 目录，例如 "skills"
         */
        public AgentWrapperBuilder skillsClasspathPath(String classpathPath) {
            this.skillsClasspathPath = classpathPath;
            return this;
        }

        /**
         * 按技能名称分组的工具映射（可选）
         * key = skill 的 name（与 SKILL.md frontmatter 中 name 对应）
         * value = 该技能可用的工具列表
         */
        public AgentWrapperBuilder skillGroupedTools(Map<String, List<ToolCallback>> groupedTools) {
            this.skillGroupedTools = groupedTools;
            return this;
        }

        public AgentWrapper build() {
            validateParams();

            // 使用 Builder 类型（新版本 API）
            Builder agentBuilder = ReactAgent.builder()
                    .name(name)
                    .model(chatModel);

            // 设置描述（用于 Agent-as-Tool）
            if (description != null && !description.isEmpty()) {
                agentBuilder.description(description);
            }

            // 设置指令
            if (instruction != null && !instruction.isEmpty()) {
                agentBuilder.instruction(instruction);
            }

            // 设置系统提示
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                agentBuilder.systemPrompt(systemPrompt);
            }

            // 设置工具回调
            if (tools != null && !tools.isEmpty()) {
                agentBuilder.tools(tools.toArray(new ToolCallback[0]));
            }

            // 设置方法工具
            if (methodTools != null && methodTools.length > 0) {
                agentBuilder.methodTools(methodTools);
            }

            // 构建 SkillsAgentHook（如果配置了 skills 路径）
            // 使用 FileSystemSkillRegistry + PathMatchingResourcePatternResolver，
            // 规避 Spring Boot fat JAR 的 nested: URL 协议（ClasspathSkillRegistry 不支持）
            SkillsAgentHook skillsHook = null;
            if (skillsClasspathPath != null && !skillsClasspathPath.isBlank()) {
                try {
                    Path tempSkillsDir = extractSkillsToFilesystem(skillsClasspathPath);
                    log.info("[AgentWrapper] Skills 已提取到临时目录: {}", tempSkillsDir);

                    SkillRegistry registry = FileSystemSkillRegistry.builder()
                            .userSkillsDirectory(tempSkillsDir.toString())
                            .autoLoad(true)
                            .build();

                    SkillsAgentHook.Builder hookBuilder = SkillsAgentHook.builder()
                            .skillRegistry(registry)
                            .autoReload(false);

                    if (skillGroupedTools != null && !skillGroupedTools.isEmpty()) {
                        hookBuilder.groupedTools(skillGroupedTools);
                    }

                    skillsHook = hookBuilder.build();
                    agentBuilder.hooks(List.of(skillsHook));
                    log.info("[AgentWrapper] Agent '{}' 启用 Skills, 路径: {}, 技能数: {}",
                            name, skillsClasspathPath, skillsHook.getSkillCount());
                } catch (Exception e) {
                    log.warn("[AgentWrapper] Agent '{}' Skills 初始化失败，将在无 Skills 模式下运行: {}",
                            name, e.getMessage(), e);
                }
            }

            ReactAgent reactAgent = agentBuilder.build();

            log.info("[AgentWrapper] 构建 Agent '{}' 完成, 工具数量: {}, Skills: {}",
                    name, (tools != null ? tools.size() : 0), (skillsHook != null ? skillsHook.getSkillCount() : 0));

            return new AgentWrapper(name, description, reactAgent, instruction, skillsHook);
        }

        /**
         * 将 classpath 中的 SKILL.md 文件提取到临时文件系统目录。
         * <p>
         * ClasspathSkillRegistry 不支持 Spring Boot fat JAR 的 "nested:" URL 协议，
         * 因此使用 PathMatchingResourcePatternResolver 枚举资源后写入临时目录，
         * 再通过 FileSystemSkillRegistry 加载，完全规避协议限制。
         *
         * @param classpathPath classpath 下的技能根目录，例如 "skills"
         * @return 包含所有 SKILL.md 的临时目录路径
         */
        private static Path extractSkillsToFilesystem(String classpathPath) throws IOException {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            // 匹配 classpath 内所有层级下的 SKILL.md（支持 fat JAR / nested JAR）
            String pattern = "classpath*:" + classpathPath + "/**/SKILL.md";
            Resource[] resources = resolver.getResources(pattern);

            Path tempDir = Files.createTempDirectory("llm-agent-skills-");
            log.info("[AgentWrapper] 扫描 Skills 资源: pattern={}, 发现 {} 个", pattern, resources.length);

            for (Resource resource : resources) {
                String urlStr = resource.getURL().toString();
                // 从 URL 中提取 "<skillName>/SKILL.md" 相对路径
                int idx = urlStr.lastIndexOf(classpathPath + "/");
                if (idx < 0) {
                    log.debug("[AgentWrapper] 无法提取相对路径，跳过: {}", urlStr);
                    continue;
                }
                String relativePath = urlStr.substring(idx + classpathPath.length() + 1); // "skill-name/SKILL.md"
                // 去掉可能的 URL 后缀（如 "!/..." 或 "?..."）
                int sep = relativePath.indexOf('!');
                if (sep >= 0) relativePath = relativePath.substring(0, sep);
                sep = relativePath.indexOf('?');
                if (sep >= 0) relativePath = relativePath.substring(0, sep);

                Path target = tempDir.resolve(relativePath);
                Files.createDirectories(target.getParent());
                try (var in = resource.getInputStream()) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
                log.debug("[AgentWrapper] 已提取 Skill: {} -> {}", relativePath, target);
            }
            return tempDir;
        }

        private void validateParams() {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("Agent name 不能为空");
            }
            if (chatModel == null) {
                throw new IllegalArgumentException("ChatModel 不能为空");
            }
        }
    }
}

