/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.C0121;
import com.bot.ncl.dto.entities.ClmrbyIntervalPbrnoBus;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.ncl.util.fileVo.FileC012;
import com.bot.ncl.util.fileVo.FileSortC012;
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
import com.bot.txcontrol.util.text.format.Text2VoFormatter;
import com.bot.txcontrol.util.text.format.Vo2TextFormatter;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("C0121Lsnr")
@Scope("prototype")
public class C0121Lsnr extends BatchListenerCase<C0121> {
    @Autowired private ClmrService clmrlService;
    @Autowired private FormatUtil formatUtil;
    @Autowired private TextFileUtil textFile;
    @Autowired private ExternalSortUtil externalSortUtil;
    @Autowired private FileC012 fileC012;
    @Autowired private Parse parse;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private Vo2TextFormatter vo2TextFormatter;
    @Autowired private FileSortC012 fileSortC012;
    @Autowired private ReportUtil reportUtil;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> labelMap;
    private static final String CHARSET = "UTF-8";
    private static final String CHARSET5 = "Big5";
    private static final String CONVF_RPT = "RPT";
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;
    private C0121 event;
    private List<String> fileOutPathContents; // 檔案內容
    private String processDate; // 作業日期(民國年yyyymmdd)
    private StringBuilder sb = new StringBuilder();
    private final int pageCnts = 100000;
    private int pageIndex = 0;

    private String fileC0121Path;

    private String fileSortC0121Path;

    private String fileOutPath;

    private List<String> putfCtlFileContents;

    @Override
    public void onApplicationEvent(C0121 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C0121Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(C0121 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C0121Lsnr run()");

        init(event);

        queryCldtl();
    }

    private void init(C0121 event) {
        this.event = event;

        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();

        processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱
        fileC0121Path =
                fileDir + CONVF_DATA + PATH_SEPARATOR + processDate + PATH_SEPARATOR + "C012";
        fileSortC0121Path =
                fileDir + CONVF_DATA + PATH_SEPARATOR + processDate + PATH_SEPARATOR + "SORT_C012";
        fileOutPath =
                fileDir
                        + CONVF_RPT
                        + PATH_SEPARATOR
                        + processDate
                        + PATH_SEPARATOR
                        + "CL-BH-C012-1";
        // 開啟檔案
        // OPEN INPUT FD-C012
        putfCtlFileContents = textFile.readFileContent(fileC0121Path, CHARSET);
        if (Objects.isNull(putfCtlFileContents) || putfCtlFileContents.isEmpty()) {
            throw new LogicException("", "C012無資料");
        }
        textFile.deleteFile(fileOutPath);
        textFile.deleteFile(fileSortC0121Path);
    }

    private void queryCldtl() throws IOException {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C0121Lsnr queryCldtl");
        // C012檔案讀出進新檔案
        infile();
        // 排序資料
        sortfile();
        // 與clmr結合 有相符合出資料 沒有的出nodata
        outputclmr();
    }

    private void infile() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "infile()");
        // 循序讀取FD-C012，檔尾，結束本段程式
        List<String> sortFileContents = new ArrayList<>();
        for (String putfCtlFileContent : putfCtlFileContents) {
            text2VoFormatter.format(putfCtlFileContent, fileC012);
            fileSortC012 = new FileSortC012();

            fileSortC012.setCode(fileC012.getCode());
            fileSortC012.setPbrno(fileC012.getPbrno());
            fileSortC012.setCname(fileC012.getCname());
            fileSortC012.setEntpno(fileC012.getEntpno());
            fileSortC012.setHentpno(fileC012.getHentpno());
            fileSortC012.setStop(fileC012.getStop());
            fileSortC012.setDate(fileC012.getDate());
            fileSortC012.setActno(fileC012.getActno());
            fileSortC012.setPbrnonm(fileC012.getPbrnonm());
            fileSortC012.setFiller(fileC012.getFiller());

            sortFileContents.add(vo2TextFormatter.formatRS(fileSortC012, false));
        }
        textFile.writeFileContent(fileSortC0121Path, sortFileContents, CHARSET);
    }

    private void sortfile() throws IOException {
        File tmpFile = new File(fileSortC0121Path);
        List<KeyRange> keyRanges = new ArrayList<>();
        keyRanges.add(new KeyRange(7, 3, SortBy.ASC));
        keyRanges.add(new KeyRange(1, 6, SortBy.ASC));
        keyRanges.add(new KeyRange(50, 10, SortBy.ASC));
        keyRanges.add(new KeyRange(60, 8, SortBy.ASC));
        keyRanges.add(new KeyRange(69, 8, SortBy.ASC));
        externalSortUtil.sortingFile(tmpFile, tmpFile, keyRanges);
    }

    private void outputclmr() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "outputclmr()");
        // 日期
        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        DateFormat sdftime = new SimpleDateFormat("HH:mm");
        String ntime = sdftime.format(date);
        String today = sdf.format(new Date());
        String nowtoday = "" + (parse.string2Integer(today) - 19110000);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "usedate  = {}", nowtoday);

        String nowdate = reportUtil.customFormat(nowtoday, "Z99/99/99") + " " + ntime;
        // 頁數
        int page = 0;
        ApLogHelper.info(
                log,
                false,
                LogType.NORMAL.getCode(),
                "batchDate()" + parse.string2Integer(processDate));

        // entdy 標題中華民國 年 月 日
        int yyyy = parse.string2Integer(processDate.substring(0, 3));
        int mm = parse.string2Integer(processDate.substring(3, 5));
        int dd = parse.string2Integer(processDate.substring(5, 7));

        // 先讀sort過的檔案
        List<String> lines = textFile.readFileContent(fileSortC0121Path, CHARSET);
        // 拿取pbrno跟clmr的資料有符合拿出sort檔案裡面的資料，無符合就出nodata
        List<ClmrbyIntervalPbrnoBus> lclmr =
                clmrlService.findbyIntervalPbrno(1, 999, pageIndex, pageCnts);
        List<String> clmrpbrno = new ArrayList<>();
        int noclmr = 0;
        int knoclmr = 0;
        for (ClmrbyIntervalPbrnoBus tclmr : lclmr) {
            noclmr = tclmr.getPbrno();
            if (noclmr != knoclmr) {
                clmrpbrno.add(Integer.toString(tclmr.getPbrno()));
            }
            knoclmr = noclmr;
        }
        String filpbrno = "";
        String filcode = "";
        String iwkPbrno = "";
        String lineno = "";
        boolean nodate;
        String siwkPbrno = "";
        String filecname = "";
        String fileentpno = "";
        String filehentpno = "";
        String filestop = "";
        String fileedate = "";
        String filepbrnom = "";
        int ix = 0;
        StringBuilder se = new StringBuilder();
        int z = 0;
        boolean notprint = false;
        fileOutPathContents = new ArrayList<>();
        for (int i = 0; i < clmrpbrno.size(); i++) {
            nodate = true;
            iwkPbrno = clmrpbrno.get(i);
            for (int j = 0; j < lines.size(); j++) {
                StringBuilder sb = new StringBuilder();
                lineno = lines.get(j);
                // 代收類別
                filcode = lineno.substring(0, 6).trim();
                // 主辦分行
                filpbrno = lineno.substring(6, 9).trim();
                // 單位中文名
                filecname = lineno.substring(9, 49).trim();
                // 營利事業編號
                fileentpno = lineno.substring(49, 54).trim();
                // 總公司統編
                filehentpno = lineno.substring(54, 62);
                // 代收狀態（異動前）
                filestop = lineno.substring(62, 63).trim();
                // 建檔日
                fileedate = lineno.substring(64, 71);
                // 主辦分行中文
                filepbrnom = lineno.substring(83, 93);

                if (iwkPbrno.equals(filpbrno)) {
                    nodate = false;
                    if ((filcode != null && !filcode.isEmpty() && filcode.length() > 0)
                            && (!filpbrno.equals('0'))) {
                        ix++;

                        // 第一次的時候印表投頭
                        if (ix == 1) {
                            hdsb(iwkPbrno, filepbrnom, nowdate, page, yyyy, mm, dd);
                        }
                        // 如果分行有66筆類別 要換夜
                        if (ix == 66) {
                            page++;
                            hdsb(iwkPbrno, filepbrnom, nowdate, page, yyyy, mm, dd);
                        }
                        sb.append(formatUtil.padX(" ", 2));
                        sb.append(formatUtil.padX(" ", 2));
                        sb.append(filcode);
                        sb.append(formatUtil.padX(" ", 2));
                        sb.append(filecname.trim());
                        sb.append(formatUtil.padX(" ", 20 - filecname.trim().length()));
                        sb.append(fileentpno.trim());
                        sb.append(formatUtil.padX(" ", 5 - fileentpno.trim().length()));
                        sb.append(formatUtil.padX(" ", 3));
                        sb.append(filehentpno.trim());
                        sb.append(formatUtil.padX(" ", 8 - filehentpno.trim().length()));
                        sb.append(formatUtil.padX(" ", 2));

                        int buff = parse.string2Integer(filestop);
                        String buffst = "";
                        if (buff == 0) {
                            buffst = "０－代收中";
                        } else if (buff == 1) {
                            buffst = "１－暫停代收";
                        } else if (buff == 2) {
                            buffst = "２－解約";
                        } else if (buff == 3) {
                            buffst = "３－僅主辦行可收";
                        } else if (buff == 4) {
                            buffst = "４－僅自動化機器可收（主辦行可臨櫃代收）";
                        } else if (buff == 5) {
                            buffst = "５－僅自動化機器或臨櫃轉帳（不能收現）";
                        } else if (buff == 6) {
                            buffst = "６－僅能臨櫃繳款";
                        } else if (buff == 7) {
                            buffst = "７－僅臨櫃與匯款";
                        } else if (buff == 8) {
                            buffst = "８－僅自動化機器可收";
                        } else {
                            buffst = " ";
                        }

                        sb.append(buffst);
                        sb.append(formatUtil.padX(" ", 42 - (buffst.length() * 2)));
                        sb.append(formatUtil.padX(" ", 23));
                        sb.append(fileedate);

                        fileOutPathContents.add(formatUtil.padX(sb.toString(), 170));
                    } else {
                        notprint = true;
                    }
                }
            }

            if (nodate && !iwkPbrno.equals(siwkPbrno)) {
                se = new StringBuilder();
                z++;
                if (z == 1) {
                    page = 1;
                    filepbrnom = "";
                    hdsb(iwkPbrno, filepbrnom, nowdate, page, yyyy, mm, dd);
                    String s = " ******* 本月無資料 *******";
                    se.append(s);
                    fileOutPathContents.add(formatUtil.padX(se.toString(), 170));
                    //                    edsb();
                }
            }
            if (notprint) {
                notprint = false;
            } else {
                edsb();
            }
            siwkPbrno = iwkPbrno;
            z = 0;
            ix = 0;
            page = 0;
        }
        textFile.deleteFile(fileSortC0121Path);
        textFile.writeFileContent(fileOutPath, fileOutPathContents, CHARSET5);
    }

    private void hdsb(
            String iwkPbrno,
            String filepbrnom,
            String nowdate,
            int page,
            int yyyy,
            int mm,
            int dd) {
        // 表頭 第一行
        StringBuilder hdsb1 = new StringBuilder();
        hdsb1.append(formatUtil.padX(" ", 34));
        hdsb1.append(formatUtil.padX("主辦分行之電子化收款客戶名單（已解約）－全部", 50));
        fileOutPathContents.add(formatUtil.padX(hdsb1.toString(), 78));

        // 第二行
        StringBuilder hdsb2 = new StringBuilder();
        hdsb2.append(formatUtil.padX(" ", 1));
        hdsb2.append(formatUtil.padX(" 分行別　： ", 12));
        hdsb2.append(formatUtil.pad9(iwkPbrno, 3));
        if (filepbrnom == null || filepbrnom.isEmpty()) {
            hdsb2.append(formatUtil.padX(" ", 20));
        } else {
            hdsb2.append(filepbrnom);
        }
        hdsb2.append(formatUtil.padX(" ", 28));
        hdsb2.append(formatUtil.padX("中華民國 ", 10));
        hdsb2.append(formatUtil.pad9(yyyy + "", 3));
        hdsb2.append(formatUtil.padX("年  ", 4));
        hdsb2.append(formatUtil.pad9(mm + "", 2));
        hdsb2.append(formatUtil.padX("月  ", 4));
        hdsb2.append(formatUtil.pad9(dd + "", 2));
        hdsb2.append(formatUtil.padX("日 ", 3));
        hdsb2.append(formatUtil.padX(" ", 41));
        hdsb2.append(formatUtil.padX("報表代號： CL-C012", 19));
        fileOutPathContents.add(formatUtil.padX(hdsb2.toString(), 175));

        // 第三行
        StringBuilder hdsb3 = new StringBuilder();
        hdsb3.append(formatUtil.padX(" ", 1));
        hdsb3.append(formatUtil.padX(" 列印時間： ", 12));
        hdsb3.append(formatUtil.padX(nowdate, 15));
        hdsb3.append(formatUtil.padX(" ", 105));
        hdsb3.append(formatUtil.padX("頁　　次： ", 12));
        hdsb3.append(formatUtil.pad9(page + "", 2));
        fileOutPathContents.add(formatUtil.padX(hdsb3.toString(), 150));

        // 第四行
        StringBuilder hdsb4 = new StringBuilder();
        hdsb4.append(formatUtil.padX(" ", 1));
        hdsb4.append(formatUtil.padX(" 代收類別 ", 10));
        hdsb4.append(formatUtil.padX(" ", 1));
        hdsb4.append(formatUtil.padX(" 單位名稱 ", 10));
        hdsb4.append(formatUtil.padX(" ", 30));
        hdsb4.append(formatUtil.padX(" 營利事業編號 ", 13));
        hdsb4.append(formatUtil.padX(" ", 1));
        hdsb4.append(formatUtil.padX(" 總公司統編 ", 12));
        hdsb4.append(formatUtil.padX(" 代收狀態 ", 10));
        hdsb4.append(formatUtil.padX(" ", 35));
        hdsb4.append(formatUtil.padX(" 備註 ", 6));
        hdsb4.append(formatUtil.padX(" ", 11));
        hdsb4.append(formatUtil.padX(" 建檔日 ", 8));
        fileOutPathContents.add(formatUtil.padX(hdsb4.toString(), 155));

        // 第五行
        StringBuilder hdsb5 = new StringBuilder();
        hdsb5.append(
                formatUtil.padX(
                        " =====================================================================================================================================================",
                        151));
        fileOutPathContents.add(formatUtil.padX(hdsb5.toString(), 170));
    }

    private void edsb() {
        // 表尾第一行
        StringBuilder edsb1 = new StringBuilder();
        edsb1.append(
                formatUtil.padX(
                        " ------------------------------------------------------------------------------------------------------------------------------------------------------",
                        151));
        fileOutPathContents.add(formatUtil.padX(edsb1.toString(), 170));
        // 空行
        StringBuilder edsb2 = new StringBuilder();
        edsb2.append(formatUtil.padX(" ", 150));
        fileOutPathContents.add(formatUtil.padX(edsb2.toString(), 170));
    }
}
