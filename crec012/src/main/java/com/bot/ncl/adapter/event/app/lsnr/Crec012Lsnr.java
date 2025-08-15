/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.Crec012;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.dto.entities.CltmrbyCodeBus;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.ncl.jpa.svc.CltmrService;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FileSortC012;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.buffer.mg.Bctl;
import com.bot.txcontrol.buffer.mg.ThreadVariable;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.eum.TxCharsets;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.text.format.Vo2TextFormatter;
import com.bot.txcontrol.util.transmit.FsapSyncSftpService;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("Crec012Lsnr")
@Scope("prototype")
public class Crec012Lsnr extends BatchListenerCase<Crec012> {
    // 查詢分行控制檔
    private static final String CHARSET = "UTF-8";
    private static final String CHARSET_BIG5 = "Big5";
    private Bctl bctl;
    @Autowired private ClmrService clmrService;
    @Autowired private FormatUtil formatUtil;
    @Autowired private CltmrService cltmrService;
    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FileSortC012 fileSortC012;
    @Autowired private Vo2TextFormatter vo2TextFormatter;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;

    private Crec012 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> textMap;
    private Map<String, String> labelMap;
    private String processDate; // 批次日期(民國年yyyymmdd)
    private String tbsdy;
    private String fileOutPath;
    private String ftpFileOutPath;
    private final int pageCnts = 100000;
    private int pageIndex = 0;
    private List<String> fileContents;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(Crec012 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Crec012Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Crec012 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Crec012Lsnr run()");

        init(event);

        queryCldtl();

        upload(ftpFileOutPath, "", "");
        batchResponse();
    }

    private void init(Crec012 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Crec012 init()");

        this.event = event;

        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();

        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = formatUtil.pad9(processDate, 8);
        fileContents = new ArrayList<>();
        fileOutPath =
                fileDir + CONVF_DATA + PATH_SEPARATOR + processDate + PATH_SEPARATOR + "CRE/C012";
        ftpFileOutPath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + "C012_"
                        + tbsdy.substring(1, 8)
                        + ".TXT";
        textFile.deleteFile(fileOutPath);
        textFile.deleteFile(ftpFileOutPath);
    }

    private void queryCldtl() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Crec012 queryCldtl()");

        List<ClmrBus> clmrList = clmrService.findAll(pageIndex, pageCnts);
        if (clmrList == null || clmrList.isEmpty()) {

        } else {
            // 取出所有的資料產製CRE裡面 C012會再次抓取排序
            int ipbrno = 0;
            String icode = "";
            String icname = "";
            String ientpno = "";
            int ihentpno = 0;
            int istop = 0;
            Long iactno = 0L;
            String ipbrnonm = "";
            ApLogHelper.info(
                    log, false, LogType.NORMAL.getCode(), "Crec012 clmrList()" + clmrList.size());
            ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境

            for (ClmrBus tclmr : clmrList) {

                fileSortC012 = new FileSortC012();
                ipbrno = tclmr.getPbrno();
                icode = tclmr.getCode();
                icname = tclmr.getCname();
                ientpno = "";
                ihentpno = 0;
                List<CltmrbyCodeBus> cltmrList =
                        cltmrService.findbyCode(icode, pageIndex, pageCnts);
                if (cltmrList == null || cltmrList.isEmpty()) {
                    ientpno = "";
                    ihentpno = 0;
                } else {
                    for (CltmrbyCodeBus tcltmr : cltmrList) {
                        ientpno = tcltmr.getEntpno();
                        ihentpno = tcltmr.getHentpno();
                    }
                }
                istop = tclmr.getStop();
                iactno = tclmr.getActno();
                // 983是分行代號 PBRNONM
                bctl = event.getAggregateBuffer().getMgGlobal().getBctl(ipbrno);

                if (!Objects.isNull(bctl)) {
                    ipbrnonm = bctl.getChnam();
                } else {
                    ipbrnonm = " ";
                }
                fileSortC012.setCode(icode);
                fileSortC012.setPbrno(String.valueOf(ipbrno));
                fileSortC012.setCname(icname);
                fileSortC012.setEntpno(ientpno);
                fileSortC012.setHentpno(String.valueOf(ihentpno));
                fileSortC012.setStop(String.valueOf(istop));
                fileSortC012.setDate(processDate);
                fileSortC012.setActno(String.valueOf(iactno));
                fileSortC012.setPbrnonm(ipbrnonm);
                fileSortC012.setFiller("");
                fileContents.add(vo2TextFormatter.formatRS(fileSortC012, false));
            }
            try {
                textFile.writeFileContent(fileOutPath, fileContents, CHARSET_BIG5);
            } catch (LogicException e) {
                moveErrorResponse(e);
            }
            try {
                textFile.writeFileContent(ftpFileOutPath, fileContents, CHARSET_BIG5);
            } catch (LogicException e) {
                moveErrorResponse(e);
            }
        }
    }

    private void moveErrorResponse(LogicException e) {
        //                event.setPeripheryRequest();
    }

    private void batchResponse() {
        Map<String, String> responseTextMap = new HashMap<>(textMap);
        responseTextMap.put("APKIND", "CL");
        responseTextMap.put("AP", "CL");
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", responseTextMap);
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
}
