/* (C) 2024 */
package com.bot.ncl.modify;

import com.bot.ncl.diff.ErrorDataCom;
import com.bot.ncl.dto.entities.ClfeeBus;
import com.bot.ncl.jpa.entities.impl.ClfeeId;
import com.bot.ncl.jpa.svc.ClfeeService;
import com.bot.ncl.util.DiffParseUtil;
import com.bot.ncl.util.ModifyRecordUtil;
import com.bot.ncl.util.batch.BatchUtil;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.jpa.transaction.TransactionCase;
import java.math.BigDecimal;
import java.util.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Scope("prototype")
public class ModifyClfee {

    @Autowired private DiffParseUtil diffParse;

    @Autowired private ModifyRecordUtil modifyRecordUtil;

    @Autowired private BatchUtil batchUtil;

    @Autowired private ClfeeService clfeeService;
    @Autowired private ErrorDataCom errorDataCom;

    @Getter private Date startTime;

    @Getter private List<String> insertSqlErrorMsg;

    @Getter private Long maxCnt;

    private static final int MODIFY_BATCH_SIZE = 1000;

    private ModifyClfee() {
        super();
        // YOU SHOULD USE @Autowired ,NOT new ModifyClfee()
    }

    public Map<String, String> modify(
            List<Map<String, String>> insertList,
            List<Map<String, Map<String, String>>> updateList,
            List<Map<String, String>> deleteList,
            TransactionCase batchTransaction,
            Date startTime) {
        Map<String, String> result = new HashMap<>();
        this.startTime = startTime;
        insertSqlErrorMsg = new ArrayList<>();
        modifyRecordUtil.initialize("CLFEE");

        doInsertClfee(insertList, batchTransaction);
        doUpdateClfee(updateList, batchTransaction);
        doDeleteClfee(deleteList, batchTransaction);

        modifyRecordUtil.output();

        return result;
    }

    private void doInsertClfee(
            List<Map<String, String>> insertList, TransactionCase batchTransaction) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "doInsertClfee");
        int errorCount = 0;
        int successCount = 0;
        int insertBatchCount = 0;
        List<ClfeeBus> insertBatchList = new ArrayList<>();
        for (Map<String, String> m : insertList) {
            ClfeeBus clfeeBus = new ClfeeBus();

            String keyCodeFee = m.get("KEY_CODE_FEE");
            if (Objects.isNull(keyCodeFee) || keyCodeFee.isBlank()) {
                String errorMsg = "不寫入,收付類別/手續費類別(KEY_CODE_FEE)為空白," + m;
                modifyRecordUtil.addRecord(errorMsg);
                insertSqlErrorMsg.add(errorMsg);
                continue;
            }
            clfeeBus.setKeyCodeFee(keyCodeFee);
            clfeeBus.setCfee1(diffParse.toBigDecimal(m.get("CFEE1")));
            clfeeBus.setCfee2(diffParse.toBigDecimal(m.get("CFEE2")));
            clfeeBus.setCfee3(diffParse.toBigDecimal(m.get("CFEE3")));
            clfeeBus.setCfee4(diffParse.toBigDecimal(m.get("CFEE4")));
            clfeeBus.setFkd(diffParse.toInt(m.get("FKD")));
            clfeeBus.setMfee(diffParse.toBigDecimal(m.get("MFEE")));
            clfeeBus.setCfeeeb(diffParse.toBigDecimal(m.get("CFEEB")));

            // 特殊處理
            clfeeBus.setTxtype(diffParse.nvl(m.get("TXTYPE"), "00"));
            clfeeBus.setStamt(diffParse.toInt(m.get("STAMT")));
            clfeeBus.setFee(diffParse.toBigDecimal(m.get("FEE")));
            clfeeBus.setFeecost(diffParse.toBigDecimal(m.get("FEECOST")));
            clfeeBus.setCfee003(diffParse.toBigDecimal(m.get("CFEE_003")));
            clfeeBus.setFeeCalType(diffParse.toInt(m.get("FEE_CAL_TYPE")));

            //            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "clfeeBus={}",
            // clfeeBus);
            // PK
            ClfeeId clfeeId = getClfeeId(m);

            String checkResult = checkColumns(clfeeBus);
            if (!checkResult.isBlank()) {
                modifyRecordUtil.addRecord(checkResult + "," + m);
                insertSqlErrorMsg.add(checkResult + "," + m);
                continue;
            }

            insertBatchList.add(clfeeBus);
            insertBatchCount++;
            if (insertBatchCount >= MODIFY_BATCH_SIZE) {
                try {
                    clfeeService.insertAll(insertBatchList);
                    batchTransaction.commit();
                } catch (Exception e) {
                    ApLogHelper.error(
                            log,
                            false,
                            LogType.NORMAL.getCode(),
                            "insert failed, error={}",
                            e.getMessage());
                    String errorMsg = "Insert Failed, unexpected db error,error=" + e.getMessage();
                    modifyRecordUtil.addRecord(errorMsg);
                    insertSqlErrorMsg.add(errorMsg);
                    errorCount++;
                    startTime = new Date();
                    errorDataCom.writeTempErrorData("CLFEE", insertBatchList); // 紀錄失敗資料結束後一筆一筆寫入
                    insertBatchList.clear();
                    insertBatchCount = 0;
                    batchTransaction.rollBack();
                    continue;
                }
                successCount += insertBatchCount;
                startTime = new Date();
                insertBatchList.clear();
                insertBatchCount = 0;
            }
        }
        if (!insertBatchList.isEmpty()) {
            try {
                clfeeService.insertAll(insertBatchList);
                batchTransaction.commit();
            } catch (Exception e) {
                ApLogHelper.error(
                        log,
                        false,
                        LogType.NORMAL.getCode(),
                        "insert failed, error={}",
                        e.getMessage());
                String errorMsg = "Insert Failed, unexpected db error,error=" + e.getMessage();
                modifyRecordUtil.addRecord(errorMsg);
                insertSqlErrorMsg.add(errorMsg);
                errorDataCom.writeTempErrorData("CLFEE", insertBatchList); // 紀錄失敗資料結束後一筆一筆寫入
                errorCount++;
                insertBatchCount = 0;
                batchTransaction.rollBack();
            }
            successCount += insertBatchCount;
            startTime = new Date();
            insertBatchList.clear();
        }
        errorDataCom.readTmpErrorFile(1, "CLFEE", batchTransaction); // 一筆一筆寫入失敗資料,若還是失敗則出ErrorCLFEE
        maxCnt = clfeeService.findAllMaxCnt();
        modifyRecordUtil.addRecord(
                "Insert size = "
                        + insertList.size()
                        + " ,success = "
                        + successCount
                        + " ,error = "
                        + errorCount);
    }

    private String checkColumns(ClfeeBus clfeeBus) {
        boolean isError = false;
        String errorMsg = "不寫入,欄位檢核不通過 ";
        // KEY_CODE_FEE 收付類別/手續費類別 CHAR 6
        if (!Objects.isNull(clfeeBus.getKeyCodeFee()) && clfeeBus.getKeyCodeFee().length() > 6) {
            errorMsg += ", KEY_CODE_FEE 收付類別/手續費類別 CHAR 6 ,值為(" + clfeeBus.getKeyCodeFee() + ") ";
            isError = true;
        }
        // TXTYPE 外部代收機構類別 VARCHAR2 2
        if (!Objects.isNull(clfeeBus.getTxtype()) && clfeeBus.getTxtype().length() > 2) {
            errorMsg += ", TXTYPE 外部代收機構類別 VARCHAR2 2 ,值為(" + clfeeBus.getTxtype() + ") ";
            isError = true;
        }
        // STAMT 適用交易金額起額 DECIMAL 6
        if (clfeeBus.getStamt() > 999999) {
            errorMsg += ", STAMT 適用交易金額起額 DECIMAL 6 ,值為(" + clfeeBus.getStamt() + ") ";
            isError = true;
        }
        // CFEE1 事業單位每筆給付費用 DECIMAL 6 2
        if (clfeeBus.getCfee1().compareTo(new BigDecimal("9999.99")) > 0) {
            errorMsg += ", CFEE1 事業單位每筆給付費用 DECIMAL 6 2 ,值為(" + clfeeBus.getCfee1() + ") ";
            isError = true;
        }
        // CFEE2 主辦行每筆給付代收行費用(G61會使用) DECIMAL 5 2
        if (clfeeBus.getCfee2().compareTo(new BigDecimal("999.99")) > 0) {
            errorMsg +=
                    ", CFEE2 主辦行每筆給付代收行費用(G61會使用) DECIMAL 5 2 ,值為(" + clfeeBus.getCfee2() + ") ";
            isError = true;
        }
        // CFEE3 臨櫃手續費用 DECIMAL 6 2
        if (clfeeBus.getCfee3().compareTo(new BigDecimal("9999.99")) > 0) {
            errorMsg += ", CFEE3 臨櫃手續費用 DECIMAL 6 2 ,值為(" + clfeeBus.getCfee3() + ") ";
            isError = true;
        }
        // CFEE4 事業單位給付主辦行每月費用 DECIMAL 12 2
        if (clfeeBus.getCfee4().compareTo(new BigDecimal("9999999999.99")) > 0) {
            errorMsg += ", CFEE4 事業單位給付主辦行每月費用 DECIMAL 12 2 ,值為(" + clfeeBus.getCfee4() + ") ";
            isError = true;
        }
        // FKD 手續費種類-依繳款金額 DECIMAL 1
        if (clfeeBus.getFkd() > 9) {
            errorMsg += ", FKD 手續費種類-依繳款金額 DECIMAL 1 ,值為(" + clfeeBus.getFkd() + ") ";
            isError = true;
        }
        // MFEE 給付中華電信每筆中介費用 DECIMAL 5 2
        if (clfeeBus.getMfee().compareTo(new BigDecimal("999.99")) > 0) {
            errorMsg += ", MFEE 給付中華電信每筆中介費用 DECIMAL 5 2 ,值為(" + clfeeBus.getMfee() + ") ";
            isError = true;
        }
        // CFEEEB 全繳手續費 DECIMAL 5 2
        if (clfeeBus.getCfeeeb().compareTo(new BigDecimal("999.99")) > 0) {
            errorMsg += ", CFEEEB 全繳手續費 DECIMAL 5 2 ,值為(" + clfeeBus.getCfeeeb() + ") ";
            isError = true;
        }
        // FEE 外部收付-主辦行手續費 DECIMAL 5 2
        if (clfeeBus.getFee().compareTo(new BigDecimal("999.99")) > 0) {
            errorMsg += ", FEE 外部收付-主辦行手續費 DECIMAL 5 2 ,值為(" + clfeeBus.getFee() + ") ";
            isError = true;
        }
        // FEECOST 外部收付-外部通道手續費 DECIMAL 5 2
        if (clfeeBus.getFeecost().compareTo(new BigDecimal("999.99")) > 0) {
            errorMsg += ", FEECOST 外部收付-外部通道手續費 DECIMAL 5 2 ,值為(" + clfeeBus.getFeecost() + ") ";
            isError = true;
        }
        // CFEE_003 外部收付-營業部分潤手續費 DECIMAL 5 2
        if (clfeeBus.getCfee003().compareTo(new BigDecimal("999.99")) > 0) {
            errorMsg += ", CFEE_003 外部收付-營業部分潤手續費 DECIMAL 5 2 ,值為(" + clfeeBus.getCfee003() + ") ";
            isError = true;
        }
        // FEE_CAL_TYPE 手續費計算方式 DECIMAL 1
        if (clfeeBus.getFeeCalType() > 9) {
            errorMsg += ", FEE_CAL_TYPE 手續費計算方式 DECIMAL 1 ,值為(" + clfeeBus.getFeeCalType() + ") ";
            isError = true;
        }
        return isError ? errorMsg : "";
    }

    private void doUpdateClfee(
            List<Map<String, Map<String, String>>> updateList, TransactionCase batchTransaction) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "doUpdateClfee");
        int errorCount = 0;
        int successCount = 0;
        int updateBatchCount = 0;
        List<ClfeeBus> updateBatchList = new ArrayList<>();
        for (Map<String, Map<String, String>> um : updateList) {
            Map<String, String> updateKey = um.get("UPDATE_KEY");
            Map<String, String> m = um.get("UPDATE_VALUE");
            // PK
            ClfeeId clfeeId = getClfeeId(updateKey);
            ClfeeBus clfeeBus = clfeeService.findById(clfeeId);
            if (Objects.isNull(clfeeBus)) {
                modifyRecordUtil.addRecord("Update Skip, clfeeBus 不存在,clfeeId=" + clfeeId);
                errorCount++;
                continue;
            }
            if (m.containsKey("CFEE1")) {
                clfeeBus.setCfee1(diffParse.toBigDecimal(m.get("CFEE1")));
            }
            if (m.containsKey("CFEE2")) {
                clfeeBus.setCfee2(diffParse.toBigDecimal(m.get("CFEE2")));
            }
            if (m.containsKey("CFEE3")) {
                clfeeBus.setCfee3(diffParse.toBigDecimal(m.get("CFEE3")));
            }
            if (m.containsKey("CFEE4")) {
                clfeeBus.setCfee4(diffParse.toBigDecimal(m.get("CFEE4")));
            }
            if (m.containsKey("FKD")) {
                clfeeBus.setFkd(diffParse.toInt(m.get("FKD")));
            }
            if (m.containsKey("MFEE")) {
                clfeeBus.setMfee(diffParse.toBigDecimal(m.get("MFEE")));
            }
            if (m.containsKey("CFEEB")) {
                clfeeBus.setCfeeeb(diffParse.toBigDecimal(m.get("CFEEB")));
            }

            // 特殊處理
            clfeeBus.setFee(diffParse.toBigDecimal(m.get("FEE")));
            clfeeBus.setFeecost(diffParse.toBigDecimal(m.get("FEECOST")));
            clfeeBus.setCfee003(diffParse.toBigDecimal(m.get("CFEE_003")));
            clfeeBus.setFeeCalType(diffParse.toInt(m.get("FEE_CAL_TYPE")));

            String checkResult = checkColumns(clfeeBus);
            if (!checkResult.isBlank()) {
                modifyRecordUtil.addRecord(checkResult + "," + m);
                continue;
            }
            updateBatchList.add(clfeeBus);
            updateBatchCount++;
            if (updateBatchCount >= MODIFY_BATCH_SIZE) {
                try {
                    clfeeService.updateAllSilent(updateBatchList);
                    batchTransaction.commit();
                } catch (Exception e) {
                    ApLogHelper.error(
                            log,
                            false,
                            LogType.NORMAL.getCode(),
                            "update failed, error={}",
                            e.getMessage());
                    String errorMsg = "Update Failed, unexpected db error,error=" + e.getMessage();
                    modifyRecordUtil.addRecord(errorMsg);
                    errorCount++;
                    startTime = new Date();
                    errorDataCom.writeTempErrorData("CLFEE", updateBatchList); // 紀錄失敗資料結束後一筆一筆寫入
                    updateBatchList.clear();
                    updateBatchCount = 0;
                    batchTransaction.rollBack();
                    continue;
                }
                successCount += updateBatchCount;
                startTime = new Date();
                updateBatchList.clear();
                updateBatchCount = 0;
            }
        }
        if (!updateBatchList.isEmpty()) {
            try {
                clfeeService.updateAllSilent(updateBatchList);
                batchTransaction.commit();
            } catch (Exception e) {
                ApLogHelper.error(
                        log,
                        false,
                        LogType.NORMAL.getCode(),
                        "update failed, error={}",
                        e.getMessage());
                String errorMsg = "Update Failed, unexpected db error,error=" + e.getMessage();
                modifyRecordUtil.addRecord(errorMsg);
                errorDataCom.writeTempErrorData("CLFEE", updateBatchList); // 紀錄失敗資料結束後一筆一筆寫入
                errorCount++;
                updateBatchCount = 0;
                batchTransaction.rollBack();
            }
            successCount += updateBatchCount;
            startTime = new Date();
            updateBatchList.clear();
        }
        errorDataCom.readTmpErrorFile(2, "CLFEE", batchTransaction); // 一筆一筆寫入失敗資料,若還是失敗則出ErrorCLFEE
        modifyRecordUtil.addRecord(
                "Update size = "
                        + updateList.size()
                        + " ,success = "
                        + successCount
                        + " ,error = "
                        + errorCount);
    }

    private void doDeleteClfee(
            List<Map<String, String>> deleteList, TransactionCase batchTransaction) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "doDeleteClfee");
        int errorCount = 0;
        int successCount = 0;
        int deleteBatchCount = 0;
        List<ClfeeBus> deleteBatchList = new ArrayList<>();
        for (Map<String, String> m : deleteList) {
            // PK
            ClfeeId clfeeId = getClfeeId(m);
            ClfeeBus clfeeBus = clfeeService.findById(clfeeId);
            if (Objects.isNull(clfeeBus)) {
                modifyRecordUtil.addRecord("Delete Skip, clfeeBus 不存在,clfeeId=" + clfeeId);
                errorCount++;
                continue;
            }
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "clfeeBus={}", clfeeBus);
            deleteBatchList.add(clfeeBus);
            deleteBatchCount++;
            if (deleteBatchCount >= MODIFY_BATCH_SIZE) {
                try {
                    clfeeService.deleteAllSilent(deleteBatchList);
                    batchTransaction.commit();
                } catch (Exception e) {
                    ApLogHelper.error(
                            log,
                            false,
                            LogType.NORMAL.getCode(),
                            "delete failed, error={}",
                            e.getMessage());
                    String errorMsg = "Delete Failed, unexpected db error,error=" + e.getMessage();
                    modifyRecordUtil.addRecord(errorMsg);
                    errorCount++;
                    startTime = new Date();
                    errorDataCom.writeTempErrorData("CLFEE", deleteBatchList); // 紀錄失敗資料結束後一筆一筆寫入
                    deleteBatchList.clear();
                    deleteBatchCount = 0;
                    batchTransaction.rollBack();
                    continue;
                }
                successCount += deleteBatchCount;
                startTime = new Date();
                deleteBatchList.clear();
                deleteBatchCount = 0;
            }
        }
        if (!deleteBatchList.isEmpty()) {
            try {
                clfeeService.deleteAllSilent(deleteBatchList);
                batchTransaction.commit();
            } catch (Exception e) {
                ApLogHelper.error(
                        log,
                        false,
                        LogType.NORMAL.getCode(),
                        "delete failed, error={}",
                        e.getMessage());
                String errorMsg = "Delete Failed, unexpected db error,error=" + e.getMessage();
                modifyRecordUtil.addRecord(errorMsg);
                errorDataCom.writeTempErrorData("CLFEE", deleteBatchList); // 紀錄失敗資料結束後一筆一筆寫入
                errorCount++;
                deleteBatchCount = 0;
                batchTransaction.rollBack();
            }
            successCount += deleteBatchCount;
            startTime = new Date();
            deleteBatchList.clear();
        }
        errorDataCom.readTmpErrorFile(4, "CLFEE", batchTransaction); // 一筆一筆寫入失敗資料,若還是失敗則出ErrorCLFEE
        modifyRecordUtil.addRecord(
                "Delete size = "
                        + deleteList.size()
                        + " ,success = "
                        + successCount
                        + " ,error = "
                        + errorCount);
    }

    private ClfeeId getClfeeId(Map<String, String> m) {
        String keyCodeFee = m.get("KEY_CODE_FEE");
        String txtype = Objects.isNull(m.get("TXTYPE")) ? "00" : m.get("TXTYPE");
        int stamt = diffParse.toInt(m.get("STAMT"));
        return new ClfeeId(keyCodeFee, txtype, stamt);
    }
}
