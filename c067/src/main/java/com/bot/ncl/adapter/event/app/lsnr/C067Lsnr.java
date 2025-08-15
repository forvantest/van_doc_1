/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.C067;
import com.bot.ncl.dto.entities.CldtleqCodeEntdyBus;
import com.bot.ncl.jpa.svc.CldtlService;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.ncl.util.sort.ExternalSortUtil;
import com.bot.ncl.util.sort.eum.SortBy;
import com.bot.ncl.util.sort.vo.KeyRange;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.date.DateUtil;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.transmit.FsapSyncSftpService;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("C067Lsnr")
@Scope("prototype")
public class C067Lsnr extends BatchListenerCase<C067> {
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    @Autowired private CldtlService cldtlService;
    @Autowired private TextFileUtil textFile;
    @Autowired private Parse parse;
    @Autowired private FormatUtil formatUtil;
    @Autowired private DateUtil dateUtil;
    @Autowired private ExternalSortUtil externalSortUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;

    private C067 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> labelMap;
    private Map<String, String> textMap;

    private StringBuilder sb = new StringBuilder();
    private static final String CHARSET = "Big5"; // 檔案編碼
    private static final String FILE_TMP_NAME = "TMPC067"; // tmp檔名
    private static final String FILE_NAME = "CL-BH-C067"; // 檔名
    private List<String> fileTmpContents; // Tmp檔案內容
    private List<String> fileC067Contents; // 檔案內容
    private String tmpFilePath; // 暫存檔路徑
    private String outputFilePath; // C067路徑
    private String processDate; // 作業日期(民國年yyyymmdd)
    private String tbsdy;
    private String CODE = "121444"; // 查詢條件
    private int HEADER_ROW = 9; // 頁首固定行數
    private int nowPageRow = 0; // 目前頁面行數
    private String pageSeparator = "\u000C"; // 換頁符
    private String sysDate = ""; // 系統日期
    private boolean noData = false;
    // 明細變數---------------------
    private String dPbrno = ""; // 縣市主辦行
    private String dDate = ""; // 收付日期
    private String dCllbr = ""; // 代付分行
    private String dAmt = ""; // 金額
    private int subCnt = 0; // 同日件數
    private int wkCnt = 0;
    private BigDecimal sumAmt = BigDecimal.ZERO; // 同日金額
    // -------------------------------
    private BigDecimal pbrnoTotalAmt = BigDecimal.ZERO; // 主辦行總計金額
    private DecimalFormat pbrnoTotalAmtFormat = new DecimalFormat("#,###,###,###,##0"); // 主辦行總計金額格式
    private DecimalFormat dAmtFormat = new DecimalFormat("##,###,###,##0"); // 明細金額格式
    private DecimalFormat dCntFormat = new DecimalFormat("##,###,##0"); // 明細筆數格式

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(C067 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C067Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(C067 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C067Lsnr run()");
        init(event);
        queryCldtl();
        if (!noData) {
            // 排序檔案資料
            sortTmpFile();
            // 產C067
            toWriteC067File();
            // 刪除暫存檔
            textFile.deleteFile(tmpFilePath);
        } else {
            fileC067Contents.add(pageSeparator);
            setContents(); // 寫資料
            pbrnoTotal();
        }
        try {
            textFile.writeFileContent(outputFilePath, fileC067Contents, CHARSET);
            upload(outputFilePath, "RPT", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }

        batchResponse();
    }

    private void init(C067 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C067Lsnr init");
        this.event = event;
        // 抓批次營業日
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        tmpFilePath =
                fileDir
                        + CONVF_DATA
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + FILE_TMP_NAME;
        outputFilePath =
                fileDir + CONVF_RPT + PATH_SEPARATOR + processDate + PATH_SEPARATOR + FILE_NAME;
        fileTmpContents = new ArrayList<>();
        fileC067Contents = new ArrayList<>();
        textFile.deleteFile(tmpFilePath);
        textFile.deleteFile(outputFilePath);
        sysDate = dateUtil.getNowStringRoc(); // 取系統日
    }

    private void queryCldtl() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C067Lsnr queryCldtl");

        List<CldtleqCodeEntdyBus> lCldtl =
                cldtlService.eqCodeEntdy(
                        CODE, parse.string2Integer(processDate), 0, 0, Integer.MAX_VALUE);
        if (lCldtl == null) {
            ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "error cldtl 查無資料");
            noData = true;
        } else {
            //  寫資料到檔案中
            try {
                setTmpData(lCldtl);
            } catch (IOException e) {
                ApLogHelper.error(
                        log, false, LogType.NORMAL.getCode(), "error message = {}", e.getMessage());
            }
        }
    }

    private void setTmpData(List<CldtleqCodeEntdyBus> lCldtl) throws IOException {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C067Lsnr setTmpData");
        if (lCldtl != null) {
            for (CldtleqCodeEntdyBus tCldtl : lCldtl) {
                String pbrno = tCldtl.getRcptid().substring(4, 7);
                String date = getrocdate(tCldtl.getEntdy());
                String cllbr = String.format("%03d", tCldtl.getCllbr());
                String rcptid = String.format("%26s", tCldtl.getRcptid());
                String sitdate = getrocdate(tCldtl.getSitdate());
                String time = String.format("%06d", tCldtl.getTime());
                String amt = String.format("%15s", tCldtl.getAmt());

                String s = pbrno + date + cllbr + rcptid + sitdate + time + amt;
                fileTmpContents.add(s);
            }
        }
        try {
            textFile.writeFileContent(tmpFilePath, fileTmpContents, CHARSET);
        } catch (LogicException e) {
            ApLogHelper.error(
                    log, false, LogType.NORMAL.getCode(), "error message = {}", e.getMessage());
        }
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C067Lsnr File OK");
    }

    private void sortTmpFile() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "toWriteC067File sortTmpFile");
        File tmpFile = new File(tmpFilePath);
        List<KeyRange> keyRanges = new ArrayList<>();
        keyRanges.add(new KeyRange(1, 3, SortBy.ASC));
        keyRanges.add(new KeyRange(4, 7, SortBy.ASC));
        keyRanges.add(new KeyRange(11, 3, SortBy.ASC));
        keyRanges.add(new KeyRange(14, 26, SortBy.ASC));
        keyRanges.add(new KeyRange(40, 7, SortBy.ASC));
        keyRanges.add(new KeyRange(47, 6, SortBy.ASC));
        externalSortUtil.sortingFile(tmpFile, tmpFile, keyRanges);
    }

    private void toWriteC067File() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "toWriteC067File ");
        List<String> lines = textFile.readFileContent(tmpFilePath, CHARSET);

        int cnt = 0;
        nowPageRow = 0;
        for (String detail : lines) {
            // 總筆數
            cnt++;
            if (dCllbr.equals(detail.substring(10, 13))) {
                // -------------同日------------------
                subCnt++; // 總件數
                wkCnt++;
                dAmt = detail.substring(52, 67); // 金額
                sumAmt = sumAmt.add(parse.string2BigDecimal(dAmt)); // 總金額
            } else {
                // -------------不同日------------------
                dPbrno = detail.substring(0, 3); // 縣市主辦行
                dDate = detail.substring(3, 10); // 代付日
                dCllbr = detail.substring(10, 13); // 代付分行
                dAmt = detail.substring(52, 67); // 金額
                subCnt = 1; // 總件數
                wkCnt++;
                sumAmt = parse.string2BigDecimal(dAmt); // 總金額
            }
            // 第一頁
            if (cnt == 1) {
                toC067FileHeader(1); // 寫第一頁頁首
                nowPageRow = 9;
            }
            // 尾筆資料或下一筆資料日期、主辦行不同時寫內容
            if (cnt == lines.size()
                    || !dCllbr.equals(lines.get(cnt).substring(10, 13))
                    || !dPbrno.equals(lines.get(cnt).substring(0, 3))) {
                setContents(); // 寫資料
                nowPageRow++;
            }
            // 非尾筆資料且縣市主辦行不同時寫總計並換行
            if (cnt != lines.size() && !dPbrno.equals(lines.get(cnt).substring(0, 3))) {
                pbrnoTotal(); // 寫主辦行總計
                dPbrno = lines.get(cnt).substring(0, 3);
                toC067FileHeader(1); // 換頁寫頁首
                nowPageRow = 9;
            }
            // 為最後一筆資料時
            if (cnt == lines.size()) {
                pbrnoTotal(); // 寫主辦行總計
            }
            //            因筆數不多無單頁行數上限,註解換頁判斷
            //            if (nowPageRow >= PAGE_MAX_ROW) {
            //                toC067FileHeader(1);
            //                nowPageRow = 8;
            //            }
        }
    }

    private void toC067FileHeader(int newPageFg) { // Fg 1為需換頁上換頁符
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "toC067FileHeader .... ");

        for (int i = 1; i <= HEADER_ROW; i++) {
            sb = new StringBuilder();
            switch (i) {
                case 1:
                    if (i == newPageFg) {
                        sb.append(pageSeparator);
                    } else {
                        sb.append(""); // 預留
                    }
                    break;
                case 3:
                    String sysDateYY = sysDate.substring(0, 3);
                    String sysDateMM = sysDate.substring(3, 5);
                    String sysDateDD = sysDate.substring(5, 7);
                    sb.append("                       綜所稅退稅憑單報表－依兌付分行小計  印表日： ");
                    sb.append(sysDateYY + "/" + sysDateMM + "/" + sysDateDD); // 系統日
                    break;
                case 5:
                    String dateYY = dDate.substring(0, 3);
                    String dateMM = dDate.substring(3, 5);
                    String dateDD = dDate.substring(5, 7);
                    sb.append("   代付日期： ");
                    sb.append(dateYY + " 年 " + dateMM + " 月 " + dateDD + " 日");
                    sb.append("                           頁　次：  ");
                    sb.append("1");
                    break;
                case 6:
                    sb.append("   縣市主辦行： ");
                    sb.append(dPbrno);
                    sb.append("                                        報表名稱： ");
                    sb.append("C067 ");
                    break;
                case 8:
                    sb.append("    代付分行            總金額       總件數                備註 ");
                    break;
                default:
                    if (i == HEADER_ROW) { // 為頁首最後一行時
                        sb.append(
                                "--------------------------------------------------------------------------------");
                    } else {
                        sb.append("");
                    }
                    break;
            }
            fileC067Contents.add(sb.toString());
        }
    }

    private void setContents() {
        sb = new StringBuilder();
        sb.append("    ");
        sb.append(formatUtil.padX(dCllbr, 3)); // 代付分行
        sb.append("         ");
        sb.append(String.format("%14s", dAmtFormat.format(sumAmt))); // 總金額
        sb.append("   ");
        sb.append(String.format("%10s", dCntFormat.format(subCnt))); // 總件數
        sb.append("   ");
        sb.append(" "); // 備註 擺空白
        fileC067Contents.add(sb.toString());
        // 計算 主辦行總金額
        pbrnoTotalAmt = pbrnoTotalAmt.add(sumAmt);
        // 歸零 金額與件數
        sumAmt = BigDecimal.ZERO;
        subCnt = 0;
    }

    private void pbrnoTotal() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "pbrnoTotal .... ");
        fileC067Contents.add("");
        sb = new StringBuilder();
        sb.append(
                "--------------------------------------------------------------------------------");
        fileC067Contents.add(sb.toString());
        sb = new StringBuilder();
        sb.append("      總　計 ");
        sb.append(String.format("%17s", pbrnoTotalAmtFormat.format(pbrnoTotalAmt)));
        sb.append(formatUtil.padX("", 3));
        sb.append(reportUtil.customFormat("" + wkCnt, "ZZ,ZZZ,ZZ9"));
        fileC067Contents.add(sb.toString());
        fileC067Contents.add("");
        // 換不同主辦行 重新計算主辦行總計
        pbrnoTotalAmt = BigDecimal.ZERO;
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

    private void moveErrorResponse(LogicException e) {
        //        event.setPeripheryRequest();
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

    private void batchResponse() {
        Map<String, String> responseTextMap = new HashMap<>(textMap);
        responseTextMap.put("RPTNAME", FILE_NAME);

        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", responseTextMap);
    }
}
