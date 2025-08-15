/* (C) 2024 */
package com.bot.ncl.adapter.event.app.mapping;

import com.bot.ncl.adapter.event.app.util.StringCutUtil;
import com.bot.ncl.modify.ModifyCldtl;
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
public class CldtlToCldtl {
    @Autowired private ModifyCldtl modifyCldtl;
    @Getter private Date startTime;

    @Getter private List<String> sqlErrorMsg;

    @Getter private Long maxCnt;

    private final StringCutUtil stringCutUtil = new StringCutUtil();

    private int cnt;

    public void mapping(
            List<Map<String, String>> list,
            Map<String, Integer> pbrnoList,
            TransactionCase batchTransaction,
            Date startTime) {
        cnt = 1; // 初始值
        List<Map<String, String>> insertList = new ArrayList<>();
        for (Map<String, String> map : list) {
            // 編立交易序號
            rowNumberToTxtno(map);
            // RCPTID 截斷至26碼長
            stringCutUtil.cut(map, "RCPTID", 26);
            // USERDATA 截斷至40碼長
            stringCutUtil.cut(map, "USERDATA", 40);
            // CALDY 交易日曆日 用代收日轉入
            String entdy = map.get("ENTDY");
            map.put("CALDY", entdy);
            if (!map.get("CODE").isEmpty()) {
                if (pbrnoList.containsKey(map.get("CODE"))) {
                    map.put("PBRNO", "" + pbrnoList.get(map.get("CODE")));
                }
            }

            insertList.add(map);
        }
        modifyCldtl.modify(
                insertList, new ArrayList<>(), new ArrayList<>(), batchTransaction, startTime);
        this.startTime = modifyCldtl.getStartTime();
        sqlErrorMsg = modifyCldtl.getInsertSqlErrorMsg();
        maxCnt = modifyCldtl.getMaxCnt();
    }

    private void rowNumberToTxtno(Map<String, String> map) {
        map.put("TXTNO", "" + cnt);
        cnt++;
    }

    private String updateCLDTL() {
        String updateSql = "";
        updateSql += " MERGE INTO CLDTL T ";
        updateSql += " USING ( ";
        updateSql += "   SELECT T.CODE ";
        updateSql += "        , T.RCPTID ";
        updateSql += "        , T.ENTDY ";
        updateSql += "        , T.TRMNO ";
        updateSql += "        , T.TXTNO ";
        updateSql += "        , M.PBRNO ";
        updateSql += "        , NVL(F.CFEE2,0) AS CFEE2";
        updateSql += "   FROM CLDTL T ";
        updateSql += "   LEFT JOIN CLMR M ON M.CODE = T.CODE ";
        updateSql += "   LEFT JOIN CLFEE F ON F.KEY_CODE_FEE = T.CODE ";
        updateSql += "                    AND F.TXTYPE = '00' ";
        updateSql += "                    AND F.STAMT = 0 ";
        updateSql += "   WHERE M.PBRNO IS NOT NULL ";
        updateSql += " ) S ";
        updateSql += " ON ( ";
        updateSql += "   S.CODE = T.CODE ";
        updateSql += "   AND S.RCPTID = T.RCPTID ";
        updateSql += "   AND S.ENTDY = T.ENTDY ";
        updateSql += "   AND S.TRMNO = T.TRMNO ";
        updateSql += "   AND S.TXTNO = T.TXTNO ";
        updateSql += " ) ";
        updateSql += " WHEN MATCHED THEN UPDATE SET ";
        updateSql += " T.PBRNO = S.PBRNO ";
        updateSql += " , T.CFEE2 = S.CFEE2 ";
        updateSql += " ; ";
        return updateSql;
    }
}
