/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.Putf;
import com.bot.ncl.dto.entities.ClmcBus;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.dto.entities.CltmrBus;
import com.bot.ncl.jpa.svc.ClmcService;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.ncl.jpa.svc.CltmrService;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.batch.BatchUtil;
import com.bot.ncl.util.fileVo.*;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.buffer.mg.ThreadVariable;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.eum.TxCharsets;
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
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("PutfLsnr")
@Scope("prototype")
public class PutfLsnr extends BatchListenerCase<Putf> {
    @Autowired private ClmrService clmrService;
    @Autowired private CltmrService cltmrService;
    @Autowired private ClmcService clmcService;
    @Autowired private FsapBatchUtil fsapBatchUtil;

    @Autowired private TextFileUtil textFile;

    @Autowired private FormatUtil formatUtil;

    @Autowired private BatchUtil batchUtil;

    @Autowired private Parse parse;

    @Autowired private DateUtil dateUtil;

    @Autowired private Text2VoFormatter text2VoFormatter;

    @Autowired private Vo2TextFormatter vo2TextFormatter;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    @Autowired private FilePUTH filePuth;

    @Autowired private FilePUTF filePutf;

    @Autowired private FileSumPUTF fileSumPUTF;

    @Autowired private FileNoDataPUTF fileNoDataPUTF;

    @Autowired private FilePUTFCTL2 filePUTFCTL2;

    @Autowired private FilePUTFCTL filePUTFCTL;
    private static final String CONVF_DATA = "DATA";

    // WORKING-STORAGE  SECTION.
    // 01 WK-CLNDR-KEY                       PIC 9(03).
    // 01 WK-CLNDR-STUS                      PIC X(02).
    // 01 PUTFCTL-STATUS                     PIC X(02).
    // 01 WK-YYMMDD                          PIC 9(06).
    private int processDate;
    private String tbsdy;
    // 01 WK-PUTADDR                         PIC X(40).
    private String wkPutaddr;
    // 01 WK-PBRNO                           PIC 9(03).
    private int wkPbrno;
    // 01 WK-TOTCNT                          PIC 9(06).
    private int wkTotcnt;
    // 01 WK-TOTAMT                          PIC 9(13).
    private BigDecimal wkTotamt;
    // 01 WK-CODE                            PIC X(06).
    private String wkCode = "";
    // 01 WK-RCPTID.
    //    03 WK-RCPTID-1                     PIC X(01).
    //    03 WK-RCPTID-7                     PIC X(07).
    //    03 WK-RCPTID-8                     PIC X(08).
    private String wkRcptid;

    private String getWkRcptid7() {
        wkRcptid = formatUtil.padX(wkRcptid, 16);
        return wkRcptid.substring(0, 7);
    }

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
    private String wkPutfile = "";
    private String filePuthPath;
    private String filePutfctl2Path;
    private String filePutfctlPath;

    private static final String CHARSET = "UTF-8";

    private static final String PATH_SEPARATOR = File.separator;
    private Map<String, String> labelMap;

    private List<String> putfFileContents;
    private List<String> putfctl2FileContents;
    private List<String> putfctlFileContents;

    private CltmrBus cltmr;

    @Override
    public void onApplicationEvent(Putf event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "PutfLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Putf event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "PutfLsnr run()");

        init(event);

        // 0000-MAIN-RTN
        readFilePuthAndWriteFilePutf();

        batchResponse(event);
    }

    private void init(Putf event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "init()");

        // 讀批次日期檔，設定本營業日變數值；若讀不到，結束程式
        //     READ    FD-BHDATE AT END DISPLAY "READ FD-BHDATE ERROR"
        //          STOP RUN.
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();

        //     MOVE    FD-BHDATE-TBSDY TO     WK-YYMMDD.
        processDate = parse.string2Integer(labelMap.get("PROCESS_DATE")); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        String puthDir = fileDir + "DATA" + File.separator + processDate;
        filePuthPath = puthDir + File.separator + "PUTH";
        textFile.deleteFile(filePuthPath);
        String sourceFtpPath =
                "NCL"
                        + File.separator
                        + tbsdy
                        + File.separator
                        + "2FSAP"
                        + File.separator
                        + "DATA"
                        + File.separator
                        + "PUTH"; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, puthDir);
        if (sourceFile != null) {
            filePuthPath = getLocalPath(sourceFile);
        }

        filePutfctl2Path =
                fileDir + CONVF_DATA + PATH_SEPARATOR + processDate + PATH_SEPARATOR + "PUTFCTL2";
        filePutfctlPath =
                fileDir + CONVF_DATA + PATH_SEPARATOR + processDate + PATH_SEPARATOR + "PUTFCTL";

        putfFileContents = new ArrayList<>();
        putfctl2FileContents = new ArrayList<>();
        putfctlFileContents = new ArrayList<>();

        // REMOVE  DATA/CL/BH/PUTF/=  ;
        textFile.deleteDir(
                fileDir + "DATA" + File.separator + processDate + File.separator + "PUTF");

        textFile.deleteFile(filePutfctl2Path);
        textFile.deleteFile(filePutfctlPath);

        wkTotcnt = 0;
        wkTotamt = BigDecimal.ZERO;
    }

    // 0000-MAIN-RTN
    private void readFilePuthAndWriteFilePutf() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "readFilePuthAndWriteFilePutf()");

        // 循序讀取"DATA/CL/BH/PUTH"
        //     READ    FD-PUTH        AT  END
        // WK-PUTFILE=SPACES，表示FD-PUTH無資料，結束本段落
        //         IF      WK-PUTFILE     =        SPACES
        //          GO TO  0000-MAIN-EXIT
        //         ELSE
        List<String> puthFileContents = textFile.readFileContent(filePuthPath, CHARSET);
        if (Objects.isNull(puthFileContents) || puthFileContents.isEmpty()) {
            ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "puthFileContent is null");
            return;
        }

        Date startTime = new Date();
        for (String puthFileContent : puthFileContents) {
            ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境
            text2VoFormatter.format(puthFileContent, filePuth);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "filePuth = {}", filePuth);

            // UPUTH-PUTFILE(4:1) ="3" OR  ="4" OR ="5"跳掉，不處理
            //     IF PUTH-PUTFILE(4:1) ="3" OR ="4" OR ="5" THEN
            //     GO TO  0000-MAIN-RTN.

            // PUTFILE = PUTTYPE 9(2) + PUTNAME X(8)
            String putfile = formatUtil.pad9(filePuth.getPuttype(), 2) + filePuth.getPutname();
            putfile = formatUtil.padX(putfile, 10);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "putfile = {}", putfile);
            String putfile4 = putfile.substring(3, 4);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "putfile4 = {}", putfile4);
            if (putfile4.equals("3") || putfile4.equals("4") || putfile4.equals("5")) {
                continue;
            }

            // 媒體種類+媒體檔名 相同時：
            //   代收類別 不同時：A.寫PUTF(CTL=12) B.寫PUTFCTL2 C.清變數值
            //   代收類別 相同時，往下一步驟
            // 媒體種類+媒體檔名 不同時：
            //   WK-PUTFILE=SPACES 表處理第一筆資料， 執行2000-CNGFNAME-RTN
            //   其他，A.寫PUTF(CTL=12) B.關PUTF C.寫PUTFCTL2 D.寫PUTFCTL E.清變數值 F.執行2000-CNGFNAME-RTN
            //     IF          PUTH-PUTFILE        =      WK-PUTFILE
            //       IF        PUTH-CODE           NOT =  WK-CODE
            //         PERFORM 3000-WPUTF2-RTN     THRU   3000-WPUTF2-EXIT
            //         PERFORM 3200-WPUTFCTL2-RTN  THRU   3200-WPUTFCTL2-EXIT
            //         MOVE    0                   TO     WK-TOTCNT, WK-TOTAMT
            //       ELSE
            //         NEXT    SENTENCE
            //     ELSE
            //       IF        WK-PUTFILE          =      SPACES
            //         PERFORM 2000-CNGFNAME-RTN   THRU   2000-CNGFNAME-EXIT
            //       ELSE
            //         PERFORM 3000-WPUTF2-RTN     THRU   3000-WPUTF2-EXIT
            //         CLOSE   FD-PUTF  WITH  SAVE
            //         PERFORM 3200-WPUTFCTL2-RTN  THRU   3200-WPUTFCTL2-EXIT
            //         PERFORM 3100-WPUTFCTL-RTN   THRU   3100-WPUTFCTL-EXIT
            //         MOVE    0                   TO     WK-TOTCNT ,WK-TOTAMT
            //         PERFORM 2000-CNGFNAME-RTN   THRU   2000-CNGFNAME-EXIT.
            // 若媒體檔名相同
            if (putfile.equals(wkPutfile)) {
                // 若此筆收付類別與前一筆不同
                if (!filePuth.getCode().equals(wkCode)) {
                    if (!putfFileContents.isEmpty()) {
                        textFile.writeFileContent(wkPutdir, putfFileContents, CHARSET);
                        upload(wkPutdir, "DATA", "PUTF");
                        putfFileContents = new ArrayList<>();
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
                    if (!putfFileContents.isEmpty()) {
                        textFile.writeFileContent(wkPutdir, putfFileContents, CHARSET);
                        upload(wkPutdir, "DATA", "PUTF");
                        putfFileContents = new ArrayList<>();
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

            // 抄檔(PUTH->PUTF)
            // 保留PUTFILE、CODE
            // 彙總金額、筆數
            //     PERFORM     4000-WPUTF1-RTN     THRU   4000-WPUTF1-EXIT.
            //     MOVE        PUTH-PUTFILE        TO     WK-PUTFILE.
            //     MOVE        PUTH-CODE           TO     WK-CODE.
            //     ADD         PUTH-AMT            TO     WK-TOTAMT.
            //     ADD         1                   TO     WK-TOTCNT.
            wputf1();
            wkPutfile = formatUtil.pad9(filePuth.getPuttype(), 2) + filePuth.getPutname();
            wkPutfile = formatUtil.padX(wkPutfile, 10);
            wkCode = filePuth.getCode();
            String tempAmtString = filePuth.getAmt();
            BigDecimal tempAmt = BigDecimal.ZERO;
            if (parse.isNumeric(tempAmtString)) {
                tempAmt = parse.string2BigDecimal(filePuth.getAmt());
            }
            wkTotamt = wkTotamt.add(tempAmt);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkTotamt = {}", wkTotamt);
            wkTotcnt++;
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkTotcnt = {}", wkTotcnt);

            // LOOP讀下一筆
            //     GO TO       0000-MAIN-RTN.
            startTime = batchUtil.refreshBatchTransaction(this.batchTransaction, startTime);
        }
        if (wkPutfile.isEmpty()) return;
        // 讀檔結束之處理
        //  A.寫PUTF(CTL=12)
        //  B.寫PUTFCTL2
        //  C.寫PUTFCTL
        //  D.關PUTF
        //  E.結束本段落
        //          PERFORM 3000-WPUTF2-RTN    THRU 3000-WPUTF2-EXIT
        //          PERFORM 3200-WPUTFCTL2-RTN THRU 3200-WPUTFCTL2-EXIT
        //          PERFORM 3100-WPUTFCTL-RTN  THRU 3100-WPUTFCTL-EXIT
        //          CLOSE   FD-PUTF   WITH SAVE
        //          GO TO   0000-MAIN-EXIT
        //         END-IF.
        wputf2();
        wputfctl2();
        wputfctl();

        // 0000-MAIN-EXIT.
        //     EXIT.
    }

    // 4000-WPUTF1-RTN
    private void wputf1() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wputf1()");
        // FD-PUTH -> FD-PUTF，寫檔FD-PUTF(明細)，PUTF-CTL=11
        //     MOVE      SPACES            TO      PUTF-REC.
        //     MOVE      11                TO      PUTF-CTL.
        //     MOVE      PUTH-CODE         TO      PUTF-CODE.
        //     MOVE      PUTH-RCPTID       TO      WK-RCPTID.
        wkRcptid = filePuth.getRcptid().trim();
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkRcptid = {}", wkRcptid);
        //     IF WK-RCPTID-7  = "NO DATA"
        //        MOVE PUTH-RCPTID TO PUTF-NODATA
        //        MOVE SPACES      TO PUTF-FILLER1
        //     ELSE
        //        MOVE      PUTH-RCPTID(1:16) TO      PUTF-RCPTID
        //        MOVE      PUTH-DATE         TO      PUTF-DATE
        //        MOVE      PUTH-TIME         TO      PUTF-TIME
        //        MOVE      PUTH-AMT          TO      PUTF-AMT,PUTF-OLDAMT
        //        MOVE      PUTH-CLLBR        TO      PUTF-CLLBR
        //        MOVE      PUTH-LMTDATE      TO      PUTF-LMTDATE
        //        MOVE      PUTH-USERDATA     TO      PUTF-USERDATA
        //        MOVE      PUTH-SITDATE      TO      PUTF-SITDATE
        //        MOVE      PUTH-TXTYPE       TO      PUTF-TXTYPE.
        fileNoDataPUTF = null;
        filePutf = null;
        if (getWkRcptid7().equals("NO DATA")) {
            fileNoDataPUTF = new FileNoDataPUTF();
            fileNoDataPUTF.setCtl("11");
            fileNoDataPUTF.setCode(filePuth.getCode());
            fileNoDataPUTF.setNodata(filePuth.getRcptid());
            fileNoDataPUTF.setFiller("");
        } else {
            filePutf = new FilePUTF();
            filePutf.setCtl("11");
            filePutf.setCode(filePuth.getCode());
            filePutf.setRcptid(filePuth.getRcptid().substring(0, 16));
            filePutf.setEntdy(filePuth.getEntdy());
            filePutf.setTime(filePuth.getTime());
            filePutf.setOldamt(filePuth.getAmt());
            filePutf.setAmt(filePuth.getAmt());
            filePutf.setCllbr(filePuth.getCllbr());
            filePutf.setLmtdate(filePuth.getLmtdate());

            String usetdata = filePuth.getUserdata();
            filePutf.setUserdata(usetdata);
            filePutf.setSitdate(filePuth.getSitdate());
            filePutf.setTxtype(filePuth.getTxtype());
        }
        //     IF        PUTH-PUTNAME      =       "X2011201"
        //        MOVE   PUTH-SERINO       TO      WK-CPC-SERINO
        //        MOVE   WK-CPC            TO      PUTF-FILLER.
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "filePuth.getPutname() = {}",
                filePuth.getPutname());
        if (filePuth.getPutname().equals("X2011201")) {
            ApLogHelper.info(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "filePuth.getSerino() = {}",
                    filePuth.getSerino());
            wkCpc = formatUtil.pad9(filePuth.getSerino(), 6);
            wkCpc = formatUtil.padX(wkCpc, 10);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkCpc = {}", wkCpc);
        }
        //     WRITE     PUTF-REC.
        if (!Objects.isNull(fileNoDataPUTF)) {
            putfFileContents.add(vo2TextFormatter.formatRS(fileNoDataPUTF, false));
        } else {
            putfFileContents.add(vo2TextFormatter.formatRS(filePutf, false));
        }
        if (putfFileContents.size() >= 100000) {
            textFile.writeFileContent(wkPutdir, putfFileContents, CHARSET);
            upload(wkPutdir, "DATA", "PUTF");
            putfFileContents = new ArrayList<>();
        }

        // 4000-WPUTF1-EXIT.
        //    EXIT.
    }

    // 2000-CNGFNAME-RTN
    private void changeFileName() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "changeFileName()");

        // 設定FD-PUTF檔名，並開啟
        //     MOVE        WK-YYMMDD           TO   WK-FDATE.
        //     MOVE        PUTH-PUTFILE        TO   WK-PUTFILE.
        wkFdate = processDate % 1000000;
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "filePuth.getPuttype() = {}",
                filePuth.getPuttype());
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "filePuth.getPutname() = {}",
                filePuth.getPutname());
        wkPutfile = formatUtil.pad9(filePuth.getPuttype(), 2) + filePuth.getPutname();
        wkPutfile = formatUtil.padX(wkPutfile, 10);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkFdate = {}", wkFdate);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkPutfile = {}", wkPutfile);

        // WK-PUTDIR = <-"DATA/CL/BH/PUTF/"+WK-FDATE"/"+WK-PUTFILE
        // OPEN OUTPUT
        //     CHANGE  ATTRIBUTE FILENAME OF FD-PUTF TO WK-PUTDIR.
        //     OPEN    OUTPUT    FD-PUTF.
        wkPutdir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + "PUTF"
                        + PATH_SEPARATOR
                        + wkFdate
                        + PATH_SEPARATOR
                        + wkPutfile;
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "fileDir = {}", fileDir);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkPutdir = {}", wkPutdir);

        textFile.deleteFile(wkPutdir);

        // 2000-CNGFNAME-EXIT.
        //     EXIT.
    }

    // 3000-WPUTF2-RTN
    private void wputf2() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wputf2()");
        // 寫檔FD-PUTF(每一CODE寫一筆)，PUTF-CTL=12,代收類別、總金額、總筆數、挑檔起迄日
        //     MOVE      SPACES        TO      PUTF-REC.
        //     PERFORM   5000-FINDCLMR-RTN THRU 5000-FINDCLMR-EXIT.
        //     MOVE 12                 TO      PUTF-CTL.
        //     MOVE WK-CODE            TO      PUTF-CODE.
        //     MOVE WK-TOTCNT          TO      PUTF-TOTCNT.
        //     MOVE WK-TOTAMT          TO      PUTF-TOTAMT.
        //     WRITE PUTF-REC.
        fileSumPUTF = new FileSumPUTF();
        findClmr();
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkCode = {}", wkCode);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkTotcnt = {}", wkTotcnt);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkTotamt = {}", wkTotamt);
        fileSumPUTF.setCtl("12");
        fileSumPUTF.setCode(wkCode);
        fileSumPUTF.setTotcnt("" + wkTotcnt);
        fileSumPUTF.setTotamt(parse.decimal2String(wkTotamt, 13, 0));
        putfFileContents.add(vo2TextFormatter.formatRS(fileSumPUTF, false));
        textFile.writeFileContent(wkPutdir, putfFileContents, CHARSET);
        upload(wkPutdir, "DATA", "PUTF");
        putfFileContents = new ArrayList<>();

        // 3000-WPUTF2-EXIT.
        //     EXIT.
    }

    // 3200-WPUTFCTL2-RTN
    private void wputfctl2() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wputfctf2()");
        // 寫檔FD-PUTFCTL2，WK-PUTFILE,PUTF-CODE代收類別、PUTF-BDATE、PUTF-EDATE
        //     MOVE LOW-VALUE          TO      PUTFCTL2-REC.
        //     MOVE WK-PUTFILE         TO      PUTFCTL2-PUTFILE.
        //     MOVE PUTF-CODE          TO      PUTFCTL2-CODE.
        //     MOVE PUTF-BDATE         TO      PUTFCTL2-BDATE.
        //     MOVE PUTF-EDATE         TO      PUTFCTL2-EDATE.
        //     WRITE PUTFCTL2-REC.
        filePUTFCTL2 = new FilePUTFCTL2();
        filePUTFCTL2.setPuttype(wkPutfile.substring(0, 2));
        filePUTFCTL2.setPutname(wkPutfile.substring(2));
        filePUTFCTL2.setCode(fileSumPUTF.getCode());
        filePUTFCTL2.setBdate(fileSumPUTF.getBdate());
        filePUTFCTL2.setEdate(fileSumPUTF.getEdate());
        putfctl2FileContents.add(vo2TextFormatter.formatRS(filePUTFCTL2, false));
        textFile.writeFileContent(filePutfctl2Path, putfctl2FileContents, CHARSET);
        upload(filePutfctl2Path, "DATA", "");
        putfctl2FileContents = new ArrayList<>();

        // 3200-WPUTFCTL2-EXIT.
        //     EXIT.
    }

    // 3100-WPUTFCTL-RTN
    private void wputfctl() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wputfctf2()");
        // 寫檔FD-PUTFCTL，WK-PUTFILE
        // MOVE LOW-VALUE TO PUTFCTL-REC.
        // MOVE WK-PUTFILE TO PUTFCTL-PUTFILE.
        // MOVE WK-YYMMDD TO PUTFCTL-GENDT.
        // MOVE 0 TO PUTFCTL-TREAT.
        // MOVE WK-PUTADDR TO PUTFCTL-PUTADDR.
        // MOVE WK-PBRNO TO PUTFCTL-PBRNO.
        // WRITE PUTFCTL-REC INVALID KEY DISPLAY "WRITE PUTFCTL ERROR"
        // PUTFCTL-STATUS.
        filePUTFCTL = new FilePUTFCTL();
        filePUTFCTL.setPuttype(wkPutfile.substring(0, 2));
        filePUTFCTL.setPutname(wkPutfile.substring(2));
        filePUTFCTL.setGendt("" + processDate);
        filePUTFCTL.setPutaddr(wkPutaddr);
        filePUTFCTL.setPbrno("" + wkPbrno);
        putfctlFileContents.add(vo2TextFormatter.formatRS(filePUTFCTL, false));
        textFile.writeFileContent(filePutfctlPath, putfctlFileContents, CHARSET);
        upload(filePutfctlPath, "DATA", "");
        putfctlFileContents = new ArrayList<>();

        // 3100-WPUTFCTL-EXIT.
        // EXIT.
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

        // 找上上次CYC1挑檔日之次日
        //     PERFORM   6000-FINDBDATE-RTN THRU   6000-FINDBDATE-EXIT.
        //     MOVE      FD-CLNDR-TBSDY    TO      PUTF-BDATE.
        int bDate = findbdate();
        fileSumPUTF.setBdate("" + bDate);
        // DB-CLMR-LPUTDT上次CYC1挑檔日(CREATE 時放上營業日)
        //     MOVE      DB-CLMR-LPUTDT    TO      PUTF-EDATE.
        fileSumPUTF.setEdate(cltmr == null ? "" : "" + cltmr.getLputdt());

        // DB-CLMR-PBRNO	主辦分行
        // DB-CLMR-PUTADDR媒體給付住址，使用EMAIL傳送銷帳媒體者
        //     MOVE      DB-CLMR-PUTADDR   TO      WK-PUTADDR.
        //     MOVE      DB-CLMR-PBRNO     TO      WK-PBRNO.
        wkPutaddr = clmc == null ? "" : clmc.getPutaddr();
        wkPbrno = clmr == null ? 0 : clmr.getPbrno();

        // 5000-FINDCLMR-EXIT.
        //     EXIT.
    }

    // 6000-FINDBDATE-RTN
    private int findbdate() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "findbdate()");
        // DB-CLMR-LLPUTDT上上次CYC1挑檔日(CREATE 時放上營業日)
        // 找DB-CLMR-LLPUTDT之次日
        //     MOVE    1                   TO     WK-CLNDR-KEY.
        // 6000-LOOP.
        // *    READ FD-CLNDR INVALID KEY DISPLAY "INVALID KEY" DB-CLMR-CODE
        // *         ,WK-CLNDR-STUS  SET MYSELF(TASKVALUE) TO -1
        // *         GO TO 0000-END-RTN.
        //     READ FD-CLNDR INVALID KEY
        //          PERFORM 7000-CLNDR-FIRST-RTN THRU 7000-CLNDR-FIRST-EXIT
        //          GO TO 6000-FINDBDATE-EXIT.
        //     IF      FD-CLNDR-TBSDY      NOT =  DB-CLMR-LLPUTDT
        //      ADD    1                   TO     WK-CLNDR-KEY
        //      GO TO  6000-LOOP
        //     ELSE
        //      ADD    1                   TO     WK-CLNDR-KEY
        // *     READ FD-CLNDR INVALID KEY DISPLAY "INVALID KEY" DB-CLMR-CODE
        // *         ,WK-CLNDR-STUS  SET MYSELF(TASKVALUE) TO -1
        // *          GO TO 0000-END-RTN.
        //      READ FD-CLNDR INVALID KEY
        //          PERFORM 7000-CLNDR-FIRST-RTN THRU 7000-CLNDR-FIRST-EXIT.
        // 6000-FINDBDATE-EXIT.
        //     EXIT.
        // 7000-CLNDR-FIRST-RTN.
        // ** AVOID LLPUTDT EXCEED FD-CLNDR CURRENT DATA --861218
        //     MOVE   2                    TO    WK-CLNDR-KEY.
        // 異常，顯示錯誤訊息，輸出-1，結束程式
        //     READ  FD-CLNDR INVALID KEY DISPLAY "INVALID KEY" DB-CLMR-CODE
        //          , WK-CLNDR-STUS CHANGE ATTRIBUTE TASKVALUE OF MYSELF TO
        //     -1
        //           GO TO 0000-END-RTN.
        // 7000-CLNDR-FIRST-EXIT.
        //     EXIT.
        if (Objects.isNull(cltmr) || cltmr.getLlputdt() == 0) {
            ApLogHelper.error(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "findbdate error, cltmr is null or cltmr.getLlputdt() is 0");
            //            throw new LogicException("GE000", "findbdate error cltmr is null or
            // cltmr.getLlputdt() is 0");
            return 0;
        }
        int llputdt = cltmr.getLlputdt();
        DateDto dateDto = new DateDto();
        dateDto.setDateS(llputdt);
        dateDto.setDays(1);
        dateUtil.getCalenderDay(dateDto);
        return dateDto.getDateE2Integer(false);
    }

    private void batchResponse(Putf event) {
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
}
