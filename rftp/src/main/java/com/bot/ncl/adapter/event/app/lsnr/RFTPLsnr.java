/* (C) 2025 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.RFTP;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.ncl.util.string.StringUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.adapter.out.grpc.FsapSync;
import com.bot.txcontrol.buffer.TxBizDate;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.date.DateDto;
import com.bot.txcontrol.util.date.DateUtil;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.transmit.FsapSyncSftpService;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("RFTPLsnr")
@Scope("prototype")
public class RFTPLsnr extends BatchListenerCase<RFTP> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    @Autowired private DateUtil dateUtil;
    @Autowired private StringUtil stringUtil;
    @Autowired private FsapSync fsapSync;

    private DateDto dateDto = new DateDto();

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CHARSET_BIG5 = "Big5";
    private String PATH_SEPARATOR = File.separator;

    private RFTP event;
    private Map<String, String> labelMap;
    private Map<String, String> textMap;
    private String wkDate;
    private String wkTaskPutfile;
    private String wkTeldate;
    private String str = " 嘗試連結伺服器傳檔失敗 ";
    private String flag = "";
    private Boolean failure = false;
    private String fileIn;
    private String fileName;
    private String filePath_PUTF = "";
    private String filePath_PUTFN = "";
    private String file121454;
    private String nowDate;
    private String nowTime;

    private String code1;
    private String code2;
    private String code3;
    private String code;
    private String codehead;
    private int len;
    private String acount;
    private String acid;
    private String putname;
    private String nflg;
    private String time;
    private String mmdd;
    private String yyyymmdd;
    private String nyyyymmdd;
    private String yymmdd;
    private String yyymmdd;
    private String nyyymmdd;
    private String ctlStep;
    private String clDate;
    private String tbsdy;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(RFTP event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RFTPLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(RFTP event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RFTPLsnr run()");
        init(event);
        checkWflStep();
        batchResponse();
    }

    private void init(RFTP event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RFTPLsnr init");
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        ctlStep = labelMap.get("JOB");
        tbsdy = labelMap.get("PROCESS_DATE");
        clDate = labelMap.get("PROCESS_DATE").substring(2);
    }

    private void checkWflStep() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RFTPLsnr job = {}", ctlStep);
        switch (ctlStep) {
            case "C1530_S53_04" -> {
                wfl_rftp(clDate, "17X4115892", "");
                wfl_rftp(clDate, "17X4115902", "");
                wfl_rftp(clDate, "17X4115988", "");
                // 傳送勞保、勞退QRCODE的明細檔及彙計檔
                wfl_rftp(clDate, "17X411598T", "");
                wfl_rftp(clDate, "17X411589T", "");
                wfl_rftp(clDate, "17X411590T", "");
                fileName = "";
            }
            case "C1530_S85_10" -> {
                List<TxBizDate> txBizDates =
                        fsapSync.sy202ForAp(event.getPeripheryRequest(), tbsdy, tbsdy);
                String nbsdy = "";
                ApLogHelper.info(
                        log,
                        false,
                        LogType.NORMAL.getCode(),
                        "txBizDates{}",
                        txBizDates.get(0).isHliday());
                if (!Objects.isNull(txBizDates)) {
                    nbsdy = formatUtil.pad9("" + txBizDates.get(0).getNbsdy(), 8).substring(2);
                }
                // 360019&320019拆成臨櫃和網際網路兩個銷帳媒體檔寄送
                //      00072410
                wfl_rftp(clDate, "27X1360019TA", nbsdy);
                wfl_rftp(clDate, "27X1360019TI", nbsdy);
                // 510040分成舊格式及新格式兩個銷帳媒體檔傳送
                wfl_rftp(clDate, "07C1510040", clDate);
                wfl_rftp(clDate, "07X1510040", nbsdy);
                // 監理所檔案拆成區寄送
                wfl_rftp(clDate, "27X3121454_TPC", "");
                wfl_rftp(clDate, "27X3121454_SCH", "");
                wfl_rftp(clDate, "27X3121454_TXG", "");
                wfl_rftp(clDate, "27X3121454_CYI", "");
                wfl_rftp(clDate, "27X3121454_KHC", "");
            }
            default -> {
                ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "參數未設定 = {}", ctlStep);
            }
        }
    }

    private void wfl_rftp(String date, String putfile, String teldate) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RFTPLsnr 參數營業日 = {}", date);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RFTPLsnr 參數檔名 = {}", putfile);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RFTPLsnr 參數次營業日 = {}", teldate);
        wkDate = date;
        wkTaskPutfile = putfile;
        wkTeldate = teldate;
        //     CODE1   :=TAKE(DROP(PUTFILE,4),5);                                           00003400
        //     CODE2   :=TAKE(DROP(PUTFILE,5),4);                                           00003500
        //     CODE3   :=TAKE(DROP(PUTFILE,4),3);                                           00003550
        //     CODE    :=TAKE(DROP(PUTFILE,4),6);                                           00003600
        //     CODEHEAD:=TAKE(DROP(PUTFILE,4),1);                                           00003700
        //     LEN     :=LENGTH(PUTFILE);                                                   00003720

        wkDate = formatUtil.pad9(wkDate, 6);
        wkTaskPutfile = formatUtil.padX(wkTaskPutfile, 10);
        wkTeldate = formatUtil.pad9(wkTeldate, 8);

        code1 = stringUtil.substr(wkTaskPutfile, 4, 9);
        code2 = stringUtil.substr(wkTaskPutfile, 5, 9);
        code3 = stringUtil.substr(wkTaskPutfile, 4, 7);
        code = stringUtil.substr(wkTaskPutfile, 4, 10);
        codehead = stringUtil.substr(wkTaskPutfile, 4, 5);
        len = wkTaskPutfile.length();

        //     IF LEN  = 14 AND CODE = "121454" THEN                                        00003740
        //        BEGIN                                                                     00003742
        //          FILE121454:=TAKE(DROP(PUTFILE,10),4);                                   00003750
        //        END;                                                                      00003770
        if (len == 14 && "121454".equals(code)) {
            file121454 = stringUtil.substr(wkTaskPutfile, 10, 14);
        }
        //     ACOUNT  := CODE1 ;                                                           00003800
        //     PASSWD  := CODE1 ;                                                           00003900
        //     PUTNAME :=TAKE(DROP(PUTFILE,2),8);                                           00004000
        //     NFLG     := TAKE(DROP(PUTFILE,3),1);                                         00004100
        acount = code1;
        acid = code1;
        putname = stringUtil.substr(wkTaskPutfile, 2, 10);
        nflg = stringUtil.substr(wkTaskPutfile, 3, 4);
        //     IF DECIMAL(DATE) < 990101  THEN                                              00004200
        //      YYYMMDD:="1"&DATE                                                           00004300
        //     ELSE                                                                         00004400
        //      YYYMMDD:="0"&DATE;                                                          00004500
        int wkDateI = parse.isNumeric(wkDate) ? parse.string2Integer(wkDate) : 0;
        if (wkDateI < 990101) {
            yyymmdd = "1" + wkDate;
        } else {
            yyymmdd = "0" + wkDate;
        }
        //     IF DECIMAL(TELDATE)< 990101 THEN                                             00004600
        //       NYYYMMDD:="1"&TELDATE                                                      00004700
        //     ELSE                                                                         00004800
        //       NYYYMMDD:="0"&TELDATE;                                                     00004900
        int wkTeldateI = parse.isNumeric(wkTeldate) ? parse.string2Integer(wkTeldate) : 0;
        if (wkTeldateI < 990101) {
            nyyymmdd = "1" + wkTeldate;
        } else {
            nyyymmdd = "0" + wkTeldate;
        }
        nowDate = dateUtil.getNowStringBc();
        nowTime = dateUtil.getNowStringTime(false);
        //     TIME    :=TAKE(TIMEDATE(YYYYMMDDHHMMSS),12);                                 00005000
        //     MMDD     := TAKE(DROP(DATE,2),4);                                            00005100
        //     YYYYMMDD := STRING(DECIMAL(DATE)+20110000,8);                                00005200
        //     NYYYYMMDD:= STRING(DECIMAL(TELDATE)+20110000,8);                             00005220
        //     YYMMDD   := TAKE(DROP(STRING(DECIMAL(DATE)+20110000,8),2),6);                00005250
        time = nowDate + stringUtil.substr(nowTime, 0, 4);
        mmdd = stringUtil.substr(wkDate, 2, 6);
        yyyymmdd = "" + (wkDateI + 20110000);
        nyyyymmdd = "" + (wkTeldateI + 20110000);
        yymmdd = stringUtil.substr(yyyymmdd, 2, 8);
        // %--  空檔                                                                         00005410
        //     FILEEMPTY := "DATA/CL/BH/EMPTY";                                             00005415
        //     CLFILE(TITLE=#FILEEMPTY,                                                     00005420
        //            NEWFILE=TRUE,KIND=DISK,SECURITYTYPE=PUBLIC);                          00005425
        //     OPEN(CLFILE); CRUNCH(CLFILE);                                                00005430
        writeEmptyData("EMPTY");

        //     IF CODE    = "X1510040" AND TELDATE = "" THEN                                00005515
        //        BEGIN                                                                     00005520
        //          STR := " 錯誤：中華電信補傳需第三參數：次營業日 ";                      00005530
        //          GO  FAILURE;                                                            00005540
        //        END;                                                                      00005550
        if ("X1510040".equals(code) && wkTeldate.isEmpty()) {
            str = " 錯誤：中華電信補傳需第三參數：次營業日 ";
            failure = true;
            return;
        }
        //     IF PUTNAME = "X1360019" AND TELDATE = "" THEN                                00005600
        //        BEGIN                                                                     00005700
        //          STR := " 錯誤：中華電信補傳需第三參數：次營業日 ";                      00005800
        //          GO  FAILURE;                                                            00005900
        //        END;                                                                      00006000
        if ("X1360019".equals(putname) && wkTeldate.isEmpty()) {
            str = " 錯誤：中華電信補傳需第三參數：次營業日 ";
            failure = true;
            return;
        }

        //     IF PUTNAME = "X3350001" AND TELDATE = "" THEN                                00006200
        //        BEGIN                                                                     00006300
        //          STR := " 錯誤：台塑集團補傳需第三參數：次營業日 ";                      00006400
        //          GO  FAILURE;                                                            00006500
        //        END;                                                                      00006600
        if ("X3350001".equals(putname) && wkTeldate.isEmpty()) {
            str = " 錯誤：台塑集團補傳需第三參數：次營業日 ";
            failure = true;
            return;
        }
        //     IF  CODE NEQ "500360" AND ( CODEHEAD = "7" OR CODEHEAD = "5"                 00006900
        //      OR CODE = "510040" OR CODE = "158892" OR CODE = "420235"                    00007000
        //      OR CODE = "115892" OR CODE = "115902" OR CODE = "11591D"                    00007050
        //      OR CODE = "11589T" OR CODE = "11590T"                                       00007070
        //      OR CODE = "115988" OR CODE = "11598T"                                       00007090
        //      OR CODE = "350003" OR CODE = "36F209" OR CODE = "115154"                    00007100
        //      OR CODE = "111332" OR CODE = "158686" OR CODE = "139098"                    00007200
        //      OR CODE = "360019" OR CODE = "193016" OR CODE = "151742"                    00007300
        //      OR CODE = "158084" OR CODE = "158114" OR CODE = "159764"                    00007400
        //      OR CODE = "159773" OR CODE = "159783" OR CODE = "159790"                    00007500
        //      OR CODE = "159800" OR CODE = "159810" OR CODE = "159820"                    00007600
        //      OR CODE = "159830" OR CODE = "159840" OR CODE = "133288"                    00007700
        //      OR CODE = "198010" OR CODE = "198020" OR CODE = "198030"                    00007800
        //      OR CODE = "198040" OR CODE = "198050" OR CODE = "193040"                    00007900
        //      OR CODE = "350001" OR CODE = "36C179" OR CODE = "13512D"                    00008000
        //      OR CODE = "155928" OR CODE = "400212" OR CODE = "405212"                    00008050
        //      OR CODE3 = "118"   OR CODE = "121454" OR CODE = "191904"                    00008070
        //      OR CODE = "115804" OR CODE = "115814" OR CODE = "115824"                    00008090
        //      OR CODE = "115794" OR CODE = "13559D") THEN                                 00008095

        // 組本地檔案路徑
        String putfDir =
                fileDir
                        + "DATA"
                        + File.separator
                        + yyymmdd
                        + File.separator
                        + "PUTF"
                        + PATH_SEPARATOR
                        + wkDate;
        filePath_PUTF = putfDir + PATH_SEPARATOR + wkTaskPutfile;
        // 刪除本地檔案
        textFile.deleteFile(filePath_PUTF);
        // 組fsap共用檔案路徑
        String sourceFtpPath =
                "NCL"
                        + File.separator
                        + formatUtil.pad9(yyymmdd, 8)
                        + File.separator
                        + "2FSAP"
                        + File.separator
                        + "DATA"
                        + File.separator
                        + "PUTF"
                        + File.separator
                        + wkTaskPutfile;
        // 下載fsap共用空間的檔案
        File sourceFile = downloadFromSftp(sourceFtpPath, putfDir);
        if (sourceFile != null) {
            filePath_PUTF = getLocalPath(sourceFile);
        }
        // 組本地檔案路徑
        String putfnDir =
                fileDir
                        + "DATA"
                        + File.separator
                        + yyymmdd
                        + File.separator
                        + "PUTFN"
                        + PATH_SEPARATOR
                        + wkDate;
        filePath_PUTFN = putfnDir + PATH_SEPARATOR + wkTaskPutfile;
        // 刪除本地檔案
        textFile.deleteFile(filePath_PUTFN);
        // 組fsap共用檔案路徑
        sourceFtpPath =
                "NCL"
                        + File.separator
                        + formatUtil.pad9(yyymmdd, 8)
                        + File.separator
                        + "2FSAP"
                        + File.separator
                        + "DATA"
                        + File.separator
                        + "PUTFN"
                        + File.separator
                        + wkTaskPutfile;
        // 下載fsap共用空間的檔案
        sourceFile = downloadFromSftp(sourceFtpPath, putfnDir);
        if (sourceFile != null) {
            filePath_PUTFN = getLocalPath(sourceFile);
        }
        if (!"500360".equals(code)
                && ("7".equals(codehead)
                        || "5".equals(codehead)
                        || "510040".equals(code)
                        || "158892".equals(code)
                        || "420235".equals(code)
                        || "115892".equals(code)
                        || "115902".equals(code)
                        || "11591D".equals(code)
                        || "11589T".equals(code)
                        || "11590T".equals(code)
                        || "115988".equals(code)
                        || "11598T".equals(code)
                        || "350003".equals(code)
                        || "36F209".equals(code)
                        || "115154".equals(code)
                        || "111332".equals(code)
                        || "158686".equals(code)
                        || "139098".equals(code)
                        || "360019".equals(code)
                        || "193016".equals(code)
                        || "151742".equals(code)
                        || "158084".equals(code)
                        || "158114".equals(code)
                        || "159764".equals(code)
                        || "159773".equals(code)
                        || "159783".equals(code)
                        || "159790".equals(code)
                        || "159800".equals(code)
                        || "159810".equals(code)
                        || "159820".equals(code)
                        || "159830".equals(code)
                        || "159840".equals(code)
                        || "133288".equals(code)
                        || "198010".equals(code)
                        || "198020".equals(code)
                        || "198030".equals(code)
                        || "198040".equals(code)
                        || "198050".equals(code)
                        || "193040".equals(code)
                        || "350001".equals(code)
                        || "36C179".equals(code)
                        || "13512D".equals(code)
                        || "155928".equals(code)
                        || "400212".equals(code)
                        || "405212".equals(code)
                        || "118".equals(code3)
                        || "121454".equals(code)
                        || "191904".equals(code)
                        || "115804".equals(code)
                        || "115814".equals(code)
                        || "115824".equals(code)
                        || "115794".equals(code)
                        || "13559D".equals(code))) {
            //          GO CHGFTPRTN
            // 00008100
            chgftp();
        } else {
            //          GO   FTPRTN;
            // 00008300
            ftp();
        }
    }

    private void chgftp() {
        // CHGFTPRTN:     %% 指定媒體檔名                                                   00013200
        //    IF FILE DATA/CL/BH/PUTF/#DATE/#PUTFILE IS RESIDENT                            00013300
        //    OR FILE DATA/CL/BH/PUTFN/#DATE/#PUTFILE IS RESIDENT  THEN                     00013400
        //      ELSE                                                                        00013500
        //       BEGIN                                                                      00013600
        //           STR:= " 檔案不存在請確認檔名或日期無誤 ";                              00013700
        //           GO  FAILURE;                                                           00013800
        //       END;                                                                       00013900
        if (textFile.exists(filePath_PUTF) || textFile.exists(filePath_PUTFN)) {
        } else {
            str = " 檔案不存在請確認檔名或日期無誤 ";
            failure = true;
            return;
        }
        //  IF NFLG = "3" OR NFLG = "4" OR NFLG = "5"  THEN                                 00014000
        //  BEGIN                                                                           00014100
        //    FILEIN := "("&USRCODE&")DATA/CL/BH/PUTFN/"&DATE&"/"&PUTFILE;                  00014200
        //    FILEOUT:= "("&USRCODE&")DATA/CL/BH/PUTFN/"&DATE&"/"&PUTFILE&"/OUT";           00014300
        //  END;                                                                            00014400
        //  ELSE                                                                            00014500
        //  BEGIN                                                                           00014600
        //    FILEIN := "("&USRCODE&")DATA/CL/BH/PUTF/"&DATE&"/"&PUTFILE;                   00014700
        //    FILEOUT:= "("&USRCODE&")DATA/CL/BH/PUTF/"&DATE&"/"&PUTFILE&"/OUT";            00014800
        //  END;                                                                            00014900
        if ("3".equals(nflg) || "4".equals(nflg) || "5".equals(nflg)) {
            fileIn = filePath_PUTFN;
        } else {
            fileIn = filePath_PUTF;
        }
        //    IF CODE = "158084" OR CODE = "158114" OR CODE = "159764" OR                   00016700
        //       CODE = "159773" OR CODE = "159783" OR CODE = "159790" OR                   00016800
        //       CODE = "159800" OR CODE = "159810" OR CODE = "159820" OR                   00016900
        //       CODE = "159830" OR CODE = "159840" OR CODE = "193040" OR                   00017000
        //       CODE = "198010" OR CODE = "198020" OR CODE = "198030" OR                   00017100
        //       CODE = "198040" OR CODE = "198050" OR CODE = "115804" OR                   00017200
        //       CODE = "115814" OR CODE = "115824" OR CODE = "115794" THEN                 00017250
        //          FILENAME:="'BOT_"&DATE&"_"&CODE2&".TXT'" ;                              00017300
        if ("158084".equals(code)
                || "158114".equals(code)
                || "159764".equals(code)
                || "159773".equals(code)
                || "159783".equals(code)
                || "159790".equals(code)
                || "159800".equals(code)
                || "159810".equals(code)
                || "159820".equals(code)
                || "159830".equals(code)
                || "159840".equals(code)
                || "193040".equals(code)
                || "198010".equals(code)
                || "198020".equals(code)
                || "198030".equals(code)
                || "198040".equals(code)
                || "198050".equals(code)
                || "115804".equals(code)
                || "115814".equals(code)
                || "115824".equals(code)
                || "115794".equals(code)) {
            fileName = "BOT_" + wkDate + "_" + code2 + ".TXT";
        }
        //    IF CODE = "115892" THEN                                                       00017400
        //          FILENAME:="'0K004"&YYYMMDD&""&TIME&"0.TXT'" ;                           00017420
        if ("115892".equals(code)) {
            fileName = "0K004" + yyymmdd + time + "0.TXT";
        }
        //    IF CODE = "115902" THEN                                                       00017440
        //          FILENAME:="'9K004"&YYYMMDD&""&TIME&"0.TXT'" ;                           00017450
        if ("115902".equals(code)) {
            fileName = "9K004" + yyymmdd + time + "0.TXT";
        }
        //    IF CODE = "115988" THEN                                                       00017452
        //          FILENAME:="'7K004"&YYYMMDD&""&TIME&"1.TXT'" ;                           00017454
        if ("115988".equals(code)) {
            fileName = "7K004" + yyymmdd + time + "1.TXT";
        }
        //    IF CODE = "11589T" THEN                                                       00017460
        //          FILENAME:="'0KTL004"&YYYMMDD&""&TIME&"0.TXT'" ;                         00017470
        if ("11589T".equals(code)) {
            fileName = "0KTL004" + yyymmdd + time + "0.TXT";
        }
        //    IF CODE = "11590T" THEN                                                       00017480
        //          FILENAME:="'9KTL004"&YYYMMDD&""&TIME&"0.TXT'" ;                         00017500
        if ("11590T".equals(code)) {
            fileName = "9KTL004" + yyymmdd + time + "0.TXT";
        }
        //    IF CODE = "11598T" THEN                                                       00017505
        //          FILENAME:="'7KTL004"&YYYMMDD&""&TIME&"1.TXT'" ;                         00017510
        if ("11598T".equals(code)) {
            fileName = "7KTL004" + yyymmdd + time + "1.TXT";
        }
        //    IF PUTFILE = "27X1360019TA" THEN                                              00017520
        //          FILENAME:="'''bank.bk004.bp04p.t"&NYYYMMDD&"'''";                       00017540
        if ("27X1360019TA".equals(wkTaskPutfile)) {
            fileName = "'bank.bk004.bp04p.t" + nyyymmdd + "'";
        }
        //    IF PUTFILE = "27X1360019TI" THEN                                              00017560
        //          FILENAME:="'''bank.bk004.bp04pbk.t"&NYYYMMDD&"'''";                     00017580
        if ("27X1360019TI".equals(wkTaskPutfile)) {
            fileName = "'bank.bk004.bp04pbk.t" + nyyymmdd + "'";
        }
        //    IF CODE = "36C179" THEN                                                       00017600
        //          FILENAME:="'BOT4Q"&YYYYMMDD&"01I.TXT'"          ;                       00017700
        if ("36C179".equals(code)) {
            fileName = "BOT4Q" + yyyymmdd + "01I.TXT";
        }
        //    IF PUTFILE = "07C1510040" THEN                                                00017800
        //          FILENAME:="'NET004"&YYYMMDD&".89'"               ;                      00017900
        if ("07C1510040".equals(wkTaskPutfile)) {
            fileName = "NET004" + yyymmdd + ".89";
        }
        //    IF PUTFILE = "07X1510040" THEN                                                00017910
        //       BEGIN                                                                      00017920
        //          FILENAME:="'''bank.bk004.bp04pbk2.t"&NYYYMMDD&"'''";                    00017930
        //          ACOUNT  :="36001";                                                      00017940
        //          PASSWD  :="36001";                                                      00017950
        //       END;                                                                       00017960
        if ("07X1510040".equals(wkTaskPutfile)) {
            fileName = "'bank.bk004.bp04pbk2.t" + nyyymmdd + "'";
            acount = "36001";
            acid = "36001";
        }
        //    IF CODE = "158686" THEN                                                       00018000
        //          FILENAME:="'DLR004"&YYYMMDD&".TXT'"             ;                       00018100
        if ("158686".equals(code)) {
            fileName = "DLR004" + yyymmdd + ".TXT";
        }
        //    IF CODE = "158892" OR CODE = "151742" THEN                                    00018200
        //          FILENAME:="'"&CODE1&"_"&YYYMMDD&".txt'"        ;                        00018300
        if ("158892".equals(code) || "151742".equals(code)) {
            fileName = code1 + "_" + yyymmdd + ".txt";
        }
        //    IF CODE = "13559D" THEN                                                       00018320
        //          FILENAME:="'bot_TW_VA_"&YYYYMMDD&".json'";                              00018340
        if ("13559D".equals(code)) {
            fileName = "bot_TW_VA_" + yyyymmdd + ".json";
        }
        //    IF CODE = "133288" THEN                                                       00018400
        //    BEGIN                                                                         00018500
        //       RUN OBJ/CL/BH/CRE/NEXTDAY(YYYMMDD,NSYSDATE REFERENCE,                      00018600
        //                                         WEEK    REFERENCE);                      00018700
        //       YYYYMMDD := STRING(DECIMAL(NSYSDATE)+19110000,8);                          00018900
        //       FILENAME:="'BKCOL_"&YYYYMMDD&".004'" ;                                     00019000
        //                                                                                  00019100
        //    END;                                                                          00019200
        if ("133288".equals(code)) {
            dateDto.init();
            dateDto.setDateS(yyymmdd);
            dateDto.setDays(1);
            dateUtil.getCalenderDay(dateDto);
            yyyymmdd = dateDto.getDateE2String(true);
            fileName = "BKCOL_" + yyyymmdd + ".004";
        }
        // % 台電傳檔檔名異動，沿用舊帳密                                                    00019350
        //    IF CODE = "350003" THEN                                                       00019400
        //       BEGIN                                                                      00019420
        //          ACOUNT  :="'30200'"               ;                                     00019440
        //          PASSWD  :="'30200'"               ;                                     00019460
        //          FILENAME:="'SJA0"&MMDD&".DAT'"    ;                                     00019500
        //       END;                                                                       00019550
        if ("350003".equals(code)) {
            acount = "30200";
            acid = "30200";
            fileName = "SJA0" + mmdd + ".DAT";
        }
        //    IF CODE = "139098" THEN                                                       00019600
        //          FILENAME:="'BOT_"&YYYYMMDD&".txt'"             ;                        00019700
        if ("139098".equals(code)) {
            fileName = "BOT_" + yyyymmdd + ".txt";
        }
        //    IF CODE = "36F209" THEN                                                       00019800
        //          FILENAME:="'SPF004."&YYYYMMDD&"'"              ;                        00019900
        if ("36F209".equals(code)) {
            fileName = "SPF004." + yyyymmdd;
        }
        //    IF CODE = "420235" THEN                                                       00020000
        //          FILENAME:="'FITEL18AC"&YYYYMMDD&".TXT'"        ;                        00020100
        if ("420235".equals(code)) {
            fileName = "FITEL18AC" + yyyymmdd + ".TXT";
        }
        // % 亞太電信                                                                        00020110
        //    IF CODE = "13512D" THEN                                                       00020120
        //          FILENAME:="'bot"&YYMMDD&".004'" ;                                       00020140
        if ("13512D".equals(code)) {
            fileName = "bot" + yymmdd + ".004";
        }
        //    IF CODE = "11591D" THEN                                                       00020142
        //          FILENAME:="'bot1591"&YYMMDD&".004'" ;                                   00020144
        if ("11591D".equals(code)) {
            fileName = "bot1591" + yymmdd + ".004";
        }
        // % 臺灣交響樂團                                                                    00020150
        //    IF CODE = "155928" THEN                                                       00020160
        //          FILENAME:="'"&CODE1&"_"&YYYMMDD&".txt'";                                00020170
        if ("155928".equals(code)) {
            fileName = code1 + "_" + yyymmdd + ".txt";
        }
        //    IF CODE = "115154" THEN                                                       00020200
        //      BEGIN                                                                       00020300
        //          ACOUNT  :="GEIUDACC"              ;                                     00020400
        //          PASSWD  :="GEIUDACC99"            ;                                     00020500
        //          FILENAME:="'T"&CODE1&"_"&YYYMMDD&"'"  ;                                 00020600
        //      END;                                                                        00020700
        if ("115154".equals(code)) {
            acount = "GEIUDACC";
            acid = "GEIUDACC99";
            fileName = "T" + code1 + "_" + yyymmdd;
        }
        // % 花旗銀行                                                                        00020720
        //    IF CODE = "400212" OR CODE = "405212" THEN                                    00020740
        //          FILENAME:="'CC44"&CODE&"_"&YYYYMMDD&".txt'" ;                           00020760
        if ("400212".equals(code) || "405212".equals(code)) {
            fileName = "CC44" + code + "_" + yyyymmdd + ".txt";
        }
        //    IF CODE = "111332" THEN                                                       00020800
        //      BEGIN                                                                       00020900
        //          ACOUNT  :="'tuition'"              ;                                    00021000
        //          PASSWD  :="'tuition'"              ;                                    00021100
        //          FILENAME:="'bankedu."&YYYMMDD&"'"     ;                                 00021200
        //      END;                                                                        00021300
        if ("111332".equals(code)) {
            acount = "tuition";
            acid = "tuition";
            fileName = "bankedu." + yyymmdd;
        }
        //                                                                                  00021400
        //    IF CODE = "193016" THEN                                                       00021500
        //      BEGIN                                                                       00021600
        //          ACOUNT  :="'pubptl'"              ;                                     00021700
        //          PASSWD  :="'pubptl'"              ;                                     00021800
        //          FILENAME:="'govfund."&YYYMMDD&"'" ;                                     00021900
        //      END;                                                                        00022000
        if ("193016".equals(code)) {
            acount = "pubptl";
            acid = "pubptl";
            fileName = "govfund." + yyymmdd;
        }
        // % 公庫服務網                                                                      00022010
        //    IF CODE3 = "118" THEN                                                         00022020
        //      BEGIN                                                                       00022030
        //          ACOUNT  :="'pubptpms'"            ;                                     00022040
        //          PASSWD  :="'tbld8590'"            ;                                     00022050
        //          FILENAME:="govbill_"&CODE2&"_"&YYYMMDD&"" ;                             00022060
        //          FILENAME:="'inbound/govbill/" & FILENAME & "'"  ;                       00022070
        //      END;
        //      00022080
        // todo:FILENAME:="govbill_"&CODE2&"_"&YYYMMDD&""
        // todo:FILENAME:="'inbound/govbill/" & FILENAME & "'"
        if ("118".equals(code3)) {
            acount = "pubptpms";
            acid = "tbld8590";
            fileName = "govbill_" + code2 + "_" + yyymmdd;
        }
        // %
        // 00022100
        // % 兒少專案要變更檔名                                                              00022200
        //    IF CODE = "191904" THEN                                                       00022300
        //          FILENAME:="'/OUT/RECL0"&YYYMMDD&".txt'"        ;                        00022400
        // todo:目錄
        if ("191904".equals(code)) {
            fileName = "RECL0" + yyymmdd + ".txt";
        }
        //    IF CODE = "121454" THEN                                                       00022685
        //       FILENAME:= "'"&CODE1&""&FILE121454&"_"&YYYMMDD&"'";                        00022690
        if ("121454".equals(code)) {
            fileName = code1 + file121454 + "_" + yyymmdd;
        }
        //    IF ( CODEHEAD = "7" OR CODEHEAD = "5" ) AND (CODE NEQ "510040") THEN          00022700
        //       BEGIN                                                                      00022800
        //           ACOUNT  := CODE                   ;                                    00022900
        //           PASSWD  := CODE                   ;                                    00023000
        //           FILENAME:="'"&CODE&"_"&YYYMMDD&"'";                                    00023100
        //       END;                                                                       00023200
        if (("7".equals(codehead) || "5".equals(codehead)) && !"510040".equals(code)) {
            acount = code;
            acid = code;
            fileName = code + "_" + yyymmdd;
        }
        // % 敦化分行─台塑案                                                                00023205
        //     IF  CODE = "350001" THEN                                                     00023210
        //         BEGIN                                                                    00023215
        //            ACOUNT  :="BS106025";                                                 00023220
        //            PASSWD  :="BSG2602" ;                                                 00023225
        //            FILENAME:="'TBAC/TBAC"&NYYYYMMDD&"'" ;                                00023230
        //         END;                                                                     00023232
        // todo:目錄
        if ("350001".equals(code)) {
            acount = "BS106025";
            acid = "BSG2602";
            fileName = "TBAC" + nyyyymmdd;
        }
        // % 公路總局                                                                        00023235
        //     IF  CODE = "500629" THEN                                                     00023236
        //         BEGIN                                                                    00023237
        //            FILENAME:="'"&PUTFILE&"_"&YYYYMMDD&".txt'";                           00023238
        //         END;                                                                     00023239
        if ("500629".equals(code)) {
            fileName = wkTaskPutfile + "_" + yyyymmdd + ".txt";
        }
        // %
        // 00023240
        //     IF  CODE3 ="700" THEN                                                        00023245
        //         BEGIN                                                                    00023247
        //            IF DECIMAL(CODE) > 700000 AND DECIMAL(CODE) < 700501 THEN             00023250
        //               BEGIN                                                              00023255
        //                  ACOUNT  :="BS106025";                                           00023260
        //                  PASSWD  :="BSG2602" ;                                           00023265
        //                  FILENAME:="'"&CODE&"_"&YYYMMDD&"'";                             00023270
        //               END;                                                               00023275
        //         END;                                                                     00023280
        if ("700".equals(code3)) {
            int codeI = parse.isNumeric(code) ? parse.string2Integer(code) : 0;
            if (codeI > 700000 && codeI < 700501) {
                acount = "BS106025";
                acid = "BSG2602";
                fileName = code + "_" + yyymmdd;
            }
        }
        //     INITIALIZE (TS);                                                             00023400
        //     COPY   [FTP] #FILEOUT                                                        00023500
        //             AS   #FILENAME                                                       00023600
        //     (FTPTYPE=IMAGE, FTPSITE="DATA_PORT_CONNECTION_MODE=PASV")                    00023650
        //     FROM  #MYPACK(PACK) TO DISK(PACK,IPADDRESS=EAIIP,                            00023700
        //                            USERCODE=#ACOUNT/#PASSWD)[TS];                        00023800
        //     IF TS(VALUE)  NEQ  0 THEN  GO  NOSUCC;                                       00023900

        upload(fileIn);
        forFsap(wkTaskPutfile, fileName, acount, acid);
        //     IF CODE = "730489" THEN                                                      00024015
        //     BEGIN                                                                        00024020
        //        INITIALIZE (TS);                                                          00024025
        // %---  傳送空檔                                                                    00024030
        //        FILENAMEOK := "'"&CODE&"_"&YYYMMDD&".OK'";                                00024035
        //        ACOUNT  := CODE;                                                          00024040
        //        PASSWD  := CODE;                                                          00024045
        //        COPY   [FTP]   #FILEEMPTY                                                 00024050
        //                 AS    #FILENAMEOK                                                00024055
        //        (FTPTYPE=IMAGE, FTPSITE="DATA_PORT_CONNECTION_MODE=PASV")                 00024060
        //        FROM  #MYPACK(PACK) TO DISK(PACK,IPADDRESS=EAIIP,                         00024065
        //                               USERCODE=#ACOUNT/#PASSWD)[TS];                     00024070
        //        IF TS(VALUE)  NEQ  0 THEN  GO  NOSUCC;                                    00024075
        //     END;                                                                         00024080
        if ("730489".equals(code)) {
            String emptyDATA = fileDir + "EMPTY";
            upload(emptyDATA);
            String filename = code + "_" + yyymmdd + ".OK";
            acount = code;
            acid = code;
            forFsap("EMPTYOK", filename, acount, acid);
        }

        //     GO OKRTN  ;                                                                  00024090
    }

    private void ftp() {
        // FTPRTN:                                                                          00008600
        //     IF FILE DATA/CL/BH/PUTF/#DATE/#PUTFILE IS RESIDENT                           00008700
        //     OR FILE DATA/CL/BH/PUTFN/#DATE/#PUTFILE IS RESIDENT  THEN                    00008800
        //       FLAG:= "Y";                                                                00008900
        //     ELSE                                                                         00009000
        //       BEGIN                                                                      00009100
        //         STR:= " 檔案不存在請確認檔名及日期無誤 ";                                00009200
        //         GO  FAILURE;                                                             00009300
        //       END;                                                                       00009400
        // 組檔名拉到外層做
        if (textFile.exists(filePath_PUTF) || textFile.exists(filePath_PUTFN)) {
            flag = "Y";
        } else {
            str = " 檔案不存在請確認檔名及日期無誤 ";
            failure = true;
            return;
        }

        //  IF NFLG = "3" OR NFLG = "4" OR NFLG = "5" THEN                                  00009500
        //  BEGIN                                                                           00009600
        //    FILEIN := "("&USRCODE&")DATA/CL/BH/PUTFN/"&DATE&"/"&PUTFILE;                  00009700
        //    FILEOUT:= "("&USRCODE&")DATA/CL/BH/PUTFN/"&DATE&"/"&PUTFILE&"/OUT";           00009800
        //  END;                                                                            00009900
        //  ELSE                                                                            00010000
        //  BEGIN                                                                           00010100
        //    FILEIN := "("&USRCODE&")DATA/CL/BH/PUTF/"&DATE&"/"&PUTFILE;                   00010200
        //    FILEOUT:= "("&USRCODE&")DATA/CL/BH/PUTF/"&DATE&"/"&PUTFILE&"/OUT";            00010300
        //  END;                                                                            00010400
        if ("3".equals(nflg) || "4".equals(nflg) || "5".equals(nflg)) {
            fileIn = filePath_PUTFN;
        } else {
            fileIn = filePath_PUTF;
        }
        //     WKFCL(TITLE=#FILEIN,KIND=DISK,DEPENDENTSPECS=TRUE);                          00010500
        //     OPEN(WKFCL);                                                                 00010600
        //     NOFLG1:= WKFCL(LASTRECORD) + 1;                                              00010700
        //     LOCK(WKFCL);                                                                 00010800
        // %
        // 00010900
        //     IF     NOFLG1 > 0   THEN                                                     00011000
        //       BEGIN                                                                      00011100
        //          RUN OBJECT/TCPIP/FTP/DOWNLOAD [T1]; SW1= TRUE ;                         00011200
        //              FILE INF (TITLE=#FILEIN    ON  #MYPACK );                           00011300
        //              FILE OUTF(TITLE=#FILEOUT   ON  #MYPACK );                           00011400
        //              SECURITY #FILEOUT  ON #MYPACK PUBLIC IO;                            00011500
        //       END;                                                                       00011600
        //     ELSE                                                                         00011700
        //       BEGIN                                                                      00011800
        //          STR := " 請注意！檔案是空檔 ";                                          00011900
        //          GO FAILURE;                                                             00012000
        //       END;                                                                       00012100
        // %
        // 00012200
        //     FILENAME:= "'"&CODE1&"_"&YYYMMDD&"'";                                        00012300
        fileName = code1 + "_" + yyymmdd;
        //     INITIALIZE (TS);                                                             00012400
        //     COPY   [FTP]   #FILEOUT                                                      00012500
        //              AS    #FILENAME                                                     00012600
        //     (FTPTYPE=IMAGE, FTPSITE="DATA_PORT_CONNECTION_MODE=PASV")                    00012650
        //     FROM  #MYPACK(PACK) TO DISK(PACK,IPADDRESS=EAIIP,                            00012700
        //                            USERCODE=#CODE1/#CODE1)[TS];                          00012800
        upload(fileIn);
        forFsap(wkTaskPutfile, fileName, code1, code1);

        //     IF TS(VALUE)  NEQ  0 THEN  GO  NOSUCC                                        00012900
        //     ELSE  GO  OKRTN;                                                             00013000
    }

    private void writeEmptyData(String filename) {

        String emptyDATA = fileDir + filename;
        textFile.writeFileContent(emptyDATA, new ArrayList<>(), CHARSET_BIG5);
    }

    private void upload(String filePath) {
        try {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RFTPLsnr upload()");
            Path path = Paths.get(filePath);
            File file = path.toFile();
            String uploadPath =
                    File.separator + formatUtil.pad9(yyymmdd, 8) + File.separator + "2FSAP";
            fsapSyncSftpService.uploadFile(file, uploadPath);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void forFsap(String filename, String tarFilename, String ac, String acid) {
        Map<String, String> result =
                fsapBatchUtil.processFile(
                        "003001", // 檔案批號(6碼長) ex.000001
                        "CL", // 業務大項,系統別(5碼長) ex.NCL
                        "CL", // 業務細項(10碼長) ex.PUTF
                        filename, // 來源檔案名稱(20碼長)
                        tarFilename, // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        ac, // 對方FTP連線帳號
                        acid, // 對方FTP連線密碼
                        "NCL_FTP_ONLY", // 檔案設定代號 ex:CONVF001
                        "CL", // (產品代碼,系統別)，ex.”NCL”
                        "", // NTFCLSID (分類代碼,notify版型), ex.”01”
                        "", // DLVCHNL (訊息發送類型)EMAIL:”T”,簡訊:”S”
                        "", // CNTADDR 收件者，多筆以半形分號(;)區隔
                        "", // NOTIFYTITLE (訊息標題)email主旨、若為簡訊此欄留空
                        "", // CONTENT (通知本文)email內文、簡訊內容
                        "NCL_CONVFILE"); // WFL
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RESULT = " + result);
    }

    private void moveErrorResponse(LogicException e) {
        // event.setPeripheryRequest();
    }

    private String getLocalPath(File file) {
        return Objects.isNull(file) ? "" : file.getAbsolutePath();
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

    private void batchResponse() {
        Map<String, String> responseTextMap = new HashMap<>(textMap);
        responseTextMap.put("RFTP_DATE", "");
        responseTextMap.put("RFTP_PUTFILE", "");
        responseTextMap.put("RFTP_TELDATE", "");
        responseTextMap.put("RFTP_BIZID", "");
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", responseTextMap);
    }
}
