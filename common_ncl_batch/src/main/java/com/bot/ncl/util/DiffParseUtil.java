/* (C) 2024 */
package com.bot.ncl.util;

import com.bot.txcontrol.util.parse.Parse;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Scope("prototype")
public class DiffParseUtil {

    @Autowired private Parse parse;

    private DiffParseUtil() {
        // YOU SHOULD USE @Autowired ,NOT new DiffParseUtil()
    }

    public String nvl(String s, String defaultValue) {
        return Objects.isNull(s) || s.isBlank() ? defaultValue : s;
    }

    public int toInt(String s) {
        int i;
        try {
            i = Integer.parseInt(s);
        } catch (Exception e) {
            i = 0;
        }
        return i;
    }

    public long toLong(String s) {
        long i;
        try {
            i = Long.parseLong(s);
        } catch (Exception e) {
            i = 0L;
        }
        return i;
    }

    public BigDecimal toBigDecimal(String s) {
        BigDecimal b;
        if (Objects.isNull(s) || s.isBlank()) {
            s = "0";
        }
        try {
            b = parse.string2BigDecimal(s);
        } catch (Exception e) {
            b = BigDecimal.ZERO;
        }
        if (Objects.isNull(b)) {
            b = BigDecimal.ZERO;
        }
        return b;
    }
}
