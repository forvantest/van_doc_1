/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.C074;
import com.bot.ncl.dto.entities.CldtlbyCEERangeAscPCRSTBus;
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
@Component("C074Lsnr")
@Scope("prototype")
public class C074Lsnr extends BatchListenerCase<C074> {
    @Autowired private TextFileUtil textFile;
    @Autowired private Parse parse;
    @Autowired private CldtlService cldtlService;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ReportUtil reportUtil;
    @Autowired private ExternalSortUtil externalSortUtil;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> labelMap;
    private Map<String, String> textMap;

    private static final String FILE_NAME = "CL-BH-C074";
    private static final String CHARSET = "UTF-8";
    private static final String OUTCHARSET = "Big5";
    private static final String FILE_TMP_NAME = "TMPC074";
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;
    private String outputFilePath; // C074路徑
    private C074 event;
    private List<String> fileTmpContents; // 檔案內容
    private List<String> fileC074Contents; // 檔案內容
    private String tmpFilePath; // 暫存檔路徑
    private String processDate; // 批次日期(民國年yyyymmdd)
    private String tbsdy;
    private Boolean noData = false;
    private StringBuilder sb = new StringBuilder();
    private final int pageCnts = 100000;
    private int pageIndex = 0;
    private int entdy = 0;
    private String code;
    private String spbank;
    private int page = 0;
    private String pby;
    private String pbm;
    private String dDate;
    private String dprbno;
    private String wkdprbno;
    private String waitdprbno;
    private int icount = 0;
    private String dUserdata;
    private String wdDate;
    private String wkYMD;
    private BigDecimal wkwdAmt = BigDecimal.ZERO;
    private BigDecimal talwdAmt = BigDecimal.ZERO;
    private String wkdUserdata;
    private String waitdDate;
    private int talpens = 0;
    private int nowpens = 0;

    DecimalFormat df = new DecimalFormat("#,###");

    BigDecimal dAmt = BigDecimal.ZERO;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(C074 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C074Lsnr");
        this.beforRun(event);
    }

    //
    @Override
    @SneakyThrows
    protected void run(C074 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C074Lsnr run()");
        init(event);
        queryCldtl();
        textFile.deleteFile(tmpFilePath);
        batchResponse();
    }

    private void init(C074 event) {
        this.event = event;

        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();

        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        entdy = parse.string2Integer(processDate);

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
        fileC074Contents = new ArrayList<>();
        textFile.deleteFile(tmpFilePath);
        textFile.deleteFile(outputFilePath);
    }

    private void queryCldtl() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C074Lsnr queryCldtl");
        code = "121454";
        // 每個月1號
        int lmndy = (entdy / 100) * 100 + 1;
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C074Lsnrlmndy  = " + lmndy);

        List<CldtlbyCEERangeAscPCRSTBus> lCldtl =
                cldtlService.findbyCEERangeAscPCRST(code, lmndy, entdy, 0, pageIndex, pageCnts);
        // 寫資料到檔案中
        try {
            setTmpData(lCldtl);
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        // 排序檔案資料
        sortfile();
        // 產C074
        toWriteC074File();
        textFile.deleteFile(tmpFilePath);
    }

    private void setTmpData(List<CldtlbyCEERangeAscPCRSTBus> lCldtl) throws IOException {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C074Lsnr setTmpData");
        // 資料照 PBRNO主辦行, CLLBR代收行, STANO銷帳號碼(8:2), RCPTID銷帳號碼, SITDATE原代收日,TIME代收時間 由小到大排序
        // 如果有資料，塞入資料給新檔案並且給予固定位置
        if (lCldtl != null) {
            for (CldtlbyCEERangeAscPCRSTBus tCldtl : lCldtl) {
                String iprbno = tCldtl.getRcptid().substring(4, 7);
                String ientdy = getrocdate(tCldtl.getEntdy());
                String ircptid = String.format("%26s", tCldtl.getRcptid());
                String isitdate = String.format("%07d", tCldtl.getSitdate());
                String itime = String.format("%06d", tCldtl.getTime());
                String iamt = String.format("%15s", tCldtl.getAmt());
                String iuserdata = String.format("%40s", tCldtl.getUserdata());

                String s = iprbno + ientdy + ircptid + isitdate + itime + iamt + iuserdata;

                ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "iprbno=" + iprbno);
                ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C074waitdata=" + s);

                fileTmpContents.add(s);
            }
        } else {
            // 012000 01   RPT074-NONE.                                                95/03/30
            // 012100  03  FILLER                           PIC X(03) VALUE SPACES.    95/03/30
            // 012200  03  FILLER                           PIC X(18) VALUE            95/03/30
            // 012300      " 本月份無代付資料 ".
            StringBuilder s = new StringBuilder();
            s.append(formatUtil.padX("", 3));
            s.append(formatUtil.padX(" 本月份無代付資料 ", 18));
            fileTmpContents.add(s.toString());
            noData = true;
        }
        try {
            textFile.writeFileContent(tmpFilePath, fileTmpContents, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C074Lsnr File OK");
    }

    private void sortfile() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "toWriteC074File sortfile");
        if (noData) {
            return;
        }
        // 資料照 PBRNO主辦行, 3
        // DATE代收日, 8
        // RCPTID銷帳號碼, 26
        // SITDATE原代收日, 8
        // TIME代收時間 6 由小到大排序
        File tmpFile = new File(tmpFilePath);
        List<KeyRange> keyRanges = new ArrayList<>();
        keyRanges.add(new KeyRange(1, 3, SortBy.ASC));
        keyRanges.add(new KeyRange(4, 8, SortBy.ASC));
        keyRanges.add(new KeyRange(13, 26, SortBy.ASC));
        keyRanges.add(new KeyRange(40, 8, SortBy.ASC));
        keyRanges.add(new KeyRange(49, 6, SortBy.ASC));

        externalSortUtil.sortingFile(tmpFile, tmpFile, keyRanges);
    }

    private void toWriteC074File() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "toWriteC074File toWriteC074File");
        List<String> lines = textFile.readFileContent(tmpFilePath, CHARSET);

        String YMD1 = Integer.toString(entdy);
        String YYY1 = YMD1.substring(0, 3);
        String MM1 = YMD1.substring(3, 5);
        String DD1 = YMD1.substring(5, 7);
        String data = YYY1 + MM1 + DD1;

        if (noData) {
            spbank = "000";
            page = 1;
            pby = YYY1;
            pbm = MM1;
            hdsb(spbank, data, page, pby, pbm);
            for (String detail : lines) {
                fileC074Contents.add(detail);
            }
            talwdAmt = BigDecimal.ZERO;
            talpens = 0;

        } else {
            page = 1;
            for (String detail : lines) {
                // 總筆數
                dprbno = detail.substring(0, 3);
                dDate = detail.substring(3, 10);
                dAmt = parse.string2BigDecimal(detail.substring(49, 64));
                dUserdata = detail.substring(64, 104);
                wdDate = dDate;
                waitdprbno = dprbno;
                spbank = dprbno;
                pby = dDate.substring(0, 3);
                pbm = dDate.substring(3, 5);

                // 相同主辦行 不同日期
                if ((waitdprbno.equals(wkdprbno) && !wdDate.equals(waitdDate) && icount != 0)
                        // 不同主辦行
                        || (!waitdprbno.equals(wkdprbno) && icount != 0)) {

                    sb = new StringBuilder();
                    // 代付日期
                    sb.append(formatUtil.padX(" ", 5));
                    sb.append(wkYMD);

                    // 總金額
                    sb.append(formatUtil.padX(" ", 3));
                    sb.append(formatUtil.padLeft("" + df.format(wkwdAmt), 14));

                    // 總筆數
                    sb.append(formatUtil.padX(" ", 3));
                    sb.append(formatUtil.padLeft("" + df.format(nowpens), 10));

                    // 備註
                    sb.append(formatUtil.padX(" ", 5));
                    sb.append(wkdUserdata);

                    fileC074Contents.add(formatUtil.padX(sb.toString(), 120));

                    wkwdAmt = BigDecimal.ZERO;
                    nowpens = 0;
                }
                // 當主辦行不同時印出
                if (!waitdprbno.equals(wkdprbno) && icount != 0) {
                    edsb(talwdAmt, talpens);
                    StringBuilder sb2 = new StringBuilder();
                    sb2 = new StringBuilder();
                    sb2.append("\u000c");
                    fileC074Contents.add(formatUtil.padX(sb2.toString(), 170));
                    talwdAmt = BigDecimal.ZERO;
                    talpens = 0;
                    nowpens = 0;
                }
                // 相同主辦行相同日期
                if ((wdDate.equals(waitdDate) && waitdprbno.equals(wkdprbno))
                        || (waitdprbno.equals(wkdprbno))) {
                    wkwdAmt = wkwdAmt.add(dAmt);
                    nowpens++;
                    talpens++;
                }

                waitdDate = wdDate;
                talwdAmt = talwdAmt.add(dAmt);

                // 換主辦行需重新歸零
                if (!waitdprbno.equals(wkdprbno)) {
                    icount++;
                    wkwdAmt = BigDecimal.ZERO;
                    nowpens = 0;
                    talwdAmt = BigDecimal.ZERO;
                    talpens = 0;
                    hdsb(spbank, data, page, pby, pbm);
                    wkwdAmt = wkwdAmt.add(dAmt);
                    talwdAmt = talwdAmt.add(dAmt);
                    nowpens++;
                    talpens++;
                }

                //                // 代付日期
                String YYY = dDate.substring(0, 3);
                String MM = dDate.substring(3, 5);
                String DD = dDate.substring(5, 7);
                String YMD = YYY + "/" + MM + "/" + DD;

                wkdprbno = waitdprbno;
                wkYMD = YMD;
                wkdUserdata = dUserdata;
            }
            sb = new StringBuilder();
            // 代付日期
            sb.append(formatUtil.padX(" ", 5));
            sb.append(wkYMD);
            // 總金額
            sb.append(formatUtil.padX(" ", 3));
            sb.append(formatUtil.padLeft("" + df.format(wkwdAmt), 14));
            // 總筆數
            sb.append(formatUtil.padX(" ", 3));
            sb.append(formatUtil.padLeft("" + df.format(nowpens), 10));
            // 備註
            sb.append(formatUtil.padX(" ", 40));
            fileC074Contents.add(formatUtil.padX(sb.toString(), 120));
        }
        edsb(talwdAmt, talpens);
        try {
            textFile.writeFileContent(outputFilePath, fileC074Contents, OUTCHARSET);
            upload(outputFilePath, "RPT", "");
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

    // 表首
    private void hdsb(String spbank, String data, int page, String pby, String pbm) {
        // 空一行
        StringBuilder white = new StringBuilder();
        white.append(formatUtil.padX(" ", 1));
        // 放進來算一行
        fileC074Contents.add(formatUtil.padX(white.toString(), 170));

        // 空兩行
        StringBuilder white2 = new StringBuilder();
        white2.append(formatUtil.padX(" ", 1));
        fileC074Contents.add(formatUtil.padX(white2.toString(), 170));

        // 表頭第三行
        StringBuilder hdsb = new StringBuilder();
        hdsb.append(formatUtil.padX(" ", 22));
        hdsb.append(formatUtil.padX(" 汽燃費退費憑單報表－累計兌付 ", 30));
        hdsb.append(formatUtil.padX(" ", 6));
        hdsb.append(formatUtil.padX(" 印表日： ", 10));
        hdsb.append(reportUtil.customFormat(data, "999/99/99"));
        // 放進來算一行
        fileC074Contents.add(formatUtil.padX(hdsb.toString(), 78));

        // 空一行
        StringBuilder white3 = new StringBuilder();
        white3.append(formatUtil.padX(" ", 1));
        fileC074Contents.add(formatUtil.padX(white3.toString(), 170));

        // 表頭第五行
        StringBuilder hdsb2 = new StringBuilder();
        hdsb2.append(formatUtil.padX(" ", 2));
        hdsb2.append(formatUtil.padX(" 代付月份： ", 12));
        hdsb2.append(formatUtil.pad9(pby, 3));
        hdsb2.append(formatUtil.padX(" 年 ", 4));
        hdsb2.append(formatUtil.pad9(pbm, 2));
        hdsb2.append(formatUtil.padX(" 月 ", 4));
        hdsb2.append(formatUtil.padX(" ", 31));
        hdsb2.append(formatUtil.padX(" 頁　次： ", 10));
        hdsb2.append(formatUtil.padX(" 1", 2));
        fileC074Contents.add(formatUtil.padX(hdsb2.toString(), 170));

        // 表頭第六行
        StringBuilder hdsb3 = new StringBuilder();
        hdsb3.append(formatUtil.padX(" ", 2));
        hdsb3.append(formatUtil.padX(" 縣市主辦行： ", 14));
        hdsb3.append(formatUtil.padX(spbank, 3));
        hdsb3.append(formatUtil.padX(" ", 39));
        hdsb3.append(formatUtil.padX(" 報表名稱： ", 12));
        hdsb3.append(formatUtil.padX("C074", 4));
        fileC074Contents.add(formatUtil.padX(hdsb3.toString(), 170));

        // 空一行
        StringBuilder white4 = new StringBuilder();
        white4.append(formatUtil.padX(" ", 1));
        fileC074Contents.add(formatUtil.padX(white4.toString(), 170));

        // 表頭第八行
        StringBuilder hdsb4 = new StringBuilder();
        hdsb4.append(formatUtil.padX("", 3));
        hdsb4.append(formatUtil.padX(" 代付日期 ", 10));
        hdsb4.append(formatUtil.padX("", 10));
        hdsb4.append(formatUtil.padX(" 總金額 ", 8));
        hdsb4.append(formatUtil.padX("", 5));
        hdsb4.append(formatUtil.padX(" 總件數 ", 8));
        hdsb4.append(formatUtil.padX(" ", 14));
        hdsb4.append(formatUtil.padX(" 備註 ", 6));
        fileC074Contents.add(formatUtil.padX(hdsb4.toString(), 170));

        // 給虛線
        fileC074Contents.add(reportUtil.makeGate("-", 80));
    }

    // 表尾
    private void edsb(BigDecimal talwdAmt, int talpens) {
        // 空一行
        // 放進來算一行
        fileC074Contents.add("");
        fileC074Contents.add(reportUtil.makeGate("-", 80));

        // 表尾第一行
        StringBuilder edsb1 = new StringBuilder();
        edsb1.append(formatUtil.padX(" ", 5));
        edsb1.append(formatUtil.padX(" 總　計 ", 8));
        edsb1.append(reportUtil.customFormat("" + talwdAmt, "Z,ZZZ,ZZZ,ZZZ,ZZ9"));
        edsb1.append(formatUtil.padX(" ", 3));
        edsb1.append(reportUtil.customFormat("" + talpens, "ZZ,ZZZ,ZZ9"));
        fileC074Contents.add(formatUtil.padX(edsb1.toString(), 160));
    }

    private void batchResponse() {
        Map<String, String> responseTextMap = new HashMap<>(textMap);
        responseTextMap.put("RPTNAME", FILE_NAME);

        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", responseTextMap);
    }
}
