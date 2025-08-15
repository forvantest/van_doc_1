/* (C) 2024 */
package com.bot.ncl.adapter.event.app.util;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Scope("prototype")
public class StringCutUtil {

    public void cut(Map<String, String> map, String keyName, int length) {
        if (map.containsKey(keyName) && map.get(keyName) != null) {
            String value = map.get(keyName).replace("'", "");
            if (computeStringByteLength(value) > length) {
                value = cutStringByByteLength(value, length);
                map.put(keyName, "'" + value + "'");
            }
        } else {
            map.put(keyName, "' '");
        }
    }

    private int computeStringByteLength(String s) {
        if (s != null) {
            byte[] b = s.getBytes(Charset.forName("BIG5"));
            return b.length;
        }
        return 0;
    }

    private String cutStringByByteLength(String s, int length) {
        if (s != null) {
            byte[] b = s.getBytes(Charset.forName("BIG5"));
            byte[] newBytes = Arrays.copyOf(b, length);
            return new String(newBytes, Charset.forName("BIG5"));
        }
        return s;
    }
}
