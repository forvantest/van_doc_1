/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.C066;
import com.bot.ncl.dto.entities.CldtlbyCEERangeAscPCRSTBus;
import com.bot.ncl.jpa.svc.CldtlService;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.date.DateUtil;
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
@Component("C066Lsnr")
@Scope("prototype")
public class C066Lsnr extends BatchListenerCase<C066> {

    @Autowired private CldtlService cldtlService;
    @Autowired private Parse parse;
    @Autowired private FormatUtil formatUtil;
    @Autowired private TextFileUtil textFile;
    @Autowired private DateUtil dateUtil;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    private C066 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> labelMap;
    private Map<String, String> textMap;

    private static final String FILE_NAME = "CL-BH-C066";

    private static final String CHARSET = "UTF-8";
    private static final String CHARSET_BIG5 = "Big5";
    private static final String CONVF_RPT = "RPT";
    private static final String PATH_SEPARATOR = File.separator;

    private String filePath;

    private List<String> fileContents;

    @Autowired private ReportUtil reportUtil;
    private int page;
    private int pageCnt;
    private String processDate;
    private String tbsdy;

    @Override
    public void onApplicationEvent(C066 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C066Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(C066 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C066Lsnr run()");
        init(event);
        queryCldtl(event);
        batchResponse();
    }

    private void init(C066 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C066Lsnr init");
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();

        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        filePath = fileDir + CONVF_RPT + PATH_SEPARATOR + processDate + PATH_SEPARATOR + FILE_NAME;
        textFile.deleteFile(filePath);

        fileContents = new ArrayList<>();

        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C066Lsnr" + processDate);
    }

    private void queryCldtl(C066 event) {
        int lmndy = event.getAggregateBuffer().getTxCom().getLmndy();
        int totcnt = 0;
        BigDecimal totamt = new BigDecimal(0);
        BigDecimal cntBigDeciaml;
        String YYYSysDate = processDate.substring(0, 3);
        String MMSysDate = processDate.substring(3, 5);
        String DDSysDate = processDate.substring(5, 7);
        String rptYYY = processDate.substring(0, 3);
        String rptMM = processDate.substring(3, 5);
        String date = YYYSysDate + "/" + MMSysDate + "/" + DDSysDate;
        List<CldtlbyCEERangeAscPCRSTBus> cldtlbyCEERangeAscPCRSTBusList =
                cldtlService.findbyCEERangeAscPCRST(
                        "121444",
                        lmndy,
                        parse.string2Integer(processDate),
                        0,
                        0,
                        Integer.MAX_VALUE);

        if (Objects.isNull(cldtlbyCEERangeAscPCRSTBusList)
                || cldtlbyCEERangeAscPCRSTBusList.isEmpty()) {
            if (fileContents.isEmpty()) {
                fileContents.add("\u000C");
                setContents(); // 寫資料
                pbrnoTotal();
            }
        } else {
            int cnt = 0;
            pageCnt = 0;
            page = 1;
            DecimalFormat decimalFormat = new DecimalFormat("#,###");
            BigDecimal amt = new BigDecimal(0);
            if (cldtlbyCEERangeAscPCRSTBusList.size() == 1) {
                int sitdate = cldtlbyCEERangeAscPCRSTBusList.get(0).getSitdate();
                String YYYSitdate = parse.decimal2String(sitdate, 8, 0).substring(1, 4);
                String MMSitdate = parse.decimal2String(sitdate, 8, 0).substring(4, 6);
                String DDSitdate = parse.decimal2String(sitdate, 8, 0).substring(6, 8);
                String sitdateFormat = YYYSitdate + "/" + MMSitdate + "/" + DDSitdate;
                cnt = 1;
                totcnt = 1;
                BigDecimal amtFirst = cldtlbyCEERangeAscPCRSTBusList.get(0).getAmt();
                totamt = totamt.add(amtFirst);
                cntBigDeciaml = BigDecimal.valueOf(cnt);

                StringBuilder stringBuilder1 = new StringBuilder();
                stringBuilder1.append(formatUtil.padX(" ", 4));
                stringBuilder1.append(formatUtil.padX(sitdateFormat, 10));
                stringBuilder1.append(formatUtil.padX(" ", 3));
                stringBuilder1.append(formatUtil.padX(decimalFormat.format(amtFirst), 15));
                stringBuilder1.append(formatUtil.padX(" ", 3));
                stringBuilder1.append(formatUtil.padX(decimalFormat.format(cntBigDeciaml), 12));
                fileContents.add(formatUtil.padX(stringBuilder1.toString(), 158));

                StringBuilder stringBuilder2 = new StringBuilder();
                stringBuilder2.append(formatUtil.padX(" ", 1));
                fileContents.add(formatUtil.padX(stringBuilder2.toString(), 158));
            } else {
                for (int i = 0; i < cldtlbyCEERangeAscPCRSTBusList.size(); i++) {
                    String pbrno =
                            cldtlbyCEERangeAscPCRSTBusList.get(i).getRcptid().substring(4, 7);
                    int pbrnoInt = parse.string2Integer(pbrno);
                    int sitdate = cldtlbyCEERangeAscPCRSTBusList.get(i).getSitdate();
                    String pbrnoCompare = "";
                    int pbrnoCompareInt = 0;
                    int sitdateCompare = 0;
                    if (i + 1 < cldtlbyCEERangeAscPCRSTBusList.size()) {
                        pbrnoCompare =
                                cldtlbyCEERangeAscPCRSTBusList
                                        .get(i + 1)
                                        .getRcptid()
                                        .substring(4, 7);
                        pbrnoCompareInt = parse.string2Integer(pbrnoCompare);
                        sitdateCompare = cldtlbyCEERangeAscPCRSTBusList.get(i + 1).getSitdate();
                    }
                    if (pbrnoInt == pbrnoCompareInt && sitdate == sitdateCompare) {
                        cnt++;
                        totcnt++;
                        BigDecimal amtsingle = cldtlbyCEERangeAscPCRSTBusList.get(i).getAmt();
                        amt = amt.add(amtsingle);
                        totamt = totamt.add(amtsingle);
                    } else {
                        int wkcCllbr = 0;
                        int cllbr = cldtlbyCEERangeAscPCRSTBusList.get(i).getCllbr();

                        BigDecimal amtsingle = cldtlbyCEERangeAscPCRSTBusList.get(i).getAmt();
                        String sitdateString = parse.decimal2String(sitdate, 8, 0);
                        String YYYSitdate = sitdateString.substring(1, 4);
                        String MMSitdate = sitdateString.substring(4, 6);
                        String DDSitdate = sitdateString.substring(6, 8);
                        String sitdateFormat = YYYSitdate + "/" + MMSitdate + "/" + DDSitdate;

                        if (pageCnt >= 58) {
                            changePage(sitdateFormat, YYYSitdate, MMSitdate, pbrno);
                        } else if (pbrnoCompare.equals(pbrno) && totcnt != 0) {
                            changePage(sitdateFormat, YYYSitdate, MMSitdate, pbrno);
                        } else if (cllbr != wkcCllbr && totcnt != 0) {
                            reportFormatPageHeading(date, rptYYY, rptMM, pbrno);
                        }

                        cnt++;
                        totcnt++;
                        pageCnt++;
                        cntBigDeciaml = BigDecimal.valueOf(cnt);
                        amt = amt.add(amtsingle);
                        totamt = totamt.add(amtsingle);
                        StringBuilder stringBuilder1 = new StringBuilder();
                        stringBuilder1.append(formatUtil.padX(" ", 4));
                        stringBuilder1.append(formatUtil.padX(sitdateFormat, 10));
                        stringBuilder1.append(formatUtil.padX(" ", 3));
                        stringBuilder1.append(formatUtil.padX(decimalFormat.format(amt), 15));
                        stringBuilder1.append(formatUtil.padX(" ", 3));
                        stringBuilder1.append(
                                formatUtil.padX(decimalFormat.format(cntBigDeciaml), 12));
                        fileContents.add(formatUtil.padX(stringBuilder1.toString(), 158));

                        StringBuilder stringBuilder2 = new StringBuilder();
                        stringBuilder2.append(formatUtil.padX(" ", 1));
                        fileContents.add(formatUtil.padX(stringBuilder2.toString(), 158));

                        cnt = 0;
                        amt = new BigDecimal(0);
                    }

                    if (fileContents.size() >= 10000) {
                        writeFile();
                        fileContents = new ArrayList<>();
                    }
                }
            }
        }
        writeFile();
        reportFormatControlFooting(totamt, totcnt);
    }

    private void setContents() {
        StringBuilder sb = new StringBuilder();
        sb.append("    ");
        sb.append(formatUtil.padX("", 3)); // 代付分行
        sb.append("         ");
        sb.append(reportUtil.customFormat("0", "ZZ,ZZZ,ZZZ,ZZ9"));
        sb.append("   ");
        sb.append(reportUtil.customFormat("0", "ZZ,ZZZ,ZZ9"));
        sb.append("   ");
        sb.append(" "); // 備註 擺空白
        fileContents.add(sb.toString());
    }

    private void pbrnoTotal() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "pbrnoTotal .... ");
        fileContents.add("");
        StringBuilder sb = new StringBuilder();
        sb.append(reportUtil.makeGate("-", 80));
        fileContents.add(sb.toString());
        sb = new StringBuilder();
        sb.append("      總　計 ");
        sb.append(reportUtil.customFormat("0", "Z,ZZZ,ZZZ,ZZZ,ZZ9"));
        sb.append(formatUtil.padX("", 3));
        sb.append(reportUtil.customFormat("0", "ZZ,ZZZ,ZZ9"));
        fileContents.add(sb.toString());
        fileContents.add("");
    }

    private void changePage(String date, String YYY, String MM, String pbrno) {
        page++;
        pageCnt = 0;
        reportFormatPageHeading(date, YYY, MM, pbrno);
    }

    private void writeFile() {
        try {
            textFile.writeFileContent(filePath, fileContents, CHARSET_BIG5);
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
        // event.setPeripheryRequest();
    }

    private void reportFormatPageHeading(String date, String YYY, String MM, String pbrno) {
        StringBuilder stringBuilder1 = new StringBuilder();
        stringBuilder1.append(formatUtil.padX(" ", 12));
        stringBuilder1.append(formatUtil.padX("綜所稅退稅憑單報表－第二付款行累計兌付", 40));
        stringBuilder1.append(formatUtil.padX(" ", 6));
        stringBuilder1.append(formatUtil.padX("印表日： ", 10));
        stringBuilder1.append(formatUtil.padX(date, 10));
        fileContents.add(formatUtil.padX(stringBuilder1.toString(), 158));

        StringBuilder stringBuilder2 = new StringBuilder();
        stringBuilder2.append(formatUtil.padX(" ", 1));
        fileContents.add(formatUtil.padX(stringBuilder2.toString(), 158));

        StringBuilder stringBuilder3 = new StringBuilder();
        stringBuilder3.append(formatUtil.padX(" ", 2));
        stringBuilder3.append(formatUtil.padX("代付月份： ", 12));
        stringBuilder3.append(formatUtil.padX(YYY, 3));
        stringBuilder3.append(formatUtil.padX(" 年 ", 4));
        stringBuilder3.append(formatUtil.padX(MM, 2));
        stringBuilder3.append(formatUtil.padX(" 月", 4));
        stringBuilder3.append(formatUtil.padX(" ", 31));
        stringBuilder3.append(formatUtil.padX("頁　次：", 10));
        stringBuilder3.append(formatUtil.padX("" + page, 3));
        fileContents.add(formatUtil.padX(stringBuilder3.toString(), 158));

        StringBuilder stringBuilder4 = new StringBuilder();
        stringBuilder4.append(formatUtil.padX(" ", 2));
        stringBuilder4.append(formatUtil.padX("縣市主辦行：", 14));
        stringBuilder4.append(formatUtil.padX(pbrno, 3));
        stringBuilder4.append(formatUtil.padX(" ", 39));
        stringBuilder4.append(formatUtil.padX("報表名稱：", 12));
        stringBuilder4.append(formatUtil.padX("C066", 4));
        fileContents.add(formatUtil.padX(stringBuilder4.toString(), 158));

        StringBuilder stringBuilder5 = new StringBuilder();
        stringBuilder5.append(formatUtil.padX(" ", 3));
        stringBuilder5.append(formatUtil.padX("代付日期", 10));
        stringBuilder5.append(formatUtil.padX(" ", 10));
        stringBuilder5.append(formatUtil.padX("總金額", 8));
        stringBuilder5.append(formatUtil.padX(" ", 5));
        stringBuilder5.append(formatUtil.padX("總件數", 14));
        stringBuilder5.append(formatUtil.padX("備註", 6));
        fileContents.add(formatUtil.padX(stringBuilder5.toString(), 158));

        StringBuilder stringBuilder6 = new StringBuilder();
        stringBuilder6.append(
                formatUtil.padX(
                        "--------------------------------------------------------------------------------",
                        80));
        fileContents.add(formatUtil.padX(stringBuilder6.toString(), 158));
    }

    private void reportFormatControlFooting(BigDecimal totamt, int totcnt) {
        StringBuilder stringBuilder1 = new StringBuilder();
        stringBuilder1.append(
                formatUtil.padX(
                        "--------------------------------------------------------------------------------",
                        80));
        fileContents.add(formatUtil.padX(stringBuilder1.toString(), 158));

        StringBuilder stringBuilder2 = new StringBuilder();
        stringBuilder2.append(formatUtil.padX(" ", 5));
        stringBuilder2.append(formatUtil.padX("總　計", 8));
        stringBuilder2.append(formatUtil.padX("" + totamt, 18));
        stringBuilder2.append(formatUtil.padX(" ", 3));
        stringBuilder2.append(formatUtil.padX("" + totcnt, 12));
        fileContents.add(formatUtil.padX(stringBuilder2.toString(), 158));
    }

    private void batchResponse() {
        Map<String, String> responseTextMap = new HashMap<>(textMap);
        responseTextMap.put("RPTNAME", FILE_NAME);

        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", responseTextMap);
    }
}
