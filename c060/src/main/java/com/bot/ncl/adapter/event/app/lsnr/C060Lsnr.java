/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.C060;
import com.bot.ncl.dto.entities.CldtlByCodeBus;
import com.bot.ncl.jpa.svc.CldtlService;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
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
@Component("C060Lsnr")
@Scope("prototype")
public class C060Lsnr extends BatchListenerCase<C060> {
    @Autowired private CldtlService cldtlService;
    @Autowired private FormatUtil formatUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    @Autowired private ReportUtil reportUtil;
    @Autowired private TextFileUtil textFile;
    @Autowired private FsapBatchUtil fsapBatchUtil;

    @Autowired private Parse parse;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> labelMap;
    private Map<String, String> textMap;

    private String filePath;

    private C060 event;
    private List<String> filecldtListContents; // 檔案內容
    private List<String> fileContents;

    private static final String FILE_NAME = "CL-BH-C060";

    private static final String CHARSET = "UTF-8";
    private static final String CHARSET_Big5 = "Big5";
    private static final String CONVF_RPT = "RPT";
    private static final String PATH_SEPARATOR = File.separator;

    private String processDate; // 作業日期(民國年yyyymmdd)
    private String tbsdy;
    private boolean hasNextPage;
    private final int pageCnts = 100000;
    private int pageIndex = 0;
    private String code;
    private String codex;
    private String tlrno;
    private String txtype;
    private BigDecimal amt = BigDecimal.valueOf(0);
    private String rcptid;
    private int time = 0;
    private String userdata;
    private int trmno = 0;
    private int entdy = 0;
    private String caldy;
    private boolean isNumeric = true;

    int LAMT2 = 0;
    int LARTE2 = 0;

    @Override
    public void onApplicationEvent(C060 event) {
        this.beforRun(event);
    }

    @SneakyThrows
    protected void run(C060 event) {
        init(event);
        // 有資料
        queryCldtlThenWriteFile();
        // 新系統為分散式架構,須將本地產製的檔案上傳到FSAP共用空間
        copyToFtp();

        batchResponse();
    }

    private void init(C060 event) {
        this.event = event;

        fileContents = new ArrayList<>();

        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();

        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        filePath = fileDir + CONVF_RPT + PATH_SEPARATOR + processDate + PATH_SEPARATOR + FILE_NAME;
        textFile.deleteFile(filePath);

        hasNextPage = true;
        pageIndex = 0;

        filecldtListContents = new ArrayList<>();
    }

    private void queryCldtlThenWriteFile() {
        List<String> lsCode = new ArrayList<>();
        lsCode.add("366AE9");
        lsCode.add("366AG9");

        //        entdy = 1071009;
        entdy = parse.string2Integer(processDate);
        List<CldtlByCodeBus> cldtList =
                cldtlService.findByCode(lsCode, entdy, 0, pageIndex, pageCnts);

        String YMD1 = Integer.toString(entdy);
        String YYY1 = YMD1.substring(0, 3);
        String MM1 = YMD1.substring(3, 5);
        String DD1 = YMD1.substring(5, 7);
        String data = YYY1 + "/" + MM1 + "/" + DD1;

        if (cldtList == null || cldtList.isEmpty()) {
            //            if (!filecldtListContents.isEmpty()) {
            queryCldtlWriteFile();
            //                filecldtListContents = new ArrayList<>();
            //            }
            //            return;
        } else {
            int wkcllbr = 0;
            int seqno = 0;
            int page = 1;
            int pagecount = 0;
            // 國稅筆數
            int ae9 = 0;
            // 國稅金額
            BigDecimal aeamt = new BigDecimal(0);
            // 地方稅筆數
            int ag9 = 0;
            // 地方稅金額
            BigDecimal agamt = new BigDecimal(0);
            // 代收行筆數
            int icount = 0;
            int ixcount = 0;
            // 內容
            for (CldtlByCodeBus cldtl : cldtList) {
                StringBuilder sb = new StringBuilder();
                ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C060  = " + cldtl);

                seqno++;
                pagecount++;
                int cllbr = cldtl.getCllbr();
                int waitcllbr = cllbr;
                ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "cllbr  = " + cllbr);
                ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkcllbr  = " + wkcllbr);

                if ((waitcllbr != wkcllbr) && icount != 0) {
                    edsb(ae9, ag9, aeamt, agamt);
                    StringBuilder sb2 = new StringBuilder();
                    sb2 = new StringBuilder();
                    sb2.append("\u000c");
                    fileContents.add(formatUtil.padX(sb2.toString(), 170));
                    ae9 = 0;
                    ag9 = 0;
                    aeamt = BigDecimal.valueOf(0);
                    agamt = BigDecimal.valueOf(0);
                }
                if (waitcllbr != wkcllbr) {
                    seqno = 1;
                    hdsb(cllbr, data, page);
                }
                if (70 <= pagecount) {
                    StringBuilder sb3 = new StringBuilder();
                    sb3 = new StringBuilder();
                    sb3.append("\u000c");
                    fileContents.add(formatUtil.padX(sb3.toString(), 170));
                    page++;
                    pagecount = 0;
                    hdsb(cllbr, data, page);
                }

                // 交易序號
                sb.append(formatUtil.padX(" ", 1));
                sb.append(formatUtil.pad9(seqno + "", 6));

                // 稅別
                sb.append(formatUtil.padX(" ", 1));
                code = cldtl.getCode();
                if (cldtl.getCode().equals("366AE9")) {
                    codex = "國稅";
                    ae9++;
                    aeamt = aeamt.add(cldtl.getAmt());
                    sb.append(codex);
                    sb.append(formatUtil.padX(" ", 7 - codex.length()));
                } else if (cldtl.getCode().equals("366AG9")) {
                    codex = "地方稅";
                    ag9++;
                    agamt = agamt.add(cldtl.getAmt());
                    sb.append(codex);
                    sb.append(formatUtil.padX(" ", 6 - codex.length()));
                }

                // 銷帳編號
                sb.append(formatUtil.padX(" ", 1));
                rcptid = cldtl.getRcptid();
                if (rcptid.length() >= 22) {
                    rcptid = rcptid.substring(0, 22);
                    sb.append(rcptid);
                } else {
                    sb.append(rcptid);
                    sb.append(formatUtil.padX(" ", 22 - rcptid.length()));
                }

                // 交易日期
                sb.append(formatUtil.padX(" ", 1));
                caldy = Integer.toString(cldtl.getCaldy());
                String YYY = caldy.substring(0, 3);
                String MM = caldy.substring(3, 5);
                String DD = caldy.substring(5, 7);
                String YMD = YYY + "/" + MM + "/" + DD;
                sb.append(YMD);

                // 時間
                sb.append(formatUtil.padX(" ", 1));
                time = cldtl.getTime();
                sb.append(formatUtil.pad9(time + "", 6));

                // 櫃機
                sb.append(formatUtil.padX(" ", 1));
                trmno = cldtl.getTrmno();
                sb.append(formatUtil.pad9(trmno + "", 4));

                // 櫃員
                sb.append(formatUtil.padX(" ", 1));
                tlrno = cldtl.getTlrno();
                sb.append(tlrno);

                // 帳務別
                sb.append(formatUtil.padX(" ", 1));
                txtype = cldtl.getTxtype();
                sb.append(txtype);

                // 判斷金額
                userdata = cldtl.getUserdata().substring(0, 18);

                isNumeric(userdata);
                if (isNumeric) {
                    LAMT2 = Integer.parseInt(cldtl.getUserdata().substring(0, 10));
                    LARTE2 = Integer.parseInt(cldtl.getUserdata().substring(10, 20));
                } else {
                    LAMT2 = Integer.parseInt(cldtl.getUserdata().substring(19, 28));
                    LARTE2 = Integer.parseInt(cldtl.getUserdata().substring(28, 38));
                }

                int amti = cldtl.getAmt().intValue();
                int tal = amti - LAMT2 - LARTE2;
                DecimalFormat df = new DecimalFormat("#,###");
                // 繳納金額
                sb.append(formatUtil.padX(" ", 1));
                //                sb.append(formatUtil.pad9(tal + "", 13));
                sb.append(formatUtil.padLeft(df.format(tal) + "", 13));

                // 逾期滯納金
                sb.append(formatUtil.padX(" ", 1));
                //                sb.append(formatUtil.pad9(LAMT2 + "", 12));
                sb.append(formatUtil.padLeft(df.format(LAMT2) + "", 11));

                // 逾期滯納息
                sb.append(formatUtil.padX(" ", 1));
                //                sb.append(formatUtil.pad9(LARTE2 + "", 12));
                sb.append(formatUtil.padLeft(df.format(LARTE2) + "", 11));

                // 繳納總金額
                sb.append(formatUtil.padX(" ", 1));
                amt = cldtl.getAmt();
                //                sb.append(formatUtil.pad9(amt + "", 13));
                sb.append(formatUtil.padLeft(df.format(amt) + "", 13));

                // 備註
                sb.append(formatUtil.padX(" ", 1));
                userdata = cldtl.getUserdata();
                sb.append(userdata);
                sb.append(formatUtil.padX(" ", 40 - userdata.length()));

                sb.append(formatUtil.padX(" ", 1));

                fileContents.add(formatUtil.padX(sb.toString(), 120));

                ixcount++;
                if (cldtList.size() == ixcount) {
                    edsb(ae9, ag9, aeamt, agamt);
                }

                if (waitcllbr != wkcllbr) {
                    icount++;
                }
                wkcllbr = cllbr;
            }
            //            edsb(ae9, ag9, aeamt, agamt);
        }
        try {
            textFile.writeFileContent(filePath, fileContents, CHARSET_Big5);
            upload(filePath, "RPT", "");
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void queryCldtlWriteFile() {
        int cllbr = 000;
        int page = 1;
        entdy = parse.string2Integer(processDate);
        String YMD1 = Integer.toString(entdy);
        String YYY1 = YMD1.substring(0, 3);
        String MM1 = YMD1.substring(3, 5);
        String DD1 = YMD1.substring(5, 7);
        String data = YYY1 + "/" + MM1 + "/" + DD1;
        hdsb(cllbr, data, page);
        StringBuilder sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 1));
        sb.append(formatUtil.padX("         NO DATA !!", 40));
        fileContents.add(formatUtil.padX(sb.toString(), 50));
        int ae9 = 0;
        int ag9 = 0;
        BigDecimal aeamt = new BigDecimal(0);
        BigDecimal agamt = new BigDecimal(0);
        edsb(ae9, ag9, aeamt, agamt);
    }

    private void moveErrorResponse(LogicException e) {
        //                event.setPeripheryRequest();
    }

    private void copyToFtp() {
        String filePath = fileDir + FILE_NAME;
        // TODO: copy C060 from local to FTP
    }

    // 判斷文數字
    private boolean isNumeric(String userdata) {
        isNumeric = userdata.matches("[+-]?\\d*(\\.\\d+)?");
        return isNumeric;
    }

    private void hdsb(int cllbr, String data, int page) {
        // 表頭
        // 開頭空兩行
        StringBuilder fhdsb = new StringBuilder();
        fhdsb.append(formatUtil.padX(" ", 1));
        fileContents.add(formatUtil.padX(fhdsb.toString(), 78));
        StringBuilder shdsb = new StringBuilder();
        shdsb.append(formatUtil.padX(" ", 1));
        fileContents.add(formatUtil.padX(shdsb.toString(), 78));

        StringBuilder hdsb = new StringBuilder();
        hdsb.append(formatUtil.padX(" ", 58));
        hdsb.append(formatUtil.padX("臨櫃代收稅款日報表", 18));
        // 放進來算一行
        fileContents.add(formatUtil.padX(hdsb.toString(), 78));

        // 表頭第二行
        StringBuilder hdsb2 = new StringBuilder();

        hdsb2.append(formatUtil.padX(" 代收行： ", 14));
        hdsb2.append(reportUtil.customFormat("" + cllbr, "ZZZ"));
        hdsb2.append(formatUtil.padX("", 1));
        hdsb2.append(formatUtil.padX("", 88));
        hdsb2.append(formatUtil.padX(" 報表名稱： C060", 20));
        // 放進來算一行
        fileContents.add(formatUtil.padX(hdsb2.toString(), 135));

        // 表頭第三行
        StringBuilder hdsb3 = new StringBuilder();

        hdsb3.append(formatUtil.padX(" ", 1));
        hdsb3.append(formatUtil.padX("代收日期：    " + data, 35));
        hdsb3.append(formatUtil.padX(" ", 71));
        hdsb3.append(formatUtil.padX("頁    數： " + page, 20));
        // 放進來算一行
        fileContents.add(formatUtil.padX(hdsb3.toString(), 140));

        // 表頭第四行
        StringBuilder hdsb4 = new StringBuilder();

        hdsb4.append(formatUtil.padX(" ", 1));
        // 放進來算一行
        fileContents.add(formatUtil.padX(hdsb4.toString(), 1));

        // 表頭第五行
        StringBuilder hdsb5 = new StringBuilder();

        hdsb5.append(formatUtil.padX(" ", 1));
        hdsb5.append(formatUtil.padX("序號", 4));
        hdsb5.append(formatUtil.padX(" ", 4));
        hdsb5.append(formatUtil.padX("稅別", 4));
        hdsb5.append(formatUtil.padX(" ", 6));
        hdsb5.append(formatUtil.padX("銷帳編號", 8));
        hdsb5.append(formatUtil.padX(" ", 14));
        hdsb5.append(formatUtil.padX("交易日期", 8));
        hdsb5.append(formatUtil.padX(" ", 2));
        hdsb5.append(formatUtil.padX("時間", 4));
        hdsb5.append(formatUtil.padX(" ", 2));
        hdsb5.append(formatUtil.padX("櫃機", 4));
        hdsb5.append(formatUtil.padX(" ", 2));
        hdsb5.append(formatUtil.padX("櫃員", 4));
        hdsb5.append(formatUtil.padX(" ", 1));
        hdsb5.append(formatUtil.padX("帳務", 4));
        hdsb5.append(formatUtil.padX(" ", 2));
        hdsb5.append(formatUtil.padX("繳納金額", 8));
        hdsb5.append(formatUtil.padX(" ", 2));
        hdsb5.append(formatUtil.padX("逾期滯納金", 10));
        hdsb5.append(formatUtil.padX(" ", 2));
        hdsb5.append(formatUtil.padX("逾期滯納息", 10));
        hdsb5.append(formatUtil.padX(" ", 4));
        hdsb5.append(formatUtil.padX("繳納總金額", 10));
        hdsb5.append(formatUtil.padX(" ", 3));
        hdsb5.append(formatUtil.padX("備註", 10));

        // 放進來算一行
        fileContents.add(formatUtil.padX(hdsb5.toString(), 170));

        // 表頭第六行
        StringBuilder hdsb6 = new StringBuilder();

        hdsb6.append(formatUtil.padX(" ", 1));
        hdsb6.append(
                formatUtil.padX(
                        "============================================================================================================================================================",
                        312));
        // 放進來算一行
        fileContents.add(formatUtil.padX(hdsb6.toString(), 160));
    }

    private void edsb(int ae9, int ag9, BigDecimal aeamt, BigDecimal agamt) {
        DecimalFormat df = new DecimalFormat("#,###");
        // 結尾空兩行
        StringBuilder fehdsb = new StringBuilder();
        fehdsb.append(formatUtil.padX(" ", 1));
        fileContents.add(formatUtil.padX(fehdsb.toString(), 78));
        StringBuilder sehdsb = new StringBuilder();
        sehdsb.append(formatUtil.padX(" ", 1));
        fileContents.add(formatUtil.padX(sehdsb.toString(), 78));

        // 表尾第一行
        StringBuilder edsb1 = new StringBuilder();
        edsb1.append(formatUtil.padX(" ", 1));
        edsb1.append(formatUtil.padX(" ", 9));
        edsb1.append(formatUtil.padX("國稅總筆數:", 12));
        edsb1.append(formatUtil.padX(" ", 5));
        edsb1.append(formatUtil.padLeft(df.format(ae9) + "", 7));
        edsb1.append(formatUtil.padX(" ", 21));
        edsb1.append(formatUtil.padX("國稅總金額:", 12));
        edsb1.append(formatUtil.padX(" ", 7));
        edsb1.append(formatUtil.padLeft(df.format(aeamt) + "", 17));
        //        edsb1.append(formatUtil.padLeft(df.format(ae9) + "", 17));

        // 放進來算一行
        fileContents.add(formatUtil.padX(edsb1.toString(), 180));

        // 表尾第二行
        StringBuilder edsb2 = new StringBuilder();

        edsb2.append(formatUtil.padX(" ", 1));
        edsb2.append(formatUtil.padX(" ", 9));
        edsb2.append(formatUtil.padX("地方稅總筆數:", 14));
        edsb2.append(formatUtil.padX(" ", 3));
        edsb2.append(formatUtil.padLeft(df.format(ag9) + "", 7));
        edsb2.append(formatUtil.padX(" ", 21));
        edsb2.append(formatUtil.padX("地方稅總金額:", 14));
        edsb2.append(formatUtil.padX(" ", 5));
        edsb2.append(formatUtil.padLeft(df.format(agamt) + "", 17));
        // 放進來算一行
        fileContents.add(formatUtil.padX(edsb2.toString(), 180));

        // 表尾第三行
        StringBuilder edsb3 = new StringBuilder();

        edsb3.append(formatUtil.padX(" ", 1));
        // 放進來算一行
        fileContents.add(formatUtil.padX(edsb3.toString(), 1));
        // 表尾第四行
        StringBuilder edsb4 = new StringBuilder();

        edsb4.append(formatUtil.padX(" ", 1));

        // 放進來算一行
        fileContents.add(formatUtil.padX(edsb4.toString(), 1));

        // 表尾第5行
        StringBuilder edsb5 = new StringBuilder();

        edsb5.append(formatUtil.padX(" ", 69));
        edsb5.append(formatUtil.padX("經  辦：", 8));
        edsb5.append(formatUtil.padX(" ", 34));
        edsb5.append(formatUtil.padX("主  管：", 8));
        // 放進來算一行
        fileContents.add(formatUtil.padX(edsb5.toString(), 120));
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
