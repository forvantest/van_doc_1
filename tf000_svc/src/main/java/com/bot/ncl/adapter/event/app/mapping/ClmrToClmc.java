/* (C) 2024 */
package com.bot.ncl.adapter.event.app.mapping;

import com.bot.ncl.adapter.event.app.mapping.interfaces.IMapper;
import java.util.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Scope("prototype")
public class ClmrToClmc implements IMapper {

    @Getter private String dupErrorMsg;

    public ClmrToClmc() {
        clmcMap = new HashMap<>();
    }

    Map<String, Map<String, String>> clmcMap;

    @Override
    public Map<String, String> mapping(Map<String, String> map) {
        dupErrorMsg = "";
        // 特殊處理:OPUTTIME
        /*
        系統轉換若媒體檔名="07X0111332",
                "17X0115962","17X0115972",
                "17X0130859","17X0133511",
                "02C0149074","17X0156963",
                "17X0156973","17X0156982",
                "17X0156882","17X0133378",
                "17Z0198732","17Z0193082",
                "17Z0134309","17Z0199328",
                "17X0151742","17X0158892"
        或媒體檔名第3碼="S","T",則為1,否則為0
        */
        String puttype = map.get("PUTTYPE");
        String putsend = map.get("PUTSEND");
        String putform = map.get("PUTFORM");
        String putname = map.get("PUTNAME");
        String mediaFileName = puttype + putsend + putform + putname;
        String oputtime = "0"; // OPUTTIME

        switch (mediaFileName) {
            case "07X0111332",
                    "17X0115962",
                    "17X0115972",
                    "17X0130859",
                    "17X0133511",
                    "02C0149074",
                    "17X0156963",
                    "17X0156973",
                    "17X0156982",
                    "17X0156882",
                    "17X0133378",
                    "17Z0198732",
                    "17Z0193082",
                    "17Z0134309",
                    "17Z0199328",
                    "17X0151742",
                    "17X0158892":
                oputtime = "1";
                break;
            default:
                break;
        }
        if (putsend.equals("S") || putsend.equals("T")) {
            oputtime = "1";
        }
        map.put("OPUTTIME", oputtime);

        // 轉換時固定值
        // PUT_ENCODE 固定0
        // PUT_COMPRESS 固定0
        map.put("PUT_ENCODE", "0");
        map.put("PUT_COMPRESS", "0");

        // 特殊處理
        // PUTDTFG 挑檔日類別
        // 系統轉換若收付類別前5碼為11011~11115,11331,11521~11525,則為1(營業日-假日延後),否則為0(營業日-假日提前)
        String putdtfg = "0";
        Integer codeLeft5 = 0;
        try {
            Integer.parseInt(map.get("CODE").substring(0, 5).trim());
        } catch (Exception e) {
            codeLeft5 = 0;
        }
        if ((codeLeft5.compareTo(11011) >= 0 && codeLeft5.compareTo(11115) <= 0)
                || codeLeft5.compareTo(11331) == 0
                || (codeLeft5.compareTo(11521) >= 0 && codeLeft5.compareTo(11525) <= 0)) {
            putdtfg = "1";
        }
        map.put("PUTDTFG", putdtfg);

        Map<String, String> mapped;
        mapped = checkClmc(map);

        return mapped;
    }

    private Map<String, String> checkClmc(Map<String, String> map) {
        Map<String, String> mapped = new HashMap<>();
        String code = map.get("CODE");

        String putname = map.get("PUTNAME");
        String putsend = map.get("PUTSEND");
        String putform = map.get("PUTFORM");
        String puttype = map.get("PUTTYPE");
        String putaddr = map.get("PUTADDR");
        String put_encode = map.get("PUT_ENCODE");
        String put_compress = map.get("PUT_COMPRESS");
        String oputtime = map.get("OPUTTIME");
        String cyck1 = map.get("CYCK1");
        String cycno1 = map.get("CYCNO1");
        String cyck2 = map.get("CYCK2");
        String cycno2 = map.get("CYCNO2");
        String putdtfg = map.get("PUTDTFG");
        String putdt = map.get("PUTDT");
        String msg2 = map.get("MSG2");
        String tputdt = map.get("TPUTDT");

        mapped.put("PUTNAME", putname);
        mapped.put("PUTSEND", putsend);
        mapped.put("PUTFORM", putform);
        mapped.put("PUTTYPE", puttype);
        mapped.put("PUTADDR", putaddr);
        mapped.put("PUT_ENCODE", put_encode);
        mapped.put("PUT_COMPRESS", put_compress);
        mapped.put("OPUTTIME", oputtime);
        mapped.put("CYCK1", cyck1);
        mapped.put("CYCNO1", cycno1);
        mapped.put("CYCK2", cyck2);
        mapped.put("CYCNO2", cycno2);
        mapped.put("PUTDTFG", putdtfg);
        mapped.put("PUTDT", putdt);
        mapped.put("MSG2", msg2);
        mapped.put("TPUTDT", tputdt);

        if (clmcMap.containsKey(putname)) {
            Map<String, String> lastClmc = clmcMap.get(putname);

            //  檢查是否相同,若不同要印log警告
            compareMap(code, putname, lastClmc, mapped);

            // 把USECNT+1
            int usecnt = Integer.parseInt(lastClmc.get("USECNT"));
            usecnt++;
            lastClmc.put("USECNT", "" + usecnt);
            clmcMap.put(putname, lastClmc);
        } else {
            // 第一次遇到存進clmcMap
            mapped.put("USECNT", "1");
            clmcMap.put(putname, mapped);
        }
        return mapped;
    }

    private void compareMap(
            String code,
            String putname,
            Map<String, String> lastClmc,
            Map<String, String> thisClmc) {
        boolean isDifferent = false;
        String errorMsg = "CLMC彙總資料時發現PUTNAME媒體檔名相同但資料內容有差異,code=" + code + ",putname=" + putname;
        for (Map.Entry<String, String> entry : lastClmc.entrySet()) {
            String lastClmcColumnName = entry.getKey();
            String lastClmcColumnValue = entry.getValue();
            String thisClmcColumnValue = thisClmc.get(lastClmcColumnName);
            // 不比較欄位:USECNT
            if (lastClmcColumnName.equals("USECNT")) {
                continue;
            }
            if (!lastClmcColumnValue.equals(thisClmcColumnValue)) {
                // 資料不相同
                errorMsg +=
                        ",欄位名稱="
                                + lastClmcColumnName
                                + ",第一筆的值="
                                + lastClmcColumnValue
                                + ",這一筆的值="
                                + thisClmcColumnValue;
                isDifferent = true;
            }
        }
        if (isDifferent) {
            dupErrorMsg = errorMsg;
        }
    }

    public List<Map<String, String>> getDataList() {
        List<Map<String, String>> dataList = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : clmcMap.entrySet()) {
            Map<String, String> mapped = new HashMap<>();
            Map<String, String> map = entry.getValue();
            if (Objects.isNull(map.get("PUTNAME"))
                    || map.get("PUTNAME").isBlank()
                    || map.get("PUTNAME").equals("000000")) {
                continue;
            }
            mapped.put("PUTNAME", map.get("PUTNAME"));
            mapped.put("PUTSEND", map.get("PUTSEND"));
            mapped.put("PUTFORM", map.get("PUTFORM"));
            mapped.put("PUTTYPE", map.get("PUTTYPE"));
            mapped.put("PUTADDR", map.get("PUTADDR"));
            mapped.put("PUT_ENCODE", map.get("PUT_ENCODE"));
            mapped.put("PUT_COMPRESS", map.get("PUT_COMPRESS"));
            mapped.put("OPUTTIME", map.get("OPUTTIME"));
            mapped.put("CYCK1", map.get("CYCK1"));
            mapped.put("CYCNO1", map.get("CYCNO1"));
            mapped.put("CYCK2", map.get("CYCK2"));
            mapped.put("CYCNO2", map.get("CYCNO2"));
            mapped.put("PUTDTFG", map.get("PUTDTFG"));
            mapped.put("PUTDT", map.get("PUTDT"));
            mapped.put("MSG2", map.get("MSG2"));
            mapped.put("TPUTDT", map.get("TPUTDT"));
            mapped.put("USECNT", map.get("USECNT"));
            dataList.add(mapped);
        }
        return dataList;
    }
}
