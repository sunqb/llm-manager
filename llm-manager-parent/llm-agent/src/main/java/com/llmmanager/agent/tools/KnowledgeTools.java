package com.llmmanager.agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 知识库工具 - Mock 数据
 * 
 * 模拟企业内部知识库查询
 *
 * @author LLM Manager
 */
@Slf4j
@Component
public class KnowledgeTools {

    private static final Map<String, List<KnowledgeItem>> KNOWLEDGE_BASE = Map.of(
            "请假", List.of(
                    new KnowledgeItem("请假流程", "员工请假需提前在 OA 系统提交申请，1-3 天由直属领导审批，3 天以上需部门经理审批。", "HR-001"),
                    new KnowledgeItem("年假规定", "入职满一年可享受 5 天年假，满 10 年享受 10 天，满 20 年享受 15 天。", "HR-002")
            ),
            "报销", List.of(
                    new KnowledgeItem("报销流程", "员工报销需在费用发生后 30 天内提交，附上发票原件和费用明细，经部门经理审批后提交财务。", "FIN-001"),
                    new KnowledgeItem("差旅标准", "出差住宿标准：一线城市 500 元/晚，二线城市 350 元/晚，三线城市 250 元/晚。", "FIN-002"),
                    new KnowledgeItem("餐饮补贴", "出差期间餐饮补贴：早餐 30 元，午餐 50 元，晚餐 50 元。", "FIN-003")
            ),
            "入职", List.of(
                    new KnowledgeItem("入职流程", "新员工入职需携带身份证、学历证书、离职证明，到 HR 部门办理入职手续。", "HR-010"),
                    new KnowledgeItem("试用期规定", "试用期一般为 3 个月，试用期工资为正式工资的 80%。", "HR-011")
            ),
            "考勤", List.of(
                    new KnowledgeItem("工作时间", "公司实行弹性工作制，核心工作时间为 10:00-16:00，每日工作 8 小时。", "HR-020"),
                    new KnowledgeItem("迟到规定", "每月允许 3 次 15 分钟内的迟到，超过将按规定扣款。", "HR-021")
            ),
            "福利", List.of(
                    new KnowledgeItem("五险一金", "公司按国家规定缴纳五险一金，公积金缴纳比例为 12%。", "HR-030"),
                    new KnowledgeItem("节日福利", "公司在春节、中秋等传统节日发放节日礼品或购物卡。", "HR-031"),
                    new KnowledgeItem("生日福利", "员工生日当月可获得 200 元生日礼金。", "HR-032")
            )
    );

    @Tool(description = "查询企业内部知识库，获取公司规章制度、流程等信息")
    public KnowledgeResult queryKnowledge(
            @ToolParam(description = "查询关键词，如：请假、报销、入职、考勤、福利等") String keyword) {

        log.info("[KnowledgeTools] 查询知识库: {}", keyword);

        // 精确匹配
        List<KnowledgeItem> items = KNOWLEDGE_BASE.get(keyword);
        if (items != null) {
            return new KnowledgeResult(true, keyword, items, null);
        }

        // 模糊匹配
        for (Map.Entry<String, List<KnowledgeItem>> entry : KNOWLEDGE_BASE.entrySet()) {
            if (keyword.contains(entry.getKey()) || entry.getKey().contains(keyword)) {
                return new KnowledgeResult(true, entry.getKey(), entry.getValue(), null);
            }
        }

        return new KnowledgeResult(false, keyword, null, 
                "未找到相关知识，可查询的主题包括：请假、报销、入职、考勤、福利");
    }

    @Tool(description = "列出知识库中所有可查询的主题分类")
    public CategoryListResult listCategories() {
        log.info("[KnowledgeTools] 列出知识库分类");

        List<String> categories = List.copyOf(KNOWLEDGE_BASE.keySet());
        return new CategoryListResult(true, categories, categories.size());
    }

    // ==================== 数据结构 ====================

    private record KnowledgeItem(String title, String content, String docId) {}

    public record KnowledgeResult(
            boolean success,
            String keyword,
            List<KnowledgeItem> items,
            String error
    ) {}

    public record CategoryListResult(
            boolean success,
            List<String> categories,
            int count
    ) {}
}

