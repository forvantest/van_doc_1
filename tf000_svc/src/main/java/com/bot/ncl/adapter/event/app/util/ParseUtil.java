/* (C) 2024 */
package com.bot.ncl.adapter.event.app.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Scope("prototype")
public class ParseUtil {

    public static Integer stringToInteger(String s) {
        int result;
        try {
            result = Integer.parseInt(s);
        } catch (Exception e) {
            StringWriter errors = new StringWriter();
            e.printStackTrace(new PrintWriter(errors));
            // 無法轉換時固定擺0
            result = 0;
        }
        return result;
    }

    public static double stringToDouble(String s) {
        double result;
        try {
            result = Double.parseDouble(s);
        } catch (Exception e) {
            StringWriter errors = new StringWriter();
            e.printStackTrace(new PrintWriter(errors));
            // 無法轉換時固定擺0
            result = 0.0;
        }
        return result;
    }

    public static BigDecimal stringToBigDecimal(String s) {
        BigDecimal result;
        try {
            result = new BigDecimal(s);
        } catch (Exception e) {
            StringWriter errors = new StringWriter();
            e.printStackTrace(new PrintWriter(errors));
            // 無法轉換時固定擺0
            result = BigDecimal.ZERO;
        }
        return result;
    }

    public static String toPascalCase(String s) {
        String[] parts;
        if (s.contains("_")) {
            parts = s.split("_");
        } else {
            parts = new String[1];
            parts[0] = s;
        }
        StringBuilder camelCaseString = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.trim().isEmpty()) {
                String firstChar = part.substring(0, 1);
                String remainingChars = part.substring(1);
                camelCaseString
                        .append(firstChar.toUpperCase())
                        .append(remainingChars.toLowerCase());
            }
        }
        return camelCaseString.toString();
    }
}
