/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.C012;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.dto.entities.ClmrbyIntervalPbrnoBus;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FileC012;
import com.bot.ncl.util.fileVo.FileSortC012;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.ncl.util.sort.ExternalSortUtil;
import com.bot.ncl.util.sort.eum.SortBy;
import com.bot.ncl.util.sort.vo.KeyRange;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.dto.vo.BctlTable;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.date.DateUtil;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.text.format.Text2VoFormatter;
import com.bot.txcontrol.util.text.format.Vo2TextFormatter;
import com.bot.txcontrol.util.transmit.FsapSyncSftpService;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("C012Lsnr")
@Scope("prototype")
public class C012Lsnr extends BatchListenerCase<C012> {
    @Autowired private ClmrService clmrlService;
    @Autowired private FormatUtil formatUtil;
    @Autowired private TextFileUtil textFile;
    @Autowired private ExternalSortUtil externalSortUtil;
    @Autowired private FileC012 fileC012;
    @Autowired private Parse parse;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private Vo2TextFormatter vo2TextFormatter;
    @Autowired private FileSortC012 fileSortC012;
    @Autowired private ReportUtil reportUtil;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    @Autowired private DateUtil dateUtil;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> labelMap;
    private Map<String, String> textMap;

    private static final String CHARSET = "UTF-8";
    private static final String CHARSET5 = "Big5";
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;
    private C012 event;
    private List<String> fileOutPathContents = new ArrayList<>(); // 檔案內容
    private List<String> fileSortInContents = new ArrayList<>(); // 檔案內容SORTIN
    private String processDate; // 作業日期(民國年yyyymmdd)
    private String tbsdy;
    private String wkYYYYMMDD;
    private int detailCnt = 0;
    private final int LAST_DETAIL = 55;
    private String yyyy;
    private String mm;
    private String dd;
    private StringBuilder sb = new StringBuilder();
    private final int pageCnts = 100000;
    private int pageIndex = 0;
    private String PAGE_SEPARATOR = "\u000C";
    private String wkRptDate;
    private String wkRptTime;
    private int pageCounter;
    private int lineNumber;

    private BctlTable bctl;
    private String fileOutPath;
    private String fileSortInPath;
    private String fileC012Path;
    private int wkPbrno;
    private int wkNobctl = 0;

    private List<String> putfCtlFileContents;
    private int sdPbrno;
    private String sdCode;
    private String sdPbrnoNm;
    private String sdCname;
    private String sdEntpno;
    private String sdHentpno;
    private String sdStopx;
    private String sdDate;
    private String wkStopx;
    private int fdC012Pbrno;
    private String fdC012Code;
    private String fdC012Cname;
    private String fdC012Entpno;
    private String fdC012Hentpno;
    private String fdC012Date;
    private String fdC012Pbrnonm;
    private Map<Integer, Integer> wkPbrnoFg = new HashMap<Integer, Integer>();
    ;

    @Override
    public void onApplicationEvent(C012 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C012Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(C012 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C012Lsnr run()");

        init(event);

        // 019500     SORT      SORTFL  ON  ASCENDING KEY SD-PBRNO,
        // 019600                                         SD-CODE
        // 019700     MEMORY  SIZE   6 MODULES
        // 019800     DISK    SIZE  50 MODULES
        // 019900               INPUT  PROCEDURE SORT-IN
        // 020000               OUTPUT PROCEDURE SORT-OUT.
        sortin();
        sortinfd();
        sortFile();
        sortout();

        textFile.deleteFile(fileSortInPath);
        textFile.writeFileContent(fileOutPath, fileOutPathContents, CHARSET5);
        upload(fileOutPath, "RPT", "");

        batchResponse();
    }

    private void init(C012 event) {
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();

        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        wkYYYYMMDD = formatUtil.pad9(processDate, 8);
        // entdy 標題中華民國 年 月 日
        yyyy = wkYYYYMMDD.substring(0, 4);
        mm = wkYYYYMMDD.substring(4, 6);
        dd = wkYYYYMMDD.substring(6, 8);
        // 現在時間
        wkRptDate = dateUtil.getNowStringRoc();
        wkRptTime = dateUtil.getNowStringTime(false).substring(0, 4);

        fileOutPath =
                fileDir + CONVF_RPT + PATH_SEPARATOR + processDate + PATH_SEPARATOR + "CL-BH-C012";
        fileSortInPath =
                fileDir + CONVF_DATA + PATH_SEPARATOR + processDate + PATH_SEPARATOR + "SORTINC012";
        //        fileC012Path = fileDir + "CRE" + File.separator + "C012";
        // 下載來源檔
        String downLoadDir = fileDir + "DATA" + File.separator + processDate;
        String sourceFtpPath =
                "NCL"
                        + File.separator
                        + tbsdy
                        + File.separator
                        + "C012_REST_"
                        + processDate
                        + ".TXT"; // 來源檔在FTP的位置
        File c012RestFile = downloadFromSftp(sourceFtpPath, downLoadDir);
        fileC012Path = getLocalPath(c012RestFile);

        textFile.deleteFile(fileOutPath);
        textFile.deleteFile(fileSortInPath);
    }

    private void sortin() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C012Lsnr sortin");
        // 021300 SORT-IN     SECTION.
        // 021400*
        // 021500 SORT-IN-RTN.
        // 021600     MOVE      0                  TO    WK-PBRNO.
        wkPbrno = 0;

        // 021700     SET       DB-CLMR-IDX2       OF    DB-CLMR-DDS TO BEGINNING.
        // 021800
        // 021900** 先撈有建分行檔之所有主辦行，寫一筆空檔
        // 022000 SORT-IN-CLMR.
        // 022100     FIND NEXT DB-CLMR-IDX2       OF    DB-CLMR-DDS
        // 022200       ON EXCEPTION
        // 022300          IF   DMSTATUS(NOTFOUND)
        // 022400               MOVE    0          TO    WK-PBRNO
        // 022500               GO TO   SORT-IN-FD
        // 022600          ELSE
        // 022700               DISPLAY "FIND DB-CLMR-IDX2  FAIL!!!"
        // 022800               CALL SYSTEM DMTERMINATE.
        List<ClmrBus> lclmr = clmrlService.findAll(pageIndex, pageCnts);
        if (Objects.isNull(lclmr) || lclmr.isEmpty()) {
            return;
        }
        for (ClmrBus tClmr : lclmr) {
            int dbClmrPbrno = tClmr.getPbrno();

            sdPbrno = 0;
            sdCode = "";
            sdCname = "";
            sdEntpno = "";
            sdHentpno = "";
            sdStopx = "";
            sdDate = "";
            sdPbrnoNm = "";
            // 022900
            // 023000     IF        DB-CLMR-PBRNO      =     0
            // I 023050     OR        DB-CLMR-PBRNO      >=    900
            // 023100       GO  TO  SORT-IN-CLMR.
            if (dbClmrPbrno == 0 || dbClmrPbrno >= 900) {
                continue;
            }
            // 023200
            // 023300     IF        DB-CLMR-PBRNO      =     WK-PBRNO
            // 023400       GO  TO  SORT-IN-CLMR.
            if (wkPbrno == dbClmrPbrno) {
                continue;
            }
            // 023500*
            // 023510** 若主辦行未建分行檔，則不出表，例如：預建資料
            // 023520     MOVE      DB-CLMR-PBRNO      TO    WK-PBRNO.
            // 023530     PERFORM   0100-BCTL-RTN      THRU  0100-BCTL-EXIT.
            wkPbrno = dbClmrPbrno;
            _0100_bctl();
            // 023540     IF        WK-NOBCTL          =     1
            // 023550       GO  TO  SORT-IN-CLMR.
            if (wkNobctl == 1) {
                continue;
            }
            // 023560*
            // 023700     MOVE      1                  TO    WK-PBRNO-FG(WK-PBRNO).
            if (wkPbrnoFg.containsKey(wkPbrno)) {
                continue;
            } else {
                wkPbrnoFg.put(wkPbrno, 1);
            }
            // 023800*
            // 023900     MOVE      LOW-VALUE          TO    SD-REC    .
            // 024000     MOVE      DB-CLMR-PBRNO      TO    SD-PBRNO  .
            // 024100     MOVE      SPACES             TO    SD-CODE   .
            // 024300     MOVE      DB-BCTL-CHNAM      TO    SD-PBRNONM.
            sdPbrno = tClmr.getPbrno();
            sdCode = "";
            sdPbrnoNm = bctl.getChnam();

            // 024400
            // 024500     RELEASE   SD-REC.
            fileSortInContents.add(writeSdRec());
            // 024600*
            // 024700     GO  TO    SORT-IN-CLMR.
        }

        textFile.writeFileContent(fileSortInPath, fileSortInContents, CHARSET5);
        lclmr.clear();
        fileSortInContents.clear();
        // 027400*
        // 027500 SORT-IN-EXIT.
    }

    private void sortinfd() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C012Lsnr sortinfd");
        // 024900** 讀取本日異動檔
        // 025000 SORT-IN-FD.
        // 025100     READ  FD-C012    AT  END
        // 025200       GO  TO  SORT-IN-EXIT.
        if (!textFile.exists(fileC012Path)) {
            return;
        }
        List<String> lines = textFile.readFileContent(fileC012Path, CHARSET5);
        for (String detail : lines) {
            text2VoFormatter.format(detail, fileSortC012);
            fdC012Pbrno =
                    parse.isNumeric(fileSortC012.getPbrno())
                            ? parse.string2Integer(fileSortC012.getPbrno())
                            : 0;
            fdC012Code = fileSortC012.getCode();
            fdC012Cname = fileSortC012.getCname();
            fdC012Entpno = fileSortC012.getEntpno();
            fdC012Hentpno = fileSortC012.getHentpno();
            fdC012Date = fileSortC012.getDate();
            fdC012Pbrnonm = fileSortC012.getPbrnonm();
            // 025300*
            // 025400     IF        FD-C012-PBRNO      =     0
            // 025500       GO  TO  SORT-IN-FD.
            if (fdC012Pbrno == 0) {
                continue;
            }
            // 025600*
            // 025700     MOVE      2            TO    WK-PBRNO-FG(FD-C012-PBRNO).
            wkPbrnoFg.put(fdC012Pbrno, 2);

            // 025900     MOVE      LOW-VALUE          TO    SD-REC    .
            // 026000     MOVE      FD-C012-PBRNO      TO    SD-PBRNO  .
            // 026100     MOVE      FD-C012-CODE       TO    SD-CODE   .
            // 026200     MOVE      FD-C012-CNAME      TO    SD-CNAME  .
            // 026300     MOVE      FD-C012-ENTPNO     TO    SD-ENTPNO .
            // 026400     MOVE      FD-C012-HENTPNO    TO    SD-HENTPNO.
            sdPbrno = fdC012Pbrno;
            sdCode = fdC012Code;
            sdCname = fdC012Cname;
            sdEntpno = fdC012Entpno;
            sdHentpno = fdC012Hentpno;
            // 026500** 讀取目前狀態
            // 026600     PERFORM   0200-CLMR-RTN      THRU  0200-CLMR-EXIT.
            _0200_clmr();
            // 026700     MOVE      WK-STOPX           TO    SD-STOPX  .
            // 026800     MOVE      FD-C012-DATE       TO    SD-DATE   .
            // 026900     MOVE      FD-C012-PBRNONM    TO    SD-PBRNONM.
            sdStopx = wkStopx;
            sdDate = fdC012Date;
            sdPbrnoNm = fdC012Pbrnonm;
            // 027000*
            // 027100     RELEASE   SD-REC.
            fileSortInContents.add(writeSdRec());
            // 027200*
            // 027300     GO  TO    SORT-IN-FD.
        }
        textFile.writeFileContent(fileSortInPath, fileSortInContents, CHARSET5);
        lines.clear();
        fileSortInContents.clear();
    }

    private void sortout() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C012Lsnr sortout");

        // 028100 SORT-OUT  SECTION.
        // 028200*
        // 028300 SORT-OUT-RTN.
        // 028400*
        // 028500     RETURN    SORTFL  AT  END
        // 028600       GO  TO  SORT-OUT-EXIT.
        if (!textFile.exists(fileSortInPath)) {
            return;
        }
        List<String> lines = textFile.readFileContent(fileSortInPath, CHARSET5);
        wkPbrno = 0;
        Boolean firstPg = true;
        for (String detail : lines) {
            // 028700*
            // 028800* 本日無異動資料之主辦行，印 NODATA

            sdPbrno =
                    parse.isNumeric(cutStringByByteLength(detail, 0, 3))
                            ? parse.string2Integer(cutStringByByteLength(detail, 0, 3))
                            : 0;
            sdPbrnoNm = cutStringByByteLength(detail, 117, 137);
            // 008700    CONTROLS ARE  FINAL, SD-PBRNO
            // SDPBRNO控制分組
            if (wkPbrno != sdPbrno) {
                if (!firstPg) {
                    nextPage();
                }
                wkPbrno = sdPbrno;
                pageCounter = 1;
                pageHead();
                firstPg = false;
                detailCnt = 0;
            }
            // 換頁
            if (lineNumber > LAST_DETAIL) {
                nextPage();
                pageCounter++;
                pageHead();
                detailCnt = 0;
            }
            sdCode = cutStringByByteLength(detail, 3, 9).trim();
            sdCname = cutStringByByteLength(detail, 9, 49);
            sdEntpno = cutStringByByteLength(detail, 49, 59);
            sdHentpno = cutStringByByteLength(detail, 59, 67);
            sdStopx = cutStringByByteLength(detail, 67, 109);
            sdDate = cutStringByByteLength(detail, 109, 117);

            // 028900     IF        WK-PBRNO-FG(SD-PBRNO)   =   1
            // 029000       GENERATE  DT-NODATA
            // 029100       GO   TO   SORT-OUT-RTN
            // 029200     END-IF.
            int wkPbrnoFgI = wkPbrnoFg.get(sdPbrno);
            if (wkPbrnoFgI == 1) {
                // 寫nodata
                // 013700 01 DT-NODATA TYPE IS DE.
                fileOutPathContents.add(dtNodata());
                lineNumber++;
                continue;
            }
            // 029300
            // 029400     IF        SD-CODE   =   SPACES
            // 029500       GO  TO  SORT-OUT-RTN.
            if (sdCode == null || sdCode.isEmpty()) {
                continue;
            }
            // 029600
            // 029700     GENERATE  DT-1.
            fileOutPathContents.add(dt1());
            lineNumber++;

            // 029800*
            // 029900     GO   TO   SORT-OUT-RTN.
        }
        nextPage();

        // 030000*
        // 030100 SORT-OUT-EXIT.
    }

    private String dtNodata() {
        // 013800    03 LINE PLUS 1.
        // 013900       05 COLUMN  15 PIC X(30)     VALUE
        // 014000                     "******* 本日無資料 *******".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 14));
        sb.append(formatUtil.padX("******* 本日無資料 *******", 30));
        return sb.toString();
    }

    private void nextPage() {
        pageFooting();
        // 補空行
        if (lineNumber < 62) {
            for (int i = lineNumber; i < 62; i++) {
                lineNumber++;
                fileOutPathContents.add("");
            }
        }
    }

    private void pageFooting() {
        lineNumber++;
        fileOutPathContents.add(reportUtil.makeGate("-", 150));
    }

    private void pageHead() {
        // 009400 01 PAGE-HEAD TYPE IS PH.
        fileOutPathContents.add(PAGE_SEPARATOR);
        // 009500    03 LINE 1.
        // 009600       05 COLUMN  34 PIC X(42)
        // 009700              VALUE " 主辦分行之電子化收款客戶名單（已解約） ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 33));
        sb.append(formatUtil.padX(" 主辦分行之電子化收款客戶名單（已解約） ", 42));
        fileOutPathContents.add(sb.toString());
        fileOutPathContents.add("");
        // 009800    03 LINE 3.
        // 009900       05 COLUMN   1 PIC X(12)     VALUE  " 分行別　： ".
        // 010000       05 COLUMN  13 PIC 9(03)     SOURCE SD-PBRNO.
        // 010100       05 COLUMN  16 PIC X(20)     SOURCE SD-PBRNONM.
        // 010200       05 COLUMN  62 PIC X(10)     VALUE  " 中華民國 ".
        // 010300       05 COLUMN  72 PIC Z999      SOURCE FD-BHDATE-TBSYY.
        // 010400       05 COLUMN  76 PIC X(04)     VALUE  " 年 ".
        // 010500       05 COLUMN  80 PIC Z9        SOURCE FD-BHDATE-TBSMM.
        // 010600       05 COLUMN  82 PIC X(04)     VALUE  " 月 ".
        // 010700       05 COLUMN  86 PIC Z9        SOURCE FD-BHDATE-TBSDD.
        // 010800       05 COLUMN  88 PIC X(04)     VALUE  " 日 ".
        // 010900       05 COLUMN 131 PIC X(19)     VALUE  " 報表代號： CL-C012".
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 分行別　： ", 12));
        sb.append(formatUtil.pad9("" + sdPbrno, 3));
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.padX(sdPbrnoNm, 20));
        sb.append(formatUtil.padX("", 26));
        sb.append(formatUtil.padX(" 中華民國 ", 10));
        sb.append(reportUtil.customFormat(yyyy, "Z999"));
        sb.append(formatUtil.padX(" 年 ", 4));
        sb.append(reportUtil.customFormat(mm, "Z9"));
        sb.append(formatUtil.padX(" 月 ", 4));
        sb.append(reportUtil.customFormat(dd, "Z9"));
        sb.append(formatUtil.padX(" 日 ", 4));
        sb.append(formatUtil.padX("", 39));
        sb.append(formatUtil.padX("報表代號： CL-C012", 19));
        fileOutPathContents.add(sb.toString());
        // 011000    03 LINE 4.
        // 011100       05 COLUMN   1 PIC X(12)     VALUE  " 列印時間： ".
        // 011200       05 COLUMN  13 PIC Z99/99/99 SOURCE WK-RPT-DATE.
        // 011300       05 COLUMN  23 PIC X(05)     SOURCE WK-RPT-HHMM.
        // 011400       05 COLUMN 131 PIC X(12)     VALUE  " 頁　　次： ".
        // 011500       05 COLUMN 143 PIC 99        SOURCE PAGE-COUNTER.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 列印時間： ", 12));
        sb.append(reportUtil.customFormat(wkRptDate, "Z99/99/99"));
        sb.append(formatUtil.padX("", 1));
        sb.append(reportUtil.customFormat(wkRptTime, "99:99"));
        sb.append(formatUtil.padX("", 104));
        sb.append(formatUtil.padX(" 頁　　次： ", 12));
        sb.append(reportUtil.customFormat("" + pageCounter, "99"));
        fileOutPathContents.add(sb.toString());
        // 011600*
        // 011700    03 LINE PLUS 2.
        // 011800       05 COLUMN   1 PIC X(10)     VALUE  " 代收類別 ".
        // 011900       05 COLUMN  11 PIC X(10)     VALUE  " 單位名稱 ".
        // 012000       05 COLUMN  51 PIC X(14)     VALUE  " 營利事業編號 ".
        // 012100       05 COLUMN  65 PIC X(12)     VALUE  " 總公司統編 ".
        // 012200       05 COLUMN  77 PIC X(10)     VALUE  " 代收狀態 ".
        // 012300       05 COLUMN 122 PIC X(06)     VALUE  " 備註 ".
        // 012400       05 COLUMN 139 PIC X(08)     VALUE  " 建檔日 ".

        fileOutPathContents.add("");
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 代收類別 ", 10));
        sb.append(formatUtil.padX(" 單位名稱 ", 10));
        sb.append(formatUtil.padX("", 30));
        sb.append(formatUtil.padX(" 營利事業編號 ", 14));
        sb.append(formatUtil.padX(" 總公司統編 ", 12));
        sb.append(formatUtil.padX(" 代收狀態 ", 10));
        sb.append(formatUtil.padX("", 35));
        sb.append(formatUtil.padX(" 備註 ", 6));
        sb.append(formatUtil.padX("", 11));
        sb.append(formatUtil.padX(" 建檔日 ", 8));
        fileOutPathContents.add(sb.toString());
        // 012500    03 LINE PLUS 1.
        // 012600       05 COLUMN   1 PIC X(150)    VALUE ALL "	".
        fileOutPathContents.add(reportUtil.makeGate("=", 150));

        lineNumber = 8;
    }

    private String dt1() {
        // 012800 01 DT-1 TYPE IS DE.
        // 012900    03 LINE PLUS 1.
        // 013000       05 COLUMN   3 PIC X(06)     SOURCE SD-CODE   .
        // 013100       05 COLUMN  11 PIC X(40)     SOURCE SD-CNAME  .
        // 013200       05 COLUMN  52 PIC X(10)     SOURCE SD-ENTPNO .
        // 013300       05 COLUMN  66 PIC 9(08)     SOURCE SD-HENTPNO.
        // 013400       05 COLUMN  77 PIC X(42)     SOURCE SD-STOPX  .
        // 013500       05 COLUMN 142 PIC 9(08)     SOURCE SD-DATE   .

        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(sdCode, 6));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(sdCname, 40));
        sb.append(formatUtil.padX("", 1));
        sb.append(formatUtil.padX(sdEntpno, 10));
        sb.append(formatUtil.padX("", 3));
        sb.append(formatUtil.pad9(sdHentpno, 8));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(sdStopx, 42));
        sb.append(formatUtil.padX("", 22));
        sb.append(formatUtil.pad9(sdDate, 8));
        detailCnt++;
        return sb.toString();
    }

    private String writeSdRec() {
        // 003600 01  SD-REC.
        // 003700     03 SD-PBRNO             PIC 9(03).
        // 003800     03 SD-CODE              PIC X(06).
        // 003900     03 SD-CNAME             PIC X(40).
        // 004000     03 SD-ENTPNO            PIC X(10).
        // 004100     03 SD-HENTPNO           PIC 9(08).
        // 004200     03 SD-STOPX             PIC X(42).
        // 004300     03 SD-DATE              PIC 9(08).
        // 004400     03 SD-PBRNONM           PIC X(20).
        sb = new StringBuilder();
        sb.append(formatUtil.pad9("" + sdPbrno, 3));
        sb.append(formatUtil.padX(sdCode, 6));
        sb.append(formatUtil.padX(sdCname, 40));
        sb.append(formatUtil.padX(sdEntpno, 10));
        sb.append(formatUtil.pad9(sdHentpno, 8));
        sb.append(formatUtil.padX(sdStopx, 42));
        sb.append(formatUtil.pad9(sdDate, 8));
        sb.append(formatUtil.padX(sdPbrnoNm, 20));
        return sb.toString();
    }

    private void _0100_bctl() {
        // 031100 0100-BCTL-RTN.
        // 031150     MOVE      0                 TO      WK-NOBCTL.
        wkNobctl = 0;
        // 031200     FIND      DB-BCTL-ACCESS    OF      DB-BCTL-DDS
        // 031300       AT      DB-BCTL-BRNO      =       WK-PBRNO
        // 031400       ON      EXCEPTION
        // 031500         MOVE  1                 TO      WK-NOBCTL
        // I 031510         GO TO 0100-BCTL-EXIT.
        // 依主辦行 FIND DB-BCTL-ACCESS營業單位控制檔
        // 若有誤，搬1到WL-NOBCTL
        bctl = this.event.getAggregateBuffer().getMgGlobal().getBctlTable(wkPbrno);
        if (Objects.isNull(bctl)) {
            wkNobctl = 1;
            return;
        }
        // I 031530* 不是營業中的分行
        // I 031540     IF      ( DB-BCTL-TAXNO     =       SPACES OR LOW-VALUE )
        // I 031550     OR      ( DB-BCTL-BRNO      NOT =   DB-BCTL-SBRNO )
        // I 031560         MOVE  1                 TO      WK-NOBCTL
        // I 031570         GO TO 0100-BCTL-EXIT.
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "C012Lsnr bctl.taxno={}", bctl.getTaxno());

        if (bctl.getTaxno() == null) {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C012Lsnr bctl.taxno is Null");
        }
        String taxno = bctl.getTaxno();
        if (taxno == null) {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C012Lsnr taxno==null");
        }
        if (!parse.isNumeric(taxno)) {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C012Lsnr taxno not is Numeric");
        }
        if (taxno.isEmpty()) {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C012Lsnr taxno.isEmpty()");
        }
        if (taxno.trim().isEmpty()) {
            ApLogHelper.info(
                    log, false, LogType.NORMAL.getCode(), "C012Lsnr taxno.trim().isEmpty()");
        }
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "C012Lsnr bctl.Brno= [{}] ,Sbrno = [{}]",
                bctl.getBrno(),
                bctl.getSbrno());
        if (bctl.getTaxno() == null
                || !parse.isNumeric(taxno)
                || bctl.getTaxno().isEmpty()
                || !bctl.getBrno().equals(bctl.getSbrno())) {
            wkNobctl = 1;
            return;
        }
        // 031600
        // 031700 0100-BCTL-EXIT.
    }

    private void _0200_clmr() {
        // 032000 0200-CLMR-RTN.
        //
        //// 依代收類別 FIND DB-CLMR-IDX1事業單位交易設定檔
        //// 若有誤，
        //// 　A.若NOTFOUND搬SPACES到WK-STOPX，結束本段程式
        ////  B.其餘，DISPLAY錯誤訊息，異常結束程式
        //
        // 032100     FIND      DB-CLMR-IDX1      OF      DB-CLMR-DDS
        // 032200       AT      DB-CLMR-CODE      =       SD-CODE
        // 032300       ON EXCEPTION
        // 032400          IF   DMSTATUS(NOTFOUND)
        // 032500               MOVE  SPACES      TO      WK-STOPX
        // 032600               GO TO 0200-CLMR-EXIT
        // 032700          ELSE
        // 032800               DISPLAY "FIND DB-CLMR-IDX1  FAIL!!!"
        // 032900               CALL SYSTEM DMTERMINATE.
        ClmrBus tClmr = clmrlService.findById(sdCode);
        if (Objects.isNull(tClmr)) {
            wkStopx = "";
            return;
        }
        // 033000*
        // 033100     IF      DB-CLMR-STOP = 0
        // 033200       MOVE  " ０－代收中 "              TO     WK-STOPX
        // 033300     ELSE IF DB-CLMR-STOP = 1
        // 033400       MOVE  " １－暫停代收 "            TO     WK-STOPX
        // 033500     ELSE IF DB-CLMR-STOP = 2
        // 033600       MOVE  " ２－解約 "                TO     WK-STOPX
        // 033700     ELSE IF DB-CLMR-STOP = 3
        // 033800       MOVE  " ３－僅主辦行可收 "        TO     WK-STOPX
        // 033900     ELSE IF DB-CLMR-STOP = 4
        // 034000       MOVE  " ４－僅自動化機器可收（主辦行可臨櫃代收） "
        // 034100                                         TO     WK-STOPX
        // 034200     ELSE IF DB-CLMR-STOP = 5
        // 034300       MOVE  " ５－僅自動化機器或臨櫃轉帳（不能收現） "
        // 034400                                         TO     WK-STOPX
        // 034500     ELSE IF DB-CLMR-STOP = 6
        // 034600       MOVE  " ６－僅能臨櫃繳款 "        TO     WK-STOPX
        // 034700     ELSE IF DB-CLMR-STOP = 7
        // 034800       MOVE  " ７－僅臨櫃與匯款 "        TO     WK-STOPX
        // 034900     ELSE IF DB-CLMR-STOP = 8
        // 035000       MOVE  " ８－僅自動化機器可收 "    TO     WK-STOPX
        // 035100     ELSE
        // 035200       MOVE  SPACES                      TO     WK-STOPX
        // 035300     END-IF.
        wkStopx = getStopx(tClmr.getStop());
        // 035400*
        // 035500 0200-CLMR-EXIT.
    }

    private String getStopx(int clmrStop) {
        //        //033100     IF      DB-CLMR-STOP = 0
        //        //033200       MOVE  " ０－代收中 "              TO     WK-STOPX
        //        //033300     ELSE IF DB-CLMR-STOP = 1
        //        //033400       MOVE  " １－暫停代收 "            TO     WK-STOPX
        //        //033500     ELSE IF DB-CLMR-STOP = 2
        //        //033600       MOVE  " ２－解約 "                TO     WK-STOPX
        //        //033700     ELSE IF DB-CLMR-STOP = 3
        //        //033800       MOVE  " ３－僅主辦行可收 "        TO     WK-STOPX
        //        //033900     ELSE IF DB-CLMR-STOP = 4
        //        //034000       MOVE  " ４－僅自動化機器可收（主辦行可臨櫃代收） "
        //        //034100                                         TO     WK-STOPX
        //        //034200     ELSE IF DB-CLMR-STOP = 5
        //        //034300       MOVE  " ５－僅自動化機器或臨櫃轉帳（不能收現） "
        //        //034400                                         TO     WK-STOPX
        //        //034500     ELSE IF DB-CLMR-STOP = 6
        //        //034600       MOVE  " ６－僅能臨櫃繳款 "        TO     WK-STOPX
        //        //034700     ELSE IF DB-CLMR-STOP = 7
        //        //034800       MOVE  " ７－僅臨櫃與匯款 "        TO     WK-STOPX
        //        //034900     ELSE IF DB-CLMR-STOP = 8
        //        //035000       MOVE  " ８－僅自動化機器可收 "    TO     WK-STOPX
        //        //035100     ELSE
        //        //035200       MOVE  SPACES                      TO     WK-STOPX
        //        //035300     END-IF.
        String stopx = "";
        if (clmrStop == 0) {
            stopx = "０－代收中";
        } else if (clmrStop == 1) {
            stopx = "１－暫停代收";
        } else if (clmrStop == 2) {
            stopx = "２－已解約";
        } else if (clmrStop == 3) {
            stopx = "３－僅主辦行可收";
        } else if (clmrStop == 4) {
            stopx = "４－僅自動化機器可收（主辦行可臨櫃代收）";
        } else if (clmrStop == 5) {
            stopx = "５－僅自動化機器或臨櫃轉帳（不能收現）";
        } else if (clmrStop == 6) {
            stopx = "６－僅能臨櫃繳款";
        } else if (clmrStop == 7) {
            stopx = "７－僅臨櫃與匯款";
        } else if (clmrStop == 8) {
            stopx = "８－僅自動化機器可收";
        } else {
            stopx = "";
        }
        return stopx;
    }

    private void queryCldtl() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C012Lsnr queryCldtl");

        // C012檔案讀出進新檔案
        //        infile();
        // 排序資料
        sortfile();
        // 與clmr結合 有相符合出資料 沒有的出nodata
        outputclmr();
    }

    private void infile() {
        //        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "infile()");
        //        // 循序讀取FD-C012，檔尾，結束本段程式
        //        List<String> sortFileContents = new ArrayList<>();
        //        ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境
        //        for (String putfCtlFileContent : putfCtlFileContents) {
        //            text2VoFormatter.format(putfCtlFileContent, fileC012);
        //            fileSortC012 = new FileSortC012();
        //
        //            fileSortC012.setCode(fileC012.getCode());
        //            fileSortC012.setPbrno(fileC012.getPbrno());
        //            fileSortC012.setCname(fileC012.getCname());
        //            fileSortC012.setEntpno(fileC012.getEntpno());
        //            fileSortC012.setHentpno(fileC012.getHentpno());
        //            fileSortC012.setStop(fileC012.getStop());
        //            fileSortC012.setDate(fileC012.getDate());
        //            fileSortC012.setActno(fileC012.getActno());
        //            fileSortC012.setPbrnonm(fileC012.getPbrnonm());
        //            fileSortC012.setFiller(fileC012.getFiller());
        //
        //            sortFileContents.add(vo2TextFormatter.formatRS(fileSortC012, false));
        //        }
        //        textFile.writeFileContent(fileSortC012Path, sortFileContents, CHARSET);
    }

    private void sortFile() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "sortFile()");
        if (!textFile.exists(fileSortInPath)) {
            ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "sortFile NO DATAFILE");
            return;
        }
        File sortFile = new File(fileSortInPath);
        List<KeyRange> keyRanges = new ArrayList<>();
        keyRanges.add(new KeyRange(1, 3, SortBy.ASC));
        keyRanges.add(new KeyRange(4, 6, SortBy.ASC));
        externalSortUtil.sortingFile(sortFile, sortFile, keyRanges, CHARSET5);
    }

    private void sortfile() {
        File tmpFile = new File(fileC012Path);
        List<KeyRange> keyRanges = new ArrayList<>();
        keyRanges.add(new KeyRange(7, 3, SortBy.ASC));
        keyRanges.add(new KeyRange(1, 6, SortBy.ASC));
        keyRanges.add(new KeyRange(10, 40, SortBy.ASC));
        keyRanges.add(new KeyRange(69, 10, SortBy.ASC));
        keyRanges.add(new KeyRange(79, 8, SortBy.ASC));
        keyRanges.add(new KeyRange(87, 1, SortBy.ASC));
        keyRanges.add(new KeyRange(88, 8, SortBy.ASC));
        keyRanges.add(new KeyRange(108, 20, SortBy.ASC));
        externalSortUtil.sortingFile(tmpFile, tmpFile, keyRanges, CHARSET5);
    }

    private void outputclmr() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "outputclmr()");
        // 日期
        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        DateFormat sdftime = new SimpleDateFormat("HH:mm");
        String ntime = sdftime.format(date);
        String today = sdf.format(new Date());
        String nowtoday = "" + (parse.string2Integer(today) - 19110000);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "usedate  = {}", nowtoday);

        String nowdate = reportUtil.customFormat(nowtoday, "Z99/99/99") + " " + ntime;
        // 頁數
        int page = 0;
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "batchDate()" + parse.string2Integer(processDate));

        // entdy 標題中華民國 年 月 日
        int yyyy = parse.string2Integer(processDate.substring(0, 3));
        int mm = parse.string2Integer(processDate.substring(3, 5));
        int dd = parse.string2Integer(processDate.substring(5, 7));

        // 先讀sort過的檔案
        List<String> lines = textFile.readFileContent(fileC012Path, CHARSET5);
        // 拿取pbrno跟clmr的資料有符合拿出sort檔案裡面的資料，無符合就出nodata
        List<ClmrbyIntervalPbrnoBus> lclmr =
                clmrlService.findbyIntervalPbrno(1, 999, pageIndex, pageCnts);
        List<String> clmrpbrno = new ArrayList<>();
        int noclmr = 0;
        int knoclmr = 0;
        for (ClmrbyIntervalPbrnoBus tclmr : lclmr) {
            noclmr = tclmr.getPbrno();
            if (noclmr != knoclmr) {
                clmrpbrno.add(Integer.toString(tclmr.getPbrno()));
            }
            knoclmr = noclmr;
        }
        String filpbrno = "";
        String filcode = "";
        String iwkPbrno = "";
        String lineno = "";
        boolean nodate;
        String siwkPbrno = "";
        String filecname = "";
        String fileentpno = "";
        String filehentpno = "";
        String filestop = "";
        String fileedate = "";
        String filepbrnom = "";
        int ix = 0;
        StringBuilder se = new StringBuilder();
        int z = 0;
        fileOutPathContents = new ArrayList<>();
        for (int i = 0; i < clmrpbrno.size(); i++) {
            nodate = true;
            iwkPbrno = clmrpbrno.get(i);
            for (int j = 0; j < lines.size(); j++) {
                StringBuilder sb = new StringBuilder();
                lineno = lines.get(j);
                String pbrno = "";
                if (lineno.length() >= 9) {
                    pbrno = lineno.substring(6, 9);
                } else {
                    continue;
                }

                if (iwkPbrno.equals(pbrno)) {
                    text2VoFormatter.format(lineno, fileSortC012);
                    // 代收類別
                    filcode = fileSortC012.getCode();
                    // 主辦分行
                    filpbrno = fileSortC012.getPbrno();
                    // 單位中文名
                    filecname = fileSortC012.getCname();
                    // 營利事業編號
                    fileentpno = fileSortC012.getEntpno();
                    // 總公司統編
                    filehentpno = fileSortC012.getHentpno();
                    // 代收狀態（異動前）
                    filestop = fileSortC012.getStop();
                    // 建檔日
                    fileedate = fileSortC012.getDate();
                    // 主辦分行中文
                    filepbrnom = fileSortC012.getPbrnonm();

                    ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "filcode()=" + filcode);
                    ApLogHelper.info(
                            log, false, LogType.NORMAL.getCode(), "filpbrno()=" + filpbrno);
                    nodate = false;
                    if (filcode != null && !filcode.isEmpty() && filcode.length() > 0) {
                        ix++;

                        // 第一次的時候印表投頭
                        if (ix == 1) {
                            hdsb(iwkPbrno, filepbrnom, nowdate, page, yyyy, mm, dd);
                        }
                        // 如果分行有66筆類別 要換頁
                        if (ix == 66) {
                            page++;
                            hdsb(iwkPbrno, filepbrnom, nowdate, page, yyyy, mm, dd);
                        }
                        sb.append(formatUtil.padX("", 2));
                        sb.append(filcode);
                        sb.append(formatUtil.padX("", 2));
                        sb.append(formatUtil.padX(filecname, 40));
                        sb.append(formatUtil.padX("", 1));
                        sb.append(formatUtil.padX(fileentpno, 10));
                        sb.append(formatUtil.padX("", 4));
                        sb.append(formatUtil.pad9(filehentpno, 8));
                        sb.append(formatUtil.padX("", 3));

                        int buff = parse.string2Integer(filestop);
                        String buffst = "";
                        if (buff == 0) {
                            buffst = "０－代收中";
                        } else if (buff == 1) {
                            buffst = "１－暫停代收";
                        } else if (buff == 2) {
                            buffst = "２－解約";
                        } else if (buff == 3) {
                            buffst = "３－僅主辦行可收";
                        } else if (buff == 4) {
                            buffst = "４－僅自動化機器可收（主辦行可臨櫃代收）";
                        } else if (buff == 5) {
                            buffst = "５－僅自動化機器或臨櫃轉帳（不能收現）";
                        } else if (buff == 6) {
                            buffst = "６－僅能臨櫃繳款";
                        } else if (buff == 7) {
                            buffst = "７－僅臨櫃與匯款";
                        } else if (buff == 8) {
                            buffst = "８－僅自動化機器可收";
                        } else {
                            buffst = " ";
                        }

                        sb.append(formatUtil.padX(buffst, 42));
                        sb.append(formatUtil.padX("", 23));
                        sb.append(formatUtil.pad9(fileedate, 8));

                        fileOutPathContents.add(formatUtil.padX(sb.toString(), 170));
                    } else {
                        StringBuilder sb2 = new StringBuilder();
                        hdsb(iwkPbrno, filepbrnom, nowdate, page, yyyy, mm, dd);
                        String s = " ******* 本日無資料 *******";
                        sb2.append(s);
                        fileOutPathContents.add(formatUtil.padX(sb2.toString(), 170));
                    }
                }
            }
            if (nodate && !iwkPbrno.equals(siwkPbrno)) {
                se = new StringBuilder();
                z++;
                if (z == 1) {
                    page = 1;
                    filepbrnom = "";
                    hdsb(iwkPbrno, filepbrnom, nowdate, page, yyyy, mm, dd);
                    String s = " ******* 本日無資料 *******";
                    se.append(s);
                    fileOutPathContents.add(formatUtil.padX(se.toString(), 170));
                    edsb();
                }
            } else {
                edsb();
            }
            siwkPbrno = iwkPbrno;
            z = 0;
            ix = 0;
            page = 0;
        }
        //        textFile.deleteFile(fileSortC012Path);
        textFile.writeFileContent(fileOutPath, fileOutPathContents, CHARSET5);
        upload(fileOutPath, "RPT", "");
    }

    private void hdsb(
            String iwkPbrno,
            String filepbrnom,
            String nowdate,
            int page,
            int yyyy,
            int mm,
            int dd) {
        // 表頭 第一行
        StringBuilder hdsb1 = new StringBuilder();
        hdsb1.append(formatUtil.padX(" ", 34));
        hdsb1.append(formatUtil.padX("主辦分行之電子化收款客戶名單（暫停代收） ", 42));
        fileOutPathContents.add(formatUtil.padX(hdsb1.toString(), 78));

        fileOutPathContents.add("");

        // 第二行
        StringBuilder hdsb2 = new StringBuilder();
        hdsb2.append(formatUtil.padX(" ", 1));
        hdsb2.append(formatUtil.padX(" 分行別　： ", 12));
        hdsb2.append(formatUtil.pad9(iwkPbrno, 3));
        if (filepbrnom == null || filepbrnom.isEmpty()) {
            hdsb2.append(formatUtil.padX(" ", 20));
        } else {
            hdsb2.append(formatUtil.padX(filepbrnom, 20));
        }
        hdsb2.append(formatUtil.padX(" ", 26));
        hdsb2.append(formatUtil.padX("中華民國 ", 10));
        hdsb2.append(formatUtil.pad9(yyyy + "", 3));
        hdsb2.append(formatUtil.padX("年  ", 4));
        hdsb2.append(formatUtil.pad9(mm + "", 2));
        hdsb2.append(formatUtil.padX("月  ", 4));
        hdsb2.append(formatUtil.pad9(dd + "", 2));
        hdsb2.append(formatUtil.padX("日 ", 3));
        hdsb2.append(formatUtil.padX(" ", 41));
        hdsb2.append(formatUtil.padX("報表代號： CL-C012", 19));
        fileOutPathContents.add(formatUtil.padX(hdsb2.toString(), 175));

        // 第三行
        StringBuilder hdsb3 = new StringBuilder();
        hdsb3.append(formatUtil.padX(" ", 1));
        hdsb3.append(formatUtil.padX(" 列印時間： ", 12));
        hdsb3.append(formatUtil.padX(nowdate, 15));
        hdsb3.append(formatUtil.padX(" ", 105));
        hdsb3.append(formatUtil.padX("頁　　次： ", 12));
        hdsb3.append(formatUtil.pad9(page + "", 2));
        fileOutPathContents.add(formatUtil.padX(hdsb3.toString(), 150));

        fileOutPathContents.add("");

        // 第四行
        StringBuilder hdsb4 = new StringBuilder();
        hdsb4.append(formatUtil.padX(" ", 1));
        hdsb4.append(formatUtil.padX(" 代收類別 ", 10));
        hdsb4.append(formatUtil.padX(" ", 1));
        hdsb4.append(formatUtil.padX(" 單位名稱 ", 10));
        hdsb4.append(formatUtil.padX(" ", 30));
        hdsb4.append(formatUtil.padX(" 營利事業編號 ", 13));
        hdsb4.append(formatUtil.padX(" ", 1));
        hdsb4.append(formatUtil.padX(" 總公司統編 ", 12));
        hdsb4.append(formatUtil.padX(" 代收狀態 ", 10));
        hdsb4.append(formatUtil.padX(" ", 35));
        hdsb4.append(formatUtil.padX(" 備註 ", 6));
        hdsb4.append(formatUtil.padX(" ", 11));
        hdsb4.append(formatUtil.padX(" 建檔日 ", 8));
        fileOutPathContents.add(formatUtil.padX(hdsb4.toString(), 155));

        // 第五行
        StringBuilder hdsb5 = new StringBuilder();
        hdsb5.append(
                formatUtil.padX(
                        " =====================================================================================================================================================",
                        151));
        fileOutPathContents.add(formatUtil.padX(hdsb5.toString(), 170));
    }

    private void edsb() {
        // 表尾第一行
        StringBuilder edsb1 = new StringBuilder();
        edsb1.append(
                formatUtil.padX(
                        " ------------------------------------------------------------------------------------------------------------------------------------------------------",
                        151));
        fileOutPathContents.add(formatUtil.padX(edsb1.toString(), 170));
        // 空行
        StringBuilder edsb2 = new StringBuilder();
        edsb2.append(formatUtil.padX(" ", 150));
        fileOutPathContents.add(formatUtil.padX(edsb2.toString(), 170));
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
            //            fsapBatchUtil.response(event, "E999", "檔案不存在(" + fileFtpPath + ")");
            throw new LogicException("GE999", "檔案不存在(" + fileFtpPath + ")");
        }
        return file;
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
        // event.setPeripheryRequest();
    }

    private String getLocalPath(File file) {
        return Objects.isNull(file) ? "" : file.getAbsolutePath();
    }

    //    private String cutBig5(String string, int st, int ed) {
    //        AstarUtils astarUtils = ApplicationContextUtil.getAstarUtils();
    //        String str = "";
    //        str = substringComparator.cutStringByByteLength(string, st, ed);
    //        byte[] newBytes =astarUtils.utf8ToBIG5(str);
    ////        str = astarUtils.byte2String();
    //        str = String(newBytes, Charset.forName("BIG5"));
    //        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "cutbig5 = {}", str);
    //        return str;
    //    }

    private String cutStringByByteLength(String s, int startIndex, int endIndex) {
        if (s != null && startIndex >= 0 && endIndex > startIndex) {
            byte[] b = s.getBytes(Charset.forName("BIG5"));
            if (startIndex >= b.length) {
                return "";
            }
            endIndex = Math.min(endIndex, b.length);
            byte[] newBytes = Arrays.copyOfRange(b, startIndex, endIndex);
            return new String(newBytes, Charset.forName("BIG5"));
        }
        return s;
    }

    private void batchResponse() {
        Map<String, String> responseTextMap = new HashMap<>(textMap);
        responseTextMap.put("RPTNAME", "CL-BH-C012");

        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", responseTextMap);
    }
}
