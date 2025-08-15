/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.Rpt017;
import com.bot.ncl.dto.entities.ClfeebyCodeBus;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.jpa.svc.ClfeeService;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.ncl.util.fileVo.FileKPUTH;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.text.format.Text2VoFormatter;
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
@Component("Rpt017Lsnr")
@Scope("prototype")
public class Rpt017Lsnr extends BatchListenerCase<Rpt017> {

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉

    @Autowired private ClmrService clmrService;
    @Autowired private ClfeeService clfeeService;
    @Autowired private FormatUtil formatUtil;
    @Autowired private TextFileUtil textFile;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private Parse parse;
    @Autowired private FileKPUTH fileKPUTH;

    private ClmrBus clmrBus;
    private List<ClfeebyCodeBus> clfeebyCodeBusList;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> labelMap;
    private static final DecimalFormat decimalFormat =
            new DecimalFormat("##,###,###,##0"); // 明細金額格式
    private static final DecimalFormat cntFormat = new DecimalFormat("##,###,##0"); // 筆數格式

    private static final String CHARSET = "UTF-8"; // 檔案編碼
    private static final String FILE_INPUT_NAME = "KPUTH"; // 讀檔檔名
    private static final String FILE_TMP_NAME = "TMPRpt017"; // tmp檔名
    private static final String FILE_NAME = "CL-BH-017"; // 檔名1
    private static final String PATH_SEPARATOR = File.separator;
    private static final String ANALY_FILE_PATH = "ANALY"; // 讀檔目錄
    private String inputFilePath; // 讀檔路徑
    private String sortTmpFilePath; // SortTmp檔路徑
    private String outputFilePath; // 產檔路徑
    private List<String> sortTmpfileRpt017Contents; // Tmp檔案內容
    private List<String> fileRpt017Contents; // 檔案內容
    private String wkCode = "";
    private String wkEntdy;
    private String wkRpt017DateR;
    private String wkRpt017DateRYYY;
    private String wkRpt017DateRMM;
    private String wkRpt017DateRDD;
    private String wkYear;
    private String wkMonth;
    private String wkDay;
    private String titleName;
    private String titleCode;
    private int wkBrno;
    private int wkSubcnt;
    private int wkTotcnt;
    private int page = 1;
    private Long wkActno;
    private BigDecimal wkSubamt = new BigDecimal(0);
    private BigDecimal wkTotamt = new BigDecimal(0);
    private BigDecimal titleCfee1 = new BigDecimal(0);
    private BigDecimal titleCfee4 = new BigDecimal(0);

    @Override
    public void onApplicationEvent(Rpt017 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Rpt017Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Rpt017 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Rpt017Lsnr run()");
        init(event);
        queryData();
        sortFileContent();

        try {
            textFile.writeFileContent(outputFilePath, fileRpt017Contents, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        textFile.deleteFile(sortTmpFilePath);
    }

    private void moveErrorResponse(LogicException e) {
        // event.setPeripheryRequest();
    }

    private void init(Rpt017 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "RPT017Lsnr init");

        // 抓批次營業日

        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();

        String batchDate =
                getrocdate(parse.string2Integer(labelMap.get("BBSDY"))); // 待中菲APPLE提供正確名稱

        int fnbsdy = event.getAggregateBuffer().getTxCom().getFnbsdy();
        String fnbsdyString = parse.decimal2String(fnbsdy, 8, 0);
        wkYear = fnbsdyString.substring(0, 4);
        wkMonth = fnbsdyString.substring(4, 6);
        wkDay = fnbsdyString.substring(6, 8);

        // 讀檔路徑
        inputFilePath = fileDir + ANALY_FILE_PATH + PATH_SEPARATOR + FILE_INPUT_NAME;
        // 暫存檔路徑
        sortTmpFilePath = fileDir + FILE_TMP_NAME;
        // 產檔路徑
        outputFilePath = fileDir + FILE_NAME;
        // 刪除舊檔
        textFile.deleteFile(sortTmpFilePath);
        textFile.deleteFile(outputFilePath);
        sortTmpfileRpt017Contents = new ArrayList<>();
        fileRpt017Contents = new ArrayList<>();
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "RPT017Lsnr batchDate = {}", batchDate);
    }

    private String getrocdate(int dateI) {
        String date = "" + dateI;
        if (dateI > 19110101) {
            dateI = dateI - 19110000;
        }
        if (String.valueOf(dateI).length() < 7) {
            date = String.format("%07d", dateI);
        }
        return date;
    }

    private void sortFileContent() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Rpt017Lsnr sortFileContent ....");
        List<String> lines = textFile.readFileContent(inputFilePath, CHARSET);
        for (String detail : lines) {
            text2VoFormatter.format(detail, fileKPUTH);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "fileKPUTH = {}", fileKPUTH);
            sortTmpfileRpt017Contents.add(detail);
        }
        try {
            textFile.writeFileContent(sortTmpFilePath, sortTmpfileRpt017Contents, CHARSET);
        } catch (LogicException e) {
            ApLogHelper.error(
                    log, false, LogType.NORMAL.getCode(), "error message = {}", e.getMessage());
        }
    }

    private void queryData() {
        List<String> lines = textFile.readFileContent(inputFilePath, CHARSET);
        for (String detail : lines) {
            text2VoFormatter.format(detail, fileKPUTH);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "fileKPUTH = {}", fileKPUTH);
            String kputhCode = detail.substring(20, 26);
            String kputhDate = detail.substring(42, 49);
            BigDecimal kputhAmt = parse.string2BigDecimal(detail.substring(64, 74));
            if (wkCode.isEmpty() || wkCode.isBlank()) {
                wkCode = kputhCode;
                wkEntdy = kputhDate;
                wkRpt017DateR = kputhDate;
                wkRpt017DateRYYY = wkRpt017DateR.substring(0, 3);
                wkRpt017DateRMM = wkRpt017DateR.substring(3, 5);
                wkRpt017DateRDD = wkRpt017DateR.substring(5, 7);
                wkSubamt = wkSubamt.add(kputhAmt);
                wkTotamt = wkTotamt.add(kputhAmt);
                wkSubcnt++;
                wkTotcnt++;
                clmrBus = clmrService.findById(wkCode);
                if (Objects.isNull(clmrBus)) {
                    ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "clmrBus 無此收付類別");
                    return;
                }
                titleName = clmrBus.getCname();
                titleCode = clmrBus.getCode();
                wkBrno = clmrBus.getPbrno();
                wkActno = clmrBus.getActno();
                clfeebyCodeBusList = clfeeService.findbyCode(wkCode, 0, Integer.MAX_VALUE);
                for (ClfeebyCodeBus clfeebyCodeBus : clfeebyCodeBusList) {
                    titleCfee1 = clfeebyCodeBus.getCfee1();
                    titleCfee4 = clfeebyCodeBus.getCfee4();
                }
                reportHeader();
            }
            if (kputhCode.equals(wkCode) && kputhDate.equals(wkEntdy)) {
                wkSubcnt++;
                wkTotcnt++;
                wkSubamt = wkSubamt.add(kputhAmt);
                wkTotamt = wkTotamt.add(kputhAmt);
            }
            if (kputhCode.equals(wkCode) && (!kputhDate.equals(wkEntdy))) {
                wkSubcnt++;
                wkTotcnt++;
                wkSubamt = wkSubamt.add(kputhAmt);
                wkTotamt = wkTotamt.add(kputhAmt);
                reportContents();
                wkSubcnt = 0;
                wkSubamt = new BigDecimal(0);
            }
            if (!kputhCode.equals(wkCode)) {
                wkSubcnt++;
                wkTotcnt++;
                wkSubamt = wkSubamt.add(kputhAmt);
                wkTotamt = wkTotamt.add(kputhAmt);
                reportContents();
                reportFooter();
                wkSubcnt = 0;
                wkSubamt = new BigDecimal(0);
                page++;
                wkCode = kputhCode;
                wkEntdy = kputhDate;
                wkRpt017DateR = kputhDate;
                clmrBus = clmrService.findById(wkCode);
                if (Objects.isNull(clmrBus)) {
                    ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "clmrBus 無此收付類別");
                    return;
                }
                titleName = clmrBus.getCname();
                titleCode = clmrBus.getCode();
                wkBrno = clmrBus.getPbrno();
                wkActno = clmrBus.getActno();
                clfeebyCodeBusList = clfeeService.findbyCode(wkCode, 0, Integer.MAX_VALUE);
                for (ClfeebyCodeBus clfeebyCodeBus : clfeebyCodeBusList) {
                    titleCfee1 = clfeebyCodeBus.getCfee1();
                    titleCfee4 = clfeebyCodeBus.getCfee4();
                }
                reportHeader();
            }
        }
        if (wkTotcnt == lines.size() && wkSubcnt != 0) {
            reportContents();
        }
        reportFooter();
    }

    private void reportHeader() {
        StringBuilder stringBuilder1 = new StringBuilder();
        stringBuilder1.append(formatUtil.padX(" ", 19));
        stringBuilder1.append(formatUtil.padX("台灣銀行連線代收", 18));
        stringBuilder1.append(formatUtil.padX(wkYear, 4));
        stringBuilder1.append(formatUtil.padX("年", 4));
        stringBuilder1.append(formatUtil.padX(wkMonth, 2));
        stringBuilder1.append(formatUtil.padX("月彙總月報表            ", 28));
        stringBuilder1.append(formatUtil.padX(" ", 6));
        fileRpt017Contents.add(formatUtil.padX(stringBuilder1.toString(), 158));

        StringBuilder stringBuilder2 = new StringBuilder();
        stringBuilder2.append(formatUtil.padX(" ", 1));
        fileRpt017Contents.add(formatUtil.padX(stringBuilder2.toString(), 158));

        StringBuilder stringBuilder3 = new StringBuilder();
        stringBuilder3.append(formatUtil.padX(" ", 31));
        stringBuilder3.append(formatUtil.padX("印表日:", 9));
        stringBuilder3.append(formatUtil.padX(wkYear + "/" + wkMonth + "/" + wkDay, 10));
        fileRpt017Contents.add(formatUtil.padX(stringBuilder3.toString(), 158));

        StringBuilder stringBuilder4 = new StringBuilder();
        stringBuilder4.append(formatUtil.padX("主辦分行      :", 17));
        stringBuilder4.append(formatUtil.padX("" + wkBrno, 3));
        stringBuilder4.append(formatUtil.padX(" ", 45));
        stringBuilder4.append(formatUtil.padX("頁　　次:", 12));
        stringBuilder4.append(formatUtil.padX("" + page, 3));
        fileRpt017Contents.add(formatUtil.padX(stringBuilder4.toString(), 158));

        StringBuilder stringBuilder5 = new StringBuilder();
        stringBuilder5.append(formatUtil.padX("代收類別／名稱:", 17));
        stringBuilder5.append(formatUtil.padX(titleCode, 6));
        stringBuilder5.append(formatUtil.padX("/", 1));
        stringBuilder5.append(formatUtil.padX(titleName, 40));
        stringBuilder5.append(formatUtil.padX(" 報表名稱:C017", 16));
        fileRpt017Contents.add(formatUtil.padX(stringBuilder5.toString(), 158));

        StringBuilder stringBuilder6 = new StringBuilder();
        stringBuilder6.append(formatUtil.padX("事業單位給付主辦行每筆／每月費用:", 36));
        stringBuilder6.append(formatUtil.padX(decimalFormat.format(titleCfee1), 10));
        stringBuilder6.append(formatUtil.padX("/", 1));
        stringBuilder6.append(formatUtil.padX(decimalFormat.format(titleCfee4), 20));
        fileRpt017Contents.add(formatUtil.padX(stringBuilder6.toString(), 158));

        StringBuilder stringBuilder7 = new StringBuilder();
        stringBuilder7.append(formatUtil.padX("授權扣款帳號　:", 17));
        stringBuilder7.append(formatUtil.padX("" + wkActno, 12));
        fileRpt017Contents.add(formatUtil.padX(stringBuilder7.toString(), 158));

        StringBuilder stringBuilder8 = new StringBuilder();
        stringBuilder8.append(formatUtil.padX(" ", 1));
        fileRpt017Contents.add(formatUtil.padX(stringBuilder8.toString(), 158));

        StringBuilder stringBuilder9 = new StringBuilder();
        stringBuilder9.append(formatUtil.padX(" ", 3));
        stringBuilder9.append(formatUtil.padX("入帳日期", 10));
        stringBuilder9.append(formatUtil.padX(" ", 9));
        stringBuilder9.append(formatUtil.padX("總金額", 8));
        stringBuilder9.append(formatUtil.padX(" ", 4));
        stringBuilder9.append(formatUtil.padX("總件數", 8));
        stringBuilder9.append(formatUtil.padX(" ", 14));
        stringBuilder9.append(formatUtil.padX("備註", 6));
        fileRpt017Contents.add(formatUtil.padX(stringBuilder9.toString(), 158));

        StringBuilder stringBuilder10 = new StringBuilder();
        stringBuilder10.append(
                formatUtil.padX(
                        "-------------------------"
                                + "-------------------------------------------------------",
                        80));
        fileRpt017Contents.add(formatUtil.padX(stringBuilder10.toString(), 158));
    }

    private void reportContents() {
        StringBuilder stringBuilder1 = new StringBuilder();
        stringBuilder1.append(formatUtil.padX(" ", 4));
        stringBuilder1.append(
                formatUtil.padX(
                        wkRpt017DateRYYY + "/" + wkRpt017DateRMM + "/" + wkRpt017DateRDD, 10));
        stringBuilder1.append(formatUtil.padX(" ", 3));
        stringBuilder1.append(formatUtil.padX(decimalFormat.format(wkSubamt), 14));
        stringBuilder1.append(formatUtil.padX(" ", 3));
        stringBuilder1.append(formatUtil.padX(cntFormat.format(wkSubcnt), 10));
        fileRpt017Contents.add(formatUtil.padX(stringBuilder1.toString(), 158));
    }

    private void reportFooter() {
        StringBuilder stringBuilder1 = new StringBuilder();
        stringBuilder1.append(formatUtil.padX(" ", 5));
        stringBuilder1.append(formatUtil.padX("總　計 ", 9));
        stringBuilder1.append(formatUtil.padX(decimalFormat.format(wkTotamt), 16));
        stringBuilder1.append(formatUtil.padX(" ", 3));
        stringBuilder1.append(formatUtil.padX(cntFormat.format(wkTotcnt), 10));
        fileRpt017Contents.add(formatUtil.padX(stringBuilder1.toString(), 158));

        StringBuilder stringBuilder2 = new StringBuilder();
        stringBuilder2.append(
                formatUtil.padX(
                        "--------------------------"
                                + "------------------------------------------------------",
                        80));
        fileRpt017Contents.add(formatUtil.padX(stringBuilder2.toString(), 158));

        StringBuilder stringBuilder3 = new StringBuilder();
        stringBuilder3.append(formatUtil.padX(" 備註： ", 10));
        fileRpt017Contents.add(formatUtil.padX(stringBuilder3.toString(), 158));

        StringBuilder stringBuilder4 = new StringBuilder();
        stringBuilder4.append(formatUtil.padX(" 「若客戶已連續六個月以上未有收款紀錄， ", 40));
        stringBuilder4.append(formatUtil.padX(" 或已無收款需求者， ", 30));
        fileRpt017Contents.add(formatUtil.padX(stringBuilder4.toString(), 158));

        StringBuilder stringBuilder5 = new StringBuilder();
        stringBuilder5.append(formatUtil.padX(" 請主辦行依規定辦理註銷事宜。」 ", 40));
        fileRpt017Contents.add(formatUtil.padX(stringBuilder5.toString(), 158));
    }
}
