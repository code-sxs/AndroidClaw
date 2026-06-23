// CalculatorSkill.kt
// 计算器/数学 Skill - 本地数学计算和单位转换

package com.androidclaw.app.skills

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.net.URL
import kotlin.math.*

/**
 * 计算器/数学 Skill
 * 提供安全的数学表达式求值、单位转换和货币转换（可选联网）
 * 使用自定义数学表达式解析器，避免使用 javax.script（Android 不支持）
 */
class CalculatorSkill : SkillDefinition {

    companion object {
        private const val TAG = "CalculatorSkill"

        // 单位转换系数表 (转换为标准单位: 米/千克/摄氏度/平方米/立方米)
        private val LENGTH_UNITS = mapOf(
            "mm" to 0.001, "cm" to 0.01, "m" to 1.0, "km" to 1000.0,
            "in" to 0.0254, "inch" to 0.0254, "ft" to 0.3048, "feet" to 0.3048,
            "yd" to 0.9144, "yard" to 0.9144, "mile" to 1609.344
        )

        private val WEIGHT_UNITS = mapOf(
            "mg" to 0.000001, "g" to 0.001, "kg" to 1.0, "ton" to 1000.0,
            "oz" to 0.0283495, "ounce" to 0.0283495,
            "lb" to 0.453592, "lbs" to 0.453592, "pound" to 0.453592
        )

        private val TEMPERATURE_UNITS = setOf("c", "celsius", "f", "fahrenheit", "k", "kelvin")

        private val AREA_UNITS = mapOf(
            "mm2" to 0.000001, "cm2" to 0.0001, "m2" to 1.0, "km2" to 1000000.0,
            "ha" to 10000.0, "hectare" to 10000.0,
            "acre" to 4046.8564224
        )

        private val VOLUME_UNITS = mapOf(
            "ml" to 0.000001, "l" to 0.001, "liter" to 0.001, "litre" to 0.001,
            "m3" to 1.0, "gal" to 0.00378541, "gallon" to 0.00378541,
            "qt" to 0.000946353, "pint" to 0.000473176
        )

        // 汇率缓存（可选的货币转换）
        private var exchangeRates: Map<String, Double>? = null
        private var lastRateUpdate: Long = 0
        private const val RATE_CACHE_DURATION = 3600000L // 1小时缓存
    }

    override val skillName: String = "calculator"
    override val displayName: String = "计算器"
    override val description: String = "数学计算、单位转换和货币转换"
    override val requiredPermissions: List<String> = emptyList()

    override suspend fun initialize(context: Context) {
        Log.i(TAG, "CalculatorSkill initialized (using built-in math parser)")
    }

    override fun getTools(): List<ToolDefinition> = listOf(
        ToolDefinition(
            toolName = "calculate",
            displayName = "数学计算",
            description = "安全的数学表达式求值，支持四则运算、括号",
            parameters = listOf(
                ToolParameter("expression", "string", true,
                    "数学表达式，支持: + - * / ( ) 和小数")
            ),
            returnType = "number"
        ),
        ToolDefinition(
            toolName = "convert_unit",
            displayName = "单位转换",
            description = "长度、重量、温度、面积、体积的单位转换",
            parameters = listOf(
                ToolParameter("value", "number", true, "要转换的数值"),
                ToolParameter("from_unit", "string", true, "源单位（如 m, kg, c, f, m2, l 等）"),
                ToolParameter("to_unit", "string", true, "目标单位")
            ),
            returnType = "number"
        ),
        ToolDefinition(
            toolName = "currency_convert",
            displayName = "货币转换",
            description = "货币汇率转换（需要联网获取最新汇率）",
            parameters = listOf(
                ToolParameter("amount", "number", true, "金额"),
                ToolParameter("from_currency", "string", true, "源货币代码（如 USD, CNY, EUR, JPY）"),
                ToolParameter("to_currency", "string", true, "目标货币代码")
            ),
            returnType = "number"
        )
    )

    override suspend fun executeTool(toolName: String, parameters: Map<String, Any>): ToolResult {
        return try {
            when (toolName) {
                "calculate" -> calculate(parameters)
                "convert_unit" -> convertUnit(parameters)
                "currency_convert" -> currencyConvert(parameters)
                else -> ToolResult.Error("Unknown tool: $toolName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing $toolName", e)
            ToolResult.Error("计算失败: ${e.message}", e)
        }
    }

    /**
     * 安全表达式求值
     * 使用自定义数学表达式解析器
     */
    private fun calculate(params: Map<String, Any>): ToolResult {
        val expression = params["expression"] as? String
            ?: return ToolResult.Error("缺少参数: expression")

        if (expression.isBlank()) {
            return ToolResult.Error("表达式不能为空")
        }

        // 安全检查：禁止的危险模式
        val dangerousPatterns = listOf(
            Regex("""(exec|eval|require|import|load|run|Runtime|Process|File)""", RegexOption.IGNORE_CASE),
            Regex("""['"]\s*\+\s*['"]"""), // 字符串拼接
            Regex("""new\s+""", RegexOption.IGNORE_CASE),
            Regex("""this""", RegexOption.IGNORE_CASE),
            Regex("""\.\s*class"""),
            Regex("""getClass""", RegexOption.IGNORE_CASE)
        )

        for (pattern in dangerousPatterns) {
            if (pattern.containsMatchIn(expression)) {
                Log.w(TAG, "Dangerous pattern detected in expression: $expression")
                return ToolResult.Error("表达式包含不允许的操作")
            }
        }

        return try {
            val result = MathParser.evaluate(expression)
            ToolResult.Success(mapOf(
                "expression" to expression,
                "result" to result,
                "result_display" to formatNumber(result)
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Math parsing error", e)
            ToolResult.Error("表达式语法错误: ${e.message}")
        }
    }

    /**
     * 单位转换
     */
    private fun convertUnit(params: Map<String, Any>): ToolResult {
        val value = (params["value"] as? Number)?.toDouble()
            ?: return ToolResult.Error("缺少参数: value")
        val fromUnit = (params["from_unit"] as? String)?.lowercase()?.trim()
            ?: return ToolResult.Error("缺少参数: from_unit")
        val toUnit = (params["to_unit"] as? String)?.lowercase()?.trim()
            ?: return ToolResult.Error("缺少参数: to_unit")

        if (fromUnit == toUnit) {
            return ToolResult.Success(mapOf(
                "value" to value,
                "from_unit" to fromUnit,
                "to_unit" to toUnit,
                "result" to value,
                "result_display" to formatNumber(value)
            ))
        }

        // 温度转换特殊处理
        if (fromUnit in TEMPERATURE_UNITS && toUnit in TEMPERATURE_UNITS) {
            return convertTemperature(value, fromUnit, toUnit)
        }

        // 查找单位所属类别并转换
        val category = findUnitCategory(fromUnit, toUnit)
            ?: return ToolResult.Error("不兼容的单位: $fromUnit -> $toUnit")

        return when (category) {
            "length" -> convertByFactor(value, fromUnit, toUnit, LENGTH_UNITS)
            "weight" -> convertByFactor(value, fromUnit, toUnit, WEIGHT_UNITS)
            "area" -> convertByFactor(value, fromUnit, toUnit, AREA_UNITS)
            "volume" -> convertByFactor(value, fromUnit, toUnit, VOLUME_UNITS)
            else -> ToolResult.Error("不支持的转换类型")
        }
    }

    private fun findUnitCategory(unit: String): String? {
        return when {
            unit in LENGTH_UNITS -> "length"
            unit in WEIGHT_UNITS -> "weight"
            unit in TEMPERATURE_UNITS -> "temperature"
            unit in AREA_UNITS -> "area"
            unit in VOLUME_UNITS -> "volume"
            else -> null
        }
    }

    private fun findUnitCategory(u1: String, u2: String): String? {
        val cat1 = findUnitCategory(u1)
        val cat2 = findUnitCategory(u2)
        return if (cat1 != null && cat1 == cat2) cat1 else null
    }

    private fun convertByFactor(
        value: Double,
        fromUnit: String,
        toUnit: String,
        unitMap: Map<String, Double>
    ): ToolResult {
        val fromFactor = unitMap[fromUnit]
            ?: return ToolResult.Error("未知单位: $fromUnit")
        val toFactor = unitMap[toUnit]
            ?: return ToolResult.Error("未知单位: $toUnit")

        // 转成标准单位，再转成目标单位
        val standardValue = value * fromFactor
        val result = standardValue / toFactor

        return ToolResult.Success(mapOf(
            "value" to value,
            "from_unit" to fromUnit,
            "to_unit" to toUnit,
            "result" to result,
            "result_display" to formatNumber(result)
        ))
    }

    private fun convertTemperature(value: Double, fromUnit: String, toUnit: String): ToolResult {
        // 先转成摄氏度
        val celsius = when (fromUnit.first()) {
            'c' -> value
            'f' -> (value - 32) * 5.0 / 9.0
            'k' -> value - 273.15
            else -> return ToolResult.Error("未知温度单位: $fromUnit")
        }

        // 从摄氏度转成目标单位
        val result = when (toUnit.first()) {
            'c' -> celsius
            'f' -> celsius * 9.0 / 5.0 + 32
            'k' -> celsius + 273.15
            else -> return ToolResult.Error("未知温度单位: $toUnit")
        }

        return ToolResult.Success(mapOf(
            "value" to value,
            "from_unit" to fromUnit,
            "to_unit" to toUnit,
            "result" to result,
            "result_display" to formatNumber(result)
        ))
    }

    /**
     * 货币转换（联网获取汇率）
     */
    private suspend fun currencyConvert(params: Map<String, Any>): ToolResult {
        val amount = (params["amount"] as? Number)?.toDouble()
            ?: return ToolResult.Error("缺少参数: amount")
        val fromCurrency = (params["from_currency"] as? String)?.uppercase()?.trim()
            ?: return ToolResult.Error("缺少参数: from_currency")
        val toCurrency = (params["to_currency"] as? String)?.uppercase()?.trim()
            ?: return ToolResult.Error("缺少参数: to_currency")

        if (fromCurrency.length != 3 || toCurrency.length != 3) {
            return ToolResult.Error("货币代码应为3位字母（如 USD, CNY, EUR）")
        }

        if (fromCurrency == toCurrency) {
            return ToolResult.Success(mapOf(
                "amount" to amount,
                "from" to fromCurrency,
                "to" to toCurrency,
                "result" to amount,
                "rate" to 1.0
            ))
        }

        // 尝试从缓存或网络获取汇率
        val rates = getExchangeRates()
        if (rates == null) {
            return ToolResult.Error("无法获取汇率信息，请检查网络连接")
        }

        // 标准货币代码映射
        val baseCurrency = "USD"

        val fromRate = if (fromCurrency == baseCurrency) 1.0 else rates[fromCurrency]
        val toRate = if (toCurrency == baseCurrency) 1.0 else rates[toCurrency]

        if (fromRate == null) {
            return ToolResult.Error("不支持的货币: $fromCurrency")
        }
        if (toRate == null) {
            return ToolResult.Error("不支持的货币: $toCurrency")
        }

        // 通过美元中间价转换
        val result = amount / fromRate * toRate

        return ToolResult.Success(mapOf(
            "amount" to amount,
            "from" to fromCurrency,
            "to" to toCurrency,
            "result" to result,
            "result_display" to formatCurrency(result, toCurrency),
            "rate" to (toRate / fromRate)
        ))
    }

    /**
     * 获取汇率（优先缓存，过期则联网获取）
     */
    private fun getExchangeRates(): Map<String, Double>? {
        val now = System.currentTimeMillis()
        if (exchangeRates != null && (now - lastRateUpdate) < RATE_CACHE_DURATION) {
            return exchangeRates
        }

        return try {
            // 使用免费 API: exchangerate-api.com
            val url = URL("https://api.exchangerate-api.com/v4/latest/USD")
            val json = url.readText()

            val gson = Gson()
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val response: Map<String, Any> = gson.fromJson(json, type)

            val ratesRaw = response["rates"] as? Map<String, Double>
            if (ratesRaw != null) {
                exchangeRates = ratesRaw
                lastRateUpdate = now
                exchangeRates
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch exchange rates", e)
            exchangeRates // 返回过期缓存（如果有）
        }
    }

    private fun formatNumber(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format("%.6f", value).trimEnd('0').trimEnd('.')
        }
    }

    private fun formatCurrency(value: Double, currency: String): String {
        val symbol = when (currency) {
            "CNY" -> "¥"
            "USD" -> "$"
            "EUR" -> "€"
            "GBP" -> "£"
            "JPY" -> "¥"
            "KRW" -> "₩"
            else -> "$currency "
        }
        return "$symbol${String.format("%.2f", value)}"
    }

    override fun release() {
        exchangeRates = null
        Log.i(TAG, "CalculatorSkill released")
    }

    /**
     * 简单数学表达式解析器（支持 + - * / 和括号）
     */
    object MathParser {
        fun evaluate(expression: String): Double {
            val tokens = tokenize(expression.replace(" ", ""))
            val rpn = infixToRPN(tokens)
            return evaluateRPN(rpn)
        }

        private fun tokenize(expr: String): List<String> {
            val tokens = mutableListOf<String>()
            var i = 0
            while (i < expr.length) {
                when (val c = expr[i]) {
                    in '0'..'9', '.' -> {
                        val sb = StringBuilder()
                        while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) {
                            sb.append(expr[i])
                            i++
                        }
                        tokens.add(sb.toString())
                        continue
                    }
                    '+', '-', '*', '/', '(', ')' -> {
                        tokens.add(c.toString())
                    }
                    else -> throw IllegalArgumentException("非法字符: $c")
                }
                i++
            }
            return tokens
        }

        private fun infixToRPN(tokens: List<String>): List<String> {
            val output = mutableListOf<String>()
            val operators = ArrayDeque<String>()
            
            for (token in tokens) {
                when {
                    token.matches(Regex("""^-?\d+(\.\d+)?$""")) -> output.add(token)
                    token == "(" -> operators.addLast(token)
                    token == ")" -> {
                        while (operators.isNotEmpty() && operators.last() != "(") {
                            output.add(operators.removeLast())
                        }
                        if (operators.isNotEmpty()) operators.removeLast()
                    }
                    token in setOf("+", "-", "*", "/") -> {
                        while (operators.isNotEmpty() && precedence(operators.last()) >= precedence(token)) {
                            output.add(operators.removeLast())
                        }
                        operators.addLast(token)
                    }
                }
            }
            while (operators.isNotEmpty()) {
                output.add(operators.removeLast())
            }
            return output
        }

        private fun precedence(op: String): Int = when (op) {
            "+", "-" -> 1
            "*", "/" -> 2
            else -> 0
        }

        private fun evaluateRPN(rpn: List<String>): Double {
            val stack = ArrayDeque<Double>()
            for (token in rpn) {
                when {
                    token.matches(Regex("""^-?\d+(\.\d+)?$""")) -> stack.addLast(token.toDouble())
                    token in setOf("+", "-", "*", "/") -> {
                        val b = stack.removeLast()
                        val a = stack.removeLast()
                        val result = when (token) {
                            "+" -> a + b
                            "-" -> a - b
                            "*" -> a * b
                            "/" -> a / b
                            else -> 0.0
                        }
                        stack.addLast(result)
                    }
                }
            }
            return stack.last()
        }
    }
}
