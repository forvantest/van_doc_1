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
public class ClmrToClmr implements IMapper {

    @Override
    public Map<String, String> mapping(Map<String, String> map) {
        Map<String, String> mapped = new HashMap<>();

        mapped.put("CODE", map.get("CODE"));
        mapped.put("PBRNO", map.get("PBRNO"));
        mapped.put("VRCODE", map.get("VRCODE"));
        mapped.put("RIDDUP", map.get("RIDDUP"));
        // 2024-06-13 Wei 增加 from 展嘉:
        // DUPCYC	銷帳編號不可重覆週期	DECIMAL	3		"000:一律不可重覆(現行作法)
        // 1xx:xx天內不可重覆(即上次代收付日與本次代收付日不可小於xx天)
        // 2xx:xx周內不可重覆(即上次代收付日與本次代收付日不可小於xx周)
        // 3xx:xx個月不可重覆(即上次代收付日與本次代收付日不可小於xx月)"
        // 轉換處理:
        // 若RIDDUP(銷帳編號重複記號)為1:可重覆時,不判斷此欄位,以0寫入,
        // 否則,若收付類別為145952,146002,111801,121801,111981,121981,366009,115265時,以312(12個月內不可重覆)寫入,
        // 否則,若收付類別為115892,115902,115988時,以306(6個月內不可重覆)寫入,
        // 其他,以302(2個月內不可重覆)寫入.
        String code = map.get("CODE");
        String riddup = map.get("RIDDUP");
        String dupcyc = "";
        if (riddup.equals("1")) {
            dupcyc = "0";
        } else {
            switch (code) {
                case "145952", "146002", "111801", "121801", "111981", "121981", "366009", "115265":
                    dupcyc = "312";
                    break;
                case "115892", "115902", "115988":
                    dupcyc = "306";
                    break;
                default:
                    dupcyc = "302";
                    break;
            }
        }
        mapped.put("DUPCYC", dupcyc);
        mapped.put("ACTNO", map.get("ACTNO"));
        mapped.put("MSG1", map.get("MSG1"));
        mapped.put("PUTTIME", map.get("PUTTIME"));
        mapped.put("CHKTYPE", map.get("CHKTYPE"));
        mapped.put("CHKAMT", map.get("CHKAMT"));
        mapped.put("UNIT", map.get("UNIT"));
        mapped.put("AMT", map.get("AMT"));
        mapped.put("CNAME", map.get("CNAME"));
        mapped.put("STOP", map.get("STOP"));
        mapped.put("AFCBV", map.get("AFCBV"));
        mapped.put("NETINFO", map.get("NETINFO"));
        mapped.put("PRINT", map.get("PRINT"));
        mapped.put("STOPDATE", map.get("STOPDATE"));
        mapped.put("STOPTIME", map.get("STOPTIME"));
        // 2024-06-06 Wei 增加 from 展嘉:
        // 若該筆資料的CODE(收付類別)前兩碼為"12"時，CRDB(代收付記號)以1(代付)寫入，否則以0(代收)寫入。
        String crdb = "0";
        if (code.startsWith("12")) {
            crdb = "1";
        }
        mapped.put("CRDB", crdb);
        mapped.put("LKCODE", map.get("LKCODE"));

        return mapped;
    }
}
