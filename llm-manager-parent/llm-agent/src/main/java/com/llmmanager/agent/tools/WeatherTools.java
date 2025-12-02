package com.llmmanager.agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 天气工具 - 使用 Spring AI 原生 @Tool 注解
 *
 * Spring AI 会自动：
 * 1. 解析方法签名生成 JSON Schema
 * 2. 将工具描述传递给 LLM
 * 3. LLM 决定是否调用，自动执行
 *
 * @author LLM Manager
 */
@Slf4j
@Component
public class WeatherTools {

    /**
     * 获取指定城市的天气信息
     *
     * @param city 城市名称
     * @param unit 温度单位（celsius 或 fahrenheit）
     * @return 天气信息
     */
    @Tool(description = "获取指定城市的当前天气信息，包括温度、天气状况、湿度等")
    public WeatherResponse getWeather(
            @ToolParam(description = "城市名称，例如：北京、上海、深圳") String city,
            @ToolParam(description = "温度单位，可选值：celsius（摄氏度）或 fahrenheit（华氏度）") String unit) {

        log.info("[WeatherTools] LLM 调用天气工具，城市: {}, 单位: {}", city, unit);

        // 模拟天气数据（实际应调用真实 API）
        String condition = getConditionByCity(city);
        double temperature = getTemperatureByCity(city);

        // 如果需要华氏度，进行转换
        if ("fahrenheit".equalsIgnoreCase(unit)) {
            temperature = temperature * 9 / 5 + 32;
        }

        String unitDisplay = "fahrenheit".equalsIgnoreCase(unit) ? "°F" : "°C";

        return new WeatherResponse(
                city,
                condition,
                temperature,
                unitDisplay,
                getHumidityByCity(city),
                getForecastByCity(city)
        );
    }

    /**
     * 天气响应记录
     */
    public record WeatherResponse(
            String city,
            String condition,
            double temperature,
            String unit,
            int humidity,
            String forecast
    ) {}

    // ==================== 模拟数据方法 ====================

    private String getConditionByCity(String city) {
        if (city.contains("北京")) return "晴朗";
        if (city.contains("上海")) return "多云";
        if (city.contains("深圳")) return "小雨";
        if (city.contains("广州")) return "阴天";
        return "晴";
    }

    private double getTemperatureByCity(String city) {
        if (city.contains("北京")) return 22.0;
        if (city.contains("上海")) return 26.0;
        if (city.contains("深圳")) return 30.0;
        if (city.contains("广州")) return 28.0;
        return 25.0;
    }

    private int getHumidityByCity(String city) {
        if (city.contains("北京")) return 45;
        if (city.contains("上海")) return 65;
        if (city.contains("深圳")) return 80;
        if (city.contains("广州")) return 75;
        return 60;
    }

    private String getForecastByCity(String city) {
        if (city.contains("北京")) return "未来三天以晴为主，气温适宜";
        if (city.contains("上海")) return "明天转阴，后天有小雨";
        if (city.contains("深圳")) return "持续阵雨，请带伞出行";
        if (city.contains("广州")) return "阴转多云，气温略降";
        return "天气晴好，适合出行";
    }
}
