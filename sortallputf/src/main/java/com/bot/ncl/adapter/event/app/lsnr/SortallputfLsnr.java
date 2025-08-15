/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.Sortallputf;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FilePUTFCTL;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.sort.ExternalSortUtil;
import com.bot.ncl.util.sort.eum.SortBy;
import com.bot.ncl.util.sort.vo.KeyRange;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.date.DateUtil;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.text.format.Text2VoFormatter;
import com.bot.txcontrol.util.text.format.Vo2TextFormatter;
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
@Component("SortallputfLsnr")
@Scope("prototype")
public class SortallputfLsnr extends BatchListenerCase<Sortallputf> {

    @Autowired private TextFileUtil textFile;

    @Autowired private FormatUtil formatUtil;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    @Autowired private Parse parse;

    @Autowired private DateUtil dateUtil;

    @Autowired private Text2VoFormatter text2VoFormatter;

    @Autowired private Vo2TextFormatter vo2TextFormatter;

    @Autowired private ExternalSortUtil externalSortUtil;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CONVF_DATA = "DATA";

    // DATE REFERENCE
    // 77 WK-TASK-DATE                       PIC X(06).
    private int date; // TODO: 待確認BATCH參數名稱
    // PUTFILE REFERENCE
    // 77 WK-TASK-PUTFILE                    PIC X(10).
    private String putfile; // TODO: 待確認BATCH參數名稱
    // CODE1 REFERENCE
    // 77 WK-TASK-CODE1                      PIC X(05).
    // 代收類別前5碼=PUTFCTL-PUTNAME3=PUTFCTL-PUTNAME(3:5)
    private String code1; // TODO: 待確認BATCH參數名稱
    private String tbsdy;
    private String processDate;
    // 001600 FILE             SECTION.
    // 001700 FD  FD-PUTFCTL
    // 001800     COPY "SYM/CL/BH/FD/PUTFCTL.".
    @Autowired private FilePUTFCTL fdPutfctl;

    private String putfctlPath;

    private static final String UTF8 = "UTF-8";
    private static final String PATH_SEPARATOR = File.separator;

    @Override
    public void onApplicationEvent(Sortallputf event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "SortallputfLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Sortallputf event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "SortallputfLsnr run()");

        init(event);

        sortAllPutf();

        batchResponse(event);
    }

    private void init(Sortallputf event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "init()");
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();

        // 設定本營業日、檔名日期變數值
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        String putfCtlDir = fileDir + CONVF_DATA + PATH_SEPARATOR + processDate;
        putfctlPath = putfCtlDir + PATH_SEPARATOR + "PUTFCTL";
        textFile.deleteFile(putfctlPath);
        String sourceFtpPath =
                "NCL"
                        + File.separator
                        + tbsdy
                        + File.separator
                        + "2FSAP"
                        + File.separator
                        + "DATA"
                        + File.separator
                        + "PUTFCTL"; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, putfCtlDir);
        if (sourceFile != null) {
            putfctlPath = getLocalPath(sourceFile);
        }
    }

    private void sortAllPutf() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "sortAllPutf()");
        List<String> putfctlDataList = textFile.readFileContent(putfctlPath, UTF8);

        for (String putfctlData : putfctlDataList) {
            text2VoFormatter.format(putfctlData, fdPutfctl);
            int t = 0;
            date = 0;
            putfile = null;
            code1 = null;
            // 參考原SPUTF邏輯
            // 006400*    IF          PUTFCTL-PUTNAME3    =      "30001"
            // 006420*                                OR  =      "11004"
            // 006440     IF          PUTFCTL-PUTNAME3    =      "41011"
            // 006500*                                OR  =      "30002"
            // 006550                                 OR  =      "15868"
            // 006560                                 OR  =      "51004"
            // 006580*                                OR  =      "36F20"
            // 006600*                                OR  =      "30009"
            // 006650                                 OR  =      "30200"
            // 006700       MOVE      PUTFCTL-GENDT       TO     WK-TASK-DATE
            // 006800       MOVE      PUTFCTL-PUTFILE     TO     WK-TASK-PUTFILE
            // 006900       MOVE      PUTFCTL-PUTNAME3    TO     WK-TASK-CODE1
            // 006950     CHANGE ATTRIBUTE TASKVALUE OF MYSELF TO 1
            // 007000       GO TO     0000-MAIN-EXIT.
            // 007010* 縣市政府稅費解繳之代收類別亦需排序
            // 007020     IF   (    PUTFCTL-PUTNAME3   NOT  <    "11011"
            // 007040           AND PUTFCTL-PUTNAME3   NOT  >    "11115"  )
            // 007050       OR (    PUTFCTL-PUTNAME3   =         "11331"  )
            // 007052       OR (    PUTFCTL-PUTNAME3   NOT  <    "11521"
            // 007054           AND PUTFCTL-PUTNAME3   NOT  >    "11525"  )
            // 007060       MOVE      PUTFCTL-GENDT       TO     WK-TASK-DATE
            // 007080       MOVE      PUTFCTL-PUTFILE     TO     WK-TASK-PUTFILE
            // 007100       MOVE      PUTFCTL-PUTNAME3    TO     WK-TASK-CODE1
            // 007120     CHANGE ATTRIBUTE TASKVALUE OF MYSELF TO 2
            // 007140       GO TO     0000-MAIN-EXIT.
            String putname3 = fdPutfctl.getPutname().substring(2, 7);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "putname3 = {}", putname3);
            if (putname3.equals("41011")
                    || putname3.equals("15868")
                    || putname3.equals("51004")
                    || putname3.equals("30200")) {
                t = 1;
                String gendt = fdPutfctl.getGendt();
                date = parse.isNumeric(gendt) ? parse.string2Integer(gendt) : 0;
                putfile = fdPutfctl.getPuttype() + fdPutfctl.getPutname();
                code1 = putname3;
            } else if (parse.isNumeric(putname3)) {
                int intPutname3 = parse.string2Integer(putname3);
                if ((intPutname3 >= 11011 && intPutname3 <= 11115)
                        || intPutname3 == 11331
                        || (intPutname3 >= 11521 && intPutname3 <= 11525)) {
                    t = 2;
                    String gendt = fdPutfctl.getGendt();
                    date = parse.isNumeric(gendt) ? parse.string2Integer(gendt) : 0;
                    putfile = fdPutfctl.getPuttype() + fdPutfctl.getPutname();
                    code1 = putname3;
                }
            }
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "t = {}", t);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "date = {}", date);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "putfile = {}", putfile);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "code1 = {}", code1);

            if (t == 1) {
                // RUN  OBJ/CL/BH/SORT/#CODE1
                sortPutfByCode();
            } else if (t == 2) {
                // RUN  OBJ/CL/BH/SORT/TAX
                sortTaxPutf();
            }
        }
    }

    private void sortPutfByCode() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "sortPutfByCode()");
        // 41011
        // 15868
        // 51004
        // 30200
        List<KeyRange> keyRanges = new ArrayList<>();
        switch (code1) {
            case "41011":
                sort41011(keyRanges);
                break;
            case "15868":
                sort15868(keyRanges);
                break;
            case "51004":
                sort51004(keyRanges);
                break;
            case "30200":
                sort30200(keyRanges);
                break;
            default:
                ApLogHelper.info(
                        log, false, LogType.NORMAL.getCode(), "unexpected code1 = {}", code1);
                return;
        }
        String putfDir =
                fileDir
                        + "DATA"
                        + File.separator
                        + processDate
                        + File.separator
                        + "PUTF"
                        + PATH_SEPARATOR
                        + date;
        String putfFilePath = putfDir + PATH_SEPARATOR + putfile;
        textFile.deleteFile(putfFilePath);
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
                        + putfile; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, putfDir);
        if (sourceFile != null) {
            putfFilePath = getLocalPath(sourceFile);
        }

        File file = Paths.get(putfFilePath).toFile();
        externalSortUtil.sortingFile(file, file, keyRanges, UTF8);
        upload(putfFilePath, "DATA", "PUTF");
    }

    private void sort41011(List<KeyRange> keyRanges) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "sort41011()");
        // DISKSORT 00000100
        // FILE IN  (TITLE = "DATA/CL/BH/SPUTF")                                   00000200
        // FILE OUT (TITLE = "DATA/CL/BH/SPUTF")                                   00000300
        // KEY (  1  1 A )                                                         00000320
        // KEY (  2  1 A )                                                         00000340
        // KEY ( 65  8 A )                                                         00000350
        keyRanges.add(new KeyRange(1, 1, SortBy.ASC));
        keyRanges.add(new KeyRange(2, 1, SortBy.ASC));
        keyRanges.add(new KeyRange(65, 8, SortBy.ASC));
    }

    private void sort15868(List<KeyRange> keyRanges) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "sort15868()");
        // DISKSORT                                                                00000100
        // FILE IN  (TITLE = "DATA/CL/BH/SPUTF")                                   00000200
        // FILE OUT (TITLE = "DATA/CL/BH/SPUTF")                                   00000300
        // KEY (  1  1 A )                                                         00000350
        // KEY (  2  1 A )                                                         00000400
        // KEY ( 37  3 A )                                                         00000450
        // KEY (  9  8 A )                                                         00000550
        keyRanges.add(new KeyRange(1, 1, SortBy.ASC));
        keyRanges.add(new KeyRange(2, 1, SortBy.ASC));
        keyRanges.add(new KeyRange(37, 3, SortBy.ASC));
        keyRanges.add(new KeyRange(9, 8, SortBy.ASC));
    }

    private void sort51004(List<KeyRange> keyRanges) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "sort51004()");
        // DISKSORT                                                                00000100
        // FILE IN  (TITLE = "DATA/CL/BH/SPUTF")                                   00000200
        // FILE OUT (TITLE = "DATA/CL/BH/SPUTF")                                   00000300
        // KEY (  1  1 A )                                                         00000400
        // KEY (  2  1 A )                                                         00000500
        // KEY ( 55  3 A )                                                         00000600
        keyRanges.add(new KeyRange(1, 1, SortBy.ASC));
        keyRanges.add(new KeyRange(2, 1, SortBy.ASC));
        keyRanges.add(new KeyRange(55, 3, SortBy.ASC));
    }

    private void sort30200(List<KeyRange> keyRanges) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "sort30200()");
        // DISKSORT                                                                00000100
        // FILE IN  (TITLE = "DATA/CL/BH/SPUTF")                                   00000200
        // FILE OUT (TITLE = "DATA/CL/BH/SPUTF")                                   00000300
        // KEY (  1  1 A )                                                         00000400
        // KEY (  2  1 A )                                                         00000500
        // KEY (111  3 A )                                                         00000600
        // KEY ( 25  6 A )                                                         00000700
        // KEY (  3  6 A )                                                         00000750
        // KEY (  9 10 A )                                                         00000800
        keyRanges.add(new KeyRange(1, 1, SortBy.ASC));
        keyRanges.add(new KeyRange(2, 1, SortBy.ASC));
        keyRanges.add(new KeyRange(111, 3, SortBy.ASC));
        keyRanges.add(new KeyRange(25, 6, SortBy.ASC));
        keyRanges.add(new KeyRange(3, 6, SortBy.ASC));
        keyRanges.add(new KeyRange(9, 10, SortBy.ASC));
    }

    private void sortTaxPutf() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "sortTaxPutf()");

        // 參考 SYM_CL_BH_SORT_TAX
        // DISKSORT                                                                00000100
        // FILE IN  (TITLE = "DATA/CL/BH/SPUTF")                                   00000200
        // FILE OUT (TITLE = "DATA/CL/BH/SPUTF")                                   00000300
        // KEY (  1   1 A )                                                        00000400
        // KEY (  2   1 A )                                                        00000500
        // KEY (  73  2 A )                                                        00000600
        // KEY (  54  7 A )                                                        00000700
        List<KeyRange> keyRanges = new ArrayList<>();
        keyRanges.add(new KeyRange(1, 1, SortBy.ASC));
        keyRanges.add(new KeyRange(2, 1, SortBy.ASC));
        keyRanges.add(new KeyRange(73, 2, SortBy.ASC));
        keyRanges.add(new KeyRange(54, 7, SortBy.ASC));
        String putfDir =
                fileDir
                        + "DATA"
                        + File.separator
                        + processDate
                        + File.separator
                        + "PUTF"
                        + PATH_SEPARATOR
                        + date;
        String putfFilePath = putfDir + PATH_SEPARATOR + putfile;
        textFile.deleteFile(putfFilePath);
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
                        + putfile; // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath, putfDir);
        if (sourceFile != null) {
            putfFilePath = getLocalPath(sourceFile);
        }

        File file = Paths.get(putfFilePath).toFile();
        externalSortUtil.sortingFile(file, file, keyRanges, UTF8);
        upload(putfFilePath, "DATA", "PUTF");
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

    private void moveErrorResponse(LogicException e) {
        // event.setPeripheryRequest();
    }

    private void batchResponse(Sortallputf event) {
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
}
