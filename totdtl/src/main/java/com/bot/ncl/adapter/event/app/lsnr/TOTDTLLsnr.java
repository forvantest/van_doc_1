/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.TOTDTL;
import com.bot.ncl.dto.entities.CldtlEntdyHcodeBus;
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
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
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
@Component("TOTDTLLsnr")
@Scope("prototype")
public class TOTDTLLsnr extends BatchListenerCase<TOTDTL> {
    @Autowired private TextFileUtil textFile;
    @Autowired private Parse parse;
    @Autowired private CldtlService cldtlService;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ExternalSortUtil externalSortUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> labelMap;
    private Map<String, String> textMap;

    private static final String FILE_NAME = "CL-BH-003";
    private static final String CHARSET = "UTF-8";
    private static final String CHARSET_BIG5 = "Big5";
    private static final String CONVF_RPT = "RPT";
    private static final String PATH_SEPARATOR = File.separator;
    private static final String FILE_TMP_NAME = "TMPTOTDTL";
    private String outputFilePath; // C073路徑
    private TOTDTL event;
    private List<String> fileTmpContents; // 檔案內容
    private List<String> fileTOTDTLContents; // 檔案內容
    private StringBuilder sb;
    private String tmpFilePath; // 暫存檔路徑
    private String processDate; // 批次日期(民國年yyyymmdd)
    private String tbsdy;
    private Boolean noData = false;
    private int entdy = 0;
    private final int pageCnts = 100000;
    private int pageIndex = 0;
    private int ixcllbr = 0;
    private int ixpbrno = 0;
    private int nowPageRow = 0;
    private String dpbrno;
    private String dcllbr;
    private String dcode;
    private String dtime;
    private String damt;
    private String drcptid;
    private String dtxtype;
    private String dtrmno;
    private String dtlrno;
    private String dactno;
    private String duserdata;
    private String dentdy;
    private String dfee;
    private String wtpbrno;
    private String wkpbrno;
    private int icount = 0;
    private int page = 1;
    private String hdpbrno;
    private int itotalcount1 = 0;
    private BigDecimal itotalamt1 = BigDecimal.ZERO;
    private BigDecimal itotalfee1 = BigDecimal.ZERO;
    private int itotalcount2 = 0;
    private BigDecimal itotalamt2 = BigDecimal.ZERO;
    private BigDecimal itotalfee2 = BigDecimal.ZERO;
    private int itotalcount3 = 0;
    private BigDecimal itotalamt3 = BigDecimal.ZERO;
    private BigDecimal itotalfee3 = BigDecimal.ZERO;
    private final DecimalFormat dFormatNum00 = new DecimalFormat("#,##0.  ");
    // BB是空白、9是數字0(補0) 等於程式的0、Z是空白 等於程式的#
    private final DecimalFormat dFormatNum99 = new DecimalFormat("#,###.00");
    private String wkcode;
    private String wtcode;
    private String wtcllbr;
    private String wkcllbr;
    BigDecimal wtdamt = BigDecimal.ZERO;
    BigDecimal wtdfee = BigDecimal.ZERO;
    BigDecimal wtdamt2 = BigDecimal.ZERO;
    BigDecimal wtdfee2 = BigDecimal.ZERO;
    BigDecimal wtdamt3 = BigDecimal.ZERO;
    BigDecimal wtdfee3 = BigDecimal.ZERO;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(TOTDTL event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "TOTDTLLsnr");
        this.beforRun(event);
    }

    @SneakyThrows
    protected void run(TOTDTL event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "TOTDTLLsnr run()");
        init(event);
        queryCldtl();
        textFile.deleteFile(tmpFilePath);

        batchResponse();
    }

    private void init(TOTDTL event) {
        this.event = event;

        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();

        processDate = formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1);
        // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        entdy = parse.string2Integer(processDate);

        tmpFilePath =
                fileDir + "DATA" + File.separator + processDate + File.separator + FILE_TMP_NAME;
        outputFilePath =
                fileDir + CONVF_RPT + PATH_SEPARATOR + processDate + PATH_SEPARATOR + FILE_NAME;
        fileTmpContents = new ArrayList<>();
        fileTOTDTLContents = new ArrayList<>();
        textFile.deleteFile(tmpFilePath);
        textFile.deleteFile(outputFilePath);
    }

    private void queryCldtl() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "TOTDTL queryCldtl");
        List<CldtlEntdyHcodeBus> lCldtl =
                cldtlService.findEntdyHcode(entdy, 0, pageIndex, pageCnts);
        // 寫資料到檔案中
        try {
            setTmpData(lCldtl);
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        // 排序檔案資料
        sortfile();
        // 產C073
        toWriteTOTDTLFile();
        textFile.deleteFile(tmpFilePath);
    }

    private void setTmpData(List<CldtlEntdyHcodeBus> lCldtl) throws IOException {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C074Lsnr setTmpData");
        // 如果有資料，塞入資料給新檔案並且給予固定位置
        if (lCldtl != null) {
            for (CldtlEntdyHcodeBus tCldtl : lCldtl) {

                ixpbrno = tCldtl.getPbrno();
                ixcllbr = tCldtl.getCllbr();
                if (ixcllbr != ixpbrno) {
                    // 03 S-PBRNO	9(03)	主辦行	DB-CLMR-PBRNO
                    // 03 S-CODE	X(06)	代收類別	DB-CLDTL-CODE
                    // 03 S-CLLBR	9(03)	代收行	DB-CLDTL-CLLBR
                    // 03 S-DATE	9(07)	代收日	DB-CLDTL-DATE
                    // 03 S-TIME	9(06)	代收時間	DB-CLDTL-TIME
                    // 03 S-RCPTID	X(26)	銷帳號碼 	DB-CLDTL-RCPTID
                    // 03 S-AMT	9(10)	繳費金額 	DB-CLDTL-AMT
                    // 03 S-TXTYPE	X(01)	帳務別 	DB-CLDTL-TXTYPE
                    // 03 S-TRMNO	9(07)	櫃檯機編號 	DB-CLDTL-TRMNO
                    // 03 S-TLRNO	X(02)	櫃員號碼 	DB-CLDTL-TLRNO
                    // 03 S-ACTNO	X(12)	網銀語音繳款帳號	DB-CLDTL-ACTNO
                    // 03 S-USERDATA	X(40)	備註資料	DB-CLDTL-USERDATA
                    // 03 S-CFEE	9(06)V99	主辦行每筆給付代收行費用	DB-CLMR-CFEE2
                    sb = new StringBuilder();
                    sb.append(formatUtil.pad9("" + ixpbrno, 3));
                    sb.append(formatUtil.padX(tCldtl.getCode(), 6));
                    sb.append(formatUtil.pad9("" + tCldtl.getCllbr(), 3));
                    sb.append(formatUtil.pad9("" + tCldtl.getEntdy(), 7));
                    sb.append(formatUtil.pad9("" + tCldtl.getTime(), 6));
                    sb.append(formatUtil.padX(tCldtl.getRcptid(), 26));
                    sb.append(formatUtil.pad9("" + tCldtl.getAmt(), 10));
                    sb.append(formatUtil.padX(tCldtl.getTxtype(), 1));
                    sb.append(formatUtil.pad9("" + tCldtl.getTrmno(), 7));
                    sb.append(formatUtil.padX(tCldtl.getTlrno(), 2));
                    sb.append(formatUtil.padX(tCldtl.getActno(), 12));
                    sb.append(formatUtil.padX(tCldtl.getUserdata(), 40));
                    sb.append(
                            reportUtil
                                    .customFormat("" + tCldtl.getCfee2(), "999999.99")
                                    .replace(".", ""));
                    fileTmpContents.add(sb.toString());
                }
            }
        } else {
            fileTmpContents.add("");
            fileTmpContents.add("            NO DATA !!");
            fileTmpContents.add("");
            noData = true;
        }
        try {
            textFile.writeFileContent(tmpFilePath, fileTmpContents, CHARSET_BIG5);
            upload(tmpFilePath, "DATA", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "TOTDTLLsnr File OK");
    }

    private void sortfile() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "TOTDTL sortfile");
        if (noData) {
            return;
        }
        File tmpFile = new File(tmpFilePath);
        List<KeyRange> keyRanges = new ArrayList<>();
        // S-PBRNO S-CODE S-CLLBR S-RCPTID
        keyRanges.add(new KeyRange(1, 3, SortBy.ASC));
        keyRanges.add(new KeyRange(4, 6, SortBy.ASC));
        keyRanges.add(new KeyRange(10, 3, SortBy.ASC));
        keyRanges.add(new KeyRange(26, 26, SortBy.ASC));
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "keyRanges  = " + keyRanges);

        externalSortUtil.sortingFile(tmpFile, tmpFile, keyRanges, CHARSET_BIG5);
    }

    private String cutStringByByteLength(String s, int startIndex, int endIndex) {
        if (s != null && startIndex >= 0 && endIndex > startIndex) {
            byte[] b = s.getBytes(Charset.forName("BIG5"));
            if (startIndex >= b.length) {
                return "";
            } else {
                endIndex = Math.min(endIndex, b.length);
                byte[] newBytes = Arrays.copyOfRange(b, startIndex, endIndex);
                return new String(newBytes, Charset.forName("BIG5"));
            }
        } else {
            return s;
        }
    }

    private void toWriteTOTDTLFile() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "toWriteC074File toWriteTOTDTLFile");
        List<String> lines = textFile.readFileContent(tmpFilePath, CHARSET_BIG5);
        String YMD1 = Integer.toString(entdy);
        String YYY1 = YMD1.substring(0, 3);
        String MM1 = YMD1.substring(3, 5);
        String DD1 = YMD1.substring(5, 7);
        String data = YYY1 + "/" + MM1 + "/" + DD1;

        if (noData) {
            icount = 0;
            page = 1;
            hdpbrno = "000";
            fileTOTDTLContents.add("\u000c");
            hdsb(icount, page, data, hdpbrno);
            for (String detail : lines) {
                fileTOTDTLContents.add(detail);
            }

            edsb1(itotalcount1, itotalamt1, itotalfee1);
            edsb2(itotalcount2, itotalamt2, itotalfee2);
            edsb3(itotalcount3, itotalamt3, itotalfee3);

        } else {
            nowPageRow = 0;

            for (String detail : lines) {

                dpbrno = detail.substring(0, 3); // 0-3
                dcode = detail.substring(3, 9); // 3-9
                dcllbr = detail.substring(9, 12); // 9-12
                dentdy = detail.substring(12, 19); // 12-19
                dtime = detail.substring(19, 25); // 19-25
                drcptid = detail.substring(25, 51); // 25-51
                damt = detail.substring(51, 61); // 51-61
                dtxtype = detail.substring(61, 62); // 61-62
                dtrmno = detail.substring(62, 69); // 62-69
                dtlrno = detail.substring(69, 71); // 69-71
                dactno = detail.substring(71, 83); // 71-83
                duserdata = cutStringByByteLength(detail, 83, 123); // 83-123
                dfee =
                        cutStringByByteLength(detail, 123, 129)
                                + "."
                                + cutStringByByteLength(detail, 129, 131); // 123-131

                wtpbrno = dpbrno;
                wtcode = dcode;
                wtcllbr = dcllbr;
                // 當主辦行不同時印出
                if (!wtpbrno.equals(wkpbrno) && icount != 0) {

                    edsb1(itotalcount1, itotalamt1, itotalfee1);
                    edsb2(itotalcount2, itotalamt2, itotalfee2);

                    edsb3(itotalcount3, itotalamt3, itotalfee3);

                    page = 0;
                    itotalcount1 = 0;
                    itotalamt1 = BigDecimal.ZERO;
                    itotalfee1 = BigDecimal.ZERO;
                    itotalcount2 = 0;
                    itotalamt2 = BigDecimal.ZERO;
                    itotalfee2 = BigDecimal.ZERO;
                    itotalcount3 = 0;
                    itotalamt3 = BigDecimal.ZERO;
                    itotalfee3 = BigDecimal.ZERO;
                    icount = 0;
                    page++;
                }
                // code不同計算一筆
                if (!wtcode.equals(wkcode) && icount != 0) {
                    ApLogHelper.info(
                            log, false, LogType.NORMAL.getCode(), "itotalamt1  = " + itotalamt1);

                    edsb1(itotalcount1, itotalamt1, itotalfee1);
                    itotalcount1 = 0;
                    itotalamt1 = BigDecimal.ZERO;
                    itotalfee1 = BigDecimal.ZERO;
                    edsb2(itotalcount2, itotalamt2, itotalfee2);
                    itotalcount2 = 0;
                    itotalamt2 = BigDecimal.ZERO;
                    itotalfee2 = BigDecimal.ZERO;
                }
                if (!wtcllbr.equals(wkcllbr) && icount != 0) {
                    edsb2(itotalcount2, itotalamt2, itotalfee2);
                    itotalcount2 = 0;
                    itotalamt2 = BigDecimal.ZERO;
                    itotalfee2 = BigDecimal.ZERO;
                }
                // 當主辦行不相同
                if (!wtpbrno.equals(wkpbrno)) {
                    hdpbrno = dpbrno;
                    fileTOTDTLContents.add("\u000c");
                    hdsb(icount, page, data, hdpbrno);
                }
                // 超過52筆 需新增一頁
                if (wtpbrno.equals(wkpbrno) && icount > 52) {
                    icount++;
                    hdpbrno = dpbrno;
                    page++;
                    fileTOTDTLContents.add("\u000c");
                    hdsb(icount, page, data, hdpbrno);
                    icount = 0;
                }

                sb = new StringBuilder();
                sb.append(formatUtil.padX(" ", 2));
                sb.append(dcllbr);
                sb.append(formatUtil.padX(" ", 2));
                sb.append(dcode);
                sb.append(formatUtil.padX(" ", 2));
                sb.append(reportUtil.customFormat(dentdy, "Z99/99/99"));
                sb.append(formatUtil.padX(" ", 1));
                sb.append(dtime);
                sb.append(formatUtil.padX(" ", 2));
                sb.append(reportUtil.customFormat(damt, "Z,ZZZ,ZZZ,ZZ9"));
                sb.append(formatUtil.padX(" ", 2));
                sb.append(drcptid);
                sb.append(formatUtil.padX(" ", 1));
                sb.append(dtxtype);
                sb.append(formatUtil.padX(" ", 2));
                sb.append(dtrmno);
                sb.append(formatUtil.padX(" ", 2));
                sb.append(dtlrno);
                sb.append(formatUtil.padX(" ", 2));
                sb.append(dactno);
                sb.append(formatUtil.padX(" ", 2));
                sb.append(duserdata);

                fileTOTDTLContents.add(formatUtil.padX(sb.toString(), 170));

                wkpbrno = wtpbrno;
                wkcode = wtcode;
                wkcllbr = wtcllbr;

                icount++;

                wtdamt = parse.string2BigDecimal(damt);
                wtdfee = parse.string2BigDecimal(dfee);
                itotalcount1++;
                itotalamt1 = itotalamt1.add(wtdamt);
                itotalfee1 = itotalfee1.add(wtdfee);

                wtdamt2 = parse.string2BigDecimal(damt);
                wtdfee2 = parse.string2BigDecimal(dfee);
                itotalcount2++;
                itotalamt2 = itotalamt2.add(wtdamt2);
                itotalfee2 = itotalfee2.add(wtdfee2);

                wtdamt3 = parse.string2BigDecimal(damt);
                wtdfee3 = parse.string2BigDecimal(dfee);
                itotalcount3++;
                itotalamt3 = itotalamt3.add(wtdamt3);
                itotalfee3 = itotalfee3.add(wtdfee3);

                if (icount > 52) {
                    page++;
                }
            }
            edsb1(itotalcount1, itotalamt1, itotalfee1);
            edsb2(itotalcount2, itotalamt2, itotalfee2);
            edsb3(itotalcount3, itotalamt3, itotalfee3);
        }

        try {
            textFile.writeFileContent(outputFilePath, fileTOTDTLContents, CHARSET_BIG5);
            upload(outputFilePath, "RPT", "");

        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void moveErrorResponse(LogicException e) {
        //                event.setPeripheryRequest();
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

    private void hdsb(int icount, int page, String data, String hdpbrno) {

        // 表頭第一行
        StringBuilder hdsb = new StringBuilder();
        hdsb.append(formatUtil.padX(" ", 30));
        hdsb.append(formatUtil.padX("ONLINE COLLECT OPERATION DETAIL REPORT (BE COLLECTTED)", 54));
        hdsb.append(formatUtil.padX(" ", 24));
        hdsb.append(formatUtil.padX("FORM : C003", 12));
        // 放進來算一行
        fileTOTDTLContents.add(formatUtil.padX(hdsb.toString(), 170));
        fileTOTDTLContents.add("");

        // 表頭第二行
        StringBuilder hdsb2 = new StringBuilder();
        hdsb2.append(formatUtil.padX(" ", 1));
        hdsb2.append(formatUtil.padX("BRNO : ", 7));
        hdsb2.append(formatUtil.pad9(hdpbrno, 3));
        hdsb2.append(formatUtil.padX(" ", 4));
        hdsb2.append(formatUtil.padX("DATA DATE : ", 12));
        hdsb2.append(data);
        hdsb2.append(formatUtil.padX(" ", 72));
        hdsb2.append(formatUtil.padX("PAGE : ", 7));
        hdsb2.append(formatUtil.pad9("" + page, 3));
        // 放進來算一行
        fileTOTDTLContents.add(formatUtil.padX(hdsb2.toString(), 170));
        fileTOTDTLContents.add("");

        // 表頭第三行
        StringBuilder hdsb3 = new StringBuilder();
        hdsb3.append(formatUtil.padX(" ", 1));
        hdsb3.append(formatUtil.padX("CLLBR ", 6));
        hdsb3.append(formatUtil.padX("CODE   ", 7));
        hdsb3.append(formatUtil.padX(" DATE   ", 9));
        hdsb3.append(formatUtil.padX("  TIME  ", 8));
        hdsb3.append(formatUtil.padX("         AMOUNT ", 16));
        hdsb3.append(formatUtil.padX("      RCPTID     ", 17));
        hdsb3.append(formatUtil.padX(" ", 10));
        hdsb3.append(formatUtil.padX("KIND", 4));
        hdsb3.append(formatUtil.padX(" TRMNO", 6));
        hdsb3.append(formatUtil.padX(" TLRNO ", 7));
        hdsb3.append(formatUtil.padX("  ACTNO      ", 14));
        hdsb3.append(formatUtil.padX("      USERDATA  ", 16));

        // 放進來算一行
        fileTOTDTLContents.add(formatUtil.padX(hdsb3.toString(), 170));
    }

    private void edsb1(int itotalcount1, BigDecimal itotalamt1, BigDecimal itotalfee1) {
        // 表尾
        //        StringBuilder edsb = new StringBuilder();
        //        edsb.append(formatUtil.padX(" ", 1));
        //        edsb.append(
        //                formatUtil.padX(
        //
        // "==================================================================================================================================",
        //                        130));
        //        fileTOTDTLContents.add(formatUtil.padX(edsb.toString(), 170));

        fileTOTDTLContents.add("");
        // 表尾第一行
        StringBuilder edsb1 = new StringBuilder();
        edsb1.append(formatUtil.padX("**SUB", 5));
        edsb1.append(formatUtil.padX("    COUNT ", 10));
        edsb1.append(reportUtil.customFormat("" + itotalcount1, "ZZZ,ZZ9.BB"));
        edsb1.append(formatUtil.padX(" AMOUNT ", 8));
        edsb1.append(reportUtil.customFormat("" + itotalamt1, "Z,ZZZ,ZZZ,ZZZ,ZZ9.BB"));
        edsb1.append(formatUtil.padX("   FEE    ", 10));
        edsb1.append(reportUtil.customFormat("" + itotalfee1, "ZZZ,ZZZ.99"));
        fileTOTDTLContents.add(formatUtil.padX(edsb1.toString(), 160));
        fileTOTDTLContents.add("");
    }

    private void edsb2(int itotalcount2, BigDecimal itotalamt2, BigDecimal itotalfee2) {
        // 表尾第二行
        StringBuilder edsb2 = new StringBuilder();
        edsb2.append(formatUtil.padX("**SUBTOT", 8));
        edsb2.append(formatUtil.padX(" COUNT ", 7));
        edsb2.append(reportUtil.customFormat("" + itotalcount2, "ZZZ,ZZ9.BB"));
        edsb2.append(formatUtil.padX(" AMOUNT ", 8));
        edsb2.append(reportUtil.customFormat("" + itotalamt2, "Z,ZZZ,ZZZ,ZZZ,ZZ9.BB"));
        edsb2.append(formatUtil.padX("   FEE    ", 10));
        edsb2.append(reportUtil.customFormat("" + itotalfee2, "ZZZ,ZZZ.99"));
        fileTOTDTLContents.add(formatUtil.padX(edsb2.toString(), 160));
        edsb2 = new StringBuilder();
        edsb2.append(formatUtil.padX(" ", 2));
        edsb2.append(reportUtil.makeGate("-", 130));
        fileTOTDTLContents.add(edsb2.toString());
    }

    private void edsb3(int itotalcount3, BigDecimal itotalamt3, BigDecimal itotalfee3) {

        // 表尾第四行
        StringBuilder edsb4 = new StringBuilder();
        edsb4.append(formatUtil.padX("**TOTAL", 7));
        edsb4.append(formatUtil.padX("*COUNT", 6));
        edsb4.append(reportUtil.customFormat("" + itotalcount3, "Z,ZZZ,ZZ9.BB"));
        edsb4.append(formatUtil.padX("AMOUNT  ", 8));
        edsb4.append(reportUtil.customFormat("" + itotalamt3, "Z,ZZZ,ZZZ,ZZZ,ZZ9.BB"));
        edsb4.append(formatUtil.padX("   FEE    ", 10));
        edsb4.append(reportUtil.customFormat("" + itotalfee3, "ZZZ,ZZZ.99"));
        fileTOTDTLContents.add(formatUtil.padX(edsb4.toString(), 160));
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
