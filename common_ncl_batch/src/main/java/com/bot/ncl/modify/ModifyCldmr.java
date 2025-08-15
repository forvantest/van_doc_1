/* (C) 2024 */
package com.bot.ncl.modify;

import com.bot.ncl.diff.ErrorDataCom;
import com.bot.ncl.dto.entities.CldmrBus;
import com.bot.ncl.jpa.entities.impl.CldmrId;
import com.bot.ncl.jpa.svc.CldmrService;
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
public class ModifyCldmr {

    @Autowired private DiffParseUtil diffParse;

    @Autowired private ModifyRecordUtil modifyRecordUtil;

    @Autowired private BatchUtil batchUtil;

    @Autowired private CldmrService cldmrService;
    @Autowired private ErrorDataCom errorDataCom;

    @Getter private Date startTime;

    @Getter private List<String> insertSqlErrorMsg;

    @Getter private Long maxCnt;

    private static final int MODIFY_BATCH_SIZE = 100;

    private ModifyCldmr() {
        super();
        // YOU SHOULD USE @Autowired ,NOT new ModifyCldmr()
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
        modifyRecordUtil.initialize("CLDMR");

        doInsertCldmr(insertList, batchTransaction);
        doUpdateCldmr(updateList, batchTransaction);
        doDeleteCldmr(deleteList, batchTransaction);

        modifyRecordUtil.output();

        return result;
    }

    private void doInsertCldmr(
            List<Map<String, String>> insertList, TransactionCase batchTransaction) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "doInsertCldmr");
        int errorCount = 0;
        int successCount = 0;
        int insertBatchCount = 0;
        List<CldmrBus> insertBatchList = new ArrayList<>();
        for (Map<String, String> m : insertList) {
            CldmrBus cldmrBus = new CldmrBus();

            String code = m.get("CODE");
            if (Objects.isNull(code) || code.isBlank()) {
                String errorMsg = "不寫入,收付類別為空白," + m;
                modifyRecordUtil.addRecord(errorMsg);
                insertSqlErrorMsg.add(errorMsg);
                continue;
            }
            cldmrBus.setCode(code);
            cldmrBus.setRcptid(diffParse.nvl(m.get("RCPTID"), ""));
            cldmrBus.setPname(diffParse.nvl(m.get("PNAME"), ""));
            cldmrBus.setBal(diffParse.toBigDecimal(m.get("BAL")));
            cldmrBus.setLdate(diffParse.toInt(m.get("LDATE")));
            cldmrBus.setLtime(diffParse.toInt(m.get("LTIME")));
            cldmrBus.setKdate(diffParse.toInt(m.get("KDATE")));
            cldmrBus.setLflg(diffParse.toInt(m.get("LFLG")));
            cldmrBus.setCdate(diffParse.toInt(m.get("CDATE")));
            cldmrBus.setUdate(diffParse.toInt(m.get("UDATE")));
            cldmrBus.setUtime(diffParse.toInt(m.get("UTIME")));
            cldmrBus.setKinbr(diffParse.toInt(m.get("KINBR")));
            cldmrBus.setTlrno(diffParse.nvl(m.get("TLRNO"), ""));
            cldmrBus.setEmpno(diffParse.nvl(m.get("EMPNO"), ""));

            // 特殊處理
            cldmrBus.setId(diffParse.nvl(m.get("ID"), ""));
            cldmrBus.setCurcd(diffParse.toInt(m.get("CURCD")));
            cldmrBus.setEmpno(diffParse.nvl(m.get("EMPNO"), ""));

            //            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "cldmrBus={}",
            // cldmrBus);
            // PK
            CldmrId cldmrId = getCldmrId(m);
            //            CldmrBus findCldmrBus = cldmrService.findById(cldmrId);
            //            if (!Objects.isNull(findCldmrBus)) {
            //                modifyRecordUtil.addRecord("Insert Skip, cldmrBus 已存在,cldmrId=" +
            // cldmrId);
            //                errorCount++;
            //                continue;
            //            }

            String checkResult = checkColumns(cldmrBus);
            if (!checkResult.isBlank()) {
                modifyRecordUtil.addRecord(checkResult + "," + m);
                insertSqlErrorMsg.add(checkResult + "," + m);
                continue;
            }

            insertBatchList.add(cldmrBus);
            insertBatchCount++;
            if (insertBatchCount >= MODIFY_BATCH_SIZE) {
                try {
                    cldmrService.insertAll(insertBatchList);
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
                    errorDataCom.writeTempErrorData("CLDMR", insertBatchList); // 紀錄失敗資料結束後一筆一筆寫入
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
                cldmrService.insertAll(insertBatchList);
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
                errorDataCom.writeTempErrorData("CLDMR", insertBatchList); // 紀錄失敗資料結束後一筆一筆寫入
                errorCount++;
                insertBatchCount = 0;
                batchTransaction.rollBack();
            }
            successCount += insertBatchCount;
            startTime = new Date();
            insertBatchList.clear();
        }
        errorDataCom.readTmpErrorFile(1, "CLDMR", batchTransaction); // 一筆一筆寫入失敗資料,若還是失敗則出ErrorCLDMR
        maxCnt = cldmrService.findAllMaxCnt();
        modifyRecordUtil.addRecord(
                "Insert size = "
                        + insertList.size()
                        + " ,success = "
                        + successCount
                        + " ,error = "
                        + errorCount);
    }

    private String checkColumns(CldmrBus cldmrBus) {
        boolean isError = false;
        String errorMsg = "不寫入,欄位檢核不通過 ";
        // CODE 代收類別 CHAR 6
        if (!Objects.isNull(cldmrBus.getCode()) && cldmrBus.getCode().length() > 6) {
            errorMsg += ", CODE 代收類別 CHAR 6 ,值為(" + cldmrBus.getCode() + ") ";
            isError = true;
        }
        // RCPTID 虛擬帳號 VARCHAR2 16
        if (!Objects.isNull(cldmrBus.getRcptid()) && cldmrBus.getRcptid().length() > 16) {
            errorMsg += ", RCPTID 虛擬帳號 VARCHAR2 16 ,值為(" + cldmrBus.getRcptid() + ") ";
            isError = true;
        }
        // ID 繳款人統編 VARCHAR2 10
        if (!Objects.isNull(cldmrBus.getId()) && cldmrBus.getId().length() > 10) {
            errorMsg += ", ID 繳款人統編 VARCHAR2 10 ,值為(" + cldmrBus.getId() + ") ";
            isError = true;
        }
        // PNAME 繳款人名稱 NVARCHAR2 30
        if (!Objects.isNull(cldmrBus.getPname()) && cldmrBus.getPname().length() > 30) {
            errorMsg += ", PNAME 繳款人名稱 NVARCHAR2 30 ,值為(" + cldmrBus.getPname() + ") ";
            isError = true;
        }
        // CURCD 繳費幣別 DECIMAL 2
        if (cldmrBus.getCurcd() > 99) {
            errorMsg += ", CURCD 繳費幣別 DECIMAL 2 ,值為(" + cldmrBus.getCurcd() + ") ";
            isError = true;
        }
        // BAL 虛擬分戶餘額 DECIMAL 15 2
        if (cldmrBus.getBal().compareTo(new BigDecimal("9999999999999.99")) > 0) {
            errorMsg += ", BAL 虛擬分戶餘額 DECIMAL 15 2 ,值為(" + cldmrBus.getBal() + ") ";
            isError = true;
        }
        // LDATE 繳款期限 DECIMAL 8
        if (cldmrBus.getLdate() > 99999999) {
            errorMsg += ", LDATE 繳款期限 DECIMAL 8 ,值為(" + cldmrBus.getLdate() + ") ";
            isError = true;
        }
        // LTIME 繳款時限 DECIMAL 6
        if (cldmrBus.getLtime() > 999999) {
            errorMsg += ", LTIME 繳款時限 DECIMAL 6 ,值為(" + cldmrBus.getLtime() + ") ";
            isError = true;
        }
        // KDATE 保留期限 DECIMAL 8
        if (cldmrBus.getKdate() > 99999999) {
            errorMsg += ", KDATE 保留期限 DECIMAL 8 ,值為(" + cldmrBus.getKdate() + ") ";
            isError = true;
        }
        // LFLG 逾期記號 DECIMAL 1
        if (cldmrBus.getLflg() > 9) {
            errorMsg += ", LFLG 逾期記號 DECIMAL 1 ,值為(" + cldmrBus.getLflg() + ") ";
            isError = true;
        }
        // CDATE 建檔日 DECIMAL 8
        if (cldmrBus.getCdate() > 99999999) {
            errorMsg += ", CDATE 建檔日 DECIMAL 8 ,值為(" + cldmrBus.getCdate() + ") ";
            isError = true;
        }
        // UDATE 異動日 DECIMAL 8
        if (cldmrBus.getUdate() > 99999999) {
            errorMsg += ", UDATE 異動日 DECIMAL 8 ,值為(" + cldmrBus.getUdate() + ") ";
            isError = true;
        }
        // UTIME 異動時間 DECIMAL 6
        if (cldmrBus.getUtime() > 999999) {
            errorMsg += ", UTIME 異動時間 DECIMAL 6 ,值為(" + cldmrBus.getUtime() + ") ";
            isError = true;
        }
        // KINBR 異動分行 DECIMAL 3
        if (cldmrBus.getKinbr() > 999) {
            errorMsg += ", KINBR 異動分行 DECIMAL 3 ,值為(" + cldmrBus.getKinbr() + ") ";
            isError = true;
        }
        // TLRNO 異動櫃員 VARCHAR2 2
        if (!Objects.isNull(cldmrBus.getTlrno()) && cldmrBus.getTlrno().length() > 2) {
            errorMsg += ", TLRNO 異動櫃員 VARCHAR2 2 ,值為(" + cldmrBus.getTlrno() + ") ";
            isError = true;
        }
        // EMPNO 異動員工編號 VARCHAR2 6
        if (!Objects.isNull(cldmrBus.getEmpno()) && cldmrBus.getEmpno().length() > 6) {
            errorMsg += ", EMPNO 異動員工編號 VARCHAR2 6 ,值為(" + cldmrBus.getEmpno() + ") ";
            isError = true;
        }
        return isError ? errorMsg : "";
    }

    private void doUpdateCldmr(
            List<Map<String, Map<String, String>>> updateList, TransactionCase batchTransaction) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "doUpdateCldmr");
        int errorCount = 0;
        int successCount = 0;
        int updateBatchCount = 0;
        List<CldmrBus> updateBatchList = new ArrayList<>();
        for (Map<String, Map<String, String>> um : updateList) {
            Map<String, String> updateKey = um.get("UPDATE_KEY");
            Map<String, String> m = um.get("UPDATE_VALUE");
            // PK
            CldmrId cldmrId = getCldmrId(updateKey);
            CldmrBus cldmrBus = cldmrService.findById(cldmrId);
            if (Objects.isNull(cldmrBus)) {
                modifyRecordUtil.addRecord("Update Skip, cldmrBus 不存在,cldmrId=" + cldmrId);
                errorCount++;
                continue;
            }
            if (m.containsKey("PNAME")) {
                cldmrBus.setPname(m.get("PNAME"));
            }
            if (m.containsKey("BAL")) {
                cldmrBus.setBal(diffParse.toBigDecimal(m.get("BAL")));
            }
            if (m.containsKey("LDATE")) {
                cldmrBus.setLdate(diffParse.toInt(m.get("LDATE")));
            }
            if (m.containsKey("LTIME")) {
                cldmrBus.setLtime(diffParse.toInt(m.get("LTIME")));
            }
            if (m.containsKey("KDATE")) {
                cldmrBus.setKdate(diffParse.toInt(m.get("KDATE")));
            }
            if (m.containsKey("LFLG")) {
                cldmrBus.setLflg(diffParse.toInt(m.get("LFLG")));
            }
            if (m.containsKey("CDATE")) {
                cldmrBus.setCdate(diffParse.toInt(m.get("CDATE")));
            }
            if (m.containsKey("UDATE")) {
                cldmrBus.setUdate(diffParse.toInt(m.get("UDATE")));
            }
            if (m.containsKey("UTIME")) {
                cldmrBus.setUtime(diffParse.toInt(m.get("UTIME")));
            }
            if (m.containsKey("KINBR")) {
                cldmrBus.setKinbr(diffParse.toInt(m.get("KINBR")));
            }
            if (m.containsKey("TLRNO")) {
                cldmrBus.setTlrno(m.get("TLRNO"));
            }
            if (m.containsKey("EMPNO")) {
                cldmrBus.setEmpno(m.get("EMPNO"));
            }

            // 特殊處理
            cldmrBus.setId(m.get("ID"));

            String checkResult = checkColumns(cldmrBus);
            if (!checkResult.isBlank()) {
                modifyRecordUtil.addRecord(checkResult + "," + m);
                continue;
            }
            updateBatchList.add(cldmrBus);
            updateBatchCount++;
            if (updateBatchCount >= MODIFY_BATCH_SIZE) {
                try {
                    cldmrService.updateAllSilent(updateBatchList);
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
                    errorDataCom.writeTempErrorData("CLDMR", updateBatchList); // 紀錄失敗資料結束後一筆一筆寫入
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
                cldmrService.updateAllSilent(updateBatchList);
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
                errorDataCom.writeTempErrorData("CLDMR", updateBatchList); // 紀錄失敗資料結束後一筆一筆寫入
                errorCount++;
                updateBatchCount = 0;
                batchTransaction.rollBack();
            }
            successCount += updateBatchCount;
            startTime = new Date();
            updateBatchList.clear();
        }
        errorDataCom.readTmpErrorFile(2, "CLDMR", batchTransaction); // 一筆一筆寫入失敗資料,若還是失敗則出ErrorCLDMR
        modifyRecordUtil.addRecord(
                "Update size = "
                        + updateList.size()
                        + " ,success = "
                        + successCount
                        + " ,error = "
                        + errorCount);
    }

    private void doDeleteCldmr(
            List<Map<String, String>> deleteList, TransactionCase batchTransaction) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "doDeleteCldmr");
        int errorCount = 0;
        int successCount = 0;
        int deleteBatchCount = 0;
        List<CldmrBus> deleteBatchList = new ArrayList<>();
        for (Map<String, String> m : deleteList) {
            // PK
            CldmrId cldmrId = getCldmrId(m);
            CldmrBus cldmrBus = cldmrService.findById(cldmrId);
            if (Objects.isNull(cldmrBus)) {
                modifyRecordUtil.addRecord("Delete Skip, cldmrBus 不存在,cldmrId=" + cldmrId);
                errorCount++;
                continue;
            }
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "cldmrBus={}", cldmrBus);
            deleteBatchList.add(cldmrBus);
            deleteBatchCount++;
            if (deleteBatchCount >= MODIFY_BATCH_SIZE) {
                try {
                    cldmrService.deleteAllSilent(deleteBatchList);
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
                    errorDataCom.writeTempErrorData("CLDMR", deleteBatchList); // 紀錄失敗資料結束後一筆一筆寫入
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
                cldmrService.deleteAllSilent(deleteBatchList);
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
                errorDataCom.writeTempErrorData("CLDMR", deleteBatchList); // 紀錄失敗資料結束後一筆一筆寫入
                errorCount++;
                deleteBatchCount = 0;
                batchTransaction.rollBack();
            }
            successCount += deleteBatchCount;
            startTime = new Date();
            deleteBatchList.clear();
        }
        errorDataCom.readTmpErrorFile(4, "CLDMR", batchTransaction); // 一筆一筆寫入失敗資料,若還是失敗則出ErrorCLDMR
        modifyRecordUtil.addRecord(
                "Delete size = "
                        + deleteList.size()
                        + " ,success = "
                        + successCount
                        + " ,error = "
                        + errorCount);
    }

    private CldmrId getCldmrId(Map<String, String> m) {
        // CODE+RCPTID+CURCD
        String code = m.get("CODE");
        String rcptid = m.get("RCPTID");
        int curcd = diffParse.toInt(m.get("CURCD"));
        return new CldmrId(code, rcptid, curcd);
    }
}
