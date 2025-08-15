/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.TF000_SVC;
import com.bot.ncl.adapter.event.app.services.AnalyseService;
import com.bot.ncl.adapter.event.app.services.SqlGenService;
import com.bot.ncl.adapter.event.app.util.FileUtil;
import com.bot.ncl.util.DiffParseUtil;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.transmit.FsapSyncSftpService;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.integration.file.remote.FileInfo;
import org.springframework.stereotype.Component;

@Slf4j
@Component("TF000_SVCLsnr")
@Scope("prototype")
public class TF000_SVCLsnr extends BatchListenerCase<TF000_SVC> {

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    @Autowired private TextFileUtil textFileUtil;

    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    @Autowired private FsapBatchUtil fsapBatchUtil;

    @Autowired private FileUtil fileUtil;

    @Autowired private SqlGenService sqlGenService;

    @Autowired private DiffParseUtil diffParseUtil;

    private List<String> errorDetail;

    private String errorDetailOutputFilePath;

    private List<String> tf000Summary;

    private String tf000SummaryOutputFilePath;

    private static final String UTF8 = "UTF-8";

    private TF000_SVC event;

    private String batchDate = "";

    private Date startTime;

    private long astarErrorSize;
    private List<String> lModifyType = Arrays.asList("Insert", "Update", "Delete");

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(TF000_SVC event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "TF000_SVCLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(TF000_SVC event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "TF000_SVCLsnr run()");
        initParams(event);

        deleteLastErrorDataFile("CLMR"); // 刪除上一輪處理結果
        doTransfer("CLMR");
        deleteLastErrorDataFile("CLBAF");
        doTransfer("CLBAF");
        deleteLastErrorDataFile("CLCMP");
        doTransfer("CLCMP");
        deleteLastErrorDataFile("CLDTL");
        doTransfer("CLDTL");

        outputSummary();
        putTota();
    }

    private void initParams(TF000_SVC event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "initParams()");
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();
        batchDate = labelMap.get("BBSDY"); // 批次營業日
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "batchDate = {}", batchDate);

        this.event = event;

        listFTP("NCL/" + batchDate);

        startTime = new Date();

        initializeSummary();
    }

    private void doTransfer(String tableName) {
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "doTransfer() tableName={}", tableName);
        astarErrorSize = 0;
        initializeErrorDetail(tableName);
        addSummary("[轉換資料表]:" + tableName);
        // 下載來源檔
        String sourceFtpPath =
                "NCL"
                        + File.separator
                        + batchDate
                        + File.separator
                        + "NCL"
                        + tableName
                        + "_"
                        + batchDate.substring(1); // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath);
        addSummary("[來源檔FTP路徑]:" + sourceFtpPath);
        String sourceFileLocalPath = getLocalPath(sourceFile);
        List<String> data = readFile(sourceFileLocalPath, tableName);
        if (data == null) {
            throw new LogicException("GE999", "來源資料為空或無法讀取(" + sourceFileLocalPath + ")");
        }
        long sourceSize = data.size();
        addSummary("[來源檔轉碼成功筆數]:" + sourceSize);
        addSummary("[來源檔轉碼失敗筆數]:" + astarErrorSize);
        int BATCH_SIZE = 500000;
        for (int i = 0; i < sourceSize; i += BATCH_SIZE) {
            long end = Math.min((long) i + BATCH_SIZE, sourceSize);
            //            List<String> batch = data.subList(i, (int) end);
            List<Map<String, String>> list = analyse(tableName, data.subList(i, (int) end));
            createSqlCommand(tableName, list, sourceSize);
            list.clear();
        }
        addSummary("====================");
        outputErrorDetail();
    }

    private File downloadFromSftp(String fileFtpPath) {
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "downloadFromSftp fileFtpPath = {}",
                fileFtpPath);
        File file;
        try {
            file = fsapSyncSftpService.downloadFiles(fileFtpPath);
        } catch (Exception e) {
            ApLogHelper.error(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "downloadFromSftp error = {}",
                    e.getMessage());
            throw new LogicException("GE999", "檔案不存在(" + fileFtpPath + ")");
        }
        return file;
    }

    private String getLocalPath(File file) {
        return Objects.isNull(file) ? "" : file.getAbsolutePath();
    }

    private void listFTP(String dir) {
        List<FileInfo> fileInfoList;
        try {
            fileInfoList = fsapSyncSftpService.listFile(dir);
        } catch (Exception e) {
            ApLogHelper.error(
                    log, false, LogType.NORMAL.getCode(), "listFile ERROR = {}", e.getMessage());
            throw new LogicException("GE999", "目錄不存在(" + dir + ")");
        }

        if (!Objects.isNull(fileInfoList) && !fileInfoList.isEmpty()) {
            for (FileInfo fileInfo : fileInfoList) {
                ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "fileInfo = {}", fileInfo);
            }
        }
    }

    private List<String> readFile(String filePath, String tableName) {
        List<String> data = null;
        try {
            data = fileUtil.readFileToByteArrayByLine(filePath);
        } catch (Exception e) {
            ApLogHelper.error(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "FileUtil.readFile Exception = " + e.getMessage());
            throw new LogicException("GE999", "讀取檔案失敗(" + filePath + ")");
        }
        if (!fileUtil.getAstarErrorMsg().isEmpty()) {
            astarErrorSize = fileUtil.getAstarErrorMsg().size();
            for (String astarErrorMsg : fileUtil.getAstarErrorMsg()) {
                addErrorDetail(astarErrorMsg);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String timestamp = now.format(formatter);
        String rawDataUtf8FilePath =
                fileDir + "TF000_SRC_" + tableName + "_TO_UTF8_" + timestamp + ".txt";
        textFileUtil.writeFileContent(rawDataUtf8FilePath, data, UTF8);
        return data;
    }

    private List<Map<String, String>> analyse(String tableName, List<String> data) {
        List<Map<String, String>> result = new ArrayList<>();
        Map<String, String> map;
        AnalyseService analyseService = new AnalyseService();
        List<String> analyError = new ArrayList<>();
        try {
            analyseService.loadTableLayout(tableName);
        } catch (LogicException e) {
            ApLogHelper.error(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "load table layout Error:" + e.getErrMsg());
            throw new LogicException("GE999", "讀取資料結構失敗(" + tableName + ")");
        }
        String line = "";
        try {
            int rowNumber = 1;
            int tableLayoutSize = analyseService.getTableLayoutMapSize();
            for (String s : data) {
                line = s;
                String[] values = s.split("\\^");
                String[] fullColumns = new String[tableLayoutSize];
                try {
                    System.arraycopy(values, 0, fullColumns, 0, values.length);
                } catch (Exception e) {
                    ApLogHelper.error(
                            log,
                            false,
                            LogType.NORMAL.getCode(),
                            "data string split Exception = " + e.getMessage());
                    ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "line = [{}] ", line);
                    analyError.add(line);
                    continue;
                }
                map = analyseService.analyse(fullColumns, rowNumber, tableName);
                if (map != null) {
                    result.add(map);
                } else {
                    addErrorDetail(analyseService.getAnalyseErrorMsg());
                }
                rowNumber++;
            }
        } catch (Exception e) {
            ApLogHelper.error(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "data string split Exception = " + e.getMessage());
            throw new LogicException("GE999", "切割欄位失敗(" + tableName + "),line=\"" + line + "\"");
        }
        if (!analyError.isEmpty()) {
            String analyFileName = "ANALYERROR" + tableName + "_" + batchDate.substring(1);
            textFileUtil.writeFileContent(fileDir + analyFileName, analyError, UTF8);
            analyError.clear();
        }

        return result;
    }

    private void createSqlCommand(
            String tableName, List<Map<String, String>> list, long sourceSize) {
        sqlGenService.createCommand(tableName, list, this.batchTransaction, startTime, sourceSize);
        if (!sqlGenService.getSqlErrorMsg().isEmpty()) {
            for (String sqlErrorMsg : sqlGenService.getSqlErrorMsg()) {
                addErrorDetail(sqlErrorMsg);
            }
        }
        for (String insertResult : sqlGenService.getInsertResult()) {
            addSummary(insertResult);
        }
    }

    private void putTota() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "putTota()");
        Map<String, String> responseTextMap = new HashMap<>();
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(event, "0000", "", responseTextMap);
    }

    private void initializeErrorDetail(String tableName) {
        this.errorDetail = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String timestamp = now.format(formatter);
        errorDetailOutputFilePath =
                fileDir + "TF000_ErrorDetail_" + tableName + "_" + timestamp + ".txt";
    }

    private void addErrorDetail(String msg) {
        errorDetail.add(msg);
    }

    private void outputErrorDetail() {
        textFileUtil.writeFileContent(errorDetailOutputFilePath, errorDetail, UTF8);
    }

    private void initializeSummary() {
        this.tf000Summary = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String timestamp = now.format(formatter);
        tf000SummaryOutputFilePath = fileDir + "TF000_Summary_" + timestamp + ".txt";
    }

    private void addSummary(String msg) {
        tf000Summary.add(msg);
    }

    private void outputSummary() {
        textFileUtil.writeFileContent(tf000SummaryOutputFilePath, tf000Summary, UTF8);
    }

    private void deleteLastErrorDataFile(String tableName) {

        switch (tableName) {
            case "CLMR" -> {
                deleteFile("CLMR");
                deleteFile("CLTMR");
                deleteFile("CLTOT");
                deleteFile("CLFEE");
                deleteFile("CLMC");
            }
            case "CLCMP" -> {
                deleteFile("CLCMP");
                deleteFile("CLDMR");
            }
            case "CLDTL" -> {
                deleteFile("CLDTL");
            }
            case "CLBAF" -> {
                deleteFile("CLBAF");
            }
        }
    }

    private void deleteFile(String tableName) {
        String analyFileName = "ANALYERROR" + tableName + "_" + batchDate.substring(1);
        textFileUtil.deleteFile(fileDir + analyFileName);
        textFileUtil.deleteFile(fileDir + "TempError" + tableName);

        for (String type : lModifyType) {
            textFileUtil.deleteFile(fileDir + type + "Error" + tableName);
        }
    }
}
