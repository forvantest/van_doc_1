/* (C) 2024 */
package com.bot.ncl.adapter.event.app.mapping;

import com.bot.ncl.modify.*;
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
public class ClmrTo5Tables {
    @Autowired private ModifyClmr modifyClmr;
    @Autowired private ModifyCltmr modifyCltmr;
    @Autowired private ModifyClmc modifyClmc;
    @Autowired private ModifyClfee modifyClfee;
    @Autowired private ModifyCltot modifyCltot;
    @Getter private Date startTime;

    @Getter private List<String> sqlErrorMsg;

    @Getter private Long clmrMaxCnt;
    @Getter private Long cltmrMaxCnt;
    @Getter private Long clfeeMaxCnt;
    @Getter private Long cltotMaxCnt;
    @Getter private Long clmcMaxCnt;

    @Getter private long clmcSize;

    public void mapping(
            List<Map<String, String>> list, TransactionCase batchTransaction, Date startTime) {
        this.startTime = startTime;
        sqlErrorMsg = new ArrayList<>();
        List<Map<String, String>> clmrInsertList = new ArrayList<>();
        List<Map<String, String>> cltmrInsertList = new ArrayList<>();
        List<Map<String, String>> clfeeInsertList = new ArrayList<>();
        List<Map<String, String>> cltotInsertList = new ArrayList<>();
        List<Map<String, String>> clmcInsertList = new ArrayList<>();
        ClmrToClmr clmrToClmr = new ClmrToClmr();
        ClmrToCltmr clmrToCltmr = new ClmrToCltmr();
        ClmrToClfee clmrToClfee = new ClmrToClfee();
        ClmrToClmc clmrToClmc = new ClmrToClmc();
        ClmrToCltot clmrToCltot = new ClmrToCltot();
        for (Map<String, String> map : list) {
            clmrInsertList.add(clmrToClmr.mapping(map));
            cltmrInsertList.add(clmrToCltmr.mapping(map));
            clfeeInsertList.add(clmrToClfee.mapping(map));
            cltotInsertList.add(clmrToCltot.mapping(map));
            clmrToClmc.mapping(map);
            if (!clmrToClmc.getDupErrorMsg().isEmpty()) {
                sqlErrorMsg.add(clmrToClmc.getDupErrorMsg());
            }
        }
        List<Map<String, String>> clmcList = clmrToClmc.getDataList();
        if (!Objects.isNull(clmcList)) {
            clmcInsertList.addAll(clmcList);
            clmcSize = clmcList.size();
        }
        modifyClmr.modify(
                clmrInsertList,
                new ArrayList<>(),
                new ArrayList<>(),
                batchTransaction,
                this.startTime);
        this.startTime = modifyClmr.getStartTime();
        if (!modifyClmr.getInsertSqlErrorMsg().isEmpty()) {
            sqlErrorMsg.add("Insert CLMR Error Details:");
            sqlErrorMsg.addAll(modifyClmr.getInsertSqlErrorMsg());
        }
        clmrMaxCnt = modifyClmr.getMaxCnt();

        modifyCltmr.modify(
                cltmrInsertList,
                new ArrayList<>(),
                new ArrayList<>(),
                batchTransaction,
                this.startTime);
        this.startTime = modifyCltmr.getStartTime();
        if (!modifyCltmr.getInsertSqlErrorMsg().isEmpty()) {
            sqlErrorMsg.add("Insert CLTMR Error Details:");
            sqlErrorMsg.addAll(modifyCltmr.getInsertSqlErrorMsg());
        }
        cltmrMaxCnt = modifyCltmr.getMaxCnt();

        modifyClfee.modify(
                clfeeInsertList,
                new ArrayList<>(),
                new ArrayList<>(),
                batchTransaction,
                this.startTime);
        this.startTime = modifyClfee.getStartTime();
        if (!modifyClfee.getInsertSqlErrorMsg().isEmpty()) {
            sqlErrorMsg.add("Insert CLFEE Error Details:");
            sqlErrorMsg.addAll(modifyClfee.getInsertSqlErrorMsg());
        }
        clfeeMaxCnt = modifyClfee.getMaxCnt();

        modifyCltot.modify(
                cltotInsertList,
                new ArrayList<>(),
                new ArrayList<>(),
                batchTransaction,
                this.startTime);
        this.startTime = modifyCltot.getStartTime();
        if (!modifyCltot.getInsertSqlErrorMsg().isEmpty()) {
            sqlErrorMsg.add("Insert CLTOT Error Details:");
            sqlErrorMsg.addAll(modifyCltot.getInsertSqlErrorMsg());
        }
        cltotMaxCnt = modifyCltot.getMaxCnt();

        modifyClmc.modify(
                clmcInsertList,
                new ArrayList<>(),
                new ArrayList<>(),
                batchTransaction,
                this.startTime);
        this.startTime = modifyClmc.getStartTime();
        if (!modifyClmc.getInsertSqlErrorMsg().isEmpty()) {
            sqlErrorMsg.add("Insert CLMC Error Details:");
            sqlErrorMsg.addAll(modifyClmc.getInsertSqlErrorMsg());
        }
        clmcMaxCnt = modifyClmc.getMaxCnt();
    }
}
