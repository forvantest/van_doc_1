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
public class ClmrToClfee implements IMapper {

    @Override
    public Map<String, String> mapping(Map<String, String> map) {
        Map<String, String> mapped = new HashMap<>();

        Map<String, String> hardCodeMap = new HashMap<>();
        hardCodeMap.put("TXTYPE", "00");
        hardCodeMap.put("FEE", "0");
        hardCodeMap.put("FEECOST", "0");
        hardCodeMap.put("CFEE_003", "0");
        hardCodeMap.put("FEE_CAL_TYPE", "0");

        mapped.put("KEY_CODE_FEE", map.get("CODE"));
        mapped.put("TXTYPE", hardCodeMap.get("TXTYPE"));
        mapped.put("CFEE1", map.get("CFEE1"));
        mapped.put("CFEE2", map.get("CFEE2"));
        mapped.put("FKD", map.get("FKD"));
        mapped.put("MFEE", map.get("MFEE"));
        mapped.put("CFEE3", map.get("CFEE3"));
        mapped.put("CFEE4", map.get("CFEE4"));
        mapped.put("CFEEB", map.get("CFEEB"));
        mapped.put("FEE", hardCodeMap.get("FEE"));
        mapped.put("FEECOST", hardCodeMap.get("FEECOST"));
        mapped.put("CFEE_003", hardCodeMap.get("CFEE_003"));
        mapped.put("FEE_CAL_TYPE", hardCodeMap.get("FEE_CAL_TYPE"));

        return mapped;
    }
}
