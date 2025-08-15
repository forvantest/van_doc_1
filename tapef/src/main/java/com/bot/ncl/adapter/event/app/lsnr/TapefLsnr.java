/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.ncl.adapter.event.app.evt.Tapef;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.util.transmit.FsapSyncSftpService;
import java.io.File;
import java.util.Objects;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("TapefLsnr")
@Scope("prototype")
public class TapefLsnr extends BatchListenerCase<Tapef> {

    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    @Override
    public void onApplicationEvent(Tapef event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "TapefLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Tapef event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "TapefLsnr run()");
        // TODO: 待確認BATCH參數名稱
        // D ,PUTFILE ,BSIZE ,MSIZE ,FSIZE ,CODE

        // 在這裡轉換COBOL程式邏輯
        // 打開文件
        openFdPutfCtl();

        // 主程序邏輯
        mainRoutine(event);

        // 顯示結束信息
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "SYM/CL/BH/TAPEF RETURN BLOCKSIZE OK");

        // 關閉文件
        closeFdPutfCtl();
        batchResponse(event);
    }

    private void openFdPutfCtl() {
        // 打開 FD-PUTFCTL 文件邏輯
    }

    private void closeFdPutfCtl() {
        // 關閉 FD-PUTFCTL 文件邏輯
    }

    private void mainRoutine(Tapef event) {
        // 主邏輯
        try {
            // 移動 WK-TASK-PUTFILE 到 PUTFCTL-PUTFILE
            String putfctlPutfile = "";

            // START FD-PUTFCTL KEY IS > PUTFCTL-PUTFILE
            boolean isKeyValid = startFdPutfCtl(putfctlPutfile);

            if (!isKeyValid) {
                // INVALID KEY 邏輯
                return;
            }

            // 主要循環邏輯
            while (true) {
                // READ FD-PUTFCTL NEXT
                String putname = readNextFdPutfCtl();

                if (putname == null) {
                    // AT END 邏輯
                    break;
                }

                // 處理符合條件的記錄
                if (putname.startsWith("B")) {
                    // 將相關值移動到 WK 變數中
                    moveValues(event);
                } else {
                    continue;
                }
            }
        } catch (Exception e) {
            log.error("Error in mainRoutine", e);
        }
    }

    private boolean startFdPutfCtl(String putfctlPutfile) {
        // 開始 FD-PUTFCTL 文件的邏輯
        return true;
    }

    private String readNextFdPutfCtl() {
        // 讀取 FD-PUTFCTL 文件的下一條記錄邏輯
        return null;
    }

    private void moveValues(Tapef event) {
        // 將值移動到 WK 變數中邏輯
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

    private void batchResponse(Tapef event) {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(event, "0000", "", null);
    }
}
