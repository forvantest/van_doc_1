/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import static java.math.BigDecimal.ZERO;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONV9;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FilePUTF;
import com.bot.ncl.util.fileVo.FilePUTFCTL;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.buffer.mg.ThreadVariable;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.eum.TxCharsets;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
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
@Component("CONV9Lsnr")
@Scope("prototype")
public class CONV9Lsnr extends BatchListenerCase<CONV9> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FilePUTFCTL filePUTFCTL;
    @Autowired private FilePUTF filePUTF;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private ClmrService clmrService;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    private CONV9 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CHARSET_BIG5 = "Big5";
    private static final String CONVF_PATH_PUTFCTL = "PUTFCTL"; // 讀檔目錄
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;
    private String PAGE_SEPARATOR = "\u000C";
    private StringBuilder sb = new StringBuilder();
    private String processDate;
    private String wkFileNameList = "";
    private String readFdPutfctlPath;
    private String tbsdy;
    private String wkFdate;
    private String wkPdate;
    private String wkBdname;
    private String wkBddir;
    private String file028Name = "";
    private String wkPutname;
    private String wkPutname5;
    private int wkVrcode;
    private String putfCtlPutFile = "";
    private String wkCmpmon;
    private String wkPutdir;
    private String wkCode;
    private String wkCode12;
    private String wkKind;
    private String wkKindP;
    private String wkActno9;
    private String wkActnoX;
    private String wkActnoL;
    private String wkActnoP;
    private String wkBhdateP;
    private String wkAmtP;
    private String wkBknoP;
    private String wkBknameP;
    private Map<String, String> textMap;
    private Map<String, String> labelMap;

    private String wkNetinfo;
    private String wkUserdata;
    private String wkRtmonth;
    private String wkLmndy;
    private String wkWeekstart;
    private String wkWeekend;
    private String wkEndYear;
    private String wkEndMonth;
    private String taxSdate;
    private String taxEdate;
    private String wkStartmonth;
    private String wkBdate;
    private String feeSdate;
    private String feeEdate;
    private String wkBdateP;
    private String wkEdateP;
    private String wkRtlbr;
    private String wkPutfile;
    private String wkConvfile;
    private String wkHaverpt = "N";
    private int wkPage = 1;
    private int wkPctl = 1;
    private String wkBdbrno;
    private int wkBrnoP;
    private int wkPutname3;
    private int putfCtl;
    private int wkSerino;
    private int wkSerinoP;

    private int wkSubcnt;
    private int wkSubcntR;
    private BigDecimal wkSubamtR;
    private BigDecimal wkSubamt = ZERO;
    private int wkTotcnt;
    private BigDecimal wkTotamt = ZERO;
    private int wkTotcntR;
    private BigDecimal wkTotamtR;
    private BigDecimal putfAmt = ZERO;
    private List<String> fileReportContents = new ArrayList<>();

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(CONV9 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV9Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONV9 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV9Lsnr run()");
        init(event);

        //// 執行主程式
        // 016800     PERFORM 0000-MAIN-RTN   THRU   0000-MAIN-EXIT.
        _0000_main();
        // 016800     PERFORM 0000-MAIN-RTN   THRU   0000-MAIN-EXIT.
        // 016810     IF       WK-HAVERPT       =     "N"
        if ("N".equals(wkHaverpt)) {
            //// 設定REPORTFL檔名
            // 016820     MOVE     "001"            TO   WK-BDBRNO
            // 016830     MOVE     "EMPTY"          TO   WK-BDNAME
            wkBdbrno = "001";
            wkBdname = "EMPTY";
            // 016840     CHANGE   ATTRIBUTE  FILENAME  OF   REPORTFL  TO  WK-BDDIR
            file028Name = "CL-BH-028" + "-" + wkBdbrno + "-" + wkBdname;
            wkBddir =
                    fileDir
                            + CONVF_RPT
                            + PATH_SEPARATOR
                            + processDate
                            + PATH_SEPARATOR
                            + file028Name;
            //// 開啟REPORTFL
            // 016850     OPEN     OUTPUT     REPORTFL
            //// 關閉REPORTFL
            // 016860     CLOSE    REPORTFL   WITH   SAVE.
            try {
                textFile.writeFileContent(wkBddir, fileReportContents, CHARSET_BIG5);
                upload(wkBddir, "RPT", "");
                wkFileNameList += file028Name + ",";
            } catch (LogicException e) {
                moveErrorResponse(e);
            }
        }
        batchResponse();
    }

    private void init(CONV9 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV9Lsnr init");
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        wkFdate = processDate.substring(1);
        wkPdate = processDate.substring(1);
        tbsdy = labelMap.get("PROCESS_DATE");
        String readFdDir = fileDir + CONVF_DATA + PATH_SEPARATOR + processDate;
        readFdPutfctlPath = readFdDir + PATH_SEPARATOR + CONVF_PATH_PUTFCTL;
        textFile.deleteFile(readFdPutfctlPath);
        String sourceFtpPath =
                "NCL"
                        + File.separator
                        + tbsdy
                        + File.separator
                        + "2FSAP"
                        + File.separator
                        + "DATA"
                        + File.separator
                        + CONVF_PATH_PUTFCTL; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, readFdDir);
        if (sourceFile != null) {
            readFdPutfctlPath = getLocalPath(sourceFile);
        }
    }

    private void _0000_main() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV9Lsnr _0000_main");
        // 017400 0000-MAIN-RTN.
        // 017500      OPEN   INPUT   FD-PUTFCTL.
        // 017600 0000-MAIN-LOOP.
        // 017700      READ   FD-PUTFCTL    AT  END CLOSE  FD-PUTFCTL WITH  SAVE
        // 017800                                   GO    TO  0000-MAIN-EXIT.
        if (textFile.exists(readFdPutfctlPath)) {
            List<String> lines = textFile.readFileContent(readFdPutfctlPath, CHARSET_UTF8);
            ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境
            for (String detail : lines) {
                text2VoFormatter.format(detail, filePUTFCTL);
                // 017900      MOVE        PUTFCTL-PUTNAME     TO     WK-PUTNAME.
                // 018000      IF (WK-PUTNAME3 NOT < "11011" AND WK-PUTNAME3 NOT > "11115")91/05/23
                // 018020      OR (WK-PUTNAME3 = "11331")                                  95/08/28
                // 018040      OR (WK-PUTNAME3 NOT < "11521" AND WK-PUTNAME3 NOT > "11525")95/09/13
                wkPutname = filePUTFCTL.getPutname();
                putfCtlPutFile =
                        formatUtil.pad9(filePUTFCTL.getPuttype(), 2)
                                + formatUtil.padX(filePUTFCTL.getPutname(), 8);
                wkPutname3 = parse.string2Integer(wkPutname.substring(2, 7));
                if ((wkPutname3 >= 11011 && wkPutname3 <= 11115)
                        || wkPutname3 == 11331
                        || (wkPutname3 >= 11521 && wkPutname3 <= 11525)) {
                    // 018050        MOVE      "Y"                TO      WK-HAVERPT
                    // 91/03/08
                    // 018100        PERFORM   0000-CONV-RTN     THRU     0000-CONV-EXIT
                    wkHaverpt = "Y";
                    _0000_conv();
                }
            }
            // 018200      ELSE
            // 018300        GO  TO    0000-MAIN-LOOP.
            // 018400      GO    TO    0000-MAIN-LOOP.
        }
        // 018500 0000-MAIN-EXIT.
    }

    private void _0000_conv() {
        // 018800 0000-CONV-RTN.
        // 018900   MOVE        PUTFCTL-PUTFILE     TO   WK-PUTFILE,WK-CONVFILE.
        wkPutfile = putfCtlPutFile;
        wkPutname5 = wkPutname.substring(3, 7);
        wkConvfile = putfCtlPutFile;
        // 018920   MOVE        0               TO   WK-PAGE,WK-PCTL,WK-SUBCNT,    91/03/05
        // 018940                                    WK-SUBAMT,WK-TOTCNT,WK-TOTAMT.91/03/07
        wkPage = 0;
        wkPctl = 0;
        wkSubcnt = 0;
        wkSubamt = ZERO;
        wkTotcnt = 0;
        wkTotamt = ZERO;
        // 018950   MOVE        SPACES          TO   WK-CMPMON.                    91/03/07
        wkCmpmon = "";

        // 019000   CHANGE  ATTRIBUTE FILENAME OF FD-PUTF TO WK-PUTDIR.
        // 014000 01 WK-PUTDIR.
        // 014100  03 FILLER                            PIC X(16)
        // 014200                         VALUE "DATA/CL/BH/PUTF/".
        // 014300  03 WK-FDATE                          PIC 9(06).
        // 014400  03 FILLER                            PIC X(01)
        // 014500                            VALUE "/".
        // 014600  03 WK-PUTFILE                        PIC X(10).
        // 014700  03 FILLER                            PIC X(01)
        // 014800                            VALUE ".".
        String putDir =
                fileDir
                        + "DATA"
                        + File.separator
                        + processDate
                        + File.separator
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

        // 019100   OPEN   INPUT     FD-PUTF.
        // 019200   MOVE   WK-PUTNAME5   TO   WK-VRCODE.
        if (parse.isNumeric(wkPutname5)) {
            wkVrcode = parse.string2Integer(wkPutname5);
        } else {
            wkVrcode = 0;
        }
        // 019300   FIND   DB-CLMR-IDX3  AT   DB-CLMR-VRCODE = WK-VRCODE
        // 019400       ON EXCEPTION  CLOSE  FD-PUTF WITH SAVE
        // 019500                        GO  TO  0000-CONV-EXIT.
        ClmrBus tClmr = clmrService.findFirstbyI3(wkVrcode);
        if (Objects.isNull(tClmr)) {
            return;
        }
        // 019600   MOVE   DB-CLMR-CODE  TO   WK-CODE.
        wkCode = tClmr.getCode();
        wkCode12 = wkCode.substring(4, 5);
        // 019650   PERFORM    GETKIND-RTN        THRU    GETKIND-EXIT.            91/03/05
        wkKind = getKind(wkCode12);
        // 019700   MOVE       DB-CLMR-ACTNO       TO     WK-ACTNO-9.              91/07/05
        wkActno9 = String.valueOf(tClmr.getActno());
        // 019750   MOVE       WK-ACTNO-9          TO     WK-ACTNO-X.              91/07/05
        wkActnoX = wkActno9;
        wkActnoL = formatUtil.padX(wkActnoX, 12).substring(6, 12);
        // 019800   MOVE       DB-CLMR-NETINFO     TO     WK-NETINFO.
        wkNetinfo = formatUtil.padX(tClmr.getNetinfo(), 20);
        // 019820   MOVE       DB-CLMR-PBRNO       TO     WK-BDBRNO,WK-BRNO-P.     91/03/05
        wkBdbrno = formatUtil.pad9("" + tClmr.getPbrno(), 3);
        wkBrnoP = tClmr.getPbrno();
        // 019840   MOVE       WK-PUTNAME3         TO     WK-BDNAME.               91/03/04
        wkBdname = "" + wkPutname3;
        // 019860   CHANGE     ATTRIBUTE  FILENAME  OF   REPORTFL  TO  WK-BDDIR.   91/03/04
        // 015710 01 WK-BDDIR.
        // 015720  03 FILLER                            PIC X(13)
        // 015730                         VALUE "BD/CL/BH/028/".
        // 015740  03 WK-BDBRNO                         PIC X(03).
        // 015750  03 FILLER                            PIC X(01) VALUE "/".
        // 015760  03 WK-BDNAME                         PIC X(05).
        // 015770  03 FILLER                            PIC X(01) VALUE ".".
        file028Name = "CL-BH-028" + "-" + wkBdbrno + "-" + wkBdname;
        wkBddir = fileDir + CONVF_RPT + PATH_SEPARATOR + processDate + PATH_SEPARATOR + file028Name;
        // 019880   OPEN       OUTPUT     REPORTFL.                                91/03/04
        // 019900   IF     WK-CODE12     = "1"   OR  = "6"
        if ("1".equals(wkCode12) || "6".equals(wkCode12)) {
            // 020000     PERFORM   0000-FEE-RTN     THRU     0000-FEE-EXIT
            _0000_fee();
            // 020100   ELSE
        } else {
            // 020200     PERFORM   0000-TAX-RTN     THRU     0000-TAX-EXIT.
            _0000_tax();
        }
        // 020250   CLOSE      REPORTFL   WITH   SAVE.                             91/03/04
        try {
            textFile.writeFileContent(wkBddir, fileReportContents, CHARSET_BIG5);
            upload(wkBddir, "RPT", "");
            wkFileNameList += file028Name + ",";
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        // 020300 0000-CONV-EXIT.
    }

    private void _0000_tax() {
        // 020600 0000-TAX-RTN.
        //// 設定檔名
        // 020700   CHANGE  ATTRIBUTE FILENAME OF FD-TAX  TO WK-CONVDIR.
        // 020750   MOVE       0        TO    WK-SERINO.
        wkSerino = 0;
        //// 開啟檔案
        //
        // 020800   OPEN       OUTPUT   FD-TAX.
        // 020900 0000-TAX-LOOP.

        //// 循序讀取FD-PUTF，直到檔尾，跳到0000-TAX-LAST
        //
        // 021000   READ   FD-PUTF   AT  END  GO  TO 0000-TAX-LAST.

        if (textFile.exists(wkPutdir)) {
            List<String> lines = textFile.readFileContent(wkPutdir, CHARSET_UTF8);
            ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境
            for (String detail : lines) {
                //// IF PUTF-CTL NOT  = 11,跳下一筆
                text2VoFormatter.format(detail, filePUTF);
                putfCtl =
                        parse.isNumeric(filePUTF.getCtl())
                                ? parse.string2Integer(filePUTF.getCtl())
                                : 0;

                // 021100   IF         PUTF-CTL       NOT  =      11
                if (putfCtl != 11) {
                    // 021200     GO  TO   0000-TAX-LOOP.
                    continue;
                }

                //// 搬PUTF...到TAX...

                // 021300   MOVE       SPACES              TO     TAX-REC.
                // 021500   MOVE       PUTF-USERDATA       TO     WK-USERDATA.
                wkUserdata = formatUtil.padX(filePUTF.getUserdata(), 40);
                wkRtlbr = wkUserdata.substring(0, 7);
                // 022200   PERFORM    CNVRTLBR-RTN        THRU   CNVRTLBR-EXIT.
                // 022220   IF         WK-CODE12  =  "4"  OR  =  "9"
                if ("4".equals(wkCode12) || "9".equals(wkCode12)) {
                    // 022240      MOVE    "     *"            TO     TAX-ACTNO
                    // 022260   ELSE
                } else {
                    // 022300      MOVE    WK-ACTNO-L          TO     TAX-ACTNO.
                }
                // 022400   MOVE       PUTF-DATE           TO     TAX-TXDATE.
                // 022420   IF         WK-RTMONTH      NOT  =     WK-CMPMON
                if (!wkRtmonth.trim().equals(wkCmpmon.trim())) {
                    // 022425     IF       WK-CODE12  =  "4"  OR  =  "9"  OR = "5"  OR = "0"
                    if ("4".equals(wkCode12)
                            || "9".equals(wkCode12)
                            || "5".equals(wkCode12)
                            || "0".equals(wkCode12)) {
                        // 022430        NEXT  SENTENCE
                        // 022435     ELSE
                    } else {
                        // 022440        MOVE  0                   TO     WK-SERINO.
                        wkSerino = 0;
                    }
                }
                // 022460   ADD        1                   TO     WK-SERINO.
                wkSerino++;
                // 022500   MOVE       WK-SERINO           TO     TAX-SERINO.
                // 022600   MOVE       0                   TO     TAX-KIND.
                // 022620* 高雄市地方稅稅費解繳為每週兩次　但媒體提供仍為每週一次
                // 022640* 故逢跨月時代收起迄日期不用拆成兩筆匯款
                // 022660* 起迄日期為週一至週五

                wkWeekstart = wkNetinfo.substring(0, 6);
                wkStartmonth = wkWeekstart.substring(2, 4);
                wkLmndy = wkNetinfo.substring(6, 12);
                wkWeekend = wkNetinfo.substring(12, 18);
                wkEndYear = wkWeekend.substring(0, 2);
                wkEndMonth = wkWeekend.substring(2, 4);
                // 022700   IF       ( WK-LMNDY            =      SPACES   )
                // 022720    OR      ( WK-PUTNAME3         =      "11522"  )
                // 022740    OR      ( WK-PUTNAME3         =      "11523"  )
                if (wkLmndy.trim().isEmpty() || wkPutname3 == 11522 || wkPutname3 == 11523) {
                    // 022800     MOVE     WK-WEEKSTART        TO     TAX-SDATE
                    // 022900     MOVE     WK-WEEKEND          TO     TAX-EDATE
                    taxSdate = wkWeekstart;
                    taxEdate = wkWeekend;
                    // 023000   ELSE
                } else {
                    // 023100     IF       WK-RTMONTH          =      WK-STARTMONTH
                    if (wkRtmonth.trim().equals(wkStartmonth.trim())) {
                        // 023200       MOVE   WK-WEEKSTART        TO     TAX-SDATE
                        // 023300       MOVE   WK-LMNDY            TO     TAX-EDATE
                        taxSdate = wkWeekstart;
                        taxEdate = wkLmndy;
                        // 023400     ELSE
                    } else {
                        // 023500       MOVE   WK-ENDYEAR          TO     WK-BDATE-YEAR
                        // 023600       MOVE   WK-ENDMONTH         TO     WK-BDATE-MONTH
                        // 023700       MOVE   "01"                TO     WK-BDATE-DATE
                        wkBdate = wkEndYear + wkEndMonth + "01";
                        // 023800       MOVE   WK-BDATE            TO     TAX-SDATE
                        // 023900       MOVE   WK-WEEKEND          TO     TAX-EDATE.
                        taxSdate = wkBdate;
                        taxEdate = wkWeekend;
                    }
                }
                // 023920   MOVE       TAX-SDATE           TO     WK-BDATE-P.
                // 023940   MOVE       TAX-EDATE           TO     WK-EDATE-P.
                wkBdateP = taxSdate;
                wkEdateP = taxEdate;
                // 024000   MOVE       PUTF-AMT            TO     TAX-AMT.
                // 024100   MOVE        0                  TO     TAX-ADDAMT.
                // 024200   MOVE       SPACES              TO     TAX-TRMNO.
                // 024300   MOVE       SPACES              TO     TAX-HMARK.
                // 024400   MOVE       SPACES              TO     TAX-MARK.
                // 024500   MOVE       SPACES              TO     TAX-HDATE.
                // 024600   MOVE       SPACES              TO     TAX-COUNT.
                //// 寫檔FD-TAX
                // 024700   WRITE      TAX-REC.

                //// 寫報表
                // 024720   PERFORM    0000-REPORT-RTN    THRU    0000-REPORT-EXIT.
                _0000_report();
                // 024750   MOVE       WK-RTMONTH          TO     WK-CMPMON.
                wkCmpmon = wkRtmonth;
                // 024800   GO     TO     0000-TAX-LOOP.
            }
        }
        // 024900 0000-TAX-LAST.
        //// 寫報表
        // 024950       PERFORM   0000-REPORT-RTN    THRU    0000-REPORT-EXIT.
        _0000_report();
        //// 關閉檔案
        //
        // 025000       CLOSE  FD-PUTF    WITH  SAVE.
        // 025100       CHANGE ATTRIBUTE FILENAME OF FD-TAX TO WK-PUTDIR.
        // 025200       CLOSE  FD-TAX     WITH  SAVE.
        // 025300 0000-TAX-EXIT.
    }

    private void _0000_fee() {
        // 025600 0000-FEE-RTN.

        //// 設定檔名
        // 025700     CHANGE  ATTRIBUTE FILENAME OF FD-FEE  TO WK-CONVDIR.
        // 014900 01 WK-CONVDIR.
        // 015000  03 FILLER                            PIC X(17)
        // 015100                         VALUE "DATA/CL/BH/CONVF/".
        // 015200  03 WK-CDATE                          PIC 9(06).
        // 015300  03 FILLER                            PIC X(01)
        // 015400                            VALUE "/".
        // 015500  03 WK-CONVFILE                       PIC X(10).
        // 015600  03 FILLER                            PIC X(01)
        // 015700                            VALUE ".".
        //// 開啟檔案
        //
        // 025800     OPEN       OUTPUT   FD-FEE.
        // 025900 0000-FEE-LOOP.
        //
        //// 循序讀取FD-PUTF，直到檔尾，跳到0000-FEE-LAST
        //
        // 026000     READ   FD-PUTF   AT  END  GO  TO 0000-FEE-LAST.
        if (textFile.exists(wkPutdir)) {
            List<String> lines = textFile.readFileContent(wkPutdir, CHARSET_UTF8);
            ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境
            for (String detail : lines) {
                //// IF PUTF-CTL NOT = 11,跳下一筆
                text2VoFormatter.format(detail, filePUTF);

                putfCtl =
                        parse.isNumeric(filePUTF.getCtl())
                                ? parse.string2Integer(filePUTF.getCtl())
                                : 0;
                // 026100     IF         PUTF-CTL       NOT  =      11
                if (putfCtl != 11) {
                    // 026200       GO  TO   0000-FEE-LOOP.
                    continue;
                }
                //// 搬PUTF...到FEE...
                // 026300     MOVE       SPACES              TO     FEE-REC.
                // 026400     MOVE       PUTF-DATE           TO     FEE-TXDATE.
                // 026440     MOVE       PUTF-USERDATA       TO     WK-USERDATA.
                wkUserdata = formatUtil.padX(filePUTF.getUserdata(), 40);
                wkRtmonth = wkUserdata.substring(19, 21);
                // 026600     MOVE       WK-RTLBR            TO     FEE-BANKNO.
                // 026620     IF         WK-RTMONTH      NOT  =     WK-CMPMON
                if (!wkRtmonth.trim().equals(wkCmpmon.trim())) {
                    // 026640        MOVE    0                   TO     WK-SERINO.
                    wkSerino = 0;
                }
                // 026660     ADD        1                   TO     WK-SERINO.
                wkSerino++;
                if (wkNetinfo.length() < 20) {
                    continue;
                }
                wkWeekstart = wkNetinfo.substring(0, 6);
                wkStartmonth = wkWeekstart.substring(2, 4);
                wkLmndy = wkNetinfo.substring(6, 12);
                wkWeekend = wkNetinfo.substring(12, 18);
                wkEndYear = wkWeekend.substring(0, 2);
                wkEndMonth = wkWeekend.substring(2, 4);
                // 026700     IF         WK-LMNDY            =      SPACES
                if (wkLmndy.trim().isEmpty()) {
                    // 026800       MOVE     WK-WEEKSTART        TO     FEE-SDATE
                    // 026900       MOVE     WK-WEEKEND          TO     FEE-EDATE
                    feeSdate = wkWeekstart;
                    feeEdate = wkWeekend;
                    // 027000     ELSE
                } else {
                    // 027100       IF       WK-RTMONTH          =      WK-STARTMONTH
                    if (wkRtmonth.trim().equals(wkStartmonth.trim())) {
                        // 027200         MOVE   WK-WEEKSTART        TO     FEE-SDATE
                        // 027300         MOVE   WK-LMNDY            TO     FEE-EDATE
                        feeEdate = wkWeekend;
                        feeSdate = wkLmndy;
                        // 027400       ELSE
                    } else {
                        // 027500         MOVE   WK-ENDYEAR          TO     WK-BDATE-YEAR
                        // 027600         MOVE   WK-ENDMONTH         TO     WK-BDATE-MONTH
                        // 027700         MOVE   "01"                TO     WK-BDATE-DATE
                        wkBdate = wkEndYear + wkEndMonth + "01";
                        // 027800         MOVE   WK-BDATE            TO     FEE-SDATE
                        // 027900         MOVE   WK-WEEKEND          TO     FEE-EDATE.
                        feeSdate = wkBdate;
                        feeEdate = wkWeekend;
                    }
                }
                // 027920     MOVE       FEE-SDATE           TO     WK-BDATE-P.
                // 027940     MOVE       FEE-EDATE           TO     WK-EDATE-P.
                wkBdateP = feeSdate;
                wkEdateP = feeEdate;
                //// 計算金額位數
                // 028000     PERFORM    CNVNUM-RTN      THRU       CNVNUM-EXIT.
                //// 寫報表
                // 028020   PERFORM    0000-REPORT-RTN    THRU    0000-REPORT-EXIT.
                _0000_report();
                // 028040   MOVE       WK-RTMONTH          TO     WK-CMPMON.
                wkCmpmon = wkRtmonth;
                //// 寫檔FD-FEE

                // 028100     WRITE  FEE-REC.
                // 028200         GO     TO     0000-FEE-LOOP.
            }
        }
        // 028300 0000-FEE-LAST.
        // 028350       PERFORM    0000-REPORT-RTN    THRU    0000-REPORT-EXIT.
        _0000_report();
        // 028400       CLOSE  FD-PUTF    WITH  SAVE.
        // 028500       CHANGE ATTRIBUTE FILENAME OF FD-FEE TO WK-PUTDIR.
        // 028600       CLOSE  FD-FEE     WITH  SAVE.
        // 028700 0000-FEE-EXIT.
    }

    private void _0000_report() {
        // 030740 0000-REPORT-RTN.

        //// 寫報表
        // 030742     IF         PUTF-CTL            =      12
        if (putfCtl == 12) {
            // 030744           PERFORM   095-WSUBT-RTN  THRU   095-WSUBT-EXIT
            _095_wsubt();
            // 030746           PERFORM   090-WTOT-RTN   THRU   090-WTOT-EXIT
            _090_wtot();

            // 030748           GO        TO             0000-REPORT-EXIT.
            return;
        }
        // 030750     IF         WK-RTMONTH      NOT =      WK-CMPMON
        if (wkRtmonth.trim().equals(wkCmpmon.trim())) {
            // 030780        IF      WK-CMPMON           =      SPACES
            if (wkCmpmon.trim().isEmpty()) {
                // 030790           PERFORM   098-WTIT-RTN   THRU   098-WTIT-EXIT
                _098_wtit();
                // 030800        ELSE
            } else {
                // 030810           PERFORM   095-WSUBT-RTN  THRU   095-WSUBT-EXIT
                _095_wsubt();
                // 030820           PERFORM   090-WTOT-RTN   THRU   090-WTOT-EXIT
                _090_wtot();
                // 030830           PERFORM   098-WTIT-RTN   THRU   098-WTIT-EXIT.
                _098_wtit();
            }
        }
        // 030860     PERFORM 097-WDTL-RTN   THRU   097-WDTL-EXIT.
        _097_wdtl();
        // 030870 0000-REPORT-EXIT.
    }

    private void _095_wsubt() {
        // 033700 095-WSUBT-RTN.
        //// 寫報表筆數金額

        // 033800     MOVE     SPACES                  TO      REPORT-LINE.
        // 033900     WRITE    REPORT-LINE            AFTER  1 LINE
        fileReportContents.add("");
        // 034000     MOVE     WK-SUBCNT               TO      WK-SUBCNT-R.
        // 034100     MOVE     WK-SUBAMT               TO      WK-SUBAMT-R.
        // 034200     WRITE    REPORT-LINE    FROM     WK-SUBTOT-LINE.
        wkSubcntR = wkSubcnt;
        wkSubamtR = wkSubamt;
        fileReportContents.add(wksubtot());
        // 034300     MOVE     SPACES                  TO      REPORT-LINE.
        // 034400     WRITE    REPORT-LINE           AFTER     1.
        fileReportContents.add("");
        // 034500     MOVE     0                       TO      WK-SUBCNT,WK-SUBAMT.
        wkSubcnt = 0;
        wkSubamt = ZERO;
        // 034600 095-WSUBT-EXIT.
    }

    private String getbkname() {
        // 034900 GETBKNAME-RTN.
        String str = "";
        //// 找銀行名稱
        // 035000     FIND  CDB-BKNM-IDX  AT CDB-BKNM-KEY = WK-RTBKNO ON EXCEPTION
        // 035100                       PERFORM CNVBKNAME-RTN THRU CNVBKNAME-EXIT
        //        if(Objects.isNull(cdbBknm)){
        //            cnvbkname()
        //        }
        // 035200                             GO      TO  GETBKNAME-EXIT.
        // 035300     MOVE  CDB-BKNM-ABBR     TO      WK-BKNAME-P.

        return str;
        // 035400 GETBKNAME-EXIT.
    }

    private void _097_wdtl() {
        // 040000 097-WDTL-RTN.
        //// 寫報表明細
        putfAmt =
                parse.isNumeric(filePUTF.getAmt())
                        ? parse.string2BigDecimal(filePUTF.getAmt())
                        : ZERO;
        // 040200    MOVE      WK-SERINO          TO     WK-SERINO-P.
        // 040400    MOVE      WK-ACTNO-L         TO     WK-ACTNO-P.
        // 040600    MOVE      PUTF-DATE          TO     WK-BHDATE-P.
        // 040800    MOVE      PUTF-AMT           TO     WK-AMT-P.
        // 042000    MOVE      WK-RTLBR           TO     WK-BKNO-P.
        wkSerinoP = wkSerino;
        wkActnoP = wkActnoL;
        wkBhdateP = filePUTF.getEntdy();
        wkAmtP = filePUTF.getAmt();
        wkBknoP = wkRtlbr;
        // 044000    PERFORM   GETBKNAME-RTN    THRU     GETBKNAME-EXIT.
        wkBknameP = getbkname();
        // 046000    ADD       1                  TO     WK-PCTL.
        wkPctl++;
        // 048000    IF        WK-PCTL            >      25
        if (wkPctl > 25) {
            // 050000      PERFORM 095-WSUBT-RTN    THRU     095-WSUBT-EXIT
            _095_wsubt();
            // 052000      PERFORM 098-WTIT-RTN     THRU     098-WTIT-EXIT
            _098_wtit();
            // 054000      ADD     1                  TO     WK-PCTL.
            wkPctl++;
        }
        // 056000    MOVE      SPACES             TO     REPORT-LINE.
        // 057000    WRITE     REPORT-LINE       AFTER     1.
        fileReportContents.add("");
        // 058000    WRITE     REPORT-LINE      FROM     WK-DETAIL-LINE.
        fileReportContents.add(wkDetail());

        // 060000    ADD       1                  TO     WK-SUBCNT,WK-TOTCNT.
        // 062000    ADD       PUTF-AMT           TO     WK-SUBAMT,WK-TOTAMT.
        wkSubcnt++;
        wkTotcnt++;
        wkSubamt = wkSubamt.add(parse.string2BigDecimal(filePUTF.getAmt()));
        wkSubamt = wkSubamt.add(putfAmt);
        wkTotamt = wkTotamt.add(putfAmt);

        // 064000 097-WDTL-EXIT.
        // 066000   EXIT.
    }

    private void _098_wtit() {
        // 030900 098-WTIT-RTN.
        //// 寫表頭

        // 031000     MOVE       0                   TO     WK-PCTL.
        // 031100     ADD        1                   TO     WK-PAGE.
        // 031300     MOVE       WK-KIND             TO     WK-KIND-P.
        wkPctl = 0;
        wkPage++;
        wkKindP = wkKind;
        // 031400     MOVE       SPACES              TO     REPORT-LINE.
        // 031500     WRITE      REPORT-LINE         AFTER  PAGE.
        fileReportContents.add(PAGE_SEPARATOR);

        // 031600     MOVE       SPACES              TO     REPORT-LINE.
        // 031700     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE1.
        fileReportContents.add(wkTitle1());

        // 031800     MOVE       SPACES              TO     REPORT-LINE.
        // 031900     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE2.
        fileReportContents.add(wkTitle2());

        // 032000     MOVE       SPACES              TO     REPORT-LINE.
        // 032100     WRITE      REPORT-LINE         FROM   WK-TITLE-LINE3.
        fileReportContents.add(wkTitle3());

        // 032200     MOVE       SPACES              TO     REPORT-LINE.
        // 032300     WRITE      REPORT-LINE         AFTER  1 LINE.
        fileReportContents.add("");

        // 032400 098-WTIT-EXIT.
        // 032500     EXIT.
    }

    private void _090_wtot() {
        // 032700 090-WTOT-RTN.
        //// 寫表尾

        // 032800     MOVE       0              TO    WK-PAGE.
        // 032900     MOVE       SPACES         TO    REPORT-LINE.
        // 033000     MOVE       WK-TOTCNT      TO    WK-TOTCNT-R.
        // 033100     MOVE       WK-TOTAMT      TO    WK-TOTAMT-R.
        wkPage = 0;
        wkTotcntR = wkTotcnt;
        wkTotamtR = wkTotamt;
        // 033200     WRITE      REPORT-LINE    FROM  WK-TOTAL-LINE.
        fileReportContents.add(wkTotal());
        // 033300     MOVE       0              TO    WK-TOTCNT,WK-TOTAMT.
        wkTotcnt = 0;
        wkTotamt = ZERO;
        // 033400 090-WTOT-EXIT.
        // 033500   EXIT.
    }

    private String wkTitle3() {
        // 011390 01 WK-TITLE-LINE3.
        // 011400    02 FILLER              PIC X(02) VALUE SPACES.
        // 011402    02 FILLER              PIC X(08) VALUE " 流水號 ".
        // 011404    02 FILLER              PIC X(02) VALUE SPACES.
        // 011410    02 FILLER              PIC X(10) VALUE " 公庫代號 ".
        // 011420    02 FILLER              PIC X(03) VALUE SPACES.
        // 011430    02 FILLER              PIC X(32) VALUE
        // 011440       " 公　庫　名　稱                 ".
        // 011450    02 FILLER              PIC X(10) VALUE " 專戶帳號 ".
        // 011460    02 FILLER              PIC X(05) VALUE SPACES.
        // 011470    02 FILLER              PIC X(10) VALUE " 收款起日 ".
        // 011480    02 FILLER              PIC X(08) VALUE SPACES.
        // 011490    02 FILLER              PIC X(10) VALUE " 收款迄日 ".
        // 011500    02 FILLER              PIC X(08) VALUE SPACES.
        // 011510    02 FILLER              PIC X(10) VALUE " 解款日期 ".
        // 011520    02 FILLER              PIC X(10) VALUE SPACES.
        // 011530    02 FILLER              PIC X(10) VALUE " 送達金額 ".
        // 011540    02 FILLER              PIC X(05) VALUE SPACES.
        // 011550    02 FILLER              PIC X(10) VALUE " 專戶註記 ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(" 流水號 ", 8));
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(" 公庫代號 ", 10));
        sb.append(formatUtil.padX("", 3));
        sb.append(formatUtil.padX(" 公　庫　名　稱                 ", 32));
        sb.append(formatUtil.padX(" 專戶帳號 ", 10));
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX(" 收款起日 ", 10));
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 收款迄日 ", 10));
        sb.append(formatUtil.padX("", 8));
        sb.append(formatUtil.padX(" 解款日期 ", 10));
        sb.append(formatUtil.padX("", 10));
        sb.append(formatUtil.padX(" 送達金額 ", 10));
        sb.append(formatUtil.padX("", 5));
        sb.append(formatUtil.padX(" 專戶註記 ", 10));
        return sb.toString();
    }

    private String wkTitle2() {
        // 011270 01 WK-TITLE-LINE2.
        // 011280    02 FILLER                   PIC X(02) VALUE SPACES.
        // 011290    02 FILLER                   PIC X(12) VALUE " 主辦分行： ".
        // 011300    02 WK-BRNO-P                PIC 9(03).
        // 011310    02 FILLER                   PIC X(88) VALUE SPACES.
        // 011320    02 FILLER                   PIC X(12) VALUE
        // 011330       " 製表日期： ".
        // 011340    02 WK-PDATE                 PIC 999/99/99.
        // 011350    02 FILLER                   PIC X(06) VALUE SPACES.
        // 011360    02 FILLER                   PIC X(08) VALUE
        // 011370       " 頁次： ".
        // 011380    02 WK-PAGE                  PIC 9(04).
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 2));
        sb.append(formatUtil.padX(" 主辦分行： ", 12));
        sb.append(formatUtil.padX("" + wkBrnoP, 3));
        sb.append(formatUtil.padX("", 88));
        sb.append(formatUtil.padX(" 製表日期： ", 12));
        sb.append(reportUtil.customFormat(wkPdate, "999/99/99"));
        sb.append(formatUtil.padX("", 6));
        sb.append(formatUtil.padX(" 頁次： ", 8));
        sb.append(formatUtil.pad9("" + wkPage, 4));
        return sb.toString();
    }

    private String wkDetail() {
        // 011560 01 WK-DETAIL-LINE.
        sb = new StringBuilder();
        // 011570    02 FILLER              PIC X(03) VALUE SPACES.
        sb.append(formatUtil.padX("", 3));
        // 011572    02 WK-SERINO-P         PIC ZZZZZ9.
        sb.append(reportUtil.customFormat("" + wkSerinoP, "ZZZZZ9"));
        // 011574    02 FILLER              PIC X(04) VALUE SPACES.
        sb.append(formatUtil.padX("", 4));
        // 011580    02 WK-BKNO-P           PIC X(07).
        sb.append(formatUtil.padX(wkBknoP, 7));
        // 011590    02 FILLER              PIC X(05) VALUE SPACES.
        sb.append(formatUtil.padX("", 5));
        // 011600    02 WK-BKNAME-P         PIC X(22).
        sb.append(formatUtil.padX(wkBknameP, 22));
        // 011610    02 FILLER              PIC X(12) VALUE SPACES.
        sb.append(formatUtil.padX("", 12));
        // 011620    02 WK-ACTNO-P          PIC X(06).
        sb.append(formatUtil.padX(wkActnoP, 6));
        // 011630    02 FILLER              PIC X(10) VALUE SPACES.
        sb.append(formatUtil.padX("", 10));
        // 011640    02 WK-BDATE-P          PIC 9(06).
        sb.append(formatUtil.pad9(wkBdateP, 6));
        // 011650    02 FILLER              PIC X(12) VALUE SPACES.
        sb.append(formatUtil.padX("", 12));
        // 011660    02 WK-EDATE-P          PIC 9(06).
        sb.append(formatUtil.pad9(wkEdateP, 6));
        // 011670    02 FILLER              PIC X(12) VALUE SPACES.
        sb.append(formatUtil.padX("", 12));
        // 011680    02 WK-BHDATE-P         PIC 9(06).
        sb.append(formatUtil.padX(wkBhdateP, 6));
        // 011690    02 FILLER              PIC X(07) VALUE SPACES.
        sb.append(formatUtil.padX("", 7));
        // 011700    02 WK-AMT-P            PIC Z,ZZZ,ZZZ,ZZZ.
        sb.append(reportUtil.customFormat(wkAmtP, "Z,ZZZ,ZZZ,ZZZ"));
        // 011710    02 FILLER              PIC X(12) VALUE SPACES.
        sb.append(formatUtil.padX("", 12));
        return sb.toString();
    }

    private String wkTitle1() {
        // 011220 01 WK-TITLE-LINE1.
        // 011230    02 FILLER                   PIC X(60) VALUE SPACES.
        // 011240    02 FILLER                   PIC X(22) VALUE
        // 011250       " 公庫送款憑單清冊－－ ".
        // 011260    02 WK-KIND-P                PIC X(12).
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 60));
        sb.append(formatUtil.padX(" 公庫送款憑單清冊－－ ", 22));
        sb.append(formatUtil.padX(wkKindP, 12));
        return sb.toString();
    }

    private String wkTotal() {
        // 011840 01 WK-TOTAL-LINE.
        // 011850    02 FILLER                          PIC X(57) VALUE SPACES.
        // 011860    02 FILLER                          PIC X(10) VALUE
        // 011870       " 合　計 : ".
        // 011880    02 FILLER                          PIC X(07) VALUE
        // 011890       " 筆數 :".
        // 011900    02 WK-TOTCNT-R                     PIC ZZ,ZZ9.
        // 011910    02 FILLER                          PIC X(06) VALUE SPACES.
        // 011920    02 FILLER                          PIC X(07) VALUE
        // 011930       " 金額 :".
        // 011940    02 FILLER                          PIC X(01) VALUE SPACES.
        // 011950    02 WK-TOTAMT-R                     PIC Z,ZZZ,ZZZ,ZZZ,ZZ9.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 57));
        sb.append(formatUtil.padX(" 合　計 : ", 10));
        sb.append(formatUtil.padX(" 筆數 :", 7));
        sb.append(reportUtil.customFormat("" + wkTotcntR, "ZZ,ZZ9"));
        sb.append(formatUtil.padX("", 6));
        sb.append(formatUtil.padX(" 金額 :", 7));
        sb.append(formatUtil.padX("", 1));
        sb.append(reportUtil.customFormat("" + wkTotamtR, "Z,ZZZ,ZZZ,ZZZ,ZZ9"));
        return sb.toString();
    }

    private String wksubtot() {
        // 011720 01 WK-SUBTOT-LINE.
        // 011730    02 FILLER                          PIC X(57) VALUE SPACES.
        // 011740    02 FILLER                          PIC X(10) VALUE
        // 011750       " 頁　計 : ".
        // 011760    02 FILLER                          PIC X(07) VALUE
        // 011770       " 筆數 :".
        // 011780    02 WK-SUBCNT-R                     PIC ZZ,ZZ9.
        // 011790    02 FILLER                          PIC X(06) VALUE SPACES.
        // 011800    02 FILLER                          PIC X(07) VALUE
        // 011810       " 金額 :".
        // 011820    02 FILLER                          PIC X(01) VALUE SPACES.
        // 011830    02 WK-SUBAMT-R                     PIC Z,ZZZ,ZZZ,ZZZ,ZZ9.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 57));
        sb.append(formatUtil.padX(" 頁　計 : ", 10));
        sb.append(formatUtil.padX(" 筆數 :", 7));
        sb.append(reportUtil.customFormat("" + wkSubcntR, "ZZ,ZZ9"));
        sb.append(formatUtil.padX("", 6));
        sb.append(formatUtil.padX(" 金額 :", 7));
        sb.append(formatUtil.padX("", 1));
        sb.append(reportUtil.customFormat("" + wkSubamtR, "Z,ZZZ,ZZZ,ZZZ,ZZ9"));
        return sb.toString();
    }

    private String getKind(String code12) {
        String str = "";
        // 035700 GETKIND-RTN.
        //
        //// 判斷稅費種類
        //
        // 035800    IF        WK-CODE12 =  "1"  OR  = "6"
        // 035900      MOVE    " 燃料費 "         TO     WK-KIND.
        // 036000    IF        WK-CODE12 =  "2"  OR  = "7"
        // 036100      MOVE    " 本埠地方稅 "     TO     WK-KIND.
        // 036200    IF        WK-CODE12 =  "3"  OR  = "8"
        // 036300      MOVE    " 外埠地方稅 "     TO     WK-KIND.
        // 036400    IF        WK-CODE12 =  "4"  OR  = "9"
        // 036500      MOVE    " 本埠國稅 "       TO     WK-KIND.
        // 036600    IF        WK-CODE12 =  "5"  OR  = "0"
        // 036700      MOVE    " 外埠國稅 "       TO     WK-KIND.
        if ("1".equals(code12) || "6".equals(code12)) {
            str = " 燃料費 ";
        }
        if ("2".equals(code12) || "7".equals(code12)) {
            str = " 本埠地方稅 ";
        }
        if ("3".equals(code12) || "8".equals(code12)) {
            str = " 外埠地方稅 ";
        }
        if ("4".equals(code12) || "9".equals(code12)) {
            str = " 本埠國稅 ";
        }
        if ("5".equals(code12) || "0".equals(code12)) {
            str = " 外埠國稅 ";
        }

        return str;
        // 036800 GETKIND-EXIT.
    }

    private void moveErrorResponse(LogicException e) {
        // event.setPeripheryRequest();
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

    private void batchResponse() {
        Map<String, String> responseTextMap = new HashMap<>(textMap);
        responseTextMap.put("RPTNAME", wkFileNameList);
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", responseTextMap);
    }
}
