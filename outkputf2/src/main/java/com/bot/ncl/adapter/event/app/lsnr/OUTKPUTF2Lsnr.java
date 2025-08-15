/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.OUTKPUTF2;
import com.bot.ncl.jpa.svc.CltmrService;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("OUTKPUTF2Lsnr")
@Scope("prototype")
public class OUTKPUTF2Lsnr extends BatchListenerCase<OUTKPUTF2> {

    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private CltmrService cltmrService;
    @Autowired private Parse parse;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    private OUTKPUTF2 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    @Value("${fsapFile.gn.dwl.directory}")
    private String fsapfileDir;

    private static final String CHARSET = "UTF-8";
    private static final String CL012_FILE_PATH = "CL012"; // 目錄
    private static final String _003_FILE_PATH = "003"; // 讀檔目錄
    private static final String FILE_INPUT_NAME = "KPUTH1"; // 讀檔檔名
    private String PATH_SEPARATOR = File.separator;
    private String wkKputhdir; // 讀檔路徑
    private String wkPutdir = ""; // 產檔路徑
    private StringBuilder sb = new StringBuilder();
    private List<String> fileKPUTF2Contents; //  檔案內容

    private Map<String, String> textMap;
    private String[] wkParamL;
    private Integer wkKdate;
    private Integer wkFdate;
    private String wkPreCode;
    private Integer wkTotcnt;
    private String wkTaskDate;
    private BigDecimal wkTotamt;
    private String wkPrePutfile;
    private String wkPutfile;
    private String putfCtl;

    // ----KPUTH----
    private String kputhPutfile;
    private String kputhCode;
    private String kputhRcptid;
    private String kputhUserdata;
    private String kputhTxtype;
    private BigDecimal kputhAmt;
    private Integer kputhDate;
    private Integer kputhTime;
    private Integer kputhCllbr;
    private Integer kputhLmtdate;
    private Integer kputhSitdate;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(OUTKPUTF2 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTKPUTF2Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(OUTKPUTF2 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTKPUTF2Lsnr run");

        if (!init(event)) {
            batchResponse();
            return;
        }
        // 若FD-KPUTH檔案存在，執行0000-MAIN-RTN，循序讀FD-KPUTH，寫FD-KPUTF
        // 005700     IF  ATTRIBUTE  RESIDENT   OF FD-KPUTH IS =  VALUE(TRUE)
        // 005800       PERFORM  0000-MAIN-RTN  THRU  0000-MAIN-EXIT.
        // 檢查讀檔路徑是否存在
        if (textFile.exists(wkKputhdir)) {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "main");
            main();
        }
        batchResponse();
    }

    private Boolean init(OUTKPUTF2 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTKPUTF2Lsnr init");
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        Map<String, String> paramMap;
        paramMap = getG2007Param(textMap.get("PARAM"));
        if (paramMap == null) {
            return false;
        }
        wkTaskDate = textMap.get("DATE");
        // 搬接收參數給WK-KPUTHDIR、WK-PUTDIR檔名變數值
        // 005400     MOVE    WK-TASK-DATE      TO                WK-KDATE   ,
        // 005500                                                 WK-FDATE   .
        wkKdate = parse.string2Integer(wkTaskDate);
        wkFdate = parse.string2Integer(wkTaskDate);

        // 設定FD-KPUTH檔名
        //  WK-KPUTHDIR="DATA/GN/DWL/CL012/003/"+WK-KDATE 9(07)+"/KPUTH1."
        // 005600     CHANGE ATTRIBUTE FILENAME OF FD-KPUTH TO    WK-KPUTHDIR.
        // 003400  01 WK-KPUTHDIR.
        // 003500     03 FILLER                      PIC X(22)
        // 003600                         VALUE "DATA/GN/DWL/CL012/003/".
        // 003700     03 WK-KDATE                    PIC 9(07).
        // 003800     03 FILLER                      PIC X(08)
        // 003900                                  VALUE "/KPUTH1.".
        wkKputhdir =
                fsapfileDir
                        + CL012_FILE_PATH
                        + PATH_SEPARATOR
                        + _003_FILE_PATH
                        + PATH_SEPARATOR
                        + wkKdate
                        + PATH_SEPARATOR
                        + FILE_INPUT_NAME;
        // 檔案內容
        fileKPUTF2Contents = new ArrayList<>();
        return true;
    }

    private void main() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTKPUTF2Lsnr main");
        // 006300 0000-MAIN-RTN.
        // 開啟檔案FD-KPUTH
        // 006400     OPEN     INPUT            FD-KPUTH.
        // WK-PRE-...清空白
        // 006500     MOVE     SPACES           TO   WK-PRE-CODE .
        // 006600     MOVE     SPACES           TO   WK-PRE-PUTFILE.
        wkPreCode = "";
        wkPrePutfile = "";

        // 006700 0000-MAIN-LOOP.
        // 循序讀取FD-KPUTH，直到檔尾，跳到0000-MAIN-FLAST
        // 006800     READ     FD-KPUTH AT END GO TO 0000-MAIN-FLAST  .
        List<String> lines = textFile.readFileContent(wkKputhdir, CHARSET);
        for (String detail : lines) {
            // 執行FILENAME-SWH-RTN，PUTFILE不同時之處理
            // 執行CODE-SWH-RTN，CODE不同時之處理
            // 執行FILE-DTL-RTN，A.累計筆數、金額、B.搬KPUTH-REC...給PUTF-REC...(CTL=11)、C.寫檔FD-KPUTF
            kputhPutfile = detail.substring(0, 10);
            kputhCode = detail.substring(10, 16);
            kputhRcptid = detail.substring(16, 32);
            kputhDate = parse.string2Integer(detail.substring(32, 39));
            kputhTime = parse.string2Integer(detail.substring(39, 45));
            kputhAmt = parse.string2BigDecimal(detail.substring(54, 64));
            kputhCllbr = parse.string2Integer(detail.substring(45, 48));
            kputhLmtdate = parse.string2Integer(detail.substring(48, 54));
            kputhUserdata = detail.substring(64, 104);
            kputhSitdate = parse.string2Integer(detail.substring(104, 111));
            kputhTxtype = detail.substring(111, 112);

            // 006900     PERFORM  FILENAME-SWH-RTN THRU FILENAME-SWH-EXIT.
            filenameSwh();
            // 007000     PERFORM  CODE-SWH-RTN     THRU CODE-SWH-EXIT    .
            codeSwh();
            // 007100     PERFORM  FILE-DTL-RTN     THRU FILE-DTL-EXIT    .
            fileDtl();
            // LOOP讀下一筆FD-KPUTH
            // 007300     GO TO  0000-MAIN-LOOP.
        }

        // 007400 0000-MAIN-FLAST.
        // 執行FILE-LAST-RTN，寫檔FD-KPUTF(CTL=12)、關檔FD-KPUTF、WK-PRE-CODE清空白
        // 007500     PERFORM  FILE-LAST-RTN    THRU FILE-LAST-EXIT.
        fileLast();
        // 007700 0000-MAIN-EXIT.
    }

    private void filenameSwh() {
        // 008000 FILENAME-SWH-RTN.
        // 首筆
        //  A.設定FD-KPUTF檔名變數值
        //  B.設定FD-KPUTF檔名
        //     WK-PUTDIR="DATA/GN/DWL/CL012/003/"+WK-FDATE 9(07)+"/"+WK-PUTFILE X(10)+"."
        //  C.開新檔案FD-KPUTF
        //  D.結束本段落
        // 008200     IF (     WK-PRE-PUTFILE     =     SPACES     )
        if (wkPrePutfile.isEmpty()) {
            // 008300       MOVE   KPUTH-PUTFILE      TO    WK-PUTFILE ,
            // 008400                                       WK-PRE-PUTFILE
            wkPutfile = kputhPutfile;
            wkPrePutfile = kputhPutfile;

            // 008500     CHANGE ATTRIBUTE FILENAME OF FD-KPUTF TO WK-PUTDIR
            // 004000     01 WK-PUTDIR.
            // 004100     03 FILLER                      PIC X(22)
            // 004200                         VALUE "DATA/GN/DWL/CL012/003/".
            // 004300     03 WK-FDATE                    PIC 9(07).
            // 004400     03 FILLER                      PIC X(01) VALUE "/".
            // 004500     03 WK-PUTFILE                  PIC X(10).
            // 004600     03 FILLER                      PIC X(01) VALUE ".".
            wkPutdir =
                    fileDir
                            + CL012_FILE_PATH
                            + PATH_SEPARATOR
                            + _003_FILE_PATH
                            + PATH_SEPARATOR
                            + wkFdate
                            + PATH_SEPARATOR
                            + wkPutfile
                            + ".";
            // 008600       OPEN   OUTPUT             FD-KPUTF
            // 008700       GO TO FILENAME-SWH-EXIT.
            return;
        }
        // PUTFILE不同時
        //  A.執行FILE-LAST-RTN，寫檔FD-KPUTF(CTL=12)、關檔FD-KPUTF、WK-PRE-CODE清空白
        //  B.設定FD-KPUTF檔名變數值
        //  C.設定FD-KPUTF檔名
        //     WK-PUTDIR="DATA/GN/DWL/CL012/003/"+WK-FDATE 9(07)+"/"+WK-PUTFILE X(10)+"."
        //  D.開新檔案FD-KPUTF
        //  E.結束本段落

        // 008900     IF ( ( WK-PRE-PUTFILE       NOT = KPUTH-PUTFILE ) AND
        // 009000          ( WK-PRE-PUTFILE       NOT = SPACES     )     )
        // .trim() => 去掉字串多餘的空白
        if (!wkPrePutfile.equals(kputhPutfile) && !wkPrePutfile.trim().isEmpty()) {
            // 009100       PERFORM  FILE-LAST-RTN    THRU  FILE-LAST-EXIT
            fileLast();
            // 009200       MOVE   KPUTH-PUTFILE      TO    WK-PUTFILE,
            // 009300                                       WK-PRE-PUTFILE
            wkPutfile = kputhPutfile;
            wkPrePutfile = kputhPutfile;
            // 009400       CHANGE ATTRIBUTE FILENAME OF FD-KPUTF TO WK-PUTDIR
            // 009500       OPEN   OUTPUT             FD-KPUTF
            wkPutdir =
                    fileDir
                            + CL012_FILE_PATH
                            + PATH_SEPARATOR
                            + _003_FILE_PATH
                            + PATH_SEPARATOR
                            + wkKdate
                            + PATH_SEPARATOR
                            + FILE_INPUT_NAME;
            // 009600       GO TO FILENAME-SWH-EXIT.
            // 009800 FILENAME-SWH-EXIT.
        }
    }

    private void fileLast() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTKPUTF2Lsnr fileLast");
        // 010100 FILE-LAST-RTN.
        // 執行CODE-LAST-RTN，搬相關資料給PUTF-REC...(CTL=12)、寫檔FD-KPUTF
        // 關檔FD-KPUTF
        // WK-PRE-CODE清空白

        // 010200     PERFORM   CODE-LAST-RTN     THRU    CODE-LAST-EXIT .
        codeLast();
        // 010300     CLOSE     FD-KPUTF          WITH    SAVE           .
        if (!wkPutdir.isEmpty() || !fileKPUTF2Contents.isEmpty()) {
            try {
                textFile.writeFileContent(wkPutdir, fileKPUTF2Contents, CHARSET);
            } catch (LogicException e) {
                moveErrorResponse(e);
            }
        }
        // 010400     MOVE      SPACES            TO      WK-PRE-CODE    .
        wkPreCode = "";
        // 010500 FILE-LAST-EXIT.
    }

    private void codeSwh() {
        // 012800 CODE-SWH-RTN.
        // 首筆或不同PUTFILE之首筆
        //  A.搬代收類別給WK-PRE-CODE
        //  B.累計筆數、金額清0
        // 012900     IF        WK-PRE-CODE       =       SPACES
        if (wkPreCode.isEmpty()) {
            // 012920        MOVE  KPUTH-CODE         TO      WK-PRE-CODE
            // 012940        MOVE  0                  TO      WK-TOTCNT
            // 012960        MOVE  0                  TO      WK-TOTAMT
            wkPreCode = kputhCode;
            wkTotcnt = 0;
            wkTotamt = new BigDecimal(0);
            // 013000        GO TO CODE-SWH-EXIT.
            return;
            // 013100*
        }
        // 代收類別不同時
        //  A.執行CODE-LAST-RTN，搬相關資料給PUTF-REC...(CTL=12)、寫檔FD-KPUTF
        //  B.搬代收類別給WK-PRE-CODE
        //  C.累計筆數、金額清0

        // 013200     IF    (  KPUTH-CODE         NOT =   WK-PRE-CODE )
        if (!kputhCode.equals(wkPreCode)) {
            // 013300        PERFORM CODE-LAST-RTN    THRU    CODE-LAST-EXIT
            codeLast();
            // 013400        MOVE  KPUTH-CODE         TO      WK-PRE-CODE
            wkPreCode = kputhCode;
            // 013500        MOVE  0                  TO      WK-TOTCNT
            wkTotcnt = 0;
            // 013600        MOVE  0                  TO      WK-TOTAMT    .
            BigDecimal wkTotamt = new BigDecimal(0);
            // 013700 CODE-SWH-EXIT.
        }
    }

    private void codeLast() {
        // 搬相關資料給PUTF-REC...(CTL=12)

        // 014100     MOVE      SPACES            TO      PUTF-REC       .
        sb = new StringBuilder();
        // 014200     MOVE      WK-TOTCNT         TO      PUTF-TOTCNT    .
        Integer putfCnt = wkTotcnt;
        // 014300     MOVE      WK-TOTAMT         TO      PUTF-TOTAMT    .
        BigDecimal putfTotamt = wkTotamt;
        // 014400     MOVE      WK-TASK-DATE(2:6) TO      PUTF-BDATE     .
        String putfBdate = wkTaskDate.substring(1, 7);
        // 014500     MOVE      WK-TASK-DATE(2:6) TO      PUTF-EDATE     .
        String putfEdate = wkTaskDate.substring(1, 7);
        ;
        // 014600     MOVE      12                TO      PUTF-CTL       .
        putfCtl = "12";
        // 014700     MOVE      WK-PRE-CODE       TO      PUTF-CODE      .
        String putfCode = wkPreCode;
        // 寫檔FD-KPUTF
        // 009900 01  PUTF-REC.
        // 010000     03  PUTF-CTL                         PIC 9(02).
        // 010020     03  PUTF-CTL-R       REDEFINES   PUTF-CTL.
        // 010040      05 PUTF-CTL1                        PIC 9(01).
        // 010060      05 PUTF-CTL2                        PIC 9(01).
        sb.append(formatUtil.pad9(putfCtl, 2));
        // 010100     03  PUTF-CODE                        PIC X(06).
        sb.append(formatUtil.padX(putfCode, 6));
        // 010200     03  PUTF-DATA.
        // 011100     03  PUTF-DATA-R       REDEFINES  PUTF-DATA.
        // 011200      05 PUTF-BDATE                       PIC 9(06).
        sb.append(formatUtil.pad9("" + putfBdate, 6));
        // 011300      05 PUTF-EDATE                       PIC 9(06).
        sb.append(formatUtil.pad9("" + putfEdate, 6));
        // 011400      05 PUTF-TOTCNT                      PIC 9(06).
        sb.append(formatUtil.pad9("" + putfCnt, 6));
        // 011500      05 PUTF-TOTAMT                      PIC 9(13).
        sb.append(formatUtil.pad9("" + putfTotamt, 13));
        // 011600      05 FILLER                           PIC X(81).
        sb.append(formatUtil.padX("", 81));

        // 014800     WRITE     PUTF-REC.
        fileKPUTF2Contents.add(formatUtil.padX(sb.toString(), 120));
        // 014900 CODE-LAST-EXIT.
    }

    private void fileDtl() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTKPUTF2Lsnr fileDtl");
        // 010800 FILE-DTL-RTN.

        // 累計筆數、金額
        // 010900     ADD       1                 TO      WK-TOTCNT      .
        // 011000     ADD       KPUTH-AMT         TO      WK-TOTAMT      .
        wkTotcnt++;
        wkTotamt = wkTotamt.add(kputhAmt);
        // 搬KPUTH-REC...給PUTF-REC...(CTL=11)
        // 011100     MOVE      SPACES            TO      PUTF-REC       .
        sb = new StringBuilder();
        // 011200     MOVE      11                TO      PUTF-CTL       .
        String putfCtl = "11";
        // 011300     MOVE      KPUTH-CODE        TO      PUTF-CODE      ,
        // 011400                                         WK-PRE-CODE    .
        String putfCode = kputhCode;
        wkPreCode = kputhCode;
        // 011500     MOVE      KPUTH-RCPTID      TO      PUTF-RCPTID    .
        String putfRcptid = kputhRcptid;
        // 011600     MOVE      KPUTH-DATE        TO      PUTF-DATE      .
        Integer putfDate = kputhDate;
        // 011700     MOVE      KPUTH-TIME        TO      PUTF-TIME      .
        Integer putfTime = kputhTime;
        // 011800     MOVE      KPUTH-AMT         TO      PUTF-AMT,PUTF-OLDAMT.
        BigDecimal putfAmt = kputhAmt;
        BigDecimal putfOldamt = kputhAmt;
        // 011900     MOVE      KPUTH-CLLBR       TO      PUTF-CLLBR     .
        Integer putfCllbr = kputhCllbr;
        // 012000     MOVE      KPUTH-LMTDATE     TO      PUTF-LMTDATE   .
        Integer putfLmtdate = kputhLmtdate;
        // 012100     MOVE      KPUTH-USERDATA    TO      PUTF-USERDATA  .
        String putfUserdata = kputhUserdata;
        // 012200     MOVE      KPUTH-SITDATE     TO      PUTF-SITDATE   .
        Integer putfSitdate = kputhSitdate;
        // 012300     MOVE      KPUTH-TXTYPE      TO      PUTF-TXTYPE    .
        String putfTxtype = kputhTxtype;

        // 012400     WRITE     PUTF-REC.
        // 寫檔FD-KPUTF

        // 009900 01  PUTF-REC.
        // 010000     03  PUTF-CTL                         PIC 9(02).
        // 010020     03  PUTF-CTL-R       REDEFINES   PUTF-CTL.
        // 010040      05 PUTF-CTL1                        PIC 9(01).
        // 010060      05 PUTF-CTL2                        PIC 9(01).
        sb.append(formatUtil.pad9(putfCtl, 2));
        // 010100     03  PUTF-CODE                        PIC X(06).
        sb.append(formatUtil.padX(putfCode, 6));
        // 010200     03  PUTF-DATA.
        // 010300      05 PUTF-RCPTID                      PIC X(16).
        sb.append(formatUtil.padX(putfRcptid, 16));
        // 010400      05 PUTF-DATE                        PIC 9(06).
        sb.append(formatUtil.pad9("" + putfDate, 6));
        // 010500      05 PUTF-TIME                        PIC 9(06).
        sb.append(formatUtil.pad9("" + putfTime, 6));
        // 010600      05 PUTF-CLLBR                       PIC 9(03).
        sb.append(formatUtil.pad9("" + putfCllbr, 3));
        // 010700      05 PUTF-LMTDATE                     PIC 9(06).
        sb.append(formatUtil.pad9("" + putfLmtdate, 6));
        // 010800      05 PUTF-OLDAMT                      PIC 9(08).
        sb.append(formatUtil.pad9("" + putfOldamt, 8));
        // 010900      05 PUTF-USERDATA                    PIC X(40).
        sb.append(formatUtil.padX(putfUserdata, 40));
        // 010950      05 PUTF-SITDATE                     PIC 9(06).
        sb.append(formatUtil.pad9("" + putfSitdate, 6));
        // 010960      05 PUTF-TXTYPE                      PIC X(01).
        sb.append(formatUtil.padX(putfTxtype, 1));
        // 010980      05 PUTF-AMT                         PIC 9(10).
        sb.append(formatUtil.pad9("" + putfAmt, 10));
        // 011000      05 PUTF-FILLER                      PIC X(10).
        sb.append(formatUtil.padX("", 10));

        fileKPUTF2Contents.add(formatUtil.padX(sb.toString(), 120));

        // 012500 FILE-DTL-EXIT.
    }

    private String getrocdate(int dateI) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), " getrocdate inputdate = {}", dateI);

        String date = "" + dateI;
        if (dateI > 19110101) {
            dateI = dateI - 19110000;
        }
        if (String.valueOf(dateI).length() < 7) {
            date = String.format("%07d", dateI);
        }
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), " getrocdate outputdate = {}", date);
        return date;
    }

    private void moveErrorResponse(LogicException e) {
        //        this.event.setPeripheryRequest();
    }

    private Map<String, String> getG2007Param(String lParam) {
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
        // G2007:
        //  BRNO(;),
        //  APSEQ(;),
        //  PARAM1(;),
        //  PARAM2(;),
        //  PARAM3(;),
        //  PARAM4(;),
        //  PARAM5(;),
        //  PARAM6(;)
        Map<String, String> map = new HashMap<>();
        if (paramL.length > 0) map.put("BRNO", paramL[0]); // 對應 BRNO
        if (paramL.length > 1) map.put("APSEQ", paramL[1]); // 對應 APSEQ
        if (paramL.length > 2) map.put("PARAM1", paramL[2]); // 對應 PARAM1
        if (paramL.length > 3) map.put("PARAM2", paramL[3]); // 對應 PARAM2
        if (paramL.length > 4) map.put("PARAM3", paramL[4]); // 對應 PARAM3
        if (paramL.length > 5) map.put("PARAM4", paramL[5]); // 對應 PARAM4
        if (paramL.length > 6) map.put("PARAM5", paramL[6]); // 對應 PARAM5
        if (paramL.length > 7) map.put("PARAM6", paramL[7]); // 對應 PARAM6
        if (map.size() == 0) {
            return null;
        }
        int i = 0;
        for (String key : map.keySet()) {
            i++;
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
