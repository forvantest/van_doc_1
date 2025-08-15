/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import static java.math.BigDecimal.ZERO;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONV366AE9;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FilePUTFN;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.buffer.mg.ThreadVariable;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.eum.TxCharsets;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.text.format.Text2VoFormatter;
import com.bot.txcontrol.util.transmit.FsapSyncSftpService;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("CONV366AE9Lsnr")
@Scope("prototype")
public class CONV366AE9Lsnr extends BatchListenerCase<CONV366AE9> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FilePUTFN filePutfn;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    private CONV366AE9 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CONVF_PATH_PUTFN = "PUTFN";
    private static final String CONVF_PATH_CL004 = "CL004";
    private static final String CONVF_PATH_005 = "005";
    private static final String CONVF_PATH_27C4366AE9 = "27C4366AE9";
    private static final String STRING_FCL004030700 = "FCL004030700";
    private static final String STRING_FCL004030710 = "FCL004030710";
    private static final String STRING_FCL004030720 = "FCL004030720";
    private static final String PATH_SEPARATOR = File.separator;
    private static final String STRING_PUTFN_TAX_RECEIVE = "03070000";
    private static final String STRING_00000000000000000000 = "00000000000000000000";
    private static final String CONVF_DATA = "DATA";
    private static final DecimalFormat decimalFormat =
            new DecimalFormat("##,###,###,##0"); // 明細金額格式
    private DecimalFormat cntFormat = new DecimalFormat("##,###,##0"); // 筆數格式
    private String processDate;
    private String tbsdy;
    private String wkFsapYYYYMMDD;
    private String putfnRcptid;
    private String putfnCllbr;
    private String putfnUserdata;
    private String putfnTxtype;
    private String readFdPutfnPath;
    private String writeFdPutfntaxPath03070;
    private String writeFdPutfntaxPath03071;
    private String writeFdPutfntaxPath03072;
    private String putfnTaxSitdate;
    private String putfnTaxTxtype;
    private String putfnTaxPdata;
    private String putfnTaxActno;
    private String putfnTaxBafbank;
    private String putfnTaxUserdata;
    private String putfnTaxSend;
    private String putfnTaxSendDate;
    private String putfnTaxCode;
    private String putfnTaxType;
    private String putfnTaxBhno;
    private String putfnTaxSeqno;
    private String putfnTaxBank;
    private int putfnDate;
    private int putfnLmtdate;
    private int putfnSitdate;
    private int wkTotCnt = 0;
    private int wkSeqno = 0;
    private int wkCrchkdg = 0;
    private int putfnTaxCtl;
    private int putfnTaxTotCnt = 0;
    private BigDecimal putfnAmt = ZERO;
    private BigDecimal wkTotAmt = ZERO;
    private BigDecimal wkTempAmt = ZERO;
    private BigDecimal putfnTaxTotAmt = ZERO;
    private BigDecimal putfnTaxAmt = ZERO;
    private BigDecimal wkTempAmt1 = ZERO;
    private BigDecimal wkTempAmt2 = ZERO;
    private List<String> fileFdPutfntax03070Contents = new ArrayList<>();
    private List<String> fileFdPutfntax03071Contents = new ArrayList<>();
    private List<String> fileFdPutfntax03072Contents = new ArrayList<>();

    @Override
    public void onApplicationEvent(CONV366AE9 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV366AE9Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONV366AE9 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV366AE9Lsnr run()");

        init(event);

        chechFdPutfnDataExist();

        writeFile();

        checkPath();

        batchResponse();
    }

    private void init(CONV366AE9 event) {
        // 讀批次日期檔
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();

        // 設定作業日、設定檔名日期變數值
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        wkFsapYYYYMMDD = formatUtil.pad9(processDate, 8);
        String wkFdate = processDate.substring(1);
        String wkCdate = processDate.substring(1);

        wkSeqno = 10000000;
        putfnTaxActno = formatUtil.pad9("0", 16);
        putfnTaxBafbank = formatUtil.padX("", 3);
        putfnTaxUserdata = formatUtil.padX("", 20);
        putfnTaxPdata = formatUtil.padX("", 72);

        // 設定檔名變數,檔名
        String readFdDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_PUTFN
                        + PATH_SEPARATOR
                        + wkFdate;
        readFdPutfnPath = readFdDir + PATH_SEPARATOR + CONVF_PATH_27C4366AE9;
        textFile.deleteFile(readFdPutfnPath);
        String sourceFtpPath =
                "NCL"
                        + File.separator
                        + tbsdy
                        + File.separator
                        + "2FSAP"
                        + File.separator
                        + "DATA"
                        + File.separator
                        + CONVF_PATH_PUTFN
                        + File.separator
                        + CONVF_PATH_27C4366AE9; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, readFdDir);
        if (sourceFile != null) {
            readFdPutfnPath = getLocalPath(sourceFile);
        }

        writeFdPutfntaxPath03070 =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_CL004
                        + PATH_SEPARATOR
                        + CONVF_PATH_005
                        + PATH_SEPARATOR
                        + wkCdate
                        + PATH_SEPARATOR
                        + STRING_FCL004030700;

        writeFdPutfntaxPath03071 =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_CL004
                        + PATH_SEPARATOR
                        + CONVF_PATH_005
                        + PATH_SEPARATOR
                        + wkCdate
                        + PATH_SEPARATOR
                        + STRING_FCL004030710;

        writeFdPutfntaxPath03072 =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_CL004
                        + PATH_SEPARATOR
                        + CONVF_PATH_005
                        + PATH_SEPARATOR
                        + wkCdate
                        + PATH_SEPARATOR
                        + STRING_FCL004030720;
    }

    private void chechFdPutfnDataExist() {
        // FD-PUTFN檔案存在，執行PUTFNTAX-RTN,否則執行NODATA-RTN
        if (textFile.exists(readFdPutfnPath)) {
            valuateCtl1();
            writeCtl1();
            readFdPutfnData();
            valuateCtl3();
            writeCtl3();
        } else {
            valuateCtl1();
            writeCtl1();
        }
    }

    private void valuateCtl1() {
        putfnTaxCtl = 1;
        putfnTaxSend = "004";
        putfnTaxSendDate = processDate;
        putfnTaxCode = STRING_FCL004030700.substring(7, 12);
        putfnTaxType = "1";
        putfnTaxBhno = "01";
    }

    private void writeCtl1() {
        StringBuilder sbCtl1 = new StringBuilder();
        for (int wkCode = 0; wkCode < 3; wkCode++) {
            sbCtl1.append(formatUtil.pad9(parse.decimal2String(putfnTaxCtl, 1, 0), 1));
            sbCtl1.append(formatUtil.padX(putfnTaxSend, 8));
            sbCtl1.append(formatUtil.padX(STRING_PUTFN_TAX_RECEIVE, 8));
            sbCtl1.append(formatUtil.pad9(putfnTaxSendDate, 7));
            if (wkCode == 1) {
                putfnTaxCode = STRING_FCL004030710.substring(7, 12);
            } else if (wkCode == 2) {
                putfnTaxCode = STRING_FCL004030720.substring(7, 12);
            }
            sbCtl1.append(formatUtil.padX(putfnTaxCode, 5));
            sbCtl1.append(formatUtil.pad9(putfnTaxType, 1));
            sbCtl1.append(formatUtil.pad9(putfnTaxBhno, 2));
            sbCtl1.append(formatUtil.padX("", 148));
            if (wkCode == 0) {
                fileFdPutfntax03070Contents.add(formatUtil.padX(sbCtl1.toString(), 180));
            } else if (wkCode == 1) {
                fileFdPutfntax03071Contents.add(formatUtil.padX(sbCtl1.toString(), 180));
            } else if (wkCode == 2) {
                fileFdPutfntax03072Contents.add(formatUtil.padX(sbCtl1.toString(), 180));
            }
        }
    }

    private void readFdPutfnData() {
        List<String> lines = textFile.readFileContent(readFdPutfnPath, CHARSET_UTF8);
        for (String detail : lines) {
            ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境
            text2VoFormatter.format(detail, filePutfn);

            int putfnCtl =
                    parse.string2Integer(
                            parse.isNumeric(filePutfn.getCtl()) ? filePutfn.getCtl() : "0");

            // IF PUTFN-CTL NOT = 11,跳下一筆
            // 否則搬PUTFN...到PUTFNTAX-REC(CTL=2)
            if (putfnCtl == 11) {
                putfnRcptid = filePutfn.getRcptid();
                putfnDate =
                        parse.string2Integer(
                                parse.isNumeric(filePutfn.getEntdy()) ? filePutfn.getEntdy() : "0");
                putfnCllbr = filePutfn.getCllbr();
                putfnLmtdate =
                        parse.string2Integer(
                                (parse.isNumeric(filePutfn.getLmtdate())
                                        ? filePutfn.getLmtdate()
                                        : "0"));
                putfnUserdata = filePutfn.getUserdata();
                putfnSitdate =
                        parse.string2Integer(
                                (parse.isNumeric(filePutfn.getSitdate())
                                        ? filePutfn.getSitdate()
                                        : "0"));
                putfnTxtype = filePutfn.getTxtype();
                putfnAmt =
                        parse.string2BigDecimal(
                                (parse.isNumeric(filePutfn.getAmt()) ? filePutfn.getAmt() : "0"));

                wkSeqno++;
                valuateCtl2();
                writeCtl2();
            }
        }
    }

    private void valuateCtl2() {
        putfnTaxCtl = 2;
        putfnTaxCode = STRING_FCL004030700.substring(7, 12);
        putfnTaxSeqno = parse.decimal2String(wkSeqno, 10, 0);
        putfnTaxAmt = putfnAmt;
        valuateSitdate();
        valuateTxtype();
        cntCRCHKDG();
        putfnTaxBank = "004" + putfnCllbr + wkCrchkdg;
    }

    private void writeCtl2() {
        StringBuilder sbCtl2 = new StringBuilder();
        for (int wkCode = 0; wkCode < 3; wkCode++) {
            sbCtl2.append(formatUtil.pad9(parse.decimal2String(putfnTaxCtl, 1, 0), 1));
            sbCtl2.append(formatUtil.padX(putfnTaxSend, 8));
            sbCtl2.append(formatUtil.padX(STRING_PUTFN_TAX_RECEIVE, 8));
            sbCtl2.append(formatUtil.pad9(putfnTaxSendDate, 7));
            if (wkCode == 1) {
                putfnTaxCode = STRING_FCL004030710.substring(7, 12);
            } else if (wkCode == 2) {
                putfnTaxCode = STRING_FCL004030720.substring(7, 12);
            }
            sbCtl2.append(formatUtil.padX(putfnTaxCode, 5));
            if (wkCode == 0) {
                if (parse.isNumeric(putfnUserdata.substring(0, 20))) {
                    continue;
                }
                valuatePdata03070();
            } else if (wkCode == 1) {
                if (!parse.isNumeric(putfnUserdata.substring(0, 20))
                        || !putfnRcptid.substring(20, 22).isBlank()) {
                    continue;
                }
                valuatePdata03071();
            } else if (wkCode == 2) {
                if (!parse.isNumeric(putfnUserdata.substring(0, 20))
                        || !putfnRcptid.substring(20, 22).isBlank()) {
                    continue;
                }
                valuatePdata03072();
            }
            sbCtl2.append(formatUtil.padX(putfnTaxSeqno, 10));
            sbCtl2.append(formatUtil.pad9(decimalFormat.format(putfnTaxAmt), 12));
            sbCtl2.append(formatUtil.pad9(putfnTaxSitdate, 7));
            sbCtl2.append(formatUtil.pad9(putfnTaxTxtype, 2));
            sbCtl2.append(formatUtil.padX(putfnTaxBank, 8));
            sbCtl2.append(formatUtil.pad9(putfnTaxActno, 16));
            sbCtl2.append(formatUtil.padX(putfnTaxBafbank, 3));
            sbCtl2.append(formatUtil.padX(putfnTaxUserdata, 20));
            sbCtl2.append(formatUtil.padX(putfnTaxPdata, 72));
            if (wkCode == 0) {
                fileFdPutfntax03070Contents.add(formatUtil.padX(sbCtl2.toString(), 180));
            } else if (wkCode == 1) {
                fileFdPutfntax03071Contents.add(formatUtil.padX(sbCtl2.toString(), 180));
            } else if (wkCode == 2) {
                fileFdPutfntax03072Contents.add(formatUtil.padX(sbCtl2.toString(), 180));
            }
        }
    }

    private void valuateSitdate() {
        if (putfnTxtype.equals("C") && putfnSitdate > 0) {
            putfnTaxSitdate = parse.decimal2String(putfnSitdate, 7, 0);
        } else {
            putfnTaxSitdate = parse.decimal2String(putfnDate, 7, 0);
        }
    }

    private void valuateTxtype() {
        if (putfnSitdate < putfnDate) {
            putfnTaxTxtype = "09";
        } else {
            putfnTaxTxtype = "02";
        }
    }

    private void cntCRCHKDG() {
        int wkRscnt1 = parse.string2Integer(putfnCllbr.substring(0, 1)) * 3;
        int wkRscnt2 = parse.string2Integer(putfnCllbr.substring(1, 2)) * 7;
        int wkRscnt3 = parse.string2Integer(putfnCllbr.substring(2, 3)) * 9;
        int rstotal = wkRscnt1 + wkRscnt2 + wkRscnt3 + 36;
        wkCrchkdg = 10 - (rstotal % 10);
    }

    private void valuatePdata03070() {
        putfnTaxPdata =
                parse.decimal2String(putfnLmtdate, 8, 0).substring(2, 8)
                        + putfnUserdata.substring(15, 18)
                        + putfnRcptid.substring(0, 19)
                        + putfnUserdata.substring(0, 15)
                        + putfnTaxPdata.substring(43, 72);

        valuateOverdue();
        valuateTaxState();
        chkTxtype03070();

        wkTotAmt = wkTotAmt.add(putfnAmt);
        wkTotCnt++;
        wkSeqno++;
        putfnTaxSeqno = parse.decimal2String(wkSeqno, 10, 0);
    }

    private void valuateOverdue() {
        if (!putfnUserdata.startsWith(STRING_00000000000000000000, 19)) {
            putfnTaxPdata =
                    putfnTaxPdata.substring(0, 44)
                            + "1"
                            + putfnUserdata.substring(19, 29)
                            + putfnUserdata.substring(29, 39)
                            + putfnTaxPdata.substring(65, 72);
        } else {
            putfnTaxPdata = putfnTaxPdata.substring(0, 44) + "0" + putfnTaxPdata.substring(45, 72);
        }
    }

    private void valuateTaxState() {
        if (putfnUserdata.startsWith("00", 38)) {
            putfnTaxPdata = putfnTaxPdata.substring(0, 44) + "4" + putfnTaxPdata.substring(45, 72);
        }
    }

    private void chkTxtype03070() {
        if (putfnTaxTxtype.equals("09")) {
            putfnTaxPdata =
                    putfnTaxPdata.substring(0, 64)
                            + parse.decimal2String(putfnSitdate, 8, 0).substring(1, 8)
                            + putfnTaxPdata.substring(71, 72);
        }
    }

    private void valuatePdata03071() {
        putfnTaxPdata = putfnRcptid.substring(0, 16) + putfnTaxPdata.substring(16, 72);
        wkTempAmt = putfnAmt;

        resetTaxAmt03071();

        putfnTaxPdata =
                putfnTaxPdata.substring(0, 16)
                        + parse.decimal2String(wkTempAmt, 10, 0)
                        + putfnTaxPdata.substring(26, 72);

        checkTxtype03071();

        valuateNationalTax03071();

        wkSeqno++;
        putfnTaxSeqno = parse.decimal2String(wkSeqno, 10, 0);
        wkTotAmt = wkTotAmt.add(putfnAmt);
        wkTotCnt++;
    }

    private void resetTaxAmt03071() {
        if (!putfnUserdata.startsWith(STRING_00000000000000000000)) {
            putfnTaxPdata =
                    putfnTaxPdata.substring(0, 26)
                            + "1"
                            + putfnUserdata.substring(0, 20)
                            + putfnTaxPdata.substring(47, 72);
            wkTempAmt1 = parse.string2BigDecimal(putfnUserdata.substring(0, 10));
            wkTempAmt2 = parse.string2BigDecimal(putfnUserdata.substring(10, 20));
            wkTotAmt = wkTempAmt.subtract(wkTempAmt1).subtract(wkTempAmt2);
        } else {
            putfnTaxPdata = putfnTaxPdata.substring(0, 26) + "0" + putfnTaxPdata.substring(27, 72);
        }
    }

    private void checkTxtype03071() {
        if (putfnTaxTxtype.equals("09")) {
            putfnTaxPdata =
                    putfnTaxPdata.substring(0, 47)
                            + parse.decimal2String(putfnSitdate, 8, 0).substring(1, 8)
                            + putfnTaxPdata.substring(54, 72);
        }
    }

    private void valuateNationalTax03071() {
        Set<String> wkTax =
                Set.of("15", "25", "35", "40", "45", "49", "75", "79", "83", "84", "43", "86");
        if (wkTax.contains(putfnTaxPdata.substring(3, 5))) {
            putfnTaxPdata = putfnTaxPdata.substring(0, 54) + "1" + putfnTaxPdata.substring(55, 72);
        } else {
            putfnTaxPdata = putfnTaxPdata.substring(0, 54) + "2" + putfnTaxPdata.substring(55, 72);
        }
    }

    private void valuatePdata03072() {
        putfnTaxPdata = putfnRcptid.substring(0, 24) + putfnTaxPdata.substring(24, 72);
        wkTempAmt = putfnAmt;

        resetTaxAmt03072();

        putfnTaxPdata =
                putfnTaxPdata.substring(0, 24)
                        + parse.decimal2String(wkTempAmt, 10, 0)
                        + putfnTaxPdata.substring(34, 72);

        checkTxtype03072();

        valuateNationalTax03072();

        wkSeqno++;
        putfnTaxSeqno = parse.decimal2String(wkSeqno, 10, 0);
        wkTotAmt = wkTotAmt.add(putfnAmt);
        wkTotCnt++;
    }

    private void resetTaxAmt03072() {
        if (!putfnUserdata.startsWith(STRING_00000000000000000000)) {
            putfnTaxPdata =
                    putfnTaxPdata.substring(0, 34)
                            + "1"
                            + putfnUserdata.substring(0, 20)
                            + putfnTaxPdata.substring(55, 72);
            wkTempAmt1 = parse.string2BigDecimal(putfnUserdata.substring(0, 10));
            wkTempAmt2 = parse.string2BigDecimal(putfnUserdata.substring(10, 20));
            wkTempAmt = wkTempAmt.subtract(wkTempAmt1).subtract(wkTempAmt2);
        } else {
            putfnTaxPdata = putfnTaxPdata.substring(0, 34) + "1" + putfnTaxPdata.substring(35, 72);
        }
    }

    private void checkTxtype03072() {
        if (putfnTaxTxtype.equals("09")) {
            putfnTaxPdata =
                    putfnTaxPdata.substring(0, 55)
                            + parse.decimal2String(putfnSitdate, 8, 0).substring(1, 8)
                            + putfnTaxPdata.substring(62, 72);
        }
    }

    private void valuateNationalTax03072() {
        Set<String> wkTax =
                Set.of("15", "35", "40", "45", "43", "49", "75", "79", "83", "84", "86", "25");
        if (wkTax.contains(putfnTaxPdata.substring(5, 7))) {
            putfnTaxPdata = putfnTaxPdata.substring(0, 62) + "1" + putfnTaxPdata.substring(63, 72);
        } else {
            putfnTaxPdata = putfnTaxPdata.substring(0, 62) + "2" + putfnTaxPdata.substring(63, 72);
        }
    }

    private void valuateCtl3() {
        putfnTaxCtl = 3;
        putfnTaxTotCnt = wkTotCnt;
        putfnTaxTotAmt = wkTotAmt;
    }

    private void writeCtl3() {
        StringBuilder sbCtl3 = new StringBuilder();
        for (int wkCode = 0; wkCode < 3; wkCode++) {
            sbCtl3.append(formatUtil.pad9(parse.decimal2String(putfnTaxCtl, 1, 0), 1));
            sbCtl3.append(formatUtil.padX(putfnTaxSend, 8));
            sbCtl3.append(formatUtil.padX(STRING_PUTFN_TAX_RECEIVE, 8));
            sbCtl3.append(formatUtil.pad9(putfnTaxSendDate, 7));
            if (wkCode == 1) {
                putfnTaxCode = "03071";
            } else if (wkCode == 2) {
                putfnTaxCode = "03072";
            }
            sbCtl3.append(formatUtil.padX(putfnTaxCode, 5));
            sbCtl3.append(formatUtil.pad9(decimalFormat.format(putfnTaxTotAmt), 14));
            sbCtl3.append(formatUtil.pad9(cntFormat.format(putfnTaxTotCnt), 10));
            sbCtl3.append(formatUtil.padX("", 125));
            if (wkCode == 0) {
                fileFdPutfntax03070Contents.add(formatUtil.padX(sbCtl3.toString(), 180));
            } else if (wkCode == 1) {
                fileFdPutfntax03071Contents.add(formatUtil.padX(sbCtl3.toString(), 180));
            } else if (wkCode == 2) {
                fileFdPutfntax03072Contents.add(formatUtil.padX(sbCtl3.toString(), 180));
            }
        }
    }

    private void writeFile() {
        try {
            textFile.deleteFile(writeFdPutfntaxPath03070);
            textFile.deleteFile(writeFdPutfntaxPath03071);
            textFile.deleteFile(writeFdPutfntaxPath03072);
            textFile.writeFileContent(
                    writeFdPutfntaxPath03070, fileFdPutfntax03070Contents, CHARSET_UTF8);
            upload(writeFdPutfntaxPath03070, "DATA", "");
            textFile.writeFileContent(
                    writeFdPutfntaxPath03071, fileFdPutfntax03071Contents, CHARSET_UTF8);
            upload(writeFdPutfntaxPath03071, "DATA", "");
            textFile.writeFileContent(
                    writeFdPutfntaxPath03072, fileFdPutfntax03072Contents, CHARSET_UTF8);
            upload(writeFdPutfntaxPath03072, "DATA", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void moveErrorResponse(LogicException e) {
        // event.setPeripheryRequest();
    }

    private void upload(String filePath, String directory1, String directory2) {
        try {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "upload = {}", filePath);
            Path path = Paths.get(filePath);
            File file = path.toFile();
            String uploadPath = File.separator + tbsdy + File.separator + "2FSAP";
            if (!directory1.isEmpty()) {
                uploadPath += File.separator + directory1;
            }
            if (!directory2.isEmpty()) {
                uploadPath += File.separator + directory2;
            }
            fsapSyncSftpService.uploadFile(file, uploadPath);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void checkPath() {
        if (textFile.exists(readFdPutfnPath)) {
            upload(readFdPutfnPath, "", "");
            //            forFsap();
        }
    }

    private void forFsap() {
        Map<String, String> result =
                fsapBatchUtil.processFile(
                        "003001", // 檔案批號(6碼長) ex.000001
                        "CL", // 業務大項,系統別(5碼長) ex.NCL
                        "CL", // 業務細項(10碼長) ex.PUTF
                        CONVF_PATH_27C4366AE9, // 來源檔案名稱(20碼長)
                        CONVF_PATH_27C4366AE9, // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        "CONV366AE9", // 檔案設定代號 ex:CONVF001
                        "CL", // (產品代碼,系統別)，ex.”NCL”
                        "", // NTFCLSID (分類代碼,notify版型), ex.”01”
                        "", // DLVCHNL (訊息發送類型)EMAIL:”T”,簡訊:”S”
                        "", // CNTADDR 收件者，多筆以半形分號(;)區隔
                        "", // NOTIFYTITLE (訊息標題)email主旨、若為簡訊此欄留空
                        "", // CONTENT (通知本文)email內文、簡訊內容
                        "NCL_CONVFILE"); // WFL
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RESULT = " + result);
    }

    private File downloadFromSftp(String fileFtpPath, String tarDir) {
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "downloadFromSftp fileFtpPath = {}",
                fileFtpPath);
        File file;
        try {
            file = fsapSyncSftpService.downloadFiles(fileFtpPath, tarDir);
        } catch (Exception e) {
            ApLogHelper.error(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "downloadFromSftp error = {}",
                    e.getMessage());
            return null;
        }
        return file;
    }

    private String getLocalPath(File file) {
        return Objects.isNull(file) ? "" : file.getAbsolutePath();
    }

    private void batchResponse() {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", null);
    }
}
