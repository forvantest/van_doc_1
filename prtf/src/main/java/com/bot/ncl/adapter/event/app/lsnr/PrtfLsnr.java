/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.Prtf;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FilePUTF;
import com.bot.ncl.util.fileVo.FilePUTFCTL;
import com.bot.ncl.util.fileVo.FileSortPUTFCTL;
import com.bot.ncl.util.fileVo.FileSumPUTF;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.ncl.util.sort.ExternalSortUtil;
import com.bot.ncl.util.sort.eum.SortBy;
import com.bot.ncl.util.sort.vo.KeyRange;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.buffer.mg.ThreadVariable;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.eum.TxCharsets;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.text.format.Text2VoFormatter;
import com.bot.txcontrol.util.text.format.Vo2TextFormatter;
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
@Component("PrtfLsnr")
@Scope("prototype")
public class PrtfLsnr extends BatchListenerCase<Prtf> {

    @Autowired private TextFileUtil textFile;
    @Autowired private ExternalSortUtil externalSortUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private FsapBatchUtil fsapBatchUtil;

    @Autowired private FormatUtil formatUtil;
    @Autowired private Parse parse;
    @Autowired private Text2VoFormatter text2VoFormatter;

    @Autowired private Vo2TextFormatter vo2TextFormatter;

    @Autowired private FilePUTFCTL filePUTFCTL;

    @Autowired private FileSortPUTFCTL fileSortPUTFCTL;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    @Autowired private FilePUTF filePutf;
    @Autowired private FileSumPUTF fileSumPutf;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> labelMap;
    private Map<String, String> textMap;
    private static final String CHARSET = "UTF-8";

    private static final String BD_CHARSET = "Big5";

    private static final String PATH_SEPARATOR = File.separator;
    private static final String FILENAME_008 = "CL-BH-008";
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";

    private String filePutfCtlPath;

    private String fileSortPutfCtlPath;

    private String reportflPath;

    private List<String> putfCtlFileContents;

    // REPORT-LINE
    private List<String> reportfl = new ArrayList<>();
    private Prtf event;
    // WK-TOTPAGE
    private int wkTotPage;
    // WK-PAGE
    private int wkPage;
    // WK-PCTL
    private int wkPctl;
    // WK-PDATE
    private String wkPdate;
    // WK-PBRNO-P
    private String wkPbrnoP;
    // WK-COUNT
    private int wkCount;
    // WK-CTL
    private int wkCtl;
    // WK-CODE
    private String wkCode;
    // WK-RCPTID
    private String wkRcptid;
    // WK-DATE
    private String wkDate;
    // WK-TIME
    private String wkTime;
    // WK-CLLBR
    private int wkCllbr;
    // WK-LMTDATE
    private String wkLmtdate;
    // WK-AMT
    private String wkAmt;
    // WK-TXTYPE
    private String wkTxtype;
    // WK-USERDATA
    private String wkUserdata;
    // WK-BDATE
    private String wkBdate;
    // WK-EDATE
    private String wkEdate;
    // WK-TOTCNT
    private String wkTotcnt;
    // WK-TOTAMT
    private String wkTotamt;
    private String processDate;
    private String tbsdy;

    @Override
    public void onApplicationEvent(Prtf event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "PrtfLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Prtf event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "PrtfLsnr run()");

        init(event);

        // 主程式0000-MAIN-RTN
        readPutfAndWriteBD008(event);

        end();

        batchResponse();
    }

    private void init(Prtf event) { // 開啟批次日期檔
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        // 設定本營業日、檔名日期變數值
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "init()");
        String putfCtlDir = fileDir + CONVF_DATA + PATH_SEPARATOR + processDate;
        filePutfCtlPath = putfCtlDir + PATH_SEPARATOR + "PUTFCTL";
        textFile.deleteFile(filePutfCtlPath);
        String sourceFtpPath =
                "NCL"
                        + File.separator
                        + tbsdy
                        + File.separator
                        + "2FSAP"
                        + File.separator
                        + "DATA"
                        + File.separator
                        + "PUTFCTL"; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, putfCtlDir);
        if (sourceFile != null) {
            filePutfCtlPath = getLocalPath(sourceFile);
        }

        fileSortPutfCtlPath =
                fileDir + "DATA" + File.separator + processDate + File.separator + "SORT_PUTFCTL";
        reportflPath =
                fileDir + CONVF_RPT + PATH_SEPARATOR + processDate + PATH_SEPARATOR + FILENAME_008;
        // 開啟檔案
        //     OPEN OUTPUT  REPORTFL.
        textFile.deleteFile(reportflPath);
        reportfl = new ArrayList<>();
        //     OPEN INPUT   FD-PUTFCTL.
        putfCtlFileContents = textFile.readFileContent(filePutfCtlPath, CHARSET);
        if (Objects.isNull(putfCtlFileContents) || putfCtlFileContents.isEmpty()) {
            ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "putfCtlFileContents is null");
            return;
        }

        // DISPLAY訊息，包含在系統訊息中
        //     CHANGE ATTRIBUTE DISPLAYONLYTOMCS OF MYSELF TO TRUE.
        //     MOVE    0    TO        WK-TOTPAGE.
        wkTotPage = 0;
        wkCount = 0;

        textFile.deleteFile(fileSortPutfCtlPath);
    }

    // 0000-MAIN-RTN
    private void readPutfAndWriteBD008(Prtf event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "readPutfAndWriteBD008()");

        sortin();

        sortout(event);
    }

    // CS-SORTIN-RTN.
    private void sortin() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "sortin()");
        // 循序讀取"DATA/CL/BH/PUTFCTL"，直到檔尾，結束本節
        //     READ    FD-PUTFCTL    AT END
        //       GO TO CS-SORTIN-EXIT.
        // 挑PUTFCTL-PUTFILE(1:1)=1 AND PUTFCTL-PUTNAME(1:1)="0"之資料
        // 搬相關欄位至SORT-REC
        // PUTFCTL-PUTFILE(1:1)=1 報表
        // 因PUTFCTL-GENDT 9(06)，for 民國百年後正常顯示7位
        // S-FDATE 9(06)
        // S-PDATE 9(07)
        //     IF      PUTFCTL-PUTTYPE1 = 1 AND PUTFCTL-PUTNAME-2-1="0"
        //       MOVE  PUTFCTL-PUTFILE         TO   S-PUTFILE
        //       MOVE  PUTFCTL-GENDT           TO   S-FDATE,S-PDATE
        //       MOVE  PUTFCTL-PBRNO           TO   S-PBRNO
        //       IF    S-PDATE < 991220
        //         ADD 1000000                 TO   S-PDATE
        //         RELEASE SORT-REC
        //       ELSE
        //         RELEASE SORT-REC.
        // *      PERFORM  2000-FINDPBRNO-RTN   THRU 2000-FINDPBRNO-EXIT
        // *      PERFORM  1000-WCODE-RTN       THRU 1000-WCODE-EXIT.
        List<String> sortFileContents = new ArrayList<>();
        ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境
        for (String putfCtlFileContent : putfCtlFileContents) {
            text2VoFormatter.format(putfCtlFileContent, filePUTFCTL);
            // 01  SORT-REC.
            //     03  S-PUTFILE                        PIC X(10).
            //     03  S-FDATE                          PIC 9(06).
            //     03  S-PDATE                          PIC 9(07).
            //     03  S-PBRNO                          PIC 9(03).
            if (filePUTFCTL.getPuttype().charAt(0) == '1'
                    && filePUTFCTL.getPutname().charAt(1) == '0') {
                fileSortPUTFCTL = new FileSortPUTFCTL();
                fileSortPUTFCTL.setPutfile(filePUTFCTL.getPuttype() + filePUTFCTL.getPutname());
                fileSortPUTFCTL.setFdate(filePUTFCTL.getGendt());
                String pdate = filePUTFCTL.getGendt();
                if (parse.isNumeric(pdate) && parse.string2Integer(pdate) <= 991220) {
                    pdate = "" + (parse.string2Integer(pdate) + 1000000);
                }
                fileSortPUTFCTL.setPdate(pdate);
                fileSortPUTFCTL.setPbrno(filePUTFCTL.getPbrno());
                sortFileContents.add(vo2TextFormatter.formatRS(fileSortPUTFCTL, false));
            } else {
                // ??
            }
            // LOOP讀下一筆FD-PUTFCTL
            //     GO TO CS-SORTIN-RTN.
        }
        textFile.writeFileContent(fileSortPutfCtlPath, sortFileContents, CHARSET);
        upload(fileSortPutfCtlPath, "DATA", "");
        // CS-SORTIN-EXIT.
        //     EXIT.
    }

    // CS-SORTOUT-RTN
    private void sortout(Prtf event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "sortout()");
        //     SORT    SORTFL
        //             ASCENDING KEY        S-PBRNO
        List<KeyRange> keyRanges = new ArrayList<>();
        keyRanges.add(new KeyRange(24, 3, SortBy.ASC));
        File file = Paths.get(fileSortPutfCtlPath).toFile();
        externalSortUtil.sortingFile(file, file, keyRanges, CHARSET);

        // 1000-WCODE-RTN.
        // 將SORT後的記錄讀出，直到檔尾，結束本節
        //     RETURN  SORTFL    AT END   GO TO  CS-SORTOUT-EXIT.
        List<String> sortFileContents = textFile.readFileContent(fileSortPutfCtlPath, CHARSET);
        if (Objects.isNull(sortFileContents) || sortFileContents.isEmpty()) {
            ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "sortFileContents is null");
            return;
        }

        for (String sortFileContent : sortFileContents) {
            text2VoFormatter.format(sortFileContent, fileSortPUTFCTL);
            // 設定FD-PUTF檔名，並開啟
            //     MOVE    S-PUTFILE           TO     WK-PUTFILE.
            //     MOVE    S-FDATE             TO     WK-FDATE.
            String wkPutfile = fileSortPUTFCTL.getPutfile();
            String wkFdate = fileSortPUTFCTL.getFdate();
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkFdate = {}", wkFdate);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkPutfile = {}", wkPutfile);

            // WK-PDATE  :印表日期(WK-TITLE-LINE2'S變數)
            // WK-PBRNO-P:分行別  (WK-TITLE-LINE2'S變數)
            //     MOVE    S-PDATE             TO     WK-PDATE.
            //     MOVE    S-PBRNO             TO     WK-PBRNO-P.
            wkPdate = fileSortPUTFCTL.getPdate();
            wkPbrnoP = fileSortPUTFCTL.getPbrno();

            // WK-PUTDIR = <-"DATA/CL/BH/PUTF/"+WK-FDATE"/"+WK-PUTFILE
            // OPEN INPUT
            //     CHANGE  ATTRIBUTE FILENAME OF FD-PUTF TO WK-PUTDIR.
            //     OPEN    INPUT     FD-PUTF.
            //     MOVE    1                   TO     WK-PAGE,WK-PCTL.
            //     ADD     1                   TO     WK-TOTPAGE.
            String putfDir =
                    fileDir
                            + CONVF_DATA
                            + PATH_SEPARATOR
                            + processDate
                            + PATH_SEPARATOR
                            + "PUTF"
                            + PATH_SEPARATOR
                            + wkFdate;
            String wkPutdir = putfDir + PATH_SEPARATOR + wkPutfile;
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

            if (wkPutfile.trim().isEmpty()) {
                ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "wkPutfile is empty");
                continue;
            }
            List<String> putfFileContents;
            try {
                putfFileContents = textFile.readFileContent(wkPutdir, CHARSET);
            } catch (LogicException e) {
                putfFileContents = new ArrayList<>();
            }
            if (Objects.isNull(putfFileContents) || putfFileContents.isEmpty()) {
                ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "putfFileContents is null");
                continue;
            }
            wkPage = 1;
            wkPctl = 1;
            wkTotPage++;

            // 執行200-WTIT-RTN 寫REPORTFL表頭
            //     PERFORM 200-WTIT-RTN        THRU   200-WTIT-EXIT.
            wtit();

            int wkRtncd = 0; // Wei: 原本3000-FINDOTR-RTN的相關處理改配合JAVA的FOR LOOP處理修改判斷方式

            Map<String, ArrayMap> arrayMap =
                    event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
            Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();

            // 設定工作日、檔名日期變數值
            processDate =
                    formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱

            // 1000-WCODE-LOOP.
            // 循序讀取"DATA/CL/BH/PUTF/..."，直到檔尾，關檔，跳到1000-WCODE-EXIT
            //     READ    FD-PUTF   AT END
            //       CLOSE FD-PUTF   WITH   SAVE
            //       GO TO 1000-WCODE-EXIT.
            // 1000-WCODE-OTHLOOP.
            for (String putfFilContent : putfFileContents) {

                if (wkRtncd == 1) { // Wei: 原本3000-FINDOTR-RTN的相關處理改配合JAVA的FOR LOOP處理修改判斷方式
                    wkRtncd = 0;
                    wkPage = 1;
                    wkPctl = 1;
                    wtit();
                }

                text2VoFormatter.format(putfFilContent, filePutf);
                text2VoFormatter.format(putfFilContent, fileSumPutf);
                // PUTF-CTL2=1(明細)
                //  A.執行300-WDTL-RTN 寫REPORTFL報表明細
                // PUTF-CTL2=2(代收類別、總金額、總筆數)
                //  A.執行400-WTOT-RTN 寫REPORTFL表尾
                //  B.執行3000-FINDOTR-RTN，判斷PUTFILE是否有重複
                //   重複時，執行200-WTIT-RTN 寫REPORTFL表頭
                //   正常時，結束1000-WCODE-LOOP
                //     IF      PUTF-CTL2           =      1
                //       ADD   1                   TO     WK-COUNT
                //       PERFORM 300-WDTL-RTN      THRU   300-WDTL-EXIT
                //     ELSE
                //       PERFORM 400-WTOT-RTN      THRU   400-WTOT-EXIT
                //       MOVE  0                   TO     WK-COUNT
                //       PERFORM  3000-FINDOTR-RTN THRU   3000-FINDOTR-EXIT
                //       IF    WK-RTNCD            =      1
                //         MOVE    1               TO     WK-PAGE,WK-PCTL
                //         ADD     1               TO     WK-TOTPAGE
                //         PERFORM 200-WTIT-RTN    THRU   200-WTIT-EXIT
                //         GO TO   1000-WCODE-OTHLOOP
                //       ELSE
                //         GO TO   1000-WCODE-EXIT.
                if (filePutf.getCtl().charAt(1) == '1') {
                    wkCount++;
                    wdtl();
                    // 執行200-WTIT-RTN 寫REPORTFL表頭
                    //     IF      WK-PCTL             =      50
                    //       ADD   1                   TO     WK-PAGE,WK-TOTPAGE
                    //       PERFORM 200-WTIT-RTN      THRU   200-WTIT-EXIT
                    //       MOVE  0                   TO     WK-PCTL.
                    //     ADD     1                   TO     WK-PCTL.
                    if (wkPctl == 50) {
                        wkPage++;
                        wkTotPage++;
                        wtit();
                        wkPctl = 0;
                    }
                    wkPctl++;
                } else {
                    wtot();
                    wkCount = 0;
                    wkRtncd = 1; // Wei: 原本3000-FINDOTR-RTN的相關處理改配合JAVA的FOR LOOP處理修改判斷方式
                }
                // LOOP讀下一筆FD-PUTF
                //     GO TO   1000-WCODE-LOOP.
            }
            // 1000-WCODE-EXIT.
            // LOOP讀下一筆SORTFL
            //     GO TO   CS-SORTOUT-RTN
        }
        if (!reportfl.isEmpty()) {
            textFile.writeFileContent(reportflPath, reportfl, BD_CHARSET);
            upload(reportflPath, "RPT", "");

            reportfl = new ArrayList<>();
        } else {
            ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "reportfl is null");
        }
        // CS-SORTOUT-EXIT.
        //     EXIT.
    }

    // 200-WTIT-RTN
    private void wtit() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wtit()");
        // 寫REPORTFL表頭(WK-TITLE-LINE1、WK-TITLE-LINE2、WK-TITLE-LINE3)
        //     MOVE       SPACES              TO     REPORT-LINE.
        //     WRITE      REPORT-LINE         AFTER  PAGE.
        //     MOVE       SPACES              TO     REPORT-LINE.
        //     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE1.
        //     MOVE       SPACES              TO     REPORT-LINE.
        //     WRITE      REPORT-LINE         AFTER  1 LINE.
        //     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE2.
        //     MOVE       SPACES              TO     REPORT-LINE.
        //     WRITE      REPORT-LINE         AFTER  1 LINE.
        //     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE3.
        //     MOVE       SPACES              TO     REPORT-LINE.
        //     WRITE      REPORT-LINE         FROM   WK-GATE-LINE.
        reportfl.add("\u000c");
        reportfl.add(wkTitleLine1());
        reportfl.add("");
        reportfl.add(wkTitleLine2());
        reportfl.add("");
        reportfl.add(wkTitleLine3());
        reportfl.add(wkGateLine());

        // 200-WTIT-EXIT.
        //     EXIT.
    }

    // 300-WDTL-RTN
    private void wdtl() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wdtl()");
        // 寫REPORTFL報表明細(WK-DETAIL-LINE)
        // 搬相關欄位到WK-DETAIL-LINE for REPORTFL
        //     MOVE       PUTF-CTL               TO     WK-CTL.
        wkCtl = parse.isNumeric(filePutf.getCtl()) ? parse.string2Integer(filePutf.getCtl()) : 0;
        //     MOVE       PUTF-CLLBR             TO     WK-CLLBR.
        wkCllbr =
                parse.isNumeric(filePutf.getCllbr())
                        ? parse.string2Integer(filePutf.getCllbr())
                        : 0;
        //     MOVE       PUTF-CODE              TO     WK-CODE.
        wkCode = filePutf.getCode();
        //     MOVE       PUTF-DATE              TO     WK-DATE.
        wkDate = formatUtil.pad9(filePutf.getEntdy(), 6);
        //     IF         PUTF-DATE < 991220
        //       MOVE     1                      TO     WK-DATE(1:1).
        int numericDate =
                parse.isNumeric(filePutf.getEntdy())
                        ? parse.string2Integer(filePutf.getEntdy())
                        : 0;
        if (numericDate < 991220) {
            wkDate = "1" + wkDate;
        }
        //     MOVE       PUTF-TIME              TO     WK-TIME.
        wkTime = filePutf.getTime();
        //     MOVE       PUTF-RCPTID            TO     WK-RCPTID.
        wkRcptid = filePutf.getRcptid();
        //     MOVE       PUTF-AMT               TO     WK-AMT.
        wkAmt = filePutf.getAmt();
        //     MOVE       PUTF-TXTYPE            TO     WK-TXTYPE.
        wkTxtype = filePutf.getTxtype();
        //     MOVE       PUTF-LMTDATE           TO     WK-LMTDATE.
        wkLmtdate = filePutf.getLmtdate();
        //     IF         PUTF-LMTDATE < 991220 AND PUTF-LMTDATE > 000000
        //       MOVE     1                      TO     WK-LMTDATE(1:1).
        int lmtdate =
                (parse.isNumeric(filePutf.getLmtdate())
                        ? parse.string2Integer(filePutf.getLmtdate())
                        : 0);
        if (lmtdate < 991220 && lmtdate > 0) {
            wkLmtdate = "1" + wkLmtdate;
        }
        //     MOVE       PUTF-USERDATA          TO     WK-USERDATA.
        wkUserdata = filePutf.getUserdata();

        //     WRITE      REPORT-LINE            FROM   WK-DETAIL-LINE.
        reportfl.add(wkDetailLine());

        // 300-WDTL-EXIT.
        //     EXIT.
    }

    // 400-WTOT-RTN
    private void wtot() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wtot()");
        // 寫REPORTFL表尾(WK-TOTAL-LINE)
        // 搬相關欄位到WK-TOTAL-LINE for REPORTFL
        //     MOVE       PUTF-BDATE     TO    WK-BDATE.
        wkBdate = fileSumPutf.getBdate();
        int bdate =
                (parse.isNumeric(fileSumPutf.getBdate())
                        ? parse.string2Integer(fileSumPutf.getBdate())
                        : 0);
        //     IF         PUTF-BDATE < 991220
        //       MOVE     1              TO    WK-BDATE(1:1).
        if (bdate < 991220) {
            wkBdate = "1" + wkBdate;
        }
        //     MOVE       PUTF-EDATE     TO    WK-EDATE.
        wkEdate = fileSumPutf.getEdate();
        //     IF         PUTF-EDATE < 991220
        //       MOVE     1              TO    WK-EDATE(1:1).
        int edate =
                (parse.isNumeric(fileSumPutf.getEdate())
                        ? parse.string2Integer(fileSumPutf.getEdate())
                        : 0);
        if (edate < 991220) {
            wkEdate = "1" + wkEdate;
        }
        //     MOVE       PUTF-TOTCNT    TO    WK-TOTCNT.
        wkTotcnt = fileSumPutf.getTotcnt();
        //     MOVE       PUTF-TOTAMT    TO    WK-TOTAMT.
        wkTotamt = fileSumPutf.getTotamt();

        //     MOVE       SPACES         TO    REPORT-LINE.
        //     WRITE      REPORT-LINE    FROM  WK-GATE-LINE.
        reportfl.add(wkGateLine());
        //     MOVE       SPACES         TO    REPORT-LINE.
        //     WRITE      REPORT-LINE    FROM  WK-TOTAL-LINE.
        reportfl.add(wkTotalLine());

        // 400-WTOT-EXIT.
        //     EXIT.
    }

    private void end() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "end()");
        textFile.deleteFile(fileSortPutfCtlPath);
    }

    private String wkTitleLine1() {
        // 01 WK-TITLE-LINE1.
        //    02 FILLER                          PIC X(30) VALUE SPACE.
        //    02 FILLER                          PIC X(54) VALUE
        //       "ONLINE COLLECT OPERATION DETAIL REPORT (FOR CUSTOMER) ".
        //    02 FILLER                          PIC X(24) VALUE SPACE.
        //    02 FILLER                          PIC X(12) VALUE
        //       "FORM : C008 ".
        String wkTitleLine1 = "";
        wkTitleLine1 = formatUtil.padX(wkTitleLine1, 30);
        wkTitleLine1 +=
                formatUtil.padX("ONLINE COLLECT OPERATION DETAIL REPORT (FOR CUSTOMER) ", 54);
        wkTitleLine1 += formatUtil.padX("", 24);
        wkTitleLine1 += formatUtil.padX("FORM : C008 ", 12);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkTitleLine1 = {}", wkTitleLine1);
        return wkTitleLine1;
    }

    private String wkTitleLine2() {
        // 01 WK-TITLE-LINE2.
        //    02 FILLER                          PIC X(10) VALUE
        //       " 分行別： ".
        //    02 WK-PBRNO-P                      PIC X(03).
        // *   02 WK-PBRNO1                       PIC X(02).
        // *   02 WK-PBRNO2                       PIC X(01).
        //    02 FILLER                          PIC X(04) VALUE SPACE.
        //    02 FILLER                          PIC X(13) VALUE
        //       " 印表日期：  ".
        //    02 WK-PDATE                        PIC Z99/99/99.
        //    02 FILLER                          PIC X(69) VALUE SPACE.
        //    02 FILLER                          PIC X(09) VALUE
        //       " 總頁次 :".
        //    02 WK-TOTPAGE                      PIC 9(04).
        //    02 FILLER                          PIC X(11) VALUE
        //       "   分頁次 :".
        //    02 WK-PAGE                         PIC 9(04).
        String wkTitleLine2;
        wkTitleLine2 = formatUtil.padX(" 分行別： ", 10);
        wkTitleLine2 += formatUtil.padX(wkPbrnoP, 3);
        wkTitleLine2 += formatUtil.padX("", 4);
        wkTitleLine2 += formatUtil.padX(" 印表日期：  ", 13);
        wkTitleLine2 += reportUtil.customFormat(wkPdate, "Z99/99/99");
        wkTitleLine2 += formatUtil.padX("", 69);
        wkTitleLine2 += formatUtil.padX(" 總頁次 :", 9);
        wkTitleLine2 += formatUtil.pad9("" + wkTotPage, 4);
        wkTitleLine2 += formatUtil.padX("   分頁次 :", 11);
        wkTitleLine2 += formatUtil.pad9("" + wkPage, 4);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkTitleLine2 = {}", wkTitleLine2);
        return wkTitleLine2;
    }

    private String wkTitleLine3() {
        // 01 WK-TITLE-LINE3.
        //    02 FILLER              PIC X(02) VALUE SPACE.
        //    02 FILLER              PIC X(08) VALUE "  序號  ".
        //    02 FILLER              PIC X(10) VALUE " 媒體格式 ".
        //    02 FILLER              PIC X(10) VALUE " 代收類別 ".
        //    02 FILLER              PIC X(19) VALUE "  銷帳編號         ".
        //    02 FILLER              PIC X(11) VALUE "  日期     ".
        //    02 FILLER              PIC X(11) VALUE "  時間     ".
        //    02 FILLER              PIC X(09) VALUE " 代收行  ".
        //    02 FILLER              PIC X(10) VALUE " 繳款期限 ".
        //    02 FILLER              PIC X(18) VALUE "         金額     ".
        //    02 FILLER              PIC X(10) VALUE " 繳款方式 ".
        //    02 FILLER              PIC X(16) VALUE "    備註資料    ".
        String wkTitleLine3 = "";
        wkTitleLine3 = formatUtil.padX(wkTitleLine3, 2);
        wkTitleLine3 += formatUtil.padX("  序號  ", 8);
        wkTitleLine3 += formatUtil.padX(" 媒體格式 ", 10);
        wkTitleLine3 += formatUtil.padX(" 代收類別 ", 10);
        wkTitleLine3 += formatUtil.padX("  銷帳編號         ", 19);
        wkTitleLine3 += formatUtil.padX("  日期     ", 11);
        wkTitleLine3 += formatUtil.padX("  時間     ", 11);
        wkTitleLine3 += formatUtil.padX(" 代收行  ", 9);
        wkTitleLine3 += formatUtil.padX(" 繳款期限 ", 10);
        wkTitleLine3 += formatUtil.padX("         金額     ", 18);
        wkTitleLine3 += formatUtil.padX(" 繳款方式 ", 10);
        wkTitleLine3 += formatUtil.padX("    備註資料    ", 16);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkTitleLine3 = {}", wkTitleLine3);
        return wkTitleLine3;
    }

    private String wkGateLine() {
        // 01 WK-GATE-LINE.
        //    02 FILLER                   PIC X(03) VALUE SPACE.
        //    02 FILLER                   PIC X(150) VALUE ALL "-".
        return "   " + reportUtil.makeGate("-", 150);
    }

    private String wkDetailLine() {
        // 01 WK-DETAIL-LINE.
        String wkDetailLine = "";
        //    02 FILLER                          PIC X(04) VALUE SPACE.
        wkDetailLine = formatUtil.padX(wkDetailLine, 4);
        //    02 WK-COUNT                        PIC 9(06).
        wkDetailLine += formatUtil.pad9("" + wkCount, 6);
        //    02 FILLER                          PIC X(05) VALUE SPACE.
        wkDetailLine += formatUtil.padX("", 5);
        //    02 WK-CTL                          PIC 9(02).
        wkDetailLine += formatUtil.pad9("" + wkCtl, 2);
        //    02 FILLER                          PIC X(04) VALUE SPACE.
        wkDetailLine += formatUtil.padX("", 4);
        //    02 WK-CODE                         PIC X(06).
        wkDetailLine += formatUtil.padX(wkCode, 6);
        //    02 FILLER                          PIC X(04) VALUE SPACE.
        wkDetailLine += formatUtil.padX("", 4);
        //    02 WK-RCPTID                       PIC X(16).
        wkDetailLine += formatUtil.padX(wkRcptid, 16);
        //    02 FILLER                          PIC X(01) VALUE SPACE.
        wkDetailLine += formatUtil.padX("", 1);
        //    02 WK-DATE                         PIC Z99/99/99.
        wkDetailLine += reportUtil.customFormat(wkDate, "Z99/99/99");
        //    02 FILLER                          PIC X(03) VALUE SPACE.
        wkDetailLine += formatUtil.padX("", 3);
        //    02 WK-TIME                         PIC 99:99:99.
        wkDetailLine += reportUtil.customFormat(wkTime, "99:99:99");
        //    02 FILLER                          PIC X(05) VALUE SPACE.
        wkDetailLine += formatUtil.padX("", 5);
        //    02 WK-CLLBR                        PIC 9(03).
        wkDetailLine += formatUtil.pad9("" + wkCllbr, 3);
        //    02 FILLER                          PIC X(04) VALUE SPACE.
        wkDetailLine += formatUtil.padX("", 4);
        //    02 WK-LMTDATE                      PIC Z99/99/99.
        wkDetailLine += reportUtil.customFormat(wkLmtdate, "Z99/99/99");
        //    02 FILLER                          PIC X(02) VALUE SPACE.
        wkDetailLine += formatUtil.padX("", 2);
        //    02 WK-AMT                          PIC Z,ZZZ,ZZZ,ZZ9.
        wkDetailLine += reportUtil.customFormat(wkAmt, "Z,ZZZ,ZZZ,ZZ9");
        //    02 FILLER                          PIC X(07) VALUE SPACE.
        wkDetailLine += formatUtil.padX("", 7);
        //    02 WK-TXTYPE                       PIC X(01).
        wkDetailLine += formatUtil.padX(wkTxtype, 1);
        //    02 FILLER                          PIC X(10) VALUE SPACE.
        wkDetailLine += formatUtil.padX("", 10);
        //    02 WK-USERDATA                     PIC X(40).
        wkDetailLine += formatUtil.padX(wkUserdata, 40);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkDetailLine = {}", wkDetailLine);
        return wkDetailLine;
    }

    private String wkTotalLine() {
        String wkTotalLine = "";
        // 01 WK-TOTAL-LINE.
        //    02 FILLER                   PIC X(04) VALUE SPACES.
        wkTotalLine = formatUtil.padX(wkTotalLine, 4);
        //    02 FILLER                   PIC X(13) VALUE " 資料起日  : ".
        wkTotalLine += formatUtil.padX(" 資料起日  : ", 13);
        //    02 WK-BDATE                 PIC Z99/99/99.
        wkTotalLine += reportUtil.customFormat(wkBdate, "Z99/99/99");
        //    02 FILLER                   PIC X(04) VALUE SPACES.
        wkTotalLine += formatUtil.padX("", 4);
        //    02 FILLER                   PIC X(11) VALUE " 資料迄日 :".
        wkTotalLine += formatUtil.padX(" 資料迄日 :", 11);
        //    02 WK-EDATE                 PIC Z99/99/99.
        wkTotalLine += reportUtil.customFormat(wkEdate, "Z99/99/99");
        //    02 FILLER                   PIC X(04) VALUE SPACES.
        wkTotalLine += formatUtil.padX("", 4);
        //    02 FILLER                   PIC X(08) VALUE "  筆數 :".
        wkTotalLine += formatUtil.padX("  筆數 :", 8);
        //    02 WK-TOTCNT                PIC ZZZ,ZZ9.B.
        wkTotalLine += reportUtil.customFormat(wkTotcnt, "ZZZ,ZZ9.B");
        //    02 FILLER                   PIC X(04) VALUE SPACES.
        wkTotalLine += formatUtil.padX("", 4);
        //    02 FILLER                   PIC X(13) VALUE "  代收金額 : ".
        wkTotalLine += formatUtil.padX("  代收金額 : ", 13);
        //    02 WK-TOTAMT                PIC Z,ZZZ,ZZZ,ZZZ,ZZ9.B.
        wkTotalLine += reportUtil.customFormat(wkTotamt, "Z,ZZZ,ZZZ,ZZZ,ZZ9.B");
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkTotalLine = {}", wkTotalLine);
        return wkTotalLine;
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
        // event.setPeripheryRequest();
    }

    private void batchResponse() {
        Map<String, String> responseTextMap = new HashMap<>(textMap);
        responseTextMap.put("RPTNAME", FILENAME_008);
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", responseTextMap);
    }
}
