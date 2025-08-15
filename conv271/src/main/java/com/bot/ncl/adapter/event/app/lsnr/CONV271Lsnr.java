/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONV271;
import com.bot.ncl.dto.entities.*;
import com.bot.ncl.jpa.entities.impl.ClcmpId;
import com.bot.ncl.jpa.svc.ClcmpService;
import com.bot.ncl.jpa.svc.CldtlService;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FilePUTF;
import com.bot.ncl.util.fileVo.FileSumPUTF;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.ncl.util.string.StringUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.buffer.mg.ThreadVariable;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.eum.TxCharsets;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.date.DateUtil;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.astart.AstarUtils;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.text.format.Text2VoFormatter;
import com.bot.txcontrol.util.transmit.FsapSyncSftpService;
import java.io.File;
import java.math.BigDecimal;
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
@Component("CONV271Lsnr")
@Scope("prototype")
public class CONV271Lsnr extends BatchListenerCase<CONV271> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    private CONV271 event;
    @Autowired private DateUtil dateUtil;
    @Autowired private ClcmpService clcmpService;
    @Autowired private CldtlService cldtlService;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FilePUTF filePutf;
    @Autowired private FileSumPUTF fileSumPUTF;
    @Autowired private StringUtil strutil;
    @Autowired private AstarUtils astarUtils;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> labelMap;
    private Map<String, String> textMap;

    private static final String CHARSET = "UTF-8";
    private static final String PATH_SEPARATOR = File.separator;
    private static final String STRING_27X1111981 = "27X1111981";
    private static final String STRING_27X1111801 = "27X1111801";
    private static final String STRING_111981 = "111981";
    private static final String STRING_111801 = "111801";
    private static final String CONVF_DATA = "DATA";
    private static final String SPACE = "";
    private final List<String> fileCONV271Contents = new ArrayList<>();
    private StringBuilder sb = new StringBuilder();
    private ClcmpBus tClcmp;
    private Boolean goToEnd = false;
    private String wkFdate;
    private String isNotConv = "N"; // 告訴CONV272是否轉檔成功
    private String processDate;
    private String tbsdy;
    private int processDateInt = 0;
    private int wkYYMMDD;
    private int wkCtlFlag = 0;
    private int wk111981SumCtl;
    private int wk111981Ctl;
    private int wk111981Date;
    private int wk111981Time;
    private int wk111981Sdate;
    private int wk111981Edate;
    private int wk111981Sitdate;
    private int wk111981Cnt1 = 0;
    private int wk111981Cnt2;
    private int putfBdate;
    private int putfDate;
    private int putfEdate;
    private int putfTime;
    private int putfSitdate;
    private int putfCtl;

    private String wkPutfile;
    private String wkPutdir;
    private String wk111981Code;
    private String wk111981Code1;
    private String wk111981Code2;
    private String wk111981Code3;
    private String wk111981Rcptid;
    private String wkClcmpRcptid;
    private String wkClcmpRcptid1;
    private String wkClcmpRcptid2;
    private String wkFilename;
    private String wk111981Txtype;
    private String wkCode;
    private String wkCldtlCode;
    private String wkCldtlCode1;
    private String wkCldtlCode2;
    private String wkCldtlCode3;
    private String wk111981Userdata;
    private String wk111981Userdata1;
    private String wkTswhPname;
    private String wkClcmpCode;
    private String wkClcmpCode1;
    private String wkClcmpCode2;
    private String wkClcmpCode3;
    private String wkCldtlRcptid;
    private String wkForwardRcptid = "";
    private String wkForwardRcptid1;
    private String wk111981Rcptid2 = "";
    private String wk111981Rcptid1;
    private String wkForwardRcptid2 = "";
    private String putfCode;
    private String putfRcptid;
    private String putfUserdata;
    private String putfTxtype;

    private BigDecimal wkBalance = BigDecimal.ZERO;
    private BigDecimal wk111981Amt;
    private BigDecimal wk111981Amt1 = BigDecimal.ZERO;
    private BigDecimal wk111981Amt2 = BigDecimal.ZERO;
    private BigDecimal wk111981Amt3 = BigDecimal.ZERO;
    private BigDecimal wkForwardAmt = BigDecimal.ZERO;
    private BigDecimal putfAmt;
    private BigDecimal wkNtbsdSumAmt1 = BigDecimal.ZERO;
    private BigDecimal wkNtbsdSumAmt2 = BigDecimal.ZERO;
    private String wk111981Userdata2;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(CONV271 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV271Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONV271 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV271Lsnr run");
        this.event = event;
        // 開啟批次日期檔
        // 011500     OPEN    INPUT     FD-BHDATE.
        // DISPLAY訊息，包含在系統訊息中
        // 011600     CHANGE  ATTRIBUTE DISPLAYONLYTOMCS OF MYSELF TO TRUE.

        // 讀批次日期檔；若讀不到，顯示訊息，結束程式
        // 011700     READ    FD-BHDATE AT END DISPLAY "READ FD-BHDATE ERROR"

        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        // 作業日期(民國年yyyymmdd)
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        processDateInt = parse.string2Integer(processDate);
        if (Objects.isNull(textMap.get("WK_FILENAME")) || textMap.get("WK_FILENAME").isEmpty()) {
            wkFilename = "27X1111981";
        } else {
            wkFilename = textMap.get("WK_FILENAME"); // TODO: 待確認BATCH參數名稱
        }
        // 011800             STOP RUN.

        // 設定本營業日、檔名日期、檔名變數值
        // WK-FDATE PIC 9(06) <-WK-PUTDIR'S變數
        // WK-CDATE PIC 9(06) <-WK-CONVDIR'S變數
        // WK-PUTFILE  PIC X(10) <--WK-PUTDIR'S變數
        // WK-CONVFILE PIC X(10) <--WK-CONVDIR'S變數
        // WK-PUTDIR  <-"DATA/CL/BH/PUTF/"+WK-FDATE+"/"+WK-PUTFILE+"."
        // WK-CONVDIR <-"DATA/CL/BH/CONVF/"+WK-CDATE+"/"+WK-CONVFILE+"."

        // 搬接收參數WK-FILENAME 給 WK-PUTFILE,WK-CONVFILE
        // 011900     MOVE    FD-BHDATE-TBSDY     TO     WK-YYMMDD.
        wkYYMMDD = processDateInt;
        // 012000     MOVE    WK-YYMMDD           TO     WK-FDATE,WK-CDATE.
        wkFdate = formatUtil.pad9("" + wkYYMMDD, 7).substring(1, 7);
        // 012100     MOVE    WK-FILENAME         TO     WK-PUTFILE,WK-CONVFILE.
        wkPutfile = wkFilename;

        // 設定代收類別變數
        // 012200     IF      WK-FILENAME         =      "27X1111981"
        if (STRING_27X1111981.equals(wkFilename)) {
            // 012300        MOVE "111981"            TO     WK-CODE
            wkCode = STRING_111981;
            // 012400     ELSE IF WK-FILENAME         =      "27X1111801"
        } else if (STRING_27X1111801.equals(wkFilename)) {
            // 012500        MOVE "111801"            TO     WK-CODE.
            wkCode = STRING_111801;
        }

        // 設定檔名
        // 012600     CHANGE  ATTRIBUTE FILENAME  OF FD-PUTF   TO WK-PUTDIR.
        // 012700     CHANGE  ATTRIBUTE FILENAME  OF FD-111981 TO WK-CONVDIR.
        String putDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + "PUTF"
                        + PATH_SEPARATOR
                        + wkFdate;
        wkPutdir = putDir + PATH_SEPARATOR + wkPutfile;
        textFile.deleteFile(wkPutdir);
        String sourceFtpPath =
                "NCL"
                        + File.separator
                        + tbsdy
                        + File.separator
                        + "2FSAP"
                        + File.separator
                        + "DATA"
                        + File.separator
                        + "PUTF"
                        + File.separator
                        + wkPutfile; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, putDir);
        if (sourceFile != null) {
            wkPutdir = getLocalPath(sourceFile);
        }

        // FD-PUTF檔案存在，執行0000-MAIN-RTN 主程式
        // 012800     IF  ATTRIBUTE  RESIDENT     OF FD-PUTF   IS = VALUE(TRUE)
        if (textFile.exists(wkPutdir)) {
            // 012900       PERFORM  0000-MAIN-RTN    THRU   0000-MAIN-EXIT.
            _0000_main();
        }
        // 013000 0000-END-RTN.
        // 顯示訊息、關閉批次日期檔、結束程式
        // 013100     DISPLAY "SYM/CL/BH/CONV27/1 GENERATE DATA/CL/BH/PUTF OK".
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "SYM/CL/BH/CONV27/1 GENERATE DATA/CL/BH/PUTF OK");
        // 013200     CLOSE   FD-BHDATE  WITH SAVE.
        // 013300     STOP RUN.
        batchResponse();
    }

    private void _0000_main() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV271Lsnr _0000_main");
        // 013500 0000-MAIN-RTN.
        //// 開啟檔案
        // 013600     OPEN       INPUT             FD-PUTF.
        // 013700     OPEN       OUTPUT            FD-111981.
        // 013800     OPEN       INQUIRY           BOTSRDB.
        // 013900     MOVE       0            TO   WK-CTL-FLAG.

        //// INITIAL
        // 014050     PERFORM    INITIAL-RTN       THRU INITIAL-EXIT.
        init();
        // 014100 0000-MAIN-LOOP.
        //// 循序讀取"DATA/CL/BH/PUTF/..."，直到檔尾，跳到0000-MAIN-LAST
        // 014200     READ  FD-PUTF  AT  END    GO TO  0000-MAIN-LAST.
        List<String> lines = textFile.readFileContent(wkPutdir, CHARSET);
        for (String detail : lines) {
            // PUTF-CTL=11,21 明細資料
            ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境

            text2VoFormatter.format(detail, filePutf);
            text2VoFormatter.format(detail, fileSumPUTF);
            if (parse.isNumeric(filePutf.getCtl())) {
                putfCtl = parse.string2Integer(filePutf.getCtl());
            } else {
                ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "Ctl轉型錯誤");
                putfCtl = 0;
            }
            putfRcptid = filePutf.getRcptid();
            putfCode = filePutf.getCode();
            putfUserdata = formatUtil.padX(filePutf.getUserdata(), 40);
            putfAmt =
                    parse.string2BigDecimal(
                            parse.isNumeric(filePutf.getAmt()) ? filePutf.getAmt() : "0");
            putfDate =
                    parse.string2Integer(
                            parse.isNumeric(filePutf.getEntdy()) ? filePutf.getEntdy() : "0");
            putfTime =
                    parse.string2Integer(
                            parse.isNumeric(filePutf.getTime()) ? filePutf.getTime() : "0");
            putfSitdate =
                    parse.string2Integer(
                            parse.isNumeric(filePutf.getSitdate()) ? filePutf.getSitdate() : "0");
            putfTxtype = filePutf.getTxtype();
            putfBdate =
                    parse.string2Integer(
                            parse.isNumeric(fileSumPUTF.getBdate()) ? fileSumPUTF.getBdate() : "0");
            putfEdate =
                    parse.string2Integer(
                            parse.isNumeric(fileSumPUTF.getEdate()) ? fileSumPUTF.getEdate() : "0");
            //// PUTF-CTL=11,21 明細資料
            ////  A.WK-CTL-FLAG設為0
            ////  B.執行MOVE-REC-RTN，搬PUTF-...到WK-111981-REC...
            ////  C.執行FIND-CLCMP-RTN，計算當日此虛擬帳號之餘額
            ////  D.執行SWH-RCPTID-RTN，計算餘額
            ////  E.執行USERDATA-IN-RTN，寫檔FD-111981(DTL)
            ////  F.GO TO 0000-MAIN-LOOP，LOOP讀下一筆FD-PUTF

            // 014400     IF  PUTF-CTL                 =    11 OR 21
            if (putfCtl == 11 || putfCtl == 21) {
                // 014450         MOVE    0                TO   WK-CTL-FLAG
                wkCtlFlag = 0;
                // 014500         PERFORM MOVE-REC-RTN     THRU MOVE-REC-EXIT
                move_Rec();
                // 014700         PERFORM FIND-CLCMP-RTN   THRU FIND-CLCMP-EXIT
                find_Clcmp();
                if (goToEnd) return;
                // 014800         PERFORM SWH-RCPTID-RTN   THRU SWH-RCPTID-EXIT
                swh_Rcptid();
                // 014900         PERFORM USERDATA-IN-RTN  THRU USERDATA-IN-EXIT
                userdata_In();
                // 015000         GO TO 0000-MAIN-LOOP.
                continue;
            }
            //// PUTF-CTL=12,22 & WK-CTL-FLAG NOT = 0，跳到0000-MAIN-LOOP，LOOP讀下一筆FD-PUTF
            // 015020     IF  ( PUTF-CTL = 12 OR 22 ) AND ( WK-CTL-FLAG NOT = 0 )
            if ((putfCtl == 12 || putfCtl == 22) && wkCtlFlag != 0) {
                // 015060         GO TO 0000-MAIN-LOOP.
                continue;
            }
            //// PUTF-CTL=12,22 彙總資料
            ////  A.WK-CTL-FLAG設為1
            ////  B.執行CLDTL-SUM-RTN，依代收類別找出大於當日之所有提取或所有存入之金額
            ////  C.執行CLCMP-SUM-RTN，依代收類別FIND NEXT DB-CLCMP-IDX1，累計總餘額
            ////  D.執行FILE-SUM-RTN，寫檔FD-111981(SUM)
            ////  E.執行INITIAL-RTN，INITIAL
            ////  F.GO TO 0000-MAIN-LOOP，LOOP讀下一筆FD-PUTF
            // 015100     IF  PUTF-CTL                 =    12 OR 22
            if (putfCtl == 12 || putfCtl == 22) {
                // 015140         MOVE    1                TO   WK-CTL-FLAG
                // 015150         MOVE    PUTF-CTL         TO   WK-111981-SUM-CTL
                // 015200         MOVE    PUTF-BDATE       TO   WK-111981-SDATE
                // 015300         MOVE    PUTF-EDATE       TO   WK-111981-EDATE
                wkCtlFlag = 1;
                wk111981SumCtl = putfCtl;
                wk111981Sdate = putfBdate;
                wk111981Edate = putfEdate;
                // 015320         PERFORM CLDTL-SUM-RTN    THRU CLDTL-SUM-EXIT
                cldtl_Sum();
                // 015340         PERFORM CLCMP-SUM-RTN    THRU CLCMP-SUM-EXIT
                clcmp_Sum();
                // 015360         PERFORM FILE-SUM-RTN     THRU FILE-SUM-EXIT
                file_Sum();
                // 015370         PERFORM INITIAL-RTN      THRU INITIAL-EXIT
                init();
                // 015400         GO TO 0000-MAIN-LOOP.
                continue;
            }
        }
        // 015500 0000-MAIN-LAST.
        //
        //// 關檔
        //
        // 015800     CLOSE    FD-PUTF             WITH  SAVE.
        // 015900     CHANGE   ATTRIBUTE  FILENAME OF FD-111981 TO WK-PUTDIR.
        // 016000     CLOSE    FD-111981           WITH  SAVE.
        textFile.deleteFile(wkPutdir);
        try {
            textFile.writeFileContent(wkPutdir, fileCONV271Contents, CHARSET);
            upload(wkPutdir, "DATA", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        // 016100     CLOSE    BOTSRDB.
        // 016200 0000-MAIN-EXIT.
    }

    private void move_Rec() {
        // 036170 MOVE-REC-RTN.
        //// 搬PUTF-...到WK-111981-REC...

        // 036270     MOVE  PUTF-CTL            TO    WK-111981-CTL     .
        wk111981Ctl = putfCtl;
        // 036370     MOVE  PUTF-CODE           TO    WK-111981-CODE    .
        wk111981Code = putfCode;
        wk111981Code1 = strutil.substr(wk111981Code, 0, 1);
        wk111981Code2 = strutil.substr(wk111981Code, 1, 2);
        wk111981Code3 = strutil.substr(wk111981Code, 2, 6);
        // 036470     MOVE  PUTF-RCPTID         TO    WK-111981-RCPTID  .
        wk111981Rcptid = putfRcptid;
        wk111981Rcptid1 = strutil.substr(wk111981Rcptid, 0, 1);
        wk111981Rcptid2 = strutil.substr(wk111981Rcptid, 1, 16);
        // 036570     MOVE  PUTF-DATE           TO    WK-111981-DATE    .
        // 036620     ADD   1000000             TO    WK-111981-DATE    .
        wk111981Date = putfDate + 1000000;
        // 036670     MOVE  PUTF-TIME           TO    WK-111981-TIME    .
        wk111981Time = putfTime;
        // 036770     MOVE  PUTF-USERDATA       TO    WK-111981-USERDATA.
        wk111981Userdata = filePutf.getUserdata();
        wk111981Userdata1 = strutil.substr(wk111981Userdata, 0, 30);
        wk111981Userdata2 = strutil.substr(wk111981Userdata, 30, 40);
        // 036870     MOVE  PUTF-SITDATE        TO    WK-111981-SITDATE .
        // 036920     ADD   1000000             TO    WK-111981-SITDATE .
        wk111981Sitdate = putfSitdate + 1000000;
        // 036970     MOVE  PUTF-TXTYPE         TO    WK-111981-TXTYPE  .
        wk111981Txtype = putfTxtype;
        // 037070     MOVE  PUTF-AMT            TO    WK-111981-AMT     .
        wk111981Amt = putfAmt;

        // 037075* 台北市退撫軍戶取匯款備註為機關名稱
        // 037080     IF    ( PUTF-CODE         =    "111981" )
        // 037085       AND ( PUTF-RCPTID(12:3) =    "016"    )
        String putfRcptid12 = putfRcptid.substring(11, 14);
        String putfUserdata8 = putfUserdata.substring(7, 8);
        if ("111981".equals(putfCode) && "016".equals(putfRcptid12)) {
            // 037090       IF  ( PUTF-USERDATA(8:1)  = @2B@  AND PUTF-TXTYPE= "R")
            if ("R".equals(putfTxtype)) { // todo:鍾 檢查是否為全型字
                // 037095         MOVE  WK-111981-USERDATA(20:21) TO WK-TSWH-PNAME
                wkTswhPname = wk111981Userdata.substring(20, 40);
                // 037100         MOVE  SPACE              TO  WK-111981-USERDATA
                wk111981Userdata = SPACE;
                wk111981Userdata1 = SPACE;
                wk111981Userdata2 = SPACE;
                // 037105         MOVE  WK-TSWH-PNAME      TO  WK-111981-USERDATA-1
                wk111981Userdata1 = formatUtil.padX(wkTswhPname, 30);
                wk111981Userdata = wk111981Userdata1 + wk111981Userdata2;
                // 037115         GO  TO  MOVE-REC-EXIT
                // 037120       ELSE
            } else {
                // 037125         GO  TO  MOVE-REC-EXIT.
            }
        }
        // 037170 MOVE-REC-EXIT.
    }

    private void find_Clcmp() {
        // 016500 FIND-CLCMP-RTN.
        //
        //// WK-FORWARD-...上一筆的資料
        //// 首筆或虛擬帳號不同時，往下一步驟執行
        //// 虛擬帳號相同時，結束本段落
        //
        // 016510     IF   (WK-FORWARD-RCPTID      =    SPACES               )
        // 016520       OR (WK-111981-RCPTID-2 NOT =    WK-FORWARD-RCPTID-2  )
        if (wkForwardRcptid.isEmpty() || !wk111981Rcptid2.equals(wkForwardRcptid2)) {
            // 016530       NEXT  SENTENCE
            // 016540     ELSE
        } else {
            // 016550       GO TO  FIND-CLCMP-EXIT.
            return;
        }
        // 016570*
        //// 若PUTF-RCPTID(1:1)="1"，搬PUTF-... 給WK-CLCMP-...
        //// 其他
        ////   A.WK-CLCMP-CODE=PUTF-CODE(1:1)+"1"+PUTF-CODE(3:4)
        ////   B.WK-CLCMP-RCPTID="1"+PUTF-RCPTID(2:15)
        //
        // 016600     IF     WK-111981-RCPTID-1    =    "1"
        if ("1".equals(wk111981Rcptid1)) {
            // 016700        MOVE WK-111981-CODE       TO   WK-CLCMP-CODE
            // 016800        MOVE WK-111981-RCPTID     TO   WK-CLCMP-RCPTID
            wkClcmpCode = wk111981Code;
            wkClcmpRcptid = wk111981Rcptid;
            wkClcmpRcptid1 = wkClcmpRcptid.substring(0, 1);
            wkClcmpRcptid2 = wkClcmpRcptid.substring(1, 16);
            // 016900     ELSE
        } else {
            // 017000        MOVE WK-111981-CODE       TO   WK-CLCMP-CODE
            // 017100        MOVE WK-111981-RCPTID     TO   WK-CLCMP-RCPTID
            // 017200        MOVE "1"                  TO   WK-CLCMP-CODE-2
            // 017300        MOVE "1"                  TO   WK-CLCMP-RCPTID-1.
            wkClcmpCode = wk111981Code;
            wkClcmpCode1 = wkClcmpCode.substring(0, 1);
            wkClcmpCode2 = "1";
            wkClcmpCode3 = wkClcmpCode.substring(2, 6);
            wkClcmpRcptid = wk111981Rcptid;
            wkClcmpRcptid1 = "1";
            wkClcmpRcptid2 = wkClcmpRcptid.substring(1, 16);
            wkClcmpCode = wkClcmpCode1 + wkClcmpCode2 + wkClcmpCode3;
            wkClcmpRcptid = wkClcmpRcptid1 + wkClcmpRcptid2;
        }
        // 017400*
        //// 將DB-CLCMP-IDX1指標移至開始
        // 017500     SET     DB-CLCMP-IDX1        TO   BEGINNING.
        //// KEY IS (DB-CLCMP-CODE, DB-CLCMP-RCPTID ) NO DUPLICATES;
        //// 依代收類別、銷帳號碼 FIND DB-CLCMP-IDX1收付比對檔，若有誤，GO TO 0000-END-RTN，關檔、結束程式
        // 017600     FIND DB-CLCMP-IDX1 AT DB-CLCMP-CODE   = WK-CLCMP-CODE
        // 017700                       AND DB-CLCMP-RCPTID = WK-CLCMP-RCPTID
        tClcmp = clcmpService.findById(new ClcmpId(wkClcmpCode, wkClcmpRcptid));
        // 017800       ON EXCEPTION
        // 017900       IF DMSTATUS(NOTFOUND)
        // 018000          GO TO 0000-END-RTN
        // 018100       ELSE
        // 018200          GO TO 0000-END-RTN.
        // 018210
        if (Objects.isNull(tClcmp)) {
            isNotConv = "Y";
            goToEnd = true;
            return;
        }

        //// 計算當日此虛擬帳號之餘額
        // 018220     MOVE    DB-CLCMP-AMT         TO   WK-BALANCE.
        wkBalance = tClcmp.getAmt();
        // 018240     PERFORM FIND-CLDTL-RTN       THRU FIND-CLDTL-EXIT.
        find_Cldtl();
        // 018260     MOVE    WK-BALANCE           TO   WK-111981-USERDATA-2.
        wk111981Userdata2 = formatUtil.pad9("" + wkBalance, 10);
        wk111981Userdata = wk111981Userdata1 + wk111981Userdata2;
        // 018300 FIND-CLCMP-EXIT.
    }

    private void find_Cldtl() {
        // 025000 FIND-CLDTL-RTN.
        // 025100* 當日有交易之虛擬分戶中找出大於當日之提取或存入之金額
        // 025150* 必須先加當日提款，再減當日存入，比照更正規則
        // 025200     PERFORM  FIND-DTL2-RTN       THRU  FIND-DTL2-EXIT.
        find_Dtl2();
        // 025300     PERFORM  FIND-DTL1-RTN       THRU  FIND-DTL1-EXIT.
        find_Dtl1();
        // 025400 FIND-CLDTL-EXIT.
    }

    private void find_Dtl2() {
        // 027800 FIND-DTL2-RTN.
        // 027900*  計算出大於本日之提取交易之金額
        //// WK-CLDTL-CODE=DB-CLCMP-CODE(1:1)+"2"+DB-CLCMP-CODE(3:4)
        //// WK-CLDTL-RCPTID="2"+DB-CLCMP-RCPTID(2:15)
        // 028000     MOVE DB-CLCMP-CODE        TO   WK-CLDTL-CODE.
        // 028100     MOVE DB-CLCMP-RCPTID      TO   WK-CLDTL-RCPTID.
        // 028200     MOVE "2"                  TO   WK-CLDTL-CODE-2.
        // 028300     MOVE "2"                  TO   WK-CLDTL-RCPTID-1.
        wkCldtlCode = tClcmp.getCode();
        wkCldtlRcptid = tClcmp.getRcptid();
        String wkCldtlCode1 = wkCldtlCode.substring(0, 1);
        String wkCldtlCode3 = wkCldtlCode.substring(2, 6);
        String wkCldtlRcptid3 = wkCldtlRcptid.substring(1, 16);
        wkCldtlCode = wkCldtlCode1 + "2" + wkCldtlCode3;
        wkCldtlRcptid = "2" + wkCldtlRcptid3;

        //// 將DB-CLDTL-IDX1指標移至開始
        //
        // 028400     SET  DB-CLDTL-IDX1        TO   BEGINNING.
        // 028500 FIND-DTL2-LOOP.
        //
        //// KEY IS ( DB-CLDTL-CODE, DB-CLDTL-RCPTID ) DUPLICATES LAST;
        //// 依 代收類別+銷帳編號 FIND NEXT 收付明細檔，若有誤
        ////  A.若不存在，GO TO FIND-DTL2-EXIT，結束本段落
        ////  B.其他，GO TO 0000-MAIN-RTN，應 GO TO 0000-END-RTN ???
        //
        // 028600     FIND NEXT DB-CLDTL-IDX1 AT DB-CLDTL-CODE   = WK-CLDTL-CODE
        // 028700                            AND DB-CLDTL-RCPTID = WK-CLDTL-RCPTID
        List<CldtlbyCodeRcptidHcodeBus> lCldtl =
                cldtlService.findbyCodeRcptidHcode(
                        wkCldtlCode, wkCldtlRcptid, 0, 0, Integer.MAX_VALUE);
        // 028800       ON EXCEPTION
        // 028900       IF DMSTATUS(NOTFOUND)
        // 029000          GO TO FIND-DTL2-EXIT
        if (Objects.isNull(lCldtl)) {
            return;
        }
        // 029100       ELSE
        // 029200          GO TO 0000-MAIN-RTN.
        for (CldtlbyCodeRcptidHcodeBus tCldtl : lCldtl) {
            //// 大於當日之交易，累計金額 WK-BALANCE
            //// GO TO FIND-DTL2-LOOP，LOOP讀下一筆CLDTL，直到NOTFOUND
            // 029300     IF   DB-CLDTL-DATE        >    FD-BHDATE-TBSDY
            if (tCldtl.getEntdy() > processDateInt) {
                // 029400       ADD   DB-CLDTL-AMT      TO   WK-BALANCE
                // 029500       GO TO FIND-DTL2-LOOP
                wkBalance = wkBalance.add(tCldtl.getAmt());
            }
            // 029600     ELSE
            // 029700       GO TO FIND-DTL2-LOOP.
        }
        // 029800 FIND-DTL2-EXIT.
    }

    private void find_Dtl1() {
        // 025700 FIND-DTL1-RTN.
        // 025800*  計算出大於本日之存入交易之金額
        // 025900     MOVE DB-CLCMP-CODE        TO   WK-CLDTL-CODE.
        // 026000     MOVE DB-CLCMP-RCPTID      TO   WK-CLDTL-RCPTID.
        wkCldtlCode = tClcmp.getCode();
        wkCldtlRcptid = tClcmp.getRcptid();
        //// 將DB-CLDTL-IDX1指標移至開始
        // 026100     SET  DB-CLDTL-IDX1        TO   BEGINNING.
        // 026200 FIND-DTL1-LOOP.
        //// KEY IS ( DB-CLDTL-CODE, DB-CLDTL-RCPTID ) DUPLICATES LAST;
        //// 依 代收類別+銷帳編號 FIND NEXT 收付明細檔，若有誤
        ////  A.若不存在，GO TO FIND-DTL1-EXIT，結束本段落
        ////  B.其他，GO TO 0000-MAIN-RTN，應 GO TO 0000-END-RTN ???

        // 026300     FIND NEXT DB-CLDTL-IDX1 AT DB-CLDTL-CODE   = WK-CLDTL-CODE
        // 026400                            AND DB-CLDTL-RCPTID = WK-CLDTL-RCPTID
        List<CldtlbyCodeRcptidHcodeBus> lCldtl =
                cldtlService.findbyCodeRcptidHcode(
                        wkCldtlCode, wkCldtlRcptid, 0, 0, Integer.MAX_VALUE);
        // 026500       ON EXCEPTION
        // 026600       IF DMSTATUS(NOTFOUND)
        if (Objects.isNull(lCldtl)) {
            // 026700          GO TO FIND-DTL1-EXIT
            return;
        }
        // 026800       ELSE
        // 026900          GO TO 0000-MAIN-RTN.
        //// 大於當日之交易，從累計金額 WK-BALANCE減掉
        //// GO TO FIND-DTL1-LOOP，LOOP讀下一筆CLDTL，直到NOTFOUND
        for (CldtlbyCodeRcptidHcodeBus tCldtl : lCldtl) {
            // 027000     IF   DB-CLDTL-DATE        >    FD-BHDATE-TBSDY
            if (tCldtl.getEntdy() > processDateInt) {
                // 027100       SUBTRACT DB-CLDTL-AMT   FROM WK-BALANCE
                wkBalance = wkBalance.subtract(tCldtl.getAmt());
            }
            // 027200       GO TO FIND-DTL1-LOOP
        }
        // 027300     ELSE
        // 027400       GO TO FIND-DTL1-LOOP.
        // 027500 FIND-DTL1-EXIT.
    }

    private void swh_Rcptid() {
        // 018600 SWH-RCPTID-RTN.
        //// WK-FORWARD-...上一筆的資料
        //// 首筆時
        ////  A.保留資料至WK-FORWARD-RCPTID,AMT
        ////  B.執行PANME-RTN，準備繳款人名稱
        ////  C.搬WK-BALANCE至WK-111981-USERDATA-2
        ////  D.結束本段落
        // 018700     IF WK-FORWARD-RCPTID         =     SPACE
        if (wkForwardRcptid.isEmpty()) {
            // 018800        MOVE    WK-111981-RCPTID  TO    WK-FORWARD-RCPTID
            // 018900        MOVE    WK-111981-AMT     TO    WK-FORWARD-AMT
            wkForwardRcptid = wk111981Rcptid;
            wkForwardRcptid1 = wkForwardRcptid.substring(0, 1);
            wkForwardRcptid2 = wkForwardRcptid.substring(1, 16);
            wkForwardAmt = wk111981Amt;
            // 019000        PERFORM PANME-RTN         THRU  PNAME-EXIT
            pname_Rtn();
            // 019100        MOVE    WK-BALANCE        TO    WK-111981-USERDATA-2
            wk111981Userdata2 = formatUtil.pad9("" + wkBalance, 10);
            wk111981Userdata = wk111981Userdata1 + wk111981Userdata2;
            // 019300        GO TO   SWH-RCPTID-EXIT.
            return;
        }
        // 019400*
        //// 虛擬帳號不同時
        ////  A.保留資料至WK-FORWARD-RCPTID,AMT
        ////  B.執行PANME-RTN，準備繳款人名稱
        ////  C.搬WK-BALANCE至WK-111981-USERDATA-2
        ////  D.結束本段落
        // 019500     IF   WK-FORWARD-RCPTID-2     NOT = WK-111981-RCPTID-2
        if (!wk111981Rcptid2.equals(wkForwardRcptid2)) {
            // 019600        MOVE    WK-111981-RCPTID  TO    WK-FORWARD-RCPTID
            // 019700        MOVE    WK-111981-AMT     TO    WK-FORWARD-AMT
            wkForwardRcptid = wk111981Rcptid;
            wkForwardRcptid1 = wkForwardRcptid.substring(0, 1);
            wkForwardRcptid2 = wkForwardRcptid.substring(1, 15);
            wkForwardAmt = wk111981Amt;
            // 019800        PERFORM PANME-RTN         THRU  PNAME-EXIT
            pname_Rtn();
            // 019900        MOVE    WK-BALANCE        TO    WK-111981-USERDATA-2
            wk111981Userdata2 = formatUtil.pad9("" + wkBalance, 10);
            wk111981Userdata = wk111981Userdata1 + wk111981Userdata2;
            // 020100        GO TO   SWH-RCPTID-EXIT
            return;
        } else {
            //// 虛擬帳號相同時
            // 020200     ELSE
            ////  WK-FORWARD-RCPTID-1="1"
            ////   A.從累計金額 WK-BALANCE減掉WK-FORWARD-AMT
            ////   B.保留資料至WK-FORWARD-AMT
            ////   C.執行PANME-RTN，準備繳款人名稱
            ////   D.搬WK-BALANCE至WK-111981-USERDATA-2
            ////   E.保留資料至WK-FORWARD-RCPTID
            // 020300          IF WK-FORWARD-RCPTID-1  =     "1"
            if ("1".equals(wkForwardRcptid1)) {
                // 020400            SUBTRACT WK-FORWARD-AMT FROM WK-BALANCE
                // 020500            MOVE WK-111981-AMT    TO    WK-FORWARD-AMT
                wkBalance = wkBalance.subtract(wkForwardAmt);
                wkForwardAmt = wk111981Amt;
                // 020600            PERFORM PANME-RTN     THRU  PNAME-EXIT
                pname_Rtn();
                // 020700            MOVE WK-BALANCE       TO    WK-111981-USERDATA-2
                // 020800            MOVE WK-111981-RCPTID TO    WK-FORWARD-RCPTID
                wk111981Userdata2 = formatUtil.pad9("" + wkBalance, 10);
                wk111981Userdata = wk111981Userdata1 + wk111981Userdata2;
                wkForwardRcptid = wk111981Rcptid;
                wkForwardRcptid1 = wkForwardRcptid.substring(0, 1);
                wkForwardRcptid2 = wkForwardRcptid.substring(1, 15);
                // 020900          ELSE
            } else {
                ////  WK-FORWARD-RCPTID-1="2"
                ////   A.累加WK-FORWARD-AMT到WK-BALANCE
                ////   B.保留資料至WK-FORWARD-AMT
                ////   C.執行PANME-RTN，準備繳款人名稱
                ////   D.搬WK-BALANCE至WK-111981-USERDATA-2
                ////   E.保留資料至WK-FORWARD-RCPTID
                // 021000            ADD  WK-FORWARD-AMT   TO    WK-BALANCE
                // 021100            MOVE WK-111981-AMT    TO    WK-FORWARD-AMT
                wkBalance = wkBalance.add(wkForwardAmt);
                wkForwardAmt = wk111981Amt;
                // 021200            PERFORM PANME-RTN     THRU  PNAME-EXIT
                pname_Rtn();
                // 021300            MOVE WK-BALANCE       TO    WK-111981-USERDATA-2
                // 021400            MOVE WK-111981-RCPTID TO    WK-FORWARD-RCPTID.
                wk111981Userdata2 = formatUtil.pad9("" + wkBalance, 10);
                wk111981Userdata = wk111981Userdata1 + wk111981Userdata2;
                wkForwardRcptid = wk111981Rcptid;
                wkForwardRcptid1 = wkForwardRcptid.substring(0, 1);
                wkForwardRcptid2 = wkForwardRcptid.substring(1, 15);
            }
        }
        // 021600 SWH-RCPTID-EXIT.
    }

    private void pname_Rtn() {
        // 035470 PANME-RTN.
        // 035570     IF      PUTF-CODE         =    "111801"
        // 035590       GO TO PNAME-EXIT.
        if ("111801".equals(putfCode)) return;
        // 035600* 臺北府退撫戶護因直接使用匯款備註當機關名稱   不用再加工
        // 035610     IF    ( PUTF-CODE         =    "111981" )
        // 035620       AND ( PUTF-RCPTID(12:3) =    "016"    )
        String putfRcptid12 = putfRcptid.substring(11, 14);
        if ("111981".equals(putfCode) && "016".equals(putfRcptid12)) return;
        // 035650         GO  TO  PNAME-EXIT.
        // 035660
        //
        //// 繳款人名稱
        //
        // 035670     MOVE  DB-CLCMP-PNAME      TO    WK-111981-USERDATA-1 .
        wk111981Userdata1 = strutil.substr(tClcmp.getPname(), 0, 30);
        // 035870 PNAME-EXIT.
    }

    private void userdata_In() {
        // 021900 USERDATA-IN-RTN.
        //
        //// PUTF-CODE(2:1)="1"，累計存入筆數、金額
        //// PUTF-CODE(2:1)="2"，累計提取筆數、金額
        //
        // 022000     IF      WK-111981-CODE-2     =    "1"
        if ("1".equals(wk111981Code2)) {
            // 022100        ADD  1                    TO   WK-111981-CNT1
            // 022200        ADD  WK-111981-AMT        TO   WK-111981-AMT1
            wk111981Cnt1++;
            wk111981Amt1 = wk111981Amt1.add(wk111981Amt);
            // 022300     ELSE
        } else {
            // 022400        ADD  1                    TO   WK-111981-CNT2
            // 022500        ADD  WK-111981-AMT        TO   WK-111981-AMT2.
            wk111981Cnt2++;
            wk111981Amt2 = wk111981Amt2.add(wk111981Amt);
            // 022600*
        }
        //// 寫檔FD-111981(DTL)
        // 022700     WRITE   111981-REC           FROM WK-111981-REC.
        fileCONV271Contents.add(_111981_Rec());
        // 022800 USERDATA-IN-EXIT.
    }

    private String _111981_Rec() {

        sb = new StringBuilder();
        sb.append(formatUtil.pad9(String.valueOf(wk111981Ctl), 2));
        sb.append(formatUtil.padX(wk111981Code1, 1));
        sb.append(formatUtil.padX(wk111981Code2, 1));
        sb.append(formatUtil.padX(wk111981Code3, 4));
        sb.append(formatUtil.padX(wk111981Rcptid1, 1));
        sb.append(formatUtil.padX(wk111981Rcptid2, 15));
        sb.append(formatUtil.padX("", 10));
        sb.append(formatUtil.pad9(String.valueOf(wk111981Date), 8));
        sb.append(formatUtil.pad9(String.valueOf(wk111981Time), 6));
        sb.append(formatUtil.padX("", 21));
        sb.append(formatUtil.padX(wk111981Userdata1, 30));
        sb.append(formatUtil.pad9(wk111981Userdata2, 10));
        sb.append(formatUtil.pad9(String.valueOf(wk111981Sitdate), 8));
        sb.append(formatUtil.padX(wk111981Txtype, 1));
        sb.append(formatUtil.pad9(String.valueOf(wk111981Amt), 12));
        sb.append(formatUtil.padX("", 40));
        return sb.toString();
    }

    private void cldtl_Sum() {
        // 030100 CLDTL-SUM-RTN.
        // 030200* 找出大於當日之所有提取或所有存入之金額
        // 030300     PERFORM  SUM-DTL1-RTN        THRU  SUM-DTL1-EXIT.
        sum_Dtl1();
        // 030400     PERFORM  SUM-DTL2-RTN        THRU  SUM-DTL2-EXIT.
        sum_Dtl2();
        // 030500 CLDTL-SUM-EXIT.
    }

    private void sum_Dtl1() {
        // 030800 SUM-DTL1-RTN.
        // 030900* 找出大於當日之所有存入之金額
        // 031000     MOVE WK-111981-CODE       TO   WK-CLDTL-CODE.
        // 031100     MOVE "1"                  TO   WK-CLDTL-CODE-2.
        wkCldtlCode = wk111981Code;
        wkCldtlCode1 = wkCldtlCode.substring(0, 1);
        wkCldtlCode2 = "1";
        wkCldtlCode3 = wkCldtlCode.substring(2, 6);
        wkCldtlCode = wkCldtlCode1 + wkCldtlCode2 + wkCldtlCode3;

        //// 將DB-CLDTL-IDX3指標移至開始
        // 031200     SET  DB-CLDTL-IDX3        TO   BEGINNING.
        // 031300 SUM-DTL1-LOOP.
        //// KEY ( DB-CLDTL-CODE, DB-CLDTL-DATE ) DUPLICATES LAST;
        //// 依 代收類別&代收日>本營業日 FIND NEXT 收付明細檔，若有誤
        ////  A.若不存在，GO TO SUM-DTL1-EXIT，結束本段落
        ////  B.其他，GO TO 0000-MAIN-RTN，應 GO TO 0000-END-RTN ???
        //
        // 031400     FIND NEXT DB-CLDTL-IDX3 AT DB-CLDTL-CODE   = WK-CLDTL-CODE
        // 031500                            AND DB-CLDTL-DATE   > FD-BHDATE-TBSDY
        List<CldtlbyCodeEntdyforCONV271Bus> lCldtl =
                cldtlService.findbyCodeEntdyforCONV271(
                        wkCldtlCode, processDateInt, 0, 0, Integer.MAX_VALUE);
        // 031600       ON EXCEPTION
        // 031700       IF DMSTATUS(NOTFOUND)
        if (Objects.isNull(lCldtl)) {
            // 031800          GO TO SUM-DTL1-EXIT
            return;
        }
        // 031900       ELSE
        // 032000          GO TO 0000-MAIN-RTN.
        // 032100
        //// 大於本日之交易，累計存入之金額 WK-NTBSD-SUM-AMT1
        for (CldtlbyCodeEntdyforCONV271Bus tCldtl : lCldtl) {
            // 032200     ADD  DB-CLDTL-AMT         TO   WK-NTBSD-SUM-AMT1.
            wkNtbsdSumAmt1 = wkNtbsdSumAmt1.add(tCldtl.getAmt());
            //// LOOP讀下一筆CLDTL，直到NOTFOUND
            // 032500     GO TO SUM-DTL1-LOOP.
        }
        // 032600 SUM-DTL1-EXIT.
    }

    private void sum_Dtl2() {
        // 032900 SUM-DTL2-RTN.
        // 033000* 找出大於當日之所有提取之金額
        // 033100     MOVE WK-111981-CODE       TO   WK-CLDTL-CODE.
        // 033150     MOVE "2"                  TO   WK-CLDTL-CODE-2.
        wkCldtlCode = wk111981Code;
        wkCldtlCode1 = wkCldtlCode.substring(0, 1);
        wkCldtlCode2 = "2";
        wkCldtlCode3 = wkCldtlCode.substring(2, 6);
        wkCldtlCode = wkCldtlCode1 + wkCldtlCode2 + wkCldtlCode3;
        //// 將DB-CLDTL-IDX3指標移至開始
        // 033200     SET  DB-CLDTL-IDX3        TO   BEGINNING.
        // 033300 SUM-DTL2-LOOP.
        //// KEY ( DB-CLDTL-CODE, DB-CLDTL-DATE ) DUPLICATES LAST;
        //// 依 代收類別&代收日>本營業日 FIND NEXT 收付明細檔 ，若有誤
        ////  A.若不存在，GO TO SUM-DTL2-EXIT，結束本段落
        ////  B.其他，GO TO 0000-MAIN-RTN，應 GO TO 0000-END-RTN ???
        // 033400     FIND NEXT DB-CLDTL-IDX3 AT DB-CLDTL-CODE   = WK-CLDTL-CODE
        // 033500                            AND DB-CLDTL-DATE   > FD-BHDATE-TBSDY
        List<CldtlbyCodeEntdyforCONV271Bus> lCldtl =
                cldtlService.findbyCodeEntdyforCONV271(
                        wkCldtlCode, processDateInt, 0, 0, Integer.MAX_VALUE);
        // 033600       ON EXCEPTION
        // 033700       IF DMSTATUS(NOTFOUND)
        if (Objects.isNull(lCldtl)) {
            // 033800          GO TO SUM-DTL2-EXIT
            return;
        }
        // 033900       ELSE
        // 034000          GO TO 0000-MAIN-RTN.
        // 034100
        //// 大於本日之交易，累計提取之金額 WK-NTBSD-SUM-AMT2
        // 034300
        for (CldtlbyCodeEntdyforCONV271Bus tCldtl : lCldtl) {
            // 034200     ADD  DB-CLDTL-AMT         TO   WK-NTBSD-SUM-AMT2
            wkNtbsdSumAmt2 = wkNtbsdSumAmt2.add(tCldtl.getAmt());
            //// LOOP讀下一筆CLDTL，直到NOTFOUND
            // 034500     GO TO SUM-DTL2-LOOP.
        }
        // 034600 SUM-DTL2-EXIT.
    }

    private void clcmp_Sum() {
        // 023100 CLCMP-SUM-RTN.
        //// 將DB-CLCMP-IDX1指標移至開始
        // 023200     SET     DB-CLCMP-IDX1        TO   BEGINNING.
        // 023300 CLCMP-SUM-LOOP.
        //// KEY IS (DB-CLCMP-CODE, DB-CLCMP-RCPTID ) NO DUPLICATES;
        //// 依代收類別 FIND NEXT DB-CLCMP-IDX1收付比對檔，若有誤
        ////  若NOTFOUND，
        ////   A.扣除大於當日之資料
        ////   B.GO TO CLCMP-SUM-EXIT，結束本段落
        ////  其他，GO TO 0000-MAIN-EXIT，關檔、結束程式
        // 023400     FIND NEXT DB-CLCMP-IDX1 AT DB-CLCMP-CODE = WK-CODE
        List<ClcmpbyCodeBus> lClcmp = clcmpService.findbyCode(wkCode, 0, Integer.MAX_VALUE);
        // 023500       ON EXCEPTION
        // 023600       IF DMSTATUS(NOTFOUND)
        if (Objects.isNull(lClcmp)) {
            // 023620         SUBTRACT WK-NTBSD-SUM-AMT1 FROM WK-111981-AMT3
            // 023640         ADD      WK-NTBSD-SUM-AMT2 TO   WK-111981-AMT3
            wk111981Amt3 = wk111981Amt3.subtract(wkNtbsdSumAmt1);
            wk111981Amt3 = wk111981Amt3.add(wkNtbsdSumAmt2);
            // 023700         GO TO CLCMP-SUM-EXIT
            return;
        }
        // 023800       ELSE
        // 023900         GO TO 0000-MAIN-EXIT.
        for (ClcmpbyCodeBus tClcmp : lClcmp) {
            //// 依代收類別累計總餘額
            // 024000     ADD   DB-CLCMP-AMT           TO   WK-111981-AMT3.
            wk111981Amt3 = wk111981Amt3.add(tClcmp.getAmt());
            //// LOOP讀下一筆CLCMP，直到NOTFOUND
            // 024100     GO TO CLCMP-SUM-LOOP.
        }
        // 024200 CLCMP-SUM-EXIT.
    }

    private void file_Sum() {
        // 024500 FILE-SUM-RTN.
        //// 寫檔FD-111981(SUM)

        // 024600     WRITE   111981-REC           FROM WK-111981-SUM.
        fileCONV271Contents.add(_111981_Sum());

        // 024700 FILE-SUM-EXIT.
    }

    private String _111981_Sum() {

        sb = new StringBuilder();
        sb.append(formatUtil.pad9(String.valueOf(wk111981SumCtl), 2));
        sb.append(formatUtil.padX("", 6));
        sb.append(formatUtil.pad9(String.valueOf(wk111981Sdate), 8));
        sb.append(formatUtil.pad9(String.valueOf(wk111981Edate), 8));
        sb.append(formatUtil.pad9(String.valueOf(wk111981Cnt1), 6));
        sb.append(formatUtil.pad9(String.valueOf(wk111981Amt1), 13));
        sb.append(formatUtil.pad9(String.valueOf(wk111981Cnt2), 6));
        sb.append(formatUtil.pad9(String.valueOf(wk111981Amt2), 13));
        sb.append(formatUtil.pad9(String.valueOf(wk111981Amt3), 13));
        sb.append(formatUtil.padX("", 95));

        return sb.toString();
    }

    private void init() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV271Lsnr init");

        // 034900 INITIAL-RTN.
        // 035000     MOVE       SPACE          TO   WK-FORWARD-RCPTID,
        // 035050                                    WK-111981-REC    ,
        // 035070                                    WK-111981-SUM    .
        wkForwardRcptid = SPACE;
        wkForwardRcptid1 = SPACE;
        wkForwardRcptid2 = SPACE;

        // 035100     MOVE       0              TO   WK-RPT-CNT       ,
        // 035200                                    WK-BALANCE       ,
        // 035210                                    WK-111981-CNT1   ,
        // 035220                                    WK-111981-AMT1   ,
        // 035230                                    WK-111981-CNT2   ,
        // 035240                                    WK-111981-AMT2   ,
        // 035250                                    WK-111981-AMT3   .
        wkBalance = BigDecimal.ZERO;
        wk111981Cnt1 = 0;
        wk111981Amt1 = BigDecimal.ZERO;
        wk111981Cnt2 = 0;
        wk111981Amt2 = BigDecimal.ZERO;
        wk111981Amt3 = BigDecimal.ZERO;
    }

    private void moveErrorResponse(LogicException e) {
        // this.event.setPeripheryRequest();

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
        if ("27X1111981".equals(wkFilename)) {
            responseTextMap.put("WK_FILENAME", "27X1111981");
        } else if ("27X1111801".equals(wkFilename)) {
            responseTextMap.put("WK_FILENAME", "27X1111801");
        }
        responseTextMap.put("ISNOTCONV", isNotConv);
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", responseTextMap);
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
}
