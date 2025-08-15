/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.ExKputh1;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FileKPUTH;
import com.bot.ncl.util.fileVo.FilePUTH;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.text.format.Text2VoFormatter;
import com.bot.txcontrol.util.text.format.Vo2TextFormatter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("ExKputh1Lsnr")
@Scope("prototype")
public class ExKputh1Lsnr extends BatchListenerCase<ExKputh1> {

    private ExKputh1 event;

    @Autowired private TextFileUtil textFile;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private Text2VoFormatter text2VoFormatter;

    @Autowired private Vo2TextFormatter vo2TextFormatter;

    @Autowired private FormatUtil formatUtil;

    @Autowired private FileKPUTH fileKputh;

    @Autowired private FilePUTH filePuth;

    @Autowired private Parse parse;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String PUTH_FILE_NAME = "PUTH";
    private static final String CHARSET = "UTF-8";
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;

    private String puthFilePath;

    // WORKING-STORAGE  SECTION.
    // 01 WK-YYMMDD                          PIC 9(06).
    private int wkYymmdd;
    // 01 WK-PBRNO                           PIC 9(03).
    // 01 WK-PUTDIR.
    //    03 FILLER                      PIC X(22)
    //                             VALUE "DATA/GN/DWL/CL012/003/".
    //    03 WK-FDATE                    PIC 9(07).
    //    03 FILLER                      PIC X(08) VALUE "/KPUTH2.".
    private String wkPutdir;

    @Value("${fsapFile.gn.dwl.directory}")
    private String fsapFileGnDwlDirectory;

    private int wkFdate;
    private final String sourceFileName = "/KPUTH2";

    @Override
    public void onApplicationEvent(ExKputh1 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "in ExKputh1Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(ExKputh1 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "ExKputh1Lsnr run()");
        this.event = event;
        /* SYM/CL/BH/OUTING/KPUTH1 */

        // 設定FD-KPUTH檔名
        boolean isError = settingWkPutdir();

        if (!isError) {
            // 0000-MAIN-RTN
            mainRtn();
        }

        // 0000-END-RTN
        endRtn();

        //        batchResponse();
    }

    // 0000-MAIN-RTN
    private void mainRtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "mainRtn()");
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();

        // 設定作業日、檔名日期變數值
        String processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱

        //     OPEN    INPUT   FD-KPUTH.
        List<String> fdKputhContents = textFile.readFileContent(wkPutdir, CHARSET);

        //     OPEN    EXTEND  FD-PUTH.
        puthFilePath =
                this.fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + PUTH_FILE_NAME;
        List<String> fdPuthContents = new ArrayList<>();

        // 0000-MAIN-LOOP.
        //     READ    FD-KPUTH AT END GO TO 0000-MAIN-EXIT.
        // *
        //     MOVE    KPUTH-PUTFILE    TO   PUTH-PUTFILE  .
        //     MOVE    KPUTH-CODE       TO   PUTH-CODE     .
        //     MOVE    KPUTH-RCPTID     TO   PUTH-RCPTID   .
        //     MOVE    KPUTH-DATE       TO   PUTH-DATE     .
        //     MOVE    KPUTH-TIME       TO   PUTH-TIME     .
        //     MOVE    KPUTH-CLLBR      TO   PUTH-CLLBR    .
        //     MOVE    KPUTH-LMTDATE    TO   PUTH-LMTDATE  .
        //     MOVE    KPUTH-AMT        TO   PUTH-AMT      .
        //     MOVE    KPUTH-USERDATA   TO   PUTH-USERDATA .
        //     MOVE    KPUTH-SITDATE    TO   PUTH-SITDATE  .
        //     MOVE    KPUTH-TXTYPE     TO   PUTH-TXTYPE   .
        //     MOVE    KPUTH-SERINO     TO   PUTH-SERINO   .
        //     WRITE   PUTH-REC.
        //     GO TO   0000-MAIN-LOOP.
        for (String fdKputhContent : fdKputhContents) {
            ApLogHelper.info(
                    log, false, LogType.NORMAL.getCode(), "fdKputhContent = {}", fdKputhContent);
            text2VoFormatter.format(fdKputhContent, fileKputh);
            filePuth = new FilePUTH();
            filePuth.setPuttype(fileKputh.getPuttype());
            filePuth.setPutname(fileKputh.getPutname());
            filePuth.setCode(fileKputh.getCode());
            filePuth.setRcptid(fileKputh.getRcptid());
            filePuth.setEntdy(fileKputh.getEntdy());
            filePuth.setTime(fileKputh.getTime());
            filePuth.setCllbr(fileKputh.getCllbr());
            filePuth.setLmtdate(fileKputh.getLmtdate());
            filePuth.setAmt(fileKputh.getAmt());
            filePuth.setUserdata(fileKputh.getUserdata());
            filePuth.setSitdate(fileKputh.getSitdate());
            filePuth.setTxtype(fileKputh.getTxtype());
            filePuth.setSerino(fileKputh.getSerino());
            String fdPuthContent = vo2TextFormatter.formatRS(filePuth, false);
            ApLogHelper.info(
                    log, false, LogType.NORMAL.getCode(), "fdPuthContent = {}", fdPuthContent);
            fdPuthContents.add(fdPuthContent);
        }
        textFile.writeFileContent(puthFilePath, fdPuthContents, CHARSET);
    }

    // 0000-END-RTN
    private void endRtn() {
        // 關批次日期檔，結束程式
        //     DISPLAY "SYM/CL/BH/OUTING/KPUTH1 OK".
        //     CLOSE   FD-BHDATE .
        //     STOP RUN.
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "ExKputh1Lsnr ok.");
    }

    private boolean settingWkPutdir() {
        // 開啟批次日期檔
        //     OPEN INPUT   FD-BHDATE.
        // DISPLAY訊息，包含在系統訊息中
        //     CHANGE ATTRIBUTE DISPLAYONLYTOMCS OF MYSELF TO TRUE.
        // 讀批次日期檔，設定本營業日變數值；若讀不到，顯示訊息，結束程式
        // 搬FD-BHDATE-TBSDY 9(08) 給 WK-YYMMDD 9(06)
        //     READ    FD-BHDATE AT END DISPLAY "READ FD-BHDATE ERROR"
        //        STOP RUN.
        //     MOVE    FD-BHDATE-TBSDY TO     WK-YYMMDD.
        int tbsdy = event.getAggregateBuffer().getTxCom().getTbsdy();
        wkYymmdd = tbsdy % 1000000;
        // 設定FD-KPUTH檔名
        //  若WK-YYMMDD<500101(for 民國百年後)，則加1000000放到WK-FDATE；
        //  否則直接搬WK-YYMMDD給WK-FDATE
        //     IF      WK-YYMMDD       <       500101
        //       COMPUTE  WK-FDATE = 1000000 + WK-YYMMDD
        //     ELSE
        //       MOVE  WK-YYMMDD       TO     WK-FDATE.
        // *
        if (wkYymmdd < 500101) {
            this.wkFdate = 1000000 + wkYymmdd;
        } else {
            this.wkFdate = wkYymmdd;
        }
        // WK-PUTDIR = "DATA/GN/DWL/CL012/003/"+WK-FDATE+"/KPUTH2."
        //     CHANGE  ATTRIBUTE FILENAME  OF FD-KPUTH TO WK-PUTDIR .
        // 若FD-KPUTH不存在，跳至0000-END-RTN
        //     IF ATTRIBUTE RESIDENT OF FD-KPUTH IS NOT = VALUE(TRUE)
        //       GO TO 0000-END-RTN.
        // *
        // *    DISPLAY "WK-PUTDIR="x WK-PUTDIR.W
        wkPutdir = fsapFileGnDwlDirectory + wkFdate + sourceFileName;
        Path wkPutdirPath = Paths.get(wkPutdir);
        if (!Files.exists(wkPutdirPath)) {
            // error msg
            ApLogHelper.error(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "file not exists. wkPutdir = {}",
                    wkPutdir);
            return true;
        }
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkPutdir = {}", wkPutdir);

        return false;
    }

    private void batchResponse() {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", null);
    }
}
