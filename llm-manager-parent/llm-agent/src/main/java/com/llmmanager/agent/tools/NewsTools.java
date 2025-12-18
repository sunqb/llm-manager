package com.llmmanager.agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 新闻工具 - Mock 数据
 *
 * @author LLM Manager
 */
@Slf4j
@Component
public class NewsTools {

    private static final Map<String, List<NewsItem>> NEWS_BY_CATEGORY = Map.of(
            "科技", List.of(
                    new NewsItem("OpenAI 发布 GPT-5，性能大幅提升", "OpenAI 今日宣布推出新一代大语言模型 GPT-5，在推理能力和多模态处理方面取得重大突破。", "科技日报"),
                    new NewsItem("苹果发布 Vision Pro 2，价格更亲民", "苹果公司发布第二代混合现实头显 Vision Pro 2，起售价降至 2499 美元。", "新浪科技"),
                    new NewsItem("国产芯片取得重大突破", "华为海思发布新一代 7nm 芯片，性能达到国际先进水平。", "人民日报")
            ),
            "财经", List.of(
                    new NewsItem("A股三大指数集体上涨", "今日 A 股市场表现强劲，上证指数上涨 1.5%，创业板指上涨 2.3%。", "财经网"),
                    new NewsItem("央行宣布降准 0.5 个百分点", "中国人民银行宣布下调存款准备金率 0.5 个百分点，释放长期资金约 1 万亿元。", "经济日报"),
                    new NewsItem("比特币突破 10 万美元大关", "加密货币市场持续火热，比特币价格首次突破 10 万美元。", "华尔街日报")
            ),
            "体育", List.of(
                    new NewsItem("中国男足世预赛取得关键胜利", "中国男足在世预赛中 2:1 战胜对手，晋级形势一片大好。", "体坛周报"),
                    new NewsItem("NBA 总决赛：湖人 vs 凯尔特人", "NBA 总决赛即将开打，湖人与凯尔特人将上演经典对决。", "ESPN"),
                    new NewsItem("巴黎奥运会中国代表团名单公布", "中国体育代表团公布巴黎奥运会参赛名单，共 400 余名运动员参赛。", "新华社")
            ),
            "娱乐", List.of(
                    new NewsItem("《流浪地球 3》定档春节", "科幻大片《流浪地球 3》宣布定档 2025 年春节档。", "猫眼电影"),
                    new NewsItem("周杰伦新专辑即将发布", "周杰伦宣布将于下月发布全新专辑，引发歌迷期待。", "网易娱乐"),
                    new NewsItem("热门综艺《歌手 2025》开播", "湖南卫视王牌综艺《歌手 2025》正式开播，首期收视率破 3%。", "芒果TV")
            )
    );

    @Tool(description = "获取指定分类的最新新闻")
    public NewsResult getNews(
            @ToolParam(description = "新闻分类：科技、财经、体育、娱乐") String category,
            @ToolParam(description = "返回新闻条数，默认 3 条") int limit) {

        log.info("[NewsTools] 获取新闻: 分类={}, 条数={}", category, limit);

        List<NewsItem> news = NEWS_BY_CATEGORY.get(category);
        if (news == null) {
            return new NewsResult(false, null, "不支持的分类: " + category + "，可选：科技、财经、体育、娱乐");
        }

        int actualLimit = Math.min(limit > 0 ? limit : 3, news.size());
        List<NewsItem> result = news.subList(0, actualLimit);

        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return new NewsResult(true, new NewsData(category, today, result), null);
    }

    @Tool(description = "搜索包含关键词的新闻")
    public NewsResult searchNews(
            @ToolParam(description = "搜索关键词") String keyword) {

        log.info("[NewsTools] 搜索新闻: {}", keyword);

        List<NewsItem> matchedNews = NEWS_BY_CATEGORY.values().stream()
                .flatMap(List::stream)
                .filter(item -> item.title.contains(keyword) || item.summary.contains(keyword))
                .toList();

        if (matchedNews.isEmpty()) {
            return new NewsResult(false, null, "未找到包含关键词 '" + keyword + "' 的新闻");
        }

        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return new NewsResult(true, new NewsData("搜索结果", today, matchedNews), null);
    }

    // ==================== 数据结构 ====================

    private record NewsItem(String title, String summary, String source) {}

    public record NewsResult(boolean success, NewsData data, String error) {}

    public record NewsData(String category, String date, List<NewsItem> news) {}
}

