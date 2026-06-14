package interview.guide.modules.interview.service;

import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.interview.model.InterviewQuestionDTO;
import interview.guide.modules.interview.model.InterviewQuestionDTO.QuestionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 复盘问题生成服务
 * 基于案件材料内容生成针对性的复盘核查问题
 */
@Service
public class InterviewQuestionService {
    
    private static final Logger log = LoggerFactory.getLogger(InterviewQuestionService.class);
    
    private final ChatClient chatClient;
    private final PromptTemplate systemPromptTemplate;
    private final PromptTemplate userPromptTemplate;
    private final BeanOutputConverter<QuestionListDTO> outputConverter;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final int followUpCount;
    
    // 问题类型权重分配（按优先级）
    private static final double PROJECT_RATIO = 0.20;      // 20% 项目经历
    private static final double MYSQL_RATIO = 0.20;        // 20% MySQL
    private static final double REDIS_RATIO = 0.20;        // 20% Redis
    private static final double JAVA_BASIC_RATIO = 0.10;   // 10% Java基础
    private static final double JAVA_COLLECTION_RATIO = 0.10; // 10% 集合
    private static final double JAVA_CONCURRENT_RATIO = 0.10; // 10% 并发
    private static final int MAX_FOLLOW_UP_COUNT = 2;
    
    // 中间DTO用于接收AI响应
    private record QuestionListDTO(
        List<QuestionDTO> questions
    ) {}
    
    private record QuestionDTO(
        String question,
        String type,
        String category,
        List<String> followUps
    ) {}
    
    public InterviewQuestionService(
            ChatClient.Builder chatClientBuilder,
            StructuredOutputInvoker structuredOutputInvoker,
            @Value("classpath:prompts/interview-question-system.st") Resource systemPromptResource,
            @Value("classpath:prompts/interview-question-user.st") Resource userPromptResource,
            @Value("${app.interview.follow-up-count:1}") int followUpCount) throws IOException {
        this.chatClient = chatClientBuilder.build();
        this.structuredOutputInvoker = structuredOutputInvoker;
        this.systemPromptTemplate = new PromptTemplate(systemPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.userPromptTemplate = new PromptTemplate(userPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.outputConverter = new BeanOutputConverter<>(QuestionListDTO.class);
        this.followUpCount = Math.max(0, Math.min(followUpCount, MAX_FOLLOW_UP_COUNT));
    }
    
    /**
     * 生成复盘问题
     *
     * @param resumeText 案件材料文本
     * @param questionCount 问题数量
     * @param historicalQuestions 历史问题列表（可选）
     * @return 复盘问题列表
     */
    public List<InterviewQuestionDTO> generateQuestions(String resumeText, int questionCount, List<String> historicalQuestions) {
        log.info("开始生成复盘问题，案件材料长度: {}, 问题数量: {}, 历史问题数: {}",
            resumeText.length(), questionCount, historicalQuestions != null ? historicalQuestions.size() : 0);
        
        // 计算各类型问题数量
        QuestionDistribution distribution = calculateDistribution(questionCount);
        
        try {
            // 加载系统提示词
            String systemPrompt = systemPromptTemplate.render();
            
            // 加载用户提示词并填充变量
            Map<String, Object> variables = new HashMap<>();
            variables.put("questionCount", questionCount);
            variables.put("projectCount", distribution.project);
            variables.put("mysqlCount", distribution.mysql);
            variables.put("redisCount", distribution.redis);
            variables.put("javaBasicCount", distribution.javaBasic);
            variables.put("javaCollectionCount", distribution.javaCollection);
            variables.put("javaConcurrentCount", distribution.javaConcurrent);
            variables.put("springCount", distribution.spring);
            variables.put("followUpCount", followUpCount);
            variables.put("resumeText", resumeText);
            
            // 添加历史问题
            if (historicalQuestions != null && !historicalQuestions.isEmpty()) {
                String historicalText = String.join("\n", historicalQuestions);
                variables.put("historicalQuestions", historicalText);
            } else {
                variables.put("historicalQuestions", "暂无历史提问");
            }
            
            String userPrompt = userPromptTemplate.render(variables);
            
            // 添加格式指令到系统提示词
            String systemPromptWithFormat = systemPrompt + "\n\n" + outputConverter.getFormat();
            
            // 调用AI
            QuestionListDTO dto;
            try {
                dto = structuredOutputInvoker.invoke(
                    chatClient,
                    systemPromptWithFormat,
                    userPrompt,
                    outputConverter,
                    ErrorCode.INTERVIEW_QUESTION_GENERATION_FAILED,
                    "面试问题生成失败：",
                    "结构化问题生成",
                    log
                );
                log.debug("AI响应解析成功: questions count={}", dto.questions().size());
            } catch (Exception e) {
                log.error("面试问题生成AI调用失败: {}", e.getMessage(), e);
                throw new BusinessException(ErrorCode.INTERVIEW_QUESTION_GENERATION_FAILED, 
                    "面试问题生成失败：" + e.getMessage());
            }
            
            // 转换为业务对象
            List<InterviewQuestionDTO> questions = convertToQuestions(dto);
            log.info("成功生成 {} 个面试问题", questions.size());
            
            return questions;
            
        } catch (Exception e) {
            log.error("生成面试问题失败: {}", e.getMessage(), e);
            // 返回默认问题集
            return generateDefaultQuestions(questionCount);
        }
    }

    /**
     * 生成复盘问题（不带历史问题）
     */
    public List<InterviewQuestionDTO> generateQuestions(String resumeText, int questionCount) {
        return generateQuestions(resumeText, questionCount, null);
    }
    
    /**
     * 计算各类型问题分布
     */
    private QuestionDistribution calculateDistribution(int total) {
        int project = Math.max(1, (int) Math.round(total * PROJECT_RATIO));
        int mysql = Math.max(1, (int) Math.round(total * MYSQL_RATIO));
        int redis = Math.max(1, (int) Math.round(total * REDIS_RATIO));
        int javaBasic = Math.max(1, (int) Math.round(total * JAVA_BASIC_RATIO));
        int javaCollection = (int) Math.round(total * JAVA_COLLECTION_RATIO);
        int javaConcurrent = (int) Math.round(total * JAVA_CONCURRENT_RATIO);
        int spring = total - project - mysql - redis - javaBasic - javaCollection - javaConcurrent;
        
        // 确保至少有1个
        spring = Math.max(0, spring);
        
        return new QuestionDistribution(project, mysql, redis, javaBasic, javaCollection, javaConcurrent, spring);
    }
    
    private record QuestionDistribution(
        int project, int mysql, int redis, 
        int javaBasic, int javaCollection, int javaConcurrent, int spring
    ) {}
    
    /**
     * 转换DTO为业务对象
     */
    private List<InterviewQuestionDTO> convertToQuestions(QuestionListDTO dto) {
        List<InterviewQuestionDTO> questions = new ArrayList<>();
        int index = 0;

        if (dto == null || dto.questions() == null) {
            return questions;
        }

        for (QuestionDTO q : dto.questions()) {
            if (q == null || q.question() == null || q.question().isBlank()) {
                continue;
            }
            QuestionType type = parseQuestionType(q.type());
            questions.add(InterviewQuestionDTO.create(index++, q.question(), type, q.category()));

            List<String> followUps = sanitizeFollowUps(q.followUps());
            for (int i = 0; i < followUps.size(); i++) {
                questions.add(InterviewQuestionDTO.create(
                    index++,
                    followUps.get(i),
                    type,
                    buildFollowUpCategory(q.category(), i + 1)
                ));
            }
        }
        
        return questions;
    }
    
    private QuestionType parseQuestionType(String typeStr) {
        try {
            return QuestionType.valueOf(typeStr.toUpperCase());
        } catch (Exception e) {
            return QuestionType.JAVA_BASIC;
        }
    }
    
    /**
     * 生成默认问题（备用）
     */
    private List<InterviewQuestionDTO> generateDefaultQuestions(int count) {
        List<InterviewQuestionDTO> questions = new ArrayList<>();

        String[][] defaultQuestions = {
            {"事故发生的时间、地点和天气路况分别是怎样的？", "CASE_FACTS", "案情事实"},
            {"各方当事人在事故中的交通违法行为分别是什么？", "TRAFFIC_VIOLATION", "交通违法"},
            {"违法行为与事故之间的因果关系是否明确？", "RESPONSIBILITY", "责任认定"},
            {"现场是否有监控记录？证据链是否完整？", "EVIDENCE", "证据分析"},
            {"本案应适用哪些法律条文进行责任认定？", "LAW_APPLY", "法规适用"},
            {"是否涉及酒驾/毒驾/无证驾驶等加重情节？", "TRAFFIC_VIOLATION", "交通违法"},
            {"伤亡程度是否达到刑事立案标准？", "HANDLING", "处置建议"},
            {"是否需要进行车速鉴定或车辆技术检验？", "EVIDENCE", "证据分析"},
            {"各方当事人的路权归属是否存在争议？", "RESPONSIBILITY", "责任认定"},
            {"下一步调查和取证的方向是什么？", "HANDLING", "处置建议"},
        };
        
        int index = 0;
        for (int i = 0; i < Math.min(count, defaultQuestions.length); i++) {
            String mainQuestion = defaultQuestions[i][0];
            QuestionType type = QuestionType.valueOf(defaultQuestions[i][1]);
            String category = defaultQuestions[i][2];
            questions.add(InterviewQuestionDTO.create(
                index++,
                mainQuestion,
                type,
                category
            ));

            for (int j = 0; j < followUpCount; j++) {
                questions.add(InterviewQuestionDTO.create(
                    index++,
                    buildDefaultFollowUp(mainQuestion, j + 1),
                    type,
                    buildFollowUpCategory(category, j + 1)
                ));
            }
        }
        
        return questions;
    }

    private List<String> sanitizeFollowUps(List<String> followUps) {
        if (followUpCount == 0 || followUps == null || followUps.isEmpty()) {
            return List.of();
        }
        return followUps.stream()
            .filter(item -> item != null && !item.isBlank())
            .map(String::trim)
            .limit(followUpCount)
            .collect(Collectors.toList());
    }

    private String buildFollowUpCategory(String category, int order) {
        String baseCategory = (category == null || category.isBlank()) ? "追问" : category;
        return baseCategory + "（追问" + order + "）";
    }

    private String buildDefaultFollowUp(String mainQuestion, int order) {
        if (order == 1) {
            return "基于" + mainQuestion + "，请结合案件材料中的具体细节进一步说明。";
        }
        return "基于" + mainQuestion + "，如果需要补充证据，应该从哪些方向入手？";
    }
}
