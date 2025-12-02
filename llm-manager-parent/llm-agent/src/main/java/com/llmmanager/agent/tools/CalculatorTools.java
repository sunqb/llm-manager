package com.llmmanager.agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 计算器工具 - 使用 Spring AI 原生 @Tool 注解
 *
 * @author LLM Manager
 */
@Slf4j
@Component
public class CalculatorTools {

    /**
     * 执行数学计算
     *
     * @param operation 运算符
     * @param a         第一个数
     * @param b         第二个数
     * @return 计算结果
     */
    @Tool(description = "执行数学计算，支持加减乘除运算")
    public CalculationResult calculate(
            @ToolParam(description = "运算符，可选值：add（加）、subtract（减）、multiply（乘）、divide（除）") String operation,
            @ToolParam(description = "第一个操作数") double a,
            @ToolParam(description = "第二个操作数") double b) {

        log.info("[CalculatorTools] LLM 调用计算器工具: {} {} {}", a, operation, b);

        double result;
        String expression;

        switch (operation.toLowerCase()) {
            case "add":
            case "plus":
            case "+":
                result = a + b;
                expression = a + " + " + b + " = " + result;
                break;
            case "subtract":
            case "minus":
            case "-":
                result = a - b;
                expression = a + " - " + b + " = " + result;
                break;
            case "multiply":
            case "times":
            case "*":
            case "x":
                result = a * b;
                expression = a + " × " + b + " = " + result;
                break;
            case "divide":
            case "/":
                if (b == 0) {
                    return new CalculationResult(false, 0, "除数不能为零", a + " ÷ " + b);
                }
                result = a / b;
                expression = a + " ÷ " + b + " = " + result;
                break;
            default:
                return new CalculationResult(false, 0, "不支持的运算符: " + operation, "");
        }

        return new CalculationResult(true, result, null, expression);
    }

    /**
     * 计算结果记录
     */
    public record CalculationResult(
            boolean success,
            double result,
            String error,
            String expression
    ) {}
}
