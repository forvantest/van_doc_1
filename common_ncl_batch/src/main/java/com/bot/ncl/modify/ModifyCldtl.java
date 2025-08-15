/* (C) 2024 */
package com.bot.ncl.modify;

import com.bot.ncl.diff.ErrorDataCom;
import com.bot.ncl.dto.entities.CldtlBus;
import com.bot.ncl.jpa.entities.impl.CldtlId;
import com.bot.ncl.jpa.svc.CldtlService;
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
public class ModifyCldtl {

    @Autowired private DiffParseUtil diffParse;

    @Autowired private ModifyRecordUtil modifyRecordUtil;

    @Autowired private BatchUtil batchUtil;

    @Autowired private CldtlService cldtlService;
    @Autowired private ErrorDataCom errorDataCom;

    @Getter private List<String> insertSqlErrorMsg;

    @Getter private Long maxCnt;

    @Getter private Date startTime;

    private static final int MODIFY_BATCH_SIZE = 100;

    private ModifyCldtl() {
        super();
        // YOU SHOULD USE @Autowired ,NOT new ModifyCldtl()
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
        modifyRecordUtil.initialize("CLDTL");

        doInsertCldtl(insertList, batchTransaction);
        doUpdateCldtl(updateList, batchTransaction);
        doDeleteCldtl(deleteList, batchTransaction);

        modifyRecordUtil.output();

        return result;
    }

    private void doInsertCldtl(
            List<Map<String, String>> insertList, TransactionCase batchTransaction) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "doInsertCldtl");
        int errorCount = 0;
        int successCount = 0;
        int insertBatchCount = 0;
        List<CldtlBus> insertBatchList = new ArrayList<>();
        for (Map<String, String> m : insertList) {
            CldtlBus cldtlBus = new CldtlBus();

            String code = m.get("CODE");
            if (Objects.isNull(code) || code.isBlank()) {
                String errorMsg = "不寫入,收付類別為空白," + m;
                modifyRecordUtil.addRecord(errorMsg);
                insertSqlErrorMsg.add(errorMsg);
                continue;
            }
            cldtlBus.setCode(code);
            cldtlBus.setAmt(diffParse.toBigDecimal(m.get("AMT")));
            String rcptid = m.get("RCPTID");
            if (Objects.isNull(rcptid) || rcptid.isBlank()) {
                String errorMsg = "不寫入,銷帳號碼(RCPTID)為空白," + m;
                modifyRecordUtil.addRecord(errorMsg);
                insertSqlErrorMsg.add(errorMsg);
                continue;
            }
            cldtlBus.setRcptid(diffParse.nvl(rcptid.replaceAll("\\s+$", ""), ""));
            cldtlBus.setTxtype(diffParse.nvl(m.get("TXTYPE"), ""));
            cldtlBus.setCllbr(diffParse.toInt(m.get("CLLBR")));
            cldtlBus.setTrmno(diffParse.toInt(m.get("TRMNO")));
            cldtlBus.setTlrno(diffParse.nvl(m.get("TLRNO"), ""));
            cldtlBus.setTime(diffParse.toInt(m.get("TIME")));
            cldtlBus.setLmtdate(diffParse.toInt(m.get("LMTDATE")));
            cldtlBus.setUserdata(diffParse.nvl(m.get("USERDATA"), ""));
            cldtlBus.setSitdate(diffParse.toInt(m.get("SITDATE")));
            cldtlBus.setCaldy(diffParse.toInt(m.get("CALDY")));
            cldtlBus.setActno(diffParse.nvl(m.get("ACTNO"), ""));
            cldtlBus.setSerino(diffParse.toInt(m.get("SERINO")));
            cldtlBus.setCrdb(diffParse.toInt(m.get("CRDB")));

            // 特殊處理
            cldtlBus.setEntdy(diffParse.toInt(m.get("ENTDY")));
            cldtlBus.setTxtno(diffParse.toInt(m.get("TXTNO")));
            cldtlBus.setCurcd(diffParse.toInt(m.get("CURCD")));
            cldtlBus.setHcode(diffParse.toInt(m.get("HCODE")));
            cldtlBus.setSourcetp(diffParse.nvl(m.get("SOURCETP"), ""));
            cldtlBus.setCactno(diffParse.nvl(m.get("CACTNO"), ""));
            cldtlBus.setHtxseq(diffParse.toLong(m.get("HTXSEQ")));
            cldtlBus.setEmpno(diffParse.nvl(m.get("EMPNO"), ""));
            cldtlBus.setPutfg(diffParse.toInt(m.get("PUTFG")));
            cldtlBus.setEntfg(diffParse.toInt(m.get("ENTFG")));
            cldtlBus.setSourceip(diffParse.nvl(m.get("SOURCEIP"), ""));
            cldtlBus.setUplfile(diffParse.nvl(m.get("UPLFILE"), ""));
            cldtlBus.setPbrno(diffParse.toInt(m.get("PBRNO")));
            cldtlBus.setCfee2(diffParse.toBigDecimal(m.get("CFEE2")));
            cldtlBus.setFkd(diffParse.toInt(m.get("FKD")));
            cldtlBus.setFee(diffParse.toBigDecimal(m.get("FEE")));
            cldtlBus.setFeecost(diffParse.toBigDecimal(m.get("FEECOST")));

            //            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "cldtlBus={}",
            // cldtlBus);
            // PK
            CldtlId cldtlId = getCldtlId(m);

            String checkResult = checkColumns(cldtlBus);
            if (!checkResult.isBlank()) {
                modifyRecordUtil.addRecord(checkResult + "," + m);
                insertSqlErrorMsg.add(checkResult + "," + m);
                continue;
            }

            insertBatchList.add(cldtlBus);
            insertBatchCount++;
            if (insertBatchCount >= MODIFY_BATCH_SIZE) {
                try {
                    cldtlService.insertAll(insertBatchList);
                    batchTransaction.commit();
                } catch (Exception e) {
                    ApLogHelper.error(
                            log,
                            false,
                            LogType.NORMAL.getCode(),
                            "insert failed, error={}",
                            e.getMessage());
                    String errorMsg = "Insert Failed, unexpected db error,error=" + e.getMessage();
                    //                    batchTransaction.rollBack();
                    //
                    //                    ApLogHelper.info(
                    //                            log, false, LogType.NORMAL.getCode(), "insert
                    // Failed in delete");
                    //                    try {
                    //
                    //                        for (CldtlBus t : insertBatchList) {
                    //                            CldtlId cldtlpk =
                    //                                    new CldtlId(
                    //                                            t.getCode(),
                    //                                            t.getRcptid(),
                    //                                            t.getEntdy(),
                    //                                            t.getTrmno(),
                    //                                            t.getTxtno());
                    //                            ApLogHelper.info(
                    //                                    log,
                    //                                    false,
                    //                                    LogType.NORMAL.getCode(),
                    //                                    "DELETE ING cldtlpk = [{}]",
                    //                                    cldtlpk);
                    //                        }
                    //                        cldtlService.deleteAll(insertBatchList);
                    //                        batchTransaction.commit();
                    //                        ApLogHelper.info(
                    //                                log,
                    //                                false,
                    //                                LogType.NORMAL.getCode(),
                    //                                "insert Failed delete commit");
                    //
                    //                    } catch (Exception eb) {
                    //                        ApLogHelper.error(
                    //                                log,
                    //                                false,
                    //                                LogType.NORMAL.getCode(),
                    //                                "Delete failed, error={}",
                    //                                e.getMessage());
                    //                        modifyRecordUtil.addRecord(errorMsg);
                    //                        insertSqlErrorMsg.add(errorMsg);
                    //                        errorCount++;
                    //                        startTime = new Date();
                    //                        errorDataCom.writeErrorData(1, "CLDTL",
                    // insertBatchList); // 紀錄失敗資料結束後一筆一筆寫入
                    //                        insertBatchList.clear();
                    //                        insertBatchCount = 0;
                    //                        batchTransaction.rollBack();
                    //                        continue;
                    //                    }
                    //                    try {
                    //                        cldtlService.insertAll(insertBatchList);
                    //                        batchTransaction.commit();
                    //                        ApLogHelper.info(
                    //                                log,
                    //                                false,
                    //                                LogType.NORMAL.getCode(),
                    //                                "insert Failed insert commit");
                    //                    } catch (Exception ea) {
                    //                        modifyRecordUtil.addRecord(errorMsg);
                    //                        insertSqlErrorMsg.add(errorMsg);
                    //                        errorCount++;
                    //                        startTime = new Date();
                    //                        for (CldtlBus t : insertBatchList) {
                    //                            CldtlId cldtlpk =
                    //                                    new CldtlId(
                    //                                            t.getCode(),
                    //                                            t.getRcptid(),
                    //                                            t.getEntdy(),
                    //                                            t.getTrmno(),
                    //                                            t.getTxtno());
                    //                            ApLogHelper.info(
                    //                                    log,
                    //                                    false,
                    //                                    LogType.NORMAL.getCode(),
                    //                                    "Failed cldtlpk = [{}]",
                    //                                    cldtlpk);
                    //                        }
                    //                        errorDataCom.writeErrorData(1, "CLDTL",
                    // insertBatchList); // 紀錄失敗資料結束後一筆一筆寫入
                    //                        insertBatchList.clear();
                    //                        insertBatchCount = 0;
                    //                        batchTransaction.rollBack();
                    //                        ApLogHelper.error(
                    //                                log,
                    //                                false,
                    //                                LogType.NORMAL.getCode(),
                    //                                "keydup insert failed, error={}",
                    //                                e.getMessage());
                    //                        continue;
                    //                    }
                    ApLogHelper.error(
                            log,
                            false,
                            LogType.NORMAL.getCode(),
                            "insertAll failed, error={}",
                            e.getMessage());
                    modifyRecordUtil.addRecord(errorMsg);
                    insertSqlErrorMsg.add(errorMsg);
                    errorCount++;
                    startTime = new Date();
                    errorDataCom.writeTempErrorData("CLDTL", insertBatchList); // 紀錄失敗資料結束後一筆一筆寫入
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
                cldtlService.insertAll(insertBatchList);
                batchTransaction.commit();
            } catch (Exception e) {
                ApLogHelper.error(
                        log,
                        false,
                        LogType.NORMAL.getCode(),
                        "insert failed, error={}",
                        e.getMessage());
                String errorMsg = "Insert Failed, unexpected db error,error=" + e.getMessage();
                batchTransaction.rollBack();

                //                try {
                //                    cldtlService.deleteAll(insertBatchList);
                //                    batchTransaction.commit();
                //                    try {
                //                        cldtlService.insertAll(insertBatchList);
                //                        batchTransaction.commit();
                //                    } catch (Exception ea) {
                //                        ApLogHelper.error(
                //                                log,
                //                                false,
                //                                LogType.NORMAL.getCode(),
                //                                "keydup insert failed, error={}",
                //                                ea.getMessage());
                //                        modifyRecordUtil.addRecord(errorMsg);
                //                        insertSqlErrorMsg.add(errorMsg);
                //                        errorDataCom.writeErrorData(1, "CLDTL", insertBatchList);
                // // 紀錄失敗資料結束後一筆一筆寫入
                //                        errorCount++;
                //                        insertBatchCount = 0;
                //                        batchTransaction.rollBack();
                //                    }
                //                } catch (Exception eb) {
                //                    modifyRecordUtil.addRecord(errorMsg);
                //                    insertSqlErrorMsg.add(errorMsg);
                //                    errorDataCom.writeErrorData(1, "CLDTL", insertBatchList); //
                // 紀錄失敗資料結束後一筆一筆寫入
                //                    errorCount++;
                //                    insertBatchCount = 0;
                //                    batchTransaction.rollBack();
                //                }
                ApLogHelper.error(
                        log,
                        false,
                        LogType.NORMAL.getCode(),
                        "keydup insert failed, error={}",
                        e.getMessage());
                modifyRecordUtil.addRecord(errorMsg);
                insertSqlErrorMsg.add(errorMsg);
                errorDataCom.writeTempErrorData("CLDTL", insertBatchList); // 紀錄失敗資料結束後一筆一筆寫入
                errorCount++;
                insertBatchCount = 0;
                batchTransaction.rollBack();
            }
            successCount += insertBatchCount;
            startTime = new Date();
            insertBatchList.clear();
        }
        errorDataCom.readTmpErrorFile(1, "CLDTL", batchTransaction); // 一筆一筆寫入失敗資料,若還是失敗則出ErrorCLDTL
        maxCnt = cldtlService.findAllMaxCnt();
        modifyRecordUtil.addRecord(
                "Insert size = "
                        + insertList.size()
                        + " ,success = "
                        + successCount
                        + " ,error = "
                        + errorCount);
    }

    private String checkColumns(CldtlBus cldtlBus) {
        boolean isError = false;
        String errorMsg = "不寫入,欄位檢核不通過 ";
        // CODE 代收類別 CHAR 6
        if (!Objects.isNull(cldtlBus.getCode()) && cldtlBus.getCode().length() > 6) {
            errorMsg += ", CODE 代收類別 CHAR 6 ,值為(" + cldtlBus.getCode() + ") ";
            isError = true;
        }
        // AMT 繳費金額 DECIMAL 13 2
        if (cldtlBus.getAmt().compareTo(new BigDecimal("99999999999.99")) > 0) {
            errorMsg += ", AMT 繳費金額 DECIMAL 13 2 ,值為(" + cldtlBus.getAmt() + ") ";
            isError = true;
        }
        // RCPTID 銷帳號碼 VARCHAR2 26
        if (!Objects.isNull(cldtlBus.getRcptid()) && cldtlBus.getRcptid().length() > 26) {
            errorMsg += ", RCPTID 銷帳號碼 VARCHAR2 26 ,值為(" + cldtlBus.getRcptid() + ") ";
            isError = true;
        }
        // TXTYPE 帳務別 VARCHAR2 2
        if (!Objects.isNull(cldtlBus.getTxtype()) && cldtlBus.getTxtype().length() > 2) {
            errorMsg += ", TXTYPE 帳務別 VARCHAR2 2 ,值為(" + cldtlBus.getTxtype() + ") ";
            isError = true;
        }
        // CLLBR 代收行 DECIMAL 3
        if (cldtlBus.getCllbr() > 999) {
            errorMsg += ", CLLBR 代收行 DECIMAL 3 ,值為(" + cldtlBus.getCllbr() + ") ";
            isError = true;
        }
        // TRMNO 櫃檯機編號 DECIMAL 7
        if (cldtlBus.getTrmno() > 9999999) {
            errorMsg += ", TRMNO 櫃檯機編號 DECIMAL 7 ,值為(" + cldtlBus.getTrmno() + ") ";
            isError = true;
        }
        // TXTNO 交易序號 DECIMAL 8
        if (cldtlBus.getTxtno() > 99999999) {
            errorMsg += ", TXTNO 交易序號 DECIMAL 8 ,值為(" + cldtlBus.getTxtno() + ") ";
            isError = true;
        }
        // TLRNO 櫃員號碼 CHAR 2
        if (!Objects.isNull(cldtlBus.getTlrno()) && cldtlBus.getTlrno().length() > 2) {
            errorMsg += ", TLRNO 櫃員號碼 CHAR 2 ,值為(" + cldtlBus.getTlrno() + ") ";
            isError = true;
        }
        // ENTDY 代收日 DECIMAL 8
        if (cldtlBus.getEntdy() > 99999999) {
            errorMsg += ", ENTDY 代收日 DECIMAL 8 ,值為(" + cldtlBus.getEntdy() + ") ";
            isError = true;
        }
        // TIME 代收時間 DECIMAL 6
        if (cldtlBus.getTime() > 999999) {
            errorMsg += ", TIME 代收時間 DECIMAL 6 ,值為(" + cldtlBus.getTime() + ") ";
            isError = true;
        }
        // LMTDATE 繳費期限 DECIMAL 8
        if (cldtlBus.getLmtdate() > 99999999) {
            errorMsg += ", LMTDATE 繳費期限 DECIMAL 8 ,值為(" + cldtlBus.getLmtdate() + ") ";
            isError = true;
        }
        // USERDATA 備註資料 NVARCHAR2 100
        if (!Objects.isNull(cldtlBus.getUserdata()) && cldtlBus.getUserdata().length() > 100) {
            errorMsg += ", USERDATA 備註資料 NVARCHAR2 100 ,值為(" + cldtlBus.getUserdata() + ") ";
            isError = true;
        }
        // SITDATE 原代收日 DECIMAL 8
        if (cldtlBus.getSitdate() > 99999999) {
            errorMsg += ", SITDATE 原代收日 DECIMAL 8 ,值為(" + cldtlBus.getSitdate() + ") ";
            isError = true;
        }
        // CALDY 交易日曆日 DECIMAL 8
        if (cldtlBus.getCaldy() > 99999999) {
            errorMsg += ", CALDY 交易日曆日 DECIMAL 8 ,值為(" + cldtlBus.getCaldy() + ") ";
            isError = true;
        }
        // ACTNO 事業單位帳號 VARCHAR2 20
        if (!Objects.isNull(cldtlBus.getActno()) && cldtlBus.getActno().length() > 20) {
            errorMsg += ", ACTNO 事業單位帳號 VARCHAR2 20 ,值為(" + cldtlBus.getActno() + ") ";
            isError = true;
        }
        // SERINO 交易明細流水序號 DECIMAL 6
        if (cldtlBus.getSerino() > 999999) {
            errorMsg += ", SERINO 交易明細流水序號 DECIMAL 6 ,值為(" + cldtlBus.getSerino() + ") ";
            isError = true;
        }
        // CRDB 收付記號 DECIMAL 1
        if (cldtlBus.getCrdb() > 9) {
            errorMsg += ", CRDB 收付記號 DECIMAL 1 ,值為(" + cldtlBus.getCrdb() + ") ";
            isError = true;
        }
        // CURCD 繳費幣別 DECIMAL 2
        if (cldtlBus.getCurcd() > 99) {
            errorMsg += ", CURCD 繳費幣別 DECIMAL 2 ,值為(" + cldtlBus.getCurcd() + ") ";
            isError = true;
        }
        // HCODE 更正記號 DECIMAL 1
        if (cldtlBus.getHcode() > 9) {
            errorMsg += ", HCODE 更正記號 DECIMAL 1 ,值為(" + cldtlBus.getHcode() + ") ";
            isError = true;
        }
        // SOURCETP 繳費來源通路 VARCHAR2 3
        if (!Objects.isNull(cldtlBus.getSourcetp()) && cldtlBus.getSourcetp().length() > 3) {
            errorMsg += ", SOURCETP 繳費來源通路 VARCHAR2 3 ,值為(" + cldtlBus.getSourcetp() + ") ";
            isError = true;
        }
        // CACTNO 對方帳號/對方單位 VARCHAR2 16
        if (!Objects.isNull(cldtlBus.getCactno()) && cldtlBus.getCactno().length() > 16) {
            errorMsg += ", CACTNO 對方帳號/對方單位 VARCHAR2 16 ,值為(" + cldtlBus.getCactno() + ") ";
            isError = true;
        }
        // HTXSEQ 更正交易序號 DECIMAL 15
        if (cldtlBus.getHtxseq() > 999999999999999L) {
            errorMsg += ", HTXSEQ 更正交易序號 DECIMAL 15 ,值為(" + cldtlBus.getHtxseq() + ") ";
            isError = true;
        }
        // EMPNO 員工編號 VARCHAR2 6
        if (!Objects.isNull(cldtlBus.getEmpno()) && cldtlBus.getEmpno().length() > 6) {
            errorMsg += ", EMPNO 員工編號 VARCHAR2 6 ,值為(" + cldtlBus.getEmpno() + ") ";
            isError = true;
        }
        // PUTFG 挑檔記號 DECIMAL 1
        if (cldtlBus.getPutfg() > 9) {
            errorMsg += ", PUTFG 挑檔記號 DECIMAL 1 ,值為(" + cldtlBus.getPutfg() + ") ";
            isError = true;
        }
        // ENTFG 外部代收入帳記號 DECIMAL 1
        if (cldtlBus.getEntfg() > 9) {
            errorMsg += ", ENTFG 外部代收入帳記號 DECIMAL 1 ,值為(" + cldtlBus.getEntfg() + ") ";
            isError = true;
        }
        // SOURCEIP 來源IP VARCHAR2 40
        if (!Objects.isNull(cldtlBus.getSourceip()) && cldtlBus.getSourceip().length() > 40) {
            errorMsg += ", SOURCEIP 來源IP VARCHAR2 40 ,值為(" + cldtlBus.getSourceip() + ") ";
            isError = true;
        }
        // UPLFILE 上傳檔名 VARCHAR2 40
        if (!Objects.isNull(cldtlBus.getUplfile()) && cldtlBus.getUplfile().length() > 40) {
            errorMsg += ", UPLFILE 上傳檔名 VARCHAR2 40 ,值為(" + cldtlBus.getUplfile() + ") ";
            isError = true;
        }
        // PBRNO 主辦行 DECIMAL 3
        if (cldtlBus.getPbrno() > 999) {
            errorMsg += ", PBRNO 主辦行 DECIMAL 3 ,值為(" + cldtlBus.getPbrno() + ") ";
            isError = true;
        }
        // CFEE2 收付行手續費 DECIMAL 5 2
        if (cldtlBus.getCfee2().compareTo(new BigDecimal("999.99")) > 0) {
            errorMsg += ", CFEE2 收付行手續費 DECIMAL 5 2 ,值為(" + cldtlBus.getCfee2() + ") ";
            isError = true;
        }
        // FKD 外部收付-手續費種類 DECIMAL 1
        if (cldtlBus.getFkd() > 9) {
            errorMsg += ", FKD 外部收付-手續費種類 DECIMAL 1 ,值為(" + cldtlBus.getFkd() + ") ";
            isError = true;
        }
        // FEE 外部收付-主辦行手續費   DECIMAL 5 2
        if (cldtlBus.getFee().compareTo(new BigDecimal("999.99")) > 0) {
            errorMsg += ", FEE 外部收付-主辦行手續費   DECIMAL 5 2 ,值為(" + cldtlBus.getFee() + ") ";
            isError = true;
        }
        // FEECOST 外部收付-收付通道手續費 DECIMAL 5 2
        if (cldtlBus.getFeecost().compareTo(new BigDecimal("999.99")) > 0) {
            errorMsg += ", FEECOST 外部收付-收付通道手續費 DECIMAL 5 2 ,值為(" + cldtlBus.getFeecost() + ") ";
            isError = true;
        }
        return isError ? errorMsg : "";
    }

    private void doUpdateCldtl(
            List<Map<String, Map<String, String>>> updateList, TransactionCase batchTransaction) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "doUpdateCldtl");
        int errorCount = 0;
        int successCount = 0;
        int updateBatchCount = 0;
        List<CldtlBus> updateBatchList = new ArrayList<>();
        for (Map<String, Map<String, String>> um : updateList) {
            Map<String, String> updateKey = um.get("UPDATE_KEY");
            Map<String, String> m = um.get("UPDATE_VALUE");
            // PK
            CldtlId cldtlId = getCldtlId(updateKey);
            CldtlBus cldtlBus = cldtlService.findById(cldtlId);
            if (Objects.isNull(cldtlBus)) {
                modifyRecordUtil.addRecord("Update Skip, cldtlBus 不存在,cldtlId=" + cldtlId);
                errorCount++;
                continue;
            }
            if (m.containsKey("AMT")) {
                cldtlBus.setAmt(diffParse.toBigDecimal(m.get("AMT")));
            }
            if (m.containsKey("TXTYPE")) {
                cldtlBus.setTxtype(m.get("TXTYPE"));
            }
            if (m.containsKey("CLLBR")) {
                cldtlBus.setCllbr(diffParse.toInt(m.get("CLLBR")));
            }
            if (m.containsKey("TLRNO")) {
                cldtlBus.setTlrno(m.get("TLRNO"));
            }
            if (m.containsKey("TIME")) {
                cldtlBus.setTime(diffParse.toInt(m.get("TIME")));
            }
            if (m.containsKey("LMTDATE")) {
                cldtlBus.setLmtdate(diffParse.toInt(m.get("LMTDATE")));
            }
            if (m.containsKey("USERDATA")) {
                cldtlBus.setUserdata(m.get("USERDATA"));
            }
            if (m.containsKey("SITDATE")) {
                cldtlBus.setSitdate(diffParse.toInt(m.get("SITDATE")));
            }
            if (m.containsKey("CALDY")) {
                cldtlBus.setCaldy(diffParse.toInt(m.get("CALDY")));
            }
            if (m.containsKey("ACTNO")) {
                cldtlBus.setActno(m.get("ACTNO"));
            }
            if (m.containsKey("SERINO")) {
                cldtlBus.setSerino(diffParse.toInt(m.get("SERINO")));
            }
            if (m.containsKey("CRDB")) {
                cldtlBus.setCrdb(diffParse.toInt(m.get("CRDB")));
            }

            // 特殊處理
            cldtlBus.setCurcd(diffParse.toInt(m.get("CURCD")));
            cldtlBus.setHcode(diffParse.toInt(m.get("HCODE")));
            cldtlBus.setSourcetp(m.get("SOURCETP"));
            cldtlBus.setCactno(m.get("CACTNO"));
            cldtlBus.setHtxseq(diffParse.toLong(m.get("HTXSEQ")));
            cldtlBus.setEmpno(m.get("EMPNO"));
            cldtlBus.setPutfg(diffParse.toInt(m.get("PUTFG")));
            cldtlBus.setEntfg(diffParse.toInt(m.get("ENTFG")));
            cldtlBus.setSourceip(m.get("SOURCEIP"));
            cldtlBus.setUplfile(m.get("UPLFILE"));
            cldtlBus.setPbrno(diffParse.toInt(m.get("PBRNO")));
            cldtlBus.setCfee2(diffParse.toBigDecimal(m.get("CFEE2")));
            cldtlBus.setFkd(diffParse.toInt(m.get("FKD")));
            cldtlBus.setFee(diffParse.toBigDecimal(m.get("FEE")));
            cldtlBus.setFeecost(diffParse.toBigDecimal(m.get("FEECOST")));

            String checkResult = checkColumns(cldtlBus);
            if (!checkResult.isBlank()) {
                modifyRecordUtil.addRecord(checkResult + "," + m);
                continue;
            }

            updateBatchList.add(cldtlBus);
            updateBatchCount++;
            if (updateBatchCount >= MODIFY_BATCH_SIZE) {
                try {
                    cldtlService.updateAllSilent(updateBatchList);
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
                    errorDataCom.writeTempErrorData("CLDTL", updateBatchList); // 紀錄失敗資料結束後一筆一筆寫入
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
                cldtlService.updateAllSilent(updateBatchList);
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
                errorDataCom.writeTempErrorData("CLDTL", updateBatchList); // 紀錄失敗資料結束後一筆一筆寫入
                errorCount++;
                updateBatchCount = 0;
                batchTransaction.rollBack();
            }
            successCount += updateBatchCount;
            startTime = new Date();
            updateBatchList.clear();
        }
        errorDataCom.readTmpErrorFile(2, "CLDTL", batchTransaction); // 一筆一筆寫入失敗資料,若還是失敗則出ErrorCLDTL
        modifyRecordUtil.addRecord(
                "Update size = "
                        + updateList.size()
                        + " ,success = "
                        + successCount
                        + " ,error = "
                        + errorCount);
    }

    private void doDeleteCldtl(
            List<Map<String, String>> deleteList, TransactionCase batchTransaction) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "doDeleteCldtl");
        int errorCount = 0;
        int successCount = 0;
        int deleteBatchCount = 0;
        List<CldtlBus> deleteBatchList = new ArrayList<>();
        for (Map<String, String> m : deleteList) {
            // PK
            CldtlId cldtlId = getCldtlId(m);
            CldtlBus cldtlBus = cldtlService.findById(cldtlId);
            if (Objects.isNull(cldtlBus)) {
                modifyRecordUtil.addRecord("Delete Skip, cldtlBus 不存在,cldtlId=" + cldtlId);
                errorCount++;
                continue;
            }
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "cldtlBus={}", cldtlBus);
            deleteBatchList.add(cldtlBus);
            deleteBatchCount++;
            if (deleteBatchCount >= MODIFY_BATCH_SIZE) {
                try {
                    cldtlService.deleteAllSilent(deleteBatchList);
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
                    errorDataCom.writeTempErrorData("CLDTL", deleteBatchList); // 紀錄失敗資料結束後一筆一筆寫入
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
                cldtlService.deleteAllSilent(deleteBatchList);
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
                errorDataCom.writeTempErrorData("CLDTL", deleteBatchList); // 紀錄失敗資料結束後一筆一筆寫入
                errorCount++;
                deleteBatchCount = 0;
                batchTransaction.rollBack();
            }
            successCount += deleteBatchCount;
            startTime = new Date();
            deleteBatchList.clear();
        }
        errorDataCom.readTmpErrorFile(4, "CLDTL", batchTransaction); // 一筆一筆寫入失敗資料,若還是失敗則出ErrorCLDTL
        modifyRecordUtil.addRecord(
                "Delete size = "
                        + deleteList.size()
                        + " ,success = "
                        + successCount
                        + " ,error = "
                        + errorCount);
    }

    private CldtlId getCldtlId(Map<String, String> m) {
        // CODE+RCPTID+ENTDY+TRMNO+TXTNO
        String code = m.get("CODE");
        String rcptid = m.get("RCPTID");
        int entdy = diffParse.toInt(m.get("ENTDY"));
        int trmno = diffParse.toInt(m.get("TRMNO"));
        int txtno = diffParse.toInt(m.get("TXTNO"));
        return new CldtlId(code, rcptid, entdy, trmno, txtno);
    }
}
