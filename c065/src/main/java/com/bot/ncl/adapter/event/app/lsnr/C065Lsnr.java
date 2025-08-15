/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.C065;
import com.bot.ncl.dto.entities.CldtlbyCEAscPCRSTBus;
import com.bot.ncl.jpa.svc.CldtlService;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.string.StringUtil;
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
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("C065Lsnr")
@Scope("prototype")
public class C065Lsnr extends BatchListenerCase<C065> {
    @Autowired private CldtlService cldtlService;
    @Autowired private TextFileUtil textFile;
    @Autowired private Parse parse;
    @Autowired private FormatUtil formatUtil;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    @Autowired private StringUtil strutil;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CONVF_RPT = "RPT";
    private static final String PATH_SEPARATOR = File.separator;

    private Map<String, String> labelMap;
    private Map<String, String> textMap;

    private C065 event;

    private static final String FILE_NAME = "CL-BH-C065";
    private static final String CHARSET = "UTF-8";
    private static final String CHARSET_Big5 = "Big5";

    private String filePath;

    private List<String> fileContents;

    private String processDate;
    private String tbsdy;
    private BigDecimal totcnt = new BigDecimal(0);
    private BigDecimal totamt = new BigDecimal(0);

    private int pageIndex;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(C065 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C065Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(C065 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C065Lsnr run()");

        init(event);

        queryCldtl(event);

        // 新系統為分散式架構,須將本地產製的檔案上傳到FSAP共用空間
        copyToFtp();
        batchResponse();
    }

    private void init(C065 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C065Lsnr init");
        this.event = event;

        fileContents = new ArrayList<>();

        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();

        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "batchDate" + processDate);

        filePath = fileDir + CONVF_RPT + PATH_SEPARATOR + processDate + PATH_SEPARATOR + FILE_NAME;

        textFile.deleteFile(filePath);

        pageIndex = 0;

        //  測試用日期
        if (processDate == null) {
            processDate = "01120704";
        }
    }

    private void queryCldtl(C065 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C065Lsnr queryCldtl");

        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "tbsdy = {}", tbsdy);

        String YYYBatchDate = processDate.substring(0, 3);
        String MMBatchDate = processDate.substring(3, 5);
        String DDBatchDate = processDate.substring(5, 7);
        String date = YYYBatchDate + "/" + MMBatchDate + "/" + DDBatchDate;

        if (processDate.isBlank()) {
            return;
        }

        int batchDateInt = parse.string2Integer(processDate);

        List<CldtlbyCEAscPCRSTBus> cldtlbyCEAscPCRSTBusList =
                cldtlService.findbyCEAscPCRST("121444", batchDateInt, 0, 0, Integer.MAX_VALUE);

        if (Objects.isNull(cldtlbyCEAscPCRSTBusList) || cldtlbyCEAscPCRSTBusList.isEmpty()) {
            if (fileContents.isEmpty()) {
                int page = 1;
                int cnt = 0;
                BigDecimal amt = new BigDecimal(0);
                fileContents.add("\u000C");
                reportFormatPageHeading("", date, page);

                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(formatUtil.padX(" ", 1));
                stringBuilder.append(formatUtil.padX("         NO DATA !!", 40));
                fileContents.add(formatUtil.padX(stringBuilder.toString(), 158));

                reportFormatControlFooting(cnt, amt);
                writeFile();
            }
        } else {
            ApLogHelper.info(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "C065Lsnr CldtlList=" + cldtlbyCEAscPCRSTBusList);
            int pageCnt = 0;
            int page = 1;
            int cnt = 0;
            BigDecimal amt = new BigDecimal(0);

            for (CldtlbyCEAscPCRSTBus cldtlbyCEAscPCRSTBus : cldtlbyCEAscPCRSTBusList) {
                pageCnt++;

                int sitdate = cldtlbyCEAscPCRSTBus.getSitdate();
                int time = cldtlbyCEAscPCRSTBus.getTime();
                amt = cldtlbyCEAscPCRSTBus.getAmt();
                String sitdateString = parse.decimal2String(sitdate, 7, 0);
                String rcptid = formatUtil.padX(cldtlbyCEAscPCRSTBus.getRcptid(), 26);
                String userdata = cldtlbyCEAscPCRSTBus.getUserdata();
                String txtype = cldtlbyCEAscPCRSTBus.getTxtype();
                String pbrno = rcptid.substring(4, 7);
                String userdataZeroToSeventh =
                        strutil.isBlank(userdata) ? "0" : strutil.substr(userdata, 0, 7);
                String userdataEighthToThirtythree = "";
                if (!strutil.isBlank(userdata)) {
                    userdataEighthToThirtythree = userdata.substring(7);
                }
                String YYYSitdate = sitdateString.substring(0, 3);
                String MMSitdate = sitdateString.substring(3, 5);
                String DDSitdate = sitdateString.substring(5, 7);
                String sitdateFormat = YYYSitdate + "/" + MMSitdate + "/" + DDSitdate;
                DecimalFormat decimalFormat = new DecimalFormat("#,###");

                if (cnt == 0) {
                    reportFormatPageHeading(pbrno, date, page);
                }
                if (pageCnt >= 58) {
                    page++;
                    pageCnt = 0;
                    reportFormatPageHeading(pbrno, date, page);
                }

                if (parse.isNumeric(userdataZeroToSeventh)
                        && userdataEighthToThirtythree.isBlank()) {
                    cnt++;
                    totcnt = totcnt.add(new BigDecimal(1));

                    StringBuilder stringBuilder1 = new StringBuilder();
                    stringBuilder1.append(formatUtil.padX(" ", 1));
                    fileContents.add(formatUtil.padX(stringBuilder1.toString(), 158));

                    StringBuilder stringBuilder2 = new StringBuilder();
                    stringBuilder2.append(formatUtil.padX(" ", 1));
                    stringBuilder2.append(formatUtil.padX(userdataZeroToSeventh, 11));
                    stringBuilder2.append(formatUtil.padX(rcptid, 18));
                    stringBuilder2.append(formatUtil.padX(sitdateFormat, 10));
                    stringBuilder2.append(formatUtil.padX("" + time, 9));
                    stringBuilder2.append(formatUtil.padX(decimalFormat.format(amt), 14));
                    totamt = totamt.add(amt);
                    stringBuilder2.append(formatUtil.padX(txtype, 7));
                    stringBuilder2.append(formatUtil.padX(userdata, 40));
                    fileContents.add(formatUtil.padX(stringBuilder2.toString(), 158));
                }

                if (fileContents.size() >= 10000) {
                    writeFile();
                    fileContents = new ArrayList<>();
                }
            }
            pageIndex++;
            writeFile();
            reportFormatControlFooting(cnt, amt);
        }
    }

    private void writeFile() {
        try {
            textFile.writeFileContent(filePath, fileContents, CHARSET_Big5);
            upload(filePath, "RPT", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
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

    private void moveErrorResponse(LogicException e) {
        //                event.setPeripheryRequest();
    }

    private void copyToFtp() {
        String filePath = fileDir + FILE_NAME;
        // TODO: copy PUTH from local to FTP
    }

    private void reportFormatPageHeading(String pbrno, String date, int page) {
        fileContents.add("");
        fileContents.add("");
        StringBuilder stringBuilder1 = new StringBuilder();
        stringBuilder1.append(formatUtil.padX(" ", 28));
        stringBuilder1.append(formatUtil.padX("綜所稅退稅憑單日報表－第二付款行明細檔", 48));
        fileContents.add(formatUtil.padX(stringBuilder1.toString(), 158));

        StringBuilder stringBuilder3 = new StringBuilder();
        stringBuilder3.append(formatUtil.padX(" 縣市主辦行： ", 14));
        stringBuilder3.append(formatUtil.padX(pbrno, 3));
        stringBuilder3.append(formatUtil.padX(" ", 50));
        stringBuilder3.append(formatUtil.padX("報表名稱： ", 12));
        stringBuilder3.append(formatUtil.padX("C065", 8));
        fileContents.add(formatUtil.padX(stringBuilder3.toString(), 158));

        StringBuilder stringBuilder4 = new StringBuilder();
        stringBuilder4.append(formatUtil.padX(" 代付日期： ", 12));
        stringBuilder4.append(formatUtil.padX(date, 9));
        stringBuilder4.append(formatUtil.padX(" ", 44));
        stringBuilder4.append(formatUtil.padX("頁　　數： ", 12));
        stringBuilder4.append(formatUtil.padX("" + page, 4));
        fileContents.add(formatUtil.padX(stringBuilder4.toString(), 158));

        fileContents.add("");
        StringBuilder stringBuilder5 = new StringBuilder();
        stringBuilder5.append(formatUtil.padX(" ", 1));
        stringBuilder5.append(formatUtil.padX("付款銀行", 10));
        stringBuilder5.append(formatUtil.padX(" ", 2));
        stringBuilder5.append(formatUtil.padX("銷帳編號", 16));
        stringBuilder5.append(formatUtil.padX(" ", 2));
        stringBuilder5.append(formatUtil.padX("交易日期", 10));
        stringBuilder5.append(formatUtil.padX("時間", 6));
        stringBuilder4.append(formatUtil.padX(" ", 9));
        stringBuilder5.append(formatUtil.padX("金額", 10));
        stringBuilder5.append(formatUtil.padX("帳務別", 10));
        stringBuilder5.append(formatUtil.padX("備註", 6));
        fileContents.add(formatUtil.padX(stringBuilder5.toString(), 158));

        StringBuilder stringBuilder6 = new StringBuilder();
        stringBuilder6.append(
                formatUtil.padX(
                        "=================================================="
                                + "========================================",
                        90));
        fileContents.add(formatUtil.padX(stringBuilder6.toString(), 158));
    }

    private void reportFormatControlFooting(int cnt, BigDecimal amt) {
        DecimalFormat decimalFormat = new DecimalFormat("#,###");

        StringBuilder stringBuilder1 = new StringBuilder();
        //        stringBuilder1.append(
        //                formatUtil.padX(
        //                        "=================================================="
        //                                + "========================================",
        //                        90));
        fileContents.add("");

        StringBuilder stringBuilder3 = new StringBuilder();
        stringBuilder3.append(formatUtil.padX(" ", 10));
        stringBuilder3.append(formatUtil.padX("銀行小計筆數:   ", 20));
        stringBuilder3.append(formatUtil.padX(decimalFormat.format(cnt), 10));
        stringBuilder3.append(formatUtil.padX(" ", 5));
        stringBuilder3.append(formatUtil.padX("小計金額:     ", 20));
        stringBuilder3.append(formatUtil.padX(decimalFormat.format(amt), 20));
        fileContents.add(formatUtil.padX(stringBuilder3.toString(), 158));

        StringBuilder stringBuilder4 = new StringBuilder();
        stringBuilder4.append(
                formatUtil.padX(
                        "=================================================="
                                + "========================================",
                        90));
        fileContents.add(formatUtil.padX(stringBuilder4.toString(), 158));

        fileContents.add("");
        StringBuilder stringBuilder5 = new StringBuilder();
        stringBuilder5.append(formatUtil.padX(" ", 10));
        stringBuilder5.append(formatUtil.padX("總筆數:", 18));
        stringBuilder5.append(formatUtil.padX(decimalFormat.format(totcnt), 8));
        stringBuilder5.append(formatUtil.padX(" ", 9));
        stringBuilder5.append(formatUtil.padX("總金額:", 20));
        stringBuilder5.append(formatUtil.padX(decimalFormat.format(totamt), 18));
        fileContents.add(formatUtil.padX(stringBuilder5.toString(), 158));

        StringBuilder stringBuilder6 = new StringBuilder();
        stringBuilder6.append(formatUtil.padX(" ", 1));
        fileContents.add(formatUtil.padX(stringBuilder6.toString(), 158));
        fileContents.add("");
        fileContents.add("");
        StringBuilder stringBuilder7 = new StringBuilder();
        stringBuilder7.append(formatUtil.padX(" ", 39));
        stringBuilder7.append(formatUtil.padX("經　辦：", 10));
        stringBuilder7.append(formatUtil.padX(" ", 32));
        stringBuilder7.append(formatUtil.padX("主　管：", 10));
        fileContents.add(formatUtil.padX(stringBuilder7.toString(), 158));
    }

    private void batchResponse() {
        Map<String, String> responseTextMap = new HashMap<>(textMap);
        responseTextMap.put("RPTNAME", FILE_NAME);

        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", responseTextMap);
    }
}
