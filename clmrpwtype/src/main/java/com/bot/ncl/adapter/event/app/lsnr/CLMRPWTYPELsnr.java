/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.ncl.adapter.event.app.evt.CLMRPWTYPE;
import com.bot.ncl.dto.entities.CltmrBus;
import com.bot.ncl.jpa.svc.CltmrService;
import com.bot.ncl.util.fileVo.FileC012;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.util.text.format.Text2VoFormatter;
import com.bot.txcontrol.util.text.format.Vo2TextFormatter;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("CLMRPWTYPELsnr")
@Scope("prototype")
public class CLMRPWTYPELsnr extends BatchListenerCase<CLMRPWTYPE> {

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET = "UTF-8";

    private static final String PATH_SEPARATOR = File.separator;

    @Autowired private TextFileUtil textFileUtil;

    @Autowired private Text2VoFormatter text2VoFormatter;

    @Autowired private Vo2TextFormatter vo2TextFormatter;

    @Autowired private CltmrService cltmrService;

    private int wkCnt = 0;
    private boolean wkNotFound = false;

    List<FileC012> c012List;

    // @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(CLMRPWTYPE event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CLMRPWTYPELsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CLMRPWTYPE event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CLMRPWTYPELsnr run()");
        mainRoutine(event);
    }

    private void mainRoutine(CLMRPWTYPE event) {
        readC012();
        if (c012List == null || c012List.isEmpty()) {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "***EMPTY FD-C012 FILE***");
            return;
        }

        for (FileC012 c012 : c012List) {
            processRecord(c012);
        }

        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "MNT/CLMRPWTYPE UPDATE CNT=" + wkCnt);
    }

    private void readC012() {
        // FD  FD-C012           COPY "SYM/CL/BH/FD/C012.".
        // "DATA/CL/CRE/C012"
        String c012FilePath = fileDir + "CRE" + PATH_SEPARATOR + "C012";
        c012List = new ArrayList<>();
        List<String> dataList = textFileUtil.readFileContent(c012FilePath, CHARSET);
        if (Objects.isNull(dataList) || dataList.isEmpty()) {
            logError("C012", "NO DATA");
            return;
        }
        for (String data : dataList) {
            FileC012 c012 = new FileC012();
            text2VoFormatter.format(data, c012);
            c012List.add(c012);
        }
    }

    private void processRecord(FileC012 fdC012) {
        CltmrBus cltmr = findDbCltmrByCode(fdC012.getCode());
        if (wkNotFound) {
            return;
        }

        cltmr.setPwtype(""); // 清空「異動日」欄位
        try {
            cltmrService.update(cltmr);
        } catch (Exception e) {
            if (e.getMessage().contains("NOTFOUND")) {
                wkNotFound = true;
            } else {
                logError("UPDCLMR", e.getMessage());
            }
        }
        wkCnt++;
    }

    private CltmrBus findDbCltmrByCode(String code) {
        wkNotFound = false;
        CltmrBus cltmr = null;
        try {
            cltmr = cltmrService.holdById(code);
        } catch (Exception e) {
            if (e.getMessage().contains("NOTFOUND")) {
                wkNotFound = true;
            } else {
                logError("LKCLMR", e.getMessage());
            }
        }
        return cltmr;
    }

    private void logError(String sub, String message) {
        ApLogHelper.error(
                log,
                LogType.NORMAL.getCode(),
                String.format("Error in %s-%s: %s", sub, "MNTPWTYPE", message));
    }
}
