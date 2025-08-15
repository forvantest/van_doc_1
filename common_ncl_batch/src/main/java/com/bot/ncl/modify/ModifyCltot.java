/* (C) 2024 */
package com.bot.ncl.modify;

import com.bot.ncl.diff.ErrorDataCom;
import com.bot.ncl.dto.entities.CltotBus;
import com.bot.ncl.jpa.entities.impl.CltotId;
import com.bot.ncl.jpa.svc.CltotService;
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
public class ModifyCltot {

    @Autowired private DiffParseUtil diffParse;

    @Autowired private ModifyRecordUtil modifyRecordUtil;

    @Autowired private BatchUtil batchUtil;

    @Autowired private CltotService cltotService;
    @Autowired private ErrorDataCom errorDataCom;

    @Getter private Date startTime;

    @Getter private List<String> insertSqlErrorMsg;

    @Getter private Long maxCnt;

    private static final int MODIFY_BATCH_SIZE = 1000;

    private ModifyCltot() {
        super();
        // YOU SHOULD USE @Autowired ,NOT new ModifyCltot()
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
        modifyRecordUtil.initialize("CLTOT");

        doInsertCltot(insertList, batchTransaction);
        doUpdateCltot(updateList, batchTransaction);
        doDeleteCltot(deleteList, batchTransaction);

        modifyRecordUtil.output();

        return result;
    }

    private void doInsertCltot(
            List<Map<String, String>> insertList, TransactionCase batchTransaction) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "doInsertCltot");
        int errorCount = 0;
        int successCount = 0;
        int insertBatchCount = 0;
        List<CltotBus> insertBatchList = new ArrayList<>();
        for (Map<String, String> m : insertList) {
            CltotBus cltotBus = new CltotBus();

            String code = m.get("CODE");
            if (Objects.isNull(code) || code.isBlank()) {
                String errorMsg = "不寫入,收付類別為空白," + m;
                modifyRecordUtil.addRecord(errorMsg);
                insertSqlErrorMsg.add(errorMsg);
                continue;
            }
            cltotBus.setCode(code);
            cltotBus.setRcvamt(diffParse.toBigDecimal(m.get("RCVAMT")));
            cltotBus.setPayamt(diffParse.toBigDecimal(m.get("PAYAMT")));
            cltotBus.setTotcnt(diffParse.toInt(m.get("TOTCNT")));

            // 特殊處理
            cltotBus.setCurcd(diffParse.toInt(m.get("CURCD")));

            String checkResult = checkColumns(cltotBus);
            if (!checkResult.isBlank()) {
                modifyRecordUtil.addRecord(checkResult + "," + m);
                insertSqlErrorMsg.add(checkResult + "," + m);
                continue;
            }

            insertBatchList.add(cltotBus);
            insertBatchCount++;
            if (insertBatchCount >= MODIFY_BATCH_SIZE) {
                try {
                    cltotService.insertAll(insertBatchList);
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
                    errorDataCom.writeTempErrorData("CLTOT", insertBatchList); // 紀錄失敗資料結束後一筆一筆寫入
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
                cltotService.insertAll(insertBatchList);
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
                errorDataCom.writeTempErrorData("CLTOT", insertBatchList); // 紀錄失敗資料結束後一筆一筆寫入
                errorCount++;
                insertBatchCount = 0;
                batchTransaction.rollBack();
            }
            successCount += insertBatchCount;
            startTime = new Date();
            insertBatchList.clear();
        }
        errorDataCom.readTmpErrorFile(1, "CLTOT", batchTransaction); // 一筆一筆寫入失敗資料,若還是失敗則出ErrorCLTOT
        maxCnt = cltotService.findAllMaxCnt();
        modifyRecordUtil.addRecord(
                "Insert size = "
                        + insertList.size()
                        + " ,success = "
                        + successCount
                        + " ,error = "
                        + errorCount);
    }

    private String checkColumns(CltotBus cltotBus) {
        boolean isError = false;
        String errorMsg = "不寫入,欄位檢核不通過 ";
        // CODE 代收類別 CHAR 6
        if (!Objects.isNull(cltotBus.getCode()) && cltotBus.getCode().length() > 6) {
            errorMsg += ", CODE 代收類別 CHAR 6 ,值為(" + cltotBus.getCode() + ") ";
            isError = true;
        }
        // CURCD 代收幣別 DECIMAL 2
        if (cltotBus.getCurcd() > 99) {
            errorMsg += ", CURCD 代收幣別 DECIMAL 2 ,值為(" + cltotBus.getCurcd() + ") ";
            isError = true;
        }
        // RCVAMT 代收累計金額 DECIMAL 17 2
        if (cltotBus.getRcvamt().compareTo(new BigDecimal("999999999999999.99")) > 0) {
            errorMsg += ", RCVAMT 代收累計金額 DECIMAL 17 2 ,值為(" + cltotBus.getRcvamt() + ") ";
            isError = true;
        }
        // PAYAMT 代付累計金額 DECIMAL 17 2
        if (cltotBus.getPayamt().compareTo(new BigDecimal("999999999999999.99")) > 0) {
            errorMsg += ", PAYAMT 代付累計金額 DECIMAL 17 2 ,值為(" + cltotBus.getPayamt() + ") ";
            isError = true;
        }
        // TOTCNT 累計筆數 DECIMAL 8
        if (cltotBus.getTotcnt() > 99999999) {
            errorMsg += ", TOTCNT 累計筆數 DECIMAL 8 ,值為(" + cltotBus.getTotcnt() + ") ";
            isError = true;
        }
        // PAMT 本日帳代付總額 DECIMAL 17 2
        if (cltotBus.getPamt().compareTo(new BigDecimal("999999999999999.99")) > 0) {
            errorMsg += ", PAMT 本日帳代付總額 DECIMAL 17 2 ,值為(" + cltotBus.getPamt() + ") ";
            isError = true;
        }
        // NPAMT 次日帳代付總額 DECIMAL 17 2
        if (cltotBus.getNpamt().compareTo(new BigDecimal("999999999999999.99")) > 0) {
            errorMsg += ", NPAMT 次日帳代付總額 DECIMAL 17 2 ,值為(" + cltotBus.getNpamt() + ") ";
            isError = true;
        }
        // LCADAY 上次異動交易日 DECIMAL 8
        if (cltotBus.getLcaday() > 99999999) {
            errorMsg += ", LCADAY 上次異動交易日 DECIMAL 8 ,值為(" + cltotBus.getLcaday() + ") ";
            isError = true;
        }
        // LTXDAY 上次異動營業日 DECIMAL 8
        if (cltotBus.getLtxday() > 99999999) {
            errorMsg += ", LTXDAY 上次異動營業日 DECIMAL 8 ,值為(" + cltotBus.getLtxday() + ") ";
            isError = true;
        }
        return isError ? errorMsg : "";
    }

    private void doUpdateCltot(
            List<Map<String, Map<String, String>>> updateList, TransactionCase batchTransaction) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "doUpdateCltot");
        int errorCount = 0;
        int successCount = 0;
        int updateBatchCount = 0;
        List<CltotBus> updateBatchList = new ArrayList<>();
        for (Map<String, Map<String, String>> um : updateList) {
            Map<String, String> updateKey = um.get("UPDATE_KEY");
            Map<String, String> m = um.get("UPDATE_VALUE");
            // PK
            CltotId cltotId = getCltotId(updateKey);
            CltotBus cltotBus = cltotService.findById(cltotId);
            if (Objects.isNull(cltotBus)) {
                modifyRecordUtil.addRecord("Update Skip, cltotBus 不存在,cltotId=" + cltotId);
                errorCount++;
                continue;
            }
            if (m.containsKey("RCVAMT")) {
                cltotBus.setRcvamt(diffParse.toBigDecimal(m.get("RCVAMT")));
            }
            if (m.containsKey("PAYAMT")) {
                cltotBus.setPayamt(diffParse.toBigDecimal(m.get("PAYAMT")));
            }
            if (m.containsKey("TOTCNT")) {
                cltotBus.setTotcnt(diffParse.toInt(m.get("TOTCNT")));
            }

            String checkResult = checkColumns(cltotBus);
            if (!checkResult.isBlank()) {
                modifyRecordUtil.addRecord(checkResult + "," + m);
                continue;
            }
            updateBatchList.add(cltotBus);
            updateBatchCount++;
            if (updateBatchCount >= MODIFY_BATCH_SIZE) {
                try {
                    cltotService.updateAllSilent(updateBatchList);
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
                    errorDataCom.writeTempErrorData("CLTOT", updateBatchList); // 紀錄失敗資料結束後一筆一筆寫入
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
                cltotService.updateAllSilent(updateBatchList);
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
                errorDataCom.writeTempErrorData("CLTOT", updateBatchList); // 紀錄失敗資料結束後一筆一筆寫入
                errorCount++;
                updateBatchCount = 0;
                batchTransaction.rollBack();
            }
            successCount += updateBatchCount;
            startTime = new Date();
            updateBatchList.clear();
        }
        errorDataCom.readTmpErrorFile(2, "CLTOT", batchTransaction); // 一筆一筆寫入失敗資料,若還是失敗則出ErrorCLTOT
        modifyRecordUtil.addRecord(
                "Update size = "
                        + updateList.size()
                        + " ,success = "
                        + successCount
                        + " ,error = "
                        + errorCount);
    }

    private void doDeleteCltot(
            List<Map<String, String>> deleteList, TransactionCase batchTransaction) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "doDeleteCltot");
        int errorCount = 0;
        int successCount = 0;
        int deleteBatchCount = 0;
        List<CltotBus> deleteBatchList = new ArrayList<>();
        for (Map<String, String> m : deleteList) {
            // PK
            CltotId cltotId = getCltotId(m);
            CltotBus cltotBus = cltotService.findById(cltotId);
            if (Objects.isNull(cltotBus)) {
                modifyRecordUtil.addRecord("Delete Skip, cltotBus 不存在,cltotId=" + cltotId);
                errorCount++;
                continue;
            }
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "cltotBus={}", cltotBus);
            deleteBatchList.add(cltotBus);
            deleteBatchCount++;
            if (deleteBatchCount >= MODIFY_BATCH_SIZE) {
                try {
                    cltotService.deleteAllSilent(deleteBatchList);
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
                    errorDataCom.writeTempErrorData("CLTOT", deleteBatchList); // 紀錄失敗資料結束後一筆一筆寫入
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
                cltotService.deleteAllSilent(deleteBatchList);
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
                errorDataCom.writeTempErrorData("CLTOT", deleteBatchList); // 紀錄失敗資料結束後一筆一筆寫入
                errorCount++;
                deleteBatchCount = 0;
                batchTransaction.rollBack();
            }
            successCount += deleteBatchCount;
            startTime = new Date();
            deleteBatchList.clear();
        }
        errorDataCom.readTmpErrorFile(4, "CLTOT", batchTransaction); // 一筆一筆寫入失敗資料,若還是失敗則出ErrorCLTOT
        modifyRecordUtil.addRecord(
                "Delete size = "
                        + deleteList.size()
                        + " ,success = "
                        + successCount
                        + " ,error = "
                        + errorCount);
    }

    private CltotId getCltotId(Map<String, String> m) {
        String code = m.get("CODE");
        int curcd = diffParse.toInt(m.get("CURCD"));
        return new CltotId(code, curcd);
    }
}
