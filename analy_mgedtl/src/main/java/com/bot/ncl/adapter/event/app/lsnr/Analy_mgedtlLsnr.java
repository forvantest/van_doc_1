/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.Charsets;
import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.Analy_mgedtl;
import com.bot.ncl.dto.entities.CltmrBus;
import com.bot.ncl.jpa.svc.CltmrService;
import com.bot.ncl.util.fileVo.FileKPUTH1;
import com.bot.ncl.util.fileVo.FileKPUTH2;
import com.bot.ncl.util.fileVo.FileUPLDIR;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.buffer.mg.ThreadVariable;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.eum.TxCharsets;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.text.format.Text2VoFormatter;
import com.bot.txcontrol.util.text.format.Vo2TextFormatter;
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
@Component("Analy_mgedtlLsnr")
@Scope("prototype")
public class Analy_mgedtlLsnr extends BatchListenerCase<Analy_mgedtl> {

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET = "UTF-8";

    private static final String PATH_SEPARATOR = File.separator;
    private static final String CONVF_DATA = "DATA";

    @Autowired private FormatUtil formatUtil;

    @Autowired private TextFileUtil textFileUtil;

    @Autowired private Text2VoFormatter text2VoFormatter;

    @Autowired private Vo2TextFormatter vo2TextFormatter;

    @Autowired private CltmrService cltmrService;

    private boolean wkNotFound = false;

    List<FileUPLDIR> upldirList;

    private String wkUdate;
    private String processDate;
    private String wkFdate;

    // @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(Analy_mgedtl event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Analy_mgedtlLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Analy_mgedtl event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Analy_mgedtlLsnr run()");
        mainRoutine(event);
    }

    private void mainRoutine(Analy_mgedtl event) {
        init(event);
        readUPLDIR();

        if (upldirList == null || upldirList.isEmpty()) {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "***EMPTY FD-UPLDIR FILE***");
            return;
        }
        for (FileUPLDIR upldir : upldirList) {
            if (isNullOrEmpty(upldir.getPutdir())) {
                continue;
            }
            mergeFiles(upldir);
        }
    }

    private void init(Analy_mgedtl event) {
        Map<String, String> headersMap = event.getPeripheryRequest().getHeadersMap();

        ThreadVariable.setObject(TxCharsets.CHARSETS.getCode(), Charsets.BUR); // 設定解析環境
        wkUdate = headersMap.get("YYYMM"); // TODO: 待確認BATCH參數名稱
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();

        // 設定作業日、檔名日期變數值
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱

        wkFdate = formatUtil.pad9(processDate, 7).substring(1, 7);
    }

    private void readUPLDIR() {
        // FD  FD-UPLDIR           COPY "SYM/CL/BH/FD/UPLDIR.".
        // ref 1:
        // 006500     CHANGE  ATTRIBUTE FILENAME  OF FD-UPLDIR TO WK-UPLDIR.
        // ref 2:
        // 005200  01 WK-UPLDIR.
        // 005300     03 WK-UAPNO                    PIC X(17)
        // 005400                              VALUE "DATA/CL/BH/ANALY/".
        // 005500     03 WK-UDATE                    PIC 9(05).
        // 005600     03 FILLER                      PIC X(08)
        // 005700                              VALUE "/UPLRPT.".
        String upldirFilePath =
                fileDir + "ANALY" + PATH_SEPARATOR + wkUdate + PATH_SEPARATOR + "UPLRPT";
        upldirList = new ArrayList<>();
        List<String> dataList = textFileUtil.readFileContent(upldirFilePath, CHARSET);
        if (Objects.isNull(dataList) || dataList.isEmpty()) {
            logError("UPLDIR", "NO DATA");
            return;
        }
        for (String data : dataList) {
            FileUPLDIR upldir = new FileUPLDIR();
            text2VoFormatter.format(data, upldir);
            upldirList.add(upldir);
        }
    }

    private boolean isNullOrEmpty(String fileName) {
        // 不可為null或空白
        return Objects.isNull(fileName) || fileName.trim().isEmpty();
    }

    private void mergeFiles(FileUPLDIR upldir) {
        List<FileKPUTH1> kputh1List = readKPUTH1(upldir.getPutdir());
        if (kputh1List.isEmpty()) {
            return;
        }
        for (FileKPUTH1 kputh1 : kputh1List) {
            FileKPUTH2 kputh2 = new FileKPUTH2();
            mergeRecords(kputh1, kputh2);
            String entpno = findClmrEntpno(kputh1.getCode());
            entpno = formatUtil.padX(entpno, 10);
            kputh2.setPuttype(entpno.substring(0, 2));
            kputh2.setPutname(entpno.substring(2));
            writeKPUTH2(kputh2);
        }
    }

    private List<FileKPUTH1> readKPUTH1(String dirPutdir) {
        // ref 1:
        // CHANGE  ATTRIBUTE FILENAME OF FD-KPUTH1 TO WK-KPUTH1DIR.
        // ref 2:
        // 01 WK-KPUTH1DIR.
        //    03 WK-K1APNO                   PIC X(35).
        //    03 FILLER                      PIC X(01)
        //                             VALUE ".".
        // ref 3:
        // MOVE    DIR-PUTDIR(1:35)      TO      WK-K1APNO.
        String kputh1FilePath = dirPutdir.substring(0, 35);
        List<FileKPUTH1> kputh1List = new ArrayList<>();
        List<String> dataList = textFileUtil.readFileContent(kputh1FilePath, CHARSET);
        if (Objects.isNull(dataList) || dataList.isEmpty()) {
            logError("KPUTH1", "NO DATA");
            return kputh1List;
        }
        for (String data : dataList) {
            FileKPUTH1 kputh1 = new FileKPUTH1();
            text2VoFormatter.format(data, kputh1);
            kputh1List.add(kputh1);
        }
        return kputh1List;
    }

    private void mergeRecords(FileKPUTH1 kputh1, FileKPUTH2 kputh2) {
        kputh2.setPuttype(kputh1.getPuttype());
        kputh2.setPutname(kputh1.getPutname());
        kputh2.setCode(kputh1.getCode());
        kputh2.setRcptid(kputh1.getRcptid());
        kputh2.setDate(kputh1.getDate());
        kputh2.setTime(kputh1.getTime());
        kputh2.setCllbr(kputh1.getCllbr());
        kputh2.setLmtdate(kputh1.getLmtdate());
        kputh2.setAmt(kputh1.getAmt());
        kputh2.setUserdata(kputh1.getUserdata());
        kputh2.setSitdate(kputh1.getSitdate());
        kputh2.setTxtype(kputh1.getTxtype());
        kputh2.setSerino(kputh1.getSerino());
        kputh2.setPbrno(kputh1.getPbrno());
        kputh2.setUpldate(kputh1.getUpldate());
        kputh2.setFeetype(kputh1.getFeetype());
        kputh2.setFeeo2l(kputh1.getFeeo2l());
        kputh2.setFileer(kputh1.getFileer());
        kputh2.setFiller(kputh1.getFiller());
    }

    private void writeKPUTH2(FileKPUTH2 kputh2) {
        // ref 1:
        // CHANGE  ATTRIBUTE FILENAME  OF FD-KPUTH2 TO WK-KPUTH2DIR.
        // ref 2:
        // 01 WK-KPUTH2DIR.                                                00/09/02
        //    03 WK-K2APNO                   PIC X(23)                     00/09/02
        //                             VALUE "DATA/CL/BH/ANALY/KPUTH.".    00/09/02
        String kputh2FilePath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + wkFdate
                        + PATH_SEPARATOR
                        + "ANALY"
                        + PATH_SEPARATOR
                        + "KPUTH";
        List<String> dataList = new ArrayList<>();
        dataList.add(vo2TextFormatter.formatRS(kputh2, false));
        textFileUtil.writeFileContent(kputh2FilePath, dataList, CHARSET);
    }

    private String findClmrEntpno(String code) {
        wkNotFound = false;
        CltmrBus cltmr = null;
        try {
            cltmr = cltmrService.findById(code);
        } catch (Exception e) {
            if (e.getMessage().contains("NOTFOUND")) {
                wkNotFound = true;
            } else {
                logError("LOOKUP", e.getMessage());
            }
        }
        if (!Objects.isNull(cltmr)) {
            if (!Objects.isNull(cltmr.getEntpno()) && !cltmr.getEntpno().isEmpty()) {
                return cltmr.getEntpno();
            } else {
                return String.valueOf(cltmr.getHentpno());
            }
        }
        return "";
    }

    private void logError(String sub, String message) {
        ApLogHelper.error(
                log,
                LogType.NORMAL.getCode(),
                String.format("Error in %s-%s: %s", sub, "Analy_mgedtlLsnr", message));
    }
}
