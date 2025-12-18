package com.llmmanager.agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * 日期时间工具
 *
 * @author LLM Manager
 */
@Slf4j
@Component
public class DateTimeTools {

    private static final Map<String, ZoneId> TIMEZONE_MAP = Map.of(
            "北京", ZoneId.of("Asia/Shanghai"),
            "上海", ZoneId.of("Asia/Shanghai"),
            "东京", ZoneId.of("Asia/Tokyo"),
            "纽约", ZoneId.of("America/New_York"),
            "伦敦", ZoneId.of("Europe/London"),
            "巴黎", ZoneId.of("Europe/Paris"),
            "悉尼", ZoneId.of("Australia/Sydney"),
            "洛杉矶", ZoneId.of("America/Los_Angeles")
    );

    @Tool(description = "获取当前日期和时间")
    public CurrentTimeResult getCurrentTime(
            @ToolParam(description = "城市名称，如：北京、东京、纽约、伦敦等，默认北京") String city) {

        log.info("[DateTimeTools] 获取当前时间: {}", city);

        ZoneId zoneId = TIMEZONE_MAP.getOrDefault(city, ZoneId.of("Asia/Shanghai"));
        ZonedDateTime now = ZonedDateTime.now(zoneId);

        return new CurrentTimeResult(
                true,
                city != null ? city : "北京",
                now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                now.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                now.format(DateTimeFormatter.ofPattern("EEEE")),
                zoneId.toString(),
                null
        );
    }

    @Tool(description = "计算两个日期之间的天数差")
    public DateDiffResult calculateDateDiff(
            @ToolParam(description = "开始日期，格式：yyyy-MM-dd") String startDate,
            @ToolParam(description = "结束日期，格式：yyyy-MM-dd") String endDate) {

        log.info("[DateTimeTools] 计算日期差: {} 到 {}", startDate, endDate);

        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            long days = ChronoUnit.DAYS.between(start, end);
            long weeks = days / 7;
            long months = ChronoUnit.MONTHS.between(start, end);

            return new DateDiffResult(true, startDate, endDate, days, weeks, months, null);
        } catch (Exception e) {
            return new DateDiffResult(false, startDate, endDate, 0, 0, 0, "日期格式错误，请使用 yyyy-MM-dd 格式");
        }
    }

    @Tool(description = "获取指定日期是星期几，以及是否为工作日")
    public DayInfoResult getDayInfo(
            @ToolParam(description = "日期，格式：yyyy-MM-dd") String date) {

        log.info("[DateTimeTools] 获取日期信息: {}", date);

        try {
            LocalDate localDate = LocalDate.parse(date);
            DayOfWeek dayOfWeek = localDate.getDayOfWeek();
            boolean isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;

            String dayName = switch (dayOfWeek) {
                case MONDAY -> "星期一";
                case TUESDAY -> "星期二";
                case WEDNESDAY -> "星期三";
                case THURSDAY -> "星期四";
                case FRIDAY -> "星期五";
                case SATURDAY -> "星期六";
                case SUNDAY -> "星期日";
            };

            return new DayInfoResult(true, date, dayName, !isWeekend, isWeekend, null);
        } catch (Exception e) {
            return new DayInfoResult(false, date, null, false, false, "日期格式错误，请使用 yyyy-MM-dd 格式");
        }
    }

    // ==================== 数据结构 ====================

    public record CurrentTimeResult(
            boolean success,
            String city,
            String date,
            String time,
            String dayOfWeek,
            String timezone,
            String error
    ) {}

    public record DateDiffResult(
            boolean success,
            String startDate,
            String endDate,
            long days,
            long weeks,
            long months,
            String error
    ) {}

    public record DayInfoResult(
            boolean success,
            String date,
            String dayOfWeek,
            boolean isWorkday,
            boolean isWeekend,
            String error
    ) {}
}

