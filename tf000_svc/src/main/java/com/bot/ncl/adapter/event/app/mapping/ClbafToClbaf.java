/* (C) 2024 */
package com.bot.ncl.adapter.event.app.mapping;

import com.bot.ncl.modify.ModifyClbaf;
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
public class ClbafToClbaf {
    @Autowired private ModifyClbaf modifyClbaf;
    @Getter private Date startTime;

    @Getter private List<String> sqlErrorMsg;

    @Getter private Long maxCnt;

    public void mapping(
            List<Map<String, String>> list,
            Map<String, Integer> pbrnoList,
            TransactionCase batchTransaction,
            Date startTime) {
        for (Map<String, String> map : list) {
            String txType = map.get("TXTYPE");
            if (txType.isEmpty()) {
                txType = " ";
            }
            map.put("TXTYPE", txType);
            if (!map.get("CODE").isEmpty()) {
                if (pbrnoList.containsKey(map.get("CODE"))) {
                    map.put("PBRNO", "" + pbrnoList.get(map.get("CODE")));
                }
            }
        }
        //        data.add(updateCLBAF());
        modifyClbaf.modify(list, new ArrayList<>(), new ArrayList<>(), batchTransaction, startTime);
        this.startTime = modifyClbaf.getStartTime();
        sqlErrorMsg = modifyClbaf.getInsertSqlErrorMsg();
        maxCnt = modifyClbaf.getMaxCnt();
    }

    private String updateCLBAF() {
        String updateSql = "";
        updateSql += " MERGE INTO CLBAF T ";
        updateSql += " USING ( ";
        updateSql += "   SELECT F.CLLBR ";
        updateSql += "        , F.ENTDY ";
        updateSql += "        , F.CODE ";
        updateSql += "        , F.CRDB ";
        updateSql += "        , F.TXTYPE ";
        updateSql += "        , F.CURCD ";
        updateSql += "        , M.PBRNO ";
        updateSql += "   FROM CLBAF F ";
        updateSql += "   LEFT JOIN CLMR M ON M.CODE = F.CODE ";
        updateSql += "   WHERE M.PBRNO IS NOT NULL ";
        updateSql += " ) S ";
        updateSql += " ON ( ";
        updateSql += "   S.CLLBR = T.CLLBR ";
        updateSql += "   AND S.ENTDY = T.ENTDY ";
        updateSql += "   AND S.CODE = T.CODE ";
        updateSql += "   AND S.CRDB = T.CRDB ";
        updateSql += "   AND S.TXTYPE = T.TXTYPE ";
        updateSql += "   AND S.CURCD = T.CURCD ";
        updateSql += " ) ";
        updateSql += " WHEN MATCHED THEN UPDATE SET ";
        updateSql += " T.PBRNO = S.PBRNO ";
        updateSql += " ; ";
        return updateSql;
    }
}
