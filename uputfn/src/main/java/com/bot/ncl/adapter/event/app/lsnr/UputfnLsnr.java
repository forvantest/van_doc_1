/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.Uputfn;
import com.bot.ncl.dto.entities.ClmcBus;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.dto.entities.CltmrBus;
import com.bot.ncl.jpa.svc.ClmcService;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.ncl.jpa.svc.CltmrService;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.*;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.date.DateDto;
import com.bot.txcontrol.util.date.DateUtil;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.text.format.Text2VoFormatter;
import com.bot.txcontrol.util.text.format.Vo2TextFormatter;
import com.bot.txcontrol.util.transmit.FsapSyncSftpService;
import java.io.File;
import java.math.BigDecimal;
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
@Component("UputfnLsnr")
@Scope("prototype")
public class UputfnLsnr extends BatchListenerCase<Uputfn> {
    @Autowired private ClmrService clmrService;
    @Autowired private CltmrService cltmrService;
    @Autowired private ClmcService clmcService;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    @Autowired private TextFileUtil textFile;

    @Autowired private FormatUtil formatUtil;

    @Autowired private Parse parse;

    @Autowired private DateUtil dateUtil;

    @Autowired private Text2VoFormatter text2VoFormatter;

    @Autowired private Vo2TextFormatter vo2TextFormatter;

    @Autowired private FileUPUTH fileUputh;

    @Autowired private FilePUTFN filePutfn;

    @Autowired private FileSumPUTFN fileSumPUTFN;

    @Autowired private FilePUTFCTL2 filePUTFCTL2;

    @Autowired private FilePUTFCTL filePUTFCTL;
    private static final String CONVF_DATA = "DATA";

    // WORKING-STORAGE  SECTION.
    // 01 WK-CLNDR-KEY                       PIC 9(03).
    // 01 WK-CLNDR-STUS                      PIC X(02).
    // 01 PUTFCTL-STATUS                     PIC X(02).
    // 01 WK-YYMMDD                          PIC 9(06).
    private int wkYymmdd;
    // 01 WK-PUTADDR                         PIC X(40).
    private String wkPutaddr;
    // 01 WK-PBRNO                           PIC 9(03).
    private int wkPbrno;
    // 01 WK-TOTCNT                          PIC 9(06).
    private int wkTotcnt;
    // 01 WK-TOTAMT                          PIC 9(13).
    private BigDecimal wkTotamt;
    // 01 WK-CODE                            PIC X(06).
    private String wkCode;

    private int wkRtn;
    // 01 WK-RCPTID.
    //    03 WK-RCPTID-1                     PIC X(01).
    //    03 WK-RCPTID-7                     PIC X(07).
    //    03 WK-RCPTID-8                     PIC X(08).
    private String wkRcptid;

    //    private String getWkRcptid1() {
    //        wkRcptid = formatUtil.padX(wkRcptid, 16);
    //        return wkRcptid.substring(0, 1);
    //    }

    private String getWkRcptid7() {
        wkRcptid = formatUtil.padX(wkRcptid, 16);
        return wkRcptid.substring(1, 8);
    }

    //    private String getWkRcptid8() {
    //        wkRcptid = formatUtil.padX(wkRcptid, 16);
    //        return wkRcptid.substring(9);
    //    }

    // * 中油公司銷帳媒體需加入流水序號，加入ＷＫ－ＣＰＣ變數配合
    // 01 WK-CPC.
    //  03 WK-CPC-SERINO                     PIC 9(06).
    //  03 WK-CPC-FILLER                     PIC X(04) VALUE SPACES.
    private String wkCpc;

    // 01 WK-PUTDIR.
    //  03 FILLER                            PIC X(16)
    //                         VALUE "DATA/CL/BH/PUTF/".
    //  03 WK-FDATE                          PIC 9(06).
    //  03 FILLER                            PIC X(01)
    //                            VALUE "/".
    //  03 WK-PUTFILE                        PIC X(10).
    //  03 FILLER                            PIC X(01)
    //                            VALUE ".".
    private String wkPutdir;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private int wkFdate;
    private String wkPutfile;
    private String fileUputhPath;
    private String filePutfctl2Path;
    private String filePutfctlPath;

    private static final String CHARSET = "UTF-8";

    private static final String PATH_SEPARATOR = File.separator;

    private List<String> putfnFileContents;
    private List<String> putfctl2FileContents;
    private List<String> putfctlFileContents;

    private CltmrBus cltmr;
    private String tbsdy;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(Uputfn event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "UputfnLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Uputfn event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "UputfnLsnr run()");

        init(event);

        readUputhAndWritePutfn();
        batchResponse(event);
    }

    // 0000-START.
    private void init(Uputfn event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "init()");
        // 開檔
        // 0000-START.
        //     OPEN INQUIRY BOTSRDB.
        //     OPEN INPUT   FD-BHDATE.
        //     OPEN INPUT   FD-CLNDR.
        //     OPEN INPUT   FD-UPUTH.
        //     OPEN I-O     FD-PUTFCTL.
        //     OPEN EXTEND  FD-PUTFCTL2.
        // ???
        //     CHANGE ATTRIBUTE DISPLAYONLYTOMCS OF MYSELF TO TRUE.
        // 讀批次日期檔，設定本營業日變數值；若讀不到，結束程式
        //     READ    FD-BHDATE AT END DISPLAY "READ FD-BHDATE ERROR"
        //         STOP RUN.
        //     MOVE    FD-BHDATE-TBSDY TO     WK-YYMMDD.
        // 清變數值
        //     MOVE    SPACES         TO      WK-PUTFILE,WK-CODE.
        //     MOVE    0              TO      WK-TOTCNT,WK-TOTAMT.
        wkYymmdd = event.getAggregateBuffer().getTxCom().getTbsdy();

        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();

        // 設定工作日、檔名日期變數值
        String processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        String inputDir = fileDir + "DATA" + File.separator + processDate;
        fileUputhPath = inputDir + File.separator + "UPUTH";
        textFile.deleteFile(fileUputhPath);
        String sourceFtpPath =
                "NCL"
                        + File.separator
                        + tbsdy
                        + File.separator
                        + "2FSAP"
                        + File.separator
                        + "DATA"
                        + File.separator
                        + "UPUTH"; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, inputDir);
        if (sourceFile != null) {
            fileUputhPath = getLocalPath(sourceFile);
        }

        filePutfctl2Path =
                fileDir + CONVF_DATA + PATH_SEPARATOR + processDate + PATH_SEPARATOR + "PUTFCTL2";
        filePutfctlPath =
                fileDir + CONVF_DATA + PATH_SEPARATOR + processDate + PATH_SEPARATOR + "PUTFCTL";

        putfnFileContents = new ArrayList<>();
        putfctl2FileContents = new ArrayList<>();
        putfctlFileContents = new ArrayList<>();

        wkTotcnt = 0;
        wkTotamt = BigDecimal.ZERO;
    }

    private void readUputhAndWritePutfn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "readUputhAndWritePutfn()");
        // 循序讀取"DATA/CL/BH/UPUTH"
        //     READ    FD-UPUTH        AT  END
        // WK-PUTFILE=SPACES，表示FD-UPUTH無資料，結束本段落
        //         IF      WK-PUTFILE     =        SPACES
        //          GO TO  0000-MAIN-EXIT
        //         ELSE
        List<String> uputhFileContents = textFile.readFileContent(fileUputhPath, CHARSET);
        if (Objects.isNull(uputhFileContents) || uputhFileContents.isEmpty()) {
            ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "uputhFileContents is null");
            return;
        }

        for (String uputhFileContent : uputhFileContents) {
            text2VoFormatter.format(uputhFileContent, fileUputh);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "fileUputh = {}", fileUputh);

            // UPUTH-PUTFILE(4:1) NOT ="3" AND NOT ="4" AND NOT ="5" 跳掉，不處理
            //   IF UPUTH-PUTFILE(4:1) NOT ="3" AND NOT ="4" AND NOT ="5" THEN
            //   GO TO  0000-MAIN-RTN.

            // PUTFILE = PUTTYPE 9(2) + PUTNAME X(8)
            String putfile = formatUtil.pad9(fileUputh.getPuttype(), 2) + fileUputh.getPutname();
            putfile = formatUtil.padX(putfile, 10);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "putfile = {}", putfile);
            String putfile4 = putfile.substring(3, 4);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "putfile4 = {}", putfile4);
            if (!putfile4.equals("3") && !putfile4.equals("4") && !putfile4.equals("5")) {
                continue;
            }

            // 媒體種類+媒體檔名 相同時：
            //   代收類別 不同時：A.寫PUTFN(CTL=22) B.清變數值 C.寫PUTFCTL2
            //   代收類別 相同時，往下一步驟
            // 媒體種類+媒體檔名 不同時：
            //   WK-PUTFILE=SPACES 表處理第一筆資料， 執行2000-CNGFNAME-RTN
            //   其他，A.寫PUTFN(CTL=22) B.關PUTFN C.寫PUTFCTL2 D.寫PUTFCTL E.清變數值 F.執行2000-CNGFNAME-RTN

            //     IF          UPUTH-PUTFILE       =      WK-PUTFILE
            //       IF        UPUTH-CODE          NOT =  WK-CODE
            //         PERFORM 3000-WPUTF2-RTN     THRU   3000-WPUTF2-EXIT
            //         MOVE    0                   TO     WK-TOTCNT, WK-TOTAMT
            //         PERFORM 3200-WPUTFCTL2-RTN  THRU   3200-WPUTFCTL2-EXIT
            //       ELSE
            //         NEXT    SENTENCE
            //     ELSE
            //       IF        WK-PUTFILE          =      SPACES
            //         PERFORM 2000-CNGFNAME-RTN   THRU   2000-CNGFNAME-EXIT
            //       ELSE
            //         PERFORM 3000-WPUTF2-RTN     THRU   3000-WPUTF2-EXIT
            //         CLOSE   FD-PUTFN  WITH  SAVE
            //         PERFORM 3200-WPUTFCTL2-RTN  THRU   3200-WPUTFCTL2-EXIT
            //         PERFORM 3100-WPUTFCTL-RTN   THRU   3100-WPUTFCTL-EXIT
            //         MOVE    0                   TO     WK-TOTCNT ,WK-TOTAMT
            //         PERFORM 2000-CNGFNAME-RTN   THRU   2000-CNGFNAME-EXIT.
            if (putfile.equals(wkPutfile)) {
                // 若此筆收付類別與前一筆不同
                if (!fileUputh.getCode().equals(wkCode)) {
                    if (!putfnFileContents.isEmpty()) {
                        textFile.writeFileContent(wkPutdir, putfnFileContents, CHARSET);
                        upload(wkPutdir, "DATA", "PUTFN");
                        putfnFileContents = new ArrayList<>();
                    }
                    // 寫彙計資料
                    wputf2();
                    // 寫控制2檔
                    wputfctl2();
                    // 清彙計變數
                    wkTotcnt = 0;
                    wkTotamt = BigDecimal.ZERO;
                } else {
                    // NEXT    SENTENCE
                    // java 沒有類似的語法 就是繼續往下
                }
            } else { // 若媒體檔名不同
                // 若這是第一筆
                if (Objects.isNull(wkPutfile) || wkPutfile.trim().isEmpty()) {
                    // 變更檔名
                    changeFileName();
                } else { // 若這不是第一筆
                    if (!putfnFileContents.isEmpty()) {
                        textFile.writeFileContent(wkPutdir, putfnFileContents, CHARSET);
                        upload(wkPutdir, "DATA", "PUTFN");
                        putfnFileContents = new ArrayList<>();
                    }
                    // 寫彙計資料
                    wputf2();
                    // 寫控制2檔
                    wputfctl2();
                    // 寫控制檔
                    wputfctl();
                    // 清彙計變數
                    wkTotcnt = 0;
                    wkTotamt = BigDecimal.ZERO;
                    // 變更檔名
                    changeFileName();
                }
            }

            // 抄檔(UPUTH->PUTFN)
            // 保留PUTFILE、CODE
            // 彙總金額、筆數
            //     PERFORM     4000-WPUTF1-RTN     THRU   4000-WPUTF1-EXIT.
            //     MOVE        UPUTH-PUTFILE       TO   WK-PUTFILE.
            //     MOVE        UPUTH-CODE          TO   WK-CODE.
            //     ADD         UPUTH-AMT           TO   WK-TOTAMT.
            //     ADD         1                   TO   WK-TOTCNT.
            wputf1();
            wkPutfile = formatUtil.pad9(fileUputh.getPuttype(), 2) + fileUputh.getPutname();
            wkPutfile = formatUtil.padX(wkPutfile, 10);
            wkCode = fileUputh.getCode();
            String tempAmtString = fileUputh.getAmt();
            BigDecimal tempAmt = BigDecimal.ZERO;
            if (parse.isNumeric(tempAmtString)) {
                tempAmt = parse.string2BigDecimal(fileUputh.getAmt());
            }
            wkTotamt = wkTotamt.add(tempAmt);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkTotamt = {}", wkTotamt);
            wkTotcnt++;
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkTotcnt = {}", wkTotcnt);

            // LOOP讀下一筆
            //     GO TO       0000-MAIN-RTN.
            // 0000-MAIN-EXIT.
            //     EXIT.
        }

        // 讀檔結束之處理
        //  A.寫PUTFN(CTL=22)
        //  B.寫PUTFCTL2
        //  C.寫PUTFCTL
        //  D.關PUTFN
        //  E.結束本段落
        //          PERFORM 3000-WPUTF2-RTN    THRU 3000-WPUTF2-EXIT
        //          PERFORM 3200-WPUTFCTL2-RTN THRU 3200-WPUTFCTL2-EXIT
        //          PERFORM 3100-WPUTFCTL-RTN  THRU 3100-WPUTFCTL-EXIT
        //          CLOSE   FD-PUTFN    WITH SAVE

        //          GO TO   0000-MAIN-EXIT
        //         END-IF.
    }

    // 4000-WPUTF1-RTN
    private void wputf1() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wputf1()");
        // FD-UPUTH -> FD-PUTFN，寫檔FD-PUTFN(明細)，PUTFN-CTL=21
        //     MOVE      SPACES            TO      PUTFN-REC.
        //     MOVE      21                TO      PUTFN-CTL.
        //     MOVE      UPUTH-CODE        TO      PUTFN-CODE.
        //     MOVE      UPUTH-RCPTID      TO      PUTFN-RCPTID.
        //     MOVE      UPUTH-DATE        TO      PUTFN-DATE.
        //     MOVE      UPUTH-TIME        TO      PUTFN-TIME.
        //     MOVE      UPUTH-AMT         TO      PUTFN-AMT,PUTFN-OLDAMT.
        //     MOVE      UPUTH-CLLBR       TO      PUTFN-CLLBR.
        //     MOVE      UPUTH-LMTDATE     TO      PUTFN-LMTDATE.
        //     MOVE      UPUTH-USERDATA    TO      PUTFN-USERDATA.
        //     MOVE      UPUTH-SITDATE     TO      PUTFN-SITDATE.
        //     MOVE      UPUTH-TXTYPE      TO      PUTFN-TXTYPE.
        filePutfn = null;
        filePutfn = new FilePUTFN();
        filePutfn.setCtl("21");
        filePutfn.setCode(fileUputh.getCode());
        filePutfn.setRcptid(fileUputh.getRcptid().substring(0, 16));
        filePutfn.setEntdy(fileUputh.getEntdy());
        filePutfn.setTime(fileUputh.getTime());
        filePutfn.setOldamt(fileUputh.getAmt());
        filePutfn.setAmt(fileUputh.getAmt());
        filePutfn.setCllbr(fileUputh.getCllbr());
        filePutfn.setLmtdate(fileUputh.getLmtdate());
        filePutfn.setUserdata(fileUputh.getUserdata());
        filePutfn.setSitdate(fileUputh.getSitdate());
        filePutfn.setTxtype(fileUputh.getTxtype());
        //     WRITE     PUTFN-REC.
        putfnFileContents.add(vo2TextFormatter.formatRS(filePutfn, false));

        if (putfnFileContents.size() >= 100000) {
            textFile.writeFileContent(wkPutdir, putfnFileContents, CHARSET);
            upload(wkPutdir, "DATA", "PUTFN");
            putfnFileContents = new ArrayList<>();
        }

        // 4000-WPUTF1-EXIT.
        //    EXIT.
    }

    // 2000-CNGFNAME-RTN
    private void changeFileName() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "changeFileName()");
        // 設定FD-PUTFN檔名，並開啟
        //     MOVE        WK-YYMMDD           TO   WK-FDATE.
        //     MOVE        UPUTH-PUTFILE       TO   WK-PUTFILE.
        int wkFdate = wkYymmdd % 1000000;
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "fileUputh.getPuttype() = {}",
                fileUputh.getPuttype());
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "fileUputh.getPutname() = {}",
                fileUputh.getPutname());
        wkPutfile = formatUtil.pad9(fileUputh.getPuttype(), 2) + fileUputh.getPutname();
        wkPutfile = formatUtil.padX(wkPutfile, 10);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkFdate = {}", wkFdate);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkPutfile = {}", wkPutfile);

        // WK-PUTDIR = <-"DATA/CL/BH/PUTFN/"+WK-FDATE"/"+WK-PUTFILE
        // 檔案存在，OPEN EXTEND、WK-RTN=0；否則OPEN OUTPUT->CLOSE->EXTEND、WK-RTN=1
        //     CHANGE  ATTRIBUTE FILENAME OF FD-PUTFN TO WK-PUTDIR.
        //     IF  ATTRIBUTE  RESIDENT  OF  FD-PUTFN  =   VALUE(TRUE)
        //      OPEN  EXTEND  FD-PUTFN
        //      MOVE  0       TO       WK-RTN
        //     ELSE
        //      OPEN  OUTPUT  FD-PUTFN
        // *---AFTER CLOSE THEN OPEN EXTEND BECAUE C74 CAN'T HANDLE AFTER
        // *---OPEN EXTEND THEN CLOSE AND THEN OPEN OUTPUT ---
        //      CLOSE FD-PUTFN WITH SAVE
        //      OPEN  EXTEND  FD-PUTFN
        //      MOVE  1       TO       WK-RTN.
        wkPutdir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + wkFdate
                        + PATH_SEPARATOR
                        + "PUTFN"
                        + PATH_SEPARATOR
                        + wkFdate
                        + PATH_SEPARATOR
                        + wkPutfile;
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "fileDir = {}", fileDir);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkPutdir = {}", wkPutdir);
        if (textFile.exists(wkPutdir)) {
            wkRtn = 0;
        } else {
            wkRtn = 1;
        }
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkRtn = {}", wkRtn);

        // 2000-CNGFNAME-EXIT.
        //     EXIT.
    }

    // 3100-WPUTFCTL-RTN
    private void wputfctl() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wputfctl()");
        // FD-PUTFN("DATA/CL/BH/PUTFN/"+WK-FDATE+"/"+WK-PUTFILE)為新檔時
        // 寫檔FD-PUTFCTL，WK-PUTFILE
        //     IF   WK-RTN             =       1
        //      MOVE LOW-VALUE         TO      PUTFCTL-REC
        //      MOVE WK-PUTFILE        TO      PUTFCTL-PUTFILE
        //      MOVE WK-YYMMDD         TO      PUTFCTL-GENDT
        //      MOVE 0                 TO      PUTFCTL-TREAT
        //      MOVE WK-PUTADDR        TO      PUTFCTL-PUTADDR
        //      MOVE WK-PBRNO          TO      PUTFCTL-PBRNO
        //      WRITE PUTFCTL-REC INVALID KEY DISPLAY "WRITE PUTFCTL ERROR"
        //            PUTFCTL-STATUS.
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkRtn = {}", wkRtn);
        if (wkRtn == 1) {
            filePUTFCTL = new FilePUTFCTL();
            filePUTFCTL.setPuttype(wkPutfile.substring(0, 2));
            filePUTFCTL.setPutname(wkPutfile.substring(2));
            filePUTFCTL.setGendt("" + wkYymmdd);
            filePUTFCTL.setPutaddr(wkPutaddr);
            filePUTFCTL.setPbrno("" + wkPbrno);
            putfctlFileContents.add(vo2TextFormatter.formatRS(filePUTFCTL, false));
            textFile.writeFileContent(filePutfctlPath, putfctlFileContents, CHARSET);
            upload(wkPutdir, "DATA", "");
            putfctlFileContents = new ArrayList<>();
        }

        // 3100-WPUTFCTL-EXIT.
        //     EXIT.
    }

    // 3200-WPUTFCTL2-RTN
    private void wputfctl2() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wputfctl2()");
        // 寫檔FD-PUTFCTL2，WK-PUTFILE,PUTFN-CODE代收類別、PUTFN-BDATE、PUTFN-EDATE
        //      MOVE LOW-VALUE         TO      PUTFCTL2-REC.
        //      MOVE WK-PUTFILE        TO      PUTFCTL2-PUTFILE.
        //      MOVE PUTFN-CODE         TO      PUTFCTL2-CODE.
        //      MOVE PUTFN-BDATE        TO      PUTFCTL2-BDATE
        //      MOVE PUTFN-EDATE        TO      PUTFCTL2-EDATE.
        //      WRITE PUTFCTL2-REC.
        filePUTFCTL2 = new FilePUTFCTL2();
        filePUTFCTL2.setPuttype(wkPutfile.substring(0, 2));
        filePUTFCTL2.setPutname(wkPutfile.substring(2));
        filePUTFCTL2.setCode(fileSumPUTFN.getCode());
        filePUTFCTL2.setBdate(fileSumPUTFN.getBdate());
        filePUTFCTL2.setEdate(fileSumPUTFN.getEdate());
        putfctl2FileContents.add(vo2TextFormatter.formatRS(filePUTFCTL2, false));
        textFile.writeFileContent(filePutfctl2Path, putfctl2FileContents, CHARSET);
        upload(wkPutdir, "DATA", "");
        putfctl2FileContents = new ArrayList<>();

        // 3200-WPUTFCTL2-EXIT.
        //     EXIT.
    }

    // 3000-WPUTF2-RTN
    private void wputf2() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wputf2()");
        // 寫檔FD-PUTFN(每一CODE寫一筆)，PUTFN-CTL=22,代收類別、總金額、總筆數、挑檔起迄日
        //     MOVE SPACES             TO      PUTFN-REC.
        //     PERFORM   5000-FINDCLMR-RTN THRU 5000-FINDCLMR-EXIT.
        //     MOVE 22                 TO      PUTFN-CTL.
        //     MOVE WK-CODE            TO      PUTFN-CODE.
        //     MOVE WK-TOTCNT          TO      PUTFN-TOTCNT.
        //     MOVE WK-TOTAMT          TO      PUTFN-TOTAMT.
        //     WRITE PUTFN-REC.

        fileSumPUTFN = new FileSumPUTFN();
        findClmr();
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkCode = {}", wkCode);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkTotcnt = {}", wkTotcnt);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkTotamt = {}", wkTotamt);
        fileSumPUTFN.setCtl("22");
        fileSumPUTFN.setCode(wkCode);
        fileSumPUTFN.setTotcnt("" + wkTotcnt);
        fileSumPUTFN.setTotamt(parse.decimal2String(wkTotamt, 13, 0));
        putfnFileContents.add(vo2TextFormatter.formatRS(fileSumPUTFN, false));
        textFile.writeFileContent(wkPutdir, putfnFileContents, CHARSET);
        upload(wkPutdir, "DATA", "PUTFN");
        putfnFileContents = new ArrayList<>();

        // 3000-WPUTF2-EXIT.
        //     EXIT.
    }

    // 5000-FINDCLMR-RTN
    private void findClmr() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "findClmr()");
        // 依代收類別讀取事業單位基本資料檔，若有誤，異常，結束程式
        //     FIND      DB-CLMR-IDX1      AT  DB-CLMR-CODE=WK-CODE
        //      ON EXCEPTION  IF DMSTATUS(NOTFOUND)
        //                       DISPLAY "FINDCLMR NOT FOUND" WK-CODE
        //                       CALL SYSTEM DMTERMINATE
        //                    ELSE
        //                       CALL SYSTEM DMTERMINATE.
        ClmrBus clmr = clmrService.findById(wkCode);
        cltmr = cltmrService.findById(wkCode);
        ClmcBus clmc;
        if (!Objects.isNull(cltmr)) {
            clmc = clmcService.findById(cltmr.getPutname());
        } else {
            clmc = null;
        }

        // 找上上次CYC2挑檔日之次日
        //     PERFORM   6000-FINDBDATE-RTN THRU   6000-FINDBDATE-EXIT.
        //     MOVE      FD-CLNDR-TBSDY    TO      PUTFN-BDATE.
        int bDate = findbdate();
        fileSumPUTFN.setBdate("" + bDate);

        // DB-CLMR-ULPUTDT上次CYC2挑檔日(CREATE 時放上營業日)
        //     MOVE      DB-CLMR-ULPUTDT   TO      PUTFN-EDATE.
        fileSumPUTFN.setEdate(cltmr == null ? "" : "" + cltmr.getUlputdt());

        // FD-PUTFN("DATA/CL/BH/PUTFN/"+WK-FDATE+"/"+WK-PUTFILE)為新檔時
        // DB-CLMR-PBRNO	主辦分行
        // DB-CLMR-PUTADDR媒體給付住址，使用EMAIL傳送銷帳媒體者
        //     IF        WK-RTN            =       1
        //      MOVE     DB-CLMR-PBRNO     TO      WK-PBRNO
        //      MOVE     DB-CLMR-PUTADDR   TO      WK-PUTADDR.
        if (wkRtn == 1) {
            wkPutaddr = clmc == null ? "" : clmc.getPutaddr();
            wkPbrno = clmr == null ? 0 : clmr.getPbrno();
        }
        // 5000-FINDCLMR-EXIT.
        //     EXIT.
    }

    // 6000-FINDBDATE-RTN
    private int findbdate() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "findbdate()");
        // DB-CLMR-ULLPUTDT上上次CYC2挑檔日(CREATE 時放上營業日)
        // 找DB-CLMR-ULLPUTDT之次日
        //     MOVE    1                   TO     WK-CLNDR-KEY.
        // 6000-LOOP.
        //     READ FD-CLNDR INVALID KEY
        //          PERFORM 7000-CLNDR-FIRST-RTN THRU 7000-CLNDR-FIRST-EXIT
        //          GO TO 6000-FINDBDATE-EXIT.
        //     IF      FD-CLNDR-TBSDY      NOT =  DB-CLMR-ULLPUTDT
        //      ADD    1                   TO     WK-CLNDR-KEY
        //      GO TO  6000-LOOP
        //     ELSE
        //      ADD    1                   TO     WK-CLNDR-KEY
        //      READ FD-CLNDR INVALID KEY
        //          PERFORM 7000-CLNDR-FIRST-RTN THRU 7000-CLNDR-FIRST-EXIT.
        // 6000-FINDBDATE-EXIT.
        //     EXIT.
        // 7000-CLNDR-FIRST-RTN.
        //      MOVE   2                   TO     WK-CLNDR-KEY.
        //      READ FD-CLNDR INVALID KEY DISPLAY "INVALID KEY" DB-CLMR-CODE
        //          , WK-CLNDR-STUS CHANGE ATTRIBUTE TASKVALUE OF MYSELF TO
        //     -1
        // 異常，顯示錯誤訊息，輸出-1，結束程式
        //          GO TO 0000-END-RTN.
        // 7000-CLNDR-FIRST-EXIT.
        //     EXIT.
        if (Objects.isNull(cltmr) || cltmr.getUllputdt() == 0) {
            ApLogHelper.error(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "findbdate error, cltmr is null or cltmr.getUllputdt() is 0");
            //            throw new LogicException("GE000", "findbdate error cltmr is null or
            // cltmr.getLlputdt() is 0");
            return 0;
        }
        int ullputdt = cltmr.getUllputdt();
        DateDto dateDto = new DateDto();
        dateDto.setDateS(ullputdt);
        dateDto.setDays(1);
        dateUtil.getCalenderDay(dateDto);
        return dateDto.getDateE2Integer(false);
    }

    private void batchResponse(Uputfn event) {
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
}
