/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.C060M;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.sort.ExternalSortUtil;
import com.bot.ncl.util.sort.eum.SortBy;
import com.bot.ncl.util.sort.vo.KeyRange;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import java.io.File;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("C060MLsnr")
@Scope("prototype")
public class C060MLsnr extends BatchListenerCase<C060M> {

    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private Parse parse;
    @Autowired private ExternalSortUtil externalSortUtil;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> textMap;

    private static final String CHARSET = "Big5"; // 檔案編碼
    private static final String FILE_INPUT_NAME = "CL060M"; // 讀檔檔名
    private static final String FILE_NAME_1 = "CL-BH-C060-M1"; // 檔名1
    private static final String FILE_NAME_2 = "CL-BH-C060-M2"; // 檔名2
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private String ANALY_FILE_PATH = "ANALY"; // 讀檔目錄
    private String inputFilePath; // 讀檔路徑
    private String outputFilePath1; // 產檔路徑1
    private String outputFilePath2; // 產檔路徑2
    private StringBuilder sb = new StringBuilder();
    private List<String> fileC060M1Contents; // 檔案內容1
    private List<String> fileC060M2Contents; // 檔案內容2
    private BigDecimal MINAMT = new BigDecimal(500000);
    private String PATH_SEPARATOR = File.separator;
    private String pageSeparator = "\u000C";
    private int wkBrno = 0;
    private int wkPage = 0;
    private int wkPage005 = 0;
    private int wkCnt = 0;
    private int wkCntpg = 0;
    private int wkCnt005 = 0;
    private int wkCntpg005 = 0;
    private String wkTaskDate = "";
    private BigDecimal wkAmt = BigDecimal.ZERO;
    private BigDecimal wkNetamt = BigDecimal.ZERO;
    private BigDecimal wkLamt = BigDecimal.ZERO;
    private BigDecimal wkLrate = BigDecimal.ZERO;
    private BigDecimal kputhAmt = BigDecimal.ZERO;
    private int kputhCllbr = 0;
    private String kputhUserdata = "";
    private String kputhCode = "";
    private String kputhRcptid = "";
    private String kputhSitdate = "";
    private String kputhTxType = "";
    private String kputhTime = "";
    private String kputhPutname = "";
    private String kputhUpldate = "";
    private DecimalFormat dAmt1Format = new DecimalFormat("#,###,###,##0");
    private DecimalFormat dAmt2Format = new DecimalFormat("###,###,##0");

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(C060M event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C060MLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(C060M event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C060MLsnr run()");
        init(event);
        // RUNRTN6B:
        //    RUN  OBJ/CL/BH/SORT/C060M;
        sortC060M();
        toWriteC060File();
    }

    private void init(C060M event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C060MLsnr init ....");
        // 抓批次營業日
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        wkTaskDate = textMap.get("WK_TASK_DATE"); // TODO: 待確認BATCH參數名稱
        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();
        // 設定工作日、檔名日期變數值
        String processDate = labelMap.get("PROCESS_DATE"); // 待中菲APPLE提供正確名稱
        int processDateInt = parse.string2Integer(processDate);
        // 讀檔路徑
        inputFilePath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDateInt
                        + PATH_SEPARATOR
                        + ANALY_FILE_PATH
                        + PATH_SEPARATOR
                        + FILE_INPUT_NAME;
        // 暫存檔路徑
        //        sortTmpFilePath = fileDir + FILE_TMP_NAME;
        // 產檔路徑1
        outputFilePath1 =
                fileDir
                        + CONVF_RPT
                        + PATH_SEPARATOR
                        + processDateInt
                        + PATH_SEPARATOR
                        + FILE_NAME_1;
        // 產檔路徑2
        outputFilePath2 =
                fileDir
                        + CONVF_RPT
                        + PATH_SEPARATOR
                        + processDateInt
                        + PATH_SEPARATOR
                        + FILE_NAME_2;
        // 刪除舊檔
        //        textFile.deleteFile(sortTmpFilePath);
        textFile.deleteFile(outputFilePath1);
        textFile.deleteFile(outputFilePath2);
        fileC060M1Contents = new ArrayList<>();
        fileC060M2Contents = new ArrayList<>();
    }

    private void sortC060M() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C060MLsnr sortC060M ....");
        if (textFile.exists(inputFilePath)) {
            File tmpFile = new File(inputFilePath);
            List<KeyRange> keyRanges = new ArrayList<>();
            // 03 KPUTH-CLLBR	9(03)	代收行 45-48
            keyRanges.add(new KeyRange(46, 3, SortBy.ASC));
            // 03 KPUTH-CODE	X(06)	代收類別 10-16
            keyRanges.add(new KeyRange(11, 6, SortBy.ASC));
            // 03 KPUTH-SITDATE	9(07)	原代收日 104-111
            keyRanges.add(new KeyRange(105, 7, SortBy.ASC));
            externalSortUtil.sortingFile(tmpFile, tmpFile, keyRanges, CHARSET);
        }
    }

    private void toWriteC060File() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C060MLsnr toWriteC060File ....");

        List<String> lines = textFile.readFileContent(inputFilePath, CHARSET);
        int cnt = 0;
        for (String detail : lines) {
            // 03 KPUTH-PUTFILE	GROUP
            //  05 KPUTH-PUTTYPE	9(02)	媒體種類 0-2
            //  05 KPUTH-PUTNAME	X(08)	媒體檔名 2-10
            // 03 KPUTH-PUTFILE-R1 	REDEFINES KPUTH-PUTFILE
            //  05 KPUTH-ENTPNO	X(10)
            // 03 KPUTH-CODE	X(06)	代收類別 10-16
            // 03 KPUTH-RCPTID	X(16)	銷帳號碼 16-32
            // 03 KPUTH-DATE	9(07)	代收日 32-39
            // 03 KPUTH-TIME	9(06)	代收時間 39-45
            // 03 KPUTH-CLLBR	9(03)	代收行 45-48
            // 03 KPUTH-LMTDATE	9(06)	繳費期限 48-54
            // 03 KPUTH-AMT	9(10)	繳費金額 54-64
            // 03 KPUTH-USERDATA	X(40)	備註資料 64-104
            // 03 KPUTH-USERDATE-R1 	REDEFINES KPUTH-USERDATA
            //  05 KPUTH-SMSERNO	X(03)
            //  05 KPUTH-RETAILNO	X(08)
            //  05 KPUTH-BARCODE3	X(15)
            //  05 KPUTH-FILLER	X(14)
            // 03 KPUTH-SITDATE	9(07)	原代收日 104-111
            // 03 KPUTH-TXTYPE	X(01)	帳務別 111-112
            // 03 KPUTH-SERINO	9(06)	交易明細流水序號 112-118
            // 03 KPUTH-PBRNO	9(03)	主辦分行 118-121
            // 03 KPUTH-UPLDATE	9(07) 121-128
            // 03 KPUTH-FEETYPE	9(01) 128-129
            // 03 KPUTH-FEEO2L	9(05)V99 129-136
            // 03 FILLER	X(02)
            // 03 FILLER	X(02)
            kputhRcptid = detail.substring(16, 32);
            kputhAmt = parse.string2BigDecimal(detail.substring(54, 64));
            kputhTxType = detail.substring(151, 152);
            kputhCllbr = parse.string2Integer(detail.substring(45, 48));
            kputhUserdata = detail.substring(64, 104);
            kputhCode = detail.substring(10, 16);
            kputhSitdate = detail.substring(104, 111);
            kputhTime = detail.substring(39, 45);
            kputhUpldate = detail.substring(121, 128);
            kputhPutname = detail.substring(2, 10);

            // 019300     IF  KPUTH-AMT < 500000  OR  KPUTH-TXTYPE NOT= "C"
            if (kputhAmt.compareTo(MINAMT) <= 0 || !"C".equals(kputhTxType)) {
                // 019400         GO TO 1000-START-LOOP.
                continue;
            }
            // 019500     PERFORM  2500-PAGEM2-RTN    THRU  2500-PAGEM2-EXIT.
            pageM2();
            // 019600     PERFORM  2000-PAGEM1-RTN    THRU  2000-PAGEM1-EXIT.
            pageM1();
            // 019700     IF  WK-CNTPG = 0
            if (wkCntpg == 0) {
                // 019800       ADD    1                   TO    WK-CNT005
                // 019900       MOVE   SPACE               TO    REPORTFLM2-REC
                // 020000       MOVE   KPUTH-CLLBR         TO    RPT-05BRNO
                // 020100       WRITE  REPORTFLM2-REC      FROM  RPT-LINEBRNO
                // 015400 01 RPT-LINEBRNO.
                // 015500    03 FILLER          PIC X(07)  VALUE  SPACES.
                // 015600    03 FILLER          PIC X(12)  VALUE  " 代收分行： ".
                // 015700    03 RPT-05BRNO      PIC X(03)  VALUE  SPACES.
                // 015800    03 FILLER          PIC X(143) VALUE  SPACES.
                wkCnt005 = wkCnt005 + 1;
                sb = new StringBuilder();
                sb.append(formatUtil.padX(" ", 7));
                sb.append(formatUtil.padX(" 代收分行： ", 12));
                sb.append(formatUtil.pad9("" + kputhCllbr, 3));
                sb.append(formatUtil.padX(" ", 143));
                fileC060M2Contents.add(sb.toString());

                // 020200       PERFORM  2500-PAGEM2-RTN   THRU  2500-PAGEM2-EXIT
                pageM2();

                // 020300     END-IF.
            }
            // 020400     PERFORM  3000-WRITEM-RTN    THRU  3000-WRITEM-EXIT.
            writem();
        }

        try {
            textFile.writeFileContent(outputFilePath2, fileC060M2Contents, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }

        try {
            textFile.writeFileContent(outputFilePath1, fileC060M1Contents, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void pageM2() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C060MLsnr pageM2 ....");
        // 024300 2500-PAGEM2-RTN.

        // 024500     IF  WK-BRNO = 0   OR    WK-CNT005  > 49
        if (wkBrno == 0 || wkCnt005 > 49) {
            // 024600         ADD    1                TO     WK-PAGE005
            // 024700         MOVE   WK-PAGE005       TO     RPT-PAGE
            wkPage005 = wkPage005 + 1;
            // 024900         MOVE   SPACE            TO     REPORTFLM2-REC
            // 025000         WRITE  REPORTFLM2-REC   FROM   RPT-LINE01-005
            // 007300 01 RPT-LINE01-005.
            // 007400    03 FILLER          PIC X(57) VALUE SPACES.
            // 007500    03 FILLER          PIC X(48) VALUE
            // 007600          " 臨櫃代收稅５０萬元以上（含）現金交易彙總月報表 ".
            sb = new StringBuilder();
            if (wkBrno == 0) {
                sb.append(pageSeparator);
            } else {
                sb.append(pageSeparator);
            }
            sb.append(formatUtil.padX(" ", 57));
            sb.append(formatUtil.padX(" 臨櫃代收稅５０萬元以上（含）現金交易彙總月報表 ", 48));
            fileC060M2Contents.add(sb.toString());

            // 025100         MOVE   SPACE            TO     REPORTFLM2-REC
            // 025200         WRITE  REPORTFLM2-REC   FROM   RPT-LINE02-005
            // 008500 01 RPT-LINE02-005.
            // 008600    03 FILLER          PIC X(02)  VALUE  SPACES.
            // 008700    03 FILLER          PIC X(12)  VALUE  " 代收分行： ".
            // 008800    03 FILLER          PIC X(06)  VALUE  " 全部 ".
            // 008900    03 FILLER          PIC X(112) VALUE  SPACES.
            // 009000    03 FILLER          PIC X(12)  VALUE  " 報表名稱： ".
            // 009100    03 FILLER          PIC X(08)  VALUE  "C060/M2 ".
            sb = new StringBuilder();
            sb.append(formatUtil.padX(" ", 2));
            sb.append(formatUtil.padX(" 代收分行： ", 12));
            sb.append(formatUtil.padX(" 全部 ", 6));
            sb.append(formatUtil.padX(" ", 112));
            sb.append(formatUtil.padX(" 報表名稱： ", 12));
            sb.append(formatUtil.padX("C060/M2 ", 8));
            fileC060M2Contents.add(sb.toString());

            // 025300         MOVE   SPACE            TO     REPORTFLM2-REC
            // 025400         WRITE  REPORTFLM2-REC   FROM   RPT-LINE03
            // 009300 01 RPT-LINE03.
            // 009400    03 FILLER          PIC X(02)  VALUE  SPACES.
            // 009500    03 FILLER          PIC X(12)  VALUE  " 代收月份： ".
            // 009600    03 RPT-DATE        PIC 999/99.
            // 009700    03 FILLER          PIC X(112) VALUE  SPACES.
            // 009800    03 FILLER          PIC X(12)  VALUE  " 頁　　數： ".
            // 009900    03 RPT-PAGE        PIC ZZZZ.
            sb = new StringBuilder();
            sb.append(formatUtil.padX(" ", 2));
            sb.append(formatUtil.padX(" 代收月份： ", 12));
            String fnbsdyYY = wkTaskDate.substring(0, 3);
            String fnbsdyMM = wkTaskDate.substring(3, 5);
            sb.append(formatUtil.padX(fnbsdyYY + "/" + fnbsdyMM, 6));
            sb.append(formatUtil.padX(" ", 112));
            sb.append(formatUtil.padX(" 頁　　數： ", 12));
            sb.append(formatUtil.padX("" + wkPage005, 3));
            fileC060M2Contents.add(sb.toString());

            // 025500         MOVE   SPACE            TO     REPORTFLM2-REC
            // 025600         WRITE  REPORTFLM2-REC   FROM   RPT-LINE99
            // 016400 01 RPT-LINE99.
            // 016500    03 FILLE           PIC X(175) VALUE  SPACES.
            sb = new StringBuilder();
            sb.append(formatUtil.padX(" ", 175));
            fileC060M2Contents.add(sb.toString());

            // 025700         MOVE   SPACE            TO     REPORTFLM2-REC
            // 025800         WRITE  REPORTFLM2-REC   FROM   RPT-LINE04
            // 010100 01 RPT-LINE04.
            sb = new StringBuilder();
            // 010200    03 FILLER          PIC X(02)  VALUE  SPACES.
            sb.append(formatUtil.padX(" ", 2));
            // 010300    03 FILLER          PIC X(06)  VALUE  " 序號 ".
            sb.append(formatUtil.padX(" 序號 ", 6));
            // 010400    03 FILLER          PIC X(02)  VALUE  SPACES.
            sb.append(formatUtil.padX(" ", 2));
            // 010500    03 FILLER          PIC X(06)  VALUE  " 稅別 ".
            sb.append(formatUtil.padX(" 稅別 ", 6));
            // 010600    03 FILLER          PIC X(02)  VALUE  SPACES.
            sb.append(formatUtil.padX(" ", 2));
            // 010700    03 FILLER          PIC X(10)  VALUE  " 銷帳編號 ".
            sb.append(formatUtil.padX(" 銷帳編號 ", 10));
            // 010800    03 FILLER          PIC X(14)  VALUE  SPACES.
            sb.append(formatUtil.padX(" ", 14));
            // 010900    03 FILLER          PIC X(10)  VALUE  " 交易日期 ".
            sb.append(formatUtil.padX(" 交易日期 ", 10));
            // 011000    03 FILLER          PIC X(02)  VALUE  SPACES.
            sb.append(formatUtil.padX(" ", 2));
            // 011100    03 FILLER          PIC X(06)  VALUE  " 時間 ".
            sb.append(formatUtil.padX(" 時間 ", 6));
            // 011200    03 FILLER          PIC X(02)  VALUE  SPACES.
            sb.append(formatUtil.padX(" ", 2));
            // 011300    03 FILLER          PIC X(06)  VALUE  " 櫃機 ".
            sb.append(formatUtil.padX(" 櫃機 ", 6));
            // 011400    03 FILLER          PIC X(06)  VALUE  " 櫃員 ".
            sb.append(formatUtil.padX(" 櫃員 ", 6));
            // 011500    03 FILLER          PIC X(06)  VALUE  " 帳務 ".
            sb.append(formatUtil.padX(" 帳務 ", 6));
            // 011600    03 FILLER          PIC X(02)  VALUE  SPACES.
            sb.append(formatUtil.padX(" ", 2));
            // 011700    03 FILLER          PIC X(10)  VALUE  " 繳納金額 ".
            sb.append(formatUtil.padX(" 繳納金額 ", 10));
            // 011800    03 FILLER          PIC X(02)  VALUE  SPACES.
            sb.append(formatUtil.padX(" ", 2));
            // 011900    03 FILLER          PIC X(12)  VALUE  " 逾期滯納金 ".
            sb.append(formatUtil.padX(" 逾期滯納金 ", 12));
            // 012000    03 FILLER          PIC X(02)  VALUE  SPACES.
            sb.append(formatUtil.padX(" ", 2));
            // 012100    03 FILLER          PIC X(12)  VALUE  " 逾期滯納息 ".
            sb.append(formatUtil.padX(" 逾期滯納息 ", 12));
            // 012200    03 FILLER          PIC X(03)  VALUE  SPACES.
            sb.append(formatUtil.padX(" ", 3));
            // 012300    03 FILLER          PIC X(12)  VALUE  " 繳納總金額 ".
            sb.append(formatUtil.padX(" 繳納總金額 ", 12));
            // 012400    03 FILLER          PIC X(06)  VALUE  " 備註 ".
            sb.append(formatUtil.padX(" 備註 ", 6));
            fileC060M2Contents.add(sb.toString());

            // 025900         MOVE   SPACE            TO     REPORTFLM2-REC
            // 026000         WRITE  REPORTFLM2-REC   FROM   RPT-LINE98
            // 016000 01 RPT-LINE98.
            // 016100    03 FILLER          PIC X(02)  VALUE  SPACES.
            // 016200    03 FILLER          PIC X(169) VALUE  ALL "=".
            sb = new StringBuilder();
            sb.append(
                    formatUtil.padX(
                            "=========================================================================================================================================================================",
                            169));
            fileC060M2Contents.add(sb.toString());

            // 026200         IF   WK-CNT005  > 49
            if (wkCnt005 > 49) {
                // 026300              MOVE  0            TO     WK-CNT005
                wkCnt005 = 0;
            }
        }
        // 026400         END-IF
        // 026500     END-IF.

        // 026600 2500-PAGEM2-EXIT.
    }

    private void pageM1() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C060MLsnr pageM1 ....");
        // 020900 2000-PAGEM1-RTN.

        // 021100     IF   WK-BRNO NOT = KPUTH-CLLBR  OR  WK-CNT > 49
        if (wkBrno != kputhCllbr || wkCnt > 49) {
            // 021200          IF  WK-BRNO NOT = KPUTH-CLLBR
            if (wkBrno != kputhCllbr) {
                // 021300              MOVE  0               TO     WK-PAGE
                // 021400              MOVE  0               TO     WK-CNT , WK-CNTPG
                // 021500              MOVE  KPUTH-CLLBR     TO     WK-BRNO, RPT-BRNO
                // 021600              MOVE  SPACE           TO     REPORTFLM1-REC
                // 021700              WRITE REPORTFLM1-REC  AFTER  PAGE
                // 021800          END-IF
                wkPage = 0;
                wkCntpg = 0;
                sb = new StringBuilder();
                if (wkBrno == 0) {
                    sb.append(pageSeparator); // 換頁符號
                } else {
                    sb.append(pageSeparator); // 換頁符號
                }
                wkBrno = kputhCllbr;
                fileC060M1Contents.add(sb.toString());
                wkCnt = 0;
            }
            // 021900          ADD    1                TO     WK-PAGE
            // 022000          MOVE   WK-PAGE          TO     RPT-PAGE
            wkPage = wkPage + 1;

            // 022200          MOVE   SPACE            TO     REPORTFLM1-REC
            // 022300          WRITE  REPORTFLM1-REC   FROM   RPT-LINE01
            // 006800 01 RPT-LINE01.
            // 006900    03 FILLER          PIC X(57) VALUE SPACES.
            // 007000    03 FILLER          PIC X(44) VALUE
            // 007100          " 臨櫃代收稅５０萬元以上（含）現金交易月報表 ".
            sb = new StringBuilder();
            sb.append(formatUtil.padX(" ", 57));
            sb.append(formatUtil.padX(" 臨櫃代收稅５０萬元以上（含）現金交易月報表 ", 48));
            fileC060M1Contents.add(sb.toString());

            // 022400          MOVE   SPACE            TO     REPORTFLM1-REC
            // 022500          WRITE  REPORTFLM1-REC   FROM   RPT-LINE02
            // 007800 01 RPT-LINE02.
            // 007900    03 FILLER          PIC X(02)  VALUE  SPACES.
            // 008000    03 FILLER          PIC X(12)  VALUE  " 代收分行： ".
            // 008100    03 RPT-BRNO        PIC X(03)  VALUE  SPACES.
            // 008200    03 FILLER          PIC X(115) VALUE  SPACES.
            // 008300    03 FILLER          PIC X(12)  VALUE  " 報表名稱： ".
            // 008400    03 FILLER          PIC X(08)  VALUE  "C060/M1 ".
            sb = new StringBuilder();
            sb.append(formatUtil.padX(" ", 2));
            sb.append(formatUtil.padX(" 代收分行： ", 12));
            sb.append(formatUtil.pad9("" + wkBrno, 3));
            sb.append(formatUtil.padX(" ", 115));
            sb.append(formatUtil.padX(" 報表名稱： ", 12));
            sb.append(formatUtil.padX("C060/M1 ", 8));
            fileC060M1Contents.add(sb.toString());

            // 022600          MOVE   SPACE            TO     REPORTFLM1-REC
            // 022700          WRITE  REPORTFLM1-REC   FROM   RPT-LINE03
            // 009300 01 RPT-LINE03.
            // 009400    03 FILLER          PIC X(02)  VALUE  SPACES.
            // 009500    03 FILLER          PIC X(12)  VALUE  " 代收月份： ".
            // 009600    03 RPT-DATE        PIC 999/99.
            // 009700    03 FILLER          PIC X(112) VALUE  SPACES.
            // 009800    03 FILLER          PIC X(12)  VALUE  " 頁　　數： ".
            // 009900    03 RPT-PAGE        PIC ZZZZ.
            sb = new StringBuilder();
            sb.append(formatUtil.padX(" ", 2));
            sb.append(formatUtil.padX(" 代收月份： ", 12));
            String fnbsdyYY = wkTaskDate.substring(0, 3);
            String fnbsdyMM = wkTaskDate.substring(3, 5);
            sb.append(formatUtil.padX(fnbsdyYY + "/" + fnbsdyMM, 6));
            sb.append(formatUtil.padX(" ", 112));
            sb.append(formatUtil.padX(" 頁　　數： ", 12));
            sb.append(formatUtil.padX("" + wkPage, 3));
            fileC060M1Contents.add(sb.toString());

            // 022800          MOVE   SPACE            TO     REPORTFLM1-REC
            // 022900          WRITE  REPORTFLM1-REC   FROM   RPT-LINE99
            // 016400 01 RPT-LINE99.
            // 016500    03 FILLE           PIC X(175) VALUE  SPACES.
            sb = new StringBuilder();
            sb.append("");
            fileC060M1Contents.add(sb.toString());

            // 023000          MOVE   SPACE            TO     REPORTFLM1-REC
            // 023100          WRITE  REPORTFLM1-REC   FROM   RPT-LINE04
            // 010100 01 RPT-LINE04.
            sb = new StringBuilder();
            // 010200    03 FILLER          PIC X(02)  VALUE  SPACES.
            sb.append(formatUtil.padX(" ", 2));
            // 010300    03 FILLER          PIC X(06)  VALUE  " 序號 ".
            sb.append(formatUtil.padX(" 序號 ", 6));
            // 010400    03 FILLER          PIC X(02)  VALUE  SPACES.
            sb.append(formatUtil.padX(" ", 2));
            // 010500    03 FILLER          PIC X(06)  VALUE  " 稅別 ".
            sb.append(formatUtil.padX(" 稅別 ", 6));
            // 010600    03 FILLER          PIC X(02)  VALUE  SPACES.
            sb.append(formatUtil.padX(" ", 2));
            // 010700    03 FILLER          PIC X(10)  VALUE  " 銷帳編號 ".
            sb.append(formatUtil.padX(" 銷帳編號 ", 10));
            // 010800    03 FILLER          PIC X(14)  VALUE  SPACES.
            sb.append(formatUtil.padX(" ", 14));
            // 010900    03 FILLER          PIC X(10)  VALUE  " 交易日期 ".
            sb.append(formatUtil.padX(" 交易日期 ", 10));
            // 011000    03 FILLER          PIC X(02)  VALUE  SPACES.
            sb.append(formatUtil.padX(" ", 2));
            // 011100    03 FILLER          PIC X(06)  VALUE  " 時間 ".
            sb.append(formatUtil.padX(" 時間 ", 6));
            // 011200    03 FILLER          PIC X(02)  VALUE  SPACES.
            sb.append(formatUtil.padX(" ", 2));
            // 011300    03 FILLER          PIC X(06)  VALUE  " 櫃機 ".
            sb.append(formatUtil.padX(" 櫃機 ", 6));
            // 011400    03 FILLER          PIC X(06)  VALUE  " 櫃員 ".
            sb.append(formatUtil.padX(" 櫃員 ", 6));
            // 011500    03 FILLER          PIC X(06)  VALUE  " 帳務 ".
            sb.append(formatUtil.padX(" 帳務 ", 6));
            // 011600    03 FILLER          PIC X(02)  VALUE  SPACES.
            sb.append(formatUtil.padX(" ", 2));
            // 011700    03 FILLER          PIC X(10)  VALUE  " 繳納金額 ".
            sb.append(formatUtil.padX(" 繳納金額 ", 10));
            // 011800    03 FILLER          PIC X(02)  VALUE  SPACES.
            sb.append(formatUtil.padX(" ", 2));
            // 011900    03 FILLER          PIC X(12)  VALUE  " 逾期滯納金 ".
            sb.append(formatUtil.padX(" 逾期滯納金 ", 12));
            // 012000    03 FILLER          PIC X(02)  VALUE  SPACES.
            sb.append(formatUtil.padX(" ", 2));
            // 012100    03 FILLER          PIC X(12)  VALUE  " 逾期滯納息 ".
            sb.append(formatUtil.padX(" 逾期滯納息 ", 12));
            // 012200    03 FILLER          PIC X(03)  VALUE  SPACES.
            sb.append(formatUtil.padX(" ", 3));
            // 012300    03 FILLER          PIC X(12)  VALUE  " 繳納總金額 ".
            sb.append(formatUtil.padX(" 繳納總金額 ", 12));
            // 012400    03 FILLER          PIC X(06)  VALUE  " 備註 ".
            sb.append(formatUtil.padX(" 備註 ", 6));
            fileC060M1Contents.add(sb.toString());

            // 023200          MOVE   SPACE            TO     REPORTFLM1-REC
            // 023300          WRITE  REPORTFLM1-REC   FROM   RPT-LINE98
            // 016000 01 RPT-LINE98.
            // 016100    03 FILLER          PIC X(02)  VALUE  SPACES.
            // 016200    03 FILLER          PIC X(169) VALUE  ALL "=".
            sb = new StringBuilder();
            sb.append(
                    formatUtil.padX(
                            "=========================================================================================================================================================================",
                            169));
            fileC060M1Contents.add(sb.toString());

            // 023500          IF   WK-CNT     > 49
            if (wkCnt > 49) {
                // 023600               MOVE  0            TO     WK-CNT
                wkCnt = 0;
            }
            // 024000 2000-PAGEM1-EXIT.
        }
    }

    private void writem() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C060MLsnr writem ....");
        // 026900 3000-WRITEM-RTN.
        // 027100      ADD     1                     TO  WK-CNT    , WK-CNTPG.
        // 027200      ADD     1                     TO  WK-CNT005 , WK-CNTPG005.
        wkCnt = wkCnt + 1;
        wkCntpg = wkCntpg + 1;
        wkCnt005 = wkCnt005 + 1;
        wkCntpg005 = wkCntpg005 + 1;
        // 027300      IF  KPUTH-USERDATA(1:18)  IS  NUMERIC
        if (parse.isNumeric(kputhUserdata.substring(0, 18))) {
            // 027400        MOVE KPUTH-USERDATA(1:10)   TO  WK-LAMT
            // 027500        MOVE KPUTH-USERDATA(11:10)  TO  WK-LRATE
            wkLamt = wkLamt.add(parse.string2BigDecimal(kputhUserdata.substring(0, 10)));
            wkLrate = wkLrate.add(parse.string2BigDecimal(kputhUserdata.substring(10, 20)));
        }
        // 027600      ELSE
        else {
            // 027700        MOVE KPUTH-USERDATA(19:10)  TO  WK-LAMT
            // 027800        MOVE KPUTH-USERDATA(29:10)  TO  WK-LRATE
            wkLamt = wkLamt.add(parse.string2BigDecimal(kputhUserdata.substring(18, 28)));
            wkLrate = wkLrate.add(parse.string2BigDecimal(kputhUserdata.substring(28, 38)));
            // 027900      END-IF.
        }
        // 028000      MOVE  KPUTH-AMT               TO  WK-AMT.
        // 028100      COMPUTE WK-NETAMT = WK-AMT - WK-LAMT - WK-LRATE.
        wkAmt = kputhAmt;
        wkNetamt = wkAmt.subtract(wkLamt).subtract(wkLrate);
        // 028300      MOVE    WK-CNTPG        TO    RPT-DT-NO.
        //        wkCntpg
        // 028400      IF    KPUTH-CODE = "366AE9"
        String rptDtTaxtype = "";
        if ("366AE9".equals(kputhCode)) {
            // 028500          MOVE  " 國稅 "      TO    RPT-DT-TAXTYPE
            rptDtTaxtype = " 國稅 ";
        }
        // 028600      ELSE
        else {
            // 028700          MOVE  " 地方稅 "    TO    RPT-DT-TAXTYPE.
            rptDtTaxtype = " 地方稅 ";
        }
        // 028800      MOVE  KPUTH-RCPTID      TO    RPT-DT-RCPTID.
        // 028900      MOVE  KPUTH-SITDATE     TO    RPT-DT-SITDATE.
        // 029000      MOVE  KPUTH-TXTYPE      TO    RPT-DT-TXTYPE.
        // 029100      MOVE  WK-NETAMT         TO    RPT-DT-NETAMT.
        // 029200      MOVE  WK-LAMT           TO    RPT-DT-LAMT.
        // 029300      MOVE  WK-LRATE          TO    RPT-DT-LRATE.
        // 029400      MOVE  KPUTH-AMT         TO    RPT-DT-AMT.
        // 029500      MOVE  KPUTH-USERDATA    TO    RPT-DT-USERDATA.
        // 029600      MOVE  KPUTH-TIME        TO    RPT-DT-TIME.
        // 029700* 來源檔案欄位借用
        // 029800      MOVE  KPUTH-PUTNAME(1:2)  TO    RPT-DT-TLRNO.
        // 029900      MOVE  KPUTH-UPLDATE       TO    RPT-DT-TRMNO.
        // 030000
        // 030100      MOVE   SPACE            TO     REPORTFLM1-REC.
        // 030200      WRITE  REPORTFLM1-REC   FROM   RPT-LINEDATA.

        // 012600 01 RPT-LINEDATA.
        sb = new StringBuilder();
        // 012700    03 FILLER              PIC X(02)  VALUE  SPACES.
        sb.append(formatUtil.padX(" ", 2));
        // 012800    03 RPT-DT-NO           PIC 9(06)  VALUE  0.
        sb.append(formatUtil.pad9("" + wkCntpg, 6));
        // 012900    03 FILLER              PIC X(02)  VALUE  SPACES.
        sb.append(formatUtil.padX(" ", 2));
        // 013000    03 RPT-DT-TAXTYPE      PIC X(08)  VALUE  SPACES.
        sb.append(formatUtil.padX(rptDtTaxtype, 8));
        // 013100    03 FILLER              PIC X(01)  VALUE  SPACES.
        sb.append(formatUtil.padX(" ", 1));
        // 013200    03 RPT-DT-RCPTID       PIC X(22)  VALUE  SPACES.
        sb.append(formatUtil.padX(kputhRcptid, 22));
        // 013300    03 FILLER              PIC X(02)  VALUE  SPACES.
        sb.append(formatUtil.padX(" ", 2));
        // 013400    03 RPT-DT-SITDATE      PIC 999/99/99.
        String sitDateYY = kputhSitdate.substring(0, 3);
        String sitDateMM = kputhSitdate.substring(3, 5);
        String sitDateDD = kputhSitdate.substring(5, 7);
        sb.append(formatUtil.padX(sitDateYY + "/" + sitDateMM + "/" + sitDateDD, 9));
        // 013500    03 FILLER              PIC X(02)  VALUE  SPACES.
        sb.append(formatUtil.padX(" ", 2));
        // 013600    03 RPT-DT-TIME         PIC 9(06)  VALUE  0.
        sb.append(formatUtil.pad9(kputhTime, 6));
        // 013700    03 FILLER              PIC X(02)  VALUE  SPACES.
        sb.append(formatUtil.padX(" ", 2));
        // 013800    03 RPT-DT-TRMNO        PIC 9(04)  VALUE  0.
        sb.append(formatUtil.pad9(kputhUpldate, 4));
        // 013900    03 FILLER              PIC X(02)  VALUE  SPACES.
        sb.append(formatUtil.padX(" ", 2));
        // 014000    03 RPT-DT-TLRNO        PIC X(02)  VALUE  SPACES.
        sb.append(formatUtil.padX(kputhPutname.substring(0, 2), 2));
        // 014100    03 FILLER              PIC X(04)  VALUE  SPACES.
        sb.append(formatUtil.padX(" ", 4));
        // 014200    03 RPT-DT-TXTYPE       PIC X(01)  VALUE  SPACES.
        sb.append(formatUtil.padX(kputhTxType, 1));
        // 014300    03 FILLER              PIC X(01)  VALUE  SPACES.
        sb.append(formatUtil.padX(" ", 1));
        // 014400    03 RPT-DT-NETAMT       PIC Z,ZZZ,ZZZ,ZZ9.
        sb.append(formatUtil.padX(dAmt1Format.format(wkNetamt), 13));
        // 014500    03 FILLER              PIC X(03)  VALUE  SPACES.
        sb.append(formatUtil.padX(" ", 3));
        // 014600    03 RPT-DT-LAMT         PIC ZZZ,ZZZ,ZZ9.
        sb.append(formatUtil.padX(dAmt2Format.format(wkLamt), 11));
        // 014700    03 FILLER              PIC X(03)  VALUE  SPACES.
        sb.append(formatUtil.padX(" ", 3));
        // 014800    03 RPT-DT-LRATE        PIC ZZZ,ZZZ,ZZ9.
        sb.append(formatUtil.padX(dAmt2Format.format(wkLrate), 11));
        // 014900    03 FILLER              PIC X(01)  VALUE  SPACES.
        sb.append(formatUtil.padX(" ", 1));
        // 015000    03 RPT-DT-AMT          PIC Z,ZZZ,ZZZ,ZZ9.
        sb.append(formatUtil.padX(dAmt1Format.format(kputhAmt), 13));
        // 015100    03 FILLER              PIC X(02)  VALUE  SPACES.
        sb.append(formatUtil.padX(" ", 2));
        // 015200    03 RPT-DT-USERDATA     PIC X(40)  VALUE  SPACES.
        sb.append(formatUtil.padX(kputhUserdata, 40));
        fileC060M1Contents.add(sb.toString());

        // 030400      MOVE   WK-CNTPG005      TO     RPT-DT-NO.
        //        wkCntpg005
        // 030500      MOVE   SPACE            TO     REPORTFLM2-REC.
        // 030600      WRITE  REPORTFLM2-REC   FROM   RPT-LINEDATA.

        sb = new StringBuilder();
        // 012700    03 FILLER              PIC X(02)  VALUE  SPACES.
        sb.append(formatUtil.padX(" ", 2));
        // 012800    03 RPT-DT-NO           PIC 9(06)  VALUE  0.
        sb.append(formatUtil.pad9("" + wkCntpg005, 6));
        // 012900    03 FILLER              PIC X(02)  VALUE  SPACES.
        sb.append(formatUtil.padX(" ", 2));
        // 013000    03 RPT-DT-TAXTYPE      PIC X(08)  VALUE  SPACES.
        sb.append(formatUtil.padX(rptDtTaxtype, 8));
        // 013100    03 FILLER              PIC X(01)  VALUE  SPACES.
        sb.append(formatUtil.padX(" ", 1));
        // 013200    03 RPT-DT-RCPTID       PIC X(22)  VALUE  SPACES.
        sb.append(formatUtil.padX(kputhRcptid, 22));
        // 013300    03 FILLER              PIC X(02)  VALUE  SPACES.
        sb.append(formatUtil.padX(" ", 2));
        // 013400    03 RPT-DT-SITDATE      PIC 999/99/99.
        sitDateYY = kputhSitdate.substring(0, 3);
        sitDateMM = kputhSitdate.substring(3, 5);
        sitDateDD = kputhSitdate.substring(5, 7);
        sb.append(formatUtil.padX(sitDateYY + "/" + sitDateMM + "/" + sitDateDD, 9));
        // 013500    03 FILLER              PIC X(02)  VALUE  SPACES.
        sb.append(formatUtil.padX(" ", 2));
        // 013600    03 RPT-DT-TIME         PIC 9(06)  VALUE  0.
        sb.append(formatUtil.pad9(kputhTime, 6));
        // 013700    03 FILLER              PIC X(02)  VALUE  SPACES.
        sb.append(formatUtil.padX(" ", 2));
        // 013800    03 RPT-DT-TRMNO        PIC 9(04)  VALUE  0.
        sb.append(formatUtil.pad9(kputhUpldate, 4));
        // 013900    03 FILLER              PIC X(02)  VALUE  SPACES.
        sb.append(formatUtil.padX(" ", 2));
        // 014000    03 RPT-DT-TLRNO        PIC X(02)  VALUE  SPACES.
        sb.append(formatUtil.padX(kputhPutname.substring(0, 2), 2));
        // 014100    03 FILLER              PIC X(04)  VALUE  SPACES.
        sb.append(formatUtil.padX(" ", 4));
        // 014200    03 RPT-DT-TXTYPE       PIC X(01)  VALUE  SPACES.
        sb.append(formatUtil.padX(kputhTxType, 1));
        // 014300    03 FILLER              PIC X(01)  VALUE  SPACES.
        sb.append(formatUtil.padX(" ", 1));
        // 014400    03 RPT-DT-NETAMT       PIC Z,ZZZ,ZZZ,ZZ9.
        sb.append(formatUtil.padX(dAmt1Format.format(wkNetamt), 13));
        // 014500    03 FILLER              PIC X(03)  VALUE  SPACES.
        sb.append(formatUtil.padX(" ", 3));
        // 014600    03 RPT-DT-LAMT         PIC ZZZ,ZZZ,ZZ9.
        sb.append(formatUtil.padX(dAmt2Format.format(wkLamt), 11));
        // 014700    03 FILLER              PIC X(03)  VALUE  SPACES.
        sb.append(formatUtil.padX(" ", 3));
        // 014800    03 RPT-DT-LRATE        PIC ZZZ,ZZZ,ZZ9.
        sb.append(formatUtil.padX(dAmt2Format.format(wkLrate), 11));
        // 014900    03 FILLER              PIC X(01)  VALUE  SPACES.
        sb.append(formatUtil.padX(" ", 1));
        // 015000    03 RPT-DT-AMT          PIC Z,ZZZ,ZZZ,ZZ9.
        sb.append(formatUtil.padX(dAmt1Format.format(kputhAmt), 13));
        // 015100    03 FILLER              PIC X(02)  VALUE  SPACES.
        sb.append(formatUtil.padX(" ", 2));
        // 015200    03 RPT-DT-USERDATA     PIC X(40)  VALUE  SPACES.
        sb.append(formatUtil.padX(kputhUserdata, 40));
        fileC060M2Contents.add(sb.toString());
        // 030700
        // 030800 3000-WRITEM-EXIT.
        // 030900     EXIT.
    }

    private void moveErrorResponse(LogicException e) {
        //        event.setPeripheryRequest();
    }
}
