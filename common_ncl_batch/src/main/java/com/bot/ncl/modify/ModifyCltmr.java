/* (C) 2024 */
package com.bot.ncl.modify;

import com.bot.ncl.diff.ErrorDataCom;
import com.bot.ncl.dto.entities.CltmrBus;
import com.bot.ncl.jpa.svc.CltmrService;
import com.bot.ncl.util.DiffParseUtil;
import com.bot.ncl.util.ModifyRecordUtil;
import com.bot.ncl.util.batch.BatchUtil;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.jpa.transaction.TransactionCase;
import java.nio.charset.StandardCharsets;
import java.util.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Scope("prototype")
public class ModifyCltmr {

    @Autowired private DiffParseUtil diffParse;

    @Autowired private ModifyRecordUtil modifyRecordUtil;

    @Autowired private BatchUtil batchUtil;

    @Autowired private CltmrService cltmrService;
    @Autowired private ErrorDataCom errorDataCom;

    @Getter private Date startTime;

    @Getter private List<String> insertSqlErrorMsg;

    @Getter private Long maxCnt;

    private static final int MODIFY_BATCH_SIZE = 100;

    private ModifyCltmr() {
        super();
        // YOU SHOULD USE @Autowired ,NOT new ModifyCltmr()
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
        modifyRecordUtil.initialize("CLTMR");

        doInsertCltmr(insertList, batchTransaction);
        doUpdateCltmr(updateList, batchTransaction);
        doDeleteCltmr(deleteList, batchTransaction);

        modifyRecordUtil.output();

        return result;
    }

    private void doInsertCltmr(
            List<Map<String, String>> insertList, TransactionCase batchTransaction) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "doInsertCltmr");
        int errorCount = 0;
        int successCount = 0;
        int insertBatchCount = 0;
        List<CltmrBus> insertBatchList = new ArrayList<>();
        for (Map<String, String> m : insertList) {
            CltmrBus cltmrBus = new CltmrBus();

            String code = m.get("CODE");
            if (Objects.isNull(code) || code.isBlank()) {
                String errorMsg = "不寫入,收付類別為空白," + m;
                modifyRecordUtil.addRecord(errorMsg);
                insertSqlErrorMsg.add(errorMsg);
                continue;
            }
            cltmrBus.setCode(code);
            cltmrBus.setAtmcode(diffParse.toInt(m.get("ATMCODE")));
            cltmrBus.setEntpno(diffParse.nvl(m.get("ENTPNO"), ""));
            cltmrBus.setHentpno(diffParse.toInt(m.get("HENTPNO")));
            cltmrBus.setScname(diffParse.nvl(m.get("SCNAME"), ""));
            cltmrBus.setCdata(diffParse.toInt(m.get("CDATA")));
            cltmrBus.setAppdt(diffParse.toInt(m.get("APPDT")));
            cltmrBus.setUpddt(diffParse.toInt(m.get("UPDDT")));
            cltmrBus.setLputdt(diffParse.toInt(m.get("LPUTDT")));
            cltmrBus.setLlputdt(diffParse.toInt(m.get("LLPUTDT")));
            cltmrBus.setUlputdt(diffParse.toInt(m.get("ULPUTDT")));
            cltmrBus.setUllputdt(diffParse.toInt(m.get("ULLPUTDT")));
            cltmrBus.setPrtype(diffParse.nvl(m.get("PRTYPE"), ""));
            cltmrBus.setClsacno(diffParse.nvl(m.get("CLSACNO"), ""));
            cltmrBus.setClssbno(diffParse.nvl(m.get("CLSSBNO"), ""));
            cltmrBus.setClsdtlno(diffParse.nvl(m.get("CLSDTLNO"), ""));
            cltmrBus.setEbtype(diffParse.nvl(m.get("EBTYPE"), ""));
            cltmrBus.setPwtype(diffParse.nvl(m.get("PWTYPE"), ""));
            cltmrBus.setFeename(diffParse.nvl(m.get("FEENAME"), ""));
            cltmrBus.setPutname(diffParse.nvl(m.get("PUTNAME"), ""));

            String checkResult = checkColumns(cltmrBus);
            if (!checkResult.isBlank()) {
                modifyRecordUtil.addRecord(checkResult + "," + m);
                insertSqlErrorMsg.add(checkResult + "," + m);
                continue;
            }

            insertBatchList.add(cltmrBus);
            insertBatchCount++;
            if (insertBatchCount >= MODIFY_BATCH_SIZE) {
                try {
                    cltmrService.insertAll(insertBatchList);
                    batchTransaction.commit();
                } catch (Exception e) {
                    for (CltmrBus errorCltmrBus : insertBatchList) {
                        String errorMsg =
                                "Insert Failed, unexpected db error,error="
                                        + e.getMessage()
                                        + ","
                                        + errorCltmrBus;
                        ApLogHelper.error(log, false, LogType.NORMAL.getCode(), errorMsg);
                        modifyRecordUtil.addRecord(errorMsg);
                        insertSqlErrorMsg.add(errorMsg);
                        errorCount++;
                    }
                    startTime = new Date();
                    errorDataCom.writeTempErrorData("CLTMR", insertBatchList); // 紀錄失敗資料結束後一筆一筆寫入
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
                cltmrService.insertAll(insertBatchList);
                batchTransaction.commit();
            } catch (Exception e) {
                for (CltmrBus errorCltmrBus : insertBatchList) {
                    String errorMsg =
                            "Insert Failed, unexpected db error,error="
                                    + e.getMessage()
                                    + ","
                                    + errorCltmrBus;
                    ApLogHelper.error(log, false, LogType.NORMAL.getCode(), errorMsg);
                    modifyRecordUtil.addRecord(errorMsg);
                    insertSqlErrorMsg.add(errorMsg);
                    errorCount++;
                }
                errorDataCom.writeTempErrorData("CLTMR", insertBatchList); // 紀錄失敗資料結束後一筆一筆寫入
                insertBatchCount = 0;
                batchTransaction.rollBack();
            }
            successCount += insertBatchCount;
            startTime = new Date();
            insertBatchList.clear();
        }
        errorDataCom.readTmpErrorFile(1, "CLTMR", batchTransaction); // 一筆一筆寫入失敗資料,若還是失敗則出ErrorCLTMR
        maxCnt = cltmrService.findAllMaxCnt();
        modifyRecordUtil.addRecord(
                "Insert size = "
                        + insertList.size()
                        + " ,success = "
                        + successCount
                        + " ,error = "
                        + errorCount);
    }

    private String checkColumns(CltmrBus cltmrBus) {
        boolean isError = false;
        String errorMsg = "不寫入,欄位檢核不通過 ";
        // CODE 代收類別 CHAR 6
        if (!Objects.isNull(cltmrBus.getCode()) && cltmrBus.getCode().length() > 6) {
            errorMsg += ", CODE 代收類別 CHAR 6 ,值為(" + cltmrBus.getCode() + ") ";
            isError = true;
        }
        // ATMCODE ATM繳款代碼 DECIMAL 3
        if (cltmrBus.getAtmcode() > 999) {
            errorMsg += ", ATMCODE ATM繳款代碼 DECIMAL 3 ,值為(" + cltmrBus.getAtmcode() + ") ";
            isError = true;
        }
        // ENTPNO 營利事業編號或個人身分証統一編號 VARCHAR2 10
        if (!Objects.isNull(cltmrBus.getEntpno()) && cltmrBus.getEntpno().length() > 10) {
            errorMsg += ", ENTPNO 營利事業編號或個人身分証統一編號 VARCHAR2 10 ,值為(" + cltmrBus.getEntpno() + ") ";
            isError = true;
        }
        if (!Objects.isNull(cltmrBus.getEntpno())
                && cltmrBus.getEntpno().getBytes(StandardCharsets.UTF_8).length > 20) {
            errorMsg +=
                    ", ENTPNO 營利事業編號或個人身分証統一編號 VARCHAR2 10 ,值為("
                            + cltmrBus.getEntpno()
                            + "),UTF8 長度為"
                            + cltmrBus.getEntpno().getBytes(StandardCharsets.UTF_8).length;
            isError = true;
        }
        // HENTPNO 營利事業編號(總公司) DECIMAL 8
        if (cltmrBus.getHentpno() > 99999999) {
            errorMsg += ", HENTPNO 營利事業編號(總公司) DECIMAL 8 ,值為(" + cltmrBus.getHentpno() + ") ";
            isError = true;
        }
        // SCNAME 簡稱 NVARCHAR2 10
        if (!Objects.isNull(cltmrBus.getScname()) && cltmrBus.getScname().length() > 10) {
            errorMsg += ", SCNAME 簡稱 NVARCHAR2 10 ,值為(" + cltmrBus.getScname() + ") ";
            isError = true;
        }
        if (!Objects.isNull(cltmrBus.getScname())
                && cltmrBus.getScname().getBytes(StandardCharsets.UTF_8).length > 20) {
            errorMsg +=
                    ", SCNAME 簡稱 NVARCHAR2 10 ,值為("
                            + cltmrBus.getScname()
                            + "),UTF8 長度為"
                            + cltmrBus.getScname().getBytes(StandardCharsets.UTF_8).length;
            isError = true;
        }
        // CDATA 檢查扣繳項目記號 DECIMAL 1
        if (cltmrBus.getCdata() > 9) {
            errorMsg += ", CDATA 檢查扣繳項目記號 DECIMAL 1 ,值為(" + cltmrBus.getCdata() + ") ";
            isError = true;
        }
        // APPDT 單位申請日期 DECIMAL 8
        if (cltmrBus.getAppdt() > 99999999) {
            errorMsg += ", APPDT 單位申請日期 DECIMAL 8 ,值為(" + cltmrBus.getAppdt() + ") ";
            isError = true;
        }
        // UPDDT 分行異動日 DECIMAL 8
        if (cltmrBus.getUpddt() > 99999999) {
            errorMsg += ", UPDDT 分行異動日 DECIMAL 8 ,值為(" + cltmrBus.getUpddt() + ") ";
            isError = true;
        }
        // LPUTDT 上次CYC1挑檔日 DECIMAL 8
        if (cltmrBus.getLputdt() > 99999999) {
            errorMsg += ", LPUTDT 上次CYC1挑檔日 DECIMAL 8 ,值為(" + cltmrBus.getLputdt() + ") ";
            isError = true;
        }
        // LLPUTDT 上上次CYC1挑檔日 DECIMAL 8
        if (cltmrBus.getLlputdt() > 99999999) {
            errorMsg += ", LLPUTDT 上上次CYC1挑檔日 DECIMAL 8 ,值為(" + cltmrBus.getLlputdt() + ") ";
            isError = true;
        }
        // ULPUTDT 上次CYC2挑檔日 DECIMAL 8
        if (cltmrBus.getUlputdt() > 99999999) {
            errorMsg += ", ULPUTDT 上次CYC2挑檔日 DECIMAL 8 ,值為(" + cltmrBus.getUlputdt() + ") ";
            isError = true;
        }
        // ULLPUTDT 上上次CYC2挑檔日 DECIMAL 8
        if (cltmrBus.getUllputdt() > 99999999) {
            errorMsg += ", ULLPUTDT 上上次CYC2挑檔日 DECIMAL 8 ,值為(" + cltmrBus.getUllputdt() + ") ";
            isError = true;
        }
        // PRTYPE 認證格式 CHAR 1
        if (!Objects.isNull(cltmrBus.getPrtype()) && cltmrBus.getPrtype().length() > 1) {
            errorMsg += ", PRTYPE 認證格式 CHAR 1 ,值為(" + cltmrBus.getPrtype() + ") ";
            isError = true;
        }
        if (!Objects.isNull(cltmrBus.getPrtype())
                && cltmrBus.getPrtype().getBytes(StandardCharsets.UTF_8).length > 20) {
            errorMsg +=
                    ", PRTYPE 認證格式 CHAR 1 ,值為("
                            + cltmrBus.getPrtype()
                            + "),UTF8 長度為"
                            + cltmrBus.getPrtype().getBytes(StandardCharsets.UTF_8).length;
            isError = true;
        }
        // CLSACNO 科目 VARCHAR2 6
        if (!Objects.isNull(cltmrBus.getClsacno()) && cltmrBus.getClsacno().length() > 6) {
            errorMsg += ", CLSACNO 科目 VARCHAR2 6 ,值為(" + cltmrBus.getClsacno() + ") ";
            isError = true;
        }
        // CLSSBNO 子目 VARCHAR2 4
        if (!Objects.isNull(cltmrBus.getClssbno()) && cltmrBus.getClssbno().length() > 4) {
            errorMsg += ", CLSSBNO 子目 VARCHAR2 4 ,值為(" + cltmrBus.getClssbno() + ") ";
            isError = true;
        }
        // CLSDTLNO 細目 VARCHAR2 4
        if (!Objects.isNull(cltmrBus.getClsdtlno()) && cltmrBus.getClsdtlno().length() > 4) {
            errorMsg += ", CLSDTLNO 細目 VARCHAR2 4 ,值為(" + cltmrBus.getClsdtlno() + ") ";
            isError = true;
        }
        // EBTYPE 全國繳費稅管道 VARCHAR2 10
        if (!Objects.isNull(cltmrBus.getEbtype()) && cltmrBus.getEbtype().length() > 10) {
            errorMsg += ", EBTYPE 全國繳費稅管道 VARCHAR2 10 ,值為(" + cltmrBus.getEbtype() + ") ";
            isError = true;
        }
        // PWTYPE 業務種類 VARCHAR2 3
        if (!Objects.isNull(cltmrBus.getPwtype()) && cltmrBus.getPwtype().length() > 3) {
            errorMsg += ", PWTYPE 業務種類 VARCHAR2 3 ,值為(" + cltmrBus.getPwtype() + ") ";
            isError = true;
        }
        // FEENAME 收付項目名稱 NVARCHAR2 42
        if (!Objects.isNull(cltmrBus.getFeename()) && cltmrBus.getFeename().length() > 42) {
            errorMsg += ", FEENAME 收付項目名稱 NVARCHAR2 42 ,值為(" + cltmrBus.getFeename() + ") ";
            isError = true;
        }
        if (!Objects.isNull(cltmrBus.getFeename())
                && cltmrBus.getFeename().getBytes(StandardCharsets.UTF_8).length > 84) {
            errorMsg +=
                    ", FEENAME 收付項目名稱 NVARCHAR2 42 ,值為("
                            + cltmrBus.getFeename()
                            + "),UTF8 長度為"
                            + cltmrBus.getFeename().getBytes(StandardCharsets.UTF_8).length;
            isError = true;
        }
        // PUTNAME 媒體檔名 VARCHAR2 6
        if (!Objects.isNull(cltmrBus.getPutname()) && cltmrBus.getPutname().length() > 6) {
            errorMsg += ", PUTNAME 媒體檔名 VARCHAR2 6 ,值為(" + cltmrBus.getPutname() + ") ";
            isError = true;
        }
        return isError ? errorMsg : "";
    }

    private void doUpdateCltmr(
            List<Map<String, Map<String, String>>> updateList, TransactionCase batchTransaction) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "doUpdateCltmr");
        int errorCount = 0;
        int successCount = 0;
        int updateBatchCount = 0;
        List<CltmrBus> updateBatchList = new ArrayList<>();
        for (Map<String, Map<String, String>> um : updateList) {
            Map<String, String> updateKey = um.get("UPDATE_KEY");
            Map<String, String> m = um.get("UPDATE_VALUE");
            // PK
            String code = updateKey.get("CODE");
            CltmrBus cltmrBus = cltmrService.findById(code);
            if (Objects.isNull(cltmrBus)) {
                modifyRecordUtil.addRecord("Update Skip, cltmrBus 不存在,code=" + code);
                errorCount++;
                continue;
            }
            if (m.containsKey("ATMCODE")) {
                cltmrBus.setAtmcode(diffParse.toInt(m.get("ATMCODE")));
            }
            if (m.containsKey("ENTPNO")) {
                cltmrBus.setEntpno(m.get("ENTPNO"));
            }
            if (m.containsKey("HENTPNO")) {
                cltmrBus.setHentpno(diffParse.toInt(m.get("HENTPNO")));
            }
            if (m.containsKey("SCNAME")) {
                cltmrBus.setScname(m.get("SCNAME"));
            }
            if (m.containsKey("CDATA")) {
                cltmrBus.setCdata(diffParse.toInt(m.get("CDATA")));
            }
            if (m.containsKey("APPDT")) {
                cltmrBus.setAppdt(diffParse.toInt(m.get("APPDT")));
            }
            if (m.containsKey("UPDDT")) {
                cltmrBus.setUpddt(diffParse.toInt(m.get("UPDDT")));
            }
            if (m.containsKey("LPUTDT")) {
                cltmrBus.setLputdt(diffParse.toInt(m.get("LPUTDT")));
            }
            if (m.containsKey("LLPUTDT")) {
                cltmrBus.setLlputdt(diffParse.toInt(m.get("LLPUTDT")));
            }
            if (m.containsKey("ULPUTDT")) {
                cltmrBus.setUlputdt(diffParse.toInt(m.get("ULPUTDT")));
            }
            if (m.containsKey("ULLPUTDT")) {
                cltmrBus.setUllputdt(diffParse.toInt(m.get("ULLPUTDT")));
            }
            if (m.containsKey("PRTYPE")) {
                cltmrBus.setPrtype(m.get("PRTYPE"));
            }
            if (m.containsKey("CLSACNO")) {
                cltmrBus.setClsacno(m.get("CLSACNO"));
            }
            if (m.containsKey("CLSSBNO")) {
                cltmrBus.setClssbno(m.get("CLSSBNO"));
            }
            if (m.containsKey("CLSDTLNO")) {
                cltmrBus.setClsdtlno(m.get("CLSDTLNO"));
            }
            if (m.containsKey("EBTYPE")) {
                cltmrBus.setEbtype(m.get("EBTYPE"));
            }
            if (m.containsKey("PWTYPE")) {
                cltmrBus.setPwtype(m.get("PWTYPE"));
            }
            if (m.containsKey("FEENAME")) {
                cltmrBus.setFeename(m.get("FEENAME"));
            }
            if (m.containsKey("PUTNAME")) {
                cltmrBus.setPutname(m.get("PUTNAME"));
            }

            String checkResult = checkColumns(cltmrBus);
            if (!checkResult.isBlank()) {
                modifyRecordUtil.addRecord(checkResult + "," + m);
                continue;
            }
            updateBatchList.add(cltmrBus);
            updateBatchCount++;
            if (updateBatchCount >= MODIFY_BATCH_SIZE) {
                try {
                    cltmrService.updateAllSilent(updateBatchList);
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
                    errorDataCom.writeTempErrorData("CLTMR", updateBatchList); // 紀錄失敗資料結束後一筆一筆寫入
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
                cltmrService.updateAllSilent(updateBatchList);
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
                errorDataCom.writeTempErrorData("CLTMR", updateBatchList); // 紀錄失敗資料結束後一筆一筆寫入
                errorCount++;
                updateBatchCount = 0;
                batchTransaction.rollBack();
            }
            successCount += updateBatchCount;
            startTime = new Date();
            updateBatchList.clear();
        }
        errorDataCom.readTmpErrorFile(2, "CLTMR", batchTransaction); // 一筆一筆寫入失敗資料,若還是失敗則出ErrorCLTMR
        modifyRecordUtil.addRecord(
                "Update size = "
                        + updateList.size()
                        + " ,success = "
                        + successCount
                        + " ,error = "
                        + errorCount);
    }

    private void doDeleteCltmr(
            List<Map<String, String>> deleteList, TransactionCase batchTransaction) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "doDeleteCltmr");
        int errorCount = 0;
        int successCount = 0;
        int deleteBatchCount = 0;
        List<CltmrBus> deleteBatchList = new ArrayList<>();
        for (Map<String, String> m : deleteList) {
            // PK
            String code = m.get("CODE");
            CltmrBus cltmrBus = cltmrService.findById(code);
            if (Objects.isNull(cltmrBus)) {
                modifyRecordUtil.addRecord("Delete Skip, cltmrBus 不存在,code=" + code);
                errorCount++;
                continue;
            }
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "cltmrBus={}", cltmrBus);
            deleteBatchList.add(cltmrBus);
            deleteBatchCount++;
            if (deleteBatchCount >= MODIFY_BATCH_SIZE) {
                try {
                    cltmrService.deleteAllSilent(deleteBatchList);
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
                    errorDataCom.writeTempErrorData("CLTMR", deleteBatchList); // 紀錄失敗資料結束後一筆一筆寫入
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
                cltmrService.deleteAllSilent(deleteBatchList);
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
                errorDataCom.writeTempErrorData("CLTMR", deleteBatchList); // 紀錄失敗資料結束後一筆一筆寫入
                errorCount++;
                deleteBatchCount = 0;
                batchTransaction.rollBack();
            }
            successCount += deleteBatchCount;
            startTime = new Date();
            deleteBatchList.clear();
        }
        errorDataCom.readTmpErrorFile(4, "CLTMR", batchTransaction); // 一筆一筆寫入失敗資料,若還是失敗則出ErrorCLTMR
        modifyRecordUtil.addRecord(
                "Delete size = "
                        + deleteList.size()
                        + " ,success = "
                        + successCount
                        + " ,error = "
                        + errorCount);
    }
}
