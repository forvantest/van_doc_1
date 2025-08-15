/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.C063;
import com.bot.ncl.dto.entities.CldtlbyCodeEntdyBus;
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
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.transmit.FsapSyncSftpService;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("C063Lsnr")
@Scope("prototype")
public class C063Lsnr extends BatchListenerCase<C063> {

    @Autowired private CldtlService cldtlService;
    @Autowired private TextFileUtil textFile;
    @Autowired private Parse parse;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private ExternalSortUtil externalSortUtil;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;

    private C063 event;
    private List<String> fileTmpContents; // 檔案內容
    private List<String> fileC063Contents; // 檔案內容

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> labelMap;
    private Map<String, String> textMap;

    private String CODE = "121444";
    private String processDate; // 作業日期(民國年yyyymmdd)
    private String tbsdy;
    private static final String CHARSET = "Big5";
    private static final String FILE_TMP_NAME = "TMPC063";
    private static final String FILE_NAME = "CL-BH-C063";
    private StringBuilder sb = new StringBuilder();

    private String tmpFilePath; // 暫存檔路徑
    private String outputFilePath; // C063路徑
    // 頁面上限行數
    private int PAGE_MAX_ROW = 54;
    // 目前頁面行數
    int nowPageRow = 0;
    // 頁首行數
    private int HEADER_ROW = 8;
    private int pageCnt = 0;
    private String pageSeparator = "\u000C";
    private DecimalFormat decimalC063AmtFormat = new DecimalFormat("###,###,##0");
    private DecimalFormat decimalC063CntFormat = new DecimalFormat("###,##0");
    private DecimalFormat decimalC063totalAmtFormat = new DecimalFormat("#,###,###,###,##0");
    private Boolean noData = false;
    // 明細變數---------------------
    private String dPbrno = "";
    private String dDate = "";
    private String dCllbr = "";
    private String dRcptid = "";
    private String dSitdate = "";
    private String dTime = "";
    private String dAmt = "";
    private String dTxtype = "";
    private String dUserdate = "";
    // -------------------------------
    // 分行小計筆數金額
    private int subCnt = 0;
    private BigDecimal subAmt = BigDecimal.ZERO;
    // 總筆數金額
    private int totalCnt = 0;
    private BigDecimal totalAmt = BigDecimal.ZERO;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(C063 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C063Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(C063 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C063Lsnr run()");
        init(event);
        queryCldtl();
        textFile.deleteFile(tmpFilePath);
        batchResponse();
    }

    private void init(C063 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C063Lsnr init");
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
        fileC063Contents = new ArrayList<>();
        textFile.deleteFile(tmpFilePath);
        textFile.deleteFile(outputFilePath);
    }

    private void queryCldtl() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C063Lsnr queryCldtl");

        List<CldtlbyCodeEntdyBus> lCldtl =
                cldtlService.findbyCodeEntdy(
                        CODE, parse.string2Integer(processDate), 0, Integer.MAX_VALUE);
        // 寫資料到檔案中
        if (Objects.isNull(lCldtl)) {
            sb = new StringBuilder();
            sb.append(formatUtil.padX("", 9));
            sb.append(formatUtil.padX("NO DATA !!", 10));
            fileTmpContents.add(sb.toString());
            noData = true;
        } else {
            setTmpData(lCldtl);
            // 排序檔案資料
            sortfile();
        }
        // 產C063
        toWriteC063File();
    }

    private void setTmpData(List<CldtlbyCodeEntdyBus> lCldtl) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C063Lsnr setTmpData");

        for (CldtlbyCodeEntdyBus tCldtl : lCldtl) {
            String pbrno = tCldtl.getRcptid().substring(4, 7);
            String date = getrocdate(tCldtl.getEntdy());
            String cllbr = String.format("%03d", tCldtl.getCllbr());
            String rcptid = String.format("%26s", tCldtl.getRcptid());
            String sitdate = getrocdate(tCldtl.getSitdate());
            String time = String.format("%06d", tCldtl.getTime());
            String code = String.format("%6s", tCldtl.getCode());
            String amt = String.format("%15s", tCldtl.getAmt());
            String txtype = String.format("%1s", tCldtl.getTxtype());
            String userdate = String.format("%40s", tCldtl.getUserdata());
            String s =
                    pbrno + date + cllbr + rcptid + sitdate + time + code + amt + txtype + userdate;
            fileTmpContents.add(s);
        }

        try {
            textFile.writeFileContent(tmpFilePath, fileTmpContents, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C063Lsnr File OK");
    }

    private void sortfile() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "toWriteC063File sortfile");

        File tmpFile = new File(tmpFilePath);
        List<KeyRange> keyRanges = new ArrayList<>();
        keyRanges.add(new KeyRange(1, 3, SortBy.ASC));
        keyRanges.add(new KeyRange(11, 3, SortBy.ASC));
        keyRanges.add(new KeyRange(14, 26, SortBy.ASC));
        keyRanges.add(new KeyRange(40, 7, SortBy.ASC));
        keyRanges.add(new KeyRange(47, 6, SortBy.ASC));

        LocalDateTime startTime = LocalDateTime.now();
        externalSortUtil.sortingFile(tmpFile, tmpFile, keyRanges);
    }

    private void toWriteC063File() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "toWriteC063File toWriteC063File");

        if (noData) {
            dDate = "0000000";
            dPbrno = "   ";
            pageCnt = 1;
            toC063FileHeader(1);
            for (String detail : fileTmpContents) {
                fileC063Contents.add(detail);
            }
            newCllbrSub();
            toC063FileFooter();
        } else {
            List<String> lines = textFile.readFileContent(tmpFilePath, CHARSET);
            int cnt = 0;
            nowPageRow = 0;
            for (String detail : lines) {
                // 總筆數
                cnt++;
                dPbrno = detail.substring(0, 3);
                dDate = detail.substring(3, 10);
                dCllbr = detail.substring(10, 13);
                dRcptid = detail.substring(13, 39).trim();
                dSitdate = detail.substring(39, 46);
                dTime = detail.substring(46, 52);
                dAmt = detail.substring(58, 73);
                dTxtype = detail.substring(73, 74);
                dUserdate = detail.substring(74, 114);
                // 第一頁
                if (cnt == 1) {
                    pageCnt = 1;
                    toC063FileHeader(1);
                    nowPageRow = 8;
                }

                sb = new StringBuilder();
                // 代付分行
                sb.append(formatUtil.pad9(dCllbr, 3));
                sb.append("        ");
                // 銷帳編號
                sb.append(String.format("%16s", dRcptid));
                sb.append("  ");
                // 交易日期
                String sitDateYY = dSitdate.substring(0, 3);
                String sitDateMM = dSitdate.substring(3, 5);
                String sitDateDD = dSitdate.substring(5, 7);
                sb.append(sitDateYY + "/" + sitDateMM + "/" + sitDateDD);
                sb.append("  ");
                // 時間
                sb.append(formatUtil.pad9(dTime, 6));
                sb.append("  ");
                // 金額
                sb.append(
                        String.format(
                                "%11s",
                                decimalC063AmtFormat.format(parse.string2BigDecimal(dAmt))));
                sb.append("     ");
                // 帳務別
                sb.append(dTxtype);
                sb.append("    ");
                // 備註
                sb.append(dUserdate);
                fileC063Contents.add(sb.toString());
                // 計算小計與總計
                subCnt++;
                subAmt = subAmt.add(parse.string2BigDecimal(dAmt));
                totalCnt++;
                totalAmt = totalAmt.add(parse.string2BigDecimal(dAmt));
                nowPageRow++;
                // 非尾筆資料 且下一筆為不同縣市主辦行或分行;當頁筆數達上限時換頁
                if (cnt != lines.size()) {
                    // 不同分行處理
                    if (!dCllbr.equals(lines.get(cnt).substring(10, 13))) {
                        newCllbrSub();
                    }
                    // 不同縣市主辦行處理
                    if (!dPbrno.equals(lines.get(cnt).substring(0, 3))) {
                        newCllbrSub();
                        toC063FileFooter();
                        nowPageRow = nowPageRow + 6;
                        if (nowPageRow != PAGE_MAX_ROW) {
                            dPbrno = lines.get(cnt).substring(0, 3);
                            dDate = lines.get(cnt).substring(3, 10);
                            pageCnt = 1;
                            toC063FileHeader(1);
                            nowPageRow = 8;
                        }
                    }
                    // 當頁筆數達上限時換新頁
                    if (nowPageRow >= PAGE_MAX_ROW) {
                        pageCnt++;
                        toC063FileHeader(1);
                        nowPageRow = 8;
                    }
                }
            }
            // 寫頁尾
            newCllbrSub();
            toC063FileFooter();
        }
        try {
            textFile.writeFileContent(outputFilePath, fileC063Contents, CHARSET);
            upload(outputFilePath, "RPT", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void toC063FileHeader(int newPageFg) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "toC063FileHeader .... ");

        for (int i = 1; i <= HEADER_ROW; i++) {
            sb = new StringBuilder();
            switch (i) {
                case 1:
                    if (i == newPageFg) {
                        sb.append(pageSeparator);
                    } else {
                        sb.append("");
                    }
                    fileC063Contents.add(sb.toString());
                    break;
                case 3:
                    sb.append("                             綜所稅退稅憑單日報表－明細檔 ");
                    fileC063Contents.add(sb.toString());
                    break;
                case 4:
                    sb.append("  縣市主辦行：  ");
                    sb.append(dPbrno);
                    sb.append("                                                 報表名稱： ");
                    sb.append("C063 ");
                    fileC063Contents.add(sb.toString());
                    break;
                case 5:
                    sb.append("  代付日期：   ");
                    String dateYY = dDate.substring(0, 3);
                    String dateMM = dDate.substring(3, 5);
                    String dateDD = dDate.substring(5, 7);
                    sb.append(dateYY + "/" + dateMM + "/" + dateDD);
                    sb.append("                                            頁　　數： ");
                    sb.append(pageCnt);
                    fileC063Contents.add(sb.toString());
                    break;
                case 7:
                    sb.append("  代付分行   銷帳編號          交易日期   時間          金額   帳務別  備註 ");
                    fileC063Contents.add(sb.toString());
                    break;
                default:
                    if (i == HEADER_ROW) {
                        sb.append(
                                " ==========================================================================================");
                    } else {
                        sb.append("");
                    }
                    fileC063Contents.add(sb.toString());

                    break;
            }
        }
    }

    private void newCllbrSub() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "newCllbrSub .... ");
        //        if (subCnt == 0) {
        //            return;
        //        }
        nowPageRow = nowPageRow + 4;
        if (nowPageRow > PAGE_MAX_ROW) {
            toC063FileHeader(1);
            nowPageRow = 8 + 4;
        }
        fileC063Contents.add("");
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 9));
        sb.append(formatUtil.padX(" 分行小計筆數 :", 18));
        sb.append(reportUtil.customFormat("" + subCnt, "ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 10));
        sb.append(formatUtil.padX("     小計金額 :", 18));
        sb.append(formatUtil.padX("", 2));
        sb.append(reportUtil.customFormat("" + subAmt, "Z,ZZZ,ZZZ,ZZZ,ZZ9"));
        fileC063Contents.add(sb.toString());
        fileC063Contents.add("");
        // 換不同分行 重新計算分行小計
        subCnt = 0;
        subAmt = BigDecimal.ZERO;
    }

    private void toC063FileFooter() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "toC063FileFooter .... ");

        // 寫表尾
        sb = new StringBuilder();
        sb.append(reportUtil.makeGate("=", 90));
        fileC063Contents.add(sb.toString());
        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 9));
        sb.append(formatUtil.padX(" 總筆數 :", 18));
        sb.append(reportUtil.customFormat("" + totalCnt, "ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 10));
        sb.append(formatUtil.padX("     總金額 :", 18));
        sb.append(formatUtil.padX("", 2));
        sb.append(reportUtil.customFormat("" + totalAmt, "Z,ZZZ,ZZZ,ZZZ,ZZ9"));
        fileC063Contents.add(sb.toString());

        sb = new StringBuilder();
        fileC063Contents.add("");
        fileC063Contents.add("");
        fileC063Contents.add("");

        sb = new StringBuilder();
        sb.append(formatUtil.padX("", 38));
        sb.append(formatUtil.padX(" 經　辦： ", 10));
        sb.append(formatUtil.padX("", 32));
        sb.append(formatUtil.padX(" 主　管： ", 10));
        fileC063Contents.add(sb.toString());

        // 總筆數金額 重新計算 尾筆資料或換縣市主辦行
        totalCnt = 0;
        totalAmt = BigDecimal.ZERO;
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
