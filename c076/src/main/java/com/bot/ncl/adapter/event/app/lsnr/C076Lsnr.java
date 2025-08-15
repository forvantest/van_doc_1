/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.C076;
import com.bot.ncl.dto.entities.ClbafbyDateRangeBus;
import com.bot.ncl.jpa.svc.ClbafService;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.sort.ExternalSortUtil;
import com.bot.ncl.util.sort.eum.SortBy;
import com.bot.ncl.util.sort.vo.KeyRange;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.adapter.out.grpc.FsapSync;
import com.bot.txcontrol.buffer.TxBizDate;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
@Component("C076Lsnr")
@Scope("prototype")
public class C076Lsnr extends BatchListenerCase<C076> {
    @Autowired private ClbafService clbafService;
    @Autowired private TextFileUtil textFile;
    @Autowired private Parse parse;
    @Autowired private FormatUtil formatUtil;
    @Autowired private ExternalSortUtil externalSortUtil;
    @Autowired private FsapSync fsapSync;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private Map<String, String> labelMap;

    private String tmpFilePath; // 暫存檔路徑
    private String outputFilePath;

    private C076 event;
    private String batchDate; // 批次日期(民國年yyyymmdd)
    private List<String> fileC076Contents; // 檔案內容
    private List<String> fileTmpContents; // 暫存內容
    private int entdy = 0;
    private static final String FILE_NAME = "BD/C076";
    private static final String FILE_TMP_NAME = "TMPC076";
    private static final String CHARSET = "UTF-8";
    private static final String OUTCHARSET = "Big5";
    private final int pageCnts = 100000;
    private int pageIndex = 0;
    private Boolean noData = false;
    int ipbrno = 0;
    String icode = "";
    int icnt = 0;
    BigDecimal iamt = BigDecimal.ZERO;
    int push1Day = 0;
    int push8Day = 0;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(C076 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C076Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(C076 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C076Lsnr run()");
        init(event);

        // 先找資料寫入暫存
        queryClbaf();
        // 排序seq
        sortfile();
        // 產出資料並加總
        toWriteC076File();
    }

    private void init(C076 event) {
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        labelMap = arrayMap.get("labelMap").getMapAttrMap();

        batchDate = labelMap.get("BBSDY"); // TODO: 待確認BATCH參數名稱
        entdy = parse.string2Integer(batchDate);

        tmpFilePath = fileDir + FILE_TMP_NAME;
        outputFilePath = fileDir + FILE_NAME;
        fileC076Contents = new ArrayList<>();
        fileTmpContents = new ArrayList<>();
        textFile.deleteFile(tmpFilePath);
        textFile.deleteFile(outputFilePath);
    }

    private void queryClbaf() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "clbafquery()");
        entdy = parse.string2Integer(batchDate);
        //        010500    03 WK-CODE              PIC X(06).
        //        010600       88 WK-CODE-SELECT    VALUE
        //        010700          "115463", "115473", "115483", "115493", "115503",
        //        010800          "115513", "115523", "115533", "115543", "115553",
        //        010900          "115563", "115573", "115583", "115593", "115603",
        //        011000          "115613", "115623", "115633", "115643", "115653",
        //        011100          "115663", "115673".

        List<String> lsCode = new ArrayList<>();
        lsCode.add("115463");
        lsCode.add("115473");
        lsCode.add("115483");
        lsCode.add("115493");
        lsCode.add("115503");

        lsCode.add("115513");
        lsCode.add("115523");
        lsCode.add("115533");
        lsCode.add("115543");
        lsCode.add("115553");

        lsCode.add("115563");
        lsCode.add("115573");
        lsCode.add("115583");
        lsCode.add("115593");
        lsCode.add("115603");

        lsCode.add("115613");
        lsCode.add("115623");
        lsCode.add("115633");
        lsCode.add("115643");
        lsCode.add("115653");

        lsCode.add("115663");
        lsCode.add("115673");

        previousRoc(entdy);

        //        "BATCH_DATE": "01120808"
        List<ClbafbyDateRangeBus> lClbafList =
                clbafService.findbyDateRange(lsCode, push8Day, push1Day, pageIndex, pageCnts);

        try {
            setTmpData(lClbafList);
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }

    private void setTmpData(List<ClbafbyDateRangeBus> lClbafList) throws IOException {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C076Lsnr setTmpData");
        if (lClbafList != null) {
            for (ClbafbyDateRangeBus tClbafList : lClbafList) {
                //                038800     MOVE      LOW-VALUE                  TO FD-C076-REC.
                //                038900     MOVE      WK-FIX-SEQ    (WK-IDX)     TO FD-C076-SEQ.
                //                039000     MOVE      WK-FIX-COUNTRY(WK-IDX)     TO
                // FD-C076-COUNTRY.
                //                039100     MOVE      WK-FIX-BRNO   (WK-IDX)     TO FD-C076-BRNO.
                //                039200     MOVE      WK-FIX-BRNONM (WK-IDX)     TO FD-C076-BRNONM.
                //                039300     MOVE      WK-FIX-CODE   (WK-IDX)     TO FD-C076-CODE.
                //                039400     MOVE      WK-SUM-CNT                 TO FD-C076-CNT.
                //                039500     MOVE      WK-SUM-AMT                 TO FD-C076-AMT.
                String ipbrno = String.format("%03d", tClbafList.getPbrno());
                String icode = String.format("%-" + 6 + "s", tClbafList.getCode());
                String icnt = String.format("%5d", tClbafList.getCnt());
                String iamt = String.format("%17.2f", tClbafList.getAmt());

                String seq = "";
                String country = "";
                String pbrnom = "";
                if (icode.equals("115463") && ipbrno.equals("005")) {
                    seq = "01";
                    country = "臺北市";
                    pbrnom = "公庫部　";
                } else if (icode.equals("115473") && ipbrno.equals("027")) {
                    seq = "02";
                    country = "新北市";
                    pbrnom = "板橋方行";
                } else if (icode.equals("115483") && ipbrno.equals("026")) {
                    seq = "03";
                    country = "桃園市";
                    pbrnom = "桃園分行";
                } else if (icode.equals("115493") && ipbrno.equals("010")) {
                    seq = "04";
                    country = "臺中市";
                    pbrnom = "臺中分行";
                } else if (icode.equals("115503") && ipbrno.equals("009")) {
                    seq = "05";
                    country = "臺南市";
                    pbrnom = "臺南分行";
                } else if (icode.equals("115513") && ipbrno.equals("011")) {
                    seq = "06";
                    country = "高雄市";
                    pbrnom = "高雄分行";
                } else if (icode.equals("115523") && ipbrno.equals("022")) {
                    seq = "07";
                    country = "宜蘭縣";
                    pbrnom = "宜蘭分行";
                } else if (icode.equals("115533") && ipbrno.equals("068")) {
                    seq = "08";
                    country = "新竹縣";
                    pbrnom = "竹北分行";
                } else if (icode.equals("115543") && ipbrno.equals("029")) {
                    seq = "09";
                    country = "苗栗縣";
                    pbrnom = "苗栗分行";
                } else if (icode.equals("115553") && ipbrno.equals("016")) {
                    seq = "10";
                    country = "彰化縣";
                    pbrnom = "彰化分行";
                } else if (icode.equals("115563") && ipbrno.equals("032")) {
                    seq = "11";
                    country = "南投縣";
                    pbrnom = "南投分行";
                } else if (icode.equals("115573") && ipbrno.equals("067")) {
                    seq = "12";
                    country = "嘉義縣";
                    pbrnom = "太保分行";
                } else if (icode.equals("115583") && ipbrno.equals("031")) {
                    seq = "13";
                    country = "雲林縣";
                    pbrnom = "斗六分行";
                } else if (icode.equals("115593") && ipbrno.equals("017")) {
                    seq = "14";
                    country = "屏東縣";
                    pbrnom = "屏東分行";
                } else if (icode.equals("115603") && ipbrno.equals("023")) {
                    seq = "15";
                    country = "臺東縣";
                    pbrnom = "臺東分行";
                } else if (icode.equals("115613") && ipbrno.equals("018")) {
                    seq = "16";
                    country = "花蓮縣";
                    pbrnom = "花蓮分行";
                } else if (icode.equals("115623") && ipbrno.equals("024")) {
                    seq = "17";
                    country = "澎湖縣";
                    pbrnom = "澎湖分行";
                } else if (icode.equals("115633") && ipbrno.equals("012")) {
                    seq = "18";
                    country = "基隆市";
                    pbrnom = "基隆分行";
                } else if (icode.equals("115643") && ipbrno.equals("015")) {
                    seq = "19";
                    country = "新竹市";
                    pbrnom = "新竹分行";
                } else if (icode.equals("115653") && ipbrno.equals("014")) {
                    seq = "20";
                    country = "嘉義市";
                    pbrnom = "嘉義分行";
                } else if (icode.equals("115663") && ipbrno.equals("038")) {
                    seq = "21";
                    country = "金門縣";
                    pbrnom = "金門分行";
                } else if (icode.equals("115673") && ipbrno.equals("039")) {
                    seq = "22";
                    country = "連江縣";
                    pbrnom = "馬祖分行";
                } else {
                    seq = "23";
                    ipbrno = "999";
                    country = "其他";
                    pbrnom = "    ";
                }

                String s = seq + icode + country + ipbrno + pbrnom + icnt + iamt;
                ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "s{}", s);

                fileTmpContents.add(s);
            }
        } else {
            String s = " ";
            fileTmpContents.add(s);
            noData = true;
        }
        try {
            textFile.writeFileContent(tmpFilePath, fileTmpContents, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "C076Lsnr File OK");
    }

    private void sortfile() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "toWriteC076File sortfile");
        if (noData) {
            return;
        }
        File tmpFile = new File(tmpFilePath);
        List<KeyRange> keyRanges = new ArrayList<>();
        keyRanges.add(new KeyRange(1, 2, SortBy.ASC));
        keyRanges.add(new KeyRange(3, 9, SortBy.ASC));

        externalSortUtil.sortingFile(tmpFile, tmpFile, keyRanges);
    }

    private void toWriteC076File() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "toWriteC076File");
        List<String> lines = textFile.readFileContent(tmpFilePath, CHARSET);

        // 留存上一筆及加總歸零
        String widseq = "";
        String widpbrno = "";
        String widcode = "";
        int widcnt = 0;
        BigDecimal widamt = BigDecimal.ZERO;
        BigDecimal locamt = BigDecimal.ZERO;
        BigDecimal icenamt = BigDecimal.ZERO;
        String wicountry = "";
        String wipbrnom = "";
        // 控制第一筆是否要出
        int z = 0;
        // 最後一筆印出
        int i = lines.size();
        // 沒有資料還是會產一個空白檔
        if (noData) {
            String s = " ";
            fileC076Contents.add(s);
        } else {
            for (String detail : lines) {
                i--;
                ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "lines{}", lines);

                // 擷取資料
                String idseq = detail.substring(0, 2);
                String idpbrno = detail.substring(2, 8);
                String icountry = detail.substring(8, 11);
                String idcode = detail.substring(11, 14);
                String ipbrnom = String.format("%-4s", detail.substring(14, 18));
                int idcnt = parse.string2Integer(detail.substring(18, 24));
                BigDecimal idamt = parse.string2BigDecimal(detail.substring(24, 40));

                // 搬WK-FIX對應INDEX內相關資料 & 累積筆數、金額 至 FD-C076-REC，並寫入FD-C076
                //  FD-C076-CNT 筆數
                //  FD-C076-CENAMT 中央基金
                //  FD-C076-LOCAMT 地方基金
                //  FD-C076-AMT 合計
                //                038800     MOVE      LOW-VALUE                  TO FD-C076-REC.
                //                038900     MOVE      WK-FIX-SEQ    (WK-IDX)     TO FD-C076-SEQ.
                //                039000     MOVE      WK-FIX-COUNTRY(WK-IDX)     TO
                // FD-C076-COUNTRY.
                //                039100     MOVE      WK-FIX-BRNO   (WK-IDX)     TO FD-C076-BRNO.
                //                039200     MOVE      WK-FIX-BRNONM (WK-IDX)     TO FD-C076-BRNONM.
                //                039300     MOVE      WK-FIX-CODE   (WK-IDX)     TO FD-C076-CODE.
                //                039400     MOVE      WK-SUM-CNT                 TO FD-C076-CNT.
                //                039500     MOVE      WK-SUM-AMT                 TO FD-C076-AMT.
                //                039600     COMPUTE   FD-C076-LOCAMT    ROUNDED
                //                039700           =   WK-SUM-AMT * WK-FIX-LOCRATE.
                //                039800     COMPUTE   FD-C076-CENAMT
                //                039900           =   WK-SUM-AMT - FD-C076-LOCAMT.

                if (!idseq.equals(widseq) && z > 0) {
                    StringBuilder sb = new StringBuilder();
                    // seq
                    sb.append(widseq);
                    // 縣市別
                    sb.append(wicountry);
                    // 代收類別
                    sb.append(widpbrno);
                    // 主辦行名稱
                    sb.append(wipbrnom);
                    // 主辦行
                    sb.append(widcode);
                    // cnt
                    if (widcnt == 0) {
                        sb.append(formatUtil.padX("    " + 0, 5));
                    } else {
                        sb.append(formatUtil.padX(widcnt + "", 5));
                    }
                    // amt
                    if (widamt.compareTo(BigDecimal.ZERO) == 0) {
                        sb.append(formatUtil.padX("           " + 0, 12));
                    } else {
                        sb.append(formatUtil.padX(widamt + "", 12));
                    }

                    // FD-C076-LOCAMT
                    if (widamt.compareTo(BigDecimal.ZERO) == 0) {
                        sb.append(formatUtil.padX("           " + 0, 12));
                    } else {
                        BigDecimal operand1 = new BigDecimal("0.6");
                        // BigDecimal 相乘
                        locamt = widamt.multiply(operand1).setScale(0, RoundingMode.DOWN);
                        sb.append(formatUtil.padX(locamt + "", 12));
                    }

                    // FD-C076-CENAMT
                    // BigDecimal 相減
                    icenamt = widamt.subtract(locamt);

                    if (icenamt.compareTo(BigDecimal.ZERO) == 0) {
                        sb.append(formatUtil.padX("           " + 0, 12));
                    } else {
                        sb.append(formatUtil.padX(icenamt + "", 12));
                    }

                    fileC076Contents.add(formatUtil.padX(sb.toString(), 180));
                }
                // 最後一筆
                if (i == 0) {

                    widcnt += idcnt;
                    widamt = widamt.add(idamt);

                    StringBuilder sb = new StringBuilder();
                    // seq
                    sb.append(idseq);
                    // 縣市別
                    sb.append(icountry);
                    // 代收類別
                    sb.append(idpbrno);
                    // 主辦行名稱
                    sb.append(ipbrnom);
                    // 主辦行
                    sb.append(idcode);
                    // cnt
                    if (widcnt == 0) {
                        sb.append(formatUtil.padX("    " + 0, 5));
                    } else {
                        sb.append(formatUtil.padX(widcnt + "", 5));
                    }
                    // amt
                    if (widamt.compareTo(BigDecimal.ZERO) == 0) {
                        sb.append(formatUtil.padX("           " + 0, 12));
                    } else {
                        sb.append(formatUtil.padX(widamt + "", 12));
                    }

                    // FD-C076-LOCAMT
                    if (widamt.compareTo(BigDecimal.ZERO) == 0) {
                        sb.append(formatUtil.padX("           " + 0, 12));
                    } else {
                        BigDecimal operand1 = new BigDecimal("0.6");
                        // BigDecimal 相乘
                        locamt = widamt.multiply(operand1).setScale(0, RoundingMode.DOWN);
                        sb.append(formatUtil.padX(locamt + "", 12));
                    }

                    // FD-C076-CENAMT
                    // BigDecimal 相減
                    icenamt = widamt.subtract(locamt);

                    if (icenamt.compareTo(BigDecimal.ZERO) == 0) {
                        sb.append(formatUtil.padX("           " + 0, 12));
                    } else {
                        sb.append(formatUtil.padX(icenamt + "", 12));
                    }

                    fileC076Contents.add(formatUtil.padX(sb.toString(), 180));
                }

                // 換主辦行需重新歸零
                if (!idseq.equals(widseq)) {
                    widcnt = 0;
                    widamt = BigDecimal.ZERO;
                    locamt = BigDecimal.ZERO;
                }

                wicountry = icountry;
                wipbrnom = ipbrnom;
                widseq = idseq;
                widpbrno = idpbrno;
                widcode = idcode;
                widcnt += idcnt;
                widamt = widamt.add(idamt);
                z++;
            }
        }
        try {
            textFile.writeFileContent(outputFilePath, fileC076Contents, OUTCHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
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
