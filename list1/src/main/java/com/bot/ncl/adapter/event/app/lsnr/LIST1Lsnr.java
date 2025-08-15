/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.LIST1;
import com.bot.ncl.util.FsapBatchUtil;
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
@Component("LIST1Lsnr")
@Scope("prototype")
public class LIST1Lsnr extends BatchListenerCase<LIST1> {

    @Autowired private TextFileUtil textFile;
    @Autowired private ExternalSortUtil externalSortUtil;
    @Autowired private FormatUtil formatUtil;
    @Autowired private Parse parse;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> labelMap;
    private Map<String, String> textMap;
    private static final String CHARSET = "Big5"; // 檔案編碼
    private static final String FILE_INPUT_NAME = "PUTFCTL"; // 讀檔檔名
    private static final String FILE_NAME = "CL-BH-005"; // 檔名
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;
    private String inputFilePath; // 讀檔路徑
    private String outputFilePath; // 產檔路徑
    private String outputTmpFilePath; // 產檔暫存路徑

    private StringBuilder sb = new StringBuilder();
    private List<String> fileLIST1Contents; // 檔案內容

    private LIST1 event;
    private String processDate = "";
    private String tbsdy;
    private String wkYYMMDD = "";
    private String wkYYMMDDN = "";
    private String wkYYMMDDN1 = "";
    private String wkPuttype2 = "";
    private String wkPutname1 = "";
    private String wkText1 = "";
    private String wkText2;
    private String wkText;
    private String wkPutname = "";
    private String wkPutaddr = "";
    private String wkPuttype = "";
    private String sPuttype1 = "";
    private String sPuttype2 = "";
    private String sPutname1 = "";
    private String sPutname = "";
    private String sPuttype = "";
    private String sPutaddr = "";
    private String putfctlPuttype1 = "";
    private String putfctlPutname = "";
    private String putfctlPuttype = "";

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(LIST1 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "LIST1Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(LIST1 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "LIST1Lsnr run()");
        init(event);
        main();
        // 刪除暫存檔
        textFile.deleteFile(outputTmpFilePath);
        batchResponse();
    }

    public void init(LIST1 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "LIST1Lsnr init");

        this.event = event;
        // 抓批次營業日

        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();

        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        // 設定作業日、檔名日期變數值、代收類別變數
        // WK-YYMMDD-N <-TITLE3-LINE'S變數
        // WK-YYMMDD-N1 <-TITLE4-LINE'S變數
        // 009400     MOVE    FD-BHDATE-TBSDY TO     WK-YYMMDD.
        // 009500     MOVE    FD-BHDATE-TBSDY TO     WK-YYMMDD-N,WK-YYMMDD-N1.
        wkYYMMDD = processDate;
        wkYYMMDDN = processDate;
        wkYYMMDDN1 = processDate;
        // 清變數
        // 009600     MOVE    0              TO      WK-PUTTYPE2.
        // 009650     MOVE    0              TO      WK-PUTNAME1.
        wkPuttype2 = "0";
        wkPutname1 = "0";

        // 讀檔路徑     "DATA/CL/BH/PUTFCTL".
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

        // 產檔路徑    "BD/CL/BH/005."
        outputFilePath =
                fileDir + CONVF_RPT + PATH_SEPARATOR + processDate + PATH_SEPARATOR + FILE_NAME;
        // Sort暫存路徑    "BD/CL/BH/tmp005."
        outputTmpFilePath =
                fileDir + CONVF_DATA + PATH_SEPARATOR + processDate + PATH_SEPARATOR + "tmp005";
        // 刪除舊檔
        textFile.deleteFile(outputFilePath);

        fileLIST1Contents = new ArrayList<>();
    }

    private void main() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "LIST1Lsnr main");
        // 010500 0000-MAIN-RTN.

        // 執行1000-WTITLE-RTN，寫REPORTFL表頭
        // 010600     PERFORM 1000-WTITLE-RTN      THRU   1000-WTITLE-EXIT.
        wtitle();
        // SORT INPUT 段落：CS-SORTIN(讀FD-PUTFCTL，寫至SORTFL)
        // 資料照 S-PUTTYPE2 S-PUTTYPE1 S-PUTNAME 由小到大排序
        // SORT OUTPUT 段落：CS-SORTOUT(將SORT後的記錄讀出，寫報表檔)
        // 010700     SORT    SORTFL
        // 010800             ASCENDING KEY        S-PUTTYPE2 S-PUTTYPE1 S-PUTNAME
        // 010900             INPUT  PROCEDURE     CS-SORTIN
        List<KeyRange> keyRanges = new ArrayList<>();
        keyRanges.add(new KeyRange(2, 1, SortBy.ASC));
        keyRanges.add(new KeyRange(1, 1, SortBy.ASC));
        keyRanges.add(new KeyRange(3, 8, SortBy.ASC));
        externalSortUtil.sortingFile(inputFilePath, outputTmpFilePath, keyRanges, CHARSET);
        // 011000             OUTPUT PROCEDURE     CS-SORTOUT.
        sortOut();
        // 執行2000-WPAPER-RTN，讀FD-PUTFCTL，挑PUTFCTL-PUTTYPE1=1資料，寫報表檔

        // 011100     PERFORM 2000-WPAPER-RTN      THRU   2000-WPAPER-EXIT.
        wpaper();
        // 011200 0000-MAIN-EXIT.

        try {
            textFile.writeFileContent(outputFilePath, fileLIST1Contents, CHARSET);
            upload(outputFilePath, "RPT", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void wtitle() {
        // 015100 1000-WTITLE-RTN.
        //// 寫REPORTFL表頭 (TITLE1-LINE~TITLE1-LINE4)

        // 015200     MOVE        SPACES              TO     REPORT-LINE.
        // 015300     WRITE       REPORT-LINE         AFTER  3 LINE.
        fileLIST1Contents.add("");
        fileLIST1Contents.add("");
        fileLIST1Contents.add("");

        // 015400     WRITE       REPORT-LINE         FROM   TITLE1-LINE.
        // 005100 01  TITLE1-LINE.
        // 005200  02 FILLER                             PIC X(30) VALUE SPACE.
        // 005300  02 FILLER                             PIC X(44) VALUE
        // 005400          " 連  線  代  收  業  務  媒  體  製  作  單 ".
        // 005500  02 FILLER                             PIC X(25) VALUE SPACE.
        // 005600  02 FILLER                             PIC X(11) VALUE
        // 005700          "FORM : C005".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 30));
        sb.append(formatUtil.padX(" 連  線  代  收  業  務  媒  體  製  作  單 ", 44));
        sb.append(formatUtil.padX("", 25));
        sb.append(formatUtil.padX("FORM : C005", 11));
        fileLIST1Contents.add(sb.toString());

        // 015500     MOVE        SPACES              TO     REPORT-LINE.
        // 015600     WRITE       REPORT-LINE         AFTER  2 LINE.
        fileLIST1Contents.add("");
        fileLIST1Contents.add("");

        // 015700     WRITE       REPORT-LINE         FROM   TITLE2-LINE.
        // 005800 01  TITLE2-LINE.
        // 005900  02 FILLER                             PIC X(10) VALUE SPACE.
        // 006000  02 FILLER                             PIC X(12) VALUE
        // 006100          " 製作日期 : ".
        // 006200  02 WK-YYMMDD                          PIC 9(06).
        // 006300  02 FILLER                             PIC X(70) VALUE SPACE.
        // 006400  02 FILLER                             PIC X(08) VALUE
        // 006500          " 頁數 : ".
        // 006600  02 WK-PAGE                            PIC 9(02) VALUE 1.
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 10));
        sb.append(formatUtil.padX(" 製作日期 : ", 12));
        sb.append(formatUtil.pad9(wkYYMMDD, 6));
        sb.append(formatUtil.padX("", 70));
        sb.append(formatUtil.padX(" 頁數 : ", 8));
        sb.append(formatUtil.pad9("1", 2));
        fileLIST1Contents.add(sb.toString());

        // 015800     MOVE        SPACES              TO     REPORT-LINE.
        // 015900     WRITE       REPORT-LINE         AFTER  1 LINE.
        fileLIST1Contents.add("");

        // 016000     WRITE       REPORT-LINE         FROM   TITLE3-LINE.
        // 006700 01  TITLE3-LINE.
        // 006800  02 FILLER                             PIC X(10) VALUE SPACE.
        // 006900  02 FILLER                             PIC X(36) VALUE
        // 007000          " 檔案名稱 : (ONLINE)DATA/CL/BH/PUTF/".
        // 007100  02 WK-YYMMDD-N                        PIC 9(06).
        // 007200  02 FILLER                             PIC X(12) VALUE
        // 007300          "/XXXXXXXXXX.".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 10));
        sb.append(formatUtil.padX(" 檔案名稱 : (ONLINE)DATA/CL/BH/PUTF/", 36));
        sb.append(formatUtil.pad9(wkYYMMDDN, 6));
        sb.append(formatUtil.padX("/XXXXXXXXXX.", 12));
        fileLIST1Contents.add(sb.toString());

        // 016020     MOVE        SPACES              TO     REPORT-LINE.
        // 016040     WRITE       REPORT-LINE         FROM   TITLE4-LINE.
        // 007310 01  TITLE4-LINE.
        // 007320  02 FILLER                             PIC X(10) VALUE SPACE.
        // 007330  02 FILLER                             PIC X(38) VALUE
        // 007340     " 利用ＥＭＡＩＬ傳送之檔案名稱： ".
        // 007350  02 FILLER                             PIC X(24) VALUE
        // 007360     "(ONLINE)DATA/CL/BH/PUTF/".
        // 007370  02 WK-YYMMDD-N1                       PIC 9(06).
        // 007380  02 FILLER                             PIC X(16) VALUE
        // 007390          "/XXXXXXXXXX/OUT.".
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 10));
        sb.append(formatUtil.padX(" 利用ＥＭＡＩＬ傳送之檔案名稱： ", 38));
        sb.append(formatUtil.padX("(ONLINE)DATA/CL/BH/PUTF/", 24));
        sb.append(formatUtil.pad9(wkYYMMDDN1, 6));
        sb.append(formatUtil.padX("/XXXXXXXXXX/OUT.", 16));
        fileLIST1Contents.add(sb.toString());

        // 016100 1000-WTITLE-EXIT.

    }

    private void wpaper() {
        // 016300 2000-WPAPER-RTN.
        // 016350     MOVE        SPACES       TO    WK-TEXT2.
        wkText2 = "";
        // 關閉檔案FD-PUTFCTL
        // 016400     CLOSE       FD-PUTFCTL   WITH  SAVE.

        // 開啟檔案FD-PUTFCTL=
        // 016500     OPEN  INPUT FD-PUTFCTL.
        List<String> lines = textFile.readFileContent(inputFilePath, CHARSET);
        Boolean firstHeader = true;
        for (String detail : lines) {
            // 016600 2000-LOOP1.

            // 循序讀取FD-PUTFCTL，直到檔尾，結束本段落=
            // 016700     READ        FD-PUTFCTL   AT END GO TO    2000-WPAPER-EXIT.

            // PUTFCTL-PUTTYPE1=1
            //  A.搬說明
            //  B.寫REPORTFL(DSCPT-LINE)
            //  C.搬資料給DETAIL-LINE
            //  D.寫REPORTFL報表明細(DETAIL-LINE)
            //  E.GO TO 2000-LOOP2，往下執行
            // PUTFCTL-PUTTYPE1<>1
            //  A.GO TO 2000-LOOP1，LOOP讀下一筆PUTFCTL

            // 016800     IF          PUTFCTL-PUTTYPE1       =     1
            putfctlPuttype1 = detail.substring(0, 1);
            putfctlPuttype = detail.substring(0, 2);
            putfctlPutname = detail.substring(2, 10);
            if ("1".equals(putfctlPuttype1)) {
                if (firstHeader) {
                    // 016900       MOVE      " 報            表  :" TO    WK-TEXT
                    wkText = " 報            表  :";
                    // 017000       MOVE      SPACES                 TO    REPORT-LINE
                    // 017100       WRITE     REPORT-LINE            AFTER 2 LINE
                    fileLIST1Contents.add("");
                    fileLIST1Contents.add("");

                    // 017200       WRITE     REPORT-LINE            FROM  DSCPT-LINE
                    // 007400 01  DSCPT-LINE.
                    // 007500  02 FILLER                             PIC X(10) VALUE SPACE.
                    // 007600  02 WK-TEXT                            PIC X(25).
                    sb = new StringBuilder();
                    sb.append(formatUtil.padX(" ", 10));
                    sb.append(formatUtil.padX(wkText, 25));
                    fileLIST1Contents.add(sb.toString());

                    // 017300       MOVE      PUTFCTL-PUTNAME        TO    WK-PUTNAME
                    // 017400       MOVE      PUTFCTL-PUTTYPE        TO    WK-PUTTYPE
                    // 017450       MOVE      SPACES                 TO    WK-PUTADDR
                    // 017500       MOVE      SPACES                 TO    REPORT-LINE
                    wkPutname = putfctlPutname;
                    wkPuttype = putfctlPuttype;
                    wkPutaddr = "";

                    // 017600       WRITE     REPORT-LINE            FROM  DETAIL-LINE
                    // 007700 01  DETAIL-LINE.
                    // 007800  02 FILLER                             PIC X(31) VALUE SPACE.
                    // 007900  02 WK-PUTTYPE.
                    // 008000    04 WK-PUTTYPE1                      PIC 9(01).
                    // 008100    04 WK-PUTTYPE2                      PIC 9(01).
                    // 008200  02 FILLER                             PIC X(01) VALUE SPACE.
                    // 008300  02 WK-PUTNAME.
                    // 008320    04 WK-PUTNAME1                      PIC X(01).
                    // 008340    04 WK-PUTNAME2                      PIC X(07).
                    // 008360  02 WK-TEXT2                           PIC X(05).
                    // 008380  02 WK-PUTADDR                         PIC X(40).
                    sb = new StringBuilder();
                    sb.append(formatUtil.padX(" ", 31));
                    sb.append(formatUtil.pad9(wkPuttype, 2));
                    sb.append(formatUtil.padX(" ", 1));
                    sb.append(formatUtil.padX(wkPutname, 8));
                    sb.append(formatUtil.padX(wkText2, 5));
                    sb.append(formatUtil.padX(wkPutaddr, 40));
                    fileLIST1Contents.add(sb.toString());
                    firstHeader = false;
                    continue;
                }
                // 017700       GO TO     2000-LOOP2
                // 018000 2000-LOOP2.

                // 循序讀取FD-PUTFCTL，直到檔尾，結束本段落
                // 018100     READ        FD-PUTFCTL   AT END GO TO  2000-WPAPER-EXIT.

                // PUTFCTL-PUTTYPE1=1
                //  A.搬資料給DETAIL-LINE
                //  B.寫REPORTFL報表明細(DETAIL-LINE)
                // 018200     IF          PUTFCTL-PUTTYPE1       =   1
                // 018300      MOVE       PUTFCTL-PUTNAME        TO  WK-PUTNAME
                // 018400      MOVE       PUTFCTL-PUTTYPE        TO  WK-PUTTYPE
                // 018450      MOVE       SPACES                 TO  WK-PUTADDR
                wkPutname = putfctlPutname;
                wkPuttype = putfctlPuttype;
                wkPutaddr = "";
                // 018500      MOVE       SPACES                 TO  REPORT-LINE
                // 018600      WRITE      REPORT-LINE            FROM DETAIL-LINE.
                sb = new StringBuilder();
                sb.append(formatUtil.padX(" ", 31));
                sb.append(formatUtil.pad9(wkPuttype, 2));
                sb.append(formatUtil.padX(" ", 1));
                sb.append(formatUtil.padX(wkPutname, 8));
                sb.append(formatUtil.padX(wkText2, 5));
                sb.append(formatUtil.padX(wkPutaddr, 40));
                fileLIST1Contents.add(sb.toString());

                //  GO TO 2000-LOOP2，LOOP讀下一筆PUTFCTL

                // 018700     GO TO       2000-LOOP2.
            }
        }
    }

    private void sortOut() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "LIST1Lsnr sortOut ....");
        // 012800 CS-SORTOUT-RTN.

        //// 將SORT後的記錄讀出，直到檔尾，結束本節
        // 012900     RETURN      SORTFL      AT END  GO TO  CS-SORTOUT-EXIT.

        List<String> lines = textFile.readFileContent(outputTmpFilePath, CHARSET);
        for (String detail : lines) {
            // PUTTYPE 媒體種類
            // 1x:報表
            // x2:磁片
            // x3:盤式磁帶
            // 07:網路傳輸
            // 1X:除磁片磁帶網路外另加報表
            // 2X:特殊報表
            //
            // PUTTYPE(1:1)=1 & PUTTYPE(2:1)=0，不處理，LOOP讀下一筆SORTFL
            detail = formatUtil.padX(detail, 56);
            sPuttype1 = detail.substring(0, 1);
            sPuttype2 = detail.substring(1, 2);
            sPutname1 = detail.substring(2, 3);
            sPuttype = detail.substring(0, 2);
            sPutname = detail.substring(2, 10);
            sPutaddr = detail.substring(16, 56);

            // 013000     IF          S-PUTTYPE1          =      1
            // 013100             AND S-PUTTYPE2          =      0
            if ("1".equals(sPuttype1) && "0".equals(sPuttype2)) {
                // 013200       GO TO     CS-SORTOUT-RTN
                continue;
            }
            // 013300     ELSE

            // PUTTYPE2不同時
            //  A.執行4000-FINDTEXT-RTN，依據S-PUTTYPE2搬說明
            //  B.寫REPORTFL(DSCPT-LINE)
            //  C.執行5000-FINDTEXT1-RTN，依據S-PUTNAME1搬說明
            //  D.寫REPORTFL(DSCPT1-LINE)
            //
            // 013400      IF         S-PUTTYPE2          NOT =  WK-PUTTYPE2
            if (!sPuttype2.equals(wkPuttype2)) {
                // 013500       PERFORM   4000-FINDTEXT-RTN   THRU   4000-FINDTEXT-EXIT
                findtext();
                // 013600       MOVE      SPACES              TO     REPORT-LINE
                // 013700       WRITE     REPORT-LINE         AFTER  2 LINE
                fileLIST1Contents.add("");
                fileLIST1Contents.add("");

                // 013800       MOVE      SPACES              TO     REPORT-LINE
                // 013900       WRITE     REPORT-LINE         FROM   DSCPT-LINE
                sb = new StringBuilder();
                sb.append(formatUtil.padX(" ", 10));
                sb.append(formatUtil.padX(wkText, 25));
                fileLIST1Contents.add(sb.toString());

                // 013920       PERFORM   5000-FINDTEXT1-RTN  THRU   5000-FINDTEXT1-EXIT
                findtext1();
                // 013940       MOVE      SPACES              TO     REPORT-LINE
                // 013960       WRITE     REPORT-LINE         FROM   DSCPT1-LINE
                sb = new StringBuilder();
                sb.append(formatUtil.padX(" ", 10));
                sb.append(formatUtil.padX(wkText1, 25));
                fileLIST1Contents.add(sb.toString());
            }
            // 014000      ELSE
            //
            // PUTTYPE2相同時
            //  PUTNAME1不同時
            //  A.執行5000-FINDTEXT1-RTN，依據S-PUTNAME1搬說明
            //  B.寫REPORTFL(DSCPT1-LINE)
            //
            // 014100      IF         S-PUTNAME1          NOT =  WK-PUTNAME1
            if (!sPutname1.equals(wkPutname1)) {
                // 014120       PERFORM   5000-FINDTEXT1-RTN  THRU   5000-FINDTEXT1-EXIT
                findtext1();
                // 014140       MOVE      SPACES              TO     REPORT-LINE
                // 014160       WRITE     REPORT-LINE         FROM   DSCPT1-LINE.
                // 007620 01  DSCPT1-LINE.
                // 007640  02 FILLER                             PIC X(10) VALUE SPACE.
                // 007660  02 WK-TEXT1                           PIC X(25).
                sb = new StringBuilder();
                sb.append(formatUtil.padX(" ", 10));
                sb.append(formatUtil.padX(wkText1, 25));
                fileLIST1Contents.add(sb.toString());
            }

            // 搬資料給DETAIL-LINE
            // 014200     MOVE        S-PUTNAME           TO     WK-PUTNAME.
            wkPutname = sPutname;
            wkPutname1 = sPutname1;
            // 014220     IF          WK-PUTNAME1         =      "Z"
            if ("Z".equals(wkPutname1)) {
                // 014240       MOVE      "/OUT "             TO     WK-TEXT2
                wkText2 = "/OUT ";
            } else {
                // 014260     ELSE
                // 014280       MOVE      SPACES              TO     WK-TEXT2.
                wkText2 = "";
            }
            // 014300     MOVE        S-PUTTYPE           TO     WK-PUTTYPE.
            // 014350     MOVE        S-PUTADDR           TO     WK-PUTADDR.
            wkPuttype = sPuttype;
            wkPutaddr = sPutaddr;
            // 014400     MOVE        SPACES              TO     REPORT-LINE.
            //
            // 寫REPORTFL報表明細(DETAIL-LINE)
            //
            // 014500     WRITE       REPORT-LINE         FROM   DETAIL-LINE.
            // 007800  02 FILLER                             PIC X(31) VALUE SPACE.
            // 007900  02 WK-PUTTYPE.
            // 008000    04 WK-PUTTYPE1                      PIC 9(01).
            // 008100    04 WK-PUTTYPE2                      PIC 9(01).
            // 008200  02 FILLER                             PIC X(01) VALUE SPACE.
            // 008300  02 WK-PUTNAME.
            // 008320    04 WK-PUTNAME1                      PIC X(01).
            // 008340    04 WK-PUTNAME2                      PIC X(07).
            // 008360  02 WK-TEXT2                           PIC X(05).
            // 008380  02 WK-PUTADDR                         PIC X(40).
            sb = new StringBuilder();
            sb.append(formatUtil.padX(" ", 31));
            sb.append(formatUtil.pad9(wkPuttype, 2));
            sb.append(formatUtil.padX(" ", 1));
            sb.append(formatUtil.padX(wkPutname, 8));
            sb.append(formatUtil.padX(wkText2, 5));
            sb.append(formatUtil.padX(wkPutaddr, 40));
            fileLIST1Contents.add(sb.toString());

            // LOOP讀下一筆SORTFL
            //
            // 014600     GO TO       CS-SORTOUT-RTN.
        }
        // 014700 CS-SORTOUT-EXIT.
    }

    private void findtext() {
        // 019000 4000-FINDTEXT-RTN.
        //
        //// 依據S-PUTTYPE2搬說明(DSCPT-LINE'S變數)
        //
        // 019100     IF          S-PUTTYPE2       =   2
        if ("2".equals(sPuttype2)) {
            // 019200       MOVE      " 磁            片  :" TO  WK-TEXT
            wkText = " 磁            片  :";
        } else if ("3".equals(sPuttype2)) {
            // 019300     ELSE
            // 019400     IF          S-PUTTYPE2       =   3
            // 019500       MOVE      " 盤   式   磁   帶 :" TO  WK-TEXT
            wkText = " 盤   式   磁   帶 :";
        } else if ("4".equals(sPuttype2)) {
            // 019600*    ELSE
            // 019700*    IF          S-PUTTYPE2             =   4
            // 019800*      MOVE      " C A R T R I G E   :" TO  WK-TEXT
            wkText = " C A R T R I G E   :";
        } else if ("5".equals(sPuttype2)) {
            // 019900     ELSE
            // 020000     IF          S-PUTTYPE2             =   5
            // 020100       MOVE      " 匣   式   磁   帶 :" TO  WK-TEXT
            wkText = " 匣   式   磁   帶 :";
        } else if ("6".equals(sPuttype2)) {
            // 020200*    ELSE
            // 020300*    IF          S-PUTTYPE2          =      6
            // 020400*      MOVE      " T D K             :" TO  WK-TEXT
            wkText = " T D K             :";
        } else if ("7".equals(sPuttype2)) {
            // 020500     ELSE
            // 020600     IF          S-PUTTYPE2          =      7
            // 020700       MOVE      " 網   路   傳   輸 :" TO  WK-TEXT.
            wkText = " 網   路   傳   輸 :";
        }
        // 020800 4000-FINDTEXT-EXIT.
    }

    private void findtext1() {
        // 022000 5000-FINDTEXT1-RTN.

        //// 依據S-PUTNAME1搬說明(DSCPT1-LINE'S變數)

        // 024000     IF          S-PUTNAME1          =      "A"
        if ("A".equals(sPutname1)) {
            // 026000       MOVE      "  ( 中心製作送客戶 )" TO  WK-TEXT1
            wkText1 = "  ( 中心製作送客戶 )";
        } else if ("B".equals(sPutname1)) {
            // 028000     ELSE
            // 030000     IF          S-PUTNAME1          =      "B"
            // 032000       MOVE      "  ( 中心製作送分行 )" TO  WK-TEXT1
            wkText1 = "  ( 中心製作送分行 )";

        } else if ("C".equals(sPutname1)) {
            // 034000     ELSE
            // 036000     IF          S-PUTNAME1          =      "C"
            // 038000       MOVE      "  ( 分行自行製作   )" TO  WK-TEXT1
            wkText1 = "  ( 分行自行製作   )";
        } else if ("W".equals(sPutname1)) {
            // 038500     ELSE
            // 039000     IF          S-PUTNAME1          =      "W"
            // 039500       MOVE      " (BIZTALK - F T P  )" TO  WK-TEXT1
            wkText1 = " (BIZTALK - F T P  )";
        } else if ("X".equals(sPutname1)) {
            // 040000     ELSE
            // 042000     IF          S-PUTNAME1          =      "X"
            // 044000       MOVE      "         ( C V S   )" TO  WK-TEXT1
            wkText1 = "         ( C V S   )";
        } else if ("Y".equals(sPutname1)) {
            // 046000     ELSE
            // 048000     IF          S-PUTNAME1          =      "Y"
            // 050000       MOVE      "         ( P E D I )" TO  WK-TEXT1
            wkText1 = "         ( P E D I )";
        } else if ("Z".equals(sPutname1)) {
            // 050500     ELSE
            // 051000     IF          S-PUTNAME1          =      "Z"
            // 051500       MOVE      "  (   E M A I L    )" TO  WK-TEXT1.
            wkText1 = "  (   E M A I L    )";
        }
        // 052000 5000-FINDTEXT1-EXIT.
    }

    private String getrocdate(int dateI) {
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "LIST1Lsnr getrocdate inputdate = {}", dateI);

        String date = "" + dateI;
        if (dateI > 19110101) {
            dateI = dateI - 19110000;
        }
        if (String.valueOf(dateI).length() < 7) {
            date = String.format("%07d", dateI);
        }
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "LIST1Lsnr getrocdate outputdate = {}", date);
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
