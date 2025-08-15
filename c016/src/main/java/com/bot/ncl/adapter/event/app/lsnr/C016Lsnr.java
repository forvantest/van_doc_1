/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.C016;
import com.bot.ncl.util.fileVo.FileC016;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.ncl.util.sort.ExternalSortUtil;
import com.bot.ncl.util.sort.eum.SortBy;
import com.bot.ncl.util.sort.vo.KeyRange;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.text.format.Text2VoFormatter;
import com.bot.txcontrol.util.text.format.Vo2TextFormatter;
import java.io.File;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("C016Lsnr")
@Scope("prototype")
public class C016Lsnr extends BatchListenerCase<C016> {
    @Autowired private TextFileUtil textFile;
    @Autowired private FileC016 fileC016;
    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private Vo2TextFormatter vo2TextFormatter;
    @Autowired private ExternalSortUtil externalSortUtil;
    @Autowired private Parse parse;
    @Autowired private ReportUtil reportUtil;
    @Autowired private FormatUtil formatUtil;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> labelMap;

    private static final String CHARSET = "UTF-8";
    private static final String CHARSET5 = "Big5";
    private List<String> fileOutPathContents; // 檔案內容
    private C016 event;
    private String batchDate; // 批次日期(民國年yyyymmdd)
    private String fileC016Path;
    private String fileSortC016Path;
    private String fileOutPath;
    private List<String> putfCtlFileContents;
    DecimalFormat df = new DecimalFormat("#,###");

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(C016 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C016Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(C016 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C016Lsnr run()");

        init(event);

        queryCldtl();
    }

    private void init(C016 event) {
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();

        batchDate = labelMap.get("BBSDY"); // 待中菲APPLE提供正確名稱

        fileC016Path = fileDir + "RPT-016";
        fileSortC016Path = fileDir + "SORT_C016";
        fileOutPath = fileDir + "CL-BH-016";
        // 開啟檔案
        // C016
        putfCtlFileContents = textFile.readFileContent(fileC016Path, CHARSET);
        if (Objects.isNull(putfCtlFileContents) || putfCtlFileContents.isEmpty()) {
            ApLogHelper.error(log, false, LogType.NORMAL.getCode(), "putfCtlFileContents is null");
            return;
        }

        textFile.deleteFile(fileOutPath);
        textFile.deleteFile(fileSortC016Path);
    }

    private void queryCldtl() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C016Lsnr queryCldtl");
        // C016檔案讀出進新檔案
        infile();
        // 排序檔案資料
        sortfile();
        // 產出
        output();
        textFile.deleteFile(fileSortC016Path);
    }

    private void infile() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "infile()");
        // 循序讀取FD-C012，檔尾，結束本段程式
        List<String> sFileContents = new ArrayList<>();
        for (String putfCtlFileContent : putfCtlFileContents) {
            text2VoFormatter.format(putfCtlFileContent, fileC016);
            sFileContents.add(vo2TextFormatter.formatRS(fileC016, false));
        }
        textFile.writeFileContent(fileSortC016Path, sFileContents, CHARSET);
    }

    private void sortfile() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "toWriteC016File sortfile");
        File tmpFile = new File(fileSortC016Path);
        List<KeyRange> keyRanges = new ArrayList<>();
        keyRanges.add(new KeyRange(1, 6, SortBy.ASC));
        keyRanges.add(new KeyRange(7, 7, SortBy.ASC));

        externalSortUtil.sortingFile(tmpFile, tmpFile, keyRanges, CHARSET);
    }

    private void output() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "output()");
        // 日期
        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        DateFormat sdftime = new SimpleDateFormat("HH:mm");
        String ntime = sdftime.format(date);
        String today = sdf.format(new Date());
        String nowtoday = "" + (parse.string2Integer(today) - 19110000);
        String nowdate = reportUtil.customFormat(nowtoday, "Z99/99/99") + " " + ntime;
        // 頁數
        int page = 0;

        // 先讀sort過的檔案
        String lineno = "";
        String filecode = "";
        String fileedate = "";
        int fileamt = 0;
        int filecount = 0;
        String wfilecode = "";
        String wfileedate = "";
        int iallfileamt = 0;
        int iallfilecount = 0;
        int wallfileamt = 0;
        int ewallfileamt = 0;
        String filecodename = "";
        int yyyy = 0;
        int mm = 0;
        int dd = 0;
        fileOutPathContents = new ArrayList<>();
        List<String> lines = textFile.readFileContent(fileSortC016Path, CHARSET);
        int z = lines.size();
        boolean flageo = false;
        for (int i = 0; i < lines.size(); i++) {
            z--;
            StringBuilder sb = new StringBuilder();
            lineno = lines.get(i);
            // 代收類別
            filecode = lineno.substring(0, 6).trim();
            // 代收日期
            fileedate = lineno.substring(6, 13).trim();
            // 總金額
            fileamt = parse.string2Integer(lineno.substring(13, 23).trim());
            // 總件數
            filecount = 1;
            // 備註

            // 先把資料跟站存後的資料作比對，日期類別一樣 ->不印   類別一樣 日期不一樣->印  類別不一樣 日期不一樣->換頁印
            // 相同類別作加總
            if (wfilecode.equals(filecode)) {
                ewallfileamt += wallfileamt;
            }
            //  相同代收類別相同日期則續作加總
            if (wfilecode.equals(filecode) && wfileedate.equals(fileedate)) {
                iallfileamt += wallfileamt;
                iallfilecount += filecount;
            }

            if (!wfileedate.isEmpty()) {
                // entdy 標題中華民國 年 月 日
                yyyy = parse.string2Integer(wfileedate.substring(0, 3));
                mm = parse.string2Integer(wfileedate.substring(3, 5));
                dd = parse.string2Integer(wfileedate.substring(5, 7));
            }
            // 設定代收名稱
            if (wfilecode.equals("510020")) {
                filecodename = "台北市停車費";
            } else if (wfilecode.equals("510030")) {
                filecodename = "高雄市停車費";
            } else if (wfilecode.equals("510100")) {
                filecodename = "嘉義市停車費";
            } else if (wfilecode.equals("510110")) {
                filecodename = "基隆市停車費";
            } else if (wfilecode.equals("510090")) {
                filecodename = "台北縣停車費";
            } else if (wfilecode.equals("510120")) {
                filecodename = "桃園縣停車費";
            } else if (wfilecode.equals("510130")) {
                filecodename = "新竹縣停車費";
            } else if (wfilecode.equals("510140")) {
                filecodename = "新竹市停車費";
            } else if (wfilecode.equals("510150")) {
                filecodename = "苗栗縣停車費";
            } else if (wfilecode.equals("510160")) {
                filecodename = "台中縣停車費";
            } else if (wfilecode.equals("510170")) {
                filecodename = "台中市停車費";
            } else if (wfilecode.equals("510180")) {
                filecodename = "彰化縣停車費";
            } else if (wfilecode.equals("510190")) {
                filecodename = "南投縣停車費";
            } else if (wfilecode.equals("510200")) {
                filecodename = "雲林縣停車費";
            } else if (wfilecode.equals("510210")) {
                filecodename = "嘉義縣停車費";
            } else if (wfilecode.equals("510220")) {
                filecodename = "台南縣停車費";
            } else if (wfilecode.equals("510080")) {
                filecodename = "台南市停車費";
            } else if (wfilecode.equals("510230")) {
                filecodename = "高雄縣停車費";
            } else if (wfilecode.equals("510240")) {
                filecodename = "屏東縣停車費";
            } else if (wfilecode.equals("510250")) {
                filecodename = "澎湖縣停車費";
            } else if (wfilecode.equals("510260")) {
                filecodename = "宜蘭縣停車費";
            } else if (wfilecode.equals("510270")) {
                filecodename = "花蓮縣停車費";
            } else if (wfilecode.equals("510280")) {
                filecodename = "台東縣停車費";
            } else if (wfilecode.equals("510290")) {
                filecodename = "金門縣停車費";
            } else if (wfilecode.equals("510300")) {
                filecodename = "馬祖縣停車費";
            } else {
                filecodename = "";
            }

            // 相同代收類別不同日期則將前一日期資料寫入報表檔中
            if (wfilecode.equals(filecode) && !wfileedate.equals(fileedate)) {
                flageo = true;
                hdsb(filecodename, nowdate);
                iallfileamt += wallfileamt;
                iallfilecount += filecount;
                sb = new StringBuilder();
                sb.append(formatUtil.padX(" ", 5));
                sb.append(
                        formatUtil.pad9(yyyy + "", 3)
                                + "/"
                                + formatUtil.pad9(mm + "", 2)
                                + "/"
                                + formatUtil.pad9(dd + "", 2));
                sb.append(formatUtil.padX(" ", 3));
                sb.append(formatUtil.padLeft(df.format(iallfileamt) + "", 11));
                sb.append(formatUtil.padX(" ", 3));
                sb.append(formatUtil.padLeft(iallfilecount + "", 10));
                fileOutPathContents.add(formatUtil.padX(sb.toString(), 170));

                iallfileamt = 0;
                iallfilecount = 0;
            }

            // 相同代收類別相同日期則續作加總的最後一筆印出
            if (!wfilecode.isEmpty() && !wfilecode.equals(filecode)) {
                if (!flageo) {
                    hdsb(filecodename, nowdate);
                }
                iallfileamt += wallfileamt;
                iallfilecount += filecount;
                ewallfileamt += wallfileamt;
                sb = new StringBuilder();
                sb.append(formatUtil.padX(" ", 5));
                sb.append(
                        formatUtil.pad9(yyyy + "", 3)
                                + "/"
                                + formatUtil.pad9(mm + "", 2)
                                + "/"
                                + formatUtil.pad9(dd + "", 2));
                sb.append(formatUtil.padX(" ", 3));
                sb.append(formatUtil.padLeft(df.format(iallfileamt) + "", 11));
                sb.append(formatUtil.padX(" ", 3));
                sb.append(formatUtil.padLeft(iallfilecount + "", 10));
                fileOutPathContents.add(formatUtil.padX(sb.toString(), 170));
                edsb(ewallfileamt);
                iallfileamt = 0;
                iallfilecount = 0;
                ewallfileamt = 0;
                flageo = false;
            }
            // 最後一筆印出
            if (z == 0) {
                if (!flageo) {
                    hdsb(filecodename, nowdate);
                }
                iallfileamt += fileamt;
                iallfilecount += filecount;
                ewallfileamt += fileamt;
                sb = new StringBuilder();
                sb.append(formatUtil.padX(" ", 5));
                sb.append(
                        formatUtil.pad9(yyyy + "", 3)
                                + "/"
                                + formatUtil.pad9(mm + "", 2)
                                + "/"
                                + formatUtil.pad9(dd + "", 2));
                sb.append(formatUtil.padX(" ", 3));
                sb.append(formatUtil.padLeft(df.format(iallfileamt) + "", 11));
                sb.append(formatUtil.padX(" ", 3));
                sb.append(formatUtil.padLeft(iallfilecount + "", 10));
                fileOutPathContents.add(formatUtil.padX(sb.toString(), 170));
                edsb(ewallfileamt);
            }
            // 都要暫存上一次的結果，下一次如果有不相同就用上次的印出
            wfilecode = filecode;
            wfileedate = fileedate;
            wallfileamt = fileamt;
        }

        textFile.deleteFile(fileSortC016Path);
        textFile.writeFileContent(fileOutPath, fileOutPathContents, CHARSET5);
    }

    private void hdsb(String filecodename, String nowdate) {
        // 表頭 第一行
        StringBuilder hd = new StringBuilder();
        hd.append(formatUtil.padX(" ", 4));
        fileOutPathContents.add(formatUtil.padX(hd.toString(), 80));

        // 表頭 第一行
        StringBuilder hdsb1 = new StringBuilder();
        hdsb1.append(formatUtil.padX(" ", 4));
        hdsb1.append(formatUtil.padX("代收單位：台灣銀行", 18));
        hdsb1.append(formatUtil.padX(" ", 13));
        hdsb1.append(formatUtil.padX("月　結　清　單", 14));
        hdsb1.append(formatUtil.padX(" ", 12));
        hdsb1.append(formatUtil.padX("印表日：", 8));
        hdsb1.append(formatUtil.padX(nowdate, 9));
        fileOutPathContents.add(formatUtil.padX(hdsb1.toString(), 80));

        // 第二行
        StringBuilder hdsb2 = new StringBuilder();
        hdsb2.append(formatUtil.padX(" ", 4));
        hdsb2.append(formatUtil.padX("代收名稱：" + filecodename, 24));
        hdsb2.append(formatUtil.padX(" ", 33));
        hdsb2.append(formatUtil.padX("頁　次：", 8));
        hdsb2.append(formatUtil.padX(" ", 1));
        hdsb2.append(formatUtil.padX(" 1", 3));
        fileOutPathContents.add(formatUtil.padX(hdsb2.toString(), 80));

        // 第三行
        StringBuilder hdsb3 = new StringBuilder();
        hdsb3.append(formatUtil.padX(" ", 4));
        hdsb3.append(formatUtil.padX("主辦分行：", 10));
        hdsb3.append(formatUtil.padX("003", 6));
        hdsb3.append(formatUtil.padX(" ", 41));
        hdsb3.append(formatUtil.padX("報表名稱：C016", 18));
        fileOutPathContents.add(formatUtil.padX(hdsb3.toString(), 100));

        // 第四行
        StringBuilder hdsb4 = new StringBuilder();
        hdsb4.append(formatUtil.padX(" ", 5));
        hdsb4.append(formatUtil.padX("代收日期", 8));
        hdsb4.append(formatUtil.padX(" ", 8));
        hdsb4.append(formatUtil.padX("總金額", 6));
        hdsb4.append(formatUtil.padX(" ", 7));
        hdsb4.append(formatUtil.padX("總件數", 6));
        hdsb4.append(formatUtil.padX(" ", 16));
        hdsb4.append(formatUtil.padX("備註", 4));
        fileOutPathContents.add(formatUtil.padX(hdsb4.toString(), 80));

        // 第五行
        StringBuilder hdsb5 = new StringBuilder();
        hdsb5.append(
                formatUtil.padX(
                        " --------------------------------------------------------------------------------",
                        81));
        fileOutPathContents.add(formatUtil.padX(hdsb5.toString(), 100));
    }

    private void edsb(int ewallfileamt) {
        // 表尾第一行
        StringBuilder edsb1 = new StringBuilder();
        edsb1.append(formatUtil.padX("       總　計", 13));
        edsb1.append(formatUtil.padX(" ", 1));
        edsb1.append(formatUtil.padLeft(df.format(ewallfileamt) + "", 13));
        fileOutPathContents.add(formatUtil.padX(edsb1.toString(), 170));
    }
}
