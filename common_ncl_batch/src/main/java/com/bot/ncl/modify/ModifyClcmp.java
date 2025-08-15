/* (C) 2024 */
package com.bot.ncl.modify;

import com.bot.ncl.diff.ErrorDataCom;
import com.bot.ncl.dto.entities.ClcmpBus;
import com.bot.ncl.jpa.entities.impl.ClcmpId;
import com.bot.ncl.jpa.svc.ClcmpService;
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
public class ModifyClcmp {

    @Autowired private DiffParseUtil diffParse;

    @Autowired private ModifyRecordUtil modifyRecordUtil;

    @Autowired private BatchUtil batchUtil;

    @Autowired private ClcmpService clcmpService;
    @Autowired private ErrorDataCom errorDataCom;

    @Getter private Date startTime;

    @Getter private List<String> insertSqlErrorMsg;

    @Getter private Long maxCnt;

    private static final int MODIFY_BATCH_SIZE = 10;

    private ModifyClcmp() {
        super();
        // YOU SHOULD USE @Autowired ,NOT new ModifyClcmp()
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
        modifyRecordUtil.initialize("CLCMP");

        doInsertClcmp(insertList, batchTransaction);
        doUpdateClcmp(updateList, batchTransaction);
        doDeleteClcmp(deleteList, batchTransaction);

        modifyRecordUtil.output();

        return result;
    }

    private void doInsertClcmp(
            List<Map<String, String>> insertList, TransactionCase batchTransaction) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "doInsertClcmp");
        int errorCount = 0;
        int successCount = 0;
        int insertBatchCount = 0;
        List<ClcmpBus> insertBatchList = new ArrayList<>();
        for (Map<String, String> m : insertList) {
            ClcmpBus clcmpBus = new ClcmpBus();

            String code = m.get("CODE").replaceAll("[\\x00]", "");
            if (Objects.isNull(code) || code.isBlank()) {
                String errorMsg = "不寫入,收付類別為空白," + m;
                modifyRecordUtil.addRecord(errorMsg);
                insertSqlErrorMsg.add(errorMsg);
                continue;
            }
            clcmpBus.setCode(code);
            clcmpBus.setRcptid(diffParse.nvl(m.get("RCPTID"), ""));
            clcmpBus.setPname(diffParse.nvl(m.get("PNAME"), ""));
            clcmpBus.setAmt(diffParse.toBigDecimal(m.get("AMT")));
            clcmpBus.setSdate(diffParse.toInt(m.get("SDATE")));
            clcmpBus.setStime(diffParse.toInt(m.get("STIME")));
            clcmpBus.setLdate(diffParse.toInt(m.get("LDATE")));
            clcmpBus.setLtime(diffParse.toInt(m.get("LTIME")));
            clcmpBus.setKdate(diffParse.toInt(m.get("KDATE")));
            clcmpBus.setLflg(diffParse.toInt(m.get("LFLG")));
            clcmpBus.setCdate(diffParse.toInt(m.get("CDATE")));
            clcmpBus.setUdate(diffParse.toInt(m.get("UDATE")));
            clcmpBus.setUtime(diffParse.toInt(m.get("UTIME")));
            clcmpBus.setKinbr(diffParse.toInt(m.get("KINBR")));
            clcmpBus.setTlrno(diffParse.nvl(m.get("TLRNO"), ""));

            // 特殊處理
            clcmpBus.setId(diffParse.nvl(m.get("ID"), ""));
            clcmpBus.setCurcd(diffParse.toInt(m.get("CURCD")));
            clcmpBus.setEmpno(diffParse.nvl(m.get("EMPNO"), ""));
            clcmpBus.setOtflag(diffParse.nvl(m.get("OTFLAG"), ""));

            //            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "clcmpBus={}",
            // clcmpBus);
            // PK
            //            ClcmpId clcmpId = getClcmpId(m);
            //            ClcmpBus findClcmpBus = clcmpService.findById(clcmpId);
            //            if (!Objects.isNull(findClcmpBus)) {
            //                modifyRecordUtil.addRecord("Insert Skip, clcmpBus 已存在,clcmpId=" +
            // clcmpId);
            //                errorCount++;
            //                continue;
            //            }

            String checkResult = checkColumns(clcmpBus);
            if (!checkResult.isBlank()) {
                modifyRecordUtil.addRecord(checkResult + "," + m);
                insertSqlErrorMsg.add(checkResult + "," + m);
                continue;
            }

            insertBatchList.add(clcmpBus);
            insertBatchCount++;
            if (insertBatchCount >= MODIFY_BATCH_SIZE) {
                try {
                    clcmpService.insertAll(insertBatchList);
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
                    errorDataCom.writeTempErrorData("CLCMP", insertBatchList); // 紀錄失敗資料結束後一筆一筆寫入
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
                clcmpService.insertAll(insertBatchList);
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
                errorDataCom.writeTempErrorData("CLCMP", insertBatchList); // 紀錄失敗資料結束後一筆一筆寫入
                errorCount++;
                insertBatchCount = 0;
                batchTransaction.rollBack();
            }
            successCount += insertBatchCount;
            startTime = new Date();
            insertBatchList.clear();
        }
        errorDataCom.readTmpErrorFile(1, "CLCMP", batchTransaction); // 一筆一筆寫入失敗資料,若還是失敗則出ErrorCLCMP
        maxCnt = clcmpService.findAllMaxCnt();
        modifyRecordUtil.addRecord(
                "Insert size = "
                        + insertList.size()
                        + " ,success = "
                        + successCount
                        + " ,error = "
                        + errorCount);
    }

    private String checkColumns(ClcmpBus clcmpBus) {
        boolean isError = false;
        String errorMsg = "不寫入,欄位檢核不通過 ";
        // CODE 代收類別 CHAR 6
        if (!Objects.isNull(clcmpBus.getCode()) && clcmpBus.getCode().length() > 6) {
            errorMsg += ", CODE 代收類別 CHAR 6 ,值為(" + clcmpBus.getCode() + ") ";
            isError = true;
        }
        // RCPTID 虛擬帳號 VARCHAR2 16
        if (!Objects.isNull(clcmpBus.getRcptid()) && clcmpBus.getRcptid().length() > 16) {
            errorMsg += ", RCPTID 虛擬帳號 VARCHAR2 16 ,值為(" + clcmpBus.getRcptid() + ") ";
            isError = true;
        }
        // ID 繳款人統編 VARCHAR2 10
        if (!Objects.isNull(clcmpBus.getId()) && clcmpBus.getId().length() > 10) {
            errorMsg += ", ID 繳款人統編 VARCHAR2 10 ,值為(" + clcmpBus.getId() + ") ";
            isError = true;
        }
        // PNAME 繳款人名稱 NVARCHAR2 30
        if (!Objects.isNull(clcmpBus.getPname()) && clcmpBus.getPname().length() > 30) {
            errorMsg += ", PNAME 繳款人名稱 NVARCHAR2 30 ,值為(" + clcmpBus.getPname() + ") ";
            isError = true;
        }
        // CURCD 繳費幣別 DECIMAL 2
        if (clcmpBus.getCurcd() > 99) {
            errorMsg += ", CURCD 繳費幣別 DECIMAL 2 ,值為(" + clcmpBus.getCurcd() + ") ";
            isError = true;
        }
        // AMT 繳費金額 DECIMAL 13 2
        if (clcmpBus.getAmt().compareTo(new BigDecimal("99999999999.99")) > 0) {
            errorMsg += ", AMT 繳費金額 DECIMAL 13 2 ,值為(" + clcmpBus.getAmt() + ") ";
            isError = true;
        }
        // SDATE 開始日期 DECIMAL 8
        if (clcmpBus.getSdate() > 99999999) {
            errorMsg += ", SDATE 開始日期 DECIMAL 8 ,值為(" + clcmpBus.getSdate() + ") ";
            isError = true;
        }
        // STIME 開始時間 DECIMAL 6
        if (clcmpBus.getStime() > 999999) {
            errorMsg += ", STIME 開始時間 DECIMAL 6 ,值為(" + clcmpBus.getStime() + ") ";
            isError = true;
        }
        // LDATE 繳款期限 DECIMAL 8
        if (clcmpBus.getLdate() > 99999999) {
            errorMsg += ", LDATE 繳款期限 DECIMAL 8 ,值為(" + clcmpBus.getLdate() + ") ";
            isError = true;
        }
        // LTIME 繳款時限 DECIMAL 6
        if (clcmpBus.getLtime() > 999999) {
            errorMsg += ", LTIME 繳款時限 DECIMAL 6 ,值為(" + clcmpBus.getLtime() + ") ";
            isError = true;
        }
        // KDATE 保留期限 DECIMAL 8
        if (clcmpBus.getKdate() > 99999999) {
            errorMsg += ", KDATE 保留期限 DECIMAL 8 ,值為(" + clcmpBus.getKdate() + ") ";
            isError = true;
        }
        // LFLG 逾期記號 DECIMAL 1
        if (clcmpBus.getLflg() > 9) {
            errorMsg += ", LFLG 逾期記號 DECIMAL 1 ,值為(" + clcmpBus.getLflg() + ") ";
            isError = true;
        }
        // CDATE 建檔日 DECIMAL 8
        if (clcmpBus.getCdate() > 99999999) {
            errorMsg += ", CDATE 建檔日 DECIMAL 8 ,值為(" + clcmpBus.getCdate() + ") ";
            isError = true;
        }
        // UDATE 異動日 DECIMAL 8
        if (clcmpBus.getUdate() > 99999999) {
            errorMsg += ", UDATE 異動日 DECIMAL 8 ,值為(" + clcmpBus.getUdate() + ") ";
            isError = true;
        }
        // UTIME 異動時間 DECIMAL 6
        if (clcmpBus.getUtime() > 999999) {
            errorMsg += ", UTIME 異動時間 DECIMAL 6 ,值為(" + clcmpBus.getUtime() + ") ";
            isError = true;
        }
        // KINBR 異動分行 DECIMAL 3
        if (clcmpBus.getKinbr() > 999) {
            errorMsg += ", KINBR 異動分行 DECIMAL 3 ,值為(" + clcmpBus.getKinbr() + ") ";
            isError = true;
        }
        // TLRNO 異動櫃員 VARCHAR2 2
        if (!Objects.isNull(clcmpBus.getTlrno()) && clcmpBus.getTlrno().length() > 2) {
            errorMsg += ", TLRNO 異動櫃員 VARCHAR2 2 ,值為(" + clcmpBus.getTlrno() + ") ";
            isError = true;
        }
        // EMPNO 異動員工編號 VARCHAR2 6
        if (!Objects.isNull(clcmpBus.getEmpno()) && clcmpBus.getEmpno().length() > 6) {
            errorMsg += ", EMPNO 異動員工編號 VARCHAR2 6 ,值為(" + clcmpBus.getEmpno() + ") ";
            isError = true;
        }
        // OTFLAG 其他業務註記 VARCHAR2 2
        if (!Objects.isNull(clcmpBus.getOtflag()) && clcmpBus.getOtflag().length() > 2) {
            errorMsg += ", OTFLAG 其他業務註記 VARCHAR2 2 ,值為(" + clcmpBus.getOtflag() + ") ";
            isError = true;
        }
        return isError ? errorMsg : "";
    }

    private void doUpdateClcmp(
            List<Map<String, Map<String, String>>> updateList, TransactionCase batchTransaction) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "doUpdateClcmp");
        int errorCount = 0;
        int successCount = 0;
        int updateBatchCount = 0;
        List<ClcmpBus> updateBatchList = new ArrayList<>();
        for (Map<String, Map<String, String>> um : updateList) {
            Map<String, String> updateKey = um.get("UPDATE_KEY");
            Map<String, String> m = um.get("UPDATE_VALUE");
            // PK
            ClcmpId clcmpId = getClcmpId(updateKey);
            ClcmpBus clcmpBus = clcmpService.findById(clcmpId);
            if (Objects.isNull(clcmpBus)) {
                modifyRecordUtil.addRecord("Update Skip, clcmpBus 不存在,clcmpId=" + clcmpId);
                errorCount++;
                continue;
            }
            if (m.containsKey("PNAME")) {
                clcmpBus.setPname(m.get("PNAME"));
            }
            if (m.containsKey("AMT")) {
                clcmpBus.setAmt(diffParse.toBigDecimal(m.get("AMT")));
            }
            if (m.containsKey("SDATE")) {
                clcmpBus.setSdate(diffParse.toInt(m.get("SDATE")));
            }
            if (m.containsKey("STIME")) {
                clcmpBus.setStime(diffParse.toInt(m.get("STIME")));
            }
            if (m.containsKey("LDATE")) {
                clcmpBus.setLdate(diffParse.toInt(m.get("LDATE")));
            }
            if (m.containsKey("LTIME")) {
                clcmpBus.setLtime(diffParse.toInt(m.get("LTIME")));
            }
            if (m.containsKey("KDATE")) {
                clcmpBus.setKdate(diffParse.toInt(m.get("KDATE")));
            }
            if (m.containsKey("LFLG")) {
                clcmpBus.setLflg(diffParse.toInt(m.get("LFLG")));
            }
            if (m.containsKey("CDATE")) {
                clcmpBus.setCdate(diffParse.toInt(m.get("CDATE")));
            }
            if (m.containsKey("UDATE")) {
                clcmpBus.setUdate(diffParse.toInt(m.get("UDATE")));
            }
            if (m.containsKey("UTIME")) {
                clcmpBus.setUtime(diffParse.toInt(m.get("UTIME")));
            }
            if (m.containsKey("KINBR")) {
                clcmpBus.setKinbr(diffParse.toInt(m.get("KINBR")));
            }
            if (m.containsKey("TLRNO")) {
                clcmpBus.setTlrno(m.get("TLRNO"));
            }

            // 特殊處理
            clcmpBus.setId(m.get("ID"));
            clcmpBus.setCurcd(diffParse.toInt(m.get("CURCD")));
            clcmpBus.setEmpno(m.get("EMPNO"));
            clcmpBus.setOtflag(m.get("OTFLAG"));

            String checkResult = checkColumns(clcmpBus);
            if (!checkResult.isBlank()) {
                modifyRecordUtil.addRecord(checkResult + "," + m);
                continue;
            }
            updateBatchList.add(clcmpBus);
            updateBatchCount++;
            if (updateBatchCount >= MODIFY_BATCH_SIZE) {
                try {
                    clcmpService.updateAllSilent(updateBatchList);
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
                    errorDataCom.writeTempErrorData("CLCMP", updateBatchList); // 紀錄失敗資料結束後一筆一筆寫入
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
                clcmpService.updateAllSilent(updateBatchList);
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
                errorDataCom.writeTempErrorData("CLCMP", updateBatchList); // 紀錄失敗資料結束後一筆一筆寫入
                errorCount++;
                updateBatchCount = 0;
                batchTransaction.rollBack();
            }
            successCount += updateBatchCount;
            startTime = new Date();
            updateBatchList.clear();
        }
        errorDataCom.readTmpErrorFile(2, "CLCMP", batchTransaction); // 一筆一筆寫入失敗資料,若還是失敗則出ErrorCLCMP
        modifyRecordUtil.addRecord(
                "Update size = "
                        + updateList.size()
                        + " ,success = "
                        + successCount
                        + " ,error = "
                        + errorCount);
    }

    private void doDeleteClcmp(
            List<Map<String, String>> deleteList, TransactionCase batchTransaction) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "doDeleteClcmp");
        int errorCount = 0;
        int successCount = 0;
        int deleteBatchCount = 0;
        List<ClcmpBus> deleteBatchList = new ArrayList<>();
        for (Map<String, String> m : deleteList) {
            // PK
            ClcmpId clcmpId = getClcmpId(m);
            ClcmpBus clcmpBus = clcmpService.findById(clcmpId);
            if (Objects.isNull(clcmpBus)) {
                modifyRecordUtil.addRecord("Delete Skip, clcmpBus 不存在,clcmpId=" + clcmpId);
                errorCount++;
                continue;
            }
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "clcmpBus={}", clcmpBus);
            deleteBatchList.add(clcmpBus);
            deleteBatchCount++;
            if (deleteBatchCount >= MODIFY_BATCH_SIZE) {
                try {
                    clcmpService.deleteAllSilent(deleteBatchList);
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
                    errorDataCom.writeTempErrorData("CLCMP", deleteBatchList); // 紀錄失敗資料結束後一筆一筆寫入
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
                clcmpService.deleteAllSilent(deleteBatchList);
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
                errorDataCom.writeTempErrorData("CLCMP", deleteBatchList); // 紀錄失敗資料結束後一筆一筆寫入
                errorCount++;
                deleteBatchCount = 0;
                batchTransaction.rollBack();
            }
            successCount += deleteBatchCount;
            startTime = new Date();
            deleteBatchList.clear();
        }
        errorDataCom.readTmpErrorFile(4, "CLCMP", batchTransaction); // 一筆一筆寫入失敗資料,若還是失敗則出ErrorCLCMP
        modifyRecordUtil.addRecord(
                "Delete size = "
                        + deleteList.size()
                        + " ,success = "
                        + successCount
                        + " ,error = "
                        + errorCount);
    }

    private ClcmpId getClcmpId(Map<String, String> m) {
        // CODE+RCPTID
        String code = m.get("CODE");
        String rcptid = m.get("RCPTID");
        return new ClcmpId(code, rcptid);
    }
}
