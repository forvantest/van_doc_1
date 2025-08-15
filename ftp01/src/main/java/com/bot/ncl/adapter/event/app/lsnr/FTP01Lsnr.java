/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.FTP01;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FilePUTFCTL;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.date.DateDto;
import com.bot.txcontrol.util.date.DateUtil;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.text.format.Text2VoFormatter;
import com.bot.txcontrol.util.transmit.FsapSyncSftpService;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("FTP01Lsnr")
@Scope("prototype")
public class FTP01Lsnr extends BatchListenerCase<FTP01> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private DateUtil dateUtil;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    private FTP01 event;
    private DateDto dateDto = new DateDto();

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FilePUTFCTL filePutfctl;

    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CHARSET_BIG5 = "Big5";
    private String PATH_SEPARATOR = File.separator;
    private String fdPutfctl;
    private String putfCtl_putfile;
    private String putfCtl_putname1;
    private Map<String, String> labelMap;
    private Map<String, String> textMap;

    private int T = 0;
    private String wkTaskDate = "";
    private int wkTaskDateI = 0;
    private int nbsdy;
    private String tbsdy;
    private String tbsdy7;
    private String yyymmdd;
    private String nyyymmdd;
    private String wkTaskPutfile;
    private String srcPath;
    private int puftctlPutfile;
    private String putfctl_Putname1;
    private int putfctlKey;
    private String flag;
    private String mmdd;
    private String code;
    private String code1;
    private String code2;
    private String code3;
    private String codehead;
    private String yyyymmdd;
    private String nyyyymmdd;
    private String yymmdd;
    private String acount;
    private String acid;
    private String bizId = "NCL_PUTF_CONV";
    private String nflg;
    private String firstflg = "1";
    private String YYYMMDD;
    private String NYYYMMDD;
    private String filename;
    private String fileout;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(FTP01 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "FTP01Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(FTP01 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "FTP01Lsnr run()");
        if (!init(event)) {
            batchResponse();
            return;
        }
        _0000_main_rtn();
        batchResponse();
    }

    private Boolean init(FTP01 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "FTP01Lsnr init");

        this.event = event;
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        tbsdy = formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8);
        tbsdy7 = tbsdy.substring(1);
        nbsdy = parse.string2Integer(labelMap.get("NBSDY"));
        fdPutfctl = fileDir + "DATA" + PATH_SEPARATOR + tbsdy7 + PATH_SEPARATOR + "PUTFCTL";
        //        fdPutfctl = fileDir + "DATA" + PATH_SEPARATOR + tbsdy7 + PATH_SEPARATOR +
        // "PUTFCTL";
        // 先刪除本地舊的PUTFCTL
        textFile.deleteFile(
                fileDir + "DATA" + File.separator + tbsdy7 + File.separator + "PUTFCTL");
        // 下載來源檔
        String sourceFtpPath =
                "NCL"
                        + File.separator
                        + tbsdy
                        + File.separator
                        + "2FSAP"
                        + File.separator
                        + "DATA"
                        + File.separator
                        + "PUTFCTL";
        File sourceFile =
                downloadFromSftp(sourceFtpPath, fileDir + "DATA" + File.separator + tbsdy7);
        if (sourceFile == null) {
            return false;
        }
        fdPutfctl = getLocalPath(sourceFile);
        return true;
    }

    private void _0000_main_rtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "FTP01Lsnr _0000_main_rtn");
        // GENRTN
        List<String> lines = textFile.readFileContent(fdPutfctl, CHARSET_UTF8);
        for (String detail : lines) {
            text2VoFormatter.format(detail, filePutfctl);
            wkTaskDate = "0";
            wkTaskPutfile = "";
            putfCtl_putname1 = filePutfctl.getPutname().substring(0, 1);
            putfCtl_putfile = filePutfctl.getPuttype() + filePutfctl.getPutname();
            if ("X".equals(putfCtl_putname1) || "S".equals(putfCtl_putname1)) {
                // 媒體產生日 批次營業日
                wkTaskDate = filePutfctl.getGendt();
                wkTaskPutfile = filePutfctl.getPuttype() + filePutfctl.getPutname();
                fpt01();
            }
        }
    }

    private void fpt01() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "FTP01Lsnr fpt01");

        //     IF  T  IS COMPLETEDOK  THEN                                                  00005300
        //        BEGIN                                                                     00005400
        //            IF  T(TASKVALUE)  =  2  THEN                                          00005500
        //                GO OKRTN;                                                         00005600
        //        END;                                                                      00005700
        //     ELSE                                                                         00005800
        //        GO FAILURE;                                                               00005900

        //     IF  DECIMAL(DATE)  > 990101    THEN                                          00006100
        //       YYYMMDD:="0"&DATE                                                          00006200
        //     ELSE                                                                         00006300
        //       YYYMMDD:="1"&DATE;                                                         00006400
        int wkTaskDateI = parse.string2Integer(wkTaskDate);
        if (wkTaskDateI > 990101) {
            yyymmdd = "0" + wkTaskDate;
        } else {
            yyymmdd = "1" + wkTaskDate;
        }
        //     IF  DECIMAL(NBSDY) > 990101   THEN                                           00006600
        //       NYYYMMDD:="0"&NBSDY                                                        00006700
        //     ELSE                                                                         00006800
        //       NYYYMMDD:="1"&NBSDY;                                                       00006900
        nyyymmdd = formatUtil.pad9("" + nbsdy, 7);
        //     MMDD     := TAKE(DROP(DATE,2),4);                                            00007000
        //     CODE     := TAKE(DROP(PUTFILE,4),6);                                         00007100
        //     CODE1    := TAKE(DROP(PUTFILE,4),5);                                         00007200
        //     CODE2    := TAKE(DROP(PUTFILE,5),4);                                         00007300
        //     CODE3    := TAKE(DROP(PUTFILE,4),3);                                         00007400
        //     CODEHEAD := TAKE(DROP(PUTFILE,4),1);                                         00007500
        //     YYYYMMDD := STRING(DECIMAL(DATE)+20110000,8);                                00007600
        //     NYYYYMMDD:= STRING(DECIMAL(NBSDY)+20110000,8);                               00007700
        //     YYMMDD   := TAKE(DROP(STRING(DECIMAL(DATE)+20110000,8),2),6);                00007800
        //     ACOUNT   := CODE1;                                                           00007900
        //     PASSWD   := CODE1;                                                           00008000
        //     NFLG     := TAKE(DROP(PUTFILE,3),1);                                         00008100
        mmdd = wkTaskDate.substring(2, 6);
        code = wkTaskPutfile.substring(4, 10);
        code1 = wkTaskPutfile.substring(4, 9);
        code2 = wkTaskPutfile.substring(5, 9);
        code3 = wkTaskPutfile.substring(4, 7);
        codehead = wkTaskPutfile.substring(4, 5);
        yyyymmdd = "" + (wkTaskDateI + 20110000);
        nyyyymmdd = "" + (nbsdy + 20110000);
        yymmdd = yyyymmdd.substring(2, 8);
        acount = code1;
        acid = code1;
        nflg = wkTaskPutfile.substring(3, 4);
        filename = wkTaskPutfile;
        //     IF FIRSTFLG = "0"                                                            00008300
        //     AND (NFLG = "1" OR  NFLG = "2" OR NFLG = "4" OR  NFLG = "5") THEN            00008400
        //     BEGIN                                                                        00008500
        // %%%%%%%%DISPLAY "PUTFILE=" & PUTFILE;
        // 00008600
        //        GO GENRTN;                                                                00008700
        //     END;                                                                         00008800
        if ("0".equals(firstflg)
                && ("1".equals(nflg) || "2".equals(nflg) || "4".equals(nflg) || "5".equals(nflg))) {
            return;
        }
        // firstflg ==1的在ftp01處理
        // % 轉檔的後送                                                                      00008900
        //     IF FIRSTFLG = "1"
        //     AND (NFLG = "0" OR  NFLG = "3") THEN                                         00009100
        //                                                     00009000
        if ("1".equals(firstflg) && ("0".equals(nflg) || "3".equals(nflg))) {
            //     BEGIN
            // 00009200
            // %%%%%%%%DISPLAY "PUTFILE=" & PUTFILE;
            // 00009300
            //        GO GENRTN;
            // 00009400
            return;
            //     END;
            // 00009500
        }

        // % 以下的不在這裡傳，由 RFTP 傳送                                                  00009600
        //     IF  CODE = "111323" OR CODE = "121004" OR CODE = "360019"                    00009700
        //     OR  CODE = "115892" OR CODE = "115902" OR CODE = "115988"                    00009800
        //     OR  CODE = "510040"                                                          00009850
        //       THEN GO GENRTN;                                                            00009900
        if ("111323".equals(code)
                || "121004".equals(code)
                || "360019".equals(code)
                || "115892".equals(code)
                || "115902".equals(code)
                || "115988".equals(code)
                || "510040".equals(code)) {
            return;
        }

        // 檢查檔案是否存在
        //     IF  FILE DATA/CL/BH/PUTF/#DATE/#PUTFILE  IS RESIDENT                         00010300
        //     OR  FILE DATA/CL/BH/PUTFN/#DATE/#PUTFILE  IS RESIDENT THEN                   00010400
        //       FLAG:= "Y";                                                                00010500
        //     ELSE                                                                         00010600
        //       FLAG:= "N";                                                                00010700
        String filePath_PUTF =
                fileDir
                        + "DATA"
                        + PATH_SEPARATOR
                        + tbsdy7
                        + PATH_SEPARATOR
                        + "PUTF"
                        + PATH_SEPARATOR
                        + wkTaskDate
                        + PATH_SEPARATOR
                        + wkTaskPutfile;
        String filePath_PUTFN =
                fileDir
                        + "DATA"
                        + PATH_SEPARATOR
                        + tbsdy7
                        + PATH_SEPARATOR
                        + "PUTFN"
                        + PATH_SEPARATOR
                        + wkTaskDate
                        + PATH_SEPARATOR
                        + wkTaskPutfile;
        if (textFile.exists(filePath_PUTF) || textFile.exists(filePath_PUTFN)) {
            flag = "Y";
        } else {
            flag = "N";
        }
        // CONVERTRTN:                                                                      00010900
        //  IF FLAG = "N"  THEN   GO  GENRTN; % 代表今日無銷帳檔產生                        00011000
        if ("N".equals(flag)) {
            return;
        }
        //  IF NFLG = "3" OR NFLG = "4" OR NFLG = "5" THEN                                  00011100
        //  BEGIN                                                                           00011200
        //    FILENAME:= "("&USRCODE&")DATA/CL/BH/PUTFN/"&DATE&"/"&PUTFILE;                 00011300
        //    FILEOUT:= "("&USRCODE&")DATA/CL/BH/PUTFN/"&DATE&"/"&PUTFILE&"/OUT";           00011400
        //  END;                                                                            00011500
        //  ELSE                                                                            00011600
        //  BEGIN                                                                           00011700
        //    FILENAME:= "("&USRCODE&")DATA/CL/BH/PUTF/"&DATE&"/"&PUTFILE;                  00011800
        //    FILEOUT:= "("&USRCODE&")DATA/CL/BH/PUTF/"&DATE&"/"&PUTFILE&"/OUT";            00011900
        //  END;                                                                            00012000
        if ("3".equals(nflg) || "4".equals(nflg) || "5".equals(nflg)) {
            srcPath =
                    fileDir
                            + "DATA"
                            + PATH_SEPARATOR
                            + tbsdy7
                            + PATH_SEPARATOR
                            + "PUTFN"
                            + PATH_SEPARATOR
                            + wkTaskDate
                            + PATH_SEPARATOR
                            + wkTaskPutfile;
            bizId = "NCL_PUTFN_CONV";
        } else {
            srcPath =
                    fileDir
                            + "DATA"
                            + PATH_SEPARATOR
                            + tbsdy7
                            + PATH_SEPARATOR
                            + "PUTF"
                            + PATH_SEPARATOR
                            + wkTaskDate
                            + PATH_SEPARATOR
                            + wkTaskPutfile;
            bizId = "NCL_PUTF_CONV";
        }

        //     IF  CODE NEQ "500360" AND ( CODEHEAD = "7" OR CODEHEAD = "5"                 00014300
        //      OR CODE = "510040" OR CODE = "158892" OR CODE = "420235"                    00014400
        //      OR CODE = "350003" OR CODE = "36F209" OR CODE = "115154"                    00014500
        //      OR CODE = "111332" OR CODE = "158686" OR CODE = "360019"                    00014600
        //      OR CODE = "158084" OR CODE = "158114" OR CODE = "159764"                    00014700
        //      OR CODE = "159773" OR CODE = "159783" OR CODE = "159790"                    00014800
        //      OR CODE = "159800" OR CODE = "159810" OR CODE = "159820"                    00014900
        //      OR CODE = "159830" OR CODE = "159840" OR CODE = "133288"                    00015000
        //      OR CODE = "198010" OR CODE = "198020" OR CODE = "198030"                    00015100
        //      OR CODE = "155928" OR CODE = "400212" OR CODE = "405212"                    00015200
        //      OR CODE = "198040" OR CODE = "198050" OR CODE = "193040"                    00015300
        //      OR CODE = "350001" OR CODE = "36C179" OR CODE = "13512D"                    00015400
        //      OR CODE = "115804" OR CODE = "115814" OR CODE = "115824"                    00015500
        //      OR CODE = "151742" OR CODE = "191904" OR CODE = "11591D"                    00015600
        //      OR CODE = "115794")  THEN                                                   00015650
        if (!"500360".equals(code)
                && ("7".equals(codehead)
                        || "5".equals(codehead)
                        || "510040".equals(code)
                        || "158892".equals(code)
                        || "420235".equals(code)
                        || "350003".equals(code)
                        || "36F209".equals(code)
                        || "115154".equals(code)
                        || "111332".equals(code)
                        || "158686".equals(code)
                        || "360019".equals(code)
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
                        || "155928".equals(code)
                        || "400212".equals(code)
                        || "405212".equals(code)
                        || "198040".equals(code)
                        || "198050".equals(code)
                        || "193040".equals(code)
                        || "350001".equals(code)
                        || "36C179".equals(code)
                        || "13512D".equals(code)
                        || "115804".equals(code)
                        || "115814".equals(code)
                        || "115824".equals(code)
                        || "151742".equals(code)
                        || "191904".equals(code)
                        || "11591D".equals(code)
                        || "115794".equals(code))) {
            //        GO  CHGFTPRTN ;
            // 00015700
            chgftp();
            //     ELSE
            // 00015800
        } else {
            //        GO    FTPRTN ;
            // 00015900
            ftp();
        }
        // NOSUCC:                                                                          00026700
        //       COUNT := COUNT + 1;                                                        00026800
        //       IF  COUNT  >  10   THEN    GO GENRTN;                                      00026900
        //       WAIT(60) ;                                                                 00027000
        //       GO LOOP ;                                                                  00027100
        //                                                                                  00027200
        // FAILURE:                                                                         00027300
        //     ACP:= ACCEPT(STR);                                                           00027400
        //     GO ENDRTN;                                                                   00027500
        // OKRTN:                                                                           00027600
        // EMPTYFILE :                                                                      00027700
        //     IF FILE DATA/CL/BH/PUTF/#DATE/27X0311180 IS RESIDENT THEN                    00027800
        //     ELSE                                                                         00027900
        //      BEGIN                                                                       00028000
        //       CODE1    := "11180"              ;                                         00028100
        //       FILENAME:= "'"&CODE1&"_"&YYYMMDD&"'";                                      00028200
        //       COPY   [FTP]  (#USRCODE)DATA/CL/BH/PUTF/EMPTY                              00028300
        //                AS    #FILENAME                                                   00028400
        //       FROM  #MYPACK(PACK) TO DISK(PACK,IPADDRESS=EAIIP,                          00028500
        //                              USERCODE=#ACOUNT/#PASSWD)[TS];                      00028600
        //      END;                                                                        00028700
        // %    DISPLAY "*** WFL/CL/BH/FTP  RUN OK  ***";
        // 00028800
        // ENDRTN:                                                                          00028900
    }

    private void chgftp() {
        // CHGFTPRTN:     %% 指定媒體檔名                                                   00017100
        // %yahoo 輕鬆付檔案                                                                 00017700
        //     IF CODE = "133288" THEN                                                      00017800
        //     BEGIN                                                                        00017900
        if ("133288".equals(code)) {
            // 找下一天
            //        RUN OBJ/CL/BH/CRE/NEXTDAY(YYYMMDD,NSYSDATE REFERENCE,
            // 00018000
            //                                          WEEK    REFERENCE);
            // 00018100
            //
            // 00018200

            dateDto.init();
            dateDto.setDateS(yyymmdd);
            dateDto.setDays(1);
            dateUtil.getCalenderDay(dateDto);
            //        YYYYMMDD := STRING(DECIMAL(NSYSDATE)+19110000,8);
            // 00018300
            yyyymmdd = dateDto.getDateE2String(true);
            //        FILENAME:="'BKCOL_"&YYYYMMDD&".004'" ;
            // 00018400
            filename = "BKCOL_" + yyyymmdd + ".004";
            // %       DISPLAY "FILENAME= " &FILENAME;
            // 00018500
            //     END;
            // 00018600
        }
        // % 兒少專案要變更檔名                                                              00018700
        //     IF CODE = "191904" THEN                                                      00018800
        if ("191904".equals(code)) {
            //           FILENAME:="'/OUT/RECL0"&YYYMMDD&".txt'" ;
            // 00018900
            filename = "RECL0" + YYYMMDD + ".txt";
        }
        //     IF CODE = "158686" THEN                                                      00019000
        if ("158686".equals(code)) {
            //           FILENAME:="'DLR004"&YYYMMDD&".TXT'"    ;
            // 00019100
            filename = "DLR004" + YYYMMDD + ".TXT";
        }
        // % 台電傳檔檔名異動，沿用舊帳密                                                    00019150
        //     IF CODE = "350003" THEN                                                      00019200
        if ("350003".equals(code)) {
            //        BEGIN
            // 00019220
            //           ACOUNT  :="'30200'"                ;
            // 00019240
            //           PASSWD  :="'30200'"                ;
            // 00019260
            //           FILENAME:="'SJA0"&MMDD&".DAT'"     ;
            // 00019300
            acount = "30200";
            acid = "30200";
            filename = "SJA0" + mmdd + ".DAT";
            //        END;
            // 00019350
        }
        //     IF CODE = "158892" OR CODE = "151742" THEN                                   00019400
        if ("158892".equals(code) || "151742".equals(code)) {
            //           FILENAME:="'"&CODE1&"_"&YYYMMDD&".txt'";
            // 00019500
            filename = code1 + yyymmdd + ".txt";
        }
        //     IF CODE = "36F209" THEN                                                      00019600
        if ("36F209".equals(code)) {
            //           FILENAME:="'SPF004."&YYYYMMDD&"'"      ;
            // 00019700
            filename = "SPF004." + yyyymmdd;
        }
        //     IF CODE = "36C179" THEN                                                      00019800
        if ("36C179".equals(code)) {
            //           FILENAME:="'BOT4Q"&YYYYMMDD&"01I.TXT'" ;
            // 00019900
            filename = "BOT4Q" + yyyymmdd + "01I.TXT";
        }
        //     IF CODE = "420235" THEN                                                      00020000
        if ("420235".equals(code)) {
            //           FILENAME:="'FITEL18AC"&YYYYMMDD&".TXT'" ;
            // 00020100
            filename = "FITEL18AC" + yyyymmdd + ".TXT";
        }
        // % 亞太電信                                                                        00020200
        //     IF CODE = "13512D" THEN                                                      00020300
        if ("13512D".equals(code)) {
            //           FILENAME:="'bot"&YYMMDD&".004'" ;
            // 00020400
            filename = "bot" + yymmdd + ".004";
        }
        //     IF CODE = "11591D" THEN                                                      00020500
        if ("11591D".equals(code)) {
            //           FILENAME:="'bot1591"&YYMMDD&".004'" ;
            // 00020600
            filename = "bot1591" + yymmdd + ".004";
        }
        // % 臺灣交響樂團                                                                    00020700
        //    IF CODE = "155928" THEN                                                       00020800
        if ("155928".equals(code)) {
            //          FILENAME:="'"&CODE1&"_"&YYYMMDD&".txt'";
            // 00020900
            filename = code1 + "_" + yyymmdd + ".txt";
        }
        //     IF CODE = "158084" OR CODE = "158114" OR CODE = "159764" OR                  00021000
        //        CODE = "159773" OR CODE = "159783" OR CODE = "159790" OR                  00021100
        //        CODE = "159800" OR CODE = "159810" OR CODE = "159820" OR                  00021200
        //        CODE = "159830" OR CODE = "159840" OR CODE = "193040" OR                  00021300
        //        CODE = "198010" OR CODE = "198020" OR CODE = "198030" OR                  00021400
        //        CODE = "198040" OR CODE = "198050" OR CODE = "115804" OR                  00021500
        //        CODE = "115814" OR CODE = "115824" OR CODE = "115794" THEN                00021600
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
            //           FILENAME:="'BOT_"&DATE&"_"&CODE2&".TXT'" ;
            // 00021700
            filename = "BOT_" + wkTaskDate + "_" + code2 + ".TXT";
        }
        // % 花旗銀行                                                                        00021800
        //    IF CODE = "400212" OR CODE = "405212" THEN                                    00021900
        if ("400212".equals(code) || "405212".equals(code)) {
            //          FILENAME:="'CC44"&CODE&"_"&YYYYMMDD&".txt'" ;
            // 00022000
            filename = "CC44" + code + "_" + yyyymmdd + ".txt";
        }
        //     IF CODE = "115154" THEN                                                      00022100
        if ("115154".equals(code)) {
            //       BEGIN
            // 00022200
            //           ACOUNT  :="GEIUDACC"              ;
            // 00022300
            //           PASSWD  :="GEIUDACC99"            ;
            // 00022400
            //           FILENAME:="'T"&CODE1&"_"&YYYMMDD&"'"  ;
            // 00022500
            acount = "GEIUDACC";
            acid = "GEIUDACC99";
            filename = "T" + code1 + "_" + yyymmdd;
            //       END;
            // 00022600
        }
        //     IF CODE = "111332" THEN                                                      00022700
        if ("111332".equals(code)) {
            //       BEGIN
            // 00022800
            //           ACOUNT  :="'tuition'"              ;
            // 00022900
            //           PASSWD  :="'tuition'"              ;
            // 00023000
            //            FILENAME:="'bankedu."&YYYMMDD&"'"    ;
            // 00023100
            acount = "tuition";
            acid = "tuition";
            filename = "bankedu." + yyymmdd;
            //       END;
            // 00023200
        }
        //     IF (CODEHEAD = "7" OR CODEHEAD = "5") AND (CODE NEQ "510040") THEN           00023300
        if (("7".equals(codehead) || "5".equals(codehead)) && (!"510040".equals(code))) {
            //        BEGIN
            // 00023400
            //            ACOUNT  := CODE                   ;
            // 00023500
            //            PASSWD  := CODE                   ;
            // 00023600
            //            FILENAME:="'"&CODE&"_"&YYYMMDD&"'";
            // 00023700
            acount = code;
            acid = code;
            filename = code + "_" + yyymmdd;
            //        END;
            // 00023800
        }
        // % 敦化分行─台塑案                                                                00023900
        //     IF  CODE = "350001" THEN                                                     00024000
        if ("350001".equals(code)) {
            //         BEGIN
            // 00024100
            //            ACOUNT  :="BS106025";
            // 00024200
            //            PASSWD  :="BSG2602" ;
            // 00024300
            //            FILENAME:="'TBAC/TBAC"&NYYYYMMDD&"'";
            // 00024400
            acount = "BS106025";
            acid = "BSG2602";
            filename = "TBAC" + nyyyymmdd;
            //         END;
            // 00024500
        }
        // % 公路總局                                                                        00024510
        //     IF  CODE = "500629" THEN                                                     00024520
        if ("500629".equals(code)) {
            //         BEGIN
            // 00024530
            //            FILENAME:="'"&PUTFILE&"_"&YYYYMMDD&".txt'";
            // 00024540
            filename = wkTaskPutfile + "_" + yyyymmdd + ".txt";
            //         END;
            // 00024550
        }
        // %
        // 00024600
        //     IF  CODE3 = "700" THEN                                                       00024700
        if ("700".equals(code3)) {
            //         BEGIN
            // 00024800
            //            IF DECIMAL(CODE) > 700000 AND DECIMAL(CODE) < 700501 THEN
            // 00024900
            if (parse.isNumeric(code)
                    && parse.string2Integer(code) > 700000
                    && parse.string2Integer(code) < 700501) {
                //               BEGIN
                // 00025000
                //                  ACOUNT  :="BS106025";
                // 00025100
                //                  PASSWD  :="BSG2602" ;
                // 00025200
                //                  FILENAME:="'"&CODE&"_"&YYYMMDD&"'";
                // 00025300
                acount = "BS106025";
                acid = "BSG2602";
                filename = code + "_" + yyymmdd;
                //               END;
                // 00025400
            }
            //         END;
            // 00025500
        }
        // %        DISPLAY "FILENAME = " &FILENAME;
        // 00025600
        // %
        // 00025700
        //     INITIALIZE (TS);                                                             00025800
        //     COPY  [FTP]   #FILEOUT                                                       00025900
        //             AS    #FILENAME                                                      00026000
        //     (FTPTYPE=IMAGE, FTPSITE="DATA_PORT_CONNECTION_MODE=PASV")                    00026100
        //     FROM  #MYPACK(PACK) TO DISK(PACK,IPADDRESS=EAIIP,                            00026200
        //                            USERCODE=#ACOUNT/#PASSWD)[TS];                        00026300
        //     IF TS(VALUE)  NEQ  0 THEN  GO  NOSUCC;                                       00026400
        // %    ELSE  GO GENRTN ;
        // 00026500
        upload(srcPath);
        forFsap(wkTaskPutfile, filename, acount, acid, bizId);
        // %
        // 00026505
        // % 若為 730489 ，多送一個空檔                                                      00026510
        //     IF CODE = "730489" THEN                                                      00026515
        if ("730489".equals(code)) {
            //     BEGIN
            // 00026520
            //        INITIALIZE (TS);
            // 00026525
            // %---  傳送空檔
            // 00026530
            //        FILENAMEOK := "'"&CODE&"_"&YYYMMDD&".OK'";
            // 00026535
            //        ACOUNT  := CODE;
            // 00026540
            //        PASSWD  := CODE;
            // 00026545
            //        COPY   [FTP]   #FILEEMPTY
            // 00026550
            //                 AS    #FILENAMEOK
            // 00026555
            //        (FTPTYPE=IMAGE, FTPSITE="DATA_PORT_CONNECTION_MODE=PASV")
            // 00026560
            //        FROM  #MYPACK(PACK) TO DISK(PACK,IPADDRESS=EAIIP,
            // 00026565
            //                               USERCODE=#ACOUNT/#PASSWD)[TS];
            // 00026570
            // 暫時寫空檔fsap待調整2025.2.22
            String emptyDATA = fileDir + "EMPTYOK";
            textFile.writeFileContent(emptyDATA, new ArrayList<>(), CHARSET_BIG5);
            upload(emptyDATA);
            filename = code + "_" + yyymmdd + ".OK";
            acount = code;
            acid = code;
            forFsap("EMPTYOK", filename, acount, acid, bizId);
            //        IF TS(VALUE)  NEQ  0 THEN  GO  NOSUCC;
            // 00026575
            //     END;
            // 00026580
        }
        // %
        // 00026585
        //     GO GENRTN ;                                                                  00026590
    }

    private void ftp() {
        // FTPRTN:    %% 一般非指定媒體檔名                                                 00016100
        //     INITIALIZE (TS);                                                             00016200
        //     COPY   [FTP]   #FILEOUT                                                      00016300
        //              AS    #FILENAME                                                     00016400
        //     (FTPTYPE=IMAGE, FTPSITE="DATA_PORT_CONNECTION_MODE=PASV")                    00016500
        //     FROM  #MYPACK(PACK) TO DISK(PACK,IPADDRESS=EAIIP,                            00016600
        //                            USERCODE=#CODE1/#CODE1)[TS];                          00016700
        upload(srcPath);
        forFsap(wkTaskPutfile, filename, code1, code1, bizId);
        //     IF TS(VALUE)  NEQ  0 THEN  GO  NOSUCC                                        00016800
        //     ELSE  GO GENRTN ;                                                            00016900
    }

    private void moveErrorResponse(LogicException e) {
        // event.setPeripheryRequest();
    }

    private void upload(String filePath) {
        try {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "FTP01 upload()");
            Path path = Paths.get(filePath);
            File file = path.toFile();
            String uploadPath = File.separator + tbsdy + File.separator + "2FSAP";
            fsapSyncSftpService.uploadFile(file, uploadPath);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void forFsap(
            String filename, String tarFilename, String ac, String acid, String bizId) {
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
                        bizId, // 檔案設定代號 ex:CONVF001
                        "CL", // (產品代碼,系統別)，ex.”NCL”
                        "", // NTFCLSID (分類代碼,notify版型), ex.”01”
                        "", // DLVCHNL (訊息發送類型)EMAIL:”T”,簡訊:”S”
                        "", // CNTADDR 收件者，多筆以半形分號(;)區隔
                        "", // NOTIFYTITLE (訊息標題)email主旨、若為簡訊此欄留空
                        "", // CONTENT (通知本文)email內文、簡訊內容
                        "NCL_CONVFILE"); // WFL
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RESULT = " + result);
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
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", null);
    }
}
