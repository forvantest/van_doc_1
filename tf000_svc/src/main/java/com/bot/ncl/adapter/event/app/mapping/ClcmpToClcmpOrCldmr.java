/* (C) 2024 */
package com.bot.ncl.adapter.event.app.mapping;

import com.bot.ncl.modify.ModifyClcmp;
import com.bot.ncl.modify.ModifyCldmr;
import com.bot.txcontrol.jpa.transaction.TransactionCase;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Scope("prototype")
public class ClcmpToClcmpOrCldmr {
    @Autowired private ModifyClcmp modifyClcmp;
    @Autowired private ModifyCldmr modifyCldmr;
    @Getter private Date startTime;

    @Getter private List<String> sqlErrorMsg;

    @Getter private Long clcmpMaxCnt;

    @Getter private Long cldmrMaxCnt;

    public void mapping(
            List<Map<String, String>> list, TransactionCase batchTransaction, Date startTime) {
        List<Map<String, String>> clcmpInsertList = new ArrayList<>();
        List<Map<String, String>> cldmrInsertList = new ArrayList<>();
        sqlErrorMsg = new ArrayList<>();
        for (Map<String, String> map : list) {
            String targetTableName = "CLCMP";
            String code = map.get("CODE");
            code = code.replaceAll("'", "");
            if (code.length() < 3) {
                String errorMsg = "不寫入,收付類別長度不足,無法分辨應寫入CLCMP還是CLDMR," + map;
                sqlErrorMsg.add(errorMsg);
                continue;
            }
            String codeLeft3 = code.substring(0, 3);
            // 以下收付類別轉入虛擬分戶檔CLDMR
            // 111801,121801,111981,121981,121961,
            // 118xxx,128xxx,119xxx,129xxx
            // 其他都轉入代收比對檔CLCMP
            if (code.equals("111801")
                    || code.equals("121801")
                    || code.equals("111981")
                    || code.equals("121981")
                    || code.equals("111961") // 2024-11-08 Wei: 補上這個
                    || code.equals("121961")
                    || codeLeft3.equals("118")
                    || codeLeft3.equals("128")
                    || codeLeft3.equals("119")
                    || codeLeft3.equals("129")) {
                targetTableName = "CLDMR";
            }

            if (targetTableName.equals("CLDMR")) {
                String amt = map.get("AMT");
                map.put("BAL", amt);
                map.remove("AMT");
                cldmrInsertList.add(map);
            } else {
                clcmpInsertList.add(map);
            }
        }
        modifyClcmp.modify(
                clcmpInsertList, new ArrayList<>(), new ArrayList<>(), batchTransaction, startTime);
        this.startTime = modifyClcmp.getStartTime();
        sqlErrorMsg.addAll(modifyClcmp.getInsertSqlErrorMsg());
        clcmpMaxCnt = modifyClcmp.getMaxCnt();
        modifyCldmr.modify(
                cldmrInsertList, new ArrayList<>(), new ArrayList<>(), batchTransaction, startTime);
        this.startTime = modifyCldmr.getStartTime();
        sqlErrorMsg.addAll(modifyCldmr.getInsertSqlErrorMsg());
        cldmrMaxCnt = modifyCldmr.getMaxCnt();
    }
}
