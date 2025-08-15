/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import static com.bot.txcontrol.mapper.MapperCase.formatUtil;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.Chgf;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FilePUTFCTL;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.Text2VoFormatter;
import com.bot.txcontrol.util.transmit.FsapSyncSftpService;
import java.io.File;
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
@Component("ChgfLsnr")
@Scope("prototype")
public class ChgfLsnr extends BatchListenerCase<Chgf> {

    @Autowired private Parse parse;

    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    @Autowired private TextFileUtil textFile;

    @Autowired private FilePUTFCTL filePUTFCTL;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private String filePutfCtlPath;

    private List<String> putfCtlDataList;

    private int index;

    private static final String UTF_8 = "UTF-8";
    private static final String BIG5 = "Big5";
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;

    private int date;
    private String putfile;
    private String code1;
    private int pbrno;

    @Override
    public void onApplicationEvent(Chgf event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "ChgfLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Chgf event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "ChgfLsnr run()");
        init(event);
        mainRtn();
        batchResponse(event);
    }

    private void init(Chgf event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "init()");

        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();

        // 設定作業日、檔名日期變數值
        String processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        String tbsdy = labelMap.get("PROCESS_DATE");
        putfCtlDataList = new ArrayList<>();
        index = 0;
        String putfCtlDir = fileDir + CONVF_DATA + PATH_SEPARATOR + processDate;
        filePutfCtlPath = putfCtlDir + PATH_SEPARATOR + "PUTFCTL";
        textFile.deleteFile(filePutfCtlPath);
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
            filePutfCtlPath = getLocalPath(sourceFile);
        }
    }

    private void mainRtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "mainRtn()");
        // CHGRTN:                                                                      00003100
        // T  (TASKVALUE = 0);                                                          00003200
        // RUN  OBJ/CL/BH/CHGF(DATE  REFERENCE,PUTFILE REFERENCE,                       00003300
        //                     CODE1 REFERENCE,PBRNO   REFERENCE)[T];                   00003400
        // TODO: 待確認BATCH參數名稱
        if (putfCtlDataList.isEmpty()) {
            if (textFile.exists(filePutfCtlPath)) {
                putfCtlDataList = textFile.readFileContent(filePutfCtlPath, UTF_8);
            }
        }
        for (String putfCtlData : putfCtlDataList) {
            text2VoFormatter.format(putfCtlData, filePUTFCTL);
            date = parseInt(filePUTFCTL.getGendt(), 0);
            putfile = filePUTFCTL.getPuttype() + filePUTFCTL.getPutname();
            pbrno = parseInt(filePUTFCTL.getPbrno(), 0);
            String wkPutname = filePUTFCTL.getPutname();
            code1 = wkPutname.length() >= 6 ? wkPutname.substring(2, 7) : "";

            // NFLG     := TAKE(DROP(PUTFILE,3),1);                                         00003441
            String nflg = putfile.substring(3, 4);
            // GPUTFILE :="FCL004" & TAKE(DROP(PUTFILE,4),6);                               00003442
            String gputfile = "FCL004" + putfile.substring(4, 10);
            // IF  DECIMAL(DATE) > 991220    THEN                                           00003445
            //     DATE7:="0" & DATE;                                                       00003450
            // ELSE                                                                         00003470
            //     DATE7:="1" & DATE;                                                       00003490
            String date7;
            if (date > 991220) {
                date7 = "0" + date;
            } else {
                date7 = "1" + date;
            }
            // IF   T  IS COMPLETEDOK                                                       00003500
            //   THEN  BEGIN                                                                00003600
            //            IF   T(TASKVALUE)      =    2                                     00003700
            //             THEN  GO OKRTN;                                                  00003800
            //         END;                                                                 00003900
            // ELSE  GO FAILURE;                                                            00004000
            // IF CODE1 ="14595"      THEN  GO  CHGRTN;                                     00004050
            if (code1.equals("14595")) {
                continue; // 讀下一筆
            }
            // IF CODE1 ="42020"      THEN  GO  CHGRTN;                                     00004060
            if (code1.equals("42020")) {
                continue; // 讀下一筆
            }
            // IF NFLG = "3" OR NFLG = "4"  THEN                                            00004070
            // BEGIN                                                                        00004080
            // COPY    (#USRCODE)DATA/CL/BH/PUTFN/#DATE/#PUTFILE                            00004320
            //     AS  (#USRCODE)DATA/GN/DWL/CL004/#PBRNO/#DATE7/#GPUTFILE                  00004340
            //      FROM #MYPACK(PACK) TO #MYPACK(PACK)[T1];                                00004360
            // END;                                                                         00004362
            // ELSE                                                                         00004364
            // BEGIN                                                                        00004366
            // COPY    (#USRCODE)DATA/CL/BH/PUTF/#DATE/#PUTFILE                             00004368
            //     AS  (#USRCODE)DATA/GN/DWL/CL004/#PBRNO/#DATE7/#GPUTFILE                  00004370
            //      FROM #MYPACK(PACK) TO #MYPACK(PACK)[T1];                                00004372
            // END;                                                                         00004380
            String sourceFilePath = fileDir;
            if (nflg.equals("3") || nflg.equals("4")) {
                sourceFilePath += "PUTFN";
            } else {
                sourceFilePath += "PUTF";
            }
            sourceFilePath += PATH_SEPARATOR + date + PATH_SEPARATOR + putfile;
            String targetFilePath =
                    fileDir
                            + "GN"
                            + PATH_SEPARATOR
                            + "DWL"
                            + PATH_SEPARATOR
                            + "CL004"
                            + PATH_SEPARATOR
                            + pbrno
                            + PATH_SEPARATOR
                            + date7
                            + PATH_SEPARATOR
                            + gputfile;
            copy(sourceFilePath, targetFilePath);
            // IF   T1 IS COMPLETEDOK THEN  GO  CHGRTN                                      00004400
        }
    }

    private void copy(String sourceFilePath, String targetFilePath) {
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "copy({},{})",
                sourceFilePath,
                targetFilePath);
        // TODO: COPY to FSAP
    }

    private int parseInt(String s, int i) {

        return parse.isNumeric(s) ? parse.string2Integer(s) : i;
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

    private void batchResponse(Chgf event) {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(event, "0000", "", null);
    }
}
