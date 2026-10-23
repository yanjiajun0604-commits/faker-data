# COND.EXPR 条件规则 - 自定义函数扩展

条件规则基于 Spring **SpEL**（Spring Expression Language），支持在条件表达式中使用自定义函数。

## 快速使用（内置函数）

以下函数已内置，无需额外配置，可直接在条件表达式中使用：

### 字符串函数

| 函数 | 语法 | 示例 | 说明 |
|------|------|------|------|
| `length` | `#length(字符串)` | `#length(#name) > 2` | 字符串长度；null 返回 0 |
| `contains` | `#contains(字符串, 子串)` | `#contains(#email, '@')` | 字符串是否包含子串 |
| `startsWith` | `#startsWith(字符串, 前缀)` | `#startsWith(#name, '张')` | 是否以指定前缀开始 |
| `endsWith` | `#endsWith(字符串, 后缀)` | `#endsWith(#email, '.com')` | 是否以指定后缀结束 |
| `isEmpty` | `#isEmpty(字符串)` | `#isEmpty(#remark)` | 是否为空或 null |
| `isNotEmpty` | `#isNotEmpty(字符串)` | `#isNotEmpty(#phone)` | 是否非空且非 null |

### 数值函数

| 函数 | 语法 | 示例 | 说明 |
|------|------|------|------|
| `between` | `#between(数值, 最小值, 最大值)` | `#between(#age, 18, 60)` | 是否在闭区间内 |

### 类型转换

| 函数 | 语法 | 示例 | 说明 |
|------|------|------|------|
| `toString` | `#toString(任意类型)` | `#toString(#score) == '95'` | 转为字符串；null 返回空串 |

### 规则配置示例

```json
{
    "condition": "#startsWith(#name, '张') && #between(#age, 18, 35)",
    "onTrue": [
        {
            "code": "RES.INPUTAREA",
            "inputs": [{ "value": "青年" }]
        }
    ],
    "onFalse": [
        {
            "code": "RES.INPUTAREA",
            "inputs": [{ "value": "其他" }]
        }
    ],
    "code": "COND.EXPR"
}
```

## 自定义函数扩展

### 1. 编写函数方法

创建一个包含静态方法的函数类：

```java
package com.example;

public class MyFunctions {

    /** 判断字符串是否匹配正则表达式 */
    public static boolean matchesRegex(String str, String regex) {
        if (str == null || regex == null) return false;
        return str.matches(regex);
    }

    /** 判断日期字符串是否在指定范围内（格式 yyyy-MM-dd） */
    public static boolean dateBetween(String date, String start, String end) {
        if (date == null || start == null || end == null) return false;
        return date.compareTo(start) >= 0 && date.compareTo(end) <= 0;
    }
}
```

### 2. 实现 SpelFunctionProvider 接口

将函数注册到 SpEL 上下文，并标注 `@Component`：

```java
package com.example;

import faker.data.service.generate.SpelFunctionProvider;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class MyFunctionProvider implements SpelFunctionProvider {

    private static final Map<String, Method> METHODS = new LinkedHashMap<>();

    static {
        try {
            METHODS.put("matchesRegex",
                    MyFunctions.class.getMethod("matchesRegex", String.class, String.class));
            METHODS.put("dateBetween",
                    MyFunctions.class.getMethod("dateBetween", String.class, String.class, String.class));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("函数注册失败", e);
        }
    }

    @Override
    public void register(StandardEvaluationContext ctx) {
        METHODS.forEach(ctx::registerFunction);
    }
}
```

### 3. 使用自定义函数

注册后即可在条件表达式中使用：

```
#matchesRegex(#phone, '^1[3-9]\\d{9}$')
#dateBetween(#birth, '1990-01-01', '2000-12-31')
```

## 命名规范

- 函数名统一使用 **驼峰命名**（如 `startsWith`、`matchesRegex`）
- 条件表达式中 `#函数名` 与 `#字段名` 都使用 `#` 前缀，区别在于：
  - **字段引用**：`#字段名`，无括号，引用本行其他列的值
  - **函数调用**：`#函数名(参数...)`，有括号，调用注册的函数

## 注意事项

1. 函数必须是 **public static** 方法
2. 每个条件表达式求值前都会调用 `register()`，建议在 `static` 块中缓存 `Method` 对象
3. 如果多个 Provider 注册了同名函数，后注册的会被忽略（不覆盖）
4. SpEL 本身不支持中文变量名，函数名也应使用英文
