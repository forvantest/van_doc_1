/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONV302007_PBRNO;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FilePUTF;
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
@Component("CONV302007_PBRNOLsnr")
@Scope("prototype")
public class CONV302007_PBRNOLsnr extends BatchListenerCase<CONV302007_PBRNO> {

    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private Parse parse;
    @Autowired private ClmrService clmrService;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FilePUTF filePutf;
    @Autowired private ExternalSortUtil externalSortUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    private CONV302007_PBRNO event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> labelMap;
    private static final String CHARSET = "UTF-8";
    private static final String CHARSET_BIG5 = "BIG5";
    private static final String PATH_SEPARATOR = File.separator;
    private static final String PAGE_SEPARATOR = "\u000C";
    private static final String FILE_SORT_NAME = "SPUTF";
    private static final String CONVF_DATA = "DATA";
    private String wkPutdir;
    private List<String> file27X1350003Contents;
    private StringBuilder sb = new StringBuilder();
    private String processDate;
    private String tbsdy;
    private String wkFdate;
    private String wkPutfile;
    private String wkCode = "000000";
    private int wkPbrno = 0;
    // -----PUTF----
    private String putfCtl2;
    private String putfCode;
    private String putfFiller;
    private String sortPath;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(CONV302007_PBRNO event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV302007_PBRNOLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONV302007_PBRNO event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV302007_PBRNOLsnr run ...");
        init(event);
        //// FD-PUTF檔案存在，開啟FD-PUTF、執行1000-PBRNO-RTN、關閉FD-PUTF
        // 006200   IF  ATTRIBUTE RESIDENT OF FD-PUTF IS = VALUE(TRUE)
        if (textFile.exists(wkPutdir)) {
            // 006300       OPEN     I-O    FD-PUTF
            // 006400       PERFORM  1000-PBRNO-RTN THRU 1000-PBRNO-EXIT
            _1000_pbrno();
            // 006500       CLOSE    FD-PUTF WITH SAVE.
            textFile.deleteDir(wkPutdir);
            try {
                textFile.writeFileContent(wkPutdir, file27X1350003Contents, CHARSET);
                upload(wkPutdir, "DATA", "PUTF");
            } catch (LogicException e) {
                moveErrorResponse(e);
            }
        }
        //        batchResponse();
        checkSortPath();
    }

    private void init(CONV302007_PBRNO event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV302007_PBRNOLsnr init");
        this.event = event;
        //// DISPLAY訊息，包含在系統訊息中
        // 005500   CHANGE ATTRIBUTE DISPLAYONLYTOMCS OF MYSELF TO TRUE.
        //// 讀作業日期檔，設定本營業日變數值；若讀不到，顯示訊息，結束程式
        // 005600   READ    FD-BHDATE AT END DISPLAY "READ FD-BHDATE ERROR"
        // 005700        STOP RUN.

        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();

        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        //// 設定檔名、日期變數值
        //// WK-FDATE PIC 9(06) 	<-WK-PUTDIR'S變數
        //// WK-PUTFILE PIC X(10) <--WK-PUTDIR'S變數
        //// WK-PUTDIR <-"DATA/CL/BH/PUTF/"+WK-FDATE+"/27X1350003."
        // 005800* 來源資料設定
        // 005900   MOVE    "27X1350003"        TO     WK-PUTFILE.
        // 006000   MOVE    FD-BHDATE-TBSDY     TO     WK-FDATE.
        // 006100   CHANGE  ATTRIBUTE FILENAME  OF  FD-PUTF TO WK-PUTDIR.
        // 003100 01 WK-PUTDIR.
        // 003200    03 FILLER                PIC X(16)
        // 003300                           VALUE "DATA/CL/BH/PUTF/".
        // 003400    03 WK-FDATE              PIC 9(06).
        // 003500    03 FILLER                PIC X(01)
        // 003600                           VALUE "/".
        // 003700    03 WK-PUTFILE            PIC X(10).
        // 003800    03 FILLER                PIC X(01)
        // 003900                           VALUE ".".
        wkPutfile = "27X1350003";
        wkFdate = formatUtil.pad9(processDate, 8).substring(2, 8);
        String putfDir =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
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

        file27X1350003Contents = new ArrayList<>();
        //// FD-PUTF檔案存在，開啟FD-PUTF、執行1000-PBRNO-RTN、關閉FD-PUTF

        sortPath =
                fileDir + "DATA" + File.separator + processDate + File.separator + FILE_SORT_NAME;
    }

    private void _1000_pbrno() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV302007_PBRNOLsnr _1000_pbrno");
        // 007400 1000-PBRNO-RTN.
        // 007600 1000-PBRNO-LOOP.
        //// 循序讀取FD-PUTF，異動：增加主辦行欄位，直到檔尾，跳到1000-PBRNO-LAST，結束本段落
        // 007800      READ   FD-PUTF    AT  END GO TO   1000-PBRNO-LAST.
        List<String> lines = textFile.readFileContent(wkPutdir, CHARSET);
        //// 挑明細資料，代收類別不同時，依代收類別FIND DB-CLMR-DDS取得主辦行資料
        for (String detail : lines) {
            text2VoFormatter.format(detail, filePutf);
            putfCtl2 = filePutf.getCtl().substring(1, 2);
            putfCode = filePutf.getCode();
            putfFiller = filePutf.getFiller();
            // 007900      IF  PUTF-CTL2      =   1
            if ("1".equals(putfCtl2)) {
                // 008000          IF  PUTF-CODE  NOT = WK-CODE
                if (!putfCode.equals(wkCode)) {
                    // 008100              MOVE  PUTF-CODE     TO WK-CODE
                    // 008200              PERFORM   3000-SCCLMR-RTN THRU 3000-SCCLMR-EXIT
                    wkCode = putfCode;
                    _3000_scclmr();
                    // 008300          END-IF
                }
                //// 搬主辦行給PUTF-FILLER(1:3)
                // 008400          MOVE  WK-PBRNO     TO  PUTF-FILLER(1:3)
                putfFiller = wkPbrno + putfFiller.substring(3, 10);
                //// 回寫PUTF-REC
                // 008500          REWRITE PUTF-REC
                sb = new StringBuilder(detail);
                sb.replace(110, 120, putfFiller);
                file27X1350003Contents.add(sb.toString());
                // 008600      END-IF.
                continue;
            }

            file27X1350003Contents.add(detail);
            //// LOOP讀下一筆FD-PUTF
            // 008700      GO TO   1000-PBRNO-LOOP.
        }
        // 008900 1000-PBRNO-LAST.
        // 009100 1000-PBRNO-EXIT.
    }

    private void _3000_scclmr() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV302007_PBRNOLsnr _3000_scclmr");
        // 009500 3000-SCCLMR-RTN.
        // 009600

        //// 依代收類別FIND DB-CLMR-DDS事業單位基本資料檔，若有誤
        ////  若NOTFOUND，顯示錯誤訊息，異常，結束程式
        ////  其他，異常，結束程式

        // 009700      FIND  DB-CLMR-IDX1  AT  DB-CLMR-CODE   = WK-CODE
        ClmrBus tClmr = clmrService.findById(wkCode);
        // 009800        ON  EXCEPTION
        // 009900        IF  DMSTATUS(NOTFOUND)
        // 010000            DISPLAY "FINDCLMR NOT FOUND" WK-CODE
        // 010100            CALL SYSTEM DMTERMINATE
        // 010200        ELSE
        // 010300            CALL SYSTEM DMTERMINATE
        // 010400        END-IF.
        if (Objects.isNull(tClmr)) {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "FINDCLMR NOT FOUND {}", wkCode);
            throw new LogicException("", "FINDCLMR NOT FOUND" + wkCode);
        }
        //// DB-CLMR-PBRNO主辦行
        // 010600      MOVE  DB-CLMR-PBRNO   TO   WK-PBRNO.
        wkPbrno = tClmr.getPbrno();
        // 010800 3000-SCCLMR-EXIT.
    }

    private void moveErrorResponse(LogicException e) {
        // this.event.setPeripheryRequest();
    }

    private void batchResponse() {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", null);
    }

    private void checkSortPath() {
        if (textFile.exists(sortPath)) {
            sort30200();
        }
    }

    private void sort30200() {
        File tmpFile = new File(sortPath);
        List<KeyRange> keyRanges = new ArrayList<>();
        keyRanges.add(new KeyRange(1, 2, SortBy.ASC));
        keyRanges.add(new KeyRange(2, 1, SortBy.ASC));
        keyRanges.add(new KeyRange(111, 3, SortBy.ASC));
        keyRanges.add(new KeyRange(25, 6, SortBy.ASC));
        keyRanges.add(new KeyRange(3, 6, SortBy.ASC));
        keyRanges.add(new KeyRange(9, 10, SortBy.ASC));
        externalSortUtil.sortingFile(tmpFile, tmpFile, keyRanges, CHARSET);
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
