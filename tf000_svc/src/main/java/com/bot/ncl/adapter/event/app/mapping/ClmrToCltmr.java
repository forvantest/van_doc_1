/* (C) 2024 */
package com.bot.ncl.adapter.event.app.mapping;

import com.bot.ncl.adapter.event.app.mapping.interfaces.IMapper;
import com.bot.ncl.adapter.event.app.util.StringCutUtil;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Scope("prototype")
public class ClmrToCltmr implements IMapper {

    private final StringCutUtil stringCutUtil = new StringCutUtil();

    @Override
    public Map<String, String> mapping(Map<String, String> map) {
        Map<String, String> mapped = new HashMap<>();
        if ((!map.containsKey("PUTNAME")) || map.get("PUTNAME") == null) {
            mapped.put("PUTNAME", "00000000");
        }

        if ((!map.containsKey("SCNAME")) || map.get("SCNAME") == null) {
            mapped.put("SCNAME", "            ");
        }
        // 特殊處理
        // SCNAME 截斷至12碼長
        stringCutUtil.cut(map, "SCNAME", 12);
        // FEENAME 截斷至42碼長
        stringCutUtil.cut(map, "FEENAME", 42);

        // PUTNAME 拆三欄位
        String oldPutname = map.get("PUTNAME");
        String putsend = "";
        String putform = "";
        String putname = "";
        if (!oldPutname.isEmpty()) {
            putsend = String.valueOf(oldPutname.charAt(0));
            putform = String.valueOf(oldPutname.charAt(1));
            putname = oldPutname.substring(2);
        }
        map.put("PUTSEND", putsend);
        map.put("PUTFORM", putform);
        map.put("PUTNAME", putname);

        mapped.put("CODE", map.get("CODE"));
        mapped.put("ATMCODE", map.get("ATMCODE"));
        mapped.put("ENTPNO", map.get("ENTPNO"));
        mapped.put("HENTPNO", map.get("HENTPNO"));
        mapped.put("SCNAME", map.get("SCNAME"));
        mapped.put("CDATA", map.get("CDATA"));

        mapped.put("APPDT", map.get("APPDT"));
        mapped.put("UPDDT", map.get("UPDDT"));
        mapped.put("LPUTDT", map.get("LPUTDT"));
        mapped.put("LLPUTDT", map.get("LLPUTDT"));
        mapped.put("ULPUTDT", map.get("ULPUTDT"));
        mapped.put("ULLPUTDT", map.get("ULLPUTDT"));

        mapped.put("CLSACNO", map.get("CLSACNO"));
        mapped.put("CLSSBNO", map.get("CLSSBNO"));
        mapped.put("CLSDTLNO", map.get("CLSDTLNO"));
        mapped.put("PRTYPE", map.get("PRTYPE"));
        mapped.put("EBTYPE", map.get("EBTYPE"));
        mapped.put("PWTYPE", map.get("PWTYPE"));
        mapped.put("FEENAME", map.get("FEENAME"));
        mapped.put("PUTNAME", map.get("PUTNAME"));

        return mapped;
    }
}
