/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.C073;
import com.bot.ncl.dto.entities.CldtlbyWriteoffBus;
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
@Component("C073Lsnr")
@Scope("prototype")
public class C073Lsnr extends BatchListenerCase<C073> {
    @Autowired private TextFileUtil textFile;
    @Autowired private Parse parse;
    @Autowired private CldtlService cldtlService;
    @Autowired private ReportUtil reportUtil;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ExternalSortUtil externalSortUtil;
    @Autowired private FsapBatchUtil fsapBatchUtil;
    @Autowired private FsapSyncSftpService fsapSyncSftpService;
    private C073 event;
    DecimalFormat df = new DecimalFormat("#,###");

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> labelMap;
    private Map<String, String> textMap;

    private String filePath;
    private List<String> fileContents;
    private static final String FILE_NAME = "CL-BH-C073";
    private static final String CHARSET = "UTF-8";
    private static final String OUTCHARSET = "Big5";
    private static final String FILE_TMP_NAME = "TMPC073";
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;
    private String outputFilePath; // C073路徑
    private List<String> fileTmpContents; // 檔案內容
    private List<String> fileC073Contents; // 檔案內容
    private String tmpFilePath; // 暫存檔路徑
    private String processDate; // 作業日期(民國年yyyymmdd)
    private String tbsdy;
    private Boolean noData = false;
    private StringBuilder sb = new StringBuilder();
    private final int pageCnts = 100000;
    private int pageIndex = 0;
    private int pageCnt = 0;
    private int entdy = 0;
    private String code;
    private String cllbr;
    private String stano;
    private String hostbranch;
    private int page = 0;
    int nowPageRow = 0;
    private String branchcode;
    private String branchcodewait;
    private int pagecount = 0;
    private String rcpid;
    private String sitdate;
    private String time;
    private String amt;
    private String txtype;
    private String userdata;
    private String stanowait;
    private String rcpidwait;
    private String stanoX;
    private int stanocount = 0;
    private String cllbrwait;
    // 第一次不進入總筆數印製
    private int icount = 0;
    private int cllbrcount = 0;
    private int ixcount = 0;
    private int talcount = 0;
    private BigDecimal talaml = BigDecimal.ZERO;
    private BigDecimal subtalaml = BigDecimal.ZERO;
    private BigDecimal stalaml = BigDecimal.ZERO;
    private String prbno;
    private String rcptid;

    @Override
    public void onApplicationEvent(C073 event) {
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(C073 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C073Lsnr run()");
        init(event);
        queryCldtl();
        textFile.deleteFile(tmpFilePath);

        batchResponse();
    }

    private void init(C073 event) {
        this.event = event;

        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        tbsdy = labelMap.get("PROCESS_DATE");
        entdy = parse.string2Integer(processDate);
        pageIndex = 0;
        tmpFilePath =
                fileDir + CONVF_DATA + PATH_SEPARATOR + entdy + PATH_SEPARATOR + FILE_TMP_NAME;
        outputFilePath = fileDir + CONVF_RPT + PATH_SEPARATOR + entdy + PATH_SEPARATOR + FILE_NAME;
        fileTmpContents = new ArrayList<>();
        fileC073Contents = new ArrayList<>();
        textFile.deleteFile(tmpFilePath);
        textFile.deleteFile(outputFilePath);
    }

    private void queryCldtl() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C063Lsnr queryCldtl");
        code = "121454";
        List<CldtlbyWriteoffBus> lCldtl =
                cldtlService.findbyWriteoff(code, entdy, 0, pageIndex, pageCnts);
        // 寫資料到檔案中

        try {
            setTmpData(lCldtl);
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        // 排序檔案資料
        sortfile();
        // 產C073
        toWriteC073File();
        textFile.deleteFile(tmpFilePath);
    }

    private void setTmpData(List<CldtlbyWriteoffBus> lCldtl) throws IOException {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C073Lsnr setTmpData");
        //        資料照 PBRNO主辦行, CLLBR代收行, STANO銷帳號碼(8:2), RCPTID銷帳號碼, SITDATE原代收日,TIME代收時間 由小到大排序
        // 如果有資料，塞入資料給新檔案並且給予固定位置
        if (lCldtl != null) {
            for (CldtlbyWriteoffBus tCldtl : lCldtl) {

                String YMD1 = Integer.toString(entdy);
                String YYY1 = YMD1.substring(0, 3);
                String MM1 = YMD1.substring(3, 5);
                String DD1 = YMD1.substring(5, 7);
                String data = YYY1 + "/" + MM1 + "/" + DD1;
                // 放入需要的資料
                String iprbno = tCldtl.getRcptid().substring(4, 7);
                String icllbr = String.format("%03d", tCldtl.getCllbr());
                String istano = tCldtl.getRcptid().substring(7, 9);
                String ircptid = String.format("%26s", tCldtl.getRcptid());
                String isitdate = String.format("%07d", tCldtl.getSitdate());
                String itime = String.format("%06d", tCldtl.getTime());
                String iamt = String.format("%15s", tCldtl.getAmt());
                String itxtype = String.format("%1s", tCldtl.getTxtype());
                String iuserdata = String.format("%40s", tCldtl.getUserdata());

                String s =
                        iprbno + icllbr + istano + ircptid + isitdate + data + itime + iamt
                                + itxtype + iuserdata;
                ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C073waitdata=" + s);

                fileTmpContents.add(s);
            }
        } else {
            String s = " ";
            s = "            NO DATA !!";
            fileTmpContents.add(s);
            noData = true;
        }
        try {
            textFile.writeFileContent(tmpFilePath, fileTmpContents, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C073Lsnr File OK");
    }

    private void sortfile() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "toWriteC073File sortfile");
        if (noData) {
            return;
        }
        //        資料照 PBRNO主辦行,
        //        CLLBR代收行,
        //        STANO銷帳號碼(8:2),
        //        RCPTID銷帳號碼,
        //        SITDATE原代收日,
        //        TIME代收時間 由小到大排序

        File tmpFile = new File(tmpFilePath);
        List<KeyRange> keyRanges = new ArrayList<>();
        keyRanges.add(new KeyRange(1, 3, SortBy.ASC));
        keyRanges.add(new KeyRange(4, 3, SortBy.ASC));
        keyRanges.add(new KeyRange(8, 2, SortBy.ASC));
        keyRanges.add(new KeyRange(11, 26, SortBy.ASC));
        keyRanges.add(new KeyRange(38, 7, SortBy.ASC));
        keyRanges.add(new KeyRange(55, 6, SortBy.ASC));

        externalSortUtil.sortingFile(tmpFile, tmpFile, keyRanges);
    }

    private void toWriteC073File() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "toWriteC073File toWriteC073File");
        List<String> lines = textFile.readFileContent(tmpFilePath, CHARSET);

        String YMD1 = Integer.toString(entdy);
        String YYY1 = YMD1.substring(0, 3);
        String MM1 = YMD1.substring(3, 5);
        String DD1 = YMD1.substring(5, 7);
        String data = YYY1 + MM1 + DD1;

        if (noData) {
            hostbranch = "";
            page = 1;
            fileC073Contents.add("\u000c");
            hdsb(hostbranch, data, page);
            for (String detail : lines) {
                fileC073Contents.add(detail);
            }
            edsb("00", 0, BigDecimal.ZERO);
            edsbone(0, BigDecimal.ZERO);
            edsbtwo(0, BigDecimal.ZERO);
        } else {
            int cnt = 0;
            nowPageRow = 0;
            page = 1;
            for (String detail : lines) {
                // 總筆數
                cnt++;
                prbno = detail.substring(0, 3);
                cllbr = detail.substring(3, 6);
                stano = detail.substring(6, 8);
                rcptid = detail.substring(8, 34).trim();
                sitdate = detail.substring(34, 41);
                time = detail.substring(50, 56);
                amt = detail.substring(56, 71);
                txtype = detail.substring(71, 72);
                userdata = detail.substring(72, 112);
                branchcode = prbno;
                hostbranch = prbno;

                if (!stano.equals(stanowait) && icount != 0) {
                    // 所站小記
                    edsb(stanowait, stanocount, stalaml);
                    stanocount = 0;
                    stalaml = BigDecimal.ZERO;
                }
                if (!cllbr.equals(cllbrwait) && icount != 0) {
                    if (stano.equals(stanowait)) {
                        // 所站小記
                        edsb(stano, stanocount, stalaml);
                        stanocount = 0;
                        stalaml = BigDecimal.ZERO;
                    }
                    // 分行小記
                    edsbone(cllbrcount, subtalaml);
                    cllbrcount = 0;
                    subtalaml = BigDecimal.ZERO;
                }
                if (!branchcode.equals(branchcodewait) && icount != 0) {
                    // 主辦行不同時分頁
                    if (stano.equals(stanowait)) {
                        // 所站小記
                        edsb(stano, stanocount, stalaml);
                        stanocount = 0;
                        stalaml = BigDecimal.ZERO;
                    }
                    if (cllbr.equals(cllbrwait)) {
                        // 所站小記
                        edsbone(cllbrcount, subtalaml);
                        cllbrcount = 0;
                        subtalaml = BigDecimal.ZERO;
                    }
                    edsbtwo(talcount, talaml);
                    sb = new StringBuilder();
                    sb.append("\u000c");
                    fileC073Contents.add(formatUtil.padX(sb.toString(), 170));
                    cllbrcount = 0;
                    stanocount = 0;
                    talcount = 0;
                    talaml = BigDecimal.ZERO;
                    subtalaml = BigDecimal.ZERO;
                }

                if (!branchcode.equals(branchcodewait)) {
                    hdsb(hostbranch, data, page);
                }
                cllbrcount++;
                stanocount++;
                talcount++;
                stalaml = stalaml.add(parse.string2BigDecimal(amt));
                subtalaml = subtalaml.add(parse.string2BigDecimal(amt));
                talaml = talaml.add(parse.string2BigDecimal(amt));

                if (54 <= pagecount) {
                    StringBuilder sb3 = new StringBuilder();
                    sb3 = new StringBuilder();
                    sb3.append("\u000c");
                    fileC073Contents.add(formatUtil.padX(sb3.toString(), 170));
                    page++;
                    pagecount = 0;
                    hdsb(hostbranch, data, page);
                    fileC073Contents = new ArrayList<>();
                }

                sb = new StringBuilder();
                sb.append(formatUtil.padX(" ", 2));
                sb.append(stano);
                // 代付分行
                sb.append(formatUtil.padX(" ", 7));
                //                sb.append(formatUtil.pad9(cllbr + "", 3));
                sb.append(cllbr);
                //                sb.append(formatUtil.padX(" ", 6 - codex.length()));
                // 銷帳編號
                sb.append(formatUtil.padX(" ", 5));
                sb.append(String.format("%16s", rcptid));

                // 交易日期
                sb.append(formatUtil.padX(" ", 2));
                String YYY = sitdate.substring(0, 3);
                String MM = sitdate.substring(3, 5);
                String DD = sitdate.substring(5, 7);
                String YMD = YYY + "/" + MM + "/" + DD;
                sb.append(YMD);

                // 時間
                sb.append(formatUtil.padX(" ", 2));
                //                sb.append(formatUtil.pad9(time + "", 6));
                sb.append(formatUtil.pad9(time, 6));
                // 金額
                //                sb.append(formatUtil.padX(" ", 1));
                sb.append(amt);
                // 帳務別
                sb.append(formatUtil.padX(" ", 2));
                sb.append(txtype);

                // 備註
                sb.append(formatUtil.padX(" ", 4));
                sb.append(userdata);

                fileC073Contents.add(formatUtil.padX(sb.toString(), 120));

                ixcount++;
                if (lines.size() == ixcount) {
                    edsb(stano, stanocount, stalaml);
                    edsbone(stanocount, subtalaml);
                    edsbtwo(talcount, talaml);
                }
                // 換主辦行OR分行
                if (!branchcode.equals(branchcodewait)) {
                    icount++;
                }

                branchcodewait = branchcode;
                stanowait = stano;
                cllbrwait = cllbr;
            }
        }
        try {
            textFile.writeFileContent(outputFilePath, fileC073Contents, OUTCHARSET);
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

    private void hdsb(String hostbranch, String data, int page) {
        // 空一行
        // 放進來算一行
        fileC073Contents.add("");

        // 空兩行
        // 放進來算一行
        fileC073Contents.add("");

        // 表頭第一行
        StringBuilder hdsb = new StringBuilder();
        hdsb.append(formatUtil.padX("", 27));
        hdsb.append(formatUtil.padX(" 汽燃費退費憑單日報表－明細檔 ", 48));
        fileC073Contents.add(formatUtil.padX(hdsb.toString(), 78));

        // 表頭第二行
        StringBuilder hdsb2 = new StringBuilder();
        hdsb2.append(formatUtil.padX(" 縣市主辦行： ", 14));
        hdsb2.append(formatUtil.padX(hostbranch, 3));
        hdsb2.append(formatUtil.padX("", 48));
        hdsb2.append(formatUtil.padX(" 報表名稱： ", 12));
        hdsb2.append(formatUtil.padX("C073    ", 8));
        // 放進來算一行
        fileC073Contents.add(formatUtil.padX(hdsb2.toString(), 135));

        // 表頭第三行
        StringBuilder hdsb3 = new StringBuilder();
        hdsb3.append(formatUtil.padX(" 代付日期： ", 12));
        hdsb3.append(reportUtil.customFormat(data, "999/99/99"));
        hdsb3.append(formatUtil.padX("", 43));
        hdsb3.append(formatUtil.padX(" 頁　　數： ", 12));
        hdsb3.append(reportUtil.customFormat("" + page, "ZZZZ"));
        // 放進來算一行
        fileC073Contents.add(formatUtil.padX(hdsb3.toString(), 140));

        // 表頭第四行
        StringBuilder hdsb4 = new StringBuilder();
        // 放進來算一行
        fileC073Contents.add(formatUtil.padX(hdsb4.toString(), 1));

        // 表頭第五行
        StringBuilder hdsb5 = new StringBuilder();
        hdsb5.append(formatUtil.padX(" 所站別 ", 8));
        hdsb5.append(formatUtil.padX(" 代付分行 ", 10));
        hdsb5.append(formatUtil.padX(" 銷帳編號 ", 16));
        hdsb5.append(formatUtil.padX(" 交易日期 ", 10));
        hdsb5.append(formatUtil.padX(" 時間 ", 6));
        hdsb5.append(formatUtil.padX(" 金額 ", 6));
        hdsb5.append(formatUtil.padX(" 帳務別 ", 8));
        hdsb5.append(formatUtil.padX(" 備註 ", 6));
        // 放進來算一行
        fileC073Contents.add(formatUtil.padX(hdsb5.toString(), 170));

        // 放進來算一行
        fileC073Contents.add(reportUtil.makeGate("=", 90));
    }

    private void edsb(String stano, int stanocount, BigDecimal stalaml) {
        // 空一行
        // 放進來算一行
        fileC073Contents.add(formatUtil.padX("", 170));

        // 表尾第一行
        StringBuilder edsb1 = new StringBuilder();
        edsb1.append(formatUtil.padX(" 所站別： ", 10));
        if (stano.equals("040")) {
            stanoX = "臺北區監理所";
        } else if (stano.equals("041")) {
            stanoX = "板橋監理站";
        } else if (stano.equals("046")) {
            stanoX = "蘆洲監理站";
        } else if (stano.equals("033")) {
            stanoX = "基隆監理站";
        } else if (stano.equals("042")) {
            stanoX = "基隆監理站";
        } else if (stano.equals("043")) {
            stanoX = "宜蘭監理站";
        } else if (stano.equals("044")) {
            stanoX = "花蓮監理站";
        } else if (stano.equals("045")) {
            stanoX = "玉里監理分站";
        } else if (stano.equals("050")) {
            stanoX = "新竹區監理所";
        } else if (stano.equals("051")) {
            stanoX = "新竹市監理站";
        } else if (stano.equals("052")) {
            stanoX = "桃園監理站";
        } else if (stano.equals("053")) {
            stanoX = "中壢監理站";
        } else if (stano.equals("054")) {
            stanoX = "苗栗監理站";
        } else if (stano.equals("060")) {
            stanoX = "臺中區監理所";
        } else if (stano.equals("061")) {
            stanoX = "臺中市監理站";
        } else if (stano.equals("062")) {
            stanoX = "埔里監理分站";
        } else if (stano.equals("064")) {
            stanoX = "彰化監理站";
        } else if (stano.equals("065")) {
            stanoX = "南投監理站";
        } else if (stano.equals("066")) {
            stanoX = "南投監理站";
        } else if (stano.equals("063")) {
            stanoX = "豐原監理站";
        } else if (stano.equals("070")) {
            stanoX = "嘉義區監理所";
        } else if (stano.equals("076")) {
            stanoX = "嘉義市監理站";
        } else if (stano.equals("073")) {
            stanoX = "新營監理站";
        } else if (stano.equals("075")) {
            stanoX = "麻豆監理站";
        } else if (stano.equals("074")) {
            stanoX = "臺南監理站";
        } else if (stano.equals("072")) {
            stanoX = "雲林監理站";
        } else if (stano.equals("071")) {
            stanoX = "雲林監理站東勢分站";
        } else if (stano.equals("080")) {
            stanoX = "高雄區監理所";
        } else if (stano.equals("082")) {
            stanoX = "屏東監理站";
        } else if (stano.equals("085")) {
            stanoX = "旗山監理站";
        } else if (stano.equals("025")) {
            stanoX = "旗山監理站";
        } else if (stano.equals("081")) {
            stanoX = "臺東監理站";
        } else if (stano.equals("083")) {
            stanoX = "恆春監理分站";
        } else if (stano.equals("084")) {
            stanoX = "澎湖監理站";
        } else {
            stanoX = " ";
        }
        edsb1.append(formatUtil.padX(stano, 2)); // 所站別代號
        edsb1.append(formatUtil.padX("", 1));
        edsb1.append(formatUtil.padX(stanoX, 20)); // 所站別名稱
        edsb1.append(formatUtil.padX(" 所站小計筆數： ", 16));
        edsb1.append(reportUtil.customFormat("" + stanocount, "ZZZ,ZZ9"));
        edsb1.append(formatUtil.padX(" 小計金額： ", 12));
        edsb1.append(reportUtil.customFormat("" + stalaml, "Z,ZZZ,ZZZ,ZZZ,ZZ9"));

        // 放進來算一行
        fileC073Contents.add(formatUtil.padX(edsb1.toString(), 170));
    }

    private void edsbone(int cllbrcount, BigDecimal subtalaml) {
        // 空一行
        // 放進來算一行
        fileC073Contents.add("");

        // 表尾第三行
        StringBuilder edsb3 = new StringBuilder();
        edsb3.append(formatUtil.padX(" ", 6));
        edsb3.append(formatUtil.padX("**", 2));
        edsb3.append(formatUtil.padX(" ", 1));
        edsb3.append(formatUtil.padX(" 分行小計筆數 :", 18));
        edsb3.append(formatUtil.padX("", 3));
        edsb3.append(reportUtil.customFormat("" + cllbrcount, "ZZZ,ZZ9"));
        edsb3.append(formatUtil.padX(" ", 10));
        edsb3.append(formatUtil.padX("     小計金額 :", 18));
        edsb3.append(formatUtil.padX(" ", 5));
        edsb3.append(reportUtil.customFormat("" + subtalaml, "Z,ZZZ,ZZZ,ZZZ,ZZ9"));
        // 放進來算一行
        fileC073Contents.add(formatUtil.padX(edsb3.toString(), 170));
    }

    private void edsbtwo(int talcount, BigDecimal talaml) {
        // 最後表尾第一行
        fileC073Contents.add("");
        // 放進來算一行
        fileC073Contents.add(reportUtil.makeGate("=", 90));

        // 最後表尾第三行
        StringBuilder edsb3 = new StringBuilder();
        edsb3.append(formatUtil.padX(" ", 9));
        edsb3.append(formatUtil.padX(" 總筆數 :", 18));
        edsb3.append(formatUtil.padX(" ", 9));
        edsb3.append(reportUtil.customFormat("" + talcount, "ZZZ,ZZ9"));
        edsb3.append(formatUtil.padX(" ", 10));
        edsb3.append(formatUtil.padX("     總金額 :", 18));
        edsb3.append(formatUtil.padX(" ", 7));
        edsb3.append(reportUtil.customFormat("" + talaml, "Z,ZZZ,ZZZ,ZZZ,ZZ9"));
        // 放進來算一行
        fileC073Contents.add(formatUtil.padX(edsb3.toString(), 170));

        // 最後表尾第四行
        // 放進來算一行
        fileC073Contents.add("");

        // 最後表尾第五行
        // 放進來算一行
        fileC073Contents.add("");

        // 最後表尾第六行
        // 放進來算一行
        fileC073Contents.add("");

        // 最後表尾第七行
        StringBuilder edsb7 = new StringBuilder();
        edsb7.append(formatUtil.padX(" ", 38));
        edsb7.append(formatUtil.padX(" 經　辦： ", 10));
        edsb7.append(formatUtil.padX(" ", 32));
        edsb7.append(formatUtil.padX(" 主　管： ", 10));
        // 放進來算一行
        fileC073Contents.add(formatUtil.padX(edsb7.toString(), 120));
    }

    private void batchResponse() {
        Map<String, String> responseTextMap = new HashMap<>(textMap);
        responseTextMap.put("RPTNAME", FILE_NAME);

        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", responseTextMap);
    }
}
