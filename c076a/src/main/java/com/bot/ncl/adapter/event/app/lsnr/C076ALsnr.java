/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.C076A;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.sort.ExternalSortUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.adapter.out.grpc.FsapSync;
import com.bot.txcontrol.buffer.TxBizDate;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.date.DateUtil;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("C076ALsnr")
@Scope("prototype")
public class C076ALsnr extends BatchListenerCase<C076A> {
    @Autowired private TextFileUtil textFile;
    @Autowired private Parse parse;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ExternalSortUtil externalSortUtil;
    @Autowired private FsapSync fsapSync;
    @Autowired private DateUtil dateUtil;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> labelMap;
    private String outputFilePath;
    private List<String> fileC076AContents;
    private C076A event;
    private int entdy = 0;
    DecimalFormat df = new DecimalFormat("#,###");
    private String batchDate; // 批次日期(民國年yyyymmdd)
    private String tmpFilePath; // 暫存檔路徑
    private static final String FILE_NAME = "CL-BH-C076-A";
    private static final String FILE_TMP_NAME = "BD/C076";
    int push1Day = 0;
    int push8Day = 0;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(C076A event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C076ALsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(C076A event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C076ALsnr run()");
        init(event);
        queryfile();
    }

    private void init(C076A event) {
        this.event = event;

        fileC076AContents = new ArrayList<>();

        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();

        batchDate = labelMap.get("BBSDY"); // TODO: 待確認BATCH參數名稱

        entdy = parse.string2Integer(batchDate);

        tmpFilePath = fileDir + FILE_TMP_NAME;
        outputFilePath = fileDir + FILE_NAME;
        textFile.deleteFile(outputFilePath);
    }

    private void queryfile() {}

    // 表頭
    private void hdsb(String pbrno, String pbrnom) {

        previousRoc(entdy);
        String previousRocYear = String.valueOf(push8Day).substring(0, 3);
        String previousMonth = String.valueOf(push8Day).substring(3, 5);
        String previousDay = String.valueOf(push8Day).substring(5, 7);
        String previousRocYear1 = String.valueOf(push1Day).substring(0, 3);
        String previousMonth1 = String.valueOf(push1Day).substring(3, 5);
        String previousDay1 = String.valueOf(push1Day).substring(5, 7);
        String nowRocYear = String.valueOf(entdy).substring(0, 3);
        String nowRocMonth = String.valueOf(entdy).substring(3, 5);
        String nowRocDay = String.valueOf(entdy).substring(5, 7);

        //            List<TxBizDate> txBizDates =
        //                    fsapSync.sy202ForAp(event.getPeripheryRequest(), push8Day, push1Day);
        //            txBizDates.get(0).isHliday();

        StringBuilder fhdsb = new StringBuilder();
        fhdsb.append(formatUtil.padX(" ", 32));
        fhdsb.append(formatUtil.padX("代收環境部水污染防治費解繳統計表－依主辦行", 42));
        fileC076AContents.add(formatUtil.padX(fhdsb.toString(), 180));

        StringBuilder fhdsb2 = new StringBuilder();
        fhdsb2.append(formatUtil.padX(" ", 3));
        fhdsb2.append(formatUtil.padX("保存期限：自酎", 14));
        fhdsb2.append(formatUtil.padX(" ", 79));
        fhdsb2.append(formatUtil.padX("報表編號： CL-C076-1", 30));
        fileC076AContents.add(formatUtil.padX(fhdsb2.toString(), 180));

        StringBuilder fhdsb4 = new StringBuilder();
        fhdsb4.append(formatUtil.padX(" ", 3));
        fhdsb4.append(formatUtil.padX("自", 2));
        fhdsb4.append(formatUtil.padX(" ", 1));
        fhdsb4.append(formatUtil.padX(previousRocYear + "", 3));
        fhdsb4.append(formatUtil.padX(" ", 1));
        fhdsb4.append(formatUtil.padX("年", 2));
        fhdsb4.append(formatUtil.padX(" ", 1));
        fhdsb4.append(formatUtil.padX(previousMonth + "", 2));
        fhdsb4.append(formatUtil.padX(" ", 1));
        fhdsb4.append(formatUtil.padX("月", 2));
        fhdsb4.append(formatUtil.padX(" ", 1));
        fhdsb4.append(formatUtil.padX(previousDay + "", 2));
        fhdsb4.append(formatUtil.padX(" ", 1));
        fhdsb4.append(formatUtil.padX("日", 2));
        fhdsb4.append(formatUtil.padX("至", 2));
        fhdsb4.append(formatUtil.padX(" ", 1));
        fhdsb4.append(formatUtil.padX(previousRocYear1 + "", 3));
        fhdsb4.append(formatUtil.padX(" ", 1));
        fhdsb4.append(formatUtil.padX("年", 2));
        fhdsb4.append(formatUtil.padX(" ", 1));
        fhdsb4.append(formatUtil.padX(previousMonth1 + "", 2));
        fhdsb4.append(formatUtil.padX(" ", 1));
        fhdsb4.append(formatUtil.padX("月", 2));
        fhdsb4.append(formatUtil.padX(" ", 1));
        fhdsb4.append(formatUtil.padX(previousDay1 + "", 2));
        fhdsb4.append(formatUtil.padX(" ", 1));
        fhdsb4.append(formatUtil.padX("日", 2));
        fhdsb4.append(formatUtil.padX(" ", 8));
        fhdsb4.append(formatUtil.padX("解繳日期：", 10));
        fhdsb4.append(formatUtil.padX(" ", 1));
        fhdsb4.append(formatUtil.padX(nowRocYear + "", 3));
        fhdsb4.append(formatUtil.padX(" ", 1));
        fhdsb4.append(formatUtil.padX("年", 2));
        fhdsb4.append(formatUtil.padX(" ", 1));
        fhdsb4.append(formatUtil.padX(nowRocMonth + "", 2));
        fhdsb4.append(formatUtil.padX(" ", 1));
        fhdsb4.append(formatUtil.padX("月", 2));
        fhdsb4.append(formatUtil.padX(" ", 1));
        fhdsb4.append(formatUtil.padX(nowRocDay + "", 2));
        fhdsb4.append(formatUtil.padX(" ", 1));
        fhdsb4.append(formatUtil.padX("日", 2));
        fhdsb4.append(formatUtil.padX(" ", 14));
        fhdsb4.append(formatUtil.padX("單位：元", 8));
        fileC076AContents.add(formatUtil.padX(fhdsb4.toString(), 180));

        StringBuilder fhdsb5 = new StringBuilder();
        fhdsb5.append(formatUtil.padX(" ", 32));
        fileC076AContents.add(formatUtil.padX(fhdsb5.toString(), 180));

        StringBuilder fhdsb6 = new StringBuilder();
        fhdsb6.append(formatUtil.padX(" ", 3));
        fhdsb6.append(formatUtil.padX("序號", 4));
        fhdsb6.append(formatUtil.padX(" ", 5));
        fhdsb6.append(formatUtil.padX("縣市別", 6));
        fhdsb6.append(formatUtil.padX(" ", 4));
        fhdsb6.append(formatUtil.padX("分行別", 6));
        fhdsb6.append(formatUtil.padX(" ", 4));
        fhdsb6.append(formatUtil.padX("主辦分行", 8));
        fhdsb6.append(formatUtil.padX(" ", 4));
        fhdsb6.append(formatUtil.padX("代收類別", 8));
        fhdsb6.append(formatUtil.padX(" ", 7));
        fhdsb6.append(formatUtil.padX("筆數", 4));
        fhdsb6.append(formatUtil.padX(" ", 13));
        fhdsb6.append(formatUtil.padX("中央基金", 8));
        fhdsb6.append(formatUtil.padX(" ", 13));
        fhdsb6.append(formatUtil.padX("地方基金", 8));
        fhdsb6.append(formatUtil.padX(" ", 13));
        fhdsb6.append(formatUtil.padX("合　　計", 8));
        fileC076AContents.add(formatUtil.padX(fhdsb6.toString(), 180));

        StringBuilder fhdsb7 = new StringBuilder();
        fhdsb7.append(formatUtil.padX(" ", 1));
        fhdsb7.append(
                formatUtil.padX(
                        "===============================================================================================================================",
                        130));
        fileC076AContents.add(formatUtil.padX(fhdsb7.toString(), 180));
    }

    //     表尾
    private void edsb(String wcnt, BigDecimal wamt, BigDecimal wlocamt, BigDecimal wcenamt) {
        StringBuilder edsbhd = new StringBuilder();
        edsbhd.append(formatUtil.padX(" ", 1));
        edsbhd.append(formatUtil.padX(":", 1));
        fileC076AContents.add(formatUtil.padX(edsbhd.toString(), 180));

        StringBuilder edsbhd1 = new StringBuilder();
        edsbhd1.append(formatUtil.padX(" ", 1));
        edsbhd1.append(formatUtil.padX(":", 1));
        fileC076AContents.add(formatUtil.padX(edsbhd1.toString(), 180));

        StringBuilder edsb1 = new StringBuilder();
        edsb1.append(formatUtil.padX(" ", 1));
        edsb1.append(
                formatUtil.padX(
                        "===============================================================================================================================",
                        130));
        fileC076AContents.add(formatUtil.padX(edsb1.toString(), 180));

        StringBuilder edsb2 = new StringBuilder();
        edsb2.append(formatUtil.padX(" ", 3));
        edsb2.append(formatUtil.padX("合計", 4));
        edsb2.append(formatUtil.padX(" ", 51));

        // 筆數
        if (wcnt.equals("0")) {
            edsb2.append(formatUtil.padX("    " + 0, 5));
        } else {
            edsb2.append(formatUtil.padX(" ", 5 - wcnt.length()));
            edsb2.append(wcnt);
        }
        edsb2.append(formatUtil.padX(" ", 6));
        // 中央基金
        if (wamt.compareTo(BigDecimal.ZERO) == 0) {
            edsb2.append(formatUtil.padX("              " + 0, 15));
        } else {
            edsb2.append(formatUtil.padX(" ", 15 - df.format(wamt).length()));
            edsb2.append(df.format(wamt));
        }
        edsb2.append(formatUtil.padX(" ", 6));
        // 地方基金
        if (wlocamt.compareTo(BigDecimal.ZERO) == 0) {
            edsb2.append(formatUtil.padX("              " + 0, 15));
        } else {
            edsb2.append(formatUtil.padX(" ", 15 - df.format(wlocamt).length()));
            edsb2.append(df.format(wlocamt));
        }
        edsb2.append(formatUtil.padX(" ", 6));
        // 合計
        if (wcenamt.compareTo(BigDecimal.ZERO) == 0) {
            edsb2.append(formatUtil.padX("              " + 0, 15));
        } else {
            edsb2.append(formatUtil.padX(" ", 15 - df.format(wcenamt).length()));
            edsb2.append(df.format(wcenamt));
        }
        fileC076AContents.add(formatUtil.padX(edsb2.toString(), 180));

        StringBuilder edsb3 = new StringBuilder();
        edsb3.append(formatUtil.padX(" ", 1));
        fileC076AContents.add(formatUtil.padX(edsb3.toString(), 180));
    }

    private List<Integer> previousRoc(int entdy) {

        // 週期前一天往前推7天  EX周二到上周三 以當天推就是推8天

        String YMD1 = Integer.toString(entdy);
        int YYY1 = Integer.parseInt(YMD1.substring(0, 3));
        int MM1 = Integer.parseInt(YMD1.substring(3, 5));
        int DD1 = Integer.parseInt(YMD1.substring(5, 7));

        // 先將民國年轉西元年
        int gregorianYear = YYY1 + 1911; // 1911年为民國元年，将民國年加上1911年即为对应的公元年

        // 用LocalDate
        LocalDate date = LocalDate.of(gregorianYear, MM1, DD1);

        // 往前推1天
        LocalDate previousDays1 = date.minusDays(1);
        // 往前推8天
        LocalDate previousDays = date.minusDays(8);

        // 获取推前1天后的年、月、日
        String previousRoc1 =
                Integer.toString(
                        ((previousDays1.getYear() - 1911) * 10000) // 年
                                + (previousDays1.getMonthValue() * 100) // 月
                                + previousDays1.getDayOfMonth() // 日
                        ); // 将公元年转换为民國年

        // 获取推前8天后的年、月、日
        String previousRoc8 =
                Integer.toString(
                        ((previousDays.getYear() - 1911) * 10000) // 年
                                + (previousDays.getMonthValue() * 100) // 月
                                + previousDays.getDayOfMonth() // 日
                        ); // 将公元年转换为民國年

        push1Day = parse.string2Integer(previousRoc1);
        push8Day = parse.string2Integer(previousRoc8);

        List<TxBizDate> txBizDates =
                fsapSync.sy202ForAp(event.getPeripheryRequest(), previousRoc8, previousRoc1);
        // 判斷是否為假日，假日 ture 營業日為false
        //        txBizDates.get(0).isHliday();//第一筆
        //        txBizDates.get(txBizDates.size()-1).isHliday(); //最後一筆
        ApLogHelper.info(
                log, false, LogType.NORMAL.getCode(), "txBizDates{}", txBizDates.get(0).isHliday());
        if (txBizDates.get(0).isHliday()) {
            // 再往前七天
            LocalDate previousDayss = date.minusDays(15);
            // 获取推前8天后的年、月、日
            String previousRoc15 =
                    Integer.toString(
                            ((previousDayss.getYear() - 1911) * 10000) // 年
                                    + (previousDayss.getMonthValue() * 100) // 月
                                    + previousDayss.getDayOfMonth() // 日
                            ); // 将公元年转换为民國年
            push8Day = parse.string2Integer(previousRoc15);
        }

        List<Integer> results = new ArrayList<>();
        results.add(push1Day);
        results.add(push8Day);

        return results;
    }

    private void moveErrorResponse(LogicException e) {
        //                event.setPeripheryRequest();
    }
}
