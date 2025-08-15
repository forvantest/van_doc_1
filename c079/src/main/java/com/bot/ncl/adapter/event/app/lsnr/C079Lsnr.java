/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.C079;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.ncl.util.sort.ExternalSortUtil;
import com.bot.ncl.util.sort.eum.SortBy;
import com.bot.ncl.util.sort.vo.KeyRange;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.date.DateDto;
import com.bot.txcontrol.util.date.DateUtil;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.CutAndCount;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.transmit.FsapSyncSftpService;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("C079Lsnr")
@Scope("prototype")
public class C079Lsnr extends BatchListenerCase<C079> {

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    @Autowired private TextFileUtil textFile;
    @Autowired private ExternalSortUtil externalSortUtil;
    @Autowired private FsapBatchUtil fsapBatchUtil;

    @Autowired private ReportUtil reportUtil;
    @Autowired private Parse parse;
    @Autowired private FormatUtil formatUtil;
    @Autowired private DateUtil dateUtil;
    private C079 event;
    private String processDate; // 作業日期(民國年yyyymmdd)
    private static final String CHARSET = "Big5";
    // 排序後檔案的路徑
    private String sortTmpFilePath = "";
    // 讀取檔案(暫存檔)的路徑
    private String readFilePath = "";
    // 輸出結果的路徑
    private String writeFilePath = "";
    private List<String> fileContents;
    private StringBuilder sb = new StringBuilder();
    private Map<String, String> labelMap;
    private Map<String, String> textMap;

    // 01  RPT-NAME.
    //     03 FILLER                PIC X(18)  VALUE "BD/CL/BH/C079.".
    //
    // 01  WK-DATE                  PIC 9(07).
    // 01  WK-CNT                   PIC 9(08).
    // 01  WK-SEQNO                 PIC 9(06).
    // 01  WK-TXAMT                 PIC X(13).
    // 01  WK-TXAMT-R   REDEFINES   WK-TXAMT.
    //     03 WK-TXAMT-INT          PIC 9(11)V99.
    // 01  WK-KINBR                 PIC 9(03).
    // 01  WK-G6120-CNT             PIC 9(06).
    // 01  WK-G6121-CNT             PIC 9(06).
    // 01  WK-G6122-CNT             PIC 9(06).
    // 01  WK-CLNDR-KEY             PIC 9(03).
    // 01  WK-CLNDR-STUS            PIC X(02).
    // 01  WK-SEQ                   PIC 9(04).
    // 77  WK-RPTFLG                PIC 9(01).
    private static final String FILE_NAME = "CL-BH-C079";
    private static final String FILE_TMP_NAME = "TMPC079"; // tmp檔名
    private static final String readFILE_NAME = "EXDTL";
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;

    //    private int wkDATE = 0;
    //    private int wkCNT = 0;
    //    private int wkSEQNO = 0;
    //    private BigDecimal wkTXAMT = BigDecimal.ZERO;
    private int wkKINBR = 0;
    private int wkG6120_CNT = 0;
    private int wkG6121_CNT = 0;
    private int wkG6122_CNT = 0;
    private int wkSEQ = 0;
    //    private int wkCLNDR_KEY = 0;
    //    private String wkCLNDR_STUS = "";
    // 營業日/非營業日記號
    private String wkRPTFLG;

    // WK-YYMMDD
    private String tbsdy;

    // FD-BHDATE-NBSDY
    private int nbsdy;

    // FD-BHDATE-LBSDY
    private int lbsdy;

    // FD-BHDATE-FNBSDY
    private int fnbsdy;

    private int page = 0;
    private int row = 0;
    private final DecimalFormat dFormatNum = new DecimalFormat("#,##0");
    //    private final DecimalFormat dFormatNum000 = new DecimalFormat("#,##0.  ");
    // BB是空白、9是數字0(補0) 等於程式的0、Z是空白 等於程式的#
    private final DecimalFormat dFormatNum00 = new DecimalFormat("#,###.  ");

    List<HashMap<String, Object>> readDataResult = new ArrayList<HashMap<String, Object>>();

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(C079 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C079Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(C079 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C079Lsnr run()");

        // 檢查執行批次日期是否為本週最後一天營業日(目前以星期五為本周末最後一天營業日)
        if (init(event)) {
            //            testFile();
            sortFileContent(); // 讀取原檔，排序後產生一個暫存檔
            readFile();
            printContent(readDataResult);
            writeFile();
        } else {
            printContentEmpty();
            writeFile();
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "批次執行日期非本週最後一天營業日，不執行");
        }
        batchResponse();
    }

    private boolean init(C079 event) {
        this.event = event;
        fileContents = new ArrayList<>();
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        // 作業日期(民國年yyyymmdd)
        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        // wkRPTFLG = attributes.get("WK_RPTFLG"); // TODO: 待確認BATCH參數名稱
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "processDate = " + processDate);
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "getWeek = " + getWeek(parse.string2Integer(processDate)));

        // 原始檔案路徑+檔名
        String readFdDir = fileDir + CONVF_DATA + PATH_SEPARATOR + processDate;
        readFilePath = readFdDir + PATH_SEPARATOR + readFILE_NAME;
        textFile.deleteFile(readFilePath);
        String sourceFtpPath =
                "NCL"
                        + File.separator
                        + tbsdy
                        + File.separator
                        + "2FSAP"
                        + File.separator
                        + "DATA"
                        + File.separator
                        + readFILE_NAME; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, readFdDir);
        if (sourceFile != null) {
            readFilePath = getLocalPath(sourceFile);
        }

        // 輸出檔案路徑+檔名
        writeFilePath =
                fileDir + CONVF_RPT + PATH_SEPARATOR + processDate + PATH_SEPARATOR + FILE_NAME;
        // 讀檔前先刪除原本的檔案
        textFile.deleteFile(writeFilePath);

        // 暫存檔案路徑+檔名

        sortTmpFilePath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + FILE_TMP_NAME;
        // 讀檔前先刪除原本的檔案
        textFile.deleteFile(sortTmpFilePath);

        //        tbsdy = event.getAggregateBuffer().getTxCom().getTbsdy();
        //        nbsdy = event.getAggregateBuffer().getTxCom().getNbsdy();
        //        fnbsdy = event.getAggregateBuffer().getTxCom().getFnbsdy();
        //        lbsdy = event.getAggregateBuffer().getTxCom().getLbsdy();
        //        lputdt = 0;
        if (!textFile.exists(readFilePath)) {
            return false;
        }

        List<String> lines = textFile.readFileContent(readFilePath, CHARSET);

        // 如果為星期五(本週最後一天營業日) 回傳true 執行後續動作，反之 回傳false
        // 是本週最後一天營業日 但是空的 也是回傳false
        // wkRPTFLG
        if (getWeek(parse.string2Integer(processDate)) == 5) {
            return !lines.isEmpty();
        } else {
            return false;
        }
    }

    /** 列印表頭 */
    private void printHeader() {

        // E HEADING
        // 01 TYPE IS PH.
        //    03 LINE 3.
        //       05  COLUMN  28 PIC X(32)
        //         VALUE " 代收稅款交易更正成功明細資料表 ".
        // 空到第三行列印表頭名稱
        fileContents.add("");
        fileContents.add("");

        sb = new StringBuilder();
        String titleName = " 代收稅款交易更正成功明細資料表 ";
        sb.append(formatUtil.padX(" ", 27));
        sb.append(titleName);
        fileContents.add(sb.toString());

        //    03 LINE PLUS 1.
        //       05  COLUMN   1  PIC X(10)  VALUE " 分行別： ".
        //       05  COLUMN  13  PIC X(03)  SOURCE SD-KINBR.
        //       05  COLUMN  67  PIC X(12)  VALUE " 報表名稱： ".
        //       05  COLUMN  81  PIC X(04)  VALUE "C079".
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 分行別： ", 10));
        sb.append(formatUtil.padX(" ", 3));
        sb.append(formatUtil.padX(formatUtil.pad9("" + wkKINBR, 3), 3));
        sb.append(formatUtil.padX(" ", 51));
        sb.append(formatUtil.padX(" 報表名稱： ", 12));
        sb.append(formatUtil.padX(" ", 2));
        sb.append(formatUtil.padX("C079", 4));
        fileContents.add(sb.toString());

        //    03 LINE PLUS 1.
        //       05  COLUMN   1  PIC X(12)  VALUE  " 印表日期： ".
        //       05  COLUMN  13  PIC 999/99/99 SOURCE WK-DATE.
        //       05  COLUMN  67  PIC X(12)  VALUE  " 頁　　數： ".
        //       05  COLUMN  80  PIC Z,ZZZ SOURCE PAGE-COUNTER.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 印表日期： ", 12));
        sb.append(formatUtil.padX(" ", 1));
        sb.append(reportUtil.customFormat(tbsdy, "999/99/99"));
        sb.append(formatUtil.padX(" ", 45));
        sb.append(formatUtil.padX(" 頁　　數： ", 12));
        sb.append(formatUtil.padX(" ", 1));
        sb.append((formatUtil.padLeft(dFormatNum.format(page), 5)));
        fileContents.add(sb.toString());

        //    03 LINE PLUS 2.
        //       05  COLUMN   1 PIC X(06)  VALUE  " 序號 ".
        //       05  COLUMN   8 PIC X(10)  VALUE  " 交易日期 ".
        //       05  COLUMN  19 PIC X(10)  VALUE  " 交易時間 ".
        //       05  COLUMN  30 PIC X(08)  VALUE  " 主管卡 ".
        //       05  COLUMN  39 PIC X(06)  VALUE  " 櫃員 ".
        //       05  COLUMN  46 PIC X(10)  VALUE  " 交易序號 ".
        //       05  COLUMN  57 PIC X(10)  VALUE  " 交易代號 ".
        //       05  COLUMN  71 PIC X(15)  VALUE  " 金額 ".
        //       05  COLUMN  88 PIC X(06)  VALUE  " 借貸 ".
        fileContents.add("");
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 序號 ", 6));
        sb.append(formatUtil.padX(" ", 2));
        sb.append(formatUtil.padX(" 交易日期 ", 10));
        sb.append(formatUtil.padX(" ", 1));
        sb.append(formatUtil.padX(" 交易時間 ", 10));
        sb.append(formatUtil.padX(" ", 1));
        sb.append(formatUtil.padX(" 主管卡 ", 8));
        sb.append(formatUtil.padX(" ", 1));
        sb.append(formatUtil.padX(" 櫃員 ", 6));
        sb.append(formatUtil.padX(" ", 1));
        sb.append(formatUtil.padX(" 交易序號 ", 10));
        sb.append(formatUtil.padX(" ", 1));
        sb.append(formatUtil.padX(" 交易代號 ", 10));
        sb.append(formatUtil.padX(" ", 4));
        sb.append(formatUtil.padX(" 金額 ", 15));
        sb.append(formatUtil.padX(" ", 2));
        sb.append(formatUtil.padX(" 借貸 ", 6));
        fileContents.add(sb.toString());

        //    03 LINE PLUS 1.
        //       05  COLUMN   1 PIC X(95) VALUE ALL "=".
        sb = new StringBuilder();
        StringBuilder dash = new StringBuilder();
        dash.append("=".repeat(95));
        sb.append(dash);
        fileContents.add(sb.toString());

        row = 8;
    }

    private void testFile() {
        String sourceSample =
                "20240726143711902045100240316998877G6120ABCDE090099999999999990112072511300212    "
                        + "                       ";
        String vtext = "2024072614";
        int kk = 1000;
        String vtext2 = "902045100240316998877";
        String vtext3 = "G6120";
        String vtext4 = "ABCDE090099999999999990112072511300212                           ";

        //        sortTmpFilePath = fileDir + FILE_TMP_NAME;

        for (int i = 0; i <= 100; i++) {
            sb = new StringBuilder();
            sb.append(vtext).append(kk + i).append(vtext2).append(vtext3).append(vtext4);
            fileContents.add(sb.toString());
        }

        try {
            textFile.writeFileContent(readFilePath, fileContents, CHARSET);
            upload(readFilePath, "RPT", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }

        fileContents = new ArrayList<String>();
    }

    private void sortFileContent() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "sortFileContent ....");

        File sortTmpFileIn = new File(readFilePath);
        // 暫存檔案名稱 TMPC079
        File sortTmpFileOut = new File(sortTmpFilePath);
        List<KeyRange> keyRanges = new ArrayList<>();
        // SORT SORTFL
        //          ON ASCENDING KEY SD-PBRNO  SD-KINBR   SD-TLRNO
        //                           SD-TXDAY  SD-TXTIME  SD-TXCODE

        // SD-PBRNO  SD-KINBR = KINBR CutAndCount.stringCutBaseOnBig5(line, 14,3));
        // TLRNO CutAndCount.stringCutBaseOnBig5(line, 31, 2)
        // TXDAY  CutAndCount.stringCutBaseOnBig5(line, 0, 8)
        // TXTIME  CutAndCount.stringCutBaseOnBig5(line, 8, 6)
        // TXCODE   CutAndCount.stringCutBaseOnBig5(line, 35, 5))
        // 注意：這裡開始位數有包含 與subString不一樣(會多1位)
        keyRanges.add(new KeyRange(15, 3, SortBy.ASC));
        keyRanges.add(new KeyRange(32, 2, SortBy.ASC));
        keyRanges.add(new KeyRange(1, 8, SortBy.ASC));
        keyRanges.add(new KeyRange(9, 6, SortBy.ASC));
        keyRanges.add(new KeyRange(36, 5, SortBy.ASC));

        externalSortUtil.sortingFile(sortTmpFileIn, sortTmpFileOut, keyRanges);
    }

    private void printContentEmpty() {
        wkKINBR = 0;
        page = 1;
        printHeader();
        fileContents.add("");
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 20));
        sb.append(formatUtil.padX(" 本日全行無代收稅款更正明細 ", 28));
        fileContents.add(sb.toString());
        row = 9;
        printFooter();
    }

    private void printContent(List<HashMap<String, Object>> data) {

        //       05 COLUMN   2 PIC 9(4)                 SOURCE WK-SEQ.
        //       05 COLUMN   8 PIC X(08)                SOURCE SD-TXDAY.
        //       05 COLUMN  21 PIC 9(06)                SOURCE SD-TXTIME.
        //       05 COLUMN  33 PIC X(02)                SOURCE SD-SUPNO.
        //       05 COLUMN  41 PIC X(02)                SOURCE SD-TLRNO.
        //       05 COLUMN  49 PIC X(04)                SOURCE SD-TXTNO.
        //       05 COLUMN  60 PIC X(06)                SOURCE SD-TXCODE.
        //       05 COLUMN  69 PIC ZZ,ZZZ,ZZZ,ZZZ.ZZ    SOURCE SD-TXAMT.
        //       05 COLUMN  89 PIC X(04)                SOURCE SD-CRDB.

        // AIL
        // 01 DT-NODATA TYPE IS DE.
        //    03 LINE PLUS 2.
        //      05 COLUMN 20 PIC X(28) VALUE " 本日全行無代收稅款更正明細 ".
        page++;

        if (data.isEmpty()) {
            printContentEmpty();
        } else {
            int dataTotalCnt = 0;
            for (HashMap<String, Object> r : data) {
                // 資料總筆數需先算
                dataTotalCnt++;

                // 上一筆與現在的分行別是否一樣
                boolean isNotNextPage;
                if (parse.string2Integer(
                                CutAndCount.stringCutBaseOnBig5(r.get("TRMNO").toString(), 0, 3))
                        != wkKINBR) {
                    wkKINBR =
                            parse.string2Integer(
                                    CutAndCount.stringCutBaseOnBig5(
                                            r.get("TRMNO").toString(), 0, 3));
                    isNotNextPage = true;
                    // 換分行頁數重新算
                    page = 1;

                } else {
                    isNotNextPage = false;
                }

                // 第一次印表頭
                if (dataTotalCnt == 1) {
                    printHeader();
                }

                // 行數 在表頭之後
                row++;

                // 分行別不同印表尾(表尾是上一頁的)並換頁 第一次除外
                if (isNotNextPage && dataTotalCnt > 1) {
                    printFooter();
                    sb = new StringBuilder();
                    sb.append("\u000c");
                    fileContents.add(sb.toString());
                    isNotNextPage = false;
                    wkSEQ = 0;
                    printHeader();
                }

                wkSEQ++;

                if ("G6120".equals(r.get("TXCODE").toString())) {
                    wkG6120_CNT++;
                }
                if ("G6121".equals(r.get("TXCODE").toString())) {
                    wkG6121_CNT++;
                }
                if ("G6122".equals(r.get("TXCODE").toString())) {
                    wkG6122_CNT++;
                }

                //       05 COLUMN   2 PIC 9(4)                 SOURCE WK-SEQ.
                //       05 COLUMN   8 PIC X(08)                SOURCE SD-TXDAY.
                //       05 COLUMN  21 PIC 9(06)                SOURCE SD-TXTIME.
                //       05 COLUMN  33 PIC X(02)                SOURCE SD-SUPNO.
                //       05 COLUMN  41 PIC X(02)                SOURCE SD-TLRNO.
                //       05 COLUMN  49 PIC X(04)                SOURCE SD-TXTNO.
                //       05 COLUMN  60 PIC X(06)                SOURCE SD-TXCODE.
                //       05 COLUMN  69 PIC ZZ,ZZZ,ZZZ,ZZZ.ZZ    SOURCE SD-TXAMT.
                //       05 COLUMN  89 PIC X(04)                SOURCE SD-CRDB.
                sb = new StringBuilder();
                // 第2位開始
                sb.append(formatUtil.padX(" ", 1));
                sb.append(formatUtil.pad9(wkSEQ + "", 4));
                sb.append(formatUtil.padX(" ", 3));
                sb.append(formatUtil.padX(r.get("TXDAY").toString(), 8));
                sb.append(formatUtil.padX(" ", 5));
                sb.append(
                        formatUtil.padX(
                                CutAndCount.stringCutBaseOnBig5(r.get("TXTIME").toString(), 0, 6),
                                6));
                sb.append(formatUtil.padX(" ", 6));
                sb.append(formatUtil.padX(r.get("SUPNO").toString(), 2));
                sb.append(formatUtil.padX(" ", 6));
                sb.append(formatUtil.padX(r.get("TLRNO").toString(), 2));
                sb.append(formatUtil.padX(" ", 6));
                sb.append(formatUtil.padX(r.get("TXTNO").toString(), 4));
                sb.append(formatUtil.padX(" ", 7));
                sb.append(formatUtil.padX(r.get("TXCODE").toString(), 6));
                sb.append(formatUtil.padX(" ", 3));
                String txAmt = "";
                if ("0000000000000".equals(r.get("TXAMT"))) {
                    txAmt = "             ";
                } else {
                    txAmt = dFormatNum00.format(parse.string2BigDecimal(r.get("TXAMT").toString()));
                }
                sb.append(formatUtil.padLeft(txAmt, 17));
                sb.append(formatUtil.padX(" ", 3));
                sb.append(formatUtil.padX(r.get("CRDB").toString(), 4));
                fileContents.add(sb.toString());

                // 資料到第53行 且當前資料筆數需小於資料總數 才需換頁 印表頭(最後一筆的時候不需換表頭和換頁)
                if (row == 53 && dataTotalCnt < data.size()) {
                    sb = new StringBuilder();
                    sb.append("\u000c");
                    fileContents.add(sb.toString());
                    page++;
                    printHeader();
                }

                // 最後一筆要印表尾
                if (dataTotalCnt == data.size()) {
                    printFooter();
                }
            }
        }
    }

    private void printFooter() {

        // TROL FOOTING
        // 01 CF-KINBR TYPE IS CF SD-KINBR.
        //    03 LINE  60  .
        //       05 COLUMN  01 PIC X(90)  VALUE  SPACES.
        // 01 CF-PBRNO  TYPE IS CF SD-PBRNO NEXT GROUP NEXT PAGE.
        //    03 LINE  PLUS 2.
        //       05 COLUMN  01 PIC X(95)  VALUE  ALL "=".
        //    03 LINE  PLUS 2 .
        //       05 COLUMN  20 PIC X(32)
        //          VALUE " 【分行代收稅款更正成功交易統計 ".
        //       05 COLUMN  51 PIC X(04)  VALUE " 】 ".
        //    03 LINE PLUS 1.
        //       05 COLUMN  20 PIC X(10)  VALUE " 【 G6120 ".
        //       05 COLUMN  29 PIC X(04)  VALUE " 共 ".
        //       05 COLUMN  34 PIC ZZZ,ZZ9
        //          SUM SD-G6120-CNT RESET ON SD-PBRNO.
        //       05 COLUMN  42 PIC X(04)  VALUE " 筆 ".
        //       05 COLUMN  51 PIC X(04)  VALUE " 】 ".
        //    03 LINE PLUS 1.
        //       05 COLUMN  20 PIC X(10)  VALUE " 【 G6121 ".
        //       05 COLUMN  29 PIC X(04)  VALUE " 共 ".
        //       05 COLUMN  34 PIC ZZZ,ZZ9
        //          SUM SD-G6121-CNT RESET ON SD-PBRNO.
        //       05 COLUMN  42 PIC X(04)  VALUE " 筆 ".
        //       05 COLUMN  51 PIC X(04)  VALUE " 】 ".
        //    03 LINE PLUS 1.
        //       05 COLUMN  20 PIC X(10)  VALUE " 【 G6122 ".
        //       05 COLUMN  29 PIC X(04)  VALUE " 共 ".
        //       05 COLUMN  34 PIC ZZZ,ZZ9
        //          SUM SD-G6122-CNT RESET ON SD-PBRNO.
        //       05 COLUMN  42 PIC X(04)  VALUE " 筆 ".
        //       05 COLUMN  51 PIC X(04)  VALUE " 】 ".
        //    03 LINE PLUS 4.
        //       05 COLUMN  54 PIC X(10)  VALUE " 經　辦： ".
        //       05 COLUMN  72 PIC X(10)  VALUE " 主　管： ".

        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "print space row = " + row);
        // 現在45行 未滿60，會空行到第59行結束
        if (row + 1 < 60) {
            for (int i = row + 1; i < 60; i++) {
                row++;
                fileContents.add("");
            }
        }
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "print footer.row = " + row);

        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 90));
        fileContents.add(sb.toString());

        fileContents.add(sb.toString());

        sb = new StringBuilder();
        sb.append("=".repeat(95));
        fileContents.add(sb.toString());

        fileContents.add("");

        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 20));
        sb.append(formatUtil.padX(" 【分行代收稅款更正成功交易統計 ", 32));
        sb.append(formatUtil.padX(" 】 ", 4));
        fileContents.add(sb.toString());

        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 20));
        sb.append(formatUtil.padX(" 【 G6120 ", 10));
        sb.append(formatUtil.padX(" 共 ", 4));
        sb.append(formatUtil.padX(" ", 1));
        sb.append(formatUtil.padLeft(dFormatNum.format(wkG6120_CNT), 7));
        sb.append(formatUtil.padX(" ", 1));
        sb.append(formatUtil.padX(" 筆 ", 4));
        sb.append(formatUtil.padX(" ", 5));
        sb.append(formatUtil.padX(" 】 ", 4));
        wkG6120_CNT = 0;
        fileContents.add(sb.toString());

        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 20));
        sb.append(formatUtil.padX(" 【 G6121 ", 10));
        sb.append(formatUtil.padX(" 共 ", 4));
        sb.append(formatUtil.padX(" ", 1));
        sb.append(formatUtil.padLeft(dFormatNum.format(wkG6121_CNT), 7));
        sb.append(formatUtil.padX(" ", 1));
        sb.append(formatUtil.padX(" 筆 ", 4));
        sb.append(formatUtil.padX(" ", 5));
        sb.append(formatUtil.padX(" 】 ", 4));
        wkG6121_CNT = 0;
        fileContents.add(sb.toString());

        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 20));
        sb.append(formatUtil.padX(" 【 G6122 ", 10));
        sb.append(formatUtil.padX(" 共 ", 4));
        sb.append(formatUtil.padX(" ", 1));
        sb.append(formatUtil.padLeft(dFormatNum.format(wkG6122_CNT), 7));
        sb.append(formatUtil.padX(" ", 1));
        sb.append(formatUtil.padX(" 筆 ", 4));
        sb.append(formatUtil.padX(" ", 5));
        sb.append(formatUtil.padX(" 】 ", 4));
        wkG6122_CNT = 0;
        fileContents.add(sb.toString());

        fileContents.add("");
        fileContents.add("");
        fileContents.add("");

        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 54));
        sb.append(formatUtil.padX(" 經　辦： ", 10));
        sb.append(formatUtil.padX(" ", 8));
        sb.append(formatUtil.padX(" 主　管： ", 10));
        fileContents.add(sb.toString());

        wkSEQ = 0;
    }

    /*
    * 03 FD-EXDTL-TXDAY                    PIC X(08).
    03 FD-EXDTL-TXTIME                   PIC 9(06).
    03 FD-EXDTL-TRMNO.
       05 FD-EXDTL-KINBR                 PIC X(03).
       05 FD-EXDTL-TRMSEQ                PIC X(04).
    03 FD-EXDTL-TXTNO                    PIC X(08).
    03 FD-EXDTL-TRMTYP                   PIC X(02).
    03 FD-EXDTL-TLRNO                    PIC X(02).
    03 FD-EXDTL-SUPNO                    PIC X(02).
    03 FD-EXDTL-TXCODE.
       05 FD-EXDTL-TXCD.
          07 FD-EXDTL-APTYPE             PIC X(01).
          07 FD-EXDTL-TXNO               PIC X(02).
       05 FD-EXDTL-STXNO                 PIC X(02).
    03 FD-EXDTL-DSCPT                    PIC X(05).
    03 FD-EXDTL-TXTYPE                   PIC X(02).
    03 FD-EXDTL-CRDB                     PIC X(01).
    03 FD-EXDTL-SPCD                     PIC X(01).
    03 FD-EXDTL-TXAMT                    PIC X(13).
    03 FD-EXDTL-CALDY                    PIC X(08).
    03 FD-EXDTL-CALTM                    PIC X(08).
    03 FILLER                            PIC X(27).
    *
    *
         03 SD-TXDAY             PIC X(08).
         03 SD-TXTIME            PIC 9(06).
         03 SD-KINBR             PIC X(03).	<-輸入行別
         03 SD-TRMSEQ            PIC X(04).	<-櫃檯機序號
         03 SD-TXTNO             PIC X(08).	<-交易傳輸編號
         03 SD-TRMTYP            PIC X(02).	<-櫃檯機種類
         03 SD-TLRNO             PIC X(02).	<-櫃員編號
         03 SD-SUPNO             PIC X(02).	<-主管櫃員代號
         03 SD-TXCODE            PIC X(05).	<-交易代號
         03 SD-DSCPT             PIC X(05).	<-交易摘要
         03 SD-TXTYPE            PIC X(02).	<-帳務別
         03 SD-CRDB              PIC X(04).	<-借貸別	0：借(提取交易)   1：貸(存入交易)
         03 SD-SPCD              PIC X(01).	<-主管許可記號	0：一般交易 1：主管核可交易
         03 SD-TXAMT             PIC 9(11)V99.
         03 SD-CALDY             PIC X(08).	<-實際交易日(西元日期日曆日)
         03 SD-CALTM             PIC X(08).	<-實際交易時間(系統時間)
         03 SD-CNT               PIC 9(01).
         03 SD-PBRNO             PIC X(03).
         03 SD-G6120-CNT         PIC 9(06).
         03 SD-G6121-CNT         PIC 9(06).
         03 SD-G6122-CNT         PIC 9(06).
    * */

    /** 讀取檔案 */
    private void readFile() {

        List<String> dataLines = textFile.readFileContent(sortTmpFilePath, CHARSET);

        if (dataLines.isEmpty()) {
            return;
        } else {

            ApLogHelper.info(
                    log, false, LogType.NORMAL.getCode(), "dataLines.size = " + dataLines.size());

            for (String line : dataLines) {

                if ("G6120".equals(CutAndCount.stringCutBaseOnBig5(line, 35, 5))
                        || "G6121".equals(CutAndCount.stringCutBaseOnBig5(line, 35, 5))
                        || "G6122".equals(CutAndCount.stringCutBaseOnBig5(line, 35, 5))) {

                    HashMap<String, Object> map = new HashMap<String, Object>();
                    // 交易日期
                    map.put("TXDAY", CutAndCount.stringCutBaseOnBig5(line, 0, 8));
                    // 交易時間
                    map.put("TXTIME", CutAndCount.stringCutBaseOnBig5(line, 8, 6));
                    // 輸入行別 + 櫃檯機序號 (TRMNO = KINBR + TRMSEQ)
                    //                map.put("KINBR", CutAndCount.stringCutBaseOnBig5(line, 14,
                    // 3));
                    //                map.put("TRMSEQ", CutAndCount.stringCutBaseOnBig5(line, 17,
                    // 4));
                    map.put("TRMNO", CutAndCount.stringCutBaseOnBig5(line, 14, 7));
                    // 交易傳輸編號
                    map.put("TXTNO", CutAndCount.stringCutBaseOnBig5(line, 21, 8));
                    // 櫃檯機種類
                    map.put("TRMTYP", CutAndCount.stringCutBaseOnBig5(line, 29, 2));
                    // 櫃員編號
                    map.put("TLRNO", CutAndCount.stringCutBaseOnBig5(line, 31, 2));
                    // 主管櫃員代號
                    map.put("SUPNO", CutAndCount.stringCutBaseOnBig5(line, 33, 2));
                    // 交易代號 TXCODE =TXCD(APTYPE+TXNO)+STXNO
                    //                map.put("APTYPE", CutAndCount.stringCutBaseOnBig5(line, 35,
                    // 1));
                    //                map.put("TXNO", CutAndCount.stringCutBaseOnBig5(line, 36, 2));
                    //                map.put("STXNO", CutAndCount.stringCutBaseOnBig5(line, 38,
                    // 2));
                    map.put("TXCODE", CutAndCount.stringCutBaseOnBig5(line, 35, 5));

                    // 交易摘要
                    map.put("DSCPT", CutAndCount.stringCutBaseOnBig5(line, 40, 5));
                    // 帳務別
                    map.put("TXTYPE", CutAndCount.stringCutBaseOnBig5(line, 45, 2));
                    // 借貸別
                    map.put("CRDB", CutAndCount.stringCutBaseOnBig5(line, 47, 1));
                    // 主管許可記號
                    map.put("SPCD", CutAndCount.stringCutBaseOnBig5(line, 48, 1));
                    // 交易金額
                    map.put("TXAMT", CutAndCount.stringCutBaseOnBig5(line, 49, 13));
                    // 實際交易日
                    map.put("CALDY", CutAndCount.stringCutBaseOnBig5(line, 62, 8));
                    // 實際交易時間
                    map.put("CALTM", CutAndCount.stringCutBaseOnBig5(line, 70, 8));
                    // Filler
                    //                map.put("FILLER", CutAndCount.stringCutBaseOnBig5(line, 98,
                    // 27));

                    readDataResult.add(map);
                }
            }
        }
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "readDataResult = " + readDataResult.toString());
    }

    private int getWeek(int inputDate) {
        DateDto dateDto = new DateDto();
        dateDto.setDateS(inputDate);
        dateUtil.getCalenderDay(dateDto);
        return dateDto.getDayOfWeek();
    }

    private void writeFile() {
        try {
            textFile.writeFileContent(writeFilePath, fileContents, CHARSET);
            upload(writeFilePath, "RPT", "");

            // 使用完把暫存檔刪除。
            textFile.deleteFile(sortTmpFilePath);

        } catch (LogicException e) {
            moveErrorResponse(e);
        }
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

    /** 日期 格式 ex: 1120101 => 112/01/01 ex: 20240101 => 2024/01/01 */
    private String dateFormat(int date) {
        String sdate;
        int yy = 0;
        int mm = 0;
        int dd = 0;
        if (date > 0) {
            yy = date / 10000;
            mm = date / 100 % 100;
            dd = date % 100;
        }
        // 判斷西元或民國年
        if (yy <= 1911) {
            sdate = formatUtil.pad9("" + yy, 3);
        } else {
            sdate = formatUtil.pad9("" + yy, 4);
        }
        // xxxx/
        sdate = sdate + "/" + formatUtil.pad9("" + mm, 2) + "/" + formatUtil.pad9("" + dd, 2);

        return sdate;
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

    private void batchResponse() {
        Map<String, String> responseTextMap = new HashMap<>(textMap);
        responseTextMap.put("RPTNAME", FILE_NAME);

        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", responseTextMap);
    }
}
