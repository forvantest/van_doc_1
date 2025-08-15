/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import static java.math.BigDecimal.ZERO;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.CONV13559D;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import java.io.File;
import java.math.BigDecimal;
import java.text.DecimalFormat;
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
@Component("CONV13559DLsnr")
@Scope("prototype")
public class CONV13559DLsnr extends BatchListenerCase<CONV13559D> {

    @Autowired private Parse parse;
    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ClmrService clmrService;
    private CONV13559D event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET_UTF8 = "UTF-8";
    private static final String CONVF_PATH_PUTFN = "PUTFN";
    private static final String CONVF_PATH_CONN = "CONN";
    private static final String CONVF_PATH_07X013559D = "07X013559D";
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;
    private static final DecimalFormat decimalFormat =
            new DecimalFormat("##,###,###,##0"); // 明細金額格式
    private String processDate;
    private String readFdPutfnPath;
    private String writeFdOutPath;
    private String putfnCode;
    private String putfnRcptid;
    private String putfnCllbr;
    private String putfnUserdata;
    private String putfnTxtype;
    private String putfnFiller;
    private String filler;
    private String putfnNodata;
    private String filler1;
    private String wkCode;
    private String wcIrmrRemnam1;
    private String wcIrmrRemnam2;
    private int putfnCtl;
    private int putfnDate;
    private int putfnTime;
    private int putfnLmtdate;
    private int putfnOldamt;
    private int putfnSitdate;
    private int putfnBdate;
    private int putfnEdate;
    private int wkFirst = 1;
    private int wkCount = 0;
    private long wkActno;
    private BigDecimal putfnAmt = ZERO;
    private BigDecimal putfnTotCnt = ZERO;
    private BigDecimal putfnTotAmt = ZERO;
    private StringBuilder sbFdDtl = new StringBuilder();
    private List<String> fileFdOutContents = new ArrayList<>();

    @Override
    public void onApplicationEvent(CONV13559D event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV13559DLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(CONV13559D event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CONV13559DLsnr run()");

        init(event);

        writeFdOutDataHeader();

        chechFdPutfnDataExist();

        writeFile();
    }

    private void init(CONV13559D event) {
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();

        processDate = formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8); // todo: 待確認BATCH參數名稱
        String wkFdate = processDate.substring(1);

        readFdPutfnPath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + wkFdate
                        + PATH_SEPARATOR
                        + CONVF_PATH_CONN
                        + PATH_SEPARATOR
                        + wkFdate
                        + PATH_SEPARATOR
                        + CONVF_PATH_07X013559D;

        writeFdOutPath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + wkFdate
                        + PATH_SEPARATOR
                        + CONVF_PATH_PUTFN
                        + PATH_SEPARATOR
                        + wkFdate
                        + PATH_SEPARATOR
                        + CONVF_PATH_07X013559D;
    }

    private void writeFdOutDataHeader() {
        fileFdOutContents.add("[");
    }

    private void chechFdPutfnDataExist() {
        if (textFile.exists(readFdPutfnPath)) {
            readFdPutfnData();
        } else {
            writeFdOutDataFooter();
        }
    }

    private void readFdPutfnData() {
        List<String> lines = textFile.readFileContent(readFdPutfnPath, CHARSET_UTF8);

        if (Objects.isNull(lines) || lines.isEmpty()) {
            writeFdOutDataFooter();
            return;
        }

        for (String detail : lines) {
            if (detail.length() < 160) {
                detail = formatUtil.padX(detail, 160);
            }

            putfnCtl =
                    parse.string2Integer(
                            parse.isNumeric(detail.substring(0, 2)) ? detail.substring(0, 2) : "0");
            if (putfnCtl == 11 || putfnCtl == 21) {
                putfnCode = detail.substring(2, 8);
                putfnRcptid = detail.substring(8, 34);
                putfnDate =
                        parse.string2Integer(
                                parse.isNumeric(detail.substring(34, 42))
                                        ? detail.substring(34, 42)
                                        : "0");
                putfnTime =
                        parse.string2Integer(
                                parse.isNumeric(detail.substring(42, 48))
                                        ? detail.substring(42, 48)
                                        : "0");
                putfnCllbr = detail.substring(48, 51);
                putfnLmtdate =
                        parse.string2Integer(
                                (parse.isNumeric(detail.substring(51, 59))
                                        ? detail.substring(51, 59)
                                        : "0"));
                putfnOldamt =
                        parse.string2Integer(
                                (parse.isNumeric(detail.substring(59, 69))
                                        ? detail.substring(59, 69)
                                        : "0"));
                putfnUserdata = detail.substring(69, 109);
                putfnSitdate =
                        parse.string2Integer(
                                (parse.isNumeric(detail.substring(109, 117))
                                        ? detail.substring(109, 117)
                                        : "0"));
                putfnTxtype = detail.substring(117, 118);
                putfnAmt =
                        parse.string2BigDecimal(
                                (parse.isNumeric(detail.substring(118, 130))
                                        ? detail.substring(118, 130)
                                        : "0"));
                putfnFiller = detail.substring(130, 160);
                valuateFdOutData();
                writeFdOutDataDtl();
                wkCount++;
                wkFirst = 0;
            } else if (putfnCtl == 12) {
                putfnBdate =
                        parse.string2Integer(
                                (parse.isNumeric(detail.substring(0, 8))
                                        ? detail.substring(0, 8)
                                        : "0"));
                putfnEdate =
                        parse.string2Integer(
                                (parse.isNumeric(detail.substring(8, 16))
                                        ? detail.substring(8, 16)
                                        : "0"));
                putfnTotCnt =
                        parse.string2BigDecimal(
                                (parse.isNumeric(detail.substring(16, 24))
                                        ? detail.substring(16, 24)
                                        : "0"));
                putfnTotAmt =
                        parse.string2BigDecimal(
                                (parse.isNumeric(detail.substring(24, 37))
                                        ? detail.substring(24, 37)
                                        : "0"));
                filler = detail.substring(37, 154);
            } else {
                putfnNodata = detail.substring(0, 26);
                filler1 = detail.substring(26, 154);
            }
        }
        writeFdOutDataFooter();
    }

    private void valuateFdOutData() {
        checkCode();
        ClmrBus clmrBus = clmrService.findById(putfnCode);
        if (!Objects.isNull(clmrBus)) {
            throw new LogicException("GE999", "CLMR 無此代收類別");
        }

        if (clmrBus.getStop() != 1 || clmrBus.getStop() != 2) {
            wkActno = clmrBus.getActno();
        }
    }

    private void checkCode() {
        if (!putfnCode.equals(wkCode)) {
            wkCode = putfnCode;
        }
    }

    private void writeFdOutDataFooter() {
        if (wkFirst != 1) {
            fileFdOutContents.add(formatUtil.padX(sbFdDtl.toString(), 1));
        }
        fileFdOutContents.add("]");
    }

    private void writeFdOutDataDtl() {
        String wkDate = parse.decimal2String(putfnDate + 19110000, 8, 0);
        String wkTime = parse.decimal2String(putfnTime, 6, 0);
        String wkIrmrTxtno = putfnFiller.substring(0, 6);
        writeFdOutComma();
        fileFdOutContents.add("{");

        sbFdDtl = new StringBuilder();
        sbFdDtl.append(formatUtil.padX("\"REQUESTUID\":\"", 14));
        sbFdDtl.append(formatUtil.pad9(wkDate, 8));
        sbFdDtl.append(formatUtil.pad9(wkTime, 6));
        sbFdDtl.append(formatUtil.pad9(wkIrmrTxtno, 6));
        sbFdDtl.append(formatUtil.padX("\",", 2));
        fileFdOutContents.add(formatUtil.padX(sbFdDtl.toString(), 40));

        sbFdDtl = new StringBuilder();
        sbFdDtl.append(formatUtil.padX("\"TXNO\":\"", 8));
        sbFdDtl.append(formatUtil.padX("", 10));
        sbFdDtl.append(formatUtil.padX("\",", 2));
        fileFdOutContents.add(formatUtil.padX(sbFdDtl.toString(), 20));

        sbFdDtl = new StringBuilder();
        sbFdDtl.append(formatUtil.padX("\"MSGDATE\":\"", 11));
        sbFdDtl.append(formatUtil.pad9(wkDate, 8));
        sbFdDtl.append(formatUtil.padX("\",", 2));
        fileFdOutContents.add(formatUtil.padX(sbFdDtl.toString(), 30));

        sbFdDtl = new StringBuilder();
        sbFdDtl.append(formatUtil.padX("\"REMDAT\":\"", 10));
        sbFdDtl.append(formatUtil.pad9(wkDate, 8));
        sbFdDtl.append(formatUtil.padX("\",", 2));
        fileFdOutContents.add(formatUtil.padX(sbFdDtl.toString(), 20));

        sbFdDtl = new StringBuilder();
        sbFdDtl.append(formatUtil.padX("\"TXNTIME\":\"", 11));
        sbFdDtl.append(formatUtil.pad9(wkTime, 6));
        sbFdDtl.append(formatUtil.padX("\",", 2));
        fileFdOutContents.add(formatUtil.padX(sbFdDtl.toString(), 20));

        sbFdDtl = new StringBuilder();
        sbFdDtl.append(formatUtil.padX("\"ACTNO\":\"", 9));
        sbFdDtl.append(formatUtil.padX(parse.decimal2String(wkActno, 12, 0), 12));
        sbFdDtl.append(formatUtil.padX("\",", 2));
        fileFdOutContents.add(formatUtil.padX(sbFdDtl.toString(), 30));

        sbFdDtl = new StringBuilder();
        sbFdDtl.append(formatUtil.padX("\"ACTCURR\":\"", 11));
        sbFdDtl.append(formatUtil.padX("TWD", 3));
        sbFdDtl.append(formatUtil.padX("\",", 2));
        fileFdOutContents.add(formatUtil.padX(sbFdDtl.toString(), 20));

        sbFdDtl = new StringBuilder();
        sbFdDtl.append(formatUtil.padX("\"AMTSIGN\":\"", 11));
        sbFdDtl.append(formatUtil.padX("+", 1));
        sbFdDtl.append(formatUtil.padX("\",", 2));
        fileFdOutContents.add(formatUtil.padX(sbFdDtl.toString(), 20));

        sbFdDtl = new StringBuilder();
        sbFdDtl.append(formatUtil.padX("\"TRXAMOUNT\":\"", 13));
        sbFdDtl.append(formatUtil.pad9(decimalFormat.format(putfnAmt), 13));
        sbFdDtl.append(formatUtil.padX("\",", 2));
        fileFdOutContents.add(formatUtil.padX(sbFdDtl.toString(), 30));

        sbFdDtl = new StringBuilder();
        sbFdDtl.append(formatUtil.padX("\"TRXAMOUNTORI\":\"", 16));
        sbFdDtl.append(formatUtil.padX("", 13));
        sbFdDtl.append(formatUtil.padX("\",", 2));
        fileFdOutContents.add(formatUtil.padX(sbFdDtl.toString(), 40));

        sbFdDtl = new StringBuilder();
        sbFdDtl.append(formatUtil.padX("\"VALUEDATE\":\"", 13));
        sbFdDtl.append(formatUtil.padX(wkDate, 8));
        sbFdDtl.append(formatUtil.padX("\",", 2));
        fileFdOutContents.add(formatUtil.padX(sbFdDtl.toString(), 30));

        sbFdDtl = new StringBuilder();
        sbFdDtl.append(formatUtil.padX("\"CHARGE\":\"", 10));
        sbFdDtl.append(formatUtil.padX("", 13));
        sbFdDtl.append(formatUtil.padX("\",", 2));
        fileFdOutContents.add(formatUtil.padX(sbFdDtl.toString(), 30));

        sbFdDtl = new StringBuilder();
        sbFdDtl.append(formatUtil.padX("\"MEMO\":\"", 8));
        sbFdDtl.append(formatUtil.padX("", 40));
        sbFdDtl.append(formatUtil.padX("\",", 2));
        fileFdOutContents.add(formatUtil.padX(sbFdDtl.toString(), 50));

        sbFdDtl = new StringBuilder();
        sbFdDtl.append(formatUtil.padX("\"TXTYPE\":\"", 10));
        sbFdDtl.append(formatUtil.padX(putfnTxtype, 2));
        sbFdDtl.append(formatUtil.padX("\",", 2));
        fileFdOutContents.add(formatUtil.padX(sbFdDtl.toString(), 20));

        sbFdDtl = new StringBuilder();
        sbFdDtl.append(formatUtil.padX("\"VIRTUALACCOUNT\":\"", 18));
        sbFdDtl.append(formatUtil.padX(putfnRcptid.substring(0, 14), 14));
        sbFdDtl.append(formatUtil.padX("\",", 2));
        fileFdOutContents.add(formatUtil.padX(sbFdDtl.toString(), 40));

        valuateRemnam1();
        sbFdDtl = new StringBuilder();
        sbFdDtl.append(formatUtil.padX("\"REMNAM1\":\"", 11));
        sbFdDtl.append(formatUtil.padX(wcIrmrRemnam1, 35));
        sbFdDtl.append(formatUtil.padX("\",", 2));
        fileFdOutContents.add(formatUtil.padX(sbFdDtl.toString(), 50));

        valuateRemnam2();
        sbFdDtl = new StringBuilder();
        sbFdDtl.append(formatUtil.padX("\"REMNAM2\":\"", 11));
        sbFdDtl.append(formatUtil.padX(wcIrmrRemnam2, 35));
        sbFdDtl.append(formatUtil.padX("\",", 2));
        fileFdOutContents.add(formatUtil.padX(sbFdDtl.toString(), 50));

        sbFdDtl = new StringBuilder();
        sbFdDtl.append(formatUtil.padX("\"REMSWFCD\":\"", 12));
        sbFdDtl.append(formatUtil.padX("", 11));
        sbFdDtl.append(formatUtil.padX("\",", 2));
        fileFdOutContents.add(formatUtil.padX(sbFdDtl.toString(), 30));

        sbFdDtl = new StringBuilder();
        sbFdDtl.append(formatUtil.padX("\"REMBKNM1\":\"", 12));
        sbFdDtl.append(formatUtil.padX("", 35));
        sbFdDtl.append(formatUtil.padX("\",", 2));
        fileFdOutContents.add(formatUtil.padX(sbFdDtl.toString(), 50));

        sbFdDtl = new StringBuilder();
        sbFdDtl.append(formatUtil.padX("\"REMBKNM2\":\"", 12));
        sbFdDtl.append(formatUtil.padX("", 35));
        sbFdDtl.append(formatUtil.padX("\",", 2));
        fileFdOutContents.add(formatUtil.padX(sbFdDtl.toString(), 50));

        sbFdDtl = new StringBuilder();
        sbFdDtl.append(formatUtil.padX("\"REMBKNM3\":\"", 12));
        sbFdDtl.append(formatUtil.padX("", 35));
        sbFdDtl.append(formatUtil.padX("\",", 2));
        fileFdOutContents.add(formatUtil.padX(sbFdDtl.toString(), 50));

        sbFdDtl = new StringBuilder();
        sbFdDtl.append(formatUtil.padX("\"REMINF1\":\"", 11));
        sbFdDtl.append(formatUtil.padX("", 35));
        sbFdDtl.append(formatUtil.padX("\",", 2));
        fileFdOutContents.add(formatUtil.padX(sbFdDtl.toString(), 50));

        sbFdDtl = new StringBuilder();
        sbFdDtl.append(formatUtil.padX("\"REMINF2\":\"", 11));
        sbFdDtl.append(formatUtil.padX("", 35));
        sbFdDtl.append(formatUtil.padX("\",", 2));
        fileFdOutContents.add(formatUtil.padX(sbFdDtl.toString(), 50));

        sbFdDtl = new StringBuilder();
        sbFdDtl.append(formatUtil.padX("\"REMINF3\":\"", 11));
        sbFdDtl.append(formatUtil.padX("", 35));
        sbFdDtl.append(formatUtil.padX("\",", 2));
        fileFdOutContents.add(formatUtil.padX(sbFdDtl.toString(), 50));

        sbFdDtl = new StringBuilder();
        sbFdDtl.append(formatUtil.padX("\"REMINF4\":\"", 11));
        sbFdDtl.append(formatUtil.padX("", 35));
        sbFdDtl.append(formatUtil.padX("\"", 1));
        fileFdOutContents.add(formatUtil.padX(sbFdDtl.toString(), 50));

        sbFdDtl = new StringBuilder();
        sbFdDtl.append(formatUtil.padX("}", 1));
    }

    private void writeFdOutComma() {
        if (wkFirst != 1) {
            sbFdDtl.append(formatUtil.padX(",", 1));
            fileFdOutContents.add(formatUtil.padX(sbFdDtl.toString(), 2));
        }
    }

    private void valuateRemnam1() {
        if (putfnTxtype.equals("A")) {
            wcIrmrRemnam1 = putfnUserdata.substring(0, 19);
        } else {
            wcIrmrRemnam1 = "";
        }
    }

    private void valuateRemnam2() {
        if (putfnTxtype.equals("R")) {
            wcIrmrRemnam2 = putfnUserdata.substring(0, 19);
        } else {
            wcIrmrRemnam2 = "";
        }
    }

    private void writeFile() {
        try {
            textFile.deleteFile(writeFdOutPath);
            textFile.writeFileContent(writeFdOutPath, fileFdOutContents, CHARSET_UTF8);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void moveErrorResponse(LogicException e) {
        // event.setPeripheryRequest();
    }
}
