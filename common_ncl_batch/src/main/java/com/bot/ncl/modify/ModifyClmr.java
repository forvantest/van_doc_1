/* (C) 2024 */
package com.bot.ncl.modify;

import com.bot.ncl.diff.ErrorDataCom;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.jpa.svc.ClmrService;
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
public class ModifyClmr {

    @Autowired private DiffParseUtil diffParse;

    @Autowired private ModifyRecordUtil modifyRecordUtil;

    @Autowired private BatchUtil batchUtil;

    @Autowired private ClmrService clmrService;
    @Autowired private ErrorDataCom errorDataCom;

    @Getter private Date startTime;

    @Getter private List<String> insertSqlErrorMsg;

    @Getter private Long maxCnt;

    private static final int MODIFY_BATCH_SIZE = 1000;

    private ModifyClmr() {
        super();
        // YOU SHOULD USE @Autowired ,NOT new ModifyClmr()
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
        modifyRecordUtil.initialize("CLMR");

        doInsertClmr(insertList, batchTransaction);
        doUpdateClmr(updateList, batchTransaction);
        doDeleteClmr(deleteList, batchTransaction);

        modifyRecordUtil.output();

        return result;
    }

    private void doInsertClmr(
            List<Map<String, String>> insertList, TransactionCase batchTransaction) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "doInsertClmr");
        int errorCount = 0;
        int successCount = 0;
        int insertBatchCount = 0;
        List<ClmrBus> insertBatchList = new ArrayList<>();

        for (Map<String, String> m : insertList) {
            ClmrBus clmrBus = new ClmrBus();

            String code = m.get("CODE").replaceAll("[\\x00]", "");
            if (Objects.isNull(code) || code.isBlank()) {
                String errorMsg = "不寫入,收付類別為空白," + m;
                modifyRecordUtil.addRecord(errorMsg);
                insertSqlErrorMsg.add(errorMsg);
                errorCount++;
                continue;
            }
            clmrBus.setCode(code);
            clmrBus.setPbrno(diffParse.toInt(m.get("PBRNO")));
            clmrBus.setVrcode(diffParse.toInt(m.get("VRCODE")));
            clmrBus.setRiddup(diffParse.toInt(m.get("RIDDUP")));
            clmrBus.setActno(diffParse.toLong(m.get("ACTNO")));
            clmrBus.setMsg1(diffParse.toInt(m.get("MSG1")));
            clmrBus.setPuttime(diffParse.toInt(m.get("PUTTIME")));
            clmrBus.setChktype(diffParse.nvl(m.get("CHKTYPE"), ""));
            clmrBus.setChkamt(diffParse.toInt(m.get("CHKAMT")));
            clmrBus.setUnit(diffParse.toBigDecimal(m.get("UNIT")));
            clmrBus.setAmt(diffParse.toBigDecimal(m.get("AMT")));
            clmrBus.setCname(diffParse.nvl(m.get("CNAME"), ""));
            clmrBus.setStop(diffParse.toInt(m.get("STOP")));
            clmrBus.setAfcbv(diffParse.toInt(m.get("AFCBV")));
            clmrBus.setNetinfo(diffParse.nvl(m.get("NETINFO"), ""));
            clmrBus.setPrint(diffParse.toInt(m.get("PRINT")));
            clmrBus.setStopdate(diffParse.toInt(m.get("STOPDATE")));
            clmrBus.setStoptime(diffParse.toInt(m.get("STOPTIME")));
            clmrBus.setLkcode(diffParse.nvl(m.get("LKCODE"), ""));

            // 特殊處理
            clmrBus.setDupcyc(diffParse.toInt(m.get("DUPCYC")));
            clmrBus.setSubfg(diffParse.toInt(m.get("SUBFG")));
            clmrBus.setAmtcyc(diffParse.toInt(m.get("AMTCYC")));
            clmrBus.setAmtfg(diffParse.toInt(m.get("AMTFG")));
            clmrBus.setHoldcnt(diffParse.toInt(m.get("HOLDCNT")));
            clmrBus.setHoldcnt2(diffParse.toInt(m.get("HOLDCNT2")));
            clmrBus.setStdate(diffParse.toInt(m.get("STDATE")));
            clmrBus.setSttime(diffParse.toInt(m.get("STTIME")));
            clmrBus.setCrdb(diffParse.toInt(m.get("CRDB")));
            clmrBus.setHcode(diffParse.toInt(m.get("HCODE")));
            clmrBus.setFlag(diffParse.nvl(m.get("FLAG"), ""));
            clmrBus.setOtherField(diffParse.nvl(m.get("OTHER_FIELD"), ""));

            String checkResult = checkColumns(clmrBus);
            if (!checkResult.isBlank()) {
                modifyRecordUtil.addRecord(checkResult + "," + m);
                insertSqlErrorMsg.add(checkResult + "," + m);
                continue;
            }

            insertBatchList.add(clmrBus);
            insertBatchCount++;
            if (insertBatchCount >= MODIFY_BATCH_SIZE) {
                try {
                    clmrService.insertAll(insertBatchList);
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
                    errorDataCom.writeTempErrorData("CLMR", insertBatchList); // 紀錄失敗資料結束後一筆一筆寫入
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
                clmrService.insertAll(insertBatchList);
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
                errorDataCom.writeTempErrorData("CLMR", insertBatchList); // 紀錄失敗資料結束後一筆一筆寫入
                errorCount++;
                insertBatchCount = 0;
                batchTransaction.rollBack();
            }
            successCount += insertBatchCount;
            startTime = new Date();
            insertBatchList.clear();
        }
        errorDataCom.readTmpErrorFile(1, "CLMR", batchTransaction); // 一筆一筆寫入失敗資料,若還是失敗則出ErrorCLMR
        maxCnt = clmrService.findAllMaxCnt();
        modifyRecordUtil.addRecord(
                "Insert size = "
                        + insertList.size()
                        + " ,success = "
                        + successCount
                        + " ,error = "
                        + errorCount);
    }

    private String checkColumns(ClmrBus clmrBus) {
        boolean isError = false;
        String errorMsg = "不寫入,欄位檢核不通過 ";
        // CODE 代收類別 CHAR 6
        if (!Objects.isNull(clmrBus.getCode()) && clmrBus.getCode().length() > 6) {
            errorMsg += ", CODE 代收類別 CHAR 6 ,值為(" + clmrBus.getCode() + ") ";
            isError = true;
        }
        // PBRNO 主辦分行 DECIMAL 3
        if (clmrBus.getPbrno() > 999) {
            errorMsg += ", PBRNO 主辦分行 DECIMAL 3 ,值為(" + clmrBus.getPbrno() + ") ";
            isError = true;
        }
        // VRCODE 虛擬帳號繳款代碼 DECIMAL 4
        if (clmrBus.getVrcode() > 9999) {
            errorMsg += ", VRCODE 虛擬帳號繳款代碼 DECIMAL 4 ,值為(" + clmrBus.getVrcode() + ") ";
            isError = true;
        }
        // RIDDUP 銷帳編號重覆記號 DECIMAL 1
        if (clmrBus.getRiddup() > 9) {
            errorMsg += ", RIDDUP 銷帳編號重覆記號 DECIMAL 1 ,值為(" + clmrBus.getRiddup() + ") ";
            isError = true;
        }
        // DUPCYC 銷帳編號不可重覆週期 DECIMAL 3
        if (clmrBus.getDupcyc() > 999) {
            errorMsg += ", DUPCYC 銷帳編號不可重覆週期 DECIMAL 3 ,值為(" + clmrBus.getDupcyc() + ") ";
            isError = true;
        }
        // ACTNO 存入戶號 DECIMAL 12
        if (clmrBus.getActno() > 999999999999L) {
            errorMsg += ", ACTNO 存入戶號 DECIMAL 12 ,值為(" + clmrBus.getActno() + ") ";
            isError = true;
        }
        // MSG1 整批入扣記號 DECIMAL 1
        if (clmrBus.getMsg1() > 9) {
            errorMsg += ", MSG1 整批入扣記號 DECIMAL 1 ,值為(" + clmrBus.getMsg1() + ") ";
            isError = true;
        }
        // PUTTIME 資料回應方式 DECIMAL 1
        if (clmrBus.getPuttime() > 9) {
            errorMsg += ", PUTTIME 資料回應方式 DECIMAL 1 ,值為(" + clmrBus.getPuttime() + ") ";
            isError = true;
        }
        // SUBFG 分戶類別 DECIMAL 1
        if (clmrBus.getSubfg() > 9) {
            errorMsg += ", SUBFG 分戶類別 DECIMAL 1 ,值為(" + clmrBus.getSubfg() + ") ";
            isError = true;
        }
        // CHKTYPE 資料檢查方式 VARCHAR2 2
        if (!Objects.isNull(clmrBus.getChktype()) && clmrBus.getChktype().length() > 2) {
            errorMsg += ", CHKTYPE 資料檢查方式 VARCHAR2 2 ,值為(" + clmrBus.getChktype() + ") ";
            isError = true;
        }
        // CHKAMT 金額檢查方式 DECIMAL 1
        if (clmrBus.getChkamt() > 9) {
            errorMsg += ", CHKAMT 金額檢查方式 DECIMAL 1 ,值為(" + clmrBus.getChkamt() + ") ";
            isError = true;
        }
        // UNIT 單位金額(每股金額) DECIMAL 10 2
        if (clmrBus.getUnit().compareTo(new BigDecimal("99999999.99")) > 0) {
            errorMsg += ", UNIT 單位金額(每股金額) DECIMAL 10 2 ,值為(" + clmrBus.getUnit() + ") ";
            isError = true;
        }
        // AMTCYC 額度控管週期 DECIMAL 1
        if (clmrBus.getAmtcyc() > 9) {
            errorMsg += ", AMTCYC 額度控管週期 DECIMAL 1 ,值為(" + clmrBus.getAmtcyc() + ") ";
            isError = true;
        }
        // AMTFG 額度控管週期類別 DECIMAL 1
        if (clmrBus.getAmtfg() > 9) {
            errorMsg += ", AMTFG 額度控管週期類別 DECIMAL 1 ,值為(" + clmrBus.getAmtfg() + ") ";
            isError = true;
        }
        // AMT 額度控管 DECIMAL 17 2
        if (clmrBus.getAmt().compareTo(new BigDecimal("999999999999999.99")) > 0) {
            errorMsg += ", AMT 額度控管 DECIMAL 17 2 ,值為(" + clmrBus.getAmt() + ") ";
            isError = true;
        }
        // CNAME 單位中文名 NVARCHAR2 40
        if (!Objects.isNull(clmrBus.getCname()) && clmrBus.getCname().length() > 40) {
            errorMsg += ", CNAME 單位中文名 NVARCHAR2 40 ,值為(" + clmrBus.getCname() + ") ";
            isError = true;
        }
        // STOP 代收狀態記號 DECIMAL 2
        if (clmrBus.getStop() > 99) {
            errorMsg += ", STOP 代收狀態記號 DECIMAL 2 ,值為(" + clmrBus.getStop() + ") ";
            isError = true;
        }
        // HOLDCNT 單筆黑名單筆數 DECIMAL 3
        if (clmrBus.getHoldcnt() > 999) {
            errorMsg += ", HOLDCNT 單筆黑名單筆數 DECIMAL 3 ,值為(" + clmrBus.getHoldcnt() + ") ";
            isError = true;
        }
        // HOLDCNT2 連續黑名單筆數 DECIMAL 3
        if (clmrBus.getHoldcnt2() > 999) {
            errorMsg += ", HOLDCNT2 連續黑名單筆數 DECIMAL 3 ,值為(" + clmrBus.getHoldcnt2() + ") ";
            isError = true;
        }
        // AFCBV 軋帳記號 DECIMAL 1
        if (clmrBus.getAfcbv() > 9) {
            errorMsg += ", AFCBV 軋帳記號 DECIMAL 1 ,值為(" + clmrBus.getAfcbv() + ") ";
            isError = true;
        }
        // NETINFO 即時傳輸平台代號或稅費解繳日期起迄控制 VARCHAR2 20
        if (!Objects.isNull(clmrBus.getNetinfo()) && clmrBus.getNetinfo().length() > 20) {
            errorMsg +=
                    ", NETINFO 即時傳輸平台代號或稅費解繳日期起迄控制 VARCHAR2 20 ,值為(" + clmrBus.getNetinfo() + ") ";
            isError = true;
        }
        // PRINT 起始列印行數 DECIMAL 2
        if (clmrBus.getPrint() > 99) {
            errorMsg += ", PRINT 起始列印行數 DECIMAL 2 ,值為(" + clmrBus.getPrint() + ") ";
            isError = true;
        }
        // STDATE 收付起始日期 DECIMAL 8
        if (clmrBus.getStdate() > 99999999) {
            errorMsg += ", STDATE 收付起始日期 DECIMAL 8 ,值為(" + clmrBus.getStdate() + ") ";
            isError = true;
        }
        // STTIME 收付起始時分秒 DECIMAL 6
        if (clmrBus.getSttime() > 999999) {
            errorMsg += ", STTIME 收付起始時分秒 DECIMAL 6 ,值為(" + clmrBus.getSttime() + ") ";
            isError = true;
        }
        // STOPDATE 收付截止日期 DECIMAL 8
        if (clmrBus.getStopdate() > 99999999) {
            errorMsg += ", STOPDATE 收付截止日期 DECIMAL 8 ,值為(" + clmrBus.getStopdate() + ") ";
            isError = true;
        }
        // STOPTIME 收付截止時分秒 DECIMAL 6
        if (clmrBus.getStoptime() > 999999) {
            errorMsg += ", STOPTIME 收付截止時分秒 DECIMAL 6 ,值為(" + clmrBus.getStoptime() + ") ";
            isError = true;
        }
        // CRDB 收付記號 DECIMAL 1
        if (clmrBus.getCrdb() > 9) {
            errorMsg += ", CRDB 收付記號 DECIMAL 1 ,值為(" + clmrBus.getCrdb() + ") ";
            isError = true;
        }
        // HCODE 更正記號 DECIMAL 1
        if (clmrBus.getHcode() > 9) {
            errorMsg += ", HCODE 更正記號 DECIMAL 1 ,值為(" + clmrBus.getHcode() + ") ";
            isError = true;
        }
        // LKCODE 全繳連動代收類別 VARCHAR2 6
        if (!Objects.isNull(clmrBus.getLkcode()) && clmrBus.getLkcode().length() > 6) {
            errorMsg += ", LKCODE 全繳連動代收類別 VARCHAR2 6 ,值為(" + clmrBus.getLkcode() + ") ";
            isError = true;
        }
        // FLAG 轉換記號 CHAR 1
        if (!Objects.isNull(clmrBus.getFlag()) && clmrBus.getFlag().length() > 1) {
            errorMsg += ", FLAG 轉換記號 CHAR 1 ,值為(" + clmrBus.getFlag() + ") ";
            isError = true;
        }
        // OTHER_FIELD 其他資料 VARCHAR2 500
        if (!Objects.isNull(clmrBus.getOtherField()) && clmrBus.getOtherField().length() > 500) {
            errorMsg += ", OTHER_FIELD 其他資料 VARCHAR2 500 ,值為(" + clmrBus.getOtherField() + ") ";
            isError = true;
        }
        return isError ? errorMsg : "";
    }

    private void doUpdateClmr(
            List<Map<String, Map<String, String>>> updateList, TransactionCase batchTransaction) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "doUpdateClmr");
        int errorCount = 0;
        int successCount = 0;
        int updateBatchCount = 0;
        List<ClmrBus> updateBatchList = new ArrayList<>();
        for (Map<String, Map<String, String>> um : updateList) {
            Map<String, String> updateKey = um.get("UPDATE_KEY");
            Map<String, String> m = um.get("UPDATE_VALUE");
            // PK
            String code = updateKey.get("CODE");
            ClmrBus clmrBus = clmrService.findById(code);
            if (Objects.isNull(clmrBus)) {
                modifyRecordUtil.addRecord("Update Skip, clmrBus 不存在,code=" + code);
                errorCount++;
                continue;
            }
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "hold clmrBus={}", clmrBus);
            if (m.containsKey("PBRNO")) {
                clmrBus.setPbrno(diffParse.toInt(m.get("PBRNO")));
            }
            if (m.containsKey("VRCODE")) {
                clmrBus.setVrcode(diffParse.toInt(m.get("VRCODE")));
            }
            if (m.containsKey("RIDDUP")) {
                clmrBus.setRiddup(diffParse.toInt(m.get("RIDDUP")));
            }
            if (m.containsKey("ACTNO")) {
                clmrBus.setActno(diffParse.toLong(m.get("ACTNO")));
            }
            if (m.containsKey("MSG1")) {
                clmrBus.setMsg1(diffParse.toInt(m.get("MSG1")));
            }
            if (m.containsKey("PUTTIME")) {
                clmrBus.setPuttime(diffParse.toInt(m.get("PUTTIME")));
            }
            if (m.containsKey("CHKTYPE")) {
                clmrBus.setChktype(m.get("CHKTYPE"));
            }
            if (m.containsKey("CHKAMT")) {
                clmrBus.setChkamt(diffParse.toInt(m.get("CHKAMT")));
            }
            if (m.containsKey("UNIT")) {
                clmrBus.setUnit(diffParse.toBigDecimal(m.get("UNIT")));
            }
            if (m.containsKey("AMT")) {
                clmrBus.setAmt(diffParse.toBigDecimal(m.get("AMT")));
            }
            if (m.containsKey("CNAME")) {
                clmrBus.setCname(m.get("CNAME"));
            }
            if (m.containsKey("STOP")) {
                clmrBus.setStop(diffParse.toInt(m.get("STOP")));
            }
            if (m.containsKey("AFCBV")) {
                clmrBus.setAfcbv(diffParse.toInt(m.get("AFCBV")));
            }
            if (m.containsKey("NETINFO")) {
                clmrBus.setNetinfo(m.get("NETINFO"));
            }
            if (m.containsKey("PRINT")) {
                clmrBus.setPrint(diffParse.toInt(m.get("PRINT")));
            }
            if (m.containsKey("STOPDATE")) {
                clmrBus.setStopdate(diffParse.toInt(m.get("STOPDATE")));
            }
            if (m.containsKey("STOPTIME")) {
                clmrBus.setStoptime(diffParse.toInt(m.get("STOPTIME")));
            }
            if (m.containsKey("LKCODE")) {
                clmrBus.setLkcode(m.get("LKCODE"));
            }

            // 特殊處理
            clmrBus.setDupcyc(diffParse.toInt(m.get("DUPCYC")));
            clmrBus.setSubfg(diffParse.toInt(m.get("SUBFG")));
            clmrBus.setAmtcyc(diffParse.toInt(m.get("AMTCYC")));
            clmrBus.setAmtfg(diffParse.toInt(m.get("AMTFG")));
            clmrBus.setHoldcnt(diffParse.toInt(m.get("HOLDCNT")));
            clmrBus.setHoldcnt2(diffParse.toInt(m.get("HOLDCNT2")));
            clmrBus.setStdate(diffParse.toInt(m.get("STDATE")));
            clmrBus.setSttime(diffParse.toInt(m.get("STTIME")));
            clmrBus.setCrdb(diffParse.toInt(m.get("CRDB")));
            clmrBus.setHcode(diffParse.toInt(m.get("HCODE")));
            clmrBus.setFlag(m.get("FLAG"));
            clmrBus.setOtherField(m.get("OTHER_FIELD"));

            String checkResult = checkColumns(clmrBus);
            if (!checkResult.isBlank()) {
                modifyRecordUtil.addRecord(checkResult + "," + m);
                continue;
            }
            updateBatchList.add(clmrBus);
            updateBatchCount++;
            if (updateBatchCount >= MODIFY_BATCH_SIZE) {
                try {
                    clmrService.updateAllSilent(updateBatchList);
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
                    errorDataCom.writeTempErrorData("CLMR", updateBatchList); // 紀錄失敗資料結束後一筆一筆寫入
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
                clmrService.updateAllSilent(updateBatchList);
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
                errorDataCom.writeTempErrorData("CLMR", updateBatchList); // 紀錄失敗資料結束後一筆一筆寫入
                errorCount++;
                updateBatchCount = 0;
                batchTransaction.rollBack();
            }
            successCount += updateBatchCount;
            startTime = new Date();
            updateBatchList.clear();
        }
        errorDataCom.readTmpErrorFile(2, "CLMR", batchTransaction); // 一筆一筆寫入失敗資料,若還是失敗則出ErrorCLMR
        modifyRecordUtil.addRecord(
                "Update size = "
                        + updateList.size()
                        + " ,success = "
                        + successCount
                        + " ,error = "
                        + errorCount);
    }

    private void doDeleteClmr(
            List<Map<String, String>> deleteList, TransactionCase batchTransaction) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "doDeleteClmr");
        int errorCount = 0;
        int successCount = 0;
        int deleteBatchCount = 0;
        List<ClmrBus> deleteBatchList = new ArrayList<>();
        for (Map<String, String> m : deleteList) {
            // PK
            String code = m.get("CODE");
            ClmrBus clmrBus = clmrService.findById(code);
            if (Objects.isNull(clmrBus)) {
                modifyRecordUtil.addRecord("Delete Skip, clmrBus 不存在,code=" + code);
                errorCount++;
                continue;
            }
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "clmrBus={}", clmrBus);
            deleteBatchList.add(clmrBus);
            deleteBatchCount++;
            if (deleteBatchCount >= MODIFY_BATCH_SIZE) {
                try {
                    clmrService.deleteAllSilent(deleteBatchList);
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
                    errorDataCom.writeTempErrorData("CLMR", deleteBatchList); // 紀錄失敗資料結束後一筆一筆寫入
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
                clmrService.deleteAllSilent(deleteBatchList);
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
                errorDataCom.writeTempErrorData("CLMR", deleteBatchList); // 紀錄失敗資料結束後一筆一筆寫入
                errorCount++;
                deleteBatchCount = 0;
                batchTransaction.rollBack();
            }
            successCount += deleteBatchCount;
            startTime = new Date();
            deleteBatchList.clear();
        }
        errorDataCom.readTmpErrorFile(4, "CLMR", batchTransaction); // 一筆一筆寫入失敗資料,若還是失敗則出ErrorCLMR
        modifyRecordUtil.addRecord(
                "Delete size = "
                        + deleteList.size()
                        + " ,success = "
                        + successCount
                        + " ,error = "
                        + errorCount);
    }
}
