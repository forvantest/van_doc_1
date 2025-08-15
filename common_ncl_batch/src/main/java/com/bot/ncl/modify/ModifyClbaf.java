/* (C) 2024 */
package com.bot.ncl.modify;

import com.bot.ncl.diff.ErrorDataCom;
import com.bot.ncl.dto.entities.ClbafBus;
import com.bot.ncl.jpa.entities.impl.ClbafId;
import com.bot.ncl.jpa.svc.ClbafService;
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
public class ModifyClbaf {

    @Autowired private DiffParseUtil diffParse;

    @Autowired private ModifyRecordUtil modifyRecordUtil;

    @Autowired private BatchUtil batchUtil;

    @Autowired private ClbafService clbafService;
    @Autowired private ErrorDataCom errorDataCom;

    @Getter private Date startTime;

    @Getter private List<String> insertSqlErrorMsg;

    @Getter private Long maxCnt;

    private static final int MODIFY_BATCH_SIZE = 100;

    private ModifyClbaf() {
        super();
        // YOU SHOULD USE @Autowired ,NOT new ModifyClbaf()
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
        modifyRecordUtil.initialize("CLBAF");

        doInsertClbaf(insertList, batchTransaction);
        doUpdateClbaf(updateList, batchTransaction);
        doDeleteClbaf(deleteList, batchTransaction);

        modifyRecordUtil.output();

        return result;
    }

    private void doInsertClbaf(
            List<Map<String, String>> insertList, TransactionCase batchTransaction) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "doInsertClbaf");
        int errorCount = 0;
        int successCount = 0;
        int insertBatchCount = 0;
        List<ClbafBus> insertBatchList = new ArrayList<>();
        for (Map<String, String> m : insertList) {
            ClbafBus clbafBus = new ClbafBus();

            clbafBus.setCllbr(diffParse.toInt(m.get("CLLBR")));
            clbafBus.setEntdy(diffParse.toInt(m.get("ENTDY")));
            String code = m.get("CODE");
            if (Objects.isNull(code) || code.isBlank()) {
                String errorMsg = "不寫入,收付類別為空白," + m;
                modifyRecordUtil.addRecord(errorMsg);
                insertSqlErrorMsg.add(errorMsg);
                continue;
            }
            clbafBus.setCode(code);
            clbafBus.setPbrno(diffParse.toInt(m.get("PBRNO")));
            clbafBus.setCrdb(diffParse.toInt(m.get("CRDB")));
            String txtype = m.get("TXTYPE");
            if (Objects.isNull(txtype) || txtype.isBlank()) {
                String errorMsg = "不寫入,交易類型(TXTYPE)為空白," + m;
                modifyRecordUtil.addRecord(errorMsg);
                insertSqlErrorMsg.add(errorMsg);
                continue;
            }
            clbafBus.setTxtype(diffParse.nvl(txtype, " "));
            clbafBus.setCnt(diffParse.toInt(m.get("CNT")));
            clbafBus.setAmt(diffParse.toBigDecimal(m.get("AMT")));
            clbafBus.setCfee2(diffParse.toBigDecimal(m.get("CFEE2")));
            clbafBus.setKfee(diffParse.toBigDecimal(m.get("KFEE")));

            // 特殊處理
            clbafBus.setCurcd(diffParse.toInt(m.get("CURCD")));

            //            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "clbafBus={}",
            // clbafBus);
            // PK
            ClbafId clbafId = getClbafId(m);

            String checkResult = checkColumns(clbafBus);
            if (!checkResult.isBlank()) {
                modifyRecordUtil.addRecord(checkResult + "," + m);
                insertSqlErrorMsg.add(checkResult + "," + m);
                continue;
            }

            insertBatchList.add(clbafBus);
            insertBatchCount++;
            if (insertBatchCount >= MODIFY_BATCH_SIZE) {
                try {
                    clbafService.insertAll(insertBatchList);
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
                    errorDataCom.writeTempErrorData("CLBAF", insertBatchList); // 紀錄失敗資料結束後一筆一筆寫入
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
                clbafService.insertAll(insertBatchList);
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
                errorDataCom.writeTempErrorData("CLBAF", insertBatchList); // 紀錄失敗資料結束後一筆一筆寫入
                errorCount++;
                insertBatchCount = 0;
                batchTransaction.rollBack();
            }
            successCount += insertBatchCount;
            startTime = new Date();
            insertBatchList.clear();
        }
        errorDataCom.readTmpErrorFile(1, "CLBAF", batchTransaction); // 一筆一筆寫入失敗資料,若還是失敗則出ErrorCLBAF
        maxCnt = clbafService.findAllMaxCnt();
        modifyRecordUtil.addRecord(
                "Insert size = "
                        + insertList.size()
                        + " ,success = "
                        + successCount
                        + " ,error = "
                        + errorCount);
    }

    private String checkColumns(ClbafBus clbafBus) {
        boolean isError = false;
        String errorMsg = "不寫入,欄位檢核不通過 ";
        // CLLBR 代收行 DECIMAL 3
        if (clbafBus.getCllbr() > 999) {
            errorMsg += ", CLLBR 代收行 DECIMAL 3 ,值為(" + clbafBus.getCllbr() + ") ";
            isError = true;
        }
        // ENTDY 代收日 DECIMAL 8
        if (clbafBus.getEntdy() > 99999999) {
            errorMsg += ", ENTDY 代收日 DECIMAL 8 ,值為(" + clbafBus.getEntdy() + ") ";
            isError = true;
        }
        // CODE 代收類別 CHAR 6
        if (!Objects.isNull(clbafBus.getCode()) && clbafBus.getCode().length() > 6) {
            errorMsg += ", CODE 代收類別 CHAR 6 ,值為(" + clbafBus.getCode() + ") ";
            isError = true;
        }
        // PBRNO 主辦行 DECIMAL 3
        if (clbafBus.getPbrno() > 999) {
            errorMsg += ", PBRNO 主辦行 DECIMAL 3 ,值為(" + clbafBus.getPbrno() + ") ";
            isError = true;
        }
        // CRDB 收付記號 DECIMAL 1
        if (clbafBus.getCrdb() > 9) {
            errorMsg += ", CRDB 收付記號 DECIMAL 1 ,值為(" + clbafBus.getCrdb() + ") ";
            isError = true;
        }
        // TXTYPE 交易類型 VARCHAR2 2
        if (!Objects.isNull(clbafBus.getTxtype()) && clbafBus.getTxtype().length() > 2) {
            errorMsg += ", TXTYPE 交易類型 VARCHAR2 2 ,值為(" + clbafBus.getTxtype() + ") ";
            isError = true;
        }
        // CURCD 繳費幣別 DECIMAL 2
        if (clbafBus.getCurcd() > 99) {
            errorMsg += ", CURCD 繳費幣別 DECIMAL 2 ,值為(" + clbafBus.getCurcd() + ") ";
            isError = true;
        }
        // CNT 該業務在當日之累計代收筆數 DECIMAL 5
        if (clbafBus.getCnt() > 99999) {
            errorMsg += ", CNT 該業務在當日之累計代收筆數 DECIMAL 5 ,值為(" + clbafBus.getCnt() + ") ";
            isError = true;
        }
        // AMT 該業務在當日之累計代收金額 DECIMAL 15 2
        BigDecimal amtLimit = new BigDecimal("9999999999999.99");
        if (clbafBus.getAmt().compareTo(amtLimit) > 0) {
            errorMsg += ", AMT 該業務在當日之累計代收金額 DECIMAL 15 2 ,值為(" + clbafBus.getAmt() + ") ";
            isError = true;
        }
        // CFEE2 該業務在當日之累計代收手續費 DECIMAL 8 2
        BigDecimal cfee2Limit = new BigDecimal("999999.99");
        if (clbafBus.getCfee2().compareTo(cfee2Limit) > 0) {
            errorMsg += ", CFEE2 該業務在當日之累計代收手續費 DECIMAL 8 2 ,值為(" + clbafBus.getCfee2() + ") ";
            isError = true;
        }
        // KFEE 外部代收電子化手續費 DECIMAL 10 2
        BigDecimal kfeeLimit = new BigDecimal("99999999.99");
        if (clbafBus.getKfee().compareTo(kfeeLimit) > 0) {
            errorMsg += ", KFEE 外部代收電子化手續費 DECIMAL 10 2 ,值為(" + clbafBus.getKfee() + ") ";
            isError = true;
        }
        return isError ? errorMsg : "";
    }

    private void doUpdateClbaf(
            List<Map<String, Map<String, String>>> updateList, TransactionCase batchTransaction) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "doUpdateClbaf");
        int errorCount = 0;
        int successCount = 0;
        int updateBatchCount = 0;
        List<ClbafBus> updateBatchList = new ArrayList<>();
        for (Map<String, Map<String, String>> um : updateList) {
            Map<String, String> updateKey = um.get("UPDATE_KEY");
            Map<String, String> m = um.get("UPDATE_VALUE");
            // PK
            ClbafId clbafId = getClbafId(updateKey);
            ClbafBus clbafBus = clbafService.findById(clbafId);
            if (Objects.isNull(clbafBus)) {
                modifyRecordUtil.addRecord("Update Skip, clbafBus 不存在,clbafId=" + clbafId);
                errorCount++;
                continue;
            }
            if (m.containsKey("CNT")) {
                clbafBus.setCnt(diffParse.toInt(m.get("CNT")));
            }
            if (m.containsKey("AMT")) {
                clbafBus.setAmt(diffParse.toBigDecimal(m.get("AMT")));
            }
            if (m.containsKey("CFEE2")) {
                clbafBus.setCfee2(diffParse.toBigDecimal(m.get("CFEE2")));
            }
            if (m.containsKey("KFEE")) {
                clbafBus.setKfee(diffParse.toBigDecimal(m.get("KFEE")));
            }

            String checkResult = checkColumns(clbafBus);
            if (!checkResult.isBlank()) {
                modifyRecordUtil.addRecord(checkResult + "," + m);
                continue;
            }
            updateBatchList.add(clbafBus);
            updateBatchCount++;
            if (updateBatchCount >= MODIFY_BATCH_SIZE) {
                try {
                    clbafService.updateAllSilent(updateBatchList);
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
                    errorDataCom.writeTempErrorData("CLBAF", updateBatchList); // 紀錄失敗資料結束後一筆一筆寫入
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
                clbafService.updateAllSilent(updateBatchList);
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
                errorDataCom.writeTempErrorData("CLBAF", updateBatchList); // 紀錄失敗資料結束後一筆一筆寫入
                errorCount++;
                updateBatchCount = 0;
                batchTransaction.rollBack();
            }
            successCount += updateBatchCount;
            startTime = new Date();
            updateBatchList.clear();
        }
        errorDataCom.readTmpErrorFile(2, "CLBAF", batchTransaction); // 一筆一筆寫入失敗資料,若還是失敗則出ErrorCLBAF
        modifyRecordUtil.addRecord(
                "Update size = "
                        + updateList.size()
                        + " ,success = "
                        + successCount
                        + " ,error = "
                        + errorCount);
    }

    private void doDeleteClbaf(
            List<Map<String, String>> deleteList, TransactionCase batchTransaction) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "doDeleteClbaf");
        int errorCount = 0;
        int successCount = 0;
        int deleteBatchCount = 0;
        List<ClbafBus> deleteBatchList = new ArrayList<>();
        for (Map<String, String> m : deleteList) {
            // PK
            ClbafId clbafId = getClbafId(m);
            ClbafBus clbafBus = clbafService.findById(clbafId);
            if (Objects.isNull(clbafBus)) {
                modifyRecordUtil.addRecord("Delete Skip, clbafBus 不存在,clbafId=" + clbafId);
                errorCount++;
                continue;
            }
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "clbafBus={}", clbafBus);
            deleteBatchList.add(clbafBus);
            deleteBatchCount++;
            if (deleteBatchCount >= MODIFY_BATCH_SIZE) {
                try {
                    clbafService.deleteAllSilent(deleteBatchList);
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
                    errorDataCom.writeTempErrorData("CLBAF", deleteBatchList); // 紀錄失敗資料結束後一筆一筆寫入
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
                clbafService.deleteAllSilent(deleteBatchList);
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
                errorDataCom.writeTempErrorData("CLBAF", deleteBatchList); // 紀錄失敗資料結束後一筆一筆寫入
                errorCount++;
                deleteBatchCount = 0;
                batchTransaction.rollBack();
            }
            successCount += deleteBatchCount;
            startTime = new Date();
            deleteBatchList.clear();
        }
        errorDataCom.readTmpErrorFile(4, "CLBAF", batchTransaction); // 一筆一筆寫入失敗資料,若還是失敗則出ErrorCLBAF
        modifyRecordUtil.addRecord(
                "Delete size = "
                        + deleteList.size()
                        + " ,success = "
                        + successCount
                        + " ,error = "
                        + errorCount);
    }

    private ClbafId getClbafId(Map<String, String> m) {
        // CLLBR+ENTDY+CODE+PBRNO+CRDB+TXTYPE+CURCD
        int cllbr = diffParse.toInt(m.get("CLLBR"));
        int entdy = diffParse.toInt(m.get("ENTDY"));
        String code = m.get("CODE");
        int pbrno = diffParse.toInt(m.get("PBRNO"));
        int crdb = diffParse.toInt(m.get("CRDB"));
        String txtype = m.get("TXTYPE");
        int curcd = diffParse.toInt(m.get("CURCD"));
        return new ClbafId(cllbr, entdy, code, pbrno, crdb, txtype, curcd);
    }
}
