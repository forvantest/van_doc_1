/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONV302007;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FilePUTF;
import com.bot.ncl.util.fileVo.FileSumPUTF;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
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
import com.bot.txcontrol.util.text.format.Text2VoFormatter;
import com.bot.txcontrol.util.transmit.FsapSyncSftpService;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("CONV302007Lsnr")
@Scope("prototype")
public class CONV302007Lsnr extends BatchListenerCase<CONV302007> {

    @Autowired private FsapSync fsapSync;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private Parse parse;
    @Autowired private DateUtil dateUtil;
    @Autowired private ClmrService clmrService;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FilePUTF filePutf;
    @Autowired private FileSumPUTF fileSumPutf;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    private CONV302007 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET = "UTF-8";
    private static final String CHARSET_BIG5 = "BIG5";
    private static final String PATH_SEPARATOR = File.separator;
    private static final String PAGE_SEPARATOR = "\u000C";
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private String wkPutdir;
    private String wkReport2dir;
    private String wkReport3dir;
    private StringBuilder sb;
    private final List<String> fileReport2Contents = new ArrayList<>();
    private final List<String> fileReport3Contents = new ArrayList<>();
    private final List<String> file302007Contents = new ArrayList<>();
    private String wkPutfile;
    private String wkConvfile;

    private ClmrBus tClmr = new ClmrBus();

    private Map<String, String> labelMap;
    private Map<String, String> textMap;
    private String processDate;
    private int nnbsdy;
    private String tbsdy;
    private int nbsdy;
    private int wkYYYMMDD;
    private String wkFdate;
    private String wkCdate;
    private int wkDate;
    private int wkPage = 0;
    private int wkTholiday1;
    private String wkBdate;
    private String wkEdate;
    private String wkHaveRpt = "N";
    private String wk530021Code = "";
    private String wk530021Flag = "N";
    private String wkCompdate = "";
    private int wkSubcnt = 0;
    private int wkTotcnt = 0;
    private int wkPctl = 0;
    private int wkSeqno = 0;
    private String wkCompno;
    private String wkUserdata;
    private String wkNo;
    private String wkChkdg;
    private String wkPbrno = "";
    private String wkRcptid;
    private String wkEledate = "0";
    private String wk530021Eledate;
    private int eledateYYYY;
    private int eledateMM;
    private int eledateDD;
    private String wk530021Indate;
    private int wkCheckno;
    private String wkCode;
    private BigDecimal wkSubamt = BigDecimal.ZERO;
    private BigDecimal wkTotamt = BigDecimal.ZERO;
    private boolean firstPage;
    private boolean go_to_302007_F = false;
    // --302007--
    private int _302007BhdateR = 0;
    private int _302007BhdateP = 0;
    private String _302007DateR;
    private int _302007CdcntR = 0;
    private BigDecimal _302007CdamtR = BigDecimal.ZERO;
    private BigDecimal _302007CdfeeR = BigDecimal.ZERO;
    private int _302007TotcntR = 0;
    private BigDecimal _302007TotamtR = BigDecimal.ZERO;
    private String _302007CnameR = "";
    private int _302007PageR;
    private int _302007PdateR;
    // --PUTF--
    private String putfCtl;
    private String putfDate;
    private String putfCllbr;
    private String putfSitdate;
    private String putfAmt;
    private String putfCode;
    private String putfUserdata;
    private String putfRcptid;
    private String putfFiller;
    private String putfTxtype;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(CONV302007 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV302007Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONV302007 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV302007Lsnr run()");
        init(event);
        main();

        batchResponse();
    }

    private void init(CONV302007 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV302007Lsnr init");
        this.event = event;
        //// 讀批次日期檔；若讀不到，顯示訊息，結束程式
        //
        // 030600     READ    FD-BHDATE AT END DISPLAY "READ FD-BHDATE ERROR"
        // 030700          STOP RUN.

        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();

        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        nnbsdy = parse.string2Integer(labelMap.get("NNBSDY"));
        //// 設定作業日、檔名日期變數值
        //// WK-FDATE PIC 9(06) <-WK-PUTDIR'S變數
        //// WK-CDATE PIC 9(06) <-WK-CONVDIR'S變數
        //
        // 030800     MOVE    FD-BHDATE-TBSDY     TO     WK-YYYMMDD.
        // 030900     MOVE    FD-BHDATE-TBSDY     TO     WK-FDATE,WK-CDATE.
        wkYYYMMDD = parse.string2Integer(processDate);
        wkFdate = formatUtil.pad9(processDate, 8).substring(2, 8);
        wkCdate = formatUtil.pad9(processDate, 8).substring(2, 8);
        //// CALL "DATE_TIME OF DTLIB"
        // 031000     CHANGE ATTRIBUTE TITLE OF "DTLIB" TO "*SYSTEM1/DTLIB.".
        // 031100     CALL "DATE_TIME OF DTLIB" USING PARA-DTREC
        // 031200     END-CALL                                  .
        //// PARA-YYMMDD PIC 9(06) 國曆日期 For 印表日期
        // 031300     MOVE    PARA-YYMMDD         TO     WK-DATE.
        wkDate = parse.string2Integer(dateUtil.getNowStringRoc());
        // 003400 FD  REPORTFL2
        // 003500     VALUE  OF  TITLE  IS  "BD/CL/BH/009/30200/2."
        wkReport2dir =
                fileDir
                        + CONVF_RPT
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + "CL-BH-009-30200-2";
        // 004000 FD  REPORTFL3
        // 004100     VALUE  OF  TITLE  IS  "BD/CL/BH/009/30200/3."
        wkReport3dir =
                fileDir
                        + CONVF_RPT
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + "CL-BH-009-30200-3";
    }

    private void main() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV302007Lsnr main");
        //// 執行029-POINTNBSDY-RTN，找到下營業日的日曆資料 For 530021-CHKEDATE-RTN
        // 031400     PERFORM 029-POINTNBSDY-RTN  THRU   029-POINTNBSDY-EXIT.
        //// FD-BHDATE-NNBSDY 下下營業日
        //// 302007-BHDATE-R  :解繳日期(302007-TIT3'S變數)
        //// 302007-BHDATE-P  :解繳日期(302007-TIT33'S變數)
        // 031500       MOVE  FD-BHDATE-NNBSDY    TO     302007-BHDATE-R.
        // 031600       MOVE  FD-BHDATE-NNBSDY    TO     302007-BHDATE-P.

        _302007BhdateR = nnbsdy;
        _302007BhdateP = nnbsdy;

        //// 將CDB-DATE-IDX指標移至開始
        // 031800     SET CDB-DATE-IDX TO BEGINNING.
        //// 執行302007-CHKRDAY-RTN 判斷解繳日前一天是否為假日，如假日，則解繳日為下一營榮日(For 報表)

        // 031900     PERFORM 302007-CHKRDAY-RTN  THRU    302007-CHKRDAY-EXIT.
        _302007_Chkrday();
        // 032000     IF WK-THOLIDAY1 = 1
        if (wkTholiday1 == 1) {
            // 032100       MOVE  FD-BHDATE-NBSDY     TO     302007-BHDATE-R
            // 032200       MOVE  FD-BHDATE-NBSDY     TO     302007-BHDATE-P.
            _302007BhdateR = nbsdy;
            _302007BhdateP = nbsdy;
        }
        // 032300     CLOSE   FD-CLNDR   WITH SAVE.

        //// 設定檔名變數值
        //// WK-PUTFILE  PIC X(10) <--WK-PUTDIR'S變數
        //// WK-CONVFILE PIC X(10) <--WK-CONVDIR'S變數
        //// WK-PUTDIR  <-"DATA/CL/BH/PUTF/" +WK-FDATE+"/27X1350003."
        //// WK-CONVDIR <-"DATA/CL/BH/CONVF/"+WK-CDATE+"/27X1350003."

        // 032500     MOVE    "27X1350003"        TO     WK-PUTFILE,WK-CONVFILE.
        wkPutfile = "27X1350003";
        wkConvfile = "27X1350003";

        // 032600     CHANGE  ATTRIBUTE FILENAME OF FD-PUTF   TO WK-PUTDIR.
        // 032700     CHANGE  ATTRIBUTE FILENAME OF FD-302007 TO WK-CONVDIR.
        String putfDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + "PUTF"
                        + PATH_SEPARATOR
                        + wkFdate;
        wkPutdir = putfDir + PATH_SEPARATOR + wkPutfile;
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
        File sourceFile = downloadFromSftp(sourceFtpPath, putfDir);
        if (sourceFile != null) {
            wkPutdir = getLocalPath(sourceFile);
        }

        //// FD-PUTF檔案存在，開啟FD-CLNDR、執行302007-RTN、關閉FD-CLNDR
        // 032800     IF  ATTRIBUTE  RESIDENT  OF  FD-PUTF IS = VALUE(TRUE)
        if (textFile.exists(wkPutdir)) {
            // 032850       OPEN INPUT   FD-CLNDR
            // 032900       PERFORM    302007-RTN  THRU  302007-EXIT
            _302007();
            // 032950       CLOSE   FD-CLNDR   WITH SAVE.
        }
        // 033000 0000-END-RTN.
    }

    private void _302007() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV302007Lsnr _302007");
        // 033700 302007-RTN.
        // 033800***  REPORT-RTN
        // 033900 302007-REPORT2.

        //// 開啟FD-PUTF
        // 034000     OPEN    INPUT   FD-PUTF.
        // 034100 302007-R2NEXT.
        //// 循序讀取FD-PUTF，直到檔尾，跳到302007-R2LAST
        //
        // 034200     READ   FD-PUTF    AT  END  GO TO  302007-R2LAST.
        List<String> lines = textFile.readFileContent(wkPutdir, CHARSET);
        for (String detail : lines) {
            text2VoFormatter.format(detail, filePutf);
            text2VoFormatter.format(detail, fileSumPutf);
            putfCtl = filePutf.getCtl();
            putfDate =
                    formatUtil.pad9(
                            filePutf.getEntdy().trim().isEmpty() ? "0" : filePutf.getEntdy(), 6);
            putfCode = filePutf.getCode();
            putfAmt = filePutf.getAmt().trim().isEmpty() ? "0" : filePutf.getAmt();
            putfRcptid = filePutf.getRcptid();
            putfFiller = filePutf.getFiller();
            putfTxtype = filePutf.getTxtype();
            putfCllbr = filePutf.getCllbr();
            putfSitdate = filePutf.getSitdate();
            putfUserdata = filePutf.getUserdata();
            _302007_R2();
        }
        //// 038300 302007-R2LAST.
        _302007_R2last();

        if (!go_to_302007_F) {
            // 038900****
            // 039000 302007-REPORT3.
            //// 開啟檔案
            // 039100     OPEN    OUTPUT  REPORTFL3.
            // 039200     OPEN    INPUT   FD-PUTF.
            // 039300     MOVE    SPACES  TO     WK-COMPNO,WK-PBRNO.
            // 039400     MOVE    0       TO     WK-TOTCNT WK-TOTAMT.
            wkCompno = "";
            wkPbrno = "";
            wkTotcnt = 0;
            wkTotamt = new BigDecimal(0);
            // 039500 302007-R3NEXT.
            //// 循序讀取FD-PUTF，直到檔尾，跳到302007-R3LAST
            // 039600     READ   FD-PUTF    AT  END  GO TO  302007-R3LAST.
            lines = textFile.readFileContent(wkPutdir, CHARSET);
            firstPage = true;
            for (String detail : lines) {
                text2VoFormatter.format(detail, filePutf);
                text2VoFormatter.format(detail, fileSumPutf);
                putfCtl = filePutf.getCtl();
                putfDate =
                        formatUtil.pad9(
                                filePutf.getEntdy().trim().isEmpty() ? "0" : filePutf.getEntdy(),
                                6);
                putfCode = filePutf.getCode();
                putfAmt = filePutf.getAmt().trim().isEmpty() ? "0" : filePutf.getAmt();
                putfRcptid = filePutf.getRcptid();
                putfFiller = filePutf.getFiller();
                putfTxtype = filePutf.getTxtype();
                putfCllbr = filePutf.getCllbr();
                putfSitdate = filePutf.getSitdate();
                putfUserdata = filePutf.getUserdata();
                _302007_R3();
            }
            // 044800 302007-R3LAST.
            _302007_R3last();
        }

        // 045100***  FILE-RTN
        // 045200 302007-FILE.
        //// 讀FD-PUTF寫FD-302007("DATA/CL/BH/CONVF/"+WK-CDATE+"/27X1350003.")
        //// 開啟檔案
        // 045300     OPEN      OUTPUT    FD-302007.
        // 045400     OPEN      INPUT     FD-PUTF.
        //// 清變數值
        // 045500     MOVE      0                 TO      WK-TOTCNT,WK-TOTAMT.
        wkTotcnt = 0;
        wkTotamt = new BigDecimal(0);
        //// 搬相關資料到302007-REC...
        // 045600     MOVE      SPACES            TO      302007-REC.
        // 045700     MOVE      "1"               TO      302007-RC.
        String _302007Rc = "1";
        // 045800     MOVE      "004"             TO      302007-SEND.
        String _302007Send = "004";
        // 045900     MOVE      "TPC"             TO      302007-RECV.
        String _302007Recv = "TPC";
        // 046000     MOVE      102               TO      302007-MTYPE.
        String _302007Mtype = "102";
        // 046100     MOVE      WK-YYYMMDD        TO      302007-DATE.
        String _302007Date = "" + wkYYYMMDD;
        // 046200     MOVE      2                 TO      302007-TX.
        String _302007Tx = "2";
        //// 寫檔FD-302007(FIRST RECORD)
        // 046300     WRITE     302007-REC.
        // 005400  01     302007-REC.
        // 005500     03  302007-RC              PIC 9(01).
        // 005600     03  302007-SEND            PIC X(08).
        // 005700     03  302007-RECV            PIC X(08).
        // 005800     03  302007-MTYPE           PIC 9(03).
        // 005900     03  302007-DATE            PIC 9(07).
        // 006000     03  302007-1.
        // 006100      05 302007-TX              PIC 9(01).
        // 006200      05 FILLER                 PIC X(92).
        // 006300     03  302007-2  REDEFINES  302007-1.
        // 006400      05 302007-ACT             PIC 9(14).
        // 006500      05 302007-AMT             PIC 9(12)V99.
        // 006600      05 302007-CHKDG           PIC 9(01).
        // 006700      05 FILLER                 PIC X(02).
        // 006750      05 302007-RECEIPT         PIC X(05).
        // 006800      05 302007-KIND            PIC 9(02).
        // 006900      05 302007-NO              PIC X(11).
        // 007000      05 FILLER                 PIC X(02).
        // 007100      05 302007-SJA0            PIC X(04).
        // 007200      05 302007-ELEDATE         PIC 9(07).
        // 007300      05 FILLER                 PIC X(31).
        // 007400     03  302007-3  REDEFINES  302007-1.
        // 007500      05 302007-ZERO            PIC X(26).
        // 007600      05 302007-TOTAMT          PIC 9(14)V99.
        // 007700      05 302007-TOTCNT          PIC 9(10).
        // 007800      05 FILLER                 PIC X(41).
        sb = new StringBuilder();
        sb.append(formatUtil.pad9(_302007Rc, 1));
        sb.append(formatUtil.padX(_302007Send, 8));
        sb.append(formatUtil.padX(_302007Recv, 8));
        sb.append(formatUtil.pad9(_302007Mtype, 3));
        sb.append(formatUtil.pad9(_302007Date, 7));
        sb.append(formatUtil.pad9(_302007Tx, 1));
        sb.append(formatUtil.padX("", 92));
        file302007Contents.add(sb.toString());

        // 046400 302007-FNEXT.
        //// 循序讀取"DATA/CL/BH/PUTF/..."，直到檔尾，跳到302007-FLAST 寫總數
        // 046500     READ   FD-PUTF    AT  END  GO TO  302007-FLAST.
        lines = textFile.readFileContent(wkPutdir, CHARSET);
        for (String detail : lines) {
            text2VoFormatter.format(detail, filePutf);
            putfCtl = filePutf.getCtl();
            putfDate =
                    formatUtil.pad9(
                            filePutf.getEntdy().trim().isEmpty() ? "0" : filePutf.getEntdy(), 6);
            putfCode = filePutf.getCode();
            putfAmt = filePutf.getAmt().trim().isEmpty() ? "0" : filePutf.getAmt();
            putfRcptid = filePutf.getRcptid();
            putfFiller = filePutf.getFiller();
            putfTxtype = filePutf.getTxtype();
            putfCllbr = filePutf.getCllbr();
            putfSitdate = filePutf.getSitdate();
            putfUserdata = filePutf.getUserdata();
            if ("NO DATA".equals(putfRcptid.trim())) { // todo:nodata
                continue;
            }
            _302007_F();
        }
        // 051500 302007-FLAST.
        _302007_Flast();
        // 052900 302007-EXIT.
    }

    private void _302007_Chkrday() {
        // todo:help
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV302007Lsnr _302007_Chkrday");
        // 063800 302007-CHKRDAY-RTN.
        // 063900     MOVE   0            TO WK-THOLIDAY1.
        wkTholiday1 = 0;

        //// FIND NEXT CDB-DATE-IDX日期檔 挑CDB-DATE-TBSDY大於本營業日，若有誤
        ////  若NOTFOUND，顯示訊息，結束本段落
        ////  其他，顯示錯誤訊息，結束本段落
        // 064000     FIND NEXT   CDB-DATE-IDX OF CDB-DATE-DDS
        // 064100       AT    CDB-DATE-TBSDY             >     FD-BHDATE-TBSDY
        // 064200       ON    EXCEPTION
        // 064300             IF DMSTATUS(NOTFOUND)
        // 064400                DISPLAY "DATE NOT FOUND"
        // 064500                GO TO 302007-CHKRDAY-EXIT
        // 064600             ELSE
        // 064700                DISPLAY "DB-DATE-DDS CAN'T OPEN"
        // 064800                GO TO 302007-CHKRDAY-EXIT.
        DateDto dateDto = new DateDto();
        dateDto.setDateS(processDate);
        dateDto.setDays(1);
        dateUtil.getCalenderDay(dateDto);
        String ntbsdy = dateDto.getDateS2String(false); // 後1天
        dateDto.init();
        dateDto.setDateS(processDate);
        dateDto.setDays(8);
        dateUtil.getCalenderDay(dateDto);
        String n8tbsdy = dateDto.getDateS2String(false); // 後8天

        List<TxBizDate> txBizDates =
                fsapSync.sy202ForAp(event.getPeripheryRequest(), ntbsdy, n8tbsdy);

        // 不是解繳日前一天(周一、四)，找下一日
        // 是解繳日前一天(周一或周四)，若為假日，WK-THOLIDAY1設為1
        // 064900* 判斷解繳日前一天是否為假日，如假日，則解繳日為下一營榮日
        // 065000     IF  CDB-DATE-WEEKDY NOT= 1 AND  CDB-DATE-WEEKDY NOT= 4
        // 065100        GO TO 302007-CHKRDAY-RTN.
        // 065200     IF CDB-DATE-HOLIDAY           =     1
        // 065300        MOVE  1   TO   WK-THOLIDAY1.
        for (TxBizDate t : txBizDates) {
            if (t.getWeekdy() == 1 || t.getWeekdy() == 4) {
                if (t.isHliday()) {
                    wkTholiday1 = 1;
                    nbsdy = t.getNbsdy();
                }
                return;
            }
        }
        // 065500 302007-CHKRDAY-EXIT.
    }

    private void _302007_R2() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV302007Lsnr _302007_R2");
        //// 只挑PUTF-CTL=21,22資料，其他跳掉
        //// PUTF-CTL=21(明細)，下一步
        //// PUTF-CTL=22(彙總)，A.執行302007-TAIL-RTN 寫REPORTFL2報表明細，B.跳到302007-R2NEXT LOOP讀下一筆FD-PUTF
        //// 其他，跳到302007-R2NEXT LOOP讀下一筆FD-PUTF

        // 034300     IF        PUTF-CTL          =       21
        if ((parse.isNumeric(putfCtl) ? parse.string2Integer(putfCtl) : 0) == 21) {
            // 034400       NEXT SENTENCE
        } else {
            // 034500     ELSE
            // 034600       IF     PUTF-CTL           =       22
            if ((parse.isNumeric(putfCtl) ? parse.string2Integer(putfCtl) : 0) == 22) {
                // 034700         PERFORM 302007-TAIL-RTN   THRU     302007-TAIL-EXIT
                // 034800         GO TO 302007-R2NEXT
                _302007_Tail();
                return;
            } else {
                // 034900       ELSE
                // 035000         GO TO 302007-R2NEXT.
                return;
            }
        }
        //// PUTF-CTL=21(明細)，執行302007-TAIL-RTN 寫REPORTFL2報表明細

        // 035200     PERFORM 302007-TAIL-RTN   THRU     302007-TAIL-EXIT.
        _302007_Tail();
        //// 第一筆資料
        // 035300     IF        WK-HAVERPT        =       "N"
        if ("N".equals(wkHaveRpt)) {
            // 035400       MOVE    PUTF-DATE      TO      WK-BDATE
            // 035500       MOVE    PUTF-DATE      TO      WK-EDATE
            // 035600       OPEN    OUTPUT    REPORTFL2
            // 035700       MOVE    "Y"       TO      WK-HAVERPT.
            wkBdate = putfDate;
            wkEdate = putfDate;
            wkHaveRpt = "Y";
        }

        //// 執行302007-WTITR2，寫REPORTFL2表頭(彙總單)
        //// FD-PUTF排列順序：CTL、FILLER(1:3)(=PBRNO)、DATE、CODE、RCPTID(1:10)
        // 035800     IF        WK-SUBCNT         =       0
        if (wkSubcnt == 0) {
            // 035900       PERFORM 302007-WTITR2 THRU 302007-WTITR2-E
            _302007_Wtitr2();
            // 036000       MOVE    PUTF-DATE         TO      WK-COMPDATE.
            wkCompdate = putfDate;
        }
        // 036010* 判斷 530021 要獨立計算，若之後有大於此代收類別，須重新判斷
        // 036020     IF   (    PUTF-CODE         =       "530021"       OR
        // 036025               WK-530021CODE     =       "530021" )     AND
        // 036030               PUTF-CODE     NOT =       WK-530021CODE  AND
        // 036035               WK-SUBCNT     NOT =       0
        if (("530021".equals(putfCode) || "530021".equals(wk530021Code))
                && !wk530021Code.equals(putfCode)
                && wkSubcnt != 0) {
            // 036040       MOVE    "Y"               TO      WK-530021FLAG.
            wk530021Flag = "Y";
        }
        //// 日期不同或WK-530021FLAG="Y"，寫REPORTFL2報表明細(彙總單 302007-DTL)
        // 036100     IF        WK-COMPDATE   NOT  =      PUTF-DATE   OR
        // 036150               WK-530021FLAG      =      "Y"
        if (!wkCompdate.equals(putfDate) || "Y".equals(wk530021Code)) {
            // 036200       MOVE    SPACES            TO      REPORT2-LINE
            // 036300       WRITE   REPORT2-LINE      AFTER   1
            fileReport2Contents.add("");

            //// 搬相關欄位(小計)到302007-DTL for REPORTFL2
            // 036400       MOVE    WK-COMPDATE       TO      302007-DATE-R
            // 036500       MOVE    WK-SUBCNT         TO      302007-CDCNT-R
            // 036600       MOVE    WK-SUBAMT         TO      302007-CDAMT-R
            // 036700       ADD     WK-SUBCNT         TO      WK-TOTCNT
            // 036800       ADD     WK-SUBAMT         TO      WK-TOTAMT
            // 036900       COMPUTE 302007-CDFEE-R    =       WK-SUBCNT * 3
            _302007DateR = wkCompdate;
            _302007CdcntR = wkSubcnt;
            _302007CdamtR = wkSubamt;
            wkTotcnt = wkTotcnt + wkSubcnt;
            wkTotamt = wkTotamt.add(wkSubamt);

            // 018100 01   302007-DTL.
            // 018200  03  FILLER                           PIC X(17) VALUE SPACES.
            // 018300  03  302007-DATE-R                    PIC 9(06).
            // 018400  03  FILLER                           PIC X(13) VALUE SPACES.
            // 018500  03  302007-CDCNT-R                   PIC ZZ,ZZZ,ZZ9.
            // 018600  03  FILLER                           PIC X(08) VALUE SPACES.
            // 018700  03  302007-CDAMT-R                   PIC Z,ZZZ,ZZZ,ZZ9.
            // 018800  03  FILLER                           PIC X(08) VALUE SPACES.
            // 018900  03  302007-CDFEE-R                   PIC ZZZ,ZZZ,ZZ9.99.
            StringBuilder _302007_dtl = new StringBuilder();
            _302007_dtl.append(formatUtil.padX("", 17));
            _302007_dtl.append(formatUtil.pad9(_302007DateR, 6));
            _302007_dtl.append(formatUtil.padX("", 13));
            _302007_dtl.append(reportUtil.customFormat("" + _302007CdcntR, "ZZ,ZZZ,ZZ9"));
            _302007_dtl.append(formatUtil.padX("", 8));
            _302007_dtl.append(reportUtil.customFormat("" + _302007CdamtR, "Z,ZZZ,ZZZ,ZZ9"));
            _302007_dtl.append(formatUtil.padX("", 8));
            _302007_dtl.append(reportUtil.customFormat("" + _302007CdfeeR, "Z,ZZZ,ZZZ,ZZ9.99"));
            // 036920       IF      WK-530021CODE     =       "530021"
            if ("530021".equals(wk530021Code)) {
                // 036940         MOVE  "(530021)"        TO      302007-DTL(8:8)
                _302007_dtl.replace(8, 16, "(530021)");

            } else {
                // 036945       ELSE
                // 036950         MOVE  SPACES            TO      302007-DTL(8:8)
                _302007_dtl.replace(8, 16, formatUtil.padX("", 8));
            }
            // 036960       END-IF

            //// 寫REPORTFL2報表明細(彙總單 302007-DTL)
            // 037000       WRITE   REPORT2-LINE      FROM    302007-DTL
            sb = new StringBuilder();
            sb.append(_302007_dtl);
            fileReport2Contents.add(sb.toString());

            // 037050       MOVE    "N"               TO      WK-530021FLAG
            // 037100       MOVE    PUTF-DATE         TO      WK-COMPDATE
            // 037200       MOVE     0                TO      WK-SUBCNT,WK-SUBAMT.
            wk530021Flag = "N";
            wkCompdate = putfDate;
            wkSubcnt = 0;
            wkSubamt = new BigDecimal(0);
        }
        //// 累計小計
        // 037250     MOVE      PUTF-CODE         TO      WK-530021CODE.
        // 037300     COMPUTE   WK-SUBCNT         =       WK-SUBCNT + 1.
        // 037400     COMPUTE   WK-SUBAMT         =       WK-SUBAMT + PUTF-AMT.
        // 037500     MOVE PUTF-RCPTID(1:2)       TO      WK-COMPNO.
        // 037550     MOVE PUTF-FILLER(1:3)       TO      WK-PBRNO.
        wk530021Code = putfCode;
        wkSubcnt = wkSubcnt + 1;
        wkSubamt = wkSubamt.add(parse.string2BigDecimal(putfAmt));
        wkCompno = putfRcptid.substring(0, 2);
        wkPbrno = putfFiller.substring(0, 3);

        //// 代收期間
        // 037600     IF        WK-BDATE          >       PUTF-DATE
        if (parse.string2Integer(wkBdate) > parse.string2Integer(putfDate)) {
            // 037700       MOVE    PUTF-DATE      TO      WK-BDATE.
            wkBdate = putfDate;
        }
        // 037800     IF        WK-EDATE          <       PUTF-DATE
        if (parse.string2Integer(wkEdate) > parse.string2Integer(putfDate)) {
            // 037900       MOVE    PUTF-DATE      TO      WK-EDATE.
            wkEdate = putfDate;
        }
        //// LOOP讀下一筆FD-PUTF

        // 038200     GO  TO    302007-R2NEXT.
    }

    private void _302007_Tail() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV302007Lsnr _302007_Tail");
        // 065800 302007-TAIL-RTN.
        // 065950  IF WK-PBRNO   NOT = PUTF-FILLER(1:3) AND WK-PBRNO  NOT= SPACES
        // 066000  AND   WK-SUBCNT > 0
        if (!wkPbrno.equals(putfFiller.substring(0, 3))
                && !wkPbrno.trim().isEmpty()
                && wkSubcnt > 0) {
            // 066100    MOVE    WK-COMPDATE     TO      302007-DATE-R
            // 066200    MOVE    WK-SUBCNT       TO      302007-CDCNT-R
            // 066300    MOVE    WK-SUBAMT       TO      302007-CDAMT-R
            // 066400    COMPUTE 302007-CDFEE-R  =       WK-SUBCNT * 3
            // 066500    ADD     WK-SUBCNT       TO      WK-TOTCNT
            // 066600    ADD     WK-SUBAMT       TO      WK-TOTAMT
            // 066700    MOVE    WK-TOTCNT       TO      302007-TOTCNT-R
            // 066800    MOVE    WK-TOTAMT       TO      302007-TOTAMT-R
            _302007DateR = wkCompdate;
            _302007CdcntR = wkSubcnt;
            _302007CdamtR = wkSubamt;
            _302007CdfeeR = new BigDecimal(wkSubcnt).multiply(new BigDecimal(3));
            wkTotcnt = wkTotcnt + wkSubcnt;
            wkTotamt = wkTotamt.add(wkSubamt);
            _302007TotcntR = wkTotcnt;
            _302007TotamtR = wkTotamt;
            // 018100 01   302007-DTL.
            // 018200  03  FILLER                           PIC X(17) VALUE SPACES.
            // 018300  03  302007-DATE-R                    PIC 9(06).
            // 018400  03  FILLER                           PIC X(13) VALUE SPACES.
            // 018500  03  302007-CDCNT-R                   PIC ZZ,ZZZ,ZZ9.
            // 018600  03  FILLER                           PIC X(08) VALUE SPACES.
            // 018700  03  302007-CDAMT-R                   PIC Z,ZZZ,ZZZ,ZZ9.
            // 018800  03  FILLER                           PIC X(08) VALUE SPACES.
            // 018900  03  302007-CDFEE-R                   PIC ZZZ,ZZZ,ZZ9.99.
            StringBuilder _302007_dtl = new StringBuilder();
            _302007_dtl.append(formatUtil.padX("", 17));
            _302007_dtl.append(formatUtil.pad9(_302007DateR, 6));
            _302007_dtl.append(formatUtil.padX("", 13));
            _302007_dtl.append(reportUtil.customFormat("" + _302007CdcntR, "ZZ,ZZZ,ZZ9"));
            _302007_dtl.append(formatUtil.padX("", 8));
            _302007_dtl.append(reportUtil.customFormat("" + _302007CdamtR, "Z,ZZZ,ZZZ,ZZ9"));
            _302007_dtl.append(formatUtil.padX("", 8));
            _302007_dtl.append(reportUtil.customFormat("" + _302007CdfeeR, "Z,ZZZ,ZZZ,ZZ9.99"));

            // 066810    IF      WK-530021CODE     =       "530021"
            if ("530021".equals(wk530021Code)) {
                // 066820      MOVE  "(530021)"        TO      302007-DTL(8:8)
                _302007_dtl.replace(8, 16, "(530021)");
            } else {
                // 066830    ELSE
                // 066840      MOVE  SPACES            TO      302007-DTL(8:8)
                _302007_dtl.replace(8, 16, formatUtil.padX("", 8));
                // 066850    END-IF
            }
            // 066900    MOVE    SPACE           TO      REPORT2-LINE
            // 067000    WRITE   REPORT2-LINE   AFTER    1
            fileReport2Contents.add("");
            //// 寫REPORTFL2報表明細(彙總單 302007-DTL)
            // 067100    WRITE   REPORT2-LINE    FROM    302007-DTL
            sb = new StringBuilder();
            sb.append(_302007_dtl);
            fileReport2Contents.add(sb.toString());

            //// 寫REPORTFL2表尾(彙總單 302007-TOT)
            // 067200    PERFORM 302007-WTOTR2 THRU 302007-WTOTR2-E
            _302007_Wtotr2();
            //// 清合計、小計數
            // 067300    MOVE  0 TO WK-TOTCNT,WK-TOTAMT, WK-SUBCNT,WK-SUBAMT.
            wkTotcnt = 0;
            wkTotamt = new BigDecimal(0);
            wkSubcnt = 0;
            wkSubamt = new BigDecimal(0);
        }
        // 067500 302007-TAIL-EXIT.
    }

    private void _302007_Wtitr2() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV302007Lsnr _302007_Wtotr2");
        // 053200 302007-WTITR2.

        //// 寫REPORTFL2表頭 (彙總單 302007-TIT1~302007-TIT5)
        //// 搬相關欄位到302007-TIT1~302007-TIT5 for REPORTFL
        // 053300    MOVE     2                       TO      302007-FORM-R.
        int _302007FormR = 2;

        //// 執行302007-FCLMR，依代收類別找事業單位基本資料檔，取得相關資料
        // 053400    PERFORM  302007-FCLMR            THRU    302007-FCLMR.
        _302007_Fclmr();

        // 053500    MOVE     DB-CLMR-PBRNO           TO      302007-BRNO-R.
        // 053600    MOVE     PUTF-CODE               TO      WK-CODE.
        // 053700    MOVE     "00"                    TO      302007-CD-R.
        // 053800    MOVE     DB-CLMR-CNAME           TO      302007-CNAME-R.
        int _302007BrnoR = tClmr.getPbrno();
        wkCode = putfCode;
        String _302007CdR = "00";
        _302007CnameR = tClmr.getCname();

        // 053820    IF       PUTF-CODE   = "530021"
        if ("530021".equals(putfCode)) {
            // 053860      MOVE   " 台電台北市區營業處 "  TO      302007-CNAME-R.
            _302007CnameR = " 台電台北市區營業處 ";
        }
        // 053900    MOVE     WK-DATE                 TO      302007-PDATE-R.
        // 054000    MOVE     1                       TO      302007-PAGE-R.
        _302007PdateR = wkDate;
        _302007PageR = 1;
        // 054100    MOVE     SPACES                  TO      REPORT2-LINE.
        // 054200    WRITE    REPORT2-LINE         AFTER      PAGE.
        fileReport2Contents.add("\u000c");

        // 054300    MOVE     SPACES                  TO      REPORT2-LINE.
        // 054400    WRITE    REPORT2-LINE          FROM      302007-TIT1.
        // 013700 01   302007-TIT1.
        // 013800  03  FILLER                           PIC X(27) VALUE SPACES.
        // 013900  03  FILLER                           PIC X(52) VALUE
        // 014000      " 臺　灣　銀　行　代　收　電　費　資　料　彙　總　單 ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 27));
        sb.append(formatUtil.padX(" 臺　灣　銀　行　代　收　電　費　資　料　彙　總　單 ", 52));
        fileReport2Contents.add(sb.toString());

        // 054500    MOVE     SPACES                  TO      REPORT2-LINE.
        // 054600    WRITE    REPORT2-LINE          FROM      302007-TIT2.
        // 014100 01   302007-TIT2.
        // 014200  03  FILLER                           PIC X(01) VALUE SPACES.
        // 014300  03  FILLER                           PIC X(07) VALUE "BRNO : ".
        // 014400  03  302007-BRNO-R                    PIC X(03).
        // 014500  03  FILLER                           PIC X(76) VALUE SPACES.
        // 014600  03  FILLER                           PIC X(18) VALUE
        // 014700                                       "FORM : C009/30200/".
        // 014800  03  302007-FORM-R                    PIC X(01).
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.padX("BRNO : ", 7));
        sb.append(formatUtil.padX("" + _302007BrnoR, 3));
        sb.append(formatUtil.padX("", 76));
        sb.append(formatUtil.padX("FORM : C009/30200/", 18));
        sb.append(formatUtil.padX("" + _302007FormR, 1));
        fileReport2Contents.add(sb.toString());

        // 054700    MOVE     SPACES                  TO      REPORT2-LINE.
        // 054800    WRITE    REPORT2-LINE         AFTER      1.
        fileReport2Contents.add("");

        // 054900    WRITE    REPORT2-LINE          FROM      302007-TIT3.
        // 014900 01   302007-TIT3.
        // 015000  03  FILLER                           PIC X(12) VALUE
        // 015100      " 解繳日期 : ".
        // 015200  03  302007-BHDATE-R                  PIC 99/99/99.
        // 015300  03  FILLER                           PIC X(66) VALUE SPACES.
        // 015400  03  FILLER                           PIC X(12) VALUE
        // 015500      " 印表日期 : ".
        // 015600  03  302007-PDATE-R                   PIC 99/99/99.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 解繳日期 : ", 12));
        sb.append(reportUtil.customFormat("" + _302007BhdateR, "99/99/99"));
        sb.append(formatUtil.padX("", 66));
        sb.append(formatUtil.padX(" 印表日期 : ", 12));
        sb.append(reportUtil.customFormat("" + _302007PdateR, "99/99/99"));
        fileReport2Contents.add(sb.toString());

        // 055000    MOVE     SPACES                  TO      REPORT2-LINE.
        // 055100    WRITE    REPORT2-LINE         AFTER      1.
        fileReport2Contents.add("");

        // 055200    MOVE     SPACES                  TO      REPORT2-LINE.
        // 055300    WRITE    REPORT2-LINE          FROM      302007-TIT4.
        // 015700 01   302007-TIT4.
        // 015800  03  FILLER                           PIC X(12) VALUE
        // 015900      " 營業區處： ".
        // 016000  03  302007-CD-R                      PIC X(02).
        // 016100  03  302007-CNAME-R                   PIC X(20).
        // 016200  03  FILLER                           PIC X(53) VALUE SPACES.
        // 016300  03  FILLER                           PIC X(07) VALUE
        // 016400      " 頁數 :".
        // 016500  03  302007-PAGE-R                    PIC 9(04).
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 營業區處 : ", 12));
        sb.append(formatUtil.padX(_302007CdR, 2));
        sb.append(formatUtil.padX(_302007CnameR, 20));
        sb.append(formatUtil.padX("", 53));
        sb.append(formatUtil.padX(" 頁數 :", 7));
        sb.append(formatUtil.pad9("" + _302007PageR, 4));
        fileReport2Contents.add(sb.toString());

        // 055400    MOVE     SPACES                  TO      REPORT2-LINE.
        // 055500    WRITE    REPORT2-LINE         AFTER      1.
        fileReport2Contents.add("");

        // 055600    WRITE    REPORT2-LINE          FROM      302007-TIT5.
        // 016600 01   302007-TIT5.
        // 016700  03  FILLER                           PIC X(14) VALUE SPACES.
        // 016800  03  FILLER                           PIC X(10) VALUE
        // 016900      " 代收日期 ".
        // 017000  03  FILLER                           PIC X(12) VALUE SPACES.
        // 017100  03  FILLER                           PIC X(12) VALUE
        // 017200      " 代收總筆數 ".
        // 017300  03  FILLER                           PIC X(10) VALUE SPACES.
        // 017400  03  FILLER                           PIC X(12) VALUE
        // 017500      " 代收總金額 ".
        // 017600  03  FILLER                           PIC X(10) VALUE SPACES.
        // 017700  03  FILLER                           PIC X(08) VALUE
        // 017800      " 手續費 ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 14));
        sb.append(formatUtil.padX(" 代收日期 ", 10));
        sb.append(formatUtil.padX("", 12));
        sb.append(formatUtil.padX(" 代收總筆數 ", 12));
        sb.append(formatUtil.padX("", 10));
        sb.append(formatUtil.padX(" 代收總金額 ", 12));
        sb.append(formatUtil.padX("", 10));
        sb.append(formatUtil.padX(" 手續費 ", 8));
        fileReport2Contents.add(sb.toString());

        // 055700    MOVE     SPACES                  TO      REPORT2-LINE.
        // 055800    WRITE    REPORT2-LINE          FROM      302007-SEP.
        // 017900 01   302007-SEP.
        // 018000  03  FILLER                           PIC X(120) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 120));
        fileReport2Contents.add(sb.toString());

        // 055900    MOVE     SPACES                  TO      REPORT2-LINE.
        // 056000    WRITE    REPORT2-LINE         AFTER      1.
        fileReport2Contents.add("");

        // 056100 302007-WTITR2-E.
    }

    private void _302007_Fclmr() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV302007Lsnr _302007Fclmr");
        // 062200 302007-FCLMR.
        // 062300    FIND   DB-CLMR-IDX1  AT DB-CLMR-CODE =   PUTF-CODE.
        tClmr = clmrService.findById(putfCode);
        // 062400 302007-FCLMR-E.
    }

    private void _302007_Wtotr2() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV302007Lsnr _302007_Wtotr2");
        // 059200 302007-WTOTR2.

        //// 寫REPORTFL2表尾(彙總單 302007-TOT)

        // 059300    MOVE     WK-BDATE                TO      302007-BDATE-R.
        // 059400    MOVE     WK-EDATE                TO      302007-EDATE-R.
        // 059500    MOVE     SPACES                  TO      REPORT2-LINE.
        String _302007_BdateR = wkBdate;
        String _302007_EdateR = wkEdate;
        // 059600    WRITE    REPORT2-LINE         AFTER      1.
        fileReport2Contents.add("");

        // 059700    WRITE    REPORT2-LINE          FROM      302007-SEP.
        // 017900 01   302007-SEP.
        // 018000  03  FILLER                           PIC X(120) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 120));
        fileReport2Contents.add(sb.toString());

        // 059800    MOVE     SPACES                  TO      REPORT2-LINE.
        // 059900    WRITE    REPORT2-LINE         AFTER      1.
        fileReport2Contents.add("");

        // 060000    COMPUTE  302007-FEE-R   =     WK-TOTCNT * 3
        BigDecimal _302007FeeR = new BigDecimal(wkTotcnt).multiply(new BigDecimal(3));

        // 060100    WRITE    REPORT2-LINE          FROM      302007-TOT.
        // 019000 01   302007-TOT.
        // 019100  03  FILLER                           PIC X(14) VALUE
        // 019200      "   代收期間 : ".
        // 019300  03  302007-BDATE-R                   PIC 99/99/99.
        // 019400  03  FILLER                           PIC X(01) VALUE "-".
        // 019500  03  302007-EDATE-R                   PIC 99/99/99.
        // 019600  03  FILLER                           PIC X(06) VALUE SPACES.
        // 019700  03  302007-TOTCNT-R                  PIC ZZ,ZZZ,ZZ9.
        // 019800  03  FILLER                           PIC X(08) VALUE SPACES.
        // 019900  03  302007-TOTAMT-R                  PIC Z,ZZZ,ZZZ,ZZ9.
        // 020000  03  FILLER                           PIC X(08) VALUE SPACES.
        // 020100  03  302007-FEE-R                     PIC ZZZ,ZZZ,ZZ9.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("   代收期間 : ", 14));
        sb.append(reportUtil.customFormat(_302007_BdateR, "99/99/99"));
        sb.append(formatUtil.padX("-", 1));
        sb.append(reportUtil.customFormat(_302007_EdateR, "99/99/99"));
        sb.append(formatUtil.padX("", 6));
        sb.append(reportUtil.customFormat("" + _302007TotcntR, "ZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 8));
        sb.append(reportUtil.customFormat("" + _302007TotamtR, "Z,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 8));
        sb.append(reportUtil.customFormat("" + _302007FeeR, "ZZZ,ZZZ,ZZ9"));
        fileReport2Contents.add(sb.toString());

        // 060200    MOVE     SPACES                  TO      REPORT2-LINE.
        // 060300    WRITE    REPORT2-LINE          AFTER     1.
        fileReport2Contents.add("");

        // 060400 302007-WTOTR2-E.
    }

    private void _302007_R2last() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV302007Lsnr _302007_R2last");
        //// 038300 302007-R2LAST.

        //// 關閉FD-PUTF

        // 038400     CLOSE     FD-PUTF    WITH  SAVE.

        //// 若有資料，關閉REPORTFL2；否則跳至302007-FILE(跳過302007-REPORT3，不出 REPORTFL3)

        // 038500     IF        WK-HAVERPT        =       "Y"
        if ("Y".equals(wkHaveRpt)) {
            // 038600       CLOSE   REPORTFL2  WITH  SAVE
            try {
                textFile.writeFileContent(wkReport2dir, fileReport2Contents, CHARSET_BIG5);
                upload(wkReport2dir, "RPT", "");
            } catch (LogicException e) {
                moveErrorResponse(e);
            }
        } else {
            // 038700     ELSE
            // 038800       GO  TO  302007-FILE.
            go_to_302007_F = true;
        }
    }

    private void _302007_R3() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV302007Lsnr _302007_R3");
        //// 只挑PUTF-CTL=21,22資料，其他跳掉
        //// PUTF-CTL=21(明細)，下一步
        //// PUTF-CTL=22(彙總)，A.執行302007-WTOTR3 寫REPORTFL3表尾，B.跳到302007-R3NEXT LOOP讀下一筆FD-PUTF
        //// 其他，跳到302007-R3NEXT LOOP讀下一筆FD-PUTF
        //
        // 039700     IF        PUTF-CTL          =       21
        if (parse.string2Integer(putfCtl) == 21) {
            // 039800       NEXT SENTENCE
        } else {
            // 039900     ELSE
            // 040000       IF      PUTF-CTL          =       22
            if (parse.string2Integer(putfCtl) == 22) {
                // 040100         PERFORM 302007-WTOTR3 THRU 302007-WTOTR3-E
                // 040200         GO TO 302007-R3NEXT
                _302007Wtotr3();
                return;
            } else {
                // 040300       ELSE
                // 040400         GO TO 302007-R3NEXT.
                return;
            }
        }
        //// PUTF-CTL=21(明細)，執行302007-WTOTR3 寫REPORTFL3表尾
        // 040600     PERFORM 302007-WTOTR3 THRU 302007-WTOTR3-E.
        _302007Wtotr3();

        //// 第一筆或換頁後第一筆資料，執行302007-WTITR3 寫REPORTFL3表頭(明細表)
        // 040800     IF        WK-PCTL           =       0
        if (wkPctl == 0) {
            // 040900       PERFORM 302007-WTITR3 THRU 302007-WTITR3-E.
            _302007Wtitr3(firstPage ? " " : PAGE_SEPARATOR);
            firstPage = false;
        }
        //// 搬相關欄位到302007-DTL3 for REPORTFL3
        //
        // 041000     MOVE      WK-SEQNO          TO      302007-SEQNO-P.
        // 041100     MOVE      PUTF-CODE         TO      WK-CODE.
        // 041200     MOVE      "00"              TO      302007-CD-P.
        // 041300     MOVE      PUTF-USERDATA     TO      WK-USERDATA.
        // 041400     MOVE      PUTF-RCPTID       TO      WK-RCPTID.
        // 041500     MOVE      PUTF-RCPTID(1:2)  TO      WK-COMPNO.
        // 041550     MOVE      PUTF-FILLER(1:3)  TO      WK-PBRNO.
        int _302007SeqnoP = wkSeqno;
        wkCode = putfCode;
        String _302007CdP = "00";
        wkUserdata = putfUserdata;
        wkNo = wkUserdata.substring(0, 11);
        wkChkdg = wkUserdata.substring(11, 12);
        wkRcptid = putfRcptid;
        wkEledate = wkRcptid.substring(10, 16);
        wkCompno = putfRcptid.substring(0, 2);
        wkPbrno = putfFiller.substring(0, 3);

        String _302007NoP = "";
        String _302007ChkdgP = "";
        String _302007EledateP = "";
        // 041600     IF        PUTF-TXTYPE       =       "F"
        if ("F".equals(putfTxtype)) {
            // 041700         MOVE  PUTF-RCPTID       TO      302007-NO-P(1:10)
            // 041800         MOVE  WK-USERDATA(33:1) TO      302007-NO-P(11:1)
            // 041900         MOVE      SPACES        TO      302007-CHKDG-P
            // 042000         MOVE  PUTF-RCPTID(11:6) TO      302007-ELEDATE-P
            _302007NoP = putfRcptid.substring(0, 10) + wkUserdata.substring(32, 33);
            _302007ChkdgP = "";
            _302007EledateP = putfRcptid.substring(10, 16);
        } else {
            // 042100     ELSE
            // 042200         MOVE      WK-NO         TO      302007-NO-P
            // 042300         MOVE      WK-CHKDG      TO      302007-CHKDG-P
            // 042400         MOVE      WK-ELEDATE    TO      302007-ELEDATE-P.
            _302007NoP = wkNo;
            _302007ChkdgP = wkChkdg;
            _302007EledateP = wkEledate;
        }

        //// 代收類別="530021"，執行530021-CHKEDATE-RTN，計算台電收費日
        String _302007KindP = "";
        // 042520     IF  PUTF-CODE               =       "530021"
        if ("530021".equals(putfCode)) {
            // 042540         MOVE  PUTF-RCPTID(1:11) TO      302007-NO-P
            // 042595         PERFORM 530021-CHKEDATE-RTN THRU  530021-CHKEDATE-EXIT
            // 042600         MOVE  WK-530021-ELEDATE(3:6) TO 302007-ELEDATE-P.
            _302007NoP = putfRcptid.substring(0, 11);
            _530021_Chkedate();
            _302007EledateP = wk530021Eledate.substring(2, 8);
        }
        // 042700     MOVE      PUTF-AMT          TO      302007-AMT-P.
        String _302007AmtP = putfAmt;
        // 042800     IF        PUTF-TXTYPE       =       "W"
        if ("W".equals(putfTxtype)) {
            // 042900       MOVE    " 行動銀行 "      TO      302007-KIND-P
            _302007KindP = " 行動銀行 ";
        } else if ("V".equals(putfTxtype)) {
            // 043000     ELSE
            // 043100     IF        PUTF-TXTYPE       =       "V"
            // 043200       MOVE    " 語音代收 "      TO      302007-KIND-P
            _302007KindP = " 語音代收 ";
        } else if ("C".equals(putfTxtype) || "M".equals(putfTxtype)) {
            // 043300     ELSE
            // 043400     IF        PUTF-TXTYPE       =       "C" OR "M"
            // 043500       MOVE    " 櫃檯代收 "      TO      302007-KIND-P
            _302007KindP = " 櫃檯代收 ";
        } else {
            // 043600     ELSE
            // 043700       MOVE    " 網路代收 "      TO      302007-KIND-P.
            _302007KindP = " 網路代收 ";
        }
        // 043800     MOVE      PUTF-DATE         TO      302007-DATE-P.
        // 043900     MOVE      PUTF-CLLBR        TO      302007-CLLBR-P.
        // 044000     MOVE      PUTF-SITDATE   TO      302007-SITDATE-P.
        String _302007DateP = putfDate;
        String _302007CllbrP = putfCllbr;
        String _302007SitdateP = putfSitdate;

        //// 寫REPORTFL3報表明細(明細表 302007-DTL3)

        // 044100     WRITE     REPORT3-LINE  FROM  302007-DTL3.
        // 026700 01   302007-DTL3.
        // 026800  03  302007-SEQNO-P                   PIC ZZZ,ZZ9.
        // 026900  03  FILLER                           PIC X(02) VALUE SPACES.
        // 027000  03  302007-CD-P                      PIC X(02).
        // 027100  03  FILLER                           PIC X(07) VALUE SPACES.
        // 027200  03  302007-NO-P                      PIC X(11).
        // 027300  03  FILLER                           PIC X(07) VALUE SPACES.
        // 027400  03  302007-CHKDG-P                   PIC X(01).
        // 027500  03  FILLER                           PIC X(05) VALUE SPACES.
        // 027600  03  302007-ELEDATE-P                 PIC 99/99/99.
        // 027700  03  FILLER                           PIC X(02) VALUE SPACES.
        // 027800  03  302007-AMT-P                     PIC ZZZ,ZZZ,ZZZ,ZZ9.
        // 027900  03  FILLER                           PIC X(02) VALUE SPACES.
        // 028000  03  302007-KIND-P                    PIC X(10).
        // 028100  03  FILLER                           PIC X(04) VALUE SPACES.
        // 028200  03  302007-DATE-P                    PIC 99/99/99.
        // 028300  03  FILLER                           PIC X(08) VALUE SPACES.
        // 028400  03  302007-CLLBR-P                   PIC X(03).
        // 028500  03  FILLER                           PIC X(08) VALUE SPACES.
        // 028600  03  302007-SITDATE-P                 PIC 9(06).
        sb = new StringBuilder();
        sb.append(reportUtil.customFormat("" + _302007SeqnoP, "ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(_302007CdP, 2));
        sb.append(formatUtil.padX("", 7));
        sb.append(formatUtil.padX(_302007NoP, 11));
        sb.append(formatUtil.padX("", 7));
        sb.append(formatUtil.padX(_302007ChkdgP, 1));
        sb.append(formatUtil.padX("", 5));
        sb.append(reportUtil.customFormat(_302007EledateP, "99/99/99"));
        sb.append(formatUtil.padX("", 2));
        sb.append(reportUtil.customFormat(_302007AmtP, "ZZZ,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(_302007KindP, 10));
        sb.append(formatUtil.padX("", 4));
        sb.append(reportUtil.customFormat(_302007DateP, "99/99/99"));
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(_302007CllbrP, 3));
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.pad9(_302007SitdateP, 6));
        fileReport3Contents.add(sb.toString());

        // 044200     ADD       1                 TO      WK-SEQNO,WK-PCTL.
        wkSeqno = wkSeqno + 1;
        wkPctl = wkPctl + 1;

        //// 累計WK-TOTCNT、WK-TOTAMT
        // 044300     ADD       PUTF-AMT          TO      WK-TOTAMT.
        // 044400     ADD       1                 TO      WK-TOTCNT.
        wkTotamt = wkTotamt.add(parse.string2BigDecimal(putfAmt));
        wkTotcnt = wkTotcnt + 1;
        // 044500     IF        WK-PCTL           =       50
        if (wkPctl == 50) {
            // 044600       MOVE    0                 TO      WK-PCTL.
            wkPctl = 0;
        }
        //// LOOP讀下一筆FD-PUTF

        // 044700     GO TO     302007-R3NEXT.
    }

    private void _530021_Chkedate() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV302007Lsnr _530021_Chkedate");
        // 067700 530021-CHKEDATE-RTN.
        // 067800    MOVE    PUTF-USERDATA(21:8)    TO  WK-530021-INDATE.
        // 067820    MOVE    PUTF-RCPTID(13:2)      TO  WK-CHECKNO.
        // 067850    COMPUTE WK-530021-INDATE = WK-530021-INDATE - 19110000.
        // 067890    MOVE    WK-530021-INDATE       TO  WK-530021-ELEDATE.
        wk530021Indate = putfUserdata.substring(20, 28);
        wkCheckno =
                parse.string2Integer(
                        putfRcptid.substring(12, 14).trim().isEmpty()
                                ? "0"
                                : putfRcptid.substring(12, 14));
        wk530021Indate =
                wk530021Indate.trim().isEmpty()
                        ? "0"
                        : "" + (parse.string2Integer(wk530021Indate) - 19110000);
        wk530021Eledate = formatUtil.pad9(wk530021Indate, 8);
        if (!isValidDate(wk530021Eledate, "yyyyMMdd")) {
            return;
        }
        // 010670 01  WK-530021-ELEDATE.
        // 010675  03 ELEDATE-YY                        PIC 9(04) VALUE 0.
        // 010680  03 ELEDATE-MM                        PIC 9(02) VALUE 0.
        // 010685  03 ELEDATE-DD                        PIC 9(02) VALUE 0.
        eledateYYYY = parse.string2Integer(wk530021Eledate.substring(0, 4));
        eledateMM = parse.string2Integer(wk530021Eledate.substring(4, 6));
        eledateDD = parse.string2Integer(wk530021Eledate.substring(6, 8));
        // 067900 530021-CHK-LOOP.
        // 068000    IF   WK-CHECKNO  >=   ELEDATE-DD
        for (int i = 1; wkCheckno >= eledateDD; i++) {
            // 068020         PERFORM 530021-COUNT-RTN  THRU  530021-COUNT-EXIT
            _530021_Count();
            // 068030         GO  TO  530021-CHK-LOOP.
        }

        // 068040 530021-CHK-END.
        // 068100    COMPUTE  ELEDATE-DD = ELEDATE-DD - WK-CHECKNO.
        eledateDD = eledateDD - wkCheckno;
        wk530021Eledate =
                formatUtil.pad9("" + eledateYYYY, 4)
                        + formatUtil.pad9("" + eledateMM, 2)
                        + formatUtil.pad9("" + eledateDD, 2);
        // 068200 530021-CHKEDATE-EXIT.
    }

    private void _530021_Count() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV302007Lsnr _530021_Count");
        // 068400 530021-COUNT-RTN.
        // 068450     MOVE   WK-530021-ELEDATE      TO  WK-530021-INDATE.
        // 068500     MOVE   1                      TO  WK-CLNDR-KEY.
        wk530021Indate = wk530021Eledate;

        // 068600 530021-LOOP.
        // 068700     READ FD-CLNDR INVALID KEY DISPLAY "INVALID KEY"
        // 068800          , WK-CLNDR-STUS CHANGE ATTRIBUTE TASKVALUE OF MYSELF TO
        // 068900     -1
        // 069000          GO TO 0000-END-RTN.
        // 069100     IF  FD-CLNDR-TBSDY         NOT =  WK-530021-INDATE
        // 069200         ADD    1                  TO  WK-CLNDR-KEY
        // 069300         GO TO  530021-LOOP.

        DateDto dateDto = new DateDto();
        dateDto.setDateS(wk530021Indate);
        dateDto.setMons(-1);
        dateUtil.getCalenderDay(dateDto);
        int monlimit = dateDto.getMonLimit(); // 取月天數
        // 069350* 期間日數減去上月天數
        // 069400     IF  WK-CHECKNO        >=  FD-CLNDR-LMNDD

        if (wkCheckno >= monlimit) {
            // 069420         COMPUTE WK-CHECKNO =  WK-CHECKNO - FD-CLNDR-LMNDD
            wkCheckno = wkCheckno - monlimit;
        } else {
            // 069440     ELSE
            // 069460         COMPUTE ELEDATE-DD =  ELEDATE-DD + FD-CLNDR-LMNDD
            eledateDD = eledateDD + monlimit;
            // 069480     END-IF.
        }
        // 069520     COMPUTE  ELEDATE-MM    =  ELEDATE-MM - 1.
        eledateMM = eledateMM - 1;
        // 069540     IF  ELEDATE-MM = 0
        if (eledateMM == 0) {
            // 069560         MOVE  12          TO  ELEDATE-MM
            // 069600         COMPUTE ELEDATE-YY =  ELEDATE-YY - 1.
            eledateMM = 12;
            eledateYYYY = eledateYYYY - 1;
        }
        // 069700* 若還需要減上月天數前先確認日期符合規則
        // 069720     IF  ELEDATE-DD  >    FD-CLNDR-LMNDD AND
        // 069740         WK-CHECKNO  >=   ELEDATE-DD
        if (eledateDD > monlimit && wkCheckno >= eledateDD) {
            // 069760         COMPUTE ELEDATE-DD =  ELEDATE-DD - FD-CLNDR-LMNDD
            // 069780         COMPUTE ELEDATE-MM =  ELEDATE-MM + 1
            eledateDD = eledateDD - monlimit;
            eledateMM = eledateMM + 1;
            // 069782         IF  ELEDATE-MM = 13
            if (eledateMM == 13) {
                // 069784             MOVE    1         TO  ELEDATE-MM
                // 069786             COMPUTE ELEDATE-YY =  ELEDATE-YY + 1
                eledateMM = 1;
                eledateYYYY = eledateYYYY + 1;
                // 069788         END-IF
            }
            // 069790      END-IF.
        }
        wk530021Eledate =
                formatUtil.pad9("" + eledateYYYY, 4)
                        + formatUtil.pad9("" + eledateMM, 2)
                        + formatUtil.pad9("" + eledateDD, 2);
        // 069800  530021-COUNT-EXIT.
    }

    private void _302007Wtitr3(String pageFg) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV302007Lsnr _302007Wtitr3");
        // 056300 302007-WTITR3.
        //// 寫REPORTFL3表頭 (明細表 302007-TIT31~302007-TIT35)

        // 056400    MOVE    3                        TO      302007-FORM-D.
        String _302007FormD = "3";
        // 056500    PERFORM  302007-FCLMR            THRU    302007-FCLMR.
        _302007_Fclmr();
        // 056800    MOVE     DB-CLMR-PBRNO           TO      302007-BRNO-P.
        String _302007BrnoP = "" + tClmr.getPbrno();
        // 056900    MOVE     WK-DATE                 TO      302007-PDATE-P.
        // 057000    MOVE     WK-BDATE                TO      302007-BDATE-P.
        // 057100    MOVE     WK-EDATE                TO      302007-EDATE-P.
        // 057200    ADD      1                       TO      WK-PAGE.
        // 057300    MOVE     WK-PAGE                 TO      302007-PAGE-P.
        String _302007PdateP = formatUtil.pad9("" + wkDate, 8).substring(2, 8);
        String _302007BdateP = wkBdate;
        String _302007EdateP = wkEdate;
        wkPage = wkPage + 1;
        int _302007PageP = wkPage;
        String _302007Tit31D = "";

        // 057400    MOVE     SPACES                  TO      REPORT3-LINE.
        // 057500    WRITE    REPORT3-LINE         AFTER      PAGE.
        sb = new StringBuilder();
        sb.append(pageFg);
        fileReport3Contents.add(sb.toString());

        // 057600    MOVE     SPACES                  TO      REPORT3-LINE.
        // 057700    WRITE    REPORT3-LINE          FROM      302007-TIT31.
        // 020300 01   302007-TIT31.
        // 020400  03  FILLER                           PIC X(27) VALUE SPACES.
        // 020500  03  FILLER                           PIC X(52) VALUE
        // 020600      " 臺　灣　銀　行　代　收　電　費　資　料　明　細　表 ".
        // 020700  03  302007-TIT31-D                   PIC X(20) VALUE SPACES.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 27));
        sb.append(formatUtil.padX(" 臺　灣　銀　行　代　收　電　費　資　料　明　細　表 ", 52));
        sb.append(formatUtil.padX(_302007Tit31D, 20));
        fileReport3Contents.add(sb.toString());

        // 057800    MOVE     SPACES                  TO      REPORT3-LINE.
        // 057900    WRITE    REPORT3-LINE          FROM      302007-TIT32.
        // 020800 01   302007-TIT32.
        // 020900  03  FILLER                           PIC X(01) VALUE SPACES.
        // 021000  03  FILLER                           PIC X(07) VALUE "BRNO : ".
        // 021100  03  302007-BRNO-P                    PIC X(03).
        // 021200  03  FILLER                           PIC X(76) VALUE SPACES.
        // 021300  03  FILLER                           PIC X(18) VALUE
        // 021400                                       "FORM : C009/30200/".
        // 021500  03  302007-FORM-D                    PIC X(01).
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.padX("BRNO : ", 7));
        sb.append(formatUtil.padX(_302007BrnoP, 3));
        sb.append(formatUtil.padX("", 76));
        sb.append(formatUtil.padX("FORM : C009/30200/", 18));
        sb.append(formatUtil.padX(_302007FormD, 1));
        fileReport3Contents.add(sb.toString());

        // 058000    MOVE     SPACES                  TO      REPORT3-LINE.
        // 058100    WRITE    REPORT3-LINE          FROM      302007-TIT33.
        // 021600 01   302007-TIT33.
        // 021700  03  FILLER                           PIC X(12) VALUE
        // 021800      " 解繳日期 : ".
        // 021900  03  302007-BHDATE-P                  PIC 99/99/99.
        // 022000  03  FILLER                           PIC X(66) VALUE SPACES.
        // 022100  03  FILLER                           PIC X(12) VALUE
        // 022200      " 印表日期 : ".
        // 022300  03  302007-PDATE-P                   PIC 99/99/99.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 解繳日期 : ", 12));
        sb.append(reportUtil.customFormat("" + _302007BhdateP, "99/99/99"));
        sb.append(formatUtil.padX("", 66));
        sb.append(formatUtil.padX(" 印表日期 : ", 12));
        sb.append(reportUtil.customFormat(_302007PdateP, "99/99/99"));
        fileReport3Contents.add(sb.toString());

        // 058200    MOVE     SPACES                  TO      REPORT3-LINE.
        // 058300    MOVE     SPACES                  TO      REPORT3-LINE.
        // 058400    WRITE    REPORT3-LINE          FROM      302007-TIT34.
        // 022400 01   302007-TIT34.
        // 022500  03  FILLER                           PIC X(12) VALUE
        // 022600      " 代收期間 : ".
        // 022700  03  302007-BDATE-P                   PIC 99/99/99.
        // 022800  03  FILLER                           PIC X(01) VALUE "-".
        // 022900  03  302007-EDATE-P                   PIC 99/99/99.
        // 023000  03  FILLER                           PIC X(66) VALUE SPACES.
        // 023100  03  FILLER                           PIC X(07) VALUE
        // 023200      " 頁數 :".
        // 023300  03  302007-PAGE-P                    PIC 9(04).
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 代收期間 : ", 12));
        sb.append(reportUtil.customFormat(_302007BdateP, "99/99/99"));
        sb.append(formatUtil.padX("-", 1));
        sb.append(reportUtil.customFormat(_302007EdateP, "99/99/99"));
        sb.append(formatUtil.padX("", 66));
        sb.append(formatUtil.padX(" 頁數 :", 7));
        sb.append(formatUtil.padX("" + _302007PageP, 4));
        fileReport3Contents.add(sb.toString());

        // 058500    MOVE     SPACES                  TO      REPORT3-LINE.
        // 058600    WRITE    REPORT3-LINE          FROM      302007-TIT35.
        // 023400 01   302007-TIT35.
        // 023500  03  FILLER                           PIC X(01) VALUE SPACES.
        // 023600  03  FILLER                           PIC X(06) VALUE
        // 023700      " 序號 ".
        // 023800  03  FILLER                           PIC X(02) VALUE SPACES.
        // 023900  03  FILLER                           PIC X(06) VALUE
        // 024000      " 區處 ".
        // 024100  03  FILLER                           PIC X(05) VALUE SPACES.
        // 024200  03  FILLER                           PIC X(06) VALUE
        // 024300      " 電號 ".
        // 024400  03  FILLER                           PIC X(05) VALUE SPACES.
        // 024500  03  FILLER                           PIC X(08) VALUE
        // 024600      " 查核碼 ".
        // 024700  03  FILLER                           PIC X(02) VALUE SPACES.
        // 024800  03  FILLER                           PIC X(12) VALUE
        // 024900      " 台電收費日 ".
        // 025000  03  FILLER                           PIC X(03) VALUE SPACES.
        // 025100  03  FILLER                           PIC X(10) VALUE
        // 025200      " 繳款金額 ".
        // 025300  03  FILLER                           PIC X(03) VALUE SPACES.
        // 025400  03  FILLER                           PIC X(10) VALUE
        // 025500      " 代收方式 ".
        // 025600  03  FILLER                           PIC X(02) VALUE SPACES.
        // 025700  03  FILLER                           PIC X(14) VALUE
        // 025800      " 代收電費日期 ".
        // 025900  03  FILLER                           PIC X(02) VALUE SPACES.
        // 026000  03  FILLER                           PIC X(10) VALUE
        // 026100      " 代收分行 ".
        // 026200  03  FILLER                           PIC X(02) VALUE SPACES.
        // 026300  03  FILLER                           PIC X(10) VALUE
        // 026400      " 原代收日 ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.padX(" 序號 ", 6));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(" 區處 ", 6));
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX(" 電號 ", 6));
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX(" 查核碼 ", 8));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(" 台電收費日 ", 12));
        sb.append(formatUtil.padX("", 3));
        sb.append(formatUtil.padX(" 繳款金額 ", 10));
        sb.append(formatUtil.padX("", 3));
        sb.append(formatUtil.padX(" 代收方式 ", 10));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(" 代收電費日期 ", 14));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(" 代收分行 ", 10));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(" 原代收日 ", 10));
        fileReport3Contents.add(sb.toString());

        // 058700    MOVE     SPACES                  TO      REPORT3-LINE.
        // 058800    WRITE    REPORT3-LINE          FROM      302007-SEP.
        // 017900 01   302007-SEP.
        // 018000  03  FILLER                           PIC X(120) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 120));
        fileReport3Contents.add(sb.toString());

        // 058900    MOVE     SPACES                  TO      REPORT3-LINE.
        // 059000 302007-WTITR3-E.
    }

    private void _302007Wtotr3() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV302007Lsnr _302007Wtotr3");
        // 060600 302007-WTOTR3.

        //// 寫REPORTFL3表尾(明細表 代收電費總筆數、總金額 302007-TOT3)
        // 060750    IF WK-PBRNO  NOT = PUTF-FILLER(1:3) AND WK-PBRNO  NOT= SPACES
        // 060800        AND WK-TOTCNT > 0
        if (!wkPbrno.equals(putfFiller.substring(0, 3))
                && !wkPbrno.trim().isEmpty()
                && wkTotcnt > 0) {
            // 060900    MOVE     SPACES                  TO      REPORT3-LINE
            // 061000    WRITE    REPORT3-LINE          FROM      302007-SEP
            // 017900 01   302007-SEP.
            // 018000  03  FILLER                           PIC X(120) VALUE ALL "-".
            sb = new StringBuilder();
            sb.append(reportUtil.makeGate("-", 120));
            fileReport3Contents.add(sb.toString());

            // 061100    MOVE     SPACES                  TO      REPORT3-LINE
            // 061200    WRITE    REPORT3-LINE         AFTER      1
            fileReport3Contents.add("");

            // 061300    MOVE     WK-TOTCNT               TO      302007-CDCNT-P
            // 061400    MOVE     WK-TOTAMT               TO      302007-CDAMT-P
            int _302007CdcntP = wkTotcnt;
            BigDecimal _302007CdamtP = wkTotamt;
            // 061500    WRITE    REPORT3-LINE          FROM      302007-TOT3
            // 028700 01   302007-TOT3.
            // 028800  03  FILLER                           PIC X(36) VALUE SPACES.
            // 028900  03  FILLER                           PIC X(18) VALUE
            // 029000      " 代收電費總筆數 : ".
            // 029100  03  302007-CDCNT-P                   PIC ZZZ,ZZZ,ZZ9.
            // 029200  03  FILLER                           PIC X(01) VALUE SPACES.
            // 029300  03  FILLER                           PIC X(18) VALUE
            // 029400      " 代收電費總金額 : ".
            // 029500  03  302007-CDAMT-P                   PIC ZZZ,ZZZ,ZZZ,ZZ9.
            sb = new StringBuilder();
            sb.append(formatUtil.padX("", 36));
            sb.append(formatUtil.padX(" 代收電費總筆數 : ", 18));
            sb.append(reportUtil.customFormat("" + _302007CdcntP, "ZZZ,ZZZ,ZZ9"));
            sb.append(formatUtil.padX("", 1));
            sb.append(formatUtil.padX(" 代收電費總金額 : ", 18));
            sb.append(reportUtil.customFormat("" + _302007CdamtP, "ZZZ,ZZZ,ZZZ,ZZ9"));
            fileReport3Contents.add(sb.toString());

            // 061600    MOVE     1                       TO      WK-SEQNO
            // 061700    MOVE     0                       TO      WK-PAGE,WK-PCTL
            // 061800    MOVE     0                       TO      WK-TOTCNT,WK-TOTAMT.
            wkSeqno = 1;
            wkPage = 0;
            wkPctl = 0;
            wkTotcnt = 0;
            wkTotamt = new BigDecimal(0);
        }
        // 061900 302007-WTOTR3-E.
    }

    private void _302007_R3last() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV302007Lsnr _302007_F");
        // 044800 302007-R3LAST.
        //// 關閉檔案
        // 044900     CLOSE     FD-PUTF    WITH  SAVE.
        // 045000     CLOSE     REPORTFL3  WITH  SAVE.
        try {
            textFile.writeFileContent(wkReport3dir, fileReport3Contents, CHARSET_BIG5);
            upload(wkReport3dir, "RPT", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void _302007_F() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV302007Lsnr _302007_F");
        //// 挑 PUTF-CTL=11明細資料
        //// PUTF-CTL NOT= 11，跳到02007-FNEXT，LOOP讀下一筆FD-PUTF

        // 046600     IF        PUTF-CTL       NOT =      11
        if (parse.string2Integer(putfCtl) != 11) {
            // 046700       GO TO   302007-FNEXT.
            return;
        }
        //// 搬PUTF-...到302007-REC...
        // 046800     MOVE      SPACES            TO      302007-REC.
        // 046900     MOVE      "2"               TO      302007-RC.
        String _302007Rc = "2";
        // 047000     MOVE      "004"             TO      302007-SEND.
        String _302007Send = "004";
        // 047100     MOVE      "TPC"             TO      302007-RECV.
        String _302007Recv = "TPC";
        // 047200     MOVE      102               TO      302007-MTYPE.
        int _302007Mtype = 102;
        // 047300     IF        PUTF-DATE         <       900000

        String _302007Date = "";
        if (parse.string2Integer(putfDate) < 900000) {
            // 047400       MOVE    "1"               TO      302007-DATE(1:1)
            // 047500       MOVE    PUTF-DATE(1:6)    TO      302007-DATE(2:6)
            _302007Date = "1" + putfDate.substring(0, 6);
        } else {
            // 047600     ELSE
            // 047700       MOVE    "0"               TO      302007-DATE(1:1)
            // 047800       MOVE    PUTF-DATE(1:6)    TO      302007-DATE(2:6).
            _302007Date = "0" + putfDate.substring(0, 6);
        }
        // 048000     MOVE      ZEROS             TO      302007-ACT.
        BigDecimal _302007Act = new BigDecimal(0);
        // 048100     MOVE      PUTF-AMT          TO      302007-AMT.
        BigDecimal _302007Amt = parse.string2BigDecimal(putfAmt);
        // 048200     MOVE      PUTF-USERDATA     TO      WK-USERDATA.
        wkUserdata = putfUserdata;
        // 048300     MOVE      PUTF-RCPTID       TO      WK-RCPTID.
        wkRcptid = putfRcptid;
        // 048400     IF        PUTF-TXTYPE       =       "F"
        String _302007No = "";
        String _302007Chkdg = "";
        String _302007Receipt = "";
        if ("F".equals(putfTxtype)) {
            // 048500         MOVE  PUTF-RCPTID(1:10) TO      302007-NO(1:10)
            // 048600         MOVE  WK-USERDATA(33:1) TO      302007-NO(11:1)
            // 048700         MOVE  0                 TO      302007-CHKDG
            _302007No = putfRcptid.substring(0, 10) + wkUserdata.substring(32, 33);
            _302007Chkdg = "0";
        } else if ("C".equals(putfTxtype) || "M".equals(putfTxtype)) {
            // 048720     ELSE IF   PUTF-TXTYPE       =       "C" OR "M"
            // 048740         MOVE  PUTF-USERDATA(1:11) TO    302007-NO
            // 048760         MOVE  PUTF-USERDATA(12:1) TO    302007-CHKDG
            // 048780         MOVE  PUTF-USERDATA(19:5) TO    302007-RECEIPT
            _302007No = putfUserdata.substring(0, 11);
            _302007Chkdg = putfUserdata.substring(11, 12);
            _302007Receipt = putfUserdata.substring(18, 23);
        } else {
            // 048800     ELSE
            // 048900         MOVE  PUTF-USERDATA(1:11) TO    302007-NO
            // 049000         MOVE  PUTF-USERDATA(12:1) TO    302007-CHKDG.
            _302007No = putfUserdata.substring(0, 11);
            _302007Chkdg = putfUserdata.substring(11, 12);
        }
        String _302007Eledate = "";
        // 049200     IF  WK-ELEDATE              <       900000
        if (parse.string2Integer(wkEledate) < 900000) {
            // 049300         MOVE  "1"               TO      302007-ELEDATE(1:1)
            // 049400         MOVE  PUTF-RCPTID(11:6) TO      302007-ELEDATE(2:6)
            _302007Eledate = "1" + putfRcptid.substring(10, 16);
        } else {
            // 049500     ELSE
            // 049600         MOVE  "0"               TO      302007-ELEDATE(1:1)
            // 049700         MOVE  PUTF-RCPTID(11:6) TO      302007-ELEDATE(2:6).
            _302007Eledate = "0" + putfRcptid.substring(10, 16);
        }
        String _302007Kind = "";
        // 049900     IF        PUTF-TXTYPE       =       "V"
        if ("V".equals(putfTxtype)) {
            // 050000       MOVE    31                TO      302007-KIND
            _302007Kind = "31";
        } else if ("C".equals(putfTxtype) || "M".equals(putfTxtype)) {
            // 050100     ELSE
            // 050200     IF        PUTF-TXTYPE       =       "C" OR "M"
            // 050300       MOVE    32                TO      302007-KIND
            _302007Kind = "32";
        } else if ("W".equals(putfTxtype)) {
            // 050400     ELSE
            // 050500     IF        PUTF-TXTYPE       =       "W"
            // 050600       MOVE    36                TO      302007-KIND
            _302007Kind = "36";
        } else {
            // 050700     ELSE
            // 050800       MOVE    33                TO      302007-KIND.
            _302007Kind = "33";
        }
        // 050900     MOVE      "SJA0"            TO      302007-SJA0.
        String _302007Sja0 = "SJA0";

        //// 代收類別="530021"，執行530021-CHKEDATE-RTN，計算台電收費日
        // 0051020     IF        PUTF-CODE         =       "530021"
        if ("530021".equals(putfCode)) {
            // 051040       MOVE    PUTF-RCPTID(1:11) TO      302007-NO
            // 051050       PERFORM 530021-CHKEDATE-RTN THRU  530021-CHKEDATE-EXIT
            // 051060       MOVE    WK-530021-ELEDATE(2:7) TO 302007-ELEDATE.
            _302007No = putfRcptid.substring(0, 11);
            _530021_Chkedate();
            _302007Eledate = wk530021Eledate.substring(1, 8);
        }
        //// 寫檔FD-302007(明細)
        // 051100     WRITE     302007-REC.
        sb = new StringBuilder();
        // 005400  01     302007-REC.
        // 005500     03  302007-RC              PIC 9(01).
        sb.append(formatUtil.pad9(_302007Rc, 1));
        // 005600     03  302007-SEND            PIC X(08).
        sb.append(formatUtil.padX(_302007Send, 8));
        // 005700     03  302007-RECV            PIC X(08).
        sb.append(formatUtil.padX(_302007Recv, 8));
        // 005800     03  302007-MTYPE           PIC 9(03).
        sb.append(formatUtil.pad9("" + _302007Mtype, 3));
        // 005900     03  302007-DATE            PIC 9(07).
        sb.append(formatUtil.pad9(_302007Date, 7));
        // 006000     03  302007-1.
        // 006100      05 302007-TX              PIC 9(01).
        // 006200      05 FILLER                 PIC X(92).
        // 006300     03  302007-2  REDEFINES  302007-1.
        // 006400      05 302007-ACT             PIC 9(14).
        sb.append(formatUtil.pad9("" + _302007Act, 14));
        // 006500      05 302007-AMT             PIC 9(12)V99.
        sb.append(formatUtil.pad9("" + _302007Amt, 12) + "00");
        // 006600      05 302007-CHKDG           PIC 9(01).
        sb.append(formatUtil.pad9(_302007Chkdg, 1));
        // 006700      05 FILLER                 PIC X(02).
        sb.append(formatUtil.padX("", 2));
        // 006750      05 302007-RECEIPT         PIC X(05).
        sb.append(formatUtil.padX(_302007Receipt, 5));
        // 006800      05 302007-KIND            PIC 9(02).
        sb.append(formatUtil.pad9(_302007Kind, 2));
        // 006900      05 302007-NO              PIC X(11).
        sb.append(formatUtil.padX(_302007No, 11));
        // 007000      05 FILLER                 PIC X(02).
        sb.append(formatUtil.padX("", 2));
        // 007100      05 302007-SJA0            PIC X(04).
        sb.append(formatUtil.padX(_302007Sja0, 4));
        // 007200      05 302007-ELEDATE         PIC 9(07).
        sb.append(formatUtil.pad9(_302007Eledate, 7));
        // 007300      05 FILLER                 PIC X(31).
        sb.append(formatUtil.padX("", 31));
        // 007400     03  302007-3  REDEFINES  302007-1.
        // 007500      05 302007-ZERO            PIC X(26).
        // 007600      05 302007-TOTAMT          PIC 9(14)V99.
        // 007700      05 302007-TOTCNT          PIC 9(10).
        // 007800      05 FILLER                 PIC X(41).

        file302007Contents.add(sb.toString());

        //// 累計筆數、金額
        // 051200     ADD       PUTF-AMT          TO      WK-TOTAMT.
        // 051300     ADD       1                 TO      WK-TOTCNT.
        wkTotamt = wkTotamt.add(parse.string2BigDecimal(putfAmt));
        wkTotcnt = wkTotcnt + 1;
        //// LOOP讀下一筆FD-PUTF
        // 051400     GO TO     302007-FNEXT.
    }

    private void _302007_Flast() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV302007Lsnr _302007_Flast");
        // 051500 302007-FLAST.

        //// 寫檔FD-302007(總數，LAST RECORD)
        //// 搬相關資料到302007-REC...
        // 051600     MOVE      SPACES            TO      302007-REC.
        // 051700     MOVE      "3"               TO      302007-RC.
        // 051800     MOVE      "004"             TO      302007-SEND.
        // 051900     MOVE      "TPC"             TO      302007-RECV.
        // 052000     MOVE      102               TO      302007-MTYPE.
        // 052100     MOVE      WK-YYYMMDD        TO      302007-DATE.
        // 052200     MOVE      WK-TOTAMT         TO      302007-TOTAMT.
        // 052300     MOVE      WK-TOTCNT         TO      302007-TOTCNT.
        // 052400     MOVE      "00000000000000000000000000" TO 302007-ZERO.
        String _302007Rc = "3";
        String _302007Send = "004";
        String _302007Recv = "TPC";
        String _302007Mtype = "102";
        String _302007Date = "" + wkYYYMMDD;
        String _302007Totamt = "" + wkTotamt;
        String _302007Totcnt = "" + wkTotcnt;
        String _302007ZERO = "00000000000000000000000000";
        //// 寫檔FD-302007(總數，LAST RECORD)
        //
        // 052500     WRITE     302007-REC.

        // 005400  01     302007-REC.
        // 005500     03  302007-RC              PIC 9(01).
        // 005600     03  302007-SEND            PIC X(08).
        // 005700     03  302007-RECV            PIC X(08).
        // 005800     03  302007-MTYPE           PIC 9(03).
        // 005900     03  302007-DATE            PIC 9(07).
        // 006000     03  302007-1.
        // 007400     03  302007-3  REDEFINES  302007-1.
        // 007500      05 302007-ZERO            PIC X(26).
        // 007600      05 302007-TOTAMT          PIC 9(14)V99.
        // 007700      05 302007-TOTCNT          PIC 9(10).
        // 007800      05 FILLER                 PIC X(41).
        sb = new StringBuilder();
        sb.append(formatUtil.pad9(_302007Rc, 1));
        sb.append(formatUtil.padX(_302007Send, 8));
        sb.append(formatUtil.padX(_302007Recv, 8));
        sb.append(formatUtil.pad9(_302007Mtype, 3));
        sb.append(formatUtil.pad9(_302007Date, 7));
        sb.append(formatUtil.padX(_302007ZERO, 26));
        sb.append(formatUtil.pad9(_302007Totamt, 14) + "00");
        sb.append(formatUtil.pad9(_302007Totcnt, 10));
        sb.append(formatUtil.padX("", 41));
        file302007Contents.add(sb.toString());
        //// 關檔
        //
        // 052600     CLOSE     FD-PUTF    WITH  SAVE.
        // 052700     CHANGE ATTRIBUTE FILENAME OF FD-302007 TO WK-PUTDIR.
        // 052800     CLOSE     FD-302007  WITH  SAVE.
        textFile.deleteFile(wkPutdir);
        try {
            textFile.writeFileContent(wkPutdir, file302007Contents, CHARSET);
            upload(wkPutdir, "DATA", "PUTF");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
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

    private void moveErrorResponse(LogicException e) {
        // this.event.setPeripheryRequest();
    }

    public static boolean isValidDate(String date, String format) {
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        sdf.setLenient(false);
        try {
            sdf.parse(date);
            return true;
        } catch (ParseException e) {
            return false;
        }
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
        String fileNameX = wkReport2dir + "," + wkReport3dir;
        responseTextMap.put("RPTNAME", fileNameX);
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", responseTextMap);
    }
}
