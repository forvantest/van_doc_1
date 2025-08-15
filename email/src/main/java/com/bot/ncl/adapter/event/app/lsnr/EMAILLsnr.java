/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.EMAIL;
import com.bot.ncl.util.fileVo.FilePUTFCTL;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.util.date.DateUtil;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.text.format.Text2VoFormatter;
import java.io.File;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("EMAILLsnr")
@Scope("prototype")
public class EMAILLsnr extends BatchListenerCase<EMAIL> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private DateUtil dateUtil;
    private EMAIL event;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FilePUTFCTL filePutfctl;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET_UTF8 = "UTF-8";
    private String PATH_SEPARATOR = File.separator;
    private String fdPutfctl;
    private Map<String, String> labelMap;
    private Map<String, String> textMap;
    private int T = 0;
    private String wkTaskDate;
    private String wkTaskPutfile;
    private int puftctlPutfile;
    private String putfctl_Putname1;
    private int putfctlKey;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(EMAIL event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "EMAILLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(EMAIL event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "EMAILLsnr run()");
        init(event);
        _0000_main_rtn();
    }

    private void init(EMAIL event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "EMAILLsnr init ");
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        wkTaskDate = formatUtil.pad9(textMap.get("WK_TASK_DATE"), 6); // 待中菲APPLE提供正確名稱
        wkTaskPutfile = textMap.get("WK_TASK_PUTFILE"); // 待中菲APPLE提供正確名稱
        // 002400 FD  FD-PUTFCTL
        // 002500     COPY "SYM/CL/BH/FD/PUTFCTL.".
        fdPutfctl = fileDir + "PUTFCTL";
    }

    private void _0000_main_rtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "EMAILLsnr _0000_main_rtn");
        // 004200 0000-MAIN-RTN.
        //// 搬BY REFERENCE傳遞之參數WK-TASK-PUTFILE到PUTFCTL-PUTFILE

        // 004300     MOVE    WK-TASK-PUTFILE         TO     PUTFCTL-PUTFILE.
        //// 將FD-PUTFCTL指標移至大於PUTFCTL-PUTFILE處
        //// 若有誤，TASKVALUE回傳值設為2，結束0000-MAIN-RTN
        // 004400     START   FD-PUTFCTL       KEY IS >  PUTFCTL-PUTFILE
        // 004500     INVALID KEY
        // 004600     CHANGE ATTRIBUTE TASKVALUE OF MYSELF TO 2
        if (!parse.isNumeric(wkTaskPutfile)) {
            T = 2;
            return;
        }
        puftctlPutfile = parse.string2Integer(wkTaskPutfile);

        // 004700       GO TO     0000-MAIN-EXIT.
        // 004800  0000-MAIN-LOOP.
        //// 讀下一筆FD-PUTFCTL
        //// 檔尾，TASKVALUE回傳值設為2，結束0000-MAIN-RTN

        // 004900     READ        FD-PUTFCTL  NEXT    AT  END
        // 005000     CHANGE ATTRIBUTE TASKVALUE OF MYSELF TO 2
        // 005100       GO TO     0000-MAIN-EXIT.

        List<String> lines = textFile.readFileContent(fdPutfctl, CHARSET_UTF8);
        for (String detail : lines) {
            text2VoFormatter.format(detail, filePutfctl);
            putfctlKey = parse.string2Integer(filePutfctl.getPuttype() + filePutfctl.getPutname());
            putfctl_Putname1 = filePutfctl.getPutname().substring(0, 1);
            if (putfctlKey > puftctlPutfile) {}

            //// 挑PUTFCTL-PUTNAME1="Z" OR = "T"
            ////  A.搬PUTFCTL-GENDT、PUTFCTL-PUTFILE至WK-TASK-...
            ////  B.結束0000-MAIN-RTN

            // 005200     IF          PUTFCTL-PUTNAME1    =      "Z" OR = "T"
            // 005300       MOVE      PUTFCTL-GENDT       TO     WK-TASK-DATE
            // 005400       MOVE      PUTFCTL-PUTFILE     TO     WK-TASK-PUTFILE
            // 005500       GO TO     0000-MAIN-EXIT
        }
        // 005600     ELSE
        //// LOOP 讀下一筆FD-PUTFCTL
        // 005700       GO TO     0000-MAIN-LOOP.
        T = 2;
        // 005800 0000-MAIN-EXIT.
    }
}
