/* (C) 2024 */
package com.bot.ncl.adapter.event.app.util;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Scope("prototype")
public class AppendUtil {
    public void append(
            String sourceKey,
            String targetKey,
            Map<String, String> map,
            StringBuilder sb1,
            StringBuilder sb2,
            String separator) {
        sb1.append("\"").append(targetKey).append("\"").append(separator);
        sb2.append(map.get(sourceKey)).append(separator);
    }

    public void appendByEntry(
            Map.Entry<String, String> entry, StringBuilder sb1, StringBuilder sb2) {
        sb1.append("\"").append(entry.getKey()).append("\"");
        sb2.append(entry.getValue());
    }
}
