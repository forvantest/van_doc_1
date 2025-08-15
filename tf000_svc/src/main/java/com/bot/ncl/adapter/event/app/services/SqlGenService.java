/* (C) 2024 */
package com.bot.ncl.adapter.event.app.services;

import com.bot.ncl.adapter.event.app.mapping.ClbafToClbaf;
import com.bot.ncl.adapter.event.app.mapping.ClcmpToClcmpOrCldmr;
import com.bot.ncl.adapter.event.app.mapping.CldtlToCldtl;
import com.bot.ncl.adapter.event.app.mapping.ClmrTo5Tables;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.txcontrol.jpa.transaction.TransactionCase;
import java.util.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Scope("prototype")
public class SqlGenService {
    @Autowired private ClmrTo5Tables clmrTo5Tables;
    @Autowired private ClcmpToClcmpOrCldmr clcmpToClcmpOrClDmr;
    @Autowired private CldtlToCldtl cldtlToCldtl;
    @Autowired private ClbafToClbaf clbafToClbaf;
    @Autowired private ClmrService clmrService;

    @Getter private List<String> sqlErrorMsg;

    @Getter private List<String> insertResult;

    @Getter private Date startTime;

    public void createCommand(
            String tableName,
            List<Map<String, String>> list,
            TransactionCase batchTransaction,
            Date startTime,
            long sourceSize) {
        insertResult = new ArrayList<>();
        switch (tableName) {
            case "CLMR" -> {
                clmrTo5Tables.mapping(list, batchTransaction, startTime);
                this.startTime = clmrTo5Tables.getStartTime();
                sqlErrorMsg = clmrTo5Tables.getSqlErrorMsg();
                long insertClmrSuccessCnt = clmrTo5Tables.getClmrMaxCnt();
                insertResult.add("[目標檔CLMR-寫入成功筆數]:" + insertClmrSuccessCnt);
                long clmrFailedCnt = sourceSize - insertClmrSuccessCnt;
                insertResult.add("[目標檔CLMR-未寫入筆數]:" + clmrFailedCnt);
                long insertCltmrSuccessCnt = clmrTo5Tables.getCltmrMaxCnt();
                insertResult.add("[目標檔CLTMR-寫入成功筆數]:" + insertCltmrSuccessCnt);
                long cltmrFailedCnt = sourceSize - insertCltmrSuccessCnt;
                insertResult.add("[目標檔CLTMR-未寫入筆數]:" + cltmrFailedCnt);
                long insertCltotSuccessCnt = clmrTo5Tables.getCltotMaxCnt();
                insertResult.add("[目標檔CLTOT-寫入成功筆數]:" + insertCltotSuccessCnt);
                long cltotFailedCnt = sourceSize - insertCltotSuccessCnt;
                insertResult.add("[目標檔CLTOT-未寫入筆數]:" + cltotFailedCnt);
                long insertClfeeSuccessCnt = clmrTo5Tables.getClfeeMaxCnt();
                insertResult.add("[目標檔CLFEE-寫入成功筆數]:" + insertClfeeSuccessCnt);
                long clfeeFailedCnt = sourceSize - insertClfeeSuccessCnt;
                insertResult.add("[目標檔CLFEE-未寫入筆數]:" + clfeeFailedCnt);
                long clmcSize = clmrTo5Tables.getClmcSize();
                insertResult.add("[目標檔CLMC-彙總後應寫入筆數]:" + clmcSize);
                long insertClmcSuccessCnt = clmrTo5Tables.getClmcMaxCnt();
                insertResult.add("[目標檔CLMC-寫入成功筆數]:" + insertClmcSuccessCnt);
                long clmcFailedCnt = clmcSize - insertClmcSuccessCnt;
                insertResult.add("[目標檔CLMC-未寫入筆數]:" + clmcFailedCnt);
            }
            case "CLCMP" -> {
                clcmpToClcmpOrClDmr.mapping(list, batchTransaction, startTime);
                this.startTime = clcmpToClcmpOrClDmr.getStartTime();
                sqlErrorMsg = clcmpToClcmpOrClDmr.getSqlErrorMsg();
                long insertClcmpSuccessCnt = clcmpToClcmpOrClDmr.getClcmpMaxCnt();
                insertResult.add("[目標檔CLCMP-寫入成功筆數]:" + insertClcmpSuccessCnt);
                long insertCldmrSuccessCnt = clcmpToClcmpOrClDmr.getCldmrMaxCnt();
                insertResult.add("[目標檔CLDMR-寫入成功筆數]:" + insertCldmrSuccessCnt);
                long failedCnt = sourceSize - insertClcmpSuccessCnt - insertCldmrSuccessCnt;
                insertResult.add("[未寫入筆數]:" + failedCnt);
            }
            case "CLDTL" -> {
                Map<String, Integer> pbrnoList = getPbrnoMap();
                cldtlToCldtl.mapping(list, pbrnoList, batchTransaction, startTime);
                this.startTime = cldtlToCldtl.getStartTime();
                sqlErrorMsg = cldtlToCldtl.getSqlErrorMsg();
                long insertSuccessCnt = cldtlToCldtl.getMaxCnt();
                insertResult.add("[目標檔CLDTL-寫入成功筆數]:" + insertSuccessCnt);
                long failedCnt = sourceSize - insertSuccessCnt;
                insertResult.add("[未寫入筆數]:" + failedCnt);
            }
            case "CLBAF" -> {
                Map<String, Integer> pbrnoList = getPbrnoMap();
                clbafToClbaf.mapping(list, pbrnoList, batchTransaction, startTime);
                this.startTime = clbafToClbaf.getStartTime();
                sqlErrorMsg = clbafToClbaf.getSqlErrorMsg();
                long insertSuccessCnt = clbafToClbaf.getMaxCnt();
                insertResult.add("[目標檔CLBAF-寫入成功筆數]:" + insertSuccessCnt);
                long failedCnt = sourceSize - insertSuccessCnt;
                insertResult.add("[未寫入筆數]:" + failedCnt);
            }
        }
    }

    private Map<String, Integer> getPbrnoMap() {
        Map<String, Integer> map = new HashMap<>();
        List<ClmrBus> lClmr = clmrService.findAll(0, Integer.MAX_VALUE);
        if (!Objects.isNull(lClmr)) {
            for (ClmrBus tClmr : lClmr) {
                if (!map.containsKey(tClmr.getCode())) {
                    map.put(tClmr.getCode(), tClmr.getPbrno());
                }
            }
        }
        return map;
    }
}
