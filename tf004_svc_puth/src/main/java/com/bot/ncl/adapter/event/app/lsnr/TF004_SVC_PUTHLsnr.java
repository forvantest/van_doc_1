/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.TF004_SVC_PUTH;
import com.bot.ncl.util.DataDiffUtil;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.batch.BatchUtil;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.transmit.FsapSyncSftpService;
import java.io.File;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.integration.file.remote.FileInfo;
import org.springframework.stereotype.Component;

@Slf4j
@Component("TF004_SVC_PUTHLsnr")
@Scope("prototype")
public class TF004_SVC_PUTHLsnr extends BatchListenerCase<TF004_SVC_PUTH> {

    @Autowired private DataDiffUtil dataDiffUtil;

    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    @Autowired private FsapBatchUtil fsapBatchUtil;

    @Autowired private BatchUtil batchUtil;
    @Autowired private TextFileUtil textFileUtil;

    private TF004_SVC_PUTH event;

    private String batchDate = "";
    private String updFg = "";

    private Date startTime;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(TF004_SVC_PUTH event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "TF004_SVCLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(TF004_SVC_PUTH event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "TF004_SVCLsnr run()");

        initParams(event);

        doDiff("PUTH");

        putTota();
    }

    private void initParams(TF004_SVC_PUTH event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "initParams()");
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();
        Map<String, String> textMap = arrayMap.get("textMap").getMapAttrMap();
        batchDate = labelMap.get("BBSDY"); // 批次營業日
        updFg = textMap.get("UPDFG");
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "batchDate = {}", batchDate);

        this.event = event;

        listFTP("NCL/" + batchDate);
        listFTP("NCL/" + batchDate + "/CHKDB");

        startTime = new Date();
    }

    private void doDiff(String layoutName) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "doDiff() tableName={}", layoutName);
        startTime = batchUtil.refreshBatchTransaction(batchTransaction, startTime);

        //         下載來源檔
        String sourceFtpPath =
                "NCL"
                        + File.separator
                        + batchDate
                        + File.separator
                        + "CHK"
                        + layoutName
                        + File.separator
                        + batchDate.substring(2); // 來源檔在FTP的位置
        File sourceFile = downloadFromSftp(sourceFtpPath);

        // 下載目標檔
        String targetFtpPath =
                "NCL"
                        + File.separator
                        + batchDate
                        + File.separator
                        + layoutName
                        + File.separator
                        + batchDate.substring(2); // 目標檔在FTP的位置
        File targetFile = downloadFromSftp(targetFtpPath);

        String sourceFileLocalPath = getLocalPath(sourceFile);
        String targetFileLocalPath = getLocalPath(targetFile);
        //        String sourceFileLocalPath = "..\\BOT\\NCL\\BH\\CHKPUTH";
        //        String targetFileLocalPath = "..\\BOT\\NCL\\BH\\PUTH";
        //        String targetFileLocalPathS = "..\\BOT\\NCL\\BH\\PUTHS";
        //        textFileUtil.deleteFile(targetFileLocalPathS);
        //        // 轉big5
        //        List<String> list = textFileUtil.readFileContent(targetFileLocalPath, "UTF-8");
        //        textFileUtil.writeFileContent(targetFileLocalPathS, list, "BIG5");

        Map<String, String> result =
                dataDiffUtil.diffFile(
                        sourceFileLocalPath,
                        targetFileLocalPath,
                        layoutName,
                        this.batchTransaction,
                        startTime,
                        updFg);

        this.startTime = dataDiffUtil.getStartTime();

        handleDiffResult(result);
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
            //            fsapBatchUtil.response(event, "E999", "檔案不存在(" + fileFtpPath + ")");
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
            //            fsapBatchUtil.response(event, "E999", "目錄不存在(" + dir + ")");
            throw new LogicException("GE999", "目錄不存在(" + dir + ")");
        }

        if (!Objects.isNull(fileInfoList) && !fileInfoList.isEmpty()) {
            for (FileInfo fileInfo : fileInfoList) {
                ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "fileInfo = {}", fileInfo);
            }
        }
    }

    private void handleDiffResult(Map<String, String> result) {
        if (!Objects.isNull(result) && result.containsKey("STATUS")) {
            String status = result.get("STATUS");
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "status={}", status);
            if (status.equals("failed")) {
                String errorStep = result.get("ERROR_STEP");
                String errorMsg = result.get(errorStep);
                throw new LogicException("GE999", "比對失敗(" + errorStep + "," + errorMsg + ")");
            }
        }
    }

    private void putTota() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "putTota()");
        Map<String, String> responseTextMap = new HashMap<>();
        responseTextMap.put("OutputParamX", "XXX");
        responseTextMap.put("OutputParamY", "YYY");
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(event, "0000", "", responseTextMap);
    }
}
