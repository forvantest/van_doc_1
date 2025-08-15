/* (C) 2024 */
package com.bot.ncl.adapter.event.app.mapping;

import com.bot.ncl.adapter.event.app.mapping.interfaces.IMapper;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Scope("prototype")
public class ClmrToCltot implements IMapper {

    @Override
    public Map<String, String> mapping(Map<String, String> map) {
        Map<String, String> mapped = new HashMap<>();

        String code = map.get("CODE");

        mapped.put("CODE", map.get("CODE"));

        //        int crdb = ParseUtil.stringToInteger(map.get("CRDB"));
        int crdb = code.startsWith("12") ? 1 : 0;

        switch (crdb) {
            case 0:
                mapped.put("RCVAMT", map.get("TOTAMT"));
                mapped.put("PAYAMT", "0");
                break;
            case 1:
                mapped.put("RCVAMT", "0");
                mapped.put("PAYAMT", map.get("TOTAMT"));
                break;
            case 2:
                mapped.put("RCVAMT", map.get("TOTAMT"));
                mapped.put("PAYAMT", map.get("TOTAMT"));
                break;
            default:
                break;
        }

        mapped.put("TOTCNT", map.get("TOTCNT"));

        return mapped;
    }
}
