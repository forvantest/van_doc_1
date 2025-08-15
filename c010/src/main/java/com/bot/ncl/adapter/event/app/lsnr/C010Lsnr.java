/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.C010;
import com.bot.ncl.dto.entities.*;
import com.bot.ncl.jpa.anq.entities.impl.FindByC010;
import com.bot.ncl.jpa.anq.svc.FindByC010Service;
import com.bot.ncl.jpa.svc.ClfeeService;
import com.bot.ncl.jpa.svc.ClmcService;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.ncl.jpa.svc.CltmrService;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.ncl.util.sort.ExternalSortUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("C010Lsnr")
@Scope("prototype")
public class C010Lsnr extends BatchListenerCase<C010> {
    @Autowired private TextFileUtil textFile;
    @Autowired private Parse parse;
    @Autowired private ClmrService clmrService;
    @Autowired private CltmrService cltmrService;
    @Autowired private ClmcService clmcService;
    @Autowired private ClfeeService clfeeService;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ExternalSortUtil externalSortUtil;
    @Autowired private FindByC010Service sFindByC010Service;
    @Autowired private ReportUtil reportUtil;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> labelMap;

    private static final String FILE_NAME = "CL-BH-C010";
    private static final String CHARSET = "Big5";
    private String outputFilePath; // C073路徑
    private C010 event;
    private List<String> fileC010Contents; // 檔案內容
    private String batchDate; // 批次日期(民國年yyyymmdd)
    private Boolean noData = false;
    private StringBuilder sb = new StringBuilder();
    private final int pageCnts = 100000;
    private int pageIndex = 0;
    private int entdy = 0;

    private String code;
    private String cname;
    private String entpno;
    private String hentpno;
    private int stop = 0;
    private String remark;
    private String appdt;
    private String putname;
    private String clmcputname;
    private int cfeeeb = 0;
    private String mark;
    private String pbrno;
    private String wtpbrno;

    private List<FindByC010> resultQry = new ArrayList<>();

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(C010 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C010Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(C010 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C010Lsnr run()");
        init(event);
        queryCldtl();
        sortOutData();
        writeFile();
    }

    private void init(C010 event) {
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();

        batchDate = labelMap.get("BBSDY"); // 待中菲APPLE提供正確名稱
        entdy = parse.string2Integer(batchDate);

        //        tmpFilePath = fileDir + FILE_TMP_NAME;
        outputFilePath = fileDir + FILE_NAME;
        //        fileTmpContents = new ArrayList<>();
        fileC010Contents = new ArrayList<>();
        // 先刪原本的檔案
        textFile.deleteFile(outputFilePath);
    }

    private void queryCldtl() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C010Lsnr queryCldtl");
        resultQry = sFindByC010Service.findbyC010(0);

        if (resultQry == null || resultQry.isEmpty()) {

            // cldtl已無資料,將剩餘資料寫入檔案
            if (!fileC010Contents.isEmpty()) {
                sb = new StringBuilder();
                String s = " ";
                fileC010Contents.add(s);
                s = "            NO DATA !!";
                fileC010Contents.add(s);
                s = " ";
                fileC010Contents.add(s);
                writeFile();
                fileC010Contents = new ArrayList<>();
            }
        }
    }

    private void sortOutData() {
        int dataTotalSize = resultQry.size();
        int count = 0;
        int icount = 0;
        int page = 0;
        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        DateFormat sdftime = new SimpleDateFormat("HH:mm");
        String ntime = sdftime.format(date);
        String today = sdf.format(new Date());
        String nowtoday = "" + (parse.string2Integer(today) - 19110000);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "usedate  = {}", nowtoday);

        String nowdate = reportUtil.customFormat(nowtoday, "Z99/99/99") + " " + ntime;

        //        String nowdate = "Z99/99/99 99:99";
        for (FindByC010 r : resultQry) {
            count++;

            pbrno = r.getPbrno();
            // 第一筆
            if (count == 1) {
                page++;
                hdsb(pbrno, page, nowdate);
            }

            // 筆數大於39筆就要換頁
            if (icount >= 39) {
                page++;
                StringBuilder sb2 = new StringBuilder();
                sb2 = new StringBuilder();
                sb2.append("\u000c");
                fileC010Contents.add(formatUtil.padX(sb2.toString(), 170));
                hdsb(pbrno, page, nowdate);
                icount = 0;
            }

            if (!pbrno.equals(wtpbrno) && icount > 0) {
                StringBuilder sb2 = new StringBuilder();
                sb2 = new StringBuilder();
                sb2.append("\u000c");
                fileC010Contents.add(formatUtil.padX(sb2.toString(), 170));
                hdsb(pbrno, page, nowdate);
                icount = 0;
                page = 1;
            }
            printDetail(r);

            // 最後一筆 要印出最後總結
            if (dataTotalSize == count) {
                page = 0;
                edsb(count);
            }
            icount++;
            wtpbrno = pbrno;
        }
    }

    private void printDetail(FindByC010 data) {
        code = data.getFindByC010Id().getCode();
        cname = data.getFindByC010Id().getCname();
        pbrno = data.getPbrno();
        entpno = data.getEntpno();
        hentpno = data.getHentpno();
        if (data.getAppdt() == null || data.getAppdt().length() == 0) {
            appdt = "9999999";
        } else {
            appdt = data.getAppdt();
        }
        putname = data.getPutname();
        clmcputname = data.getPutsend() + data.getPutform() + data.getPutname();

        if (clmcputname.equals("X0111332")) {
            remark = "學雜費入口網";
        } else if (clmcputname.equals("X0199556")) {
            remark = "帳單代收服務網";
        } else if (code.substring(0, 1).equals("5")) {
            remark = "全國性繳費業務";
        } else {
            remark = " ";
        }
        stop = data.getStop();
        if (data.getCfeeeb() == null || data.getCfeeeb().length() == 0) {
            cfeeeb = 0;
        } else {
            cfeeeb = parse.string2Integer(data.getCfeeeb());
        }

        if (cfeeeb > 0) {
            mark = "*";
        } else {
            mark = " ";
        }
        // 塞資料
        sb = new StringBuilder();
        sb.append(formatUtil.padX(" ", 1));
        // 代收類別
        sb.append(mark);
        sb.append(formatUtil.padX(" ", 1 - mark.length()));
        //                sb.append(formatUtil.padX(" ", 1));
        sb.append(code);
        sb.append(formatUtil.padX(" ", 6 - code.length()));

        // 單位名稱
        sb.append(formatUtil.padX(" ", 1));
        sb.append(formatUtil.padX(cname, 40));

        // 營利事業編號
        sb.append(formatUtil.padX(" ", 1));
        sb.append(formatUtil.padX(entpno, 10));

        // 總公司統編
        sb.append(formatUtil.padX(" ", 4));
        sb.append(formatUtil.pad9(hentpno + "", 8));

        // 代收狀態
        sb.append(formatUtil.padX(" ", 4));
        sb.append(formatUtil.pad9(stop + "", 42));

        // 備註
        sb.append(formatUtil.padX(" ", 3));
        sb.append(formatUtil.padX(remark, 20));
        // APPDT
        sb.append(formatUtil.pad9(appdt + "", 8));
        sb.append(formatUtil.padX(" ", 1));
        fileC010Contents.add(formatUtil.padX(sb.toString(), 170));
    }

    private void hdsb(String pbrno, int page, String nowdate) {
        // 空一行
        StringBuilder white = new StringBuilder();
        white.append(formatUtil.padX(" ", 1));
        // 放進來算一行
        fileC010Contents.add(formatUtil.padX(white.toString(), 170));

        // 表頭第二行
        StringBuilder hdsb = new StringBuilder();
        hdsb.append(formatUtil.padX(" ", 62));
        hdsb.append(formatUtil.padX("主辦分行之電子化收款客戶名單", 28));
        // 放進來算一行
        fileC010Contents.add(formatUtil.padX(hdsb.toString(), 170));

        // 表頭第二行
        StringBuilder hdsb2 = new StringBuilder();
        hdsb2.append(formatUtil.padX(" ", 2));
        hdsb2.append(formatUtil.padX("分行別　： ", 11));
        hdsb2.append(formatUtil.pad9(pbrno + "", 13));
        hdsb2.append(formatUtil.padX(" ", 106));
        hdsb2.append(formatUtil.padX("報表代號： ", 11));
        hdsb2.append(formatUtil.padX("CL-C010", 7));
        // 放進來算一行
        fileC010Contents.add(formatUtil.padX(hdsb2.toString(), 170));

        // 表頭第三行
        StringBuilder hdsb3 = new StringBuilder();
        hdsb3.append(formatUtil.padX(" ", 2));
        hdsb3.append(formatUtil.padX("列印時間： ", 11));
        hdsb3.append(formatUtil.padX(nowdate, 15));
        hdsb3.append(formatUtil.padX(" ", 104));
        hdsb3.append(formatUtil.padX("頁　　次： ", 11));
        hdsb3.append(formatUtil.pad9(page + "", 2));
        // 放進來算一行
        fileC010Contents.add(formatUtil.padX(hdsb3.toString(), 170));

        // 空一行
        StringBuilder white2 = new StringBuilder();
        white2.append(formatUtil.padX(" ", 1));
        // 放進來算一行
        fileC010Contents.add(formatUtil.padX(white2.toString(), 170));

        // 表頭第四行
        StringBuilder hdsb4 = new StringBuilder();
        hdsb4.append(formatUtil.padX(" ", 2));
        hdsb4.append(formatUtil.padX("代收類別  ", 10));
        hdsb4.append(formatUtil.padX("單位名稱                                ", 40));
        hdsb4.append(formatUtil.padX("營利事業編號  ", 14));
        hdsb4.append(formatUtil.padX("總公司統編  ", 12));
        hdsb4.append(formatUtil.padX("代收狀態                                     ", 45));
        hdsb4.append(formatUtil.padX("備註              ", 18));
        hdsb4.append(formatUtil.padX("建檔日 ", 7));

        // 放進來算一行
        fileC010Contents.add(formatUtil.padX(hdsb4.toString(), 170));

        // 表頭第五行
        StringBuilder hdsb5 = new StringBuilder();
        hdsb5.append(formatUtil.padX(" ", 1));
        hdsb5.append(
                formatUtil.padX(
                        "======================================================================================================================================================",
                        160));
        fileC010Contents.add(formatUtil.padX(hdsb5.toString(), 160));
    }

    private void edsb(int count) {
        // 空一行
        StringBuilder white = new StringBuilder();
        white.append(formatUtil.padX(" ", 1));
        // 放進來算一行
        fileC010Contents.add(formatUtil.padX(white.toString(), 170));
        // 表尾第一行
        StringBuilder edsb1 = new StringBuilder();
        edsb1.append(formatUtil.padX(" ", 3));
        edsb1.append(formatUtil.padX("合計： ", 7));
        edsb1.append(formatUtil.padLeft(count + "", 5));
        fileC010Contents.add(formatUtil.padX(edsb1.toString(), 160));

        // 表尾第二行
        StringBuilder edsb2 = new StringBuilder();
        edsb2.append(formatUtil.padX("   * 號表示交易手續費內含", 25));
        fileC010Contents.add(formatUtil.padX(edsb2.toString(), 160));
    }

    private void writeFile() {
        try {
            // 寫入內容到檔案
            textFile.writeFileContent(outputFilePath, fileC010Contents, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private void moveErrorResponse(LogicException e) {}
}
