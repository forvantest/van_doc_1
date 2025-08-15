/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.C021_RPT;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.ncl.util.sort.ExternalSortUtil;
import com.bot.ncl.util.sort.eum.SortBy;
import com.bot.ncl.util.sort.vo.KeyRange;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
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
@Component("C021_RPTLsnr")
@Scope("prototype")
public class C021_RPTLsnr extends BatchListenerCase<C021_RPT> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private ExternalSortUtil externalSortUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    private C021_RPT event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET = "UTF-8";
    private static final String CHARSET_BIG5 = "BIG5";
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;
    private final List<String> fileReportContents = new ArrayList<>(); // 表內容
    private String wkFdPutfDir;
    private String reportDir;
    private StringBuilder sb;
    private Map<String, String> labelMap;
    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    private String processDate;
    private int processDateInt = 0;
    private String tbsdy;
    private String wkYYMMDD;
    private int wkEnd;
    private int sSitdate;
    private String sSitTime;
    private int wkDate;
    private String wkTime;
    private int wkTempcnt;
    private int wkTopcnt = 0;
    private int wkTopcntP;
    private int wkTotcnt;
    private int wkTempdate;
    private int wkDateT;
    private String wkTemptime;
    private String wkTimeT;
    private String wkTop = "";

    @Override
    public void onApplicationEvent(C021_RPT event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C021_RPTLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(C021_RPT event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C021_RPTLsnr run()");
        this.event = event;
        _0000_Start();
    }

    private void _0000_Start() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C021_RPTLsnr _0000_Start ....");
        // 010000 0000-START.
        // 010100     OPEN INPUT   FD-BHDATE.
        // 010200     OPEN INPUT   FD-PUTF.
        // 010300     OPEN OUTPUT  REPORTFL.
        // 010400     CHANGE ATTRIBUTE DISPLAYONLYTOMCS OF MYSELF TO TRUE.
        // 010500     READ    FD-BHDATE AT END DISPLAY "READ FD-BHDATE ERROR"
        // 010600         STOP RUN.
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();

        // 作業日期(民國年yyyymmdd)
        processDate = labelMap.get("PROCESS_DATE");
        tbsdy = labelMap.get("PROCESS_DATE");
        processDateInt = parse.string2Integer(processDate);
        // 010700     MOVE    FD-BHDATE-TBSDY TO     WK-YYMMDD.
        wkYYMMDD = processDate;
        // 010800     PERFORM 0000-MAIN-RTN  THRU    0000-MAIN-EXIT.
        _0000_Main();

        // 010900 0000-END-RTN.
        // 011000     DISPLAY "SYM/CL/BH/RPT/C021  GENERATE BD/CL/BH/021  OK".
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "SYM/CL/BH/RPT/C021  GENERATE BD/CL/BH/021  OK");

        // 011100     CLOSE   REPORTFL    WITH SAVE.
        // 002700 FD  REPORTFL
        // 002800      VALUE  OF  TITLE  IS  "BD/CL/BH/021."
        // 002900                 USERBACKUPNAME IS TRUE
        // 003000                 SAVEPRINTFILE  IS TRUE                           01/03/02
        // 003100                 SECURITYTYPE   IS PUBLIC.
        reportDir =
                fileDir
                        + CONVF_RPT
                        + PATH_SEPARATOR
                        + processDateInt
                        + PATH_SEPARATOR
                        + "CL-BH-021";
        // 011200     CLOSE   FD-BHDATE   WITH SAVE.
        // 011300     CLOSE   FD-PUTF     WITH SAVE.

        try {
            textFile.writeFileContent(reportDir, fileReportContents, CHARSET_BIG5);
            upload(reportDir, "RPT", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        // 011400     STOP RUN.
    }

    private void _0000_Main() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C021_RPTLsnr _0000_Main ....");
        // 011600 0000-MAIN-RTN.
        // 011700     PERFORM 1000-WTITLE-RTN      THRU   1000-WTITLE-EXIT.
        _1000_Wtitle();
        // 011800     SORT    SORTFL
        // 011900             ASCENDING KEY        S-SITDATE S-SITTIME
        // 012000     MEMORY SIZE 7 MODULES
        // 012100     DISK SIZE 50 MODULES
        // 012200             INPUT  PROCEDURE     CS-SORTIN
        cs_Sortin();
        // 012300             OUTPUT PROCEDURE     CS-SORTOUT.
        cs_Sortout();
        // 012400 0000-MAIN-EXIT.
    }

    private void cs_Sortout() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C021_RPTLsnr cs_Sortout ....");
        // 013900 CS-SORTOUT-RTN.
        List<String> lines = textFile.readFileContent(wkFdPutfDir, CHARSET);
        int cnt = 0;
        int lCnt = lines.size();
        boolean isLast = false;
        for (String detail : lines) {
            cnt++;
            if (cnt == lCnt) {
                isLast = true;
            }
            sSitdate =
                    parse.isNumeric(detail.substring(93, 99))
                            ? parse.string2Integer(detail.substring(93, 99))
                            : 0;
            sSitTime = detail.substring(30, 36);
            // 014000     RETURN      SORTFL      AT END
            // 014100       MOVE        1                   TO     WK-END
            // 014200       PERFORM     MOVE-DATA-RTN       THRU   MOVE-DATA-EXIT
            // 014300       GO TO  CS-SORTOUT-EXIT.
            // 014400     MOVE      SPACES              TO     REPORT-LINE.
            // 014600     ADD         1000000             TO     S-SITDATE.
            // 014700     MOVE        S-SITDATE           TO     WK-DATE.
            // 014800     MOVE        S-SITTIME(1:2)      TO     WK-TIME.
            sSitdate = sSitdate + 1000000;
            wkDate = sSitdate;
            wkTime = sSitTime.substring(0, 2);
            // 015100     IF WK-TEMPDATE = 0
            if (wkTempdate == 0) {
                // 015200        MOVE        S-SITDATE        TO     WK-TEMPDATE
                // 015300        MOVE        S-SITTIME(1:2)   TO     WK-TEMPTIME
                // 015400        MOVE        1                TO     WK-TEMPCNT WK-TOTCNT
                wkTempdate = sSitdate;
                wkTemptime = sSitTime.substring(0, 2);
                wkTempcnt = 1;
                wkTotcnt = 1;
                // 015500        GO TO       CS-SORTOUT-RTN.
                if (isLast) {
                    sort_Last();
                }
                continue;
            }
            // 015700     IF WK-TEMPDATE NOT=  WK-DATE
            if (wkTempdate != wkDate) {
                // 015800        PERFORM     MOVE-DATA-RTN       THRU   MOVE-DATA-EXIT.
                move_Data();
            }
            // 015900     IF WK-TEMPDATE = WK-DATE   AND   WK-TEMPTIME NOT= WK-TIME
            if (wkTempdate == wkDate && !wkTemptime.equals(wkTime)) {
                // 016000        PERFORM     MOVE-DATA-RTN       THRU   MOVE-DATA-EXIT.
                move_Data();
            }
            // 016200     ADD         1       TO WK-TEMPCNT WK-TOTCNT.
            wkTempcnt = wkTempcnt + 1;
            wkTotcnt = wkTotcnt + 1;
            // 016300
            // 016400     GO TO       CS-SORTOUT-RTN.
            if (isLast) {
                // 014000     RETURN      SORTFL      AT END
                sort_Last();
            }
        }
        // 016500 CS-SORTOUT-EXIT.
    }

    private void move_Data() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C021_RPTLsnr moveData ....");
        // 018400 MOVE-DATA-RTN.
        // 018500     MOVE        WK-TEMPDATE         TO     WK-DATE.
        // 018600     MOVE        WK-TEMPTIME         TO     WK-TIME.
        // 018700     MOVE        WK-TEMPCNT          TO     WK-TOTCNTP.
        wkDate = wkTempdate;
        wkTime = wkTemptime;
        int wkTotcntP = wkTempcnt;
        // 018800* 挑最大值
        // 018900     IF   WK-TEMPCNT > WK-TOPCNT
        if (wkTempcnt > wkTopcnt) {
            // 019000          MOVE        WK-TEMPDATE    TO     WK-DATET
            // 019100          MOVE        WK-TEMPTIME    TO     WK-TIMET
            // 019200          MOVE        WK-TEMPCNT     TO     WK-TOPCNT
            // 019300          MOVE        WK-TOPCNT      TO     WK-TOPCNTP.
            wkDateT = wkTempdate;
            wkTimeT = wkTemptime;
            wkTopcnt = wkTempcnt;
            wkTopcntP = wkTopcnt;
        }
        // 019500     WRITE       REPORT-LINE         FROM   DETAIL-LINE.
        // 006900 01  DETAIL-LINE.
        // 007000  02 FILLER                             PIC X(11) VALUE SPACE.
        // 007100  02 WK-DATE                            PIC 9(07).
        // 007200  02 FILLER                             PIC X(13) VALUE SPACE.
        // 007300  02 WK-TIME                            PIC X(02).
        // 007400  02 FILLER                             PIC X(04) VALUE " 點 ".
        // 007500  02 FILLER                             PIC X(05) VALUE SPACE.
        // 007600  02 WK-TOTCNTP                         PIC Z,ZZZ,ZZ9 VALUE 0.
        // 007700  02 FILLER                             PIC X(07) VALUE SPACE.
        // 007900  02 FILLER                             PIC X(02) VALUE SPACE.
        // 008000  02 WK-TOP                             PIC X(08) VALUE SPACE.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 11));
        sb.append(formatUtil.pad9("" + wkDate, 7));
        sb.append(formatUtil.padX("", 13));
        sb.append(formatUtil.padX(wkTime, 2));
        sb.append(formatUtil.padX(" 點 ", 4));
        sb.append(formatUtil.padX("", 5));
        sb.append(reportUtil.customFormat("" + wkTotcntP, "Z,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 7));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(wkTop, 8));
        fileReportContents.add(sb.toString());

        // 019600     MOVE        0                   TO     WK-TEMPCNT.
        // 019700     MOVE        S-SITDATE           TO     WK-TEMPDATE.
        // 019800     MOVE        S-SITTIME(1:2)      TO     WK-TEMPTIME.
        wkTempcnt = 0;
        wkTempdate = sSitdate;
        wkTemptime = sSitTime.substring(0, 2);
        // 019900* 印尾筆
        // 020000     IF WK-END = 1
        if (wkEnd == 1) {
            // 020100        MOVE        SPACES              TO     REPORT-LINE
            // 020200        WRITE       REPORT-LINE         FROM   TITLE4-LINE
            // 006700 01  TITLE4-LINE.
            // 006800  02 FILLER                             PIC X(70) VALUE ALL "-".
            sb = new StringBuilder();
            sb.append(reportUtil.makeGate("-", 70));
            fileReportContents.add(sb.toString());

            // 020300        MOVE        SPACES              TO     REPORT-LINE
            // 020400        MOVE        WK-TOTCNT           TO     WK-SUMCNT
            // 020500        MOVE        WK-SUMCNT           TO     WK-SUMCNTP
            int wkSumcnt = wkTotcnt;
            // 020600        WRITE       REPORT-LINE         FROM   TOT-LINE
            // 008100 01  TOT-LINE.
            // 008200  02 FILLER                         PIC X(01) VALUE SPACE.
            // 008300  02 FILLER                         PIC X(10) VALUE " 總筆數： ".
            // 008400  02 WK-SUMCNTP                         PIC $$,$$$,$$9 VALUE 0.
            // 008500  02 FILLER                             PIC X(04) VALUE " 筆 ".
            sb = new StringBuilder();
            sb.append(formatUtil.padX("", 1));
            sb.append(formatUtil.padX(" 總筆數： ", 10));
            sb.append(
                    formatUtil.padX(
                            "$" + reportUtil.customFormat("" + wkSumcnt, "ZZ,ZZZ,ZZ9").trim(), 11));
            sb.append(formatUtil.padX(" 筆 ", 4));
            fileReportContents.add(sb.toString());

            // 020700        MOVE        SPACES              TO     REPORT-LINE
            // 020800        WRITE       REPORT-LINE         AFTER  1 LINE
            fileReportContents.add("");

            // 020900        WRITE       REPORT-LINE         FROM   TOP-LINE.
            // 008600 01  TOP-LINE.
            // 008700  02 FILLER                     PIC X(01) VALUE SPACE.
            // 008800  02 FILLER               PIC X(18) VALUE "*PS  最高峰落在： ".
            // 008900  02 FILLER                             PIC X(06) VALUE " 民國 ".
            // 009000  02 WK-DATET                           PIC 9(07).
            // 009100  02 FILLER                             PIC X(06) VALUE " 日， ".
            // 009200  02 WK-TIMET                           PIC X(02).
            // 009300  02 FILLER                             PIC X(04) VALUE " 點 ".
            // 009400* 02 FILLER                             PIC X(01) VALUE SPACE.
            // 009450  02 FILLER                             PIC X(06) VALUE " ，共 ".
            // 009500  02 WK-TOPCNTP                         PIC Z,ZZZ,ZZ9 VALUE 0.
            // 009600  02 FILLER                             PIC X(04) VALUE " 筆 ".
            sb = new StringBuilder();
            sb.append(formatUtil.padX("", 1));
            sb.append(formatUtil.padX("*PS  最高峰落在： ", 18));
            sb.append(formatUtil.padX(" 民國 ", 6));
            sb.append(formatUtil.pad9("" + wkDateT, 7));
            sb.append(formatUtil.padX(" 日， ", 6));
            sb.append(formatUtil.padX(wkTimeT, 2));
            sb.append(formatUtil.padX(" 點 ", 4));
            sb.append(formatUtil.padX("", 1));
            sb.append(formatUtil.padX(" ，共 ", 6));
            sb.append(reportUtil.customFormat("" + wkTopcntP, "Z,ZZZ,ZZ9"));
            sb.append(formatUtil.padX(" 筆 ", 4));
            fileReportContents.add(sb.toString());
        }
        // 021000 MOVE-DATA-EXIT.
    }

    private void sort_Last() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C021_RPTLsnr sort_Last ....");
        // 014100       MOVE        1                   TO     WK-END
        wkEnd = 1;
        // 014200       PERFORM     MOVE-DATA-RTN       THRU   MOVE-DATA-EXIT
        move_Data();
        // 014300       GO TO  CS-SORTOUT-EXIT.
    }

    private void cs_Sortin() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C021_RPTLsnr cs_Sortin ....");
        // 012800 CS-SORTIN-RTN.
        //
        // 012900     READ        FD-PUTF   AT END   GO TO  CS-SORTIN-EXIT.
        // 002500 FD  FD-PUTF
        // 002600  COPY "SYM/CL/BH/FD/PUTF.".
        String fdDir = fileDir + CONVF_DATA + PATH_SEPARATOR + processDateInt;
        wkFdPutfDir = fdDir + PATH_SEPARATOR + "PUTF"; // todo:待調整
        textFile.deleteFile(wkFdPutfDir);
        String sourceFtpPath =
                "NCL"
                        + File.separator
                        + tbsdy
                        + File.separator
                        + "2FSAP"
                        + File.separator
                        + "DATA"
                        + File.separator
                        + "PUTF"; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, fdDir);
        if (sourceFile != null) {
            wkFdPutfDir = getLocalPath(sourceFile);
        }

        // 013000     IF  PUTF-CTL2 = 1  AND PUTF-SITDATE > 0
        // 013100         MOVE        PUTF-SITDATE  TO     S-SITDATE
        // 013200         MOVE        PUTF-TIME     TO     S-SITTIME
        // 013300         RELEASE     SORT-REC.
        // 013400     GO TO CS-SORTIN-RTN.
        // 009900 01  PUTF-REC.
        // 010000     03  PUTF-CTL                         PIC 9(02). 0-2
        // 010020     03  PUTF-CTL-R       REDEFINES   PUTF-CTL.
        // 010040      05 PUTF-CTL1                        PIC 9(01).
        // 010060      05 PUTF-CTL2                        PIC 9(01).
        // 010100     03  PUTF-CODE                        PIC X(06). 2-8
        // 010200     03  PUTF-DATA.
        // 010300      05 PUTF-RCPTID                      PIC X(16). 8-24
        // 010400      05 PUTF-DATE                        PIC 9(06). 24-30
        // 010500      05 PUTF-TIME                        PIC 9(06). 30-36
        // 010600      05 PUTF-CLLBR                       PIC 9(03). 36-39
        // 010700      05 PUTF-LMTDATE                     PIC 9(06). 39-45
        // 010800      05 PUTF-OLDAMT                      PIC 9(08). 45-53
        // 010900      05 PUTF-USERDATA                    PIC X(40). 53-93
        // 010950      05 PUTF-SITDATE                     PIC 9(06). 93-99
        // 010960      05 PUTF-TXTYPE                      PIC X(01). 99-100
        // 010980      05 PUTF-AMT                         PIC 9(10). 100-110
        // 011000      05 PUTF-FILLER                      PIC X(10). 110-120
        // 011100     03  PUTF-DATA-R       REDEFINES  PUTF-DATA.
        // 011200      05 PUTF-BDATE                       PIC 9(06).
        // 011300      05 PUTF-EDATE                       PIC 9(06).
        // 011400      05 PUTF-TOTCNT                      PIC 9(06).
        // 011500      05 PUTF-TOTAMT                      PIC 9(13).
        // 011600      05 FILLER                           PIC X(81).
        // 011700     03  PUTF-DATA-NODATA  REDEFINES  PUTF-DATA.
        // 011800      05 PUTF-NODATA                      PIC X(16).
        // 011900      05 PUTF-FILLER1                     PIC X(96).
        File tmpFile = new File(wkFdPutfDir);
        List<KeyRange> keyRanges = new ArrayList<>();
        keyRanges.add(new KeyRange(93, 6, SortBy.ASC));
        keyRanges.add(new KeyRange(30, 6, SortBy.ASC));
        externalSortUtil.sortingFile(tmpFile, tmpFile, keyRanges, CHARSET);

        // 013500 CS-SORTIN-EXIT.
    }

    private void _1000_Wtitle() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C021_RPTLsnr _1000_Wtitle ....");
        // 016900 1000-WTITLE-RTN.
        // 017000     MOVE        SPACES              TO     REPORT-LINE.
        // 017100     WRITE       REPORT-LINE         AFTER  2 LINE.
        fileReportContents.add("");
        fileReportContents.add("");
        // 017200     WRITE       REPORT-LINE         FROM   TITLE1-LINE.
        // 004900 01  TITLE1-LINE.
        // 005000  02 FILLER                             PIC X(25) VALUE SPACE.
        // 005100  02 FILLER                             PIC X(56) VALUE
        // 005200       " 口　罩　實　名　制　代　收　統　計　表 ".
        // 005300  02 FILLER                             PIC X(08) VALUE SPACE.
        // 005400  02 FILLER                             PIC X(11) VALUE
        // 005500          "FORM : C021".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 25));
        sb.append(formatUtil.padX(" 口　罩　實　名　制　代　收　統　計　表 ", 56));
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX("FORM : C021", 11));
        fileReportContents.add(sb.toString());

        // 017300     MOVE        SPACES              TO     REPORT-LINE.
        // 017400     WRITE       REPORT-LINE         AFTER  2 LINE.
        fileReportContents.add("");
        fileReportContents.add("");

        // 017500     WRITE       REPORT-LINE         FROM   TITLE2-LINE.
        // 005600 01  TITLE2-LINE.
        // 005700  02 FILLER                             PIC X(10) VALUE SPACE.
        // 005800  02 FILLER                             PIC X(12) VALUE
        // 005900          " 製作日期 : ".
        // 006000  02 WK-YYMMDD                          PIC 9(07).
        // 006100  02 FILLER                             PIC X(80) VALUE SPACE.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 10));
        sb.append(formatUtil.padX(" 製作日期 : ", 12));
        sb.append(formatUtil.pad9(wkYYMMDD, 7));
        sb.append(formatUtil.padX("", 80));
        fileReportContents.add(sb.toString());

        // 017600     MOVE        SPACES              TO     REPORT-LINE.
        // 017700     WRITE       REPORT-LINE         AFTER  1 LINE.
        fileReportContents.add("");

        // 017800     WRITE       REPORT-LINE         FROM   TITLE3-LINE.
        // 006300 01  TITLE3-LINE.
        // 006400  02 FILLER                             PIC X(10) VALUE SPACE.
        // 006500  02 FILLER                             PIC X(49) VALUE
        // 006600          " 繳費日期           繳費時間         筆數小計 ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 10));
        sb.append(formatUtil.padX(" 繳費日期           繳費時間         筆數小計 ", 49));
        fileReportContents.add(sb.toString());

        // 017900     MOVE        SPACES              TO     REPORT-LINE.
        // 018000     WRITE       REPORT-LINE         FROM   TITLE4-LINE.
        // 006700 01  TITLE4-LINE.
        // 006800  02 FILLER                             PIC X(70) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 70));
        fileReportContents.add(sb.toString());

        // 018100 1000-WTITLE-EXIT.
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
        //        event.setPeripheryRequest();
    }
}
