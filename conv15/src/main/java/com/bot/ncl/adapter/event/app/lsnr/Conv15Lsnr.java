/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import static java.math.BigDecimal.ZERO;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.Conv15;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FilePUTF;
import com.bot.ncl.util.fileVo.FileSumPUTF;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
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
@Component("Conv15Lsnr")
@Scope("prototype")
public class Conv15Lsnr extends BatchListenerCase<Conv15> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FilePUTF filePUTF;
    @Autowired private FileSumPUTF fileSumPUTF;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    private Conv15 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    // Define
    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CHARSET_Big5 = "Big5";
    private static final String CONVF_PATH_PUTF = "PUTF";
    private static final String CONVF_PATH_22C1111164 = "22C1111164";
    private static final String CONVF_PATH_22C6411117 = "22C6411117";
    private static final String CONVF_PATH_22C6411118 = "22C6411118";
    private static final String CONVF_PATH_22C6411119 = "22C6411119";
    private static final String CONVF_PATH_22C6411120 = "22C6411120";
    private static final String CONVF_PATH_034 = "CL-BH-034";
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private static final DecimalFormat decimalFormat = new DecimalFormat("##,###,###,##0");
    private static final DecimalFormat cntFormat = new DecimalFormat("##,###,##0");
    private static final String PATH_SEPARATOR = File.separator;
    private Map<String, String> labelMap;
    private Map<String, String> textMap;
    private String read22C1111164Path; // 聯合報
    private String read22C6411117Path; // 經濟日報
    private String read22C6411118Path; // 民生報
    private String read22C6411119Path; // 聯合晚報
    private String read22C6411120Path; // 星報
    private String writeReportPath;
    private String processDate;
    private String wkFsapYYYYMMDD;
    private String putfRcptid;
    private String putfCllbr;
    private String putfUserdata;
    private String putfTxtype;
    private String reportTxtype;
    private String wkCust;
    private int fnbsdy;
    private String tbsdy;
    private int putfCtl;
    private int putfDate;
    private int putfTime;
    private int putfLmtdate;
    private int putfOldamt;
    private int putfSitdate;
    private int putfBdate;
    private int putfEdate;
    private int cmCnt;
    private int vCnt;
    private int wCnt;
    private int iCnt;
    private int rCnt;
    private int wkEdiSelfCnt;
    private int wkEdiOtherCnt;
    private int wkAmtpcnt;
    private int wkInbkcnt;
    private int reportCnt;
    private int reportTotcnt;
    private int page;
    private int putfTotCnt = 0;
    private BigDecimal putfAmt = ZERO;
    private BigDecimal putfTotAmt = ZERO;
    private BigDecimal cmAmt = ZERO;
    private BigDecimal vAmt = ZERO;
    private BigDecimal wAmt = ZERO;
    private BigDecimal iAmt = ZERO;
    private BigDecimal rAmt = ZERO;
    private BigDecimal wkEdiSelfAmt = ZERO;
    private BigDecimal wkEdiOtherAmt = ZERO;
    private BigDecimal wkAmtpamt = ZERO;
    private BigDecimal wkInbkamt = ZERO;
    private BigDecimal reportAmt = ZERO;
    private BigDecimal reportFee = ZERO;
    private BigDecimal fee0 = ZERO;
    private BigDecimal feeUnit = BigDecimal.valueOf(5);
    private BigDecimal reportTotamt = ZERO;
    private BigDecimal reportTotfee = ZERO;
    private List<String> fileFd111164Contents = new ArrayList<>();
    private List<String> fileReportContents = new ArrayList<>();

    @Override
    public void onApplicationEvent(Conv15 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv15Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Conv15 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv15Lsnr run()");

        init(event);

        checkFnbsdyIsTbsdy();

        checkPath();

        batchResponse();
    }

    private void init(Conv15 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv15Lsnr init()");
        // 讀批次日期檔
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        // 設定作業日、檔名日期變數值
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        wkFsapYYYYMMDD = formatUtil.pad9(processDate, 8);
        fnbsdy = event.getAggregateBuffer().getTxCom().getFnbsdy();
        tbsdy = labelMap.get("PROCESS_DATE");
        String wkFdate = formatUtil.pad9(processDate, 7).substring(1, 7);

        // 設定檔名變數值
        // 設定檔名
        String readFdDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_PUTF
                        + PATH_SEPARATOR
                        + wkFdate;
        read22C1111164Path = readFdDir + PATH_SEPARATOR + CONVF_PATH_22C1111164;
        textFile.deleteFile(read22C1111164Path);
        String sourceFtpPath =
                "NCL"
                        + File.separator
                        + tbsdy
                        + File.separator
                        + "2FSAP"
                        + File.separator
                        + "DATA"
                        + File.separator
                        + CONVF_PATH_PUTF
                        + File.separator
                        + CONVF_PATH_22C1111164; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, readFdDir);
        if (sourceFile != null) {
            read22C1111164Path = getLocalPath(sourceFile);
        }

        readFdDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_PUTF
                        + PATH_SEPARATOR
                        + wkFdate;
        read22C6411117Path = readFdDir + PATH_SEPARATOR + CONVF_PATH_22C6411117;
        textFile.deleteFile(read22C6411117Path);
        sourceFtpPath =
                "NCL"
                        + File.separator
                        + tbsdy
                        + File.separator
                        + "2FSAP"
                        + File.separator
                        + "DATA"
                        + File.separator
                        + CONVF_PATH_PUTF
                        + File.separator
                        + CONVF_PATH_22C6411117; // 來源檔在FTP的位置
        sourceFile = downloadFromSftp(sourceFtpPath, readFdDir);
        if (sourceFile != null) {
            read22C6411117Path = getLocalPath(sourceFile);
        }
        readFdDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_PUTF
                        + PATH_SEPARATOR
                        + wkFdate;
        read22C6411118Path = readFdDir + PATH_SEPARATOR + CONVF_PATH_22C6411118;
        textFile.deleteFile(read22C6411118Path);
        sourceFtpPath =
                "NCL"
                        + File.separator
                        + tbsdy
                        + File.separator
                        + "2FSAP"
                        + File.separator
                        + "DATA"
                        + File.separator
                        + CONVF_PATH_PUTF
                        + File.separator
                        + CONVF_PATH_22C6411118; // 來源檔在FTP的位置
        sourceFile = downloadFromSftp(sourceFtpPath, readFdDir);
        if (sourceFile != null) {
            read22C6411118Path = getLocalPath(sourceFile);
        }
        readFdDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_PUTF
                        + PATH_SEPARATOR
                        + wkFdate;
        read22C6411119Path = readFdDir + PATH_SEPARATOR + CONVF_PATH_22C6411119;
        textFile.deleteFile(read22C6411119Path);
        sourceFtpPath =
                "NCL"
                        + File.separator
                        + tbsdy
                        + File.separator
                        + "2FSAP"
                        + File.separator
                        + "DATA"
                        + File.separator
                        + CONVF_PATH_PUTF
                        + File.separator
                        + CONVF_PATH_22C6411119; // 來源檔在FTP的位置
        sourceFile = downloadFromSftp(sourceFtpPath, readFdDir);
        if (sourceFile != null) {
            read22C6411119Path = getLocalPath(sourceFile);
        }
        readFdDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_PUTF
                        + PATH_SEPARATOR
                        + wkFdate;
        read22C6411120Path = readFdDir + PATH_SEPARATOR + CONVF_PATH_22C6411120;
        textFile.deleteFile(read22C6411120Path);
        sourceFtpPath =
                "NCL"
                        + File.separator
                        + tbsdy
                        + File.separator
                        + "2FSAP"
                        + File.separator
                        + "DATA"
                        + File.separator
                        + CONVF_PATH_PUTF
                        + File.separator
                        + CONVF_PATH_22C6411120; // 來源檔在FTP的位置
        sourceFile = downloadFromSftp(sourceFtpPath, readFdDir);
        if (sourceFile != null) {
            read22C6411120Path = getLocalPath(sourceFile);
        }

        writeReportPath =
                fileDir
                        + CONVF_RPT
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_034;
    }

    private void checkFnbsdyIsTbsdy() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv15Lsnr checkFnbsdyIsTbsdy()");
        if (fnbsdy == parse.string2Integer(processDate)) {
            checkPutfDataExist();

            writeFile();
        }
    }

    private void checkPutfDataExist() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv15Lsnr checkPutfDataExist()");
        if (textFile.exists(read22C1111164Path)) {
            wkCust = "聯合報";
            writeReportHeader();
            read22C1111164Data();
            writeReportFooter();
        } else if (textFile.exists(read22C6411117Path)) {
            wkCust = "經濟日報";
            writeReportHeader();
            read22C6411117Data();
            writeReportFooter();
        } else if (textFile.exists(read22C6411118Path)) {
            wkCust = "民生報";
            writeReportHeader();
            read22C6411118Data();
            writeReportFooter();
        } else if (textFile.exists(read22C6411119Path)) {
            wkCust = "聯合晚報";
            writeReportHeader();
            read22C6411119Data();
            writeReportFooter();
        } else if (textFile.exists(read22C6411120Path)) {
            wkCust = "星報";
            writeReportHeader();
            read22C6411120Data();
            writeReportFooter();
        }
    }

    private void read22C1111164Data() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv15Lsnr read22C1111164Data()");
        // 開啟檔案
        List<String> lines = textFile.readFileContent(read22C1111164Path, CHARSET_UTF8);
        for (String detail : lines) {
            ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境
            text2VoFormatter.format(detail, filePUTF);
            text2VoFormatter.format(detail, fileSumPUTF);

            putfCtl =
                    parse.string2Integer(
                            parse.isNumeric(filePUTF.getCtl()) ? filePUTF.getCtl() : "0");

            // 若PUTF-CTL = 11 OR = 12 銷帳明細、銷帳彙總資料
            //  A.搬PUTF-REC給111164-REC
            //  B.寫檔FD-111164
            if (putfCtl == 11) {
                readDataCtl11();
                writeFd111164();
            } else if (putfCtl == 12) {
                readDataCtl12();
                writeFd111164Final();
            } else if (putfCtl == 21) {
                valuateCntAmtByPutfTxtype();
                sumTotal();
                page++;
                writeReportContents();
            }
        }
    }

    private void read22C6411117Data() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv15Lsnr read22C6411117Data()");
        // 開啟檔案
        List<String> lines = textFile.readFileContent(read22C6411117Path, CHARSET_UTF8);
        for (String detail : lines) {
            ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境
            text2VoFormatter.format(detail, filePUTF);
            text2VoFormatter.format(detail, fileSumPUTF);

            putfCtl =
                    parse.string2Integer(
                            parse.isNumeric(filePUTF.getCtl()) ? filePUTF.getCtl() : "0");

            // 若PUTF-CTL = 11 OR = 12 銷帳明細、銷帳彙總資料
            //  A.搬PUTF-REC給111164-REC
            //  B.寫檔FD-111164
            if (putfCtl == 11) {
                readDataCtl11();
                writeFd111164();
            } else if (putfCtl == 12) {
                readDataCtl12();
                writeFd111164Final();
            } else if (putfCtl == 21) {
                valuateCntAmtByPutfTxtype();
                sumTotal();
                page++;
                writeReportContents();
            }
        }
    }

    private void read22C6411118Data() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv15Lsnr read22C6411118Data()");
        // 開啟檔案
        List<String> lines = textFile.readFileContent(read22C6411118Path, CHARSET_UTF8);
        for (String detail : lines) {
            ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境
            text2VoFormatter.format(detail, filePUTF);
            text2VoFormatter.format(detail, fileSumPUTF);

            putfCtl =
                    parse.string2Integer(
                            parse.isNumeric(filePUTF.getCtl()) ? filePUTF.getCtl() : "0");

            // 若PUTF-CTL = 11 OR = 12 銷帳明細、銷帳彙總資料
            //  A.搬PUTF-REC給111164-REC
            //  B.寫檔FD-111164
            if (putfCtl == 11) {
                readDataCtl11();
                writeFd111164();
            } else if (putfCtl == 12) {
                readDataCtl12();
                writeFd111164Final();
            } else if (putfCtl == 21) {
                valuateCntAmtByPutfTxtype();
                sumTotal();
                page++;
                writeReportContents();
            }
        }
    }

    private void read22C6411119Data() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv15Lsnr read22C6411119Data()");
        // 開啟檔案
        List<String> lines = textFile.readFileContent(read22C6411119Path, CHARSET_UTF8);
        for (String detail : lines) {
            ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境
            text2VoFormatter.format(detail, filePUTF);
            text2VoFormatter.format(detail, fileSumPUTF);

            putfCtl =
                    parse.string2Integer(
                            parse.isNumeric(filePUTF.getCtl()) ? filePUTF.getCtl() : "0");

            // 若PUTF-CTL = 11 OR = 12 銷帳明細、銷帳彙總資料
            //  A.搬PUTF-REC給111164-REC
            //  B.寫檔FD-111164
            if (putfCtl == 11) {
                readDataCtl11();
                writeFd111164();
            } else if (putfCtl == 12) {
                readDataCtl12();
                writeFd111164Final();
            } else if (putfCtl == 21) {
                valuateCntAmtByPutfTxtype();
                sumTotal();
                page++;
                writeReportContents();
            }
        }
    }

    private void read22C6411120Data() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv15Lsnr read22C6411120Data()");
        // 開啟檔案
        List<String> lines = textFile.readFileContent(read22C6411120Path, CHARSET_UTF8);
        for (String detail : lines) {
            ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境
            text2VoFormatter.format(detail, filePUTF);
            text2VoFormatter.format(detail, fileSumPUTF);

            putfCtl =
                    parse.string2Integer(
                            parse.isNumeric(filePUTF.getCtl()) ? filePUTF.getCtl() : "0");

            // 若PUTF-CTL = 11 OR = 12 銷帳明細、銷帳彙總資料
            //  A.搬PUTF-REC給111164-REC
            //  B.寫檔FD-111164
            if (putfCtl == 11) {
                readDataCtl11();
                writeFd111164();
            } else if (putfCtl == 12) {
                readDataCtl12();
                writeFd111164Final();
            } else if (putfCtl == 21) {
                valuateCntAmtByPutfTxtype();
                sumTotal();
                page++;
                writeReportContents();
            }
        }
    }

    private void readDataCtl11() {
        putfRcptid = filePUTF.getRcptid();
        putfDate =
                parse.string2Integer(
                        parse.isNumeric(filePUTF.getEntdy()) ? filePUTF.getEntdy() : "0");
        putfTime =
                parse.string2Integer(
                        parse.isNumeric(filePUTF.getTime()) ? filePUTF.getTime() : "0");
        putfCllbr = filePUTF.getCllbr();
        putfLmtdate =
                parse.string2Integer(
                        (parse.isNumeric(filePUTF.getLmtdate()) ? filePUTF.getLmtdate() : "0"));
        putfOldamt =
                parse.string2Integer(
                        (parse.isNumeric(filePUTF.getOldamt()) ? filePUTF.getOldamt() : "0"));
        putfUserdata = filePUTF.getUserdata();
        putfSitdate =
                parse.string2Integer(
                        (parse.isNumeric(filePUTF.getSitdate()) ? filePUTF.getSitdate() : "0"));
        putfTxtype = filePUTF.getTxtype();
        putfAmt =
                parse.string2BigDecimal(
                        (parse.isNumeric(filePUTF.getAmt()) ? filePUTF.getAmt() : "0"));
    }

    private void readDataCtl12() {
        putfBdate =
                parse.string2Integer(
                        (parse.isNumeric(fileSumPUTF.getBdate()) ? fileSumPUTF.getBdate() : "0"));
        putfEdate =
                parse.string2Integer(
                        (parse.isNumeric(fileSumPUTF.getEdate()) ? fileSumPUTF.getEdate() : "0"));
        putfTotCnt =
                parse.string2Integer(
                        (parse.isNumeric(fileSumPUTF.getTotcnt()) ? fileSumPUTF.getTotcnt() : "0"));
        putfTotAmt =
                parse.string2BigDecimal(
                        (parse.isNumeric(fileSumPUTF.getTotamt()) ? fileSumPUTF.getTotamt() : "0"));
    }

    private void writeFd111164() {
        StringBuilder sbFd = new StringBuilder();
        sbFd.append(formatUtil.pad9("" + putfCtl, 2));
        sbFd.append(formatUtil.padX("111164", 6));
        sbFd.append(formatUtil.padX(putfRcptid, 16));
        sbFd.append(formatUtil.pad9("" + putfDate, 6));
        sbFd.append(formatUtil.pad9("" + putfTime, 6));
        sbFd.append(formatUtil.pad9(putfCllbr, 3));
        sbFd.append(formatUtil.pad9("" + putfLmtdate, 6));
        sbFd.append(formatUtil.pad9("" + putfOldamt, 8));
        sbFd.append(formatUtil.padX(putfUserdata, 40));
        sbFd.append(formatUtil.pad9("" + putfSitdate, 6));
        sbFd.append(formatUtil.padX(putfTxtype, 1));
        sbFd.append(formatUtil.pad9(decimalFormat.format(putfAmt), 10));
        sbFd.append(formatUtil.padX("", 10));
        fileFd111164Contents.add(formatUtil.padX(sbFd.toString(), 120));
    }

    private void writeFd111164Final() {
        StringBuilder sbFF = new StringBuilder();
        sbFF.append(formatUtil.pad9("" + putfBdate, 6));
        sbFF.append(formatUtil.pad9("" + putfEdate, 6));
        sbFF.append(formatUtil.pad9(cntFormat.format(putfTotCnt), 6));
        sbFF.append(formatUtil.padX(decimalFormat.format(putfTotAmt), 13));
        sbFF.append(formatUtil.padX("", 81));
        fileFd111164Contents.add(formatUtil.padX(sbFF.toString(), 120));
    }

    private void valuateCntAmtByPutfTxtype() {
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "Conv15Lsnr valuateCntAmtByPutfTxtype()");
        Set<String> setWkAX = Set.of("A", "X");
        String wkTromach = putfUserdata.substring(19, 22);
        String wkTrobank = putfUserdata.substring(0, 3);
        if (putfTxtype.equals("C") || putfTxtype.equals("M")) {
            cmCnt++;
            cmAmt = cmAmt.add(putfAmt);
            reportTxtype = " 臨櫃 ";
            reportCnt = cmCnt;
            reportAmt = cmAmt;
            reportFee = fee0;
        } else if (putfTxtype.equals("V")) {
            vCnt++;
            vAmt = vAmt.add(putfAmt);
            reportTxtype = " 本行語音 ";
            reportCnt = vCnt;
            reportAmt = vAmt;
            reportFee = fee0;
        } else if (putfTxtype.equals("W")) {
            wCnt++;
            wAmt = wAmt.add(putfAmt);
            reportTxtype = " 本行行動銀行 ";
            reportCnt = wCnt;
            reportAmt = wAmt;
            reportFee = fee0;
        } else if (putfTxtype.equals("I")) {
            iCnt++;
            iAmt = iAmt.add(putfAmt);
            reportTxtype = " 本行網路銀行 ";
            reportCnt = iCnt;
            reportAmt = iAmt;
            reportFee = fee0;
        } else if (putfTxtype.equals("R")) {
            rCnt++;
            rAmt = rAmt.add(putfAmt);
            reportTxtype = " 匯款 ";
            reportCnt = rCnt;
            reportAmt = rAmt;
            reportFee = feeUnit.multiply(BigDecimal.valueOf(rCnt));
        } else if (putfTxtype.equals("E") && wkTrobank.equals("004")) {
            wkEdiSelfCnt++;
            wkEdiSelfAmt = wkEdiSelfAmt.add(putfAmt);
            reportTxtype = " ＥＤＩ－自行 ";
            reportCnt = wkEdiSelfCnt;
            reportAmt = wkEdiSelfAmt;
            reportFee = fee0;
        } else if (putfTxtype.equals("E")) {
            wkEdiOtherCnt++;
            wkEdiOtherAmt = wkEdiOtherAmt.add(putfAmt);
            reportTxtype = " ＥＤＩ－跨行 ";
            reportCnt = wkEdiOtherCnt;
            reportAmt = wkEdiOtherAmt;
            reportFee = feeUnit.multiply(BigDecimal.valueOf(wkEdiOtherCnt));
        } else if ((setWkAX.contains(putfTxtype))
                && wkTrobank.equals("004")
                && wkTromach.equals("004")) {
            wkAmtpcnt++;
            wkAmtpamt = wkAmtpamt.add(putfAmt);
            reportTxtype = " 自動櫃員機－自行 ";
            reportCnt = wkAmtpcnt;
            reportAmt = wkAmtpamt;
            reportFee = fee0;
        } else if (setWkAX.contains(putfTxtype)) {
            wkInbkcnt++;
            wkInbkamt = wkInbkamt.add(putfAmt);
            reportTxtype = " 自動櫃員機－跨行 ";
            reportCnt = wkInbkcnt;
            reportAmt = wkInbkamt;
            reportFee = feeUnit.multiply(BigDecimal.valueOf(wkInbkcnt));
        }
    }

    private void sumTotal() {
        reportTotcnt++;
        reportTotamt = reportTotamt.add(putfAmt);
        reportTotfee = reportTotfee.add(reportFee);
    }

    private void writeReportHeader() {
        StringBuilder sbH1 = new StringBuilder();
        sbH1.append(formatUtil.padX("", 26));
        sbH1.append(formatUtil.padX("  台灣銀行松山分行回饋手續費月報表 -", 36));
        sbH1.append(formatUtil.padX(wkCust, 10));
        sbH1.append(formatUtil.padX("", 5));
        sbH1.append(formatUtil.padX(" 頁次  :", 8));
        sbH1.append(formatUtil.pad9("" + page, 4));
        fileReportContents.add(formatUtil.padX(sbH1.toString(), 120));

        fileReportContents.add("");

        StringBuilder sbH2 = new StringBuilder();
        sbH2.append(formatUtil.padX(" 資料月份： ", 12));
        sbH2.append(
                formatUtil.padX(
                        processDate.substring(0, 3) + PATH_SEPARATOR + processDate.substring(3, 5),
                        6));
        sbH2.append(formatUtil.padX("", 60));
        sbH2.append(formatUtil.padX("FORM : C034 ", 12));
        fileReportContents.add(formatUtil.padX(sbH2.toString(), 120));

        StringBuilder sbH3 = new StringBuilder();
        sbH3.append(formatUtil.padX(" 分行別： ", 10));
        sbH3.append(formatUtil.padX("004", 3));
        sbH3.append(formatUtil.padX("", 55));
        sbH3.append(formatUtil.padX(" 印表日期：  ", 13));
        sbH3.append(
                formatUtil.padX(
                        processDate.substring(0, 3)
                                + PATH_SEPARATOR
                                + processDate.substring(3, 5)
                                + PATH_SEPARATOR
                                + processDate.substring(5, 7),
                        9));
        fileReportContents.add(formatUtil.padX(sbH3.toString(), 120));

        StringBuilder sbH4 = new StringBuilder();
        sbH4.append(formatUtil.padX("", 3));
        sbH4.append(formatUtil.padX(" 繳款方式 ", 10));
        sbH4.append(formatUtil.padX("", 16));
        sbH4.append(formatUtil.padX(" 代收筆數 ", 10));
        sbH4.append(formatUtil.padX("", 12));
        sbH4.append(formatUtil.padX(" 代收金額 ", 10));
        sbH4.append(formatUtil.padX("", 10));
        sbH4.append(formatUtil.padX(" 應回饋手續費 ", 14));
        fileReportContents.add(formatUtil.padX(sbH4.toString(), 120));

        StringBuilder sbH5 = new StringBuilder();
        sbH5.append(formatUtil.padX("", 3));
        sbH5.append(reportUtil.makeGate("-", 120));
        fileReportContents.add(formatUtil.padX(sbH5.toString(), 125));
    }

    private void writeReportContents() {
        StringBuilder sbC = new StringBuilder();
        sbC.append(formatUtil.padX("", 3));
        sbC.append(formatUtil.padX(reportTxtype, 20));
        sbC.append(formatUtil.padX("", 8));
        sbC.append(formatUtil.padX(cntFormat.format(reportCnt), 7));
        sbC.append(formatUtil.padX("", 8));
        sbC.append(formatUtil.padX(decimalFormat.format(reportAmt), 14));
        sbC.append(formatUtil.padX("", 17));
        sbC.append(formatUtil.padX(cntFormat.format(reportFee), 7));
        fileFd111164Contents.add(formatUtil.padX(sbC.toString(), 120));
    }

    private void writeReportFooter() {
        StringBuilder sbF1 = new StringBuilder();
        sbF1.append(formatUtil.padX("", 3));
        sbF1.append(reportUtil.makeGate("-", 120));
        fileReportContents.add(formatUtil.padX(sbF1.toString(), 125));

        StringBuilder sbF2 = new StringBuilder();
        sbF2.append(formatUtil.padX("", 3));
        sbF2.append(formatUtil.padX(" 合計 ", 8));
        sbF2.append(formatUtil.padX("", 20));
        sbF2.append(formatUtil.pad9(cntFormat.format(reportTotcnt), 7));
        sbF2.append(formatUtil.padX("", 8));
        sbF2.append(formatUtil.pad9(decimalFormat.format(reportTotamt), 14));
        sbF2.append(formatUtil.padX("", 17));
        sbF2.append(formatUtil.pad9(decimalFormat.format(reportTotfee), 7));
        fileReportContents.add(formatUtil.padX(sbF2.toString(), 120));
    }

    private void writeFile() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Conv15Lsnr writeFile()");
        try {
            //            textFile.deleteFile(read22C1111164Path);
            textFile.deleteFile(writeReportPath);
            //            textFile.writeFileContent(read22C1111164Path, fileFd111164Contents,
            // CHARSET_UTF8);
            textFile.writeFileContent(writeReportPath, fileReportContents, CHARSET_Big5);
            upload(writeReportPath, "RPT", "");
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
        if (textFile.exists(read22C1111164Path)) {
            upload(read22C1111164Path, "", "");
            forFsap();
        }
    }

    private void forFsap() {
        Map<String, String> result =
                fsapBatchUtil.processFile(
                        "003001", // 檔案批號(6碼長) ex.000001
                        "CL", // 業務大項,系統別(5碼長) ex.NCL
                        "CL", // 業務細項(10碼長) ex.PUTF
                        CONVF_PATH_22C1111164, // 來源檔案名稱(20碼長)
                        CONVF_PATH_22C1111164, // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        "NCL_CONV15", // 檔案設定代號 ex:CONVF001
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
        Map<String, String> responseTextMap = new HashMap<>(textMap);
        responseTextMap.put("RPTNAME", CONVF_PATH_034);

        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", responseTextMap);
    }
}
