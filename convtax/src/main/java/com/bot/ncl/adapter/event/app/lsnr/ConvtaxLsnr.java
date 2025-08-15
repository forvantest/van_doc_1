/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.Convtax;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FilePUTF;
import com.bot.ncl.util.fileVo.FilePUTFCTL;
import com.bot.ncl.util.fileVo.FileSumPUTF;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
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
@Component("ConvtaxLsnr")
@Scope("prototype")
public class ConvtaxLsnr extends BatchListenerCase<Convtax> {

    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    @Autowired private ReportUtil reportUtil;
    private Convtax event;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FilePUTFCTL filePutfctl;
    @Autowired private FilePUTF filePutf;
    @Autowired private FileSumPUTF fileSumPutf;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET = "UTF-8";
    private static final String CONVF_DATA = "DATA";
    private String PATH_SEPARATOR = File.separator;
    private String fdPutfctlDir;
    private String wkPutdir;
    private String wkConvdir;

    private StringBuilder sb = new StringBuilder();
    private List<String> fileContents = new ArrayList<>(); //  檔案內容
    private Map<String, String> labelMap;

    private String processDate;
    private String tbsdy;
    private String wkFdate;
    private String wkCdate;
    private String wkPutname;
    private int wkPutname3;
    private String wkPutname3X;
    private String wkPutfile;
    private String wkConvfile;
    private String wkUserdata;
    private String wkUserdata1;
    private String wkRtlbr1;
    private String wkRtmonth1;
    private String wkRtcount1;
    private String wkRtlbr;
    private String wkRtperson;
    private String wkRtmonth;
    private String wkRtcount;

    private String convfCtl;
    private String convfCode;
    private String convfRcptid;
    private String convfDate;
    private String convfTime;
    private String convfCllbr;
    private String convfLmtdate;
    private String convfOldamt;
    private String convfUserdata;
    private String convfSitdate;
    private String convfTxtype;
    private String convfAmt;
    private String convfBdate;
    private String convfEdate;
    private String convfTotcnt;
    private String convfTotamt;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(Convtax event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "ConvtaxLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Convtax event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "ConvtaxLsnr run()");

        init(event);

        //// 執行0000-MAIN-RTN 循序讀取FD-PUTFCTL，挑符合條件之資料，執行0000-CONV-RTN
        // 012200     PERFORM 0000-MAIN-RTN   THRU   0000-MAIN-EXIT.
        _0000_main_rtn();

        // 012300 0000-END-RTN.
        // 012400     DISPLAY "SYM/CL/BH/CONVTAX CONVERT  DATA/CL/BH/PUTF OK".
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "SYM/CL/BH/CONVTAX CONVERT  DATA/CL/BH/PUTF OK");

        // 012500     STOP RUN.

        batchResponse(event);
    }

    private void init(Convtax event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "ConvtaxLsnr init");
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        // 設定檔名日期變數值
        // WK-FDATE PIC 9(06) <-WK-PUTDIR'S變數
        // WK-CDATE PIC 9(06) <-WK-CONVDIR'S變數
        // 012100 MOVE FD -BHDATE - TBSDY TO WK -FDATE, WK - CDATE.
        wkFdate = formatUtil.pad9(processDate, 7).substring(1, 7);
        wkCdate = formatUtil.pad9(processDate, 7).substring(1, 7);
        // 005100 FD  FD-PUTFCTL
        // 005200     COPY "SYM/CL/BH/FD/PUTFCTL.".
        String putfCtlDir = fileDir + CONVF_DATA + PATH_SEPARATOR + processDate;
        fdPutfctlDir = putfCtlDir + PATH_SEPARATOR + "PUTFCTL";
        textFile.deleteFile(fdPutfctlDir);
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
            fdPutfctlDir = getLocalPath(sourceFile);
        }
    }

    private void _0000_main_rtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "ConvtaxLsnr _0000_main_rtn");
        // 012700 0000-MAIN-RTN.

        //// 開啟FD-PUTFCTL
        // 012800      OPEN   INPUT   FD-PUTFCTL.
        // 012900 0000-MAIN-LOOP.
        //// 循序讀取"DATA/CL/BH/PUTFCTL"，直到檔尾，關閉FD-PUTFCTL，結束主程式段落
        // 013000      READ   FD-PUTFCTL    AT  END CLOSE  FD-PUTFCTL WITH  SAVE
        // 013100                                   GO    TO  0000-MAIN-EXIT.
        List<String> lines = textFile.readFileContent(fdPutfctlDir, CHARSET);
        //// 挑代收類別(WK-PUTNAME3=WK-PUTNAME(3:5))符合條件之資料，
        ////  執行0000-CONV-RTN 讀FD-PUTF、寫FD-CONVF；
        ////  否則，GO TO 0000-MAIN-LOOP
        for (String detail : lines) {
            text2VoFormatter.format(detail, filePutfctl);
            // 013200      MOVE        PUTFCTL-PUTNAME     TO     WK-PUTNAME.
            wkPutname = filePutfctl.getPutname();

            wkPutname3X = filePutfctl.getPutname().substring(2, 7);
            if (parse.isNumeric(wkPutname3X)) {
                wkPutname3 = parse.string2Integer(wkPutname3X);
                // 013300      IF (WK-PUTNAME3 NOT < "11011" AND WK-PUTNAME3 NOT > "11115")
                // 013350      OR  (WK-PUTNAME3 = "11331"                                 )
                // 013360      OR (WK-PUTNAME3 NOT < "11521" AND WK-PUTNAME3 NOT > "11525")
                if ((wkPutname3 >= 11011 && wkPutname3 <= 11115)
                        || wkPutname3 == 11331
                        || (wkPutname3 >= 11521 && wkPutname3 <= 11525)) {
                    // 013400        PERFORM   0000-CONV-RTN     THRU     0000-CONV-EXIT
                    _0000_conv_rtn();
                }
            }
            // 013500      ELSE
            // 013600        GO  TO    0000-MAIN-LOOP.
            //// LOOP 讀下一筆FD-PUTFCTL
            // 013700      GO    TO    0000-MAIN-LOOP.
        }
        // 013800 0000-MAIN-EXIT.
    }

    private void _0000_conv_rtn() {
        // 014300 0000-CONV-RTN.

        //// 讀FD-PUTF寫FD-CONVF
        //// 設定FD-PUTF、FD-CONVF檔名，並開啟
        //// WK-PUTDIR <-"DATA/CL/BH/PUTF/" +WK-FDATE 9(06)+"/"+WK-PUTFILE  X(10)+"."
        //// WK-CONVDIR<-"DATA/CL/BH/CONVF/"+WK-CDATE 9(06)+"/"+WK-CONVFILE X(10)+"."
        // 014400      MOVE     PUTFCTL-PUTFILE     TO   WK-PUTFILE,WK-CONVFILE.
        wkPutfile = filePutfctl.getPuttype() + filePutfctl.getPutname();
        wkConvfile = filePutfctl.getPuttype() + filePutfctl.getPutname();
        // 014500      CHANGE   ATTRIBUTE FILENAME OF FD-PUTF   TO  WK-PUTDIR.
        // 014600      CHANGE   ATTRIBUTE FILENAME OF FD-CONVF  TO  WK-CONVDIR.
        String putfDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + wkFdate
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

        // 014700      OPEN     INPUT     FD-PUTF.
        // 014800      OPEN     OUTPUT    FD-CONVF.
        // 014900 0000-CONV-LOOP.
        //// 循序讀取"DATA/CL/BH/PUTF/..."，直到檔尾，跳到0000-CONV-CLOSE 關檔
        // 015000      READ   FD-PUTF   AT  END  GO  TO 0000-CONV-CLOSE.
        List<String> lines = textFile.readFileContent(wkPutdir, CHARSET);
        for (String detail : lines) {
            text2VoFormatter.format(detail, filePutf);
            text2VoFormatter.format(detail, fileSumPutf);
            //// PUTF-CTL=12彙總資料，跳到0000-CONV-LAST寫總數
            //// PUTF-CTL<>11(也<>12)，跳掉這筆資料，LOOP讀下一筆FD-PUTF
            // 015100      IF         PUTF-CTL            =      12
            if (!"12".equals(filePutf.getCtl())) {
                // 015200        GO  TO   0000-CONV-LAST.
                // 015300      IF         PUTF-CTL         NOT  =    11
                if (!"11".equals(filePutf.getCtl())) {
                    // 015400        GO  TO   0000-CONV-LOOP.
                    continue;
                }
                //// PUTF-CTL=11明細資料
                //// 搬PUTF-...到CONVF-...
                // 015500      MOVE       SPACES              TO     CONVF-REC.
                // 015600      IF         PUTF-TXTYPE         =      "R"
                if ("R".equals(filePutf.getTxtype())) {
                    // 015700        MOVE     PUTF-USERDATA       TO     WK-USERDATA
                    wkUserdata = filePutf.getUserdata();
                    wkRtlbr = wkUserdata.substring(0, 7);
                    wkRtperson = wkUserdata.substring(7, 19);
                } else {
                    // 015800      ELSE
                    // 015900        MOVE     PUTF-USERDATA       TO     WK-USERDATA1
                    // 016000        MOVE     WK-RTLBR1           TO     WK-RTLBR
                    // 016100        MOVE     SPACES              TO     WK-RTPERSON
                    // 016200        MOVE     WK-RTMONTH1         TO     WK-RTMONTH
                    // 016300        MOVE     WK-RTCOUNT1         TO     WK-RTCOUNT.
                    wkUserdata1 = filePutf.getUserdata();
                    wkRtlbr1 = wkUserdata1.substring(0, 7);
                    wkRtmonth1 = wkUserdata1.substring(8, 10);
                    wkRtcount1 = wkUserdata1.substring(11, 16);
                    wkRtlbr = wkRtlbr1;
                    wkRtperson = "            ";
                    wkRtmonth = wkRtmonth1;
                    wkRtcount = wkRtcount1;
                    wkUserdata =
                            wkRtlbr + wkRtperson + wkRtmonth + " " + wkRtcount + "             ";
                }
                // 016320      IF         WK-RTLBR            =      "9030109"
                if ("9030109".equals(wkRtlbr)) {
                    // 016340        MOVE     "6060086"           TO     WK-RTLBR.
                    wkRtlbr = "6060086";
                }
                // 016360      IF         WK-RTLBR            =      "9040018"
                if ("9040018".equals(wkRtlbr)) {
                    // 016380        MOVE     "6060075"           TO     WK-RTLBR.
                    wkRtlbr = "6060075";
                }
                // 016400      IF         WK-RTLBR            =      "9280018"
                if ("9280018".equals(wkRtlbr)) {
                    // 016500        MOVE     "6060020"           TO     WK-RTLBR.
                    wkRtlbr = "6060020";
                }
                // 016510      IF         WK-RTLBR            =      "6060178"
                // 016520          AND    WK-RTPERSON         =      " 貢寮辦事處 "
                if ("6060178".equals(wkRtlbr) && " 貢寮辦事處 ".equals(wkRtperson)) {
                    // 016530          MOVE   "6060400"           TO     WK-RTLBR.
                    wkRtlbr = "6060400";
                }
                // 016540      IF      (  WK-RTLBR            =      "6060178"  )
                // 016550          AND (  WK-RTPERSON=" 雙溪辦事處 "  OR=" 溪辦事處 " )
                if ("6060178".equals(wkRtlbr)
                        && (" 雙溪辦事處 ".equals(wkRtperson) || " \uEEF5溪辦事處 ".equals(wkRtperson))) {
                    // 016560          MOVE   "6060396"           TO     WK-RTLBR.
                    wkRtlbr = "6060396";
                }
                // 016565      IF         WK-RTLBR            =      "9120086"
                if ("9120086".equals(wkRtlbr)) {
                    // 016570        MOVE     "6070087"           TO     WK-RTLBR.
                    wkRtlbr = "6070087";
                }
                // 016575      IF         WK-RTLBR            =      "9130076"
                if ("9130076".equals(wkRtlbr)) {
                    // 016580        MOVE     "6070076"           TO     WK-RTLBR.
                    wkRtlbr = "6070076";
                }
                // 016585      IF         WK-RTLBR            =      "9540105"
                if ("9540105".equals(wkRtlbr)) {
                    // 016590        MOVE     "6070043"           TO     WK-RTLBR.
                    wkRtlbr = "6070043";
                }
                // 016600      MOVE       PUTF-CTL            TO     CONVF-CTL.
                // 016700      MOVE       PUTF-CODE           TO     CONVF-CODE.
                // 016800      MOVE       PUTF-RCPTID         TO     CONVF-RCPTID.
                // 016900      MOVE       PUTF-DATE           TO     CONVF-DATE.
                // 017000      MOVE       PUTF-TIME           TO     CONVF-TIME.
                // 017100      MOVE       PUTF-CLLBR          TO     CONVF-CLLBR.
                // 017200      MOVE       PUTF-LMTDATE        TO     CONVF-LMTDATE.
                // 017300      MOVE       PUTF-OLDAMT         TO     CONVF-OLDAMT.
                // 017400      MOVE       WK-USERDATA         TO     CONVF-USERDATA.
                // 017500      MOVE       PUTF-SITDATE        TO     CONVF-SITDATE.
                // 017600      MOVE       PUTF-TXTYPE         TO     CONVF-TXTYPE.
                // 017700      MOVE       PUTF-AMT            TO     CONVF-AMT.
                convfCtl = filePutf.getCtl();
                convfCode = filePutf.getCode();
                convfRcptid = filePutf.getRcptid();
                convfDate = filePutf.getEntdy();
                convfTime = filePutf.getTime();
                convfCllbr = filePutf.getCllbr();
                convfLmtdate = filePutf.getLmtdate();
                convfOldamt = filePutf.getOldamt();
                convfUserdata = wkUserdata;
                convfSitdate = filePutf.getSitdate();
                convfTxtype = filePutf.getTxtype();
                convfAmt = filePutf.getAmt();

                //// 寫檔FD-CONVF(明細)
                // 017800      WRITE      CONVF-REC.
                fileContents.add(convf_Rec(1));
                //// LOOP讀下一筆FD-PUTF
                // 017900      GO  TO     0000-CONV-LOOP.
                continue;
            }
            // 018000 0000-CONV-LAST.
            //// 寫檔FD-CONVF(總數)
            // 018100      MOVE       SPACES              TO     CONVF-REC.
            // 018200      MOVE       PUTF-CTL            TO     CONVF-CTL.
            // 018300      MOVE       PUTF-CODE           TO     CONVF-CODE.
            // 018400      MOVE       PUTF-BDATE          TO     CONVF-BDATE.
            // 018500      MOVE       PUTF-EDATE          TO     CONVF-EDATE.
            // 018600      MOVE       PUTF-TOTCNT         TO     CONVF-TOTCNT.
            // 018700      MOVE       PUTF-TOTAMT         TO     CONVF-TOTAMT.
            convfCtl = fileSumPutf.getCtl();
            convfCode = fileSumPutf.getCode();
            convfBdate = fileSumPutf.getBdate();
            convfEdate = fileSumPutf.getEdate();
            convfTotcnt = fileSumPutf.getTotcnt();
            convfTotamt = fileSumPutf.getTotamt();
            // 018800      WRITE      CONVF-REC.
            fileContents.add(convf_Rec(2));

            //// LOOP讀下一筆FD-PUTF
            // 018900      GO  TO     0000-CONV-LOOP.
        }
        // 019000 0000-CONV-CLOSE.
        //// WK-PUTDIR <-"DATA/CL/BH/PUTF/" +WK-FDATE 9(06)+"/"+WK-PUTFILE  X(10)+"."
        //// WK-CONVDIR<-"DATA/CL/BH/CONVF/"+WK-CDATE 9(06)+"/"+WK-CONVFILE X(10)+"."
        // 019100      CLOSE  FD-PUTF    WITH  SAVE.

        // 019200      CHANGE ATTRIBUTE FILENAME OF FD-CONVF TO WK-PUTDIR.
        // 019300      CLOSE  FD-CONVF   WITH  SAVE.
        textFile.deleteFile(wkPutdir);
        try {
            textFile.writeFileContent(wkPutdir, fileContents, CHARSET);
            upload(wkPutdir, "DATA", "PUTF");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        // 019400 0000-CONV-EXIT.
    }

    private String convf_Rec(int recFg) {
        sb = new StringBuilder();
        // 003000 01  CONVF-REC.
        // 003100     03  CONVF-CTL                         PIC 9(02).
        // 003200     03  CONVF-CODE                        PIC X(06).
        sb.append(formatUtil.pad9(convfCtl, 2));
        sb.append(formatUtil.padX(convfCode, 6));
        switch (recFg) {
            case 1:
                // 003300     03  CONVF-DATA.
                // 003400      05 CONVF-RCPTID                      PIC X(16).
                // 003500      05 CONVF-DATE                        PIC 9(06).
                // 003600      05 CONVF-TIME                        PIC 9(06).
                // 003700      05 CONVF-CLLBR                       PIC 9(03).
                // 003800      05 CONVF-LMTDATE                     PIC 9(06).
                // 003900      05 CONVF-OLDAMT                      PIC 9(08).
                // 004000      05 CONVF-USERDATA                    PIC X(40).
                // 004100      05 CONVF-SITDATE                     PIC 9(06).
                // 004200      05 CONVF-TXTYPE                      PIC X(01).
                // 004300      05 CONVF-AMT                         PIC 9(10).
                // 004400      05 FILLER                            PIC X(10).
                sb.append(formatUtil.padX(convfRcptid, 16));
                sb.append(formatUtil.pad9(convfDate, 6));
                sb.append(formatUtil.pad9(convfTime, 6));
                sb.append(formatUtil.pad9(convfCllbr, 3));
                sb.append(formatUtil.pad9(convfLmtdate, 6));
                sb.append(formatUtil.pad9(convfOldamt, 8));
                sb.append(formatUtil.padX(convfUserdata, 40));
                sb.append(formatUtil.pad9(convfSitdate, 6));
                sb.append(formatUtil.padX(convfTxtype, 1));
                sb.append(formatUtil.pad9(convfAmt, 10));
                sb.append(formatUtil.padX("", 10));
                break;
            case 2:
                // 004500     03  CONVF-DATA-R       REDEFINES  CONVF-DATA.
                // 004600      05 CONVF-BDATE                       PIC 9(06).
                // 004700      05 CONVF-EDATE                       PIC 9(06).
                // 004800      05 CONVF-TOTCNT                      PIC 9(06).
                // 004900      05 CONVF-TOTAMT                      PIC 9(13).
                // 005000      05 FILLER                            PIC X(81).
                sb.append(formatUtil.pad9(convfBdate, 6));
                sb.append(formatUtil.pad9(convfEdate, 6));
                sb.append(formatUtil.pad9(convfTotcnt, 6));
                sb.append(formatUtil.pad9(convfTotamt, 13));
                sb.append(formatUtil.padX("", 81));
        }

        return sb.toString();
    }

    private void moveErrorResponse(LogicException e) {
        // event.setPeripheryRequest();
    }

    private void batchResponse(Convtax event) {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(event, "0000", "", null);
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

    private void upload(String filePath, String directory1, String directory2) {
        try {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "upload = {}", filePath);
            Path path = Paths.get(filePath);
            File file = path.toFile();
            String uploadPath =
                    File.separator + tbsdy + File.separator + "2FSAP" + File.separator + directory1;
            if (!directory2.isEmpty()) {
                uploadPath += File.separator + directory2;
            }
            fsapSyncSftpService.uploadFile(file, uploadPath);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private String getLocalPath(File file) {
        return Objects.isNull(file) ? "" : file.getAbsolutePath();
    }
}
