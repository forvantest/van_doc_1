/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.Crec0121;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.dto.entities.CltmrbyCodeBus;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.ncl.jpa.svc.CltmrService;
import com.bot.ncl.util.fileVo.FileSortC012;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.buffer.mg.Bctl;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
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
@Component("Crec0121Lsnr")
@Scope("prototype")
public class Crec0121Lsnr extends BatchListenerCase<Crec0121> {

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    // 查詢分行控制檔
    private static final String CHARSET = "UTF-8";
    private Bctl bctl;
    @Autowired private ClmrService clmrService;
    @Autowired private FormatUtil formatUtil;
    @Autowired private CltmrService cltmrService;
    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FileSortC012 fileSortC012;
    @Autowired private Vo2TextFormatter vo2TextFormatter;
    private Crec0121 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;

    private Map<String, String> labelMap;
    private String processDate; // 作業日期(民國年yyyymmdd)
    private int processDateInt = 0;
    private String fileOutPath;
    private final int pageCnts = 100000;
    private int pageIndex = 0;
    private List<String> fileContents;

    @Override
    public void onApplicationEvent(Crec0121 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Crec0121Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Crec0121 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Crec0121Lsnr run()");

        init(event);

        queryCldtl();
    }

    private void init(Crec0121 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Crec012 init()");

        this.event = event;

        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();

        processDate = labelMap.get("PROCESS_DATE"); // 待中菲APPLE提供正確名稱
        processDateInt = parse.string2Integer(processDate);

        fileContents = new ArrayList<>();
        fileOutPath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDateInt
                        + PATH_SEPARATOR
                        + "CRE/C012-1";
        textFile.deleteFile(fileOutPath);
    }

    private void queryCldtl() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Crec0121 queryCldtl()");

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
            String ipbrnom = "";
            String flag = "";
            String flagl = "";

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
                        flagl = tcltmr.getPwtype();
                        if (flagl != null && !flag.isEmpty() && flagl.length() != 0) {
                            ApLogHelper.info(
                                    log, false, LogType.NORMAL.getCode(), "Crec0121 flag()" + flag);

                            flag = flagl.trim();
                        } else {
                            flag = "";
                        }
                    }
                }
                istop = tclmr.getStop();
                iactno = tclmr.getActno();
                // 983是分行代號 PBRNONM
                bctl = event.getAggregateBuffer().getMgGlobal().getBctl(ipbrno);
                if (!Objects.isNull(bctl)) {
                    ipbrnom = bctl.getBrno();
                } else {
                    ipbrnom = " ";
                }
                if (flag != null && !flag.isEmpty() && flag.length() != 0) {
                    fileSortC012.setCode(icode);
                    fileSortC012.setPbrno(String.valueOf(ipbrno));
                    fileSortC012.setCname(icname);
                    fileSortC012.setEntpno(ientpno);
                    fileSortC012.setHentpno(String.valueOf(ihentpno));
                    fileSortC012.setStop(String.valueOf(istop));
                    fileSortC012.setDate(processDate);
                    fileSortC012.setActno(String.valueOf(iactno));
                    fileSortC012.setPbrnonm(ipbrnom);
                    fileSortC012.setFiller("");
                    fileContents.add(vo2TextFormatter.formatRS(fileSortC012, false));
                }
            }
            try {
                textFile.writeFileContent(fileOutPath, fileContents, CHARSET);
            } catch (LogicException e) {
                moveErrorResponse(e);
            }
        }
    }

    private void moveErrorResponse(LogicException e) {
        //                event.setPeripheryRequest();
    }
}
