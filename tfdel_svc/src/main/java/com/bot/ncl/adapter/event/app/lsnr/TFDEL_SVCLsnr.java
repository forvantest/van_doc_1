/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.Format;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.TFDEL_SVC;
import com.bot.ncl.jpa.anq.svc.cm.TruncateService;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.batch.BatchUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.dto.grpcclient.GrpcClientDto;
import com.bot.txcontrol.eum.Constant;
import com.bot.txcontrol.eum.Env;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.transmit.FsapSyncSftpService;
import com.bot.txcontrol.util.transmit.GrpcClientService;
import java.io.File;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.integration.file.remote.FileInfo;
import org.springframework.stereotype.Component;

@Slf4j
@Component("TFDEL_SVCLsnr")
@Scope("prototype")
public class TFDEL_SVCLsnr extends BatchListenerCase<TFDEL_SVC> {

    @Value("${spring.profiles.active}")
    private String env;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    @Autowired private FsapBatchUtil fsapBatchUtil;

    @Autowired private BatchUtil batchUtil;

    @Autowired private TruncateService truncateService;

    @Autowired private GrpcClientService grpcClientService;

    private TFDEL_SVC event;

    private Date startTime;

    private String localFlag;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(TFDEL_SVC event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "TFDEL_SVCLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(TFDEL_SVC event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "TFDEL_SVCLsnr run()");
        initParams(event);

        if (localFlag.equals("Y")) {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "cleanNclLocal()");
            cleanNclLocal();
        } else {
            truncateAllNcl();

            cleanNclSftp("NCL");

            cleanNclLocal();

            cleanNclOtherServerLocal();

            putTota();
        }
    }

    private void initParams(TFDEL_SVC event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "initParams()");
        this.event = event;
        startTime = new Date();

        localFlag = "";
        try {
            Map<String, String> textMap =
                    this.event
                            .getPeripheryRequest()
                            .getPayload()
                            .getPyheader()
                            .getArrayAttrMap()
                            .get("textMap")
                            .getMapAttrMap();
            localFlag = textMap.getOrDefault("localFlag", "N");
        } catch (Exception e) {
            ApLogHelper.info(
                    log, false, LogType.NORMAL.getCode(), "localFlag error={}", e.getMessage());
            localFlag = "N";
        }
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "localFlag={}", localFlag);
    }

    private void truncateAllNcl() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "truncateAllNcl()");

        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "truncateClmr()");
        try {
            truncateService.truncateClmr();
        } catch (Exception e) {
            throw new LogicException("GE999", "truncateClmr error" + e.getMessage());
        }
        startTime = batchUtil.refreshBatchTransaction(batchTransaction, startTime);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "truncateCltmr()");
        try {
            truncateService.truncateCltmr();
        } catch (Exception e) {
            throw new LogicException("GE999", "truncateCltmr error" + e.getMessage());
        }
        startTime = batchUtil.refreshBatchTransaction(batchTransaction, startTime);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "truncateCltot()");
        try {
            truncateService.truncateCltot();
        } catch (Exception e) {
            throw new LogicException("GE999", "truncateCltot error" + e.getMessage());
        }
        startTime = batchUtil.refreshBatchTransaction(batchTransaction, startTime);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "truncateClfee()");
        try {
            truncateService.truncateClfee();
        } catch (Exception e) {
            throw new LogicException("GE999", "truncateClfee error" + e.getMessage());
        }
        startTime = batchUtil.refreshBatchTransaction(batchTransaction, startTime);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "truncateClmc()");
        try {
            truncateService.truncateClmc();
        } catch (Exception e) {
            throw new LogicException("GE999", "truncateClmc error" + e.getMessage());
        }
        startTime = batchUtil.refreshBatchTransaction(batchTransaction, startTime);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "truncateClbaf()");
        try {
            truncateService.truncateClbaf();
        } catch (Exception e) {
            throw new LogicException("GE999", "truncateClbaf error" + e.getMessage());
        }
        startTime = batchUtil.refreshBatchTransaction(batchTransaction, startTime);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "truncateClcmp()");
        try {
            truncateService.truncateClcmp();
        } catch (Exception e) {
            throw new LogicException("GE999", "truncateClcmp error" + e.getMessage());
        }
        startTime = batchUtil.refreshBatchTransaction(batchTransaction, startTime);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "truncateCldmr()");
        try {
            truncateService.truncateCldmr();
        } catch (Exception e) {
            throw new LogicException("GE999", "truncateCldmr error" + e.getMessage());
        }
        startTime = batchUtil.refreshBatchTransaction(batchTransaction, startTime);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "truncateCldtl()");
        try {
            truncateService.truncateCldtl();
        } catch (Exception e) {
            throw new LogicException("GE999", "truncateCldtl error" + e.getMessage());
        }
        startTime = batchUtil.refreshBatchTransaction(batchTransaction, startTime);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "truncateClhold()");
        try {
            truncateService.truncateClhold();
        } catch (Exception e) {
            throw new LogicException("GE999", "truncateClhold error" + e.getMessage());
        }
        startTime = batchUtil.refreshBatchTransaction(batchTransaction, startTime);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "truncateCltmp()");
        try {
            truncateService.truncateCltmp();
        } catch (Exception e) {
            throw new LogicException("GE999", "truncateCltmp error" + e.getMessage());
        }
        startTime = batchUtil.refreshBatchTransaction(batchTransaction, startTime);

        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "truncateTxrecsvc()");
        try {
            truncateService.truncateTxrecsvc();
        } catch (Exception e) {
            throw new LogicException("GE999", "truncateTxrecsvc error" + e.getMessage());
        }
        startTime = batchUtil.refreshBatchTransaction(batchTransaction, startTime);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "truncateTxrecmain()");
        try {
            truncateService.truncateTxrecmain();
        } catch (Exception e) {
            throw new LogicException("GE999", "truncateTxrecmain error" + e.getMessage());
        }
        startTime = batchUtil.refreshBatchTransaction(batchTransaction, startTime);
    }

    private void cleanNclSftp(String path) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "cleanNclSftp(),path={}", path);
        List<FileInfo> fileInfoList;
        try {
            fileInfoList = fsapSyncSftpService.listFile(path);
        } catch (Exception e) {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "目錄不存在");
            return;
        }

        if (!Objects.isNull(fileInfoList) && !fileInfoList.isEmpty()) {
            for (FileInfo fileInfo : fileInfoList) {
                String filePath = path + File.separator + fileInfo.getFilename();
                ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "filePath={}", filePath);

                File file = new File(filePath);
                if (file.isDirectory()) {
                    // 2024-11-28 Wei: 若為目錄,遞迴處理
                    cleanNclSftp(filePath);
                } else {
                    try {
                        fsapSyncSftpService.deleteFile(filePath);
                    } catch (Exception e) {
                        ApLogHelper.error(
                                log, false, LogType.NORMAL.getCode(), "刪檔失敗,filePath={}", filePath);
                    }
                }
            }
        }
    }

    private void cleanNclLocal() {
        // 把fileDir內的資料夾及檔案刪除
        ApLogHelper.info(log, false, LogType.APLOG.getCode(), "delete filrDir={}", fileDir);
        File directory = new File(fileDir);
        if (directory.exists() && directory.isDirectory()) {
            clearDirectoryContents(directory);
            ApLogHelper.info(log, false, LogType.APLOG.getCode(), "清檔作業完成：" + fileDir);
        } else {
            ApLogHelper.warn(log, false, LogType.APLOG.getCode(), "指定目錄不存在：" + fileDir);
        }
    }

    private void clearDirectoryContents(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    // 遞迴刪除子目錄的內容
                    clearDirectoryContents(file);
                }
                // 刪除檔案或空的子目錄
                if (file.delete()) {
                    ApLogHelper.info(
                            log, false, LogType.APLOG.getCode(), "已刪除：" + file.getAbsolutePath());
                } else {
                    ApLogHelper.warn(
                            log, false, LogType.APLOG.getCode(), "無法刪除：" + file.getAbsolutePath());
                }
            }
        }
    }

    private void cleanNclOtherServerLocal() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "cleanNclOtherServerLocal()");
        List<Map<String, String>> apInstances = grpcClientService.getApInstances("ncl");

        GrpcClientDto grpcClientDto = prepareGrpcDto();

        for (Map<String, String> apInstance : apInstances) {
            // 發動NCL-API TFDEL_AP_LOCAL
            sendGrpcDto(grpcClientDto, apInstance.get("hostname"), apInstance.get("port"));
        }
        List<Map<String, String>> batchInstances = grpcClientService.getApInstances("ncl-batch");
        for (Map<String, String> batchInstance : batchInstances) {
            // 發動NCL-API TFDEL_AP_LOCAL
            sendGrpcDto(grpcClientDto, batchInstance.get("hostname"), batchInstance.get("port"));
        }
    }

    private void sendGrpcDto(GrpcClientDto grpcClientDto, String hostname, String port) {
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "sendGrpcDto,hostname={},port={}",
                hostname,
                port);
        if (Env.LOCAL.getCode().equals(this.env)) {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "此為local測試不發送");
            return;
        }
        grpcClientService.send(grpcClientDto, hostname, port);
    }

    private GrpcClientDto prepareGrpcDto() {
        GrpcClientDto grpcClientDto = new GrpcClientDto();
        grpcClientDto.setRequestid(event.getPeripheryRequest().getApheader().getXBotRequestId());
        grpcClientDto.setClientseq(event.getPeripheryRequest().getApheader().getXBotClientSeq());
        settingDto(grpcClientDto);
        return grpcClientDto;
    }

    private void settingDto(GrpcClientDto grpcClientDto) {
        grpcClientDto.setMode("-1");
        grpcClientDto.setSendSelf(false);
        grpcClientDto.setBatchflag("S");
        grpcClientDto.setAsync(false);
        grpcClientDto.setServerid(Constant.NCL.getCode());
        grpcClientDto.setClientid(Constant.NCL_BATCH.getCode());

        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        Map<String, String> textMap = new HashMap<>(arrayMap.get("textMap").getMapAttrMap());
        textMap.put("localFlag", "Y");
        grpcClientDto.put2arrayMap("labelMap", arrayMap.get("labelMap").getMapAttrMap());
        grpcClientDto.put2arrayMap("textMap", textMap);

        grpcClientDto.setTxcode("TFDEL");
        grpcClientDto.setDscpt("");
        grpcClientDto.setFmtid("CL_BATCH_I");
        grpcClientDto.setCharsets(Charsets.UTF8);
        grpcClientDto.setFormat(Format.TEXT);
        grpcClientDto.setData(null);
    }

    private void putTota() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "putTota()");
        Map<String, String> responseTextMap = new HashMap<>();
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(event, "0000", "", responseTextMap);
    }
}
