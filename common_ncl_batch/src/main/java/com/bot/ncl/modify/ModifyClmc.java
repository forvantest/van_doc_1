/* (C) 2024 */
package com.bot.ncl.modify;

import com.bot.ncl.diff.ErrorDataCom;
import com.bot.ncl.dto.entities.ClmcBus;
import com.bot.ncl.jpa.svc.ClmcService;
import com.bot.ncl.util.DiffParseUtil;
import com.bot.ncl.util.ModifyRecordUtil;
import com.bot.ncl.util.batch.BatchUtil;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
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
public class ModifyClmc {

    @Autowired private DiffParseUtil diffParse;

    @Autowired private ModifyRecordUtil modifyRecordUtil;

    @Autowired private BatchUtil batchUtil;

    @Autowired private ClmcService clmcService;
    @Autowired private ErrorDataCom errorDataCom;

    @Getter private Date startTime;

    @Getter private List<String> insertSqlErrorMsg;

    @Getter private Long maxCnt;

    private static final int MODIFY_BATCH_SIZE = 1000;

    private ModifyClmc() {
        super();
        // YOU SHOULD USE @Autowired ,NOT new ModifyClmc()
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
        modifyRecordUtil.initialize("CLMC");

        doInsertClmc(insertList, batchTransaction);
        doUpdateClmc(updateList, batchTransaction);
        doDeleteClmc(deleteList, batchTransaction);

        modifyRecordUtil.output();

        return result;
    }

    private void doInsertClmc(
            List<Map<String, String>> insertList, TransactionCase batchTransaction) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "doInsertClmc");
        int errorCount = 0;
        int successCount = 0;
        int insertBatchCount = 0;
        List<ClmcBus> insertBatchList = new ArrayList<>();
        for (Map<String, String> m : insertList) {
            ClmcBus clmcBus = new ClmcBus();

            String putname = m.get("PUTNAME").replaceAll("[\\x00]", "");
            if (Objects.isNull(putname) || putname.isBlank()) {
                String errorMsg = "不寫入,媒體檔名(PUTNAME)為空白," + m;
                modifyRecordUtil.addRecord(errorMsg);
                insertSqlErrorMsg.add(errorMsg);
                continue;
            }
            clmcBus.setPutname(putname);
            clmcBus.setPutsend(diffParse.nvl(m.get("PUTSEND"), ""));
            clmcBus.setPutform(diffParse.nvl(m.get("PUTFORM"), ""));
            clmcBus.setPuttype(diffParse.toInt(m.get("PUTTYPE")));
            clmcBus.setPutaddr(diffParse.nvl(m.get("PUTADDR"), ""));
            clmcBus.setCyck1(diffParse.toInt(m.get("CYCK1")));
            clmcBus.setCycno1(diffParse.toInt(m.get("CYCNO1")));
            clmcBus.setCyck2(diffParse.toInt(m.get("CYCK2")));
            clmcBus.setCycno2(diffParse.toInt(m.get("CYCNO2")));
            clmcBus.setPutdt(diffParse.toInt(m.get("PUTDT")));
            clmcBus.setMsg2(diffParse.toInt(m.get("MSG2")));
            clmcBus.setTputdt(diffParse.toInt(m.get("TPUTDT")));

            // 特殊處理
            clmcBus.setPutEncode(diffParse.nvl(m.get("PUTENCODE"), ""));
            clmcBus.setPutCompress(diffParse.nvl(m.get("PUTCOMPRESS"), ""));
            clmcBus.setOputtime(diffParse.nvl(m.get("OPUTTIME"), ""));
            clmcBus.setPutdtfg(diffParse.toInt(m.get("PUTDTFG")));
            clmcBus.setUsecnt(diffParse.toInt(m.get("USECNT")));

            String checkResult = checkColumns(clmcBus);
            if (!checkResult.isBlank()) {
                modifyRecordUtil.addRecord(checkResult + "," + m);
                insertSqlErrorMsg.add(checkResult + "," + m);
                continue;
            }

            insertBatchList.add(clmcBus);
            insertBatchCount++;
            if (insertBatchCount >= MODIFY_BATCH_SIZE) {
                try {
                    clmcService.insertAll(insertBatchList);
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
                    errorDataCom.writeTempErrorData("CLMC", insertBatchList); // 紀錄失敗資料結束後一筆一筆寫入
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
                clmcService.insertAll(insertBatchList);
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
                errorDataCom.writeTempErrorData("CLMC", insertBatchList); // 紀錄失敗資料結束後一筆一筆寫入
                errorCount++;
                insertBatchCount = 0;
                batchTransaction.rollBack();
            }
            successCount += insertBatchCount;
            startTime = new Date();
            insertBatchList.clear();
        }
        errorDataCom.readTmpErrorFile(1, "CLMC", batchTransaction); // 一筆一筆寫入失敗資料,若還是失敗則出ErrorCLMC
        maxCnt = clmcService.findAllMaxCnt();
        modifyRecordUtil.addRecord(
                "Insert size = "
                        + insertList.size()
                        + " ,success = "
                        + successCount
                        + " ,error = "
                        + errorCount);
    }

    private String checkColumns(ClmcBus clmcBus) {
        boolean isError = false;
        String errorMsg = "不寫入,欄位檢核不通過 ";
        // PUTNAME 媒體檔名 CHAR 6
        if (!Objects.isNull(clmcBus.getPutname()) && clmcBus.getPutname().length() > 6) {
            errorMsg += ", PUTNAME 媒體檔名 CHAR 6 ,值為(" + clmcBus.getPutname() + ") ";
            isError = true;
        }
        // PUTSEND 媒體檔傳送方式 CHAR 1
        if (!Objects.isNull(clmcBus.getPutsend()) && clmcBus.getPutsend().length() > 1) {
            errorMsg += ", PUTSEND 媒體檔傳送方式 CHAR 1 ,值為(" + clmcBus.getPutsend() + ") ";
            isError = true;
        }
        // PUTFORM 媒體檔格式 CHAR 1
        if (!Objects.isNull(clmcBus.getPutform()) && clmcBus.getPutform().length() > 1) {
            errorMsg += ", PUTFORM 媒體檔格式 CHAR 1 ,值為(" + clmcBus.getPutform() + ") ";
            isError = true;
        }
        // PUTTYPE 媒體種類 DECIMAL 2
        if (clmcBus.getPuttype() > 99) {
            errorMsg += ", PUTTYPE 媒體種類 DECIMAL 2 ,值為(" + clmcBus.getPuttype() + ") ";
            isError = true;
        }
        // PUTADDR 媒體給付住址 VARCHAR2 100
        if (!Objects.isNull(clmcBus.getPutaddr()) && clmcBus.getPutaddr().length() > 100) {
            errorMsg += ", PUTADDR 媒體給付住址 VARCHAR2 100 ,值為(" + clmcBus.getPutaddr() + ") ";
            isError = true;
        }
        // PUT_ENCODE 媒體檔編碼 CHAR 1
        if (!Objects.isNull(clmcBus.getPutEncode()) && clmcBus.getPutEncode().length() > 1) {
            errorMsg += ", PUT_ENCODE 媒體檔編碼 CHAR 1 ,值為(" + clmcBus.getPutEncode() + ") ";
            isError = true;
        }
        // PUT_COMPRESS 媒體檔壓縮記號 CHAR 1
        if (!Objects.isNull(clmcBus.getPutCompress()) && clmcBus.getPutCompress().length() > 1) {
            errorMsg += ", PUT_COMPRESS 媒體檔壓縮記號 CHAR 1 ,值為(" + clmcBus.getPutCompress() + ") ";
            isError = true;
        }
        // OPUTTIME 外部代收資料回應方式 CHAR 1
        if (!Objects.isNull(clmcBus.getOputtime()) && clmcBus.getOputtime().length() > 1) {
            errorMsg += ", OPUTTIME 外部代收資料回應方式 CHAR 1 ,值為(" + clmcBus.getOputtime() + ") ";
            isError = true;
        }
        // CYCK1 銷帳媒體產生週期 DECIMAL 1
        if (clmcBus.getCyck1() > 9) {
            errorMsg += ", CYCK1 銷帳媒體產生週期 DECIMAL 1 ,值為(" + clmcBus.getCyck1() + ") ";
            isError = true;
        }
        // CYCNO1 銷帳媒體產生週期日 DECIMAL 2
        if (clmcBus.getCycno1() > 99) {
            errorMsg += ", CYCNO1 銷帳媒體產生週期日 DECIMAL 2 ,值為(" + clmcBus.getCycno1() + ") ";
            isError = true;
        }
        // CYCK2 對帳媒體產生週期 DECIMAL 1
        if (clmcBus.getCyck2() > 9) {
            errorMsg += ", CYCK2 對帳媒體產生週期 DECIMAL 1 ,值為(" + clmcBus.getCyck2() + ") ";
            isError = true;
        }
        // CYCNO2 對帳媒體產生週期日 DECIMAL 2
        if (clmcBus.getCycno2() > 99) {
            errorMsg += ", CYCNO2 對帳媒體產生週期日 DECIMAL 2 ,值為(" + clmcBus.getCycno2() + ") ";
            isError = true;
        }
        // PUTDTFG 挑檔日類別 DECIMAL 1
        if (clmcBus.getPutdtfg() > 9) {
            errorMsg += ", PUTDTFG 挑檔日類別 DECIMAL 1 ,值為(" + clmcBus.getPutdtfg() + ") ";
            isError = true;
        }
        // PUTDT 指定挑檔日 DECIMAL 8
        if (clmcBus.getPutdt() > 99999999) {
            errorMsg += ", PUTDT 指定挑檔日 DECIMAL 8 ,值為(" + clmcBus.getPutdt() + ") ";
            isError = true;
        }
        // MSG2 傳送空檔記號 DECIMAL 1
        if (clmcBus.getMsg2() > 9) {
            errorMsg += ", MSG2 傳送空檔記號 DECIMAL 1 ,值為(" + clmcBus.getMsg2() + ") ";
            isError = true;
        }
        // TPUTDT 臨時挑檔日 DECIMAL 8
        if (clmcBus.getTputdt() > 99999999) {
            errorMsg += ", TPUTDT 臨時挑檔日 DECIMAL 8 ,值為(" + clmcBus.getTputdt() + ") ";
            isError = true;
        }
        // USECNT 使用收付類別數 DECIMAL 4
        if (clmcBus.getUsecnt() > 9999) {
            errorMsg += ", USECNT 使用收付類別數 DECIMAL 4 ,值為(" + clmcBus.getUsecnt() + ") ";
            isError = true;
        }
        return isError ? errorMsg : "";
    }

    private void doUpdateClmc(
            List<Map<String, Map<String, String>>> updateList, TransactionCase batchTransaction) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "doUpdateClmc");
        int errorCount = 0;
        int successCount = 0;
        int updateBatchCount = 0;
        List<ClmcBus> updateBatchList = new ArrayList<>();
        for (Map<String, Map<String, String>> um : updateList) {
            Map<String, String> updateKey = um.get("UPDATE_KEY");
            Map<String, String> m = um.get("UPDATE_VALUE");
            // PK
            String putname = updateKey.get("PUTNAME");
            ClmcBus clmcBus = clmcService.findById(putname);
            if (Objects.isNull(clmcBus)) {
                modifyRecordUtil.addRecord("Update Skip, clmcBus 不存在,putname=" + putname);
                errorCount++;
                continue;
            }
            if (m.containsKey("PUTSEND")) {
                clmcBus.setPutsend(m.get("PUTSEND"));
            }
            if (m.containsKey("PUTFORM")) {
                clmcBus.setPutform(m.get("PUTFORM"));
            }
            if (m.containsKey("PUTTYPE")) {
                clmcBus.setPuttype(diffParse.toInt(m.get("PUTTYPE")));
            }
            if (m.containsKey("PUTADDR")) {
                clmcBus.setPutaddr(m.get("PUTADDR"));
            }
            if (m.containsKey("CYCK1")) {
                clmcBus.setCyck1(diffParse.toInt(m.get("CYCK1")));
            }
            if (m.containsKey("CYCNO1")) {
                clmcBus.setCycno1(diffParse.toInt(m.get("CYCNO1")));
            }
            if (m.containsKey("CYCK2")) {
                clmcBus.setCyck2(diffParse.toInt(m.get("CYCK2")));
            }
            if (m.containsKey("CYCNO2")) {
                clmcBus.setCycno2(diffParse.toInt(m.get("CYCNO2")));
            }
            if (m.containsKey("PUTDT")) {
                clmcBus.setPutdt(diffParse.toInt(m.get("PUTDT")));
            }
            if (m.containsKey("MSG2")) {
                clmcBus.setMsg2(diffParse.toInt(m.get("MSG2")));
            }
            if (m.containsKey("TPUTDT")) {
                clmcBus.setTputdt(diffParse.toInt(m.get("TPUTDT")));
            }

            // 特殊處理
            clmcBus.setPutEncode(m.get("PUTENCODE"));
            clmcBus.setPutCompress(m.get("PUTCOMPRESS"));
            clmcBus.setOputtime(m.get("OPUTTIME"));
            clmcBus.setPutdtfg(diffParse.toInt(m.get("PUTDTFG")));
            clmcBus.setUsecnt(diffParse.toInt(m.get("USECNT")));

            String checkResult = checkColumns(clmcBus);
            if (!checkResult.isBlank()) {
                modifyRecordUtil.addRecord(checkResult + "," + m);
                continue;
            }
            updateBatchList.add(clmcBus);
            updateBatchCount++;
            if (updateBatchCount >= MODIFY_BATCH_SIZE) {
                try {
                    clmcService.updateAllSilent(updateBatchList);
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
                    errorDataCom.writeTempErrorData("CLMC", updateBatchList); // 紀錄失敗資料結束後一筆一筆寫入
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
                clmcService.updateAllSilent(updateBatchList);
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
                errorDataCom.writeTempErrorData("CLMC", updateBatchList); // 紀錄失敗資料結束後一筆一筆寫入
                errorCount++;
                updateBatchCount = 0;
                batchTransaction.rollBack();
            }
            successCount += updateBatchCount;
            startTime = new Date();
            updateBatchList.clear();
        }
        errorDataCom.readTmpErrorFile(2, "CLMC", batchTransaction); // 一筆一筆寫入失敗資料,若還是失敗則出ErrorCLMC
        modifyRecordUtil.addRecord(
                "Update size = "
                        + updateList.size()
                        + " ,success = "
                        + successCount
                        + " ,error = "
                        + errorCount);
    }

    private void doDeleteClmc(
            List<Map<String, String>> deleteList, TransactionCase batchTransaction) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "doDeleteClmc");
        int errorCount = 0;
        int successCount = 0;
        int deleteBatchCount = 0;
        List<ClmcBus> deleteBatchList = new ArrayList<>();
        for (Map<String, String> m : deleteList) {
            // PK
            String putname = m.get("PUTNAME");
            ClmcBus clmcBus = clmcService.findById(putname);
            if (Objects.isNull(clmcBus)) {
                modifyRecordUtil.addRecord("Delete Skip, clmcBus 不存在,putname=" + putname);
                errorCount++;
                continue;
            }
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "clmcBus={}", clmcBus);
            deleteBatchList.add(clmcBus);
            deleteBatchCount++;
            if (deleteBatchCount >= MODIFY_BATCH_SIZE) {
                try {
                    clmcService.deleteAllSilent(deleteBatchList);
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
                    errorDataCom.writeTempErrorData("CLMC", deleteBatchList); // 紀錄失敗資料結束後一筆一筆寫入
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
                clmcService.deleteAllSilent(deleteBatchList);
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
                errorDataCom.writeTempErrorData("CLMC", deleteBatchList); // 紀錄失敗資料結束後一筆一筆寫入
                errorCount++;
                deleteBatchCount = 0;
                batchTransaction.rollBack();
            }
            successCount += deleteBatchCount;
            startTime = new Date();
            deleteBatchList.clear();
        }
        errorDataCom.readTmpErrorFile(4, "CLMC", batchTransaction); // 一筆一筆寫入失敗資料,若還是失敗則出ErrorCLMC
        modifyRecordUtil.addRecord(
                "Delete size = "
                        + deleteList.size()
                        + " ,success = "
                        + successCount
                        + " ,error = "
                        + errorCount);
    }
}
