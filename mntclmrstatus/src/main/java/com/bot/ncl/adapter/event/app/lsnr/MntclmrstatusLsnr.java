/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.Mntclmrstatus;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FileC012;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.exception.dbs.UpDateNAException;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.text.format.Text2VoFormatter;
import com.bot.txcontrol.util.transmit.FsapSyncSftpService;
import java.io.File;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("MntclmrstatusLsnr")
@Scope("prototype")
public class MntclmrstatusLsnr extends BatchListenerCase<Mntclmrstatus> {
    @Autowired private ClmrService clmrService;
    @Autowired private TextFileUtil textFile;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    @Autowired private FileC012 fileC012;
    @Autowired private FormatUtil formatUtil;

    private static final String CHARSET = "UTF-8";

    private Set<String> codeInC012;

    private int clmrIndex = 0;
    private String batchDate;
    private int clmrPageLimit = 10000;

    private int wkCnt;

    @Override
    public void onApplicationEvent(Mntclmrstatus event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "MntclmrstatusLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Mntclmrstatus event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "MntclmrstatusLsnr run()");

        init(event);

        queryClmr();
        batchResponse(event);
    }

    private void init(Mntclmrstatus event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "init()");
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();
        batchDate = formatUtil.pad9(labelMap.get("BBSDY"), 8); // 批次營業日
        wkCnt = 0;

        // 開啟檔案
        // 004900     OPEN   INPUT  FD-C012.
        // 005000     OPEN   UPDATE BOTSRDB.
        // 005100*

        // 下載來源檔
        String sourceFtpPath =
                "NCL"
                        + File.separator
                        + batchDate
                        + File.separator
                        + "C012_REST_"
                        + batchDate.substring(1)
                        + ".TXT"; // 來源檔在FTP的位置
        File c012RestFile = downloadFromSftp(sourceFtpPath);
        String sourceFileLocalPath = getLocalPath(c012RestFile);
        List<String> c012DataList = textFile.readFileContent(sourceFileLocalPath, CHARSET);

        // Wei: 只會用到檔案中的"收付類別(CODE)"資訊,且有檢查有無的需求,所以在這邊利用Set的特性處理
        codeInC012 = new HashSet<>();
        for (String c012Data : c012DataList) {
            text2VoFormatter.format(c012Data, fileC012);
            String code = fileC012.getCode();
            codeInC012.add(code);
        }
    }

    private void queryClmr() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "queryClmr()");
        List<ClmrBus> clmrList = clmrService.findAll(clmrIndex, clmrPageLimit);
        if (!Objects.isNull(clmrList) && !clmrList.isEmpty()) {
            for (ClmrBus clmr : clmrList) {
                String code = clmr.getCode();
                int stop = clmr.getStop();

                ApLogHelper.info(
                        log, false, LogType.NORMAL.getCode(), "queryClmr() code = {}", code);
                ApLogHelper.info(
                        log, false, LogType.NORMAL.getCode(), "queryClmr() stop = {}", stop);
                if (stop != 1 && stop != 2 && codeInC012.contains(code)) {
                    holdAndUpdateClmr(code);
                }
            }
            if (clmrList.size() == clmrPageLimit) {
                clmrIndex++;
                queryClmr();
            }
        } else {
            ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "queryClmr() clmrList is null");
        }
    }

    private void holdAndUpdateClmr(String code) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "holdAndUpdateClmr()");
        ClmrBus holdClmr = clmrService.holdById(code);
        if (!Objects.isNull(holdClmr)) {
            holdClmr.setStop(1);
            try {
                clmrService.update(holdClmr);
            } catch (UpDateNAException e) {
                ApLogHelper.error(
                        log,
                        false,
                        LogType.NORMAL.getCode(),
                        "holdAndUpdateClmr() UpDateNAException");
            }
            wkCnt++;
        } else {
            ApLogHelper.error(
                    log, false, LogType.NORMAL.getCode(), "holdAndUpdateClmr() holdClmr is null");
        }
    }

    private void batchResponse(Mntclmrstatus event) {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(event, "0000", "", null);
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
}
