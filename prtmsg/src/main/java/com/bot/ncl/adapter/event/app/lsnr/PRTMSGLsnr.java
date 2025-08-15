/* (C) 2025 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.PRTMSG;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.ncl.util.string.StringUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.transmit.FsapSyncSftpService;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("PRTMSGLsnr")
@Scope("prototype")
public class PRTMSGLsnr extends BatchListenerCase<PRTMSG> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    @Autowired private StringUtil strutil;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private String localFilePath;
    private PRTMSG event;
    private Map<String, String> labelMap;
    private Map<String, String> textMap;
    private String wkRptNoX;
    private String[] rptNoList;
    private String rptNo5;
    private String rptName = "";
    private String rptNo = "";
    private String rptFlg;
    private String tbsdy;
    private String tbsdy7;
    private String rptPath;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(PRTMSG event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "PRTMSGLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(PRTMSG event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "PRTMSGLsnr run()");

        init(event);
        for (int i = 0; i < rptNoList.length; i++) {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "PRTMSGLsnr i = {}", i);
            if (rptNoList[i].isEmpty()) {
                ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "RPTNAME參數設定為空 ");
                continue;
            }
            rptNo = rptNoList[i].trim();
            rptName = rptNo;
            rptNo5 = strutil.substr(rptName, 0, 6);
            String localDir = fileDir + "RPT" + File.separator + tbsdy7;
            localFilePath = localDir + File.separator + rptNo;
            // 刪除本地報表檔案從FSAP下載最新報表檔案
            textFile.deleteFile(localFilePath);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "filePath={}", localFilePath);

            String sourceFtpPath =
                    "NCL"
                            + File.separator
                            + tbsdy
                            + File.separator
                            + "2FSAP"
                            + File.separator
                            + "RPT"
                            + File.separator
                            + rptNo; // 來源檔在FTP的位置
            File sourceFile = downloadFromSftp(sourceFtpPath, localDir);
            if (sourceFile != null) {
                localFilePath = getLocalPath(sourceFile);
            }
            if ("CL-BH-".equals(rptNo5)) {
                rptName = rptName.substring(6);
            }
            if (("C079".equals(rptName) || "C080".equals(rptName)) && !"1".equals(rptFlg)) {
                continue;
            }
            //    START WFL/BS/BH/PRTMSG("BD/CL/BH/009/30200","0201","N","1","0");
            //                  00026700
            prtmsg_main();
        }

        batchResponse();
    }

    private void init(PRTMSG event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "PRTMSGLsnr init");

        this.event = event;
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        tbsdy = labelMap.get("PROCESS_DATE");
        tbsdy7 = formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1);
        rptFlg = textMap.get("RPTFLG"); // TODO: 待確認BATCH參數名稱
        wkRptNoX = textMap.get("RPTNAME"); // TODO: 待確認BATCH參數名稱
        rptNoList = wkRptNoX.split(",");
    }

    private void prtmsg_main() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "PRTMSGLsnr prtmsg_main");
        // 檢查本地檔案是否存在 存在則處理if
        if (textFile.exists(localFilePath)) {
            // 新系統為分散式架構,須將本地產製的檔案上傳到FSAP共用空間
            upload(localFilePath, "", "");
            forFsap(rptNo, "NCL_TO_ONDEMAND");
        }
    }

    private void upload(String filePath, String directory1, String directory2) {
        try {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "upload = {}", filePath);
            Path path = Paths.get(filePath);
            File file = path.toFile();
            String uploadPath = File.separator + tbsdy + File.separator + "2FSAP";
            if (!directory1.isEmpty()) {
                uploadPath += File.separator + directory1;
            }
            if (!directory2.isEmpty()) {
                uploadPath += File.separator + directory2;
            }
            fsapSyncSftpService.uploadFile(file, uploadPath);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
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

    private void forFsap(String filename, String bizId) {

        // 目的檔檔名加工:"0201-TBD-"+filename+"@1"
        String tarFileName = "0201-TBD-" + filename + "@1";
        Map<String, String> result =
                fsapBatchUtil.processFile(
                        "003001", // 檔案批號(6碼長) ex.000001
                        "CL", // 業務大項,系統別(5碼長) ex.NCL
                        "CL", // 業務細項(10碼長) ex.PUTF
                        filename, // 來源檔案名稱(20碼長)
                        tarFileName, // 目的檔案名稱(20碼長)
                        "2", // 同步/非同步記號(1碼長) 1：同步 2：非同步
                        "SRC", // 檔案類型(3碼長) 若未填寫預設為SRC(來源檔),若為處理完成的回覆結果檔請寫入”RTN”
                        "", // 對方FTP連線帳號
                        "", // 對方FTP連線密碼
                        bizId, // 檔案設定代號 ex:CONVF001
                        "CL", // (產品代碼,系統別)，ex.”NCL”
                        "", // NTFCLSID (分類代碼,notify版型), ex.”01”
                        "", // DLVCHNL (訊息發送類型)EMAIL:”T”,簡訊:”S”
                        "", // CNTADDR 收件者，多筆以半形分號(;)區隔
                        "", // NOTIFYTITLE (訊息標題)email主旨、若為簡訊此欄留空
                        "", // CONTENT (通知本文)email內文、簡訊內容
                        "NCL_CONVFILE"); // WFL
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RESULT = " + result);
    }

    private void moveErrorResponse(LogicException e) {
        // event.setPeripheryRequest();
    }

    private void batchResponse() {
        Map<String, String> responseTextMap = new HashMap<>(textMap);
        responseTextMap.put("RPTNAME", "");
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", responseTextMap);
    }
}
