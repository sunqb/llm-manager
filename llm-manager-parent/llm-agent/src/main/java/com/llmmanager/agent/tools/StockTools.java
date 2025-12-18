package com.llmmanager.agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Random;

/**
 * 股票工具 - Mock 数据
 *
 * @author LLM Manager
 */
@Slf4j
@Component
public class StockTools {

    private static final Map<String, StockInfo> STOCK_DATA = Map.of(
            "AAPL", new StockInfo("苹果公司", "AAPL", 178.50, 2.35, 1.33),
            "GOOGL", new StockInfo("谷歌", "GOOGL", 141.80, -1.20, -0.84),
            "MSFT", new StockInfo("微软", "MSFT", 378.90, 5.60, 1.50),
            "TSLA", new StockInfo("特斯拉", "TSLA", 248.30, -8.70, -3.39),
            "AMZN", new StockInfo("亚马逊", "AMZN", 178.25, 3.15, 1.80),
            "BABA", new StockInfo("阿里巴巴", "BABA", 85.60, 1.20, 1.42),
            "TCEHY", new StockInfo("腾讯控股", "TCEHY", 42.30, 0.85, 2.05),
            "600519", new StockInfo("贵州茅台", "600519", 1680.00, 25.00, 1.51),
            "000858", new StockInfo("五粮液", "000858", 145.80, -2.30, -1.55),
            "601318", new StockInfo("中国平安", "601318", 48.50, 0.65, 1.36)
    );

    @Tool(description = "查询股票实时行情，获取股票价格、涨跌幅等信息")
    public StockQuote getStockQuote(
            @ToolParam(description = "股票代码，如 AAPL、GOOGL、600519 等") String symbol) {

        log.info("[StockTools] 查询股票行情: {}", symbol);

        StockInfo info = STOCK_DATA.get(symbol.toUpperCase());
        if (info == null) {
            return new StockQuote(false, null, "未找到股票: " + symbol);
        }

        // 添加随机波动模拟实时数据
        Random random = new Random();
        double fluctuation = (random.nextDouble() - 0.5) * 2;
        double currentPrice = info.basePrice + fluctuation;

        String updateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        return new StockQuote(true, new StockDetail(
                info.name,
                info.symbol,
                Math.round(currentPrice * 100.0) / 100.0,
                info.change,
                info.changePercent,
                updateTime
        ), null);
    }

    @Tool(description = "获取股票的简要分析和投资建议")
    public StockAnalysis analyzeStock(
            @ToolParam(description = "股票代码") String symbol) {

        log.info("[StockTools] 分析股票: {}", symbol);

        StockInfo info = STOCK_DATA.get(symbol.toUpperCase());
        if (info == null) {
            return new StockAnalysis(false, null, "未找到股票: " + symbol);
        }

        String recommendation;
        String analysis;
        if (info.changePercent > 1) {
            recommendation = "持有观望";
            analysis = String.format("%s 今日涨幅 %.2f%%，短期走势强劲，建议持有观望，等待回调再加仓。", info.name, info.changePercent);
        } else if (info.changePercent < -1) {
            recommendation = "逢低买入";
            analysis = String.format("%s 今日跌幅 %.2f%%，可能存在超跌机会，建议分批逢低买入。", info.name, Math.abs(info.changePercent));
        } else {
            recommendation = "中性";
            analysis = String.format("%s 今日波动较小，走势平稳，建议继续观察市场动向。", info.name);
        }

        return new StockAnalysis(true, new AnalysisDetail(
                info.name,
                info.symbol,
                recommendation,
                analysis,
                "以上分析仅供参考，不构成投资建议"
        ), null);
    }

    // ==================== 数据结构 ====================

    private record StockInfo(String name, String symbol, double basePrice, double change, double changePercent) {}

    public record StockQuote(boolean success, StockDetail data, String error) {}

    public record StockDetail(
            String name,
            String symbol,
            double price,
            double change,
            double changePercent,
            String updateTime
    ) {}

    public record StockAnalysis(boolean success, AnalysisDetail data, String error) {}

    public record AnalysisDetail(
            String name,
            String symbol,
            String recommendation,
            String analysis,
            String disclaimer
    ) {}
}

