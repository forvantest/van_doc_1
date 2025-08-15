/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.C080;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.ncl.util.sort.ExternalSortUtil;
import com.bot.ncl.util.sort.eum.SortBy;
import com.bot.ncl.util.sort.vo.KeyRange;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.buffer.mg.Bctl;
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
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("C080Lsnr")
@Scope("prototype")
public class C080Lsnr extends BatchListenerCase<C080> {

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    @Autowired private TextFileUtil textFile;
    @Autowired private ExternalSortUtil externalSortUtil;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    @Autowired private Parse parse;
    @Autowired private ReportUtil reportUtil;
    @Autowired private FormatUtil formatUtil;
    @Autowired private DateUtil dateUtil;
    private C080 event;
    private String processDate; // 批次日期(民國年yyyymmdd)
    private static final String CHARSET = "Big5";
    // 排序後檔案的路徑
    private String sortTmpFilePath = "";
    // 讀取檔案(暫存檔)的路徑
    private String readFilePath = "";
    // 輸出結果的路徑
    private String writeFilePath = "";
    private List<String> fileContents;
    private StringBuilder sb = new StringBuilder();

    // 01  RPT-NAME.
    //     03 FILLER                PIC X(18)  VALUE "BD/CL/BH/C080.".
    //
    // 01  WK-DATE                  PIC 9(07).
    // 01  WK-BDATE                 PIC 9(07) VALUE 0.
    // 01  WK-EDATE                 PIC 9(07) VALUE 0.
    // 01  WK-YYYMM                 PIC X(05) VALUE SPACES.
    // 01  WK-CNT                   PIC 9(08).
    // 01  WK-SEQNO                 PIC 9(06).
    // 01  WK-TXAMT                 PIC X(13).
    // 01  WK-TXAMT-R   REDEFINES   WK-TXAMT.
    //     03 WK-TXAMT-INT          PIC 9(11)V99.
    // 01  WK-KINBR                 PIC 9(03).
    // 01  WK-G6120-CNT             PIC 9(06).
    // 01  WK-G6121-CNT             PIC 9(06).
    // 01  WK-G6122-CNT             PIC 9(06).
    // 01  WK-G612X-CNT             PIC 9(06).
    // 01  WK-CLNDR-KEY             PIC 9(03).
    // 01  WK-CLNDR-STUS            PIC X(02).
    // 01  WK-SEQ                   PIC 9(04).
    // 01  WK-BRNO                  PIC 9(03).
    // 77  WK-RPTFLG                PIC 9(01).
    // 77  WK-BCTL-FLG              PIC 9(01) VALUE 0.
    // 01  WK-TAXTOT.
    //  05  WK-TAXTOTR OCCURS 999 TIMES.
    //     11 WK-G6120TAX          PIC 9(06).
    //     11 WK-G6121TAX          PIC 9(06).
    //     11 WK-G6122TAX          PIC 9(06).
    //     11 WK-G612XTAX          PIC 9(06).
    private static final String FILE_NAME = "CL-BH-C080";
    private static final String FILE_TMP_NAME = "TMPC080"; // tmp檔名
    private static final String readFILE_NAME = "EXDTL";
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;

    private Map<String, String> labelMap;
    private Map<String, String> textMap;

    private int wkBDATE = 0;
    private int wkEDATE = 0;
    private int wkDATE = 0;
    private int wkYYYMM = 0;
    private int wkBRNO = 0;
    private int wkSEQ = 0;
    //    private BigDecimal wkTXAMT = BigDecimal.ZERO;
    private int wkKINBR = 0;
    private int wkG6120_CNT = 0;
    private int wkG6121_CNT = 0;
    private int wkG6122_CNT = 0;
    private int wkG612X_CNT = 0;
    //    private int wkSEQ = 0;
    //    private int wkCLNDR_KEY = 0;
    //    private String wkCLNDR_STUS = "";
    // 營業日/非營業日記號
    private int wkRPTFLG = 0;

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
    // B是空白、9是數字0(補0) 等於程式的0、Z是空白等於程式的#
    private final DecimalFormat dFormatNum00 = new DecimalFormat("#,###.  ");

    List<HashMap<String, Object>> readDataResult = new ArrayList<HashMap<String, Object>>();

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(C080 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C080Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(C080 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C080Lsnr run()");

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

    private boolean init(C080 event) {
        this.event = event;
        fileContents = new ArrayList<>();
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        // 作業日期(民國年yyyymmdd)
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱

        tbsdy = labelMap.get("PROCESS_DATE");
        wkDATE = parse.string2Integer(processDate);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "PROCESS_DATE = " + processDate);
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
        //     IF FD-BHDATE-WEEKDY  = 5
        //        MOVE  1 TO  WK-RPTFLG
        //        GO TO 1000-CHKDATE-EXIT.
        if (getWeek(parse.string2Integer(processDate)) == 5) {
            wkRPTFLG = 1;
            return !lines.isEmpty();
        } else {
            return false;
        }
    }

    /** 列印表頭 */
    private void printHeader() {

        //     03 LINE 3.
        //       05  COLUMN  28 PIC X(32)
        //         VALUE " 代收稅款交易更正成功彙總資料表 ".
        // 空到第三行列印表頭名稱
        fileContents.add("");
        fileContents.add("");
        sb = new StringBuilder();
        String titleName = "代收稅款交易更正成功彙總資料表";
        sb.append(formatUtil.padX(" ", 28));
        sb.append(titleName);
        fileContents.add(sb.toString());

        //    03 LINE PLUS 1.
        //       05  COLUMN   1  PIC X(12)  VALUE  " 印表日期： ".
        //       05  COLUMN  13  PIC 999/99/99 SOURCE WK-DATE.
        //       05  COLUMN  67  PIC X(12)  VALUE " 報表名稱： ".
        //       05  COLUMN  81  PIC X(04)  VALUE "C080".
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 印表日期： ", 12));
        sb.append(reportUtil.customFormat(tbsdy, "999/99/99"));
        sb.append(formatUtil.padX(" ", 45));
        sb.append(formatUtil.padX(" 報表名稱： ", 12));
        sb.append(formatUtil.padX(" ", 2));
        sb.append(formatUtil.padX("C080", 4));
        fileContents.add(sb.toString());

        //    03 LINE PLUS 1.
        //       05  COLUMN   1  PIC X(12)  VALUE  " 起迄日期： ".
        //       05  COLUMN  13  PIC 999/99/99 SOURCE WK-BDATE.
        //       05  COLUMN  22  PIC X(03)  VALUE " ~ ".
        //       05  COLUMN  26  PIC 999/99/99 SOURCE WK-EDATE.
        //       05  COLUMN  38  PIC X(12)  VALUE  " 所屬月份： ".
        //       05  COLUMN  51  PIC X(05)  SOURCE WK-YYYMM.
        //       05  COLUMN  67  PIC X(12)  VALUE  " 頁　　數： ".
        //       05  COLUMN  80  PIC Z,ZZZ SOURCE PAGE-COUNTER.
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 起迄日期： ", 12));
        sb.append(formatUtil.padX(dateFormat(wkBDATE), 9));
        sb.append(formatUtil.padX(" ~ ", 3));
        sb.append(formatUtil.padX(dateFormat(wkEDATE), 9));
        sb.append(formatUtil.padX(" ", 3));
        sb.append(formatUtil.padX(" 所屬月份： ", 12));
        sb.append(formatUtil.padX(" ", 11));
        sb.append(formatUtil.padX(wkYYYMM + "", 5));
        sb.append(formatUtil.padX(" ", 11));
        sb.append(formatUtil.padX(" 頁　　數： ", 12));
        sb.append(formatUtil.padX(" ", 1));
        sb.append((formatUtil.padLeft(dFormatNum.format(page), 4)));
        fileContents.add(sb.toString());

        //    03 LINE PLUS 2.
        //       05  COLUMN   1 PIC X(08)  VALUE  " 分行別 ".
        //       05  COLUMN  15 PIC X(15)  VALUE  "G6120 更正筆數 ".
        //       05  COLUMN  35 PIC X(15)  VALUE  "G6121 更正筆數 ".
        //       05  COLUMN  55 PIC X(15)  VALUE  "G6122 更正筆數 ".
        //       05  COLUMN  77 PIC X(12)  VALUE  " 更正總筆數 ".
        fileContents.add("");
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" 分行別 ", 8));
        sb.append(formatUtil.padX(" ", 6));
        sb.append(formatUtil.padX("G6120 更正筆數 ", 15));
        sb.append(formatUtil.padX(" ", 5));
        sb.append(formatUtil.padX("G6121 更正筆數 ", 15));
        sb.append(formatUtil.padX(" ", 5));
        sb.append(formatUtil.padX("G6122 更正筆數 ", 15));
        sb.append(formatUtil.padX(" ", 5));
        sb.append(formatUtil.padX(" 更正總筆數 ", 12));
        fileContents.add(sb.toString());

        //    03 LINE PLUS 1.
        //       05  COLUMN   1 PIC X(95) VALUE ALL "=".
        sb = new StringBuilder();
        sb.append("=".repeat(95));
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
        // 暫存檔案名稱 TMPC080
        File sortTmpFileOut = new File(sortTmpFilePath);
        List<KeyRange> keyRanges = new ArrayList<>();
        //  SORT SORTFL
        //          ON ASCENDING KEY SD-YYYMM  SD-PBRNO   SD-KINBR
        //                           SD-TLRNO  SD-TXTIME  SD-TXCODE
        //          DISK SIZE IS 9000000  WORDS

        // map.put("TXDAY", CutAndCount.stringCutBaseOnBig5(line, 0, 8));
        // map.put("KINBR", CutAndCount.stringCutBaseOnBig5(line, 14,3));
        // map.put("TLRNO", CutAndCount.stringCutBaseOnBig5(line, 31, 2));
        // map.put("TXTIME", CutAndCount.stringCutBaseOnBig5(line, 8, 6));
        //  map.put("TXCODE", CutAndCount.stringCutBaseOnBig5(line, 35, 5));
        // 注意：這裡開始位數有包含 與subString不一樣(會多1位)
        keyRanges.add(new KeyRange(1, 8, SortBy.ASC));
        keyRanges.add(new KeyRange(15, 3, SortBy.ASC));
        keyRanges.add(new KeyRange(32, 2, SortBy.ASC));
        keyRanges.add(new KeyRange(9, 6, SortBy.ASC));
        keyRanges.add(new KeyRange(36, 5, SortBy.ASC));

        externalSortUtil.sortingFile(sortTmpFileIn, sortTmpFileOut, keyRanges);
    }

    private void printContentEmpty() {
        // 01 DT-NODATA TYPE IS DE NEXT GROUP  NEXT PAGE.
        //    03 LINE PLUS 2.
        //      05 COLUMN 20 PIC X(28) VALUE  SPACES.
        //
        wkKINBR = 0;
        page = 1;
        printHeader();
        fileContents.add("");
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 20));
        sb.append(formatUtil.padX(" ", 28));
        fileContents.add(sb.toString());
        row = 9;
        printFooter();
    }

    private void printContent(List<HashMap<String, Object>> data) {

        page++;

        List<HashMap<String, Object>> tmpTXDAYList = new ArrayList<HashMap<String, Object>>();
        HashMap<String, Object> tmpSDTXDAY = new HashMap<String, Object>();
        if (data.isEmpty()) {
            printContentEmpty();
        } else {
            //  MOVE   FD-EXDTL-TXDAY   TO SD-TXDAY .
            //  MOVE   SD-TXDAY(2:5)    TO SD-YYYMM.

            int tmpDate = 0;
            int tmpYYMM = 0;
            int rDate = 0;
            boolean isFirstTime = false;
            // 第一次先找 起訖日 跟所屬月份
            for (HashMap<String, Object> r : data) {

                rDate = parse.string2Integer(r.get("TXDAY").toString());
                // 第一次
                if (tmpYYMM != rDate / 100) {
                    tmpYYMM = rDate / 100;

                    if (!tmpTXDAYList.isEmpty()) {
                        tmpTXDAYList.add(tmpSDTXDAY);
                    }

                    isFirstTime = true;
                } else {
                    // 同月份 不同天
                    if (tmpDate != rDate) {
                        tmpSDTXDAY.put("ETXDAY", rDate);
                    } else {
                        continue;
                    }
                }

                if (isFirstTime) {
                    // 第一筆會先放暫存 並放到List
                    tmpSDTXDAY = new HashMap<String, Object>();
                    tmpDate = rDate;
                    tmpSDTXDAY.put("STXDAY", rDate);
                    tmpSDTXDAY.put("YYYMM", tmpYYMM);
                    tmpTXDAYList.add(tmpSDTXDAY);
                    isFirstTime = false;
                }
            }
            // java8功能 去除重複
            tmpTXDAYList = tmpTXDAYList.stream().distinct().collect(Collectors.toList());

            ApLogHelper.info(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "tmpTXDAYList = {}",
                    tmpTXDAYList.toString());

            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "data = {}", data.toString());

            List<HashMap<String, Object>> tmpDataList = new ArrayList<HashMap<String, Object>>();
            HashMap<String, Object> tmpData = new HashMap<String, Object>();

            String rKINBR = "";
            int tmpCnt = 0;
            // 把在起訖日範圍的資料 分類
            for (HashMap<String, Object> r : tmpTXDAYList) {
                int sTXDAY = parse.string2Integer(r.get("STXDAY").toString());
                int eTXDAY = parse.string2Integer(r.get("ETXDAY").toString());
                int cG6120 = 0;
                int cG6121 = 0;
                int cG6122 = 0;
                boolean isEmpty = false;
                for (HashMap<String, Object> r2 : data) {

                    String r2KINBR = r2.get("TRMNO").toString().substring(0, 3);
                    rDate = parse.string2Integer(r2.get("TXDAY").toString());
                    String rTXCODE = r2.get("TXCODE").toString();
                    // 因排序已把分行別排在一起
                    if (sTXDAY <= rDate && rDate <= eTXDAY) {
                        tmpCnt++;

                        if (!rKINBR.equals(r2KINBR)) {

                            rKINBR = r2KINBR;
                            // 檢查原本放進去的資料是否已經有的
                            for (HashMap<String, Object> r3 : tmpDataList) {
                                ApLogHelper.info(
                                        log, false, LogType.NORMAL.getCode(), "r3 = {}", rKINBR);
                                if (r3.containsValue(rKINBR) && r3.containsValue(sTXDAY / 100)) {
                                    if ("G6120".equals(rTXCODE)) {
                                        if (r3.get("G6120Cnt") != null) {
                                            r3.put(
                                                    "G6120Cnt",
                                                    parse.string2Integer(
                                                                    r3.get("G6120Cnt").toString())
                                                            + 1);
                                        } else {

                                            r3.put("G6120Cnt", 1);
                                        }
                                    }
                                    if ("G6121".equals(rTXCODE)) {
                                        if (r3.get("G6121Cnt") != null) {
                                            r3.put(
                                                    "G6121Cnt",
                                                    parse.string2Integer(
                                                                    r3.get("G6121Cnt").toString())
                                                            + 1);
                                        } else {

                                            r3.put("G6121Cnt", 1);
                                        }
                                    }
                                    if ("G6122".equals(rTXCODE)) {
                                        if (r3.get("G6122Cnt") != null) {
                                            r3.put(
                                                    "G6122Cnt",
                                                    parse.string2Integer(
                                                                    r3.get("G6122Cnt").toString())
                                                            + 1);
                                        } else {
                                            r3.put("G6122Cnt", 1);
                                        }
                                    }
                                    isEmpty = true;
                                }
                            }

                            if (isEmpty) {
                                isEmpty = false;
                                continue;
                            }

                            // 不同分行別 且 第一筆以上
                            if (tmpCnt > 1) {
                                tmpDataList.add(tmpData);
                            }

                            cG6120 = 0;
                            cG6121 = 0;
                            cG6122 = 0;
                            tmpData = new HashMap<String, Object>();
                        }

                        tmpData.put("KINBR", rKINBR);
                        tmpData.put("YYYMM", sTXDAY / 100);

                        if ("G6120".equals(r2.get("TXCODE").toString())) {
                            cG6120++;
                            tmpData.put("G6120Cnt", cG6120);
                        }
                        if ("G6121".equals(r2.get("TXCODE").toString())) {
                            cG6121++;
                            tmpData.put("G6121Cnt", cG6121);
                        }
                        if ("G6122".equals(r2.get("TXCODE").toString())) {
                            cG6122++;
                            tmpData.put("G6122Cnt", cG6122);
                        }
                    }

                    if (tmpCnt == data.size()) {
                        tmpDataList.add(tmpData);
                    }
                }
            }

            ApLogHelper.info(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "tmpDataList = {}",
                    tmpDataList.toString());

            int dateCnt = 0;
            //            G6120Cnt=1, KINBR=912, YYYMM=11306

            // 調整排序
            tmpDataList.sort(
                    (c1, c2) -> {
                        if (parse.string2Integer(c1.get("YYYMM").toString())
                                        - parse.string2Integer(c2.get("YYYMM").toString())
                                != 0) {
                            return parse.string2Integer(c1.get("YYYMM").toString())
                                    - parse.string2Integer(c2.get("YYYMM").toString());
                        } else if (parse.string2Integer(c1.get("KINBR").toString())
                                        - parse.string2Integer(c2.get("KINBR").toString())
                                != 0) {
                            return parse.string2Integer(c1.get("KINBR").toString())
                                    - parse.string2Integer(c2.get("KINBR").toString());
                        } else {
                            return 0;
                        }
                    });

            ApLogHelper.info(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "tmpDataList2 = {}",
                    tmpDataList.toString());

            int yyymm = 0;
            String rKinbr = "";
            // 主要檔案輸出 段落
            for (HashMap<String, Object> r : tmpDataList) {
                dateCnt++;
                if (parse.string2Integer(r.get("YYYMM").toString()) != wkYYYMM) {
                    wkYYYMM = parse.string2Integer(r.get("YYYMM").toString());
                    // 找起訖年月日
                    for (HashMap<String, Object> r2 : tmpTXDAYList) {
                        if (r2.containsValue(wkYYYMM)) {
                            wkBDATE = parse.string2Integer(r2.get("STXDAY").toString());
                            wkEDATE = parse.string2Integer(r2.get("ETXDAY").toString());
                        }
                    }
                    // 不同月份 第一筆之後 才會換行
                    if (dateCnt > 1) {
                        printFooter();
                        sb = new StringBuilder();
                        sb.append("\u000c");
                        fileContents.add(sb.toString());
                        page++;
                        printHeader();
                    }
                }
                // 第一次印表頭
                if (dateCnt == 1) {
                    printHeader();
                }

                wkBRNO = parse.string2Integer(r.get("KINBR").toString());

                if (r.get("G6120Cnt") != null) {
                    wkG6120_CNT = parse.string2Integer(r.get("G6120Cnt").toString());
                }
                if (r.get("G6121Cnt") != null) {
                    wkG6121_CNT = parse.string2Integer(r.get("G6121Cnt").toString());
                }
                if (r.get("G6122Cnt") != null) {
                    wkG6122_CNT = parse.string2Integer(r.get("G6122Cnt").toString());
                }

                // 01 DT-1 TYPE IS DE.
                //    03 LINE PLUS 1.
                //       05 COLUMN   2 PIC 9(3)               SOURCE WK-BRNO.
                //       05 COLUMN  17 PIC ZZZ,ZZ9            SOURCE WK-G6120-CNT.
                //       05 COLUMN  37 PIC ZZZ,ZZ9            SOURCE WK-G6121-CNT.
                //       05 COLUMN  57 PIC ZZZ,ZZ9            SOURCE WK-G6122-CNT.
                //       05 COLUMN  72 PIC X(06)              VALUE "TOTAL:" .
                //       05 COLUMN  80 PIC ZZZ,ZZ9            SOURCE WK-G612X-CNT.
                sb = new StringBuilder();
                // 第2位開始
                sb.append(formatUtil.padX(" ", 1));
                sb.append(formatUtil.pad9(wkBRNO + "", 3));
                sb.append(formatUtil.padX(" ", 12));
                sb.append(formatUtil.padLeft(dFormatNum.format(wkG6120_CNT), 7));
                sb.append(formatUtil.padX(" ", 13));
                sb.append(formatUtil.padLeft(dFormatNum.format(wkG6121_CNT) + "", 7));
                sb.append(formatUtil.padX(" ", 13));
                sb.append(formatUtil.padLeft(dFormatNum.format(wkG6122_CNT) + "", 7));
                sb.append(formatUtil.padX(" ", 8));
                sb.append(formatUtil.padX("TOTAL:", 6));
                wkG612X_CNT = wkG6120_CNT + wkG6121_CNT + wkG6122_CNT;
                sb.append(formatUtil.padLeft(dFormatNum.format(wkG612X_CNT) + "", 7));
                fileContents.add(sb.toString());
                row++;

                wkG6120_CNT = 0;
                wkG6121_CNT = 0;
                wkG6122_CNT = 0;

                // 資料到第40行 且當前資料筆數需小於資料總數 才需換頁 印表頭(最後一筆的時候不需換表頭和換頁)
                if (row == 40 && dateCnt < tmpDataList.size()) {
                    printFooter();
                    sb = new StringBuilder();
                    sb.append("\u000c");
                    fileContents.add(sb.toString());
                    page++;
                    printHeader();
                }

                // 最後一筆要印表尾
                if (dateCnt == tmpDataList.size()) {
                    printFooter();
                }
            }
        }
    }

    private void printFooter() {

        // 01  CF-PAGE  TYPE IS CF SD-YYYMM  NEXT GROUP NEXT PAGE.
        //  03 LINE PLUS 1.
        //     05  COLUMN 1   PIC X(100) VALUE SPACES.
        // 01 CF-KINBR TYPE IS PF .
        //    03 LINE PLUS 1.
        //       05 COLUMN  54 PIC X(10)  VALUE " 經　辦： ".
        //       05 COLUMN  72 PIC X(10)  VALUE " 主　管： ".

        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 100));
        fileContents.add(sb.toString());

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
            //            textFile.deleteFile(sortTmpFilePath);

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

    private void checkBrno(int dbBrno) {
        // GET-BCTL-RTN.
        //
        //     FIND  NEXT  DB-BCTL-ACCESS   OF   DB-BCTL-DDS
        //           ON EXCEPTION
        //           IF DMSTATUS(NOTFOUND)
        //              DISPLAY "NO BCTL "
        //              MOVE    2  TO   WK-BCTL-FLG
        //              GO         TO   GET-BCTL-EXIT.

        //     IF  DB-BCTL-BRNO > 800
        //         GO TO GET-BCTL-EXIT.

        // 當前分行別大於800
        if (dbBrno > 800) {}

        // * 營業單位的才要列出來
        //     IF  DB-BCTL-BRNO = 100 OR 102 OR 149 OR 152 OR 158 OR 166
        //              OR 167 OR 168 OR 169 OR 177 OR 192 OR 196 OR 197
        //              OR 199 OR 209 OR 212 OR 213 OR 214 OR 217 OR 219
        //              OR 231 OR 234 OR 237 OR 250 OR 251 OR 254 OR 180
        //              OR DB-BCTL-BRNO NOT= FD-SBRNO-SBRNO(DB-BCTL-BRNO)
        //         GO TO GET-BCTL-RTN .
        List<Integer> listBrno =
                Arrays.asList(
                        100, 102, 149, 152, 158, 166, 167, 168, 169, 177, 192, 196, 197, 199, 209,
                        212, 213, 214, 217, 219, 231, 234, 237, 250, 251, 254, 180);
        // 當前分行別不包含list中的分行別
        if (!listBrno.contains(dbBrno)) {}

        //     IF  DB-BCTL-BRLVL=6 OR DB-BCTL-BRNO=003 OR 005 OR 007 OR 236
        //         MOVE DB-BCTL-BRNO    TO WK-BRNO
        //         MOVE WK-G6120TAX(DB-BCTL-BRNO) TO  WK-G6120-CNT
        //         MOVE WK-G6121TAX(DB-BCTL-BRNO) TO  WK-G6121-CNT
        //         MOVE WK-G6122TAX(DB-BCTL-BRNO) TO  WK-G6122-CNT
        //         MOVE WK-G612XTAX(DB-BCTL-BRNO) TO  WK-G612X-CNT
        //         GENERATE  DT-1.
        //
        //     GO TO GET-BCTL-RTN.

        Bctl bctl = event.getAggregateBuffer().getMgGlobal().getBctl(dbBrno);
        if (bctl == null) {
            return;
        }
        if (parse.string2Integer(bctl.toString()) == 6
                || dbBrno == 3
                || dbBrno == 5
                || dbBrno == 7
                || dbBrno == 236) {
            wkBRNO = dbBrno;
        }
        // GET-BCTL-EXIT.
        //     EXIT.

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
        responseTextMap.put("RPTNAME", FILE_NAME);

        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", responseTextMap);
    }
}
