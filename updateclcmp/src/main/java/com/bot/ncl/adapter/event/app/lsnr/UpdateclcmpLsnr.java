/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.Updateclcmp;
import com.bot.ncl.dto.entities.ClcmpBus;
import com.bot.ncl.dto.entities.CldmrBus;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.jpa.entities.impl.ClcmpId;
import com.bot.ncl.jpa.entities.impl.CldmrId;
import com.bot.ncl.jpa.svc.ClcmpService;
import com.bot.ncl.jpa.svc.CldmrService;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FileStockCtl;
import com.bot.ncl.util.fileVo.FileStockDtl;
import com.bot.ncl.util.fileVo.FileStockTot;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.util.date.DateUtil;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.text.format.Text2VoFormatter;
import java.io.File;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("UpdateclcmpLsnr")
@Scope("prototype")
public class UpdateclcmpLsnr extends BatchListenerCase<Updateclcmp> {
    @Autowired private Text2VoFormatter text2VoFormatter;

    @Autowired private DateUtil dateUtil;

    @Autowired private Parse parse;

    @Autowired private ClmrService clmrService;

    @Autowired private ClcmpService clcmpService;

    @Autowired private CldmrService cldmrService;
    @Autowired private FsapBatchUtil fsapBatchUtil;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET = "UTF-8";
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";

    private static final String PATH_SEPARATOR = File.separator;

    @Autowired private TextFileUtil textFileUtil;

    @Autowired private FormatUtil formatUtil;

    @Autowired private ReportUtil reportUtil;
    private Updateclcmp event;

    // 004600 01 WK-PUTDIR.
    // 004700    03 FILLER                      PIC X(18)
    // 004800                   VALUE "DATA/GN/UPL/CL002/".
    // 004900    03 WK-PUT-BRNO                 PIC X(03).
    // 005000    03 FILLER                      PIC X(01) VALUE "/".
    // 005100    03 WK-PUT-FDATE                PIC X(07).
    // 005200    03 FILLER                      PIC X(01) VALUE "/".
    // 005300    03 WK-PUT-FILE.
    // 005400       05 WK-PUT-FILE-TYPE         PIC X(01).
    // 005500       05 WK-PUT-FILE-CLNUM        PIC X(05).
    // 005600       05 WK-PUT-FILE-CODE         PIC X(06).
    // 005700    03 FILLER                      PIC X(01) VALUE ".".
    private String wkPutdir;
    private String wkPutBrno;
    private String wkPutFdate;
    private String wkPutFile;

    private String wkErr;
    private String wkErrctl;
    private String wkKeepCode;
    private String wkKeepLmtdate;
    private String wkKeepLmttime;
    private String wkKeepKepDate;
    private String wkkeepLmtflag;
    private String wkKeepCdate;
    private String wkKeepUdate;
    private String wkKeepUtime;
    private String wkCodeRpt;
    private int wkAddTotcnt;
    private int wkAddTotamt;
    private int wkDelTotcnt;
    private int wkDelTotamt;

    private int wkRtncd;
    private String wkTextP;
    private String wkMsgflg;
    private String wkFdate;
    private String processDate;

    private List<String> inputdataList;

    private List<String> reportLineList;
    private Map<String, String> labelMap;
    private Map<String, String> textMap;
    private String[] wkParamL;

    @Override
    public void onApplicationEvent(Updateclcmp event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "UpdateclcmpLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Updateclcmp event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "UpdateclcmpLsnr run()");
        if (!init(event)) {
            batchResponse();
            return;
        }
        mainRtn();
        // 010900     DISPLAY "SYM/CL/BH/UPDCLCMP OK".
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "SYM/CL/BH/UPDCLCMP OK");
        //// 若無錯誤資料，寫一筆REPORTFL報表明細(WK-NOERR-LINE)
        // 011000     IF      WK-ERRCTL         =      "N"
        if (wkErrctl.equals("N")) {
            // 008900 01 WK-NOERR-LINE.
            // 009000  03 FILLER                            PIC X(12) VALUE
            // 009100                                           " 無錯誤資料 ".

            // 011100       MOVE    SPACES          TO     REPORT-LINE
            // 011200       WRITE   REPORT-LINE    FROM    WK-NOERR-LINE.
            reportLineList.add(formatUtil.padX(" 無錯誤資料 ", 150));
        }
        reportOutput();
        batchResponse();
    }

    private Boolean init(Updateclcmp event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "init()");
        // 009800 PROCEDURE         DIVISION  USING F-BRNO,
        // 009900                                   F-FDATE,
        // 010000                                   F-FILENAME.
        // 010200     MOVE          F-BRNO          TO     WK-PUT-BRNO.
        // 010300     MOVE          F-FDATE         TO     WK-PUT-FDATE.
        // 010400     MOVE          F-FILENAME      TO     WK-PUT-FILE.
        // 010500     CHANGE ATTRIBUTE FILENAME  OF FD-UPDCLCMP TO WK-PUTDIR.
        this.event = event;
        // ST1-Wei:要用傳入參數組讀檔路徑
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();

        Map<String, String> paramMap;
        paramMap = getG2006Param(textMap.get("PARAM"));
        if (paramMap == null) {
            return false;
        }
        wkPutFdate = textMap.get("DATE"); // TODO: 待確認BATCH參數名稱
        wkPutBrno = paramMap.get("PBRNO"); // TODO: 待確認BATCH參數名稱
        wkPutFile = textMap.get("FILENAME"); // TODO: 待確認BATCH參數名稱
        // 設定本營業日、檔名日期變數值
        processDate = labelMap.get("PROCESS_DATE"); // 待中菲APPLE提供正確名稱

        wkFdate = formatUtil.pad9(processDate, 7).substring(1, 7);

        wkPutdir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + wkPutBrno
                        + PATH_SEPARATOR
                        + wkPutFdate
                        + PATH_SEPARATOR
                        + wkPutFile;
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkPutdir = {}", wkPutdir);

        reportLineList = new ArrayList<>();
        return true;
    }

    private void mainRtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "mainRtn()");
        // 011900     MOVE    "N"                TO WK-ERR,WK-ERRCTL.
        // 012000     MOVE    WK-PUT-FILE-CODE   TO WK-KEEP-CODE,
        // 012100                                   WK-CODE-RPT.
        // 012200     MOVE    0                  TO WK-ADD-TOTCNT,WK-ADD-TOTAMT,
        // 012300                                   WK-DEL-TOTCNT,WK-DEL-TOTAMT.
        wkErr = "N";
        wkErrctl = "N";
        wkKeepCode = wkPutFile.substring(6);
        wkCodeRpt = wkPutFile.substring(6);
        wkAddTotcnt = 0;
        wkAddTotamt = 0;
        wkDelTotcnt = 0;
        wkDelTotamt = 0;

        // 012900     PERFORM RPT-TITLE-RTN      THRU  RPT-TITLE-EXIT.
        reportTitle();
        if (!textFileUtil.exists(wkPutdir)) {
            return;
        }
        inputdataList = textFileUtil.readFileContent(wkPutdir, CHARSET);

        inputdataList.forEach(
                inputData -> {
                    ApLogHelper.info(
                            log, false, LogType.NORMAL.getCode(), "inputData = {}", inputData);
                });

        // 013000 0000-READ-NEXT.
        for (String inputData : inputdataList) {
            // 013100     MOVE    0                  TO    WK-RTNCD.
            // 013200     MOVE    SPACES             TO    WK-TEXT-P.
            // 013300     MOVE    SPACES             TO    WK-MSGFLG.
            wkRtncd = 0;
            wkTextP = "";
            wkMsgflg = "";
            String f1 = inputData.substring(0, 1);
            switch (f1) {
                case "1":
                    createForm1000(inputData);
                    break;
                case "2":
                    createForm2000(inputData);
                    break;
                case "3":
                    createForm3000(inputData);
                    break;
                default:
                    // 014400       MOVE  " 無此格式 "       TO    WK-TEXT-P
                    // 014500       MOVE  STK-REC            TO    WK-REC
                    // 014600       PERFORM 9000-WRITE-ERR-RTN THRU 9000-WRITE-ERR-EXIT.
                    writeError(" 無此格式 ", inputData);
                    break;
            }
            // 014700     IF     WK-ERR     =       "Y"
            // 014800       GO   TO   0000-MAIN-EXIT.
            if (wkErr.equals("Y")) {
                break;
            }
        }
    }

    private void reportTitle() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "reportTitle()");
        // 028400 RPT-TITLE-RTN.
        // 028500     MOVE       SPACES            TO      REPORT-LINE.
        // 028600     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE1.
        reportLineList.add(wkTitleLine1());
        // 028700     MOVE       SPACES            TO      REPORT-LINE.
        // 028800     WRITE      REPORT-LINE       AFTER   1.
        reportLineList.add(" ".repeat(150));
        // 028900     MOVE       F-BRNO            TO      WK-PBRNO.
        // 029000     MOVE       F-FDATE           TO      WK-PDATE.
        // 029100*    MOVE       1                 TO      WK-TOTPAGE.
        // 029200     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE2.
        reportLineList.add(wkTitleLine2());
        // 029300     MOVE       SPACES            TO      REPORT-LINE.
        // 029400     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE3.
        reportLineList.add(wkTitleLine3());
        // 029500     MOVE       SPACES            TO      REPORT-LINE.
        // 029600     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE4.
        reportLineList.add(wkTitleLine4());
        // 029700 RPT-TITLE-EXIT.
        // 029800     EXIT.
    }

    private String wkTitleLine1() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkTitleLine1()");
        // 006400 01 WK-TITLE-LINE1.
        // 006500    02 FILLER                       PIC X(33) VALUE SPACE.
        // 006600    02 TITLE-LABEL                  PIC X(32)
        // 006700                    VALUE " 代收比對維護錯誤檢核表三 ".
        // 006800    02 FILLER                       PIC X(21) VALUE SPACE.
        // 006900    02 FILLER                       PIC X(12)
        // 007000                    VALUE "FORM : C042 ".                         96/11/29
        StringBuilder sb = new StringBuilder();
        sb.append(" ".repeat(33));
        sb.append(formatUtil.padX(" 代收比對維護錯誤檢核表三 ", 32));
        sb.append(" ".repeat(21));
        sb.append(formatUtil.padX("FORM : C042 ", 12));
        return formatUtil.padX(sb.toString(), 150);
    }

    private String wkTitleLine2() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkTitleLine2()");
        // 007100 01 WK-TITLE-LINE2.
        // 007200    02 FILLER                       PIC X(10)
        // 007300                              VALUE " 分行別： ".
        // 007400    02 WK-PBRNO                     PIC 9(03).
        // 007500    02 FILLER                       PIC X(05) VALUE SPACE.
        // 007600    02 FILLER                       PIC X(13)
        // 007700                              VALUE "  印表日期： ".
        // 007800    02 WK-PDATE                     PIC 99/99/99.
        // 007900    02 FILLER                       PIC X(38) VALUE SPACE.
        StringBuilder sb = new StringBuilder();
        sb.append(formatUtil.padX(" 分行別： ", 10));
        sb.append(formatUtil.pad9(wkPutBrno, 3));
        sb.append(" ".repeat(5));
        sb.append(formatUtil.padX("  印表日期： ", 13));
        sb.append(reportUtil.customFormat(wkPutFdate, "99/99/99"));
        sb.append(" ".repeat(38));
        return formatUtil.padX(sb.toString(), 150);
    }

    private String wkTitleLine3() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkTitleLine3()");
        // 008300 01 WK-TITLE-LINE3.
        // 008400    02 FILLER                       PIC X(12)
        // 008500                              VALUE " 代收類別： ".
        // 008600    02 WK-CODE-RPT                  PIC X(06).
        StringBuilder sb = new StringBuilder();
        sb.append(formatUtil.padX(" 代收類別： ", 12));
        sb.append(formatUtil.padX(wkCodeRpt, 6));
        return formatUtil.padX(sb.toString(), 150);
    }

    private String wkTitleLine4() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkTitleLine4()");
        // 008700 01 WK-TITLE-LINE4.
        // 008800    02 FILLER                       PIC X(100) VALUE ALL "-".
        return formatUtil.padX("-".repeat(100), 150);
    }

    private void createForm1000(String inputData) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "createForm1000()");
        // 015300 1000-CRE-FORM-RTN.
        FileStockCtl fileStockCtl = new FileStockCtl();
        text2VoFormatter.format(inputData, fileStockCtl);

        // 015400     IF      WK-KEEP-CODE         NOT =  STK-CODE
        // 015500       MOVE  " 輸入代收類別錯誤 " TO     WK-TEXT-P
        // 015600       MOVE  STK-REC              TO     WK-REC
        // 015700       PERFORM 9000-WRITE-ERR-RTN THRU 9000-WRITE-ERR-EXIT
        // 015800       MOVE  "Y"                  TO     WK-ERR,WK-ERRCTL
        // 015900       GO  TO  1000-CRE-FORM-EXIT.
        if (!wkKeepCode.equals(fileStockCtl.getCode())) {
            writeError(" 輸入代收類別錯誤 ", inputData);
            wkErr = "Y";
            wkErrctl = "Y";
            return;
        }

        // 015950     SET    DB-CLMR-IDX1          TO     BEGINNING.               97/04/25
        // 016000     FIND   DB-CLMR-IDX1  AT DB-CLMR-CODE = WK-KEEP-CODE
        ClmrBus clmrBus = clmrService.findById(wkKeepCode);

        // 016100       ON    EXCEPTION
        // 016200          MOVE  " 輸入代收類別不存在 "  TO WK-TEXT-P
        // 016300          MOVE  STK-REC            TO    WK-REC
        // 016400          PERFORM 9000-WRITE-ERR-RTN THRU 9000-WRITE-ERR-EXIT
        // 016500          MOVE    "Y"             TO     WK-ERR,WK-ERRCTL
        // 016600          GO  TO  1000-CRE-FORM-EXIT.
        if (Objects.isNull(clmrBus)) {
            writeError(" 輸入代收類別不存在 ", inputData);
            wkErr = "Y";
            wkErrctl = "Y";
            return;
        }

        // 016700     MOVE    STK-LMTDATE       TO     WK-KEEP-LMTDATE.
        wkKeepLmtdate = fileStockCtl.getLmtdate();
        // 016800     MOVE    STK-LMTTIME       TO     WK-KEEP-LMTTIME.
        wkKeepLmttime = fileStockCtl.getLmttime();
        // 016900     MOVE    STK-LMTFLAG       TO     WK-KEEP-LMTFLAG.
        wkkeepLmtflag = fileStockCtl.getLmtflag();
        // 017000     MOVE    STK-KEPDATE       TO     WK-KEEP-KEPDATE.
        wkKeepKepDate = fileStockCtl.getKepdate();
        // 017100     MOVE    PARA-YMD          TO     WK-KEEP-CDATE,
        // 017200                                      WK-KEEP-UDATE.
        dateUtil = new DateUtil();
        wkKeepCdate = dateUtil.getNowStringRoc();
        wkKeepUdate = dateUtil.getNowStringRoc();
        // 017300     MOVE    PARA-HHMMSS       TO     WK-KEEP-UTIME.
        wkKeepUtime = dateUtil.getNowStringTime(false);
        // 017400 1000-CRE-FORM-EXIT.
    }

    private void createForm2000(String inputData) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "createForm2000()");
        // 017700 2000-CRE-FORM-RTN.
        FileStockDtl fileStockDtl = new FileStockDtl();
        text2VoFormatter.format(inputData, fileStockDtl);
        // 017800     IF      WK-KEEP-UDATE     =      0
        // 017900       MOVE  " 此檔案沒有首筆 "   TO  WK-TEXT-P
        // 018000       PERFORM 9000-WRITE-ERR-RTN THRU 9000-WRITE-ERR-EXIT
        // 018100       MOVE    "Y"             TO     WK-ERR,WK-ERRCTL
        // 018200       GO  TO  2000-CRE-FORM-EXIT.
        if (wkKeepUdate.equals("0")) {
            writeError(" 此檔案沒有首筆 ", "");
            wkErr = "Y";
            wkErrctl = "Y";
            return;
        }
        // 018300     IF      STK-UFLG          NOT = 0  AND  NOT = 9
        // 018400       MOVE  " 非新增或修改的交易 "  TO  WK-TEXT-P
        // 018500       MOVE  STK-REC           TO     WK-REC
        // 018600       PERFORM 9000-WRITE-ERR-RTN THRU 9000-WRITE-ERR-EXIT
        // 018700       MOVE    "Y"             TO     WK-ERRCTL
        // 018800       GO  TO  2000-CRE-FORM-EXIT.
        if (!fileStockDtl.getUflg().equals("0") && !fileStockDtl.getUflg().equals("9")) {
            writeError(" 非新增或修改的交易 ", inputData);
            wkErrctl = "Y";
            return;
        }
        // 018900     IF      STK-UFLG          =      0
        // 019000       PERFORM   2000-ADD-FORM-RTN   THRU   2000-ADD-FORM-EXIT
        // 019100     ELSE
        // 019200       PERFORM   2000-DEL-FORM-RTN   THRU   2000-DEL-FORM-EXIT.
        if (fileStockDtl.getUflg().equals("0")) {
            addForm2000(fileStockDtl, inputData);
        } else {
            delForm2000(fileStockDtl, inputData);
        }
        // 019300 2000-CRE-FORM-EXIT.
    }

    private void addForm2000(FileStockDtl fileStockDtl, String inputData) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "addForm2000()");
        // 022000 2000-ADD-FORM-RTN.

        // 資料轉換: CLCMP拆為CLCMP & CLDMR
        // 以下收付類別轉入虛擬分戶檔CLDMR
        // 111801,121801,111981,121981,121961,118xxx,128xxx,119xxx,129xxx
        // 其他都轉入代收比對檔CLCMP
        if (wkKeepCode.equals("111801")
                || wkKeepCode.equals("121801")
                || wkKeepCode.equals("111981")
                || wkKeepCode.equals("121981")
                || wkKeepCode.equals("121961")
                || wkKeepCode.startsWith("118")
                || wkKeepCode.startsWith("128")
                || wkKeepCode.startsWith("119")
                || wkKeepCode.startsWith("129")) {
            // 022100     FIND   DB-CLCMP-IDX1  AT  DB-CLCMP-CODE   = WK-KEEP-CODE
            // 022200                          AND  DB-CLCMP-RCPTID = STK-RCPTID
            CldmrId cldmrId = new CldmrId();
            cldmrId.setCode(wkKeepCode);
            cldmrId.setRcptid(fileStockDtl.getRcptid());
            cldmrId.setCurcd(0);
            CldmrBus cldmrBus = cldmrService.findById(cldmrId);
            // 022300       ON   EXCEPTION
            // 022400            MOVE  "NORMAL"  TO   WK-MSGFLG.
            // 022500     IF      WK-MSGFLG           NOT    =     "NORMAL"
            // 022600       MOVE  "Y"           TO           WK-ERRCTL
            // 022700       MOVE  " 該筆比對資料已存在 "  TO WK-TEXT-P
            // 022800       MOVE  STK-REC       TO           WK-REC
            // 022900       PERFORM 9000-WRITE-ERR-RTN THRU  9000-WRITE-ERR-EXIT
            // 023000       ADD   1             TO           WK-ADD-TOTCNT
            // 023100       ADD   STK-AMT       TO           WK-ADD-TOTAMT
            // 023200       GO  TO  2000-ADD-FORM-EXIT.                                < CMT >
            if (!Objects.isNull(cldmrBus)) {
                writeError(" 該筆比對資料已存在 ", inputData);
                wkAddTotcnt++;
                wkAddTotamt += parse.string2Integer(fileStockDtl.getAmt());
            } else {
                // 023500     CREATE  DB-CLCMP-DDS.
                cldmrBus = new CldmrBus();
                // 023600     MOVE    WK-KEEP-CODE         TO    DB-CLCMP-CODE.
                cldmrBus.setCode(wkKeepCode);
                // 023700     MOVE    STK-RCPTID           TO    DB-CLCMP-RCPTID.
                cldmrBus.setRcptid(fileStockDtl.getRcptid());
                // 023800     MOVE    STK-AMT              TO    DB-CLCMP-AMT.
                cldmrBus.setBal(parse.string2BigDecimal(fileStockDtl.getAmt()));
                // 023820     IF      WK-KEEP-CODE         =     "111801"                  98/03/27
                // 023840         MOVE    SPACES           TO    DB-CLCMP-PNAME            98/03/27
                // 023860     ELSE                                                         98/03/27
                // 023900         MOVE    STK-PNAME        TO    DB-CLCMP-PNAME.           98/03/27
                if (wkKeepCode.equals("111801")) {
                    cldmrBus.setPname("");
                } else {
                    cldmrBus.setPname(fileStockDtl.getPname());
                }
                // 024000     MOVE    WK-KEEP-LMTDATE      TO    DB-CLCMP-LDATE.
                cldmrBus.setLdate(parse.string2Integer(wkKeepLmtdate));
                // 024100     MOVE    WK-KEEP-LMTTIME      TO    DB-CLCMP-LTIME.
                cldmrBus.setLtime(parse.string2Integer(wkKeepLmttime));
                // 024200     MOVE    WK-KEEP-LMTFLAG      TO    DB-CLCMP-LFLG.
                cldmrBus.setLflg(parse.string2Integer(wkkeepLmtflag));
                // 024300     MOVE    WK-KEEP-KEPDATE      TO    DB-CLCMP-KDATE.
                cldmrBus.setKdate(parse.string2Integer(wkKeepKepDate));
                // 024400     MOVE    WK-KEEP-CDATE        TO    DB-CLCMP-CDATE.
                cldmrBus.setCdate(parse.string2Integer(wkKeepCdate));
                // 024500     MOVE    WK-KEEP-UDATE        TO    DB-CLCMP-UDATE.
                cldmrBus.setUdate(parse.string2Integer(wkKeepUdate));
                // 024600     MOVE    WK-KEEP-UTIME        TO    DB-CLCMP-UTIME.
                cldmrBus.setUtime(parse.string2Integer(wkKeepUtime));

                // 新增欄位:ID CURCD EMPNO
                cldmrBus.setId("");
                cldmrBus.setCurcd(0);
                cldmrBus.setEmpno("");

                // 024700     BEGIN-TRANSACTION NO-AUDIT RESTART-DST.
                // 024800       STORE    DB-CLCMP-DDS.
                // 024900     END-TRANSACTION NO-AUDIT RESTART-DST.
                cldmrService.insert(cldmrBus);
                // 025000     ADD   1             TO           WK-ADD-TOTCNT.
                // 025100     ADD   STK-AMT       TO           WK-ADD-TOTAMT.
                wkAddTotcnt++;
                wkAddTotamt += parse.string2Integer(fileStockDtl.getAmt());
            }
        } else {
            // 022100     FIND   DB-CLCMP-IDX1  AT  DB-CLCMP-CODE   = WK-KEEP-CODE
            // 022200                          AND  DB-CLCMP-RCPTID = STK-RCPTID
            ClcmpId clcmpId = new ClcmpId();
            clcmpId.setCode(wkKeepCode);
            clcmpId.setRcptid(fileStockDtl.getRcptid());
            ClcmpBus clcmpBus = clcmpService.findById(clcmpId);
            // 022300       ON   EXCEPTION
            // 022400            MOVE  "NORMAL"  TO   WK-MSGFLG.
            // 022500     IF      WK-MSGFLG           NOT    =     "NORMAL"
            // 022600       MOVE  "Y"           TO           WK-ERRCTL
            // 022700       MOVE  " 該筆比對資料已存在 "  TO WK-TEXT-P
            // 022800       MOVE  STK-REC       TO           WK-REC
            // 022900       PERFORM 9000-WRITE-ERR-RTN THRU  9000-WRITE-ERR-EXIT
            // 023000       ADD   1             TO           WK-ADD-TOTCNT
            // 023100       ADD   STK-AMT       TO           WK-ADD-TOTAMT
            // 023200       GO  TO  2000-ADD-FORM-EXIT.                                < CMT >
            if (!Objects.isNull(clcmpBus)) {
                wkErrctl = "Y";
                writeError(" 該筆比對資料已存在 ", inputData);
                wkAddTotcnt++;
                wkAddTotamt += parse.string2Integer(fileStockDtl.getAmt());
            } else {
                // 023500     CREATE  DB-CLCMP-DDS.
                clcmpBus = new ClcmpBus();
                // 023600     MOVE    WK-KEEP-CODE         TO    DB-CLCMP-CODE.
                clcmpBus.setCode(wkKeepCode);
                // 023700     MOVE    STK-RCPTID           TO    DB-CLCMP-RCPTID.
                clcmpBus.setRcptid(fileStockDtl.getRcptid());
                // 023800     MOVE    STK-AMT              TO    DB-CLCMP-AMT.
                clcmpBus.setAmt(parse.string2BigDecimal(fileStockDtl.getAmt()));
                // 023820     IF      WK-KEEP-CODE         =     "111801"                  98/03/27
                // 023840         MOVE    SPACES           TO    DB-CLCMP-PNAME            98/03/27
                // 023860     ELSE                                                         98/03/27
                // 023900         MOVE    STK-PNAME        TO    DB-CLCMP-PNAME.           98/03/27
                if (wkKeepCode.equals("111801")) {
                    clcmpBus.setPname("");
                } else {
                    clcmpBus.setPname(fileStockDtl.getPname());
                }
                // 024000     MOVE    WK-KEEP-LMTDATE      TO    DB-CLCMP-LDATE.
                clcmpBus.setLdate(parse.string2Integer(wkKeepLmtdate));
                // 024100     MOVE    WK-KEEP-LMTTIME      TO    DB-CLCMP-LTIME.
                clcmpBus.setLtime(parse.string2Integer(wkKeepLmttime));
                // 024200     MOVE    WK-KEEP-LMTFLAG      TO    DB-CLCMP-LFLG.
                clcmpBus.setLflg(parse.string2Integer(wkkeepLmtflag));
                // 024300     MOVE    WK-KEEP-KEPDATE      TO    DB-CLCMP-KDATE.
                clcmpBus.setKdate(parse.string2Integer(wkKeepKepDate));
                // 024400     MOVE    WK-KEEP-CDATE        TO    DB-CLCMP-CDATE.
                clcmpBus.setCdate(parse.string2Integer(wkKeepCdate));
                // 024500     MOVE    WK-KEEP-UDATE        TO    DB-CLCMP-UDATE.
                clcmpBus.setUdate(parse.string2Integer(wkKeepUdate));
                // 024600     MOVE    WK-KEEP-UTIME        TO    DB-CLCMP-UTIME.
                clcmpBus.setUtime(parse.string2Integer(wkKeepUtime));

                // 新增欄位:ID CURCD EMPNO SDATE STIME
                clcmpBus.setId("");
                clcmpBus.setCurcd(0);
                clcmpBus.setEmpno("");
                clcmpBus.setSdate(0);
                clcmpBus.setStime(0);

                // 024700     BEGIN-TRANSACTION NO-AUDIT RESTART-DST.
                // 024800       STORE    DB-CLCMP-DDS.
                // 024900     END-TRANSACTION NO-AUDIT RESTART-DST.
                clcmpService.insert(clcmpBus);
                // 025000     ADD   1             TO           WK-ADD-TOTCNT.
                // 025100     ADD   STK-AMT       TO           WK-ADD-TOTAMT.
                wkAddTotcnt++;
                wkAddTotamt += parse.string2Integer(fileStockDtl.getAmt());
            }
        }
        // 025200 2000-ADD-FORM-EXIT.
        // 025300   EXIT.
    }

    private void delForm2000(FileStockDtl fileStockDtl, String inputData) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "delForm2000()");
        // 025500 2000-DEL-FORM-RTN.
        // 資料轉換: CLCMP拆為CLCMP & CLDMR
        // 以下收付類別轉入虛擬分戶檔CLDMR
        // 111801,121801,111981,121981,121961,118xxx,128xxx,119xxx,129xxx
        // 其他都轉入代收比對檔CLCMP
        if (wkKeepCode.equals("111801")
                || wkKeepCode.equals("121801")
                || wkKeepCode.equals("111981")
                || wkKeepCode.equals("121981")
                || wkKeepCode.equals("121961")
                || wkKeepCode.startsWith("118")
                || wkKeepCode.startsWith("128")
                || wkKeepCode.startsWith("119")
                || wkKeepCode.startsWith("129")) {
            // 025600     LOCK   DB-CLCMP-IDX1  AT  DB-CLCMP-CODE   = WK-KEEP-CODE
            // 025700                          AND  DB-CLCMP-RCPTID = STK-RCPTID
            CldmrId cldmrId = new CldmrId();
            cldmrId.setCode(wkKeepCode);
            cldmrId.setRcptid(fileStockDtl.getRcptid());
            cldmrId.setCurcd(0);
            CldmrBus cldmrBus = cldmrService.findById(cldmrId);
            // 025800       ON   EXCEPTION
            // 025900            MOVE  "ERROR "  TO   WK-MSGFLG.
            // 026000     IF     WK-MSGFLG             =     "ERROR "
            // 026100       MOVE  "Y"           TO           WK-ERRCTL
            // 026200       MOVE  " 該筆股東資料不存在 "  TO WK-TEXT-P
            // 026300       MOVE  STK-REC       TO           WK-REC
            // 026400       PERFORM 9000-WRITE-ERR-RTN THRU  9000-WRITE-ERR-EXIT
            // 026500       ADD   1             TO           WK-DEL-TOTCNT
            // 026600       ADD   STK-AMT       TO           WK-DEL-TOTAMT
            // 026700       GO  TO  2000-DEL-FORM-EXIT
            if (Objects.isNull(cldmrBus)) {
                wkErrctl = "Y";
                writeError(" 該筆股東資料不存在 ", inputData);
                wkDelTotcnt++;
                wkDelTotamt += parse.string2Integer(fileStockDtl.getAmt());
            } else {
                // 026800     ELSE
                // 026900       BEGIN-TRANSACTION NO-AUDIT RESTART-DST
                // 027000         DELETE  DB-CLCMP-DDS
                // 027100       END-TRANSACTION NO-AUDIT RESTART-DST
                // 027200       ADD   1             TO           WK-DEL-TOTCNT
                // 027300       ADD   STK-AMT       TO           WK-DEL-TOTAMT.
                cldmrService.delete(cldmrBus);
                wkDelTotcnt++;
                wkDelTotamt += parse.string2Integer(fileStockDtl.getAmt());
            }
        } else {
            // 025600     LOCK   DB-CLCMP-IDX1  AT  DB-CLCMP-CODE   = WK-KEEP-CODE
            // 025700                          AND  DB-CLCMP-RCPTID = STK-RCPTID
            ClcmpId clcmpId = new ClcmpId();
            clcmpId.setCode(wkKeepCode);
            clcmpId.setRcptid(fileStockDtl.getRcptid());
            ClcmpBus clcmpBus = clcmpService.findById(clcmpId);
            // 025800       ON   EXCEPTION
            // 025900            MOVE  "ERROR "  TO   WK-MSGFLG.
            // 026000     IF     WK-MSGFLG             =     "ERROR "
            // 026100       MOVE  "Y"           TO           WK-ERRCTL
            // 026200       MOVE  " 該筆股東資料不存在 "  TO WK-TEXT-P
            // 026300       MOVE  STK-REC       TO           WK-REC
            // 026400       PERFORM 9000-WRITE-ERR-RTN THRU  9000-WRITE-ERR-EXIT
            // 026500       ADD   1             TO           WK-DEL-TOTCNT
            // 026600       ADD   STK-AMT       TO           WK-DEL-TOTAMT
            // 026700       GO  TO  2000-DEL-FORM-EXIT
            if (Objects.isNull(clcmpBus)) {
                wkErrctl = "Y";
                writeError(" 該筆股東資料不存在 ", inputData);
                wkDelTotcnt++;
                wkDelTotamt += parse.string2Integer(fileStockDtl.getAmt());
            } else {
                // 026800     ELSE
                // 026900       BEGIN-TRANSACTION NO-AUDIT RESTART-DST
                // 027000         DELETE  DB-CLCMP-DDS
                // 027100       END-TRANSACTION NO-AUDIT RESTART-DST
                // 027200       ADD   1             TO           WK-DEL-TOTCNT
                // 027300       ADD   STK-AMT       TO           WK-DEL-TOTAMT.
                clcmpService.delete(clcmpBus);
                wkDelTotcnt++;
                wkDelTotamt += parse.string2Integer(fileStockDtl.getAmt());
            }
        }
        // 027400 2000-DEL-FORM-EXIT.
        // 027500   EXIT.
    }

    private void createForm3000(String inputData) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "createForm3000()");
        // 019600 3000-CRE-FORM-RTN.
        FileStockTot fileStockTot = new FileStockTot();
        text2VoFormatter.format(inputData, fileStockTot);
        // 019700     IF      WK-ADD-TOTCNT  NOT =    STK-F3ACNT
        // 019800       MOVE  " 新增筆數不符 "   TO   WK-TEXT-P
        // 019900       MOVE  STK-REC            TO   WK-REC
        // 020000       MOVE  "Y"                TO   WK-ERRCTL
        // 020100       PERFORM 9000-WRITE-ERR-RTN THRU  9000-WRITE-ERR-EXIT.
        if (wkAddTotcnt != parse.string2Integer(fileStockTot.getAcnt())) {
            wkErrctl = "Y";
            writeError(" 新增筆數不符 ", inputData);
        }
        // 020200     IF      WK-ADD-TOTAMT  NOT =    STK-F3AAMT
        // 020300       MOVE  " 新增金額不符 "   TO   WK-TEXT-P
        // 020400       MOVE  STK-REC            TO   WK-REC
        // 020500       MOVE  "Y"                TO   WK-ERRCTL
        // 020600       PERFORM 9000-WRITE-ERR-RTN THRU  9000-WRITE-ERR-EXIT.
        if (wkAddTotamt != parse.string2Integer(fileStockTot.getAamt())) {
            wkErrctl = "Y";
            writeError(" 新增金額不符 ", inputData);
        }
        // 020700     IF      WK-DEL-TOTCNT  NOT =    STK-F3DCNT
        // 020800       MOVE  " 刪除筆數不符 "   TO   WK-TEXT-P
        // 020900       MOVE  STK-REC            TO   WK-REC
        // 021000       MOVE  "Y"                TO   WK-ERRCTL
        // 021100       PERFORM 9000-WRITE-ERR-RTN THRU  9000-WRITE-ERR-EXIT.
        if (wkDelTotcnt != parse.string2Integer(fileStockTot.getDcnt())) {
            wkErrctl = "Y";
            writeError(" 刪除筆數不符 ", inputData);
        }
        // 021200     IF      WK-DEL-TOTAMT  NOT =    STK-F3DAMT
        // 021300       MOVE  " 刪除筆數金額 "   TO   WK-TEXT-P
        // 021400       MOVE  STK-REC            TO   WK-REC
        // 021500       MOVE  "Y"                TO   WK-ERRCTL
        // 021600       PERFORM 9000-WRITE-ERR-RTN THRU  9000-WRITE-ERR-EXIT.
        if (wkDelTotamt != parse.string2Integer(fileStockTot.getDamt())) {
            wkErrctl = "Y";
            writeError(" 刪除筆數金額 ", inputData);
        }
        // 021700 3000-CRE-FORM-EXIT.
        // 021800     EXIT.
    }

    private void writeError(String wkTextP, String wkRecP) {
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "writeError ... wkTextP = {} , wkRecP = {}",
                wkTextP,
                wkRecP);
        // 006200 01 WK-REC                             PIC X(60).

        // 009200 01 WK-TEXT-LINE.
        // 009300  03 WK-TEXT-P                         PIC X(35).
        // 009400  03 WK-REC-P                          PIC X(60).

        // 027800     MOVE    WK-REC            TO     WK-REC-P.
        // 027900     MOVE    SPACES            TO     REPORT-LINE.
        // 028000     WRITE   REPORT-LINE       FROM   WK-TEXT-LINE.
        String wkTextLine = formatUtil.padX(wkTextP, 35) + formatUtil.padX(wkRecP, 60);
        reportLineList.add(formatUtil.padX(wkTextLine, 150));
    }

    private void reportOutput() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "reportOutput()");
        if (!Objects.isNull(reportLineList) && !reportLineList.isEmpty()) {
            // 001700 FD  REPORTFL
            // 001800      VALUE  OF  TITLE  IS  "BD/CL/BH/042."
            String reportFilePath =
                    fileDir
                            + CONVF_RPT
                            + PATH_SEPARATOR
                            + processDate
                            + PATH_SEPARATOR
                            + "CL-BH-042";
            // TODO:最後要產生一行成功筆數資料
            // TODO:最後要產生一行成功金額資料
            textFileUtil.deleteFile(reportFilePath);
            textFileUtil.writeFileContent(reportFilePath, reportLineList, CHARSET);
        }
    }

    private Map<String, String> getG2006Param(String lParam) {
        String[] paramL;
        if (lParam.isEmpty()) {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "lParam is null");
            return null;
        }
        paramL = lParam.split(";");
        if (paramL == null) {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "paramL is null");
            return null;
        }
        // G2006:
        //  PBRNO(;),
        //  HCODE(;),
        //  LEN(;),
        //  PARAM1(;),
        //  PARAM2(;)
        Map<String, String> map = new HashMap<>();
        if (paramL.length > 0) map.put("PBRNO", paramL[0]); // 對應 PBRNO
        if (paramL.length > 1) map.put("HCODE", paramL[1]); // 對應 HCODE
        if (paramL.length > 2) map.put("LEN", paramL[2]); // 對應 LEN
        if (paramL.length > 3) map.put("PARAM1", paramL[3]); // 對應 PARAM1
        if (paramL.length > 4) map.put("PARAM2", paramL[4]); // 對應 PARAM2
        if (map.size() == 0) {
            return null;
        }

        for (String key : map.keySet()) {
            ApLogHelper.info(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "map KEY = {} ,VALUE = {}",
                    key,
                    map.get(key));
        }
        return map;
    }

    private void batchResponse() {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", null);
    }
}
