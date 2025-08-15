/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.fsap.model.grpc.common.periphery.PeripheryRequest;
import com.bot.ncl.adapter.event.app.evt.Htaxexdtl;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.txcontrol.adapter.RequestCase;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.adapter.in.api.RequestLabelOnly;
import com.bot.txcontrol.buffer.mg.ThreadVariable;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.dto.entities.TxrecmainBus;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.eum.TxCharsets;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.jpa.svc.TxrecmainService;
import com.bot.txcontrol.util.date.DateDto;
import com.bot.txcontrol.util.date.DateUtil;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.text.format.ProtobufUtils;
import com.bot.txcontrol.util.text.format.Text2VoFormatter;
import java.io.File;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("HtaxexdtlLsnr")
@Scope("prototype")
public class HtaxexdtlLsnr extends BatchListenerCase<Htaxexdtl> {
    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private DateUtil dateUtil;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private ProtobufUtils protobufUtils;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private TxrecmainService txrecmainService;

    private Htaxexdtl event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CONVF_PATH_EXDTL = "EXDTL";
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;
    private String writeFdExdtlPath;
    private String processDate;
    private String txday;
    private String kinbr;
    private String trmseq;
    private String txtno;
    private String trmtyp;
    private String tlrno;
    private String supno;
    private String aptype;
    private String txno;
    private String stxno;
    private String dscpt;
    private String txtype;
    private String crdb;
    private String spcd;
    private String txamt;
    private String caldy;
    private String caltm;
    private String txtime;
    private int processDateInt = 0;
    private DateDto dateDto = new DateDto();
    private List<String> fileFdExdtlContents = new ArrayList<>();
    private List<TxrecmainBus> txrecmainBusList = new ArrayList<>();

    @Override
    public void onApplicationEvent(Htaxexdtl event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "HtaxexdtlLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Htaxexdtl event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "HtaxexdtlLsnr run()");

        init(event);

        queryTxrecmain();

        writeFile();

        batchResponse();
    }

    private void init(Htaxexdtl event) {
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        processDateInt = parse.string2Integer(processDate);
        dateDto.init();
        dateDto.setDateS(processDate);
        dateUtil.getCalenderDay(dateDto);

        writeFdExdtlPath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + CONVF_PATH_EXDTL;
    }

    private void queryTxrecmain() {
        Set<String> wkTxcode = Set.of("G6120", "G6121", "G6122");
        String outputWarning = "REQUEST輸出 : ";
        txrecmainBusList = txrecmainService.findAll(0, Integer.MAX_VALUE);
        validateTxrecmain();
        for (TxrecmainBus txrecmainBus : txrecmainBusList) {
            PeripheryRequest peripheryRequest =
                    this.protobufUtils.json2ProtoRequest(txrecmainBus.getCin());
            RequestCase request = new RequestLabelOnly();
            ThreadVariable.setObject(
                    TxCharsets.CHARSETS.getCode(), peripheryRequest.getPayload().getCharsets());
            text2VoFormatter.format(peripheryRequest.getPayload().getData().toByteArray(), request);
            ApLogHelper.info(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    outputWarning + request.getLabel().getCaldy()); // 西元年
            ApLogHelper.info(
                    log, false, LogType.NORMAL.getCode(), outputWarning + txrecmainBus.getEntdy());
            ApLogHelper.info(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    outputWarning + request.getLabel().getHcode());
            ApLogHelper.info(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    outputWarning + request.getLabel().getTxcode());
            if (txrecmainBus.getEntdy() == processDateInt + 19110000
                    && request.getLabel().getHcode().equals("1")
                    && wkTxcode.contains(request.getLabel().getTxcode())) {
                ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "內部REQUEST輸出 : " + request);
                txday = parse.decimal2String(request.getLabel().getCaldy(), 8, 0);
                txtime = request.getLabel().getCaltm();
                kinbr = request.getLabel().getKinbr();
                trmseq = request.getLabel().getTrmseq();
                txtno = request.getLabel().getTxtno();
                trmtyp = request.getLabel().getTrmtyp();
                tlrno = request.getLabel().getTlrno();
                supno = request.getLabel().getSupno();
                aptype = request.getLabel().getAptype();
                txno = request.getLabel().getTxno();
                stxno = request.getLabel().getStxno();
                dscpt = request.getLabel().getDscpt();
                txtype = request.getLabel().getTxtype();
                crdb = request.getLabel().getCrdb();
                spcd = request.getLabel().getSpcd();
                txamt = parse.decimal2String(request.getLabel().getTxamt(), 13, 0);
                caldy = parse.decimal2String(request.getLabel().getCaldy(), 8, 0);
                caltm = parse.decimal2String(request.getLabel().getCaltm(), 8, 0);
                writeFdExdtl();
            }
        }
    }

    private void validateTxrecmain() {
        if (Objects.isNull(txrecmainBusList) || txrecmainBusList.isEmpty()) {
            throw new LogicException("GE999", "TXRECMAIN 查無資料");
        }
    }

    private void writeFdExdtl() {
        StringBuilder stringBuilderFE = new StringBuilder();
        stringBuilderFE.append(formatUtil.padX(txday, 8));
        stringBuilderFE.append(formatUtil.pad9(txtime, 6));
        stringBuilderFE.append(formatUtil.padX(kinbr, 3));
        stringBuilderFE.append(formatUtil.padX(trmseq, 4));
        stringBuilderFE.append(formatUtil.padX(txtno, 8));
        stringBuilderFE.append(formatUtil.padX(trmtyp, 2));
        stringBuilderFE.append(formatUtil.padX(tlrno, 2));
        stringBuilderFE.append(formatUtil.padX(supno, 2));
        stringBuilderFE.append(formatUtil.padX(aptype, 1));
        stringBuilderFE.append(formatUtil.padX(txno, 2));
        stringBuilderFE.append(formatUtil.padX(stxno, 2));
        stringBuilderFE.append(formatUtil.padX(dscpt, 5));
        stringBuilderFE.append(formatUtil.padX(txtype, 2));
        stringBuilderFE.append(formatUtil.padX(crdb, 1));
        stringBuilderFE.append(formatUtil.padX(spcd, 1));
        stringBuilderFE.append(formatUtil.padX(txamt, 13));
        stringBuilderFE.append(formatUtil.padX(caldy, 8));
        stringBuilderFE.append(formatUtil.padX(caltm, 8));
        stringBuilderFE.append(formatUtil.padX(" ", 27));
        fileFdExdtlContents.add(formatUtil.padX(stringBuilderFE.toString(), 105));
    }

    private void writeFile() {
        try {
            textFile.writeFileContent(writeFdExdtlPath, fileFdExdtlContents, CHARSET_UTF8);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void moveErrorResponse(LogicException e) {
        // event.setPeripheryRequest();
    }

    private void batchResponse() {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", null);
    }
}
