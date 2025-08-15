/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.LIST2;
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
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
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
@Component("LIST2Lsnr")
@Scope("prototype")
public class LIST2Lsnr extends BatchListenerCase<LIST2> {

    @Autowired private TextFileUtil textFile;
    @Autowired private ExternalSortUtil externalSortUtil;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private Parse parse;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> labelMap;
    private Map<String, String> textMap;
    private static final String CHARSET = "Big5"; // 檔案編碼
    private static final String FILE_INPUT_NAME = "PUTFCTL2"; // 讀檔檔名
    private static final String FILE_NAME = "CL-BH-007"; // 檔名
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;
    private String inputFilePath; // 讀檔路徑
    private String outputFilePath; // 產檔路徑
    private String outputTmpFilePath; // 產暫存檔路徑
    private StringBuilder sb = new StringBuilder();
    private List<String> fileLIST2Contents; // 檔案內容
    private LIST2 event;
    private String processDate = "";
    private String tbsdy;
    private String wkYYMMDD = "";
    private String wkPutfile = "";
    private String wkPutfileP = "";
    private String wkCode = "";
    private String wkBdate = "";
    private String wkEdate = "";
    private String sPutfile = "";
    private String sCode = "";
    private String sBdate = "";
    private String sEdate = "";

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(LIST2 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "LIST2Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(LIST2 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "LIST2Lsnr run()");
        init(event);
        main();

        // 刪除暫存檔
        textFile.deleteFile(outputTmpFilePath);
        batchResponse();
    }

    private void init(LIST2 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "LIST2Lsnr init");

        this.event = event;
        // 抓批次營業日
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();

        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        wkYYMMDD = processDate;
        wkPutfile = "";

        // 讀檔路徑     "DATA/CL/BH/PUTFCTL2"
        String inputFileDir = fileDir + CONVF_DATA + PATH_SEPARATOR + processDate;
        inputFilePath = inputFileDir + PATH_SEPARATOR + FILE_INPUT_NAME;

        textFile.deleteFile(inputFilePath);
        String sourceFtpPath =
                "NCL"
                        + File.separator
                        + tbsdy
                        + File.separator
                        + "2FSAP"
                        + File.separator
                        + "DATA"
                        + File.separator
                        + FILE_INPUT_NAME; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, inputFileDir);
        if (sourceFile != null) {
            inputFilePath = getLocalPath(sourceFile);
        }
        // 產檔路徑    "BD/CL/BH/007."
        outputFilePath =
                fileDir + CONVF_RPT + PATH_SEPARATOR + processDate + PATH_SEPARATOR + FILE_NAME;
        outputTmpFilePath =
                fileDir + CONVF_DATA + PATH_SEPARATOR + processDate + PATH_SEPARATOR + "Tmp007";
        // 刪除舊檔
        textFile.deleteFile(outputFilePath);

        fileLIST2Contents = new ArrayList<>();
    }

    private void main() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "LIST2Lsnr main");
        // 007500 0000-MAIN-RTN.
        // 執行1000-WTITLE-RTN，寫REPORTFL表頭
        // 007600     PERFORM 1000-WTITLE-RTN      THRU   1000-WTITLE-EXIT.
        wtitle();
        // SORT INPUT 段落：CS-SORTIN(讀FD-PUTFCTL2，寫至SORTFL)
        // 資料照 S-PUTTYPE2 S-PUTFILE S-CODE 由小到大排序
        // SORT OUTPUT 段落：CS-SORTOUT(將SORT後的記錄讀出，寫報表檔)
        // 03 PUTFCTL2-PUTFILE	GROUP
        // 07 PUTFCTL2-PUTTYPE	9(02)	媒體種類
        // 07 PUTFCTL2-PUTNAME	X(08)	媒體檔名
        // 03 PUTFCTL2-CODE	X(06)	代收類別
        // 03 PUTFCTL2-BDATE	9(08)	資料起日
        // 03 PUTFCTL2-EDATE	9(08)	資料迄日
        // 03 FILLER	X(08)
        List<KeyRange> keyRanges = new ArrayList<>();
        keyRanges.add(new KeyRange(1, 10, SortBy.ASC));
        keyRanges.add(new KeyRange(11, 6, SortBy.ASC));
        externalSortUtil.sortingFile(inputFilePath, outputTmpFilePath, keyRanges, CHARSET);
        // 007700     SORT    SORTFL
        // 007800             ASCENDING KEY        S-PUTFILE  S-CODE
        // 007900             INPUT  PROCEDURE     CS-SORTIN
        // 008000             OUTPUT PROCEDURE     CS-SORTOUT.
        sortOut();

        try {
            textFile.writeFileContent(outputFilePath, fileLIST2Contents, CHARSET);
            upload(outputFilePath, "RPT", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        // 008200 0000-MAIN-EXIT.
    }

    private void wtitle() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "LIST2Lsnr wtitle");
        // 011800 1000-WTITLE-RTN.
        //
        //// 寫REPORTFL表頭 (TITLE1-LINE~TITLE1-LINE4)
        //
        // 011900     MOVE        SPACES              TO     REPORT-LINE.
        // 012000     WRITE       REPORT-LINE         AFTER  3 LINE.
        fileLIST2Contents.add("");
        fileLIST2Contents.add("");
        fileLIST2Contents.add("");

        // 012100     WRITE       REPORT-LINE         FROM   TITLE1-LINE.
        // 005000 01  TITLE1-LINE.
        // 005020  02 FILLER                             PIC X(25) VALUE SPACE.
        // 005050  02 FILLER                             PIC X(56) VALUE
        // 005100       " 連　線　代　收　業　務　媒　體　起　迄　日　記　錄　單 ".
        // 005110  02 FILLER                             PIC X(18) VALUE SPACE.
        // 005120  02 FILLER                             PIC X(11) VALUE
        // 005140          "FORM : C007".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 25));
        sb.append(formatUtil.padX(" 連　線　代　收　業　務　媒　體　起　迄　日　記　錄　單 ", 56));
        sb.append(formatUtil.padX("", 18));
        sb.append(formatUtil.padX("FORM : C007", 11));
        fileLIST2Contents.add(sb.toString());

        // 012200     MOVE        SPACES              TO     REPORT-LINE.
        // 012300     WRITE       REPORT-LINE         AFTER  2 LINE.
        fileLIST2Contents.add("");
        fileLIST2Contents.add("");

        // 012400     WRITE       REPORT-LINE         FROM   TITLE2-LINE.
        // 005200 01  TITLE2-LINE.
        // 005205  02 FILLER                             PIC X(10) VALUE SPACE.
        // 005220  02 FILLER                             PIC X(12) VALUE
        // 005240          " 製作日期 : ".
        // 005300  02 WK-YYMMDD                          PIC 9(06).
        // 005310  02 FILLER                             PIC X(70) VALUE SPACE.
        // 005320  02 FILLER                             PIC X(08) VALUE
        // 005340          " 頁數 : ".
        // 005360  02 WK-PAGE                            PIC 9(02) VALUE 1.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 10));
        sb.append(formatUtil.padX(" 製作日期 : ", 12));
        sb.append(formatUtil.pad9(wkYYMMDD, 6));
        sb.append(formatUtil.padX("", 70));
        sb.append(formatUtil.padX(" 頁數 : ", 8));
        sb.append(formatUtil.pad9("1", 2));
        fileLIST2Contents.add(sb.toString());

        // 012420     MOVE        SPACES              TO     REPORT-LINE.
        // 012440     WRITE       REPORT-LINE         AFTER  2 LINE.
        fileLIST2Contents.add("");
        fileLIST2Contents.add("");

        // 012460     WRITE       REPORT-LINE         FROM   TITLE3-LINE.
        // 005370 01  TITLE3-LINE.
        // 005380  02 FILLER                             PIC X(10) VALUE SPACE.
        // 005385  02 FILLER                             PIC X(49) VALUE
        // 005390          " 媒體檔名     代收類別     資料起日     資料迄日 ".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 10));
        sb.append(formatUtil.padX(" 媒體檔名     代收類別     資料起日     資料迄日 ", 49));
        fileLIST2Contents.add(sb.toString());

        // 012470     MOVE        SPACES              TO     REPORT-LINE.
        // 012480     WRITE       REPORT-LINE         FROM   TITLE4-LINE.
        // 005410 01  TITLE4-LINE.
        // 005430  02 FILLER                             PIC X(70) VALUE ALL "-".
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 70));
        fileLIST2Contents.add(sb.toString());

        // 012500 1000-WTITLE-EXIT.
    }

    private void sortOut() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "LIST2Lsnr sortOut");
        // 009600 CS-SORTOUT-RTN.
        // 將SORT後的記錄讀出，直到檔尾，結束本節
        // 009700     RETURN      SORTFL      AT END  GO TO  CS-SORTOUT-EXIT.
        // 搬資料給DETAIL-LINE
        // PUTFILE相同時，搬空白給WK-PUTFILE-P(DETAIL-LINE'S變數)
        // PUTFILE不同時，搬S-PUTFILE給WK-PUTFILE-P(DETAIL-LINE'S變數)
        List<String> lines = textFile.readFileContent(outputTmpFilePath, CHARSET);
        for (String detail : lines) {
            sPutfile = detail.substring(0, 10);
            sCode = detail.substring(10, 16);
            sBdate = detail.substring(16, 24);
            sEdate = detail.substring(24, 32);
            // 010100     IF          S-PUTFILE           =      WK-PUTFILE
            if (sPutfile.equals(wkPutfile)) {
                // 010120       MOVE      SPACES              TO     WK-PUTFILE-P
                wkPutfileP = "";
                // 010140     ELSE
            } else {
                // 010145       MOVE      SPACES              TO     REPORT-LINE
                // 010150       WRITE     REPORT-LINE         AFTER  1 LINE
                fileLIST2Contents.add("");

                // 010160       MOVE      S-PUTFILE           TO     WK-PUTFILE-P.
                wkPutfileP = sPutfile;
            }
            // 010170     MOVE        S-CODE              TO     WK-CODE.
            // 010180     MOVE        S-BDATE             TO     WK-BDATE.
            // 010190     MOVE        S-EDATE             TO     WK-EDATE.
            // 010500     MOVE        SPACES              TO     REPORT-LINE.
            wkCode = sCode;
            wkBdate = sBdate;
            wkEdate = sEdate;
            // 寫REPORTFL報表明細(DETAIL-LINE)
            // 010600     WRITE       REPORT-LINE         FROM   DETAIL-LINE.
            // 005490 01  DETAIL-LINE.
            // 005495  02 FILLER                             PIC X(10) VALUE SPACE.
            // 005500  02 WK-PUTFILE-P                       PIC X(10).
            // 005650  02 FILLER                             PIC X(05) VALUE SPACE.
            // 005655  02 WK-CODE                            PIC X(06).
            // 005657  02 FILLER                             PIC X(07) VALUE SPACE.
            // 005660  02 WK-BDATE                           PIC 9(06).
            // 005662  02 FILLER                             PIC X(08) VALUE SPACE.
            // 005665  02 WK-EDATE                           PIC 9(06).
            sb = new StringBuilder();
            sb.append(formatUtil.padX("", 10));
            sb.append(formatUtil.padX(wkPutfileP, 10));
            sb.append(formatUtil.padX("", 5));
            sb.append(formatUtil.padX(wkCode, 6));
            sb.append(formatUtil.padX("", 7));
            sb.append(formatUtil.pad9(wkBdate, 6));
            sb.append(formatUtil.padX("", 8));
            sb.append(formatUtil.pad9(wkEdate, 6));
            fileLIST2Contents.add(sb.toString());

            // 搬S-PUTFILE給WK-PUTFILE(保留PUTFILE變數)
            // 010900     MOVE        S-PUTFILE           TO     WK-PUTFILE.
            wkPutfile = sPutfile;

            // LOOP讀下一筆SORTFL
            // 011300     GO TO       CS-SORTOUT-RTN.
        }
        // 011400 CS-SORTOUT-EXIT.
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
