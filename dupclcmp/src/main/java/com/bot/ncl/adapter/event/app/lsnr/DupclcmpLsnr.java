/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.Dupclcmp;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FileStockDtl;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.report.ReportUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.text.format.Text2VoFormatter;
import java.io.File;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("DupclcmpLsnr")
@Scope("prototype")
public class DupclcmpLsnr extends BatchListenerCase<Dupclcmp> {

    @Autowired private Parse parse;

    @Autowired private FormatUtil formatUtil;

    @Autowired private Text2VoFormatter text2VoFormatter;

    @Autowired private FileStockDtl stkDtl;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    @Autowired private ReportUtil reportUtil;

    @Autowired private TextFileUtil textFileUtil;
    @Autowired private FsapBatchUtil fsapBatchUtil;

    private Dupclcmp event;
    private static final String UTF_8 = "UTF-8";
    private static final String BIG5 = "Big5";
    private static final String PATH_SEPARATOR = File.separator;
    private static final String CONVF_RPT = "RPT";

    private String wkPutBrno;
    private String wkPutFdate;
    private String wkPutFile;

    private String rptFilePath;

    private List<String> dataList;

    private List<String> rptDataList;

    private HashSet<String> rcptidSet;

    private String wkCode;
    private String[] wkParamL;

    private String wkErrCtl;
    private Map<String, String> labelMap;
    private Map<String, String> textMap;
    private String wkPutDir;

    @Override
    public void onApplicationEvent(Dupclcmp event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "DupclcmpLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Dupclcmp event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "DupclcmpLsnr run()");
        if (!init(event)) {
            batchResponse();
            return;
        }
        mainRoutine();
        // 009500     DISPLAY "SYM/CL/BH/DUPCLCMP OK".
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "SYM/CL/BH/DUPCLCMP OK");
        //// 若無錯誤資料，寫一筆REPORTFL報表明細(WK-NOERR-LINE)
        // 009600     IF      WK-ERRCTL         =      "N"
        if ("N".equals(wkErrCtl)) {
            // 009700       MOVE  SPACES            TO     REPORT-LINE
            // 009800       WRITE REPORT-LINE       FROM   WK-NOERR-LINE.
            rptDataList.add(formatUtil.padX(" 無錯誤資料 ", 12));
            textFileUtil.writeFileContent(rptFilePath, rptDataList, BIG5);
            rptDataList = new ArrayList<>();
        }
        //// 關檔，結束程式
        //
        // 009900     CLOSE   FD-UPLCLCMP       WITH   SAVE.
        // 010000     CLOSE   REPORTFL          WITH   SAVE.
        batchResponse();
    }

    private Boolean init(Dupclcmp event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "init()");
        this.event = event;
        // REPORTFL ref:
        // 002000 FD  REPORTFL
        // 002100      VALUE  OF  TITLE  IS  "BD/CL/BH/041."
        rptDataList = new ArrayList<>();

        // 008700 0000-START-RTN.
        // 008800     MOVE          F-BRNO          TO     WK-PUT-BRNO.
        // 008900     MOVE          F-FDATE         TO     WK-PUT-FDATE.
        // 009000     MOVE          F-FILENAME      TO     WK-PUT-FILE.
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        labelMap = arrayMap.get("labelMap").getMapAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();

        Map<String, String> paramMap;
        paramMap = getG2006Param(textMap.get("PARAM"));
        if (paramMap == null) {
            return false;
        }

        wkPutFdate = formatUtil.pad9(textMap.get("DATE"), 8).substring(1); // TODO: 待確認BATCH參數名稱
        wkPutBrno = paramMap.get("PBRNO"); // TODO: 待確認BATCH參數名稱
        wkPutFile = textMap.get("FILENAME"); // TODO: 待確認BATCH參數名稱
        // 設定作業日、檔名日期變數值
        String processDate = labelMap.get("PROCESS_DATE"); // 待中菲APPLE提供正確名稱

        rptFilePath =
                fileDir + CONVF_RPT + PATH_SEPARATOR + processDate + PATH_SEPARATOR + "CL-BH-040";
        textFileUtil.deleteFile(rptFilePath);

        // 009100     CHANGE ATTRIBUTE FILENAME  OF FD-UPLCLCMP TO WK-PUTDIR.
        wkPutDir = fileDir + wkPutBrno + PATH_SEPARATOR + wkPutFdate + PATH_SEPARATOR + wkPutFile;
        rcptidSet = new HashSet<>();
        wkErrCtl = "N";
        wkCode = wkPutFile.substring(6);
        return true;
    }

    private void mainRoutine() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "mainRoutine()");
        // 010300 0000-MAIN-RTN.
        // 010400     MOVE    "N"               TO     WK-ERRCTL.
        // 010500     MOVE    WK-PUT-FILE-CODE  TO     WK-CODE-RPT.
        // 010600     OPEN    INPUT    FD-UPLCLCMP.
        // 010700     OPEN    OUTPUT   REPORTFL.
        // 010800     PERFORM RPT-TITLE-RTN    THRU  RPT-TITLE-EXIT.
        rptTitle();
        // 010900 0000-READ-NEXT.
        // 011000     READ    FD-UPLCLCMP AT  END  GO TO  0000-MAIN-EXIT.
        if (!textFileUtil.exists(wkPutDir)) {
            return;
        }
        dataList = textFileUtil.readFileContent(wkPutDir, UTF_8);
        for (String data : dataList) {
            String f1 = data.substring(0, 1);
            // 011100     IF      STK-F2              =    2
            // 011200       PERFORM 2000-CHK-RIDDUP-RTN THRU 2000-CHK-RIDDUP-EXIT.
            // 011300     IF      WK-RTNCD            =    1
            // 011400       PERFORM 9000-WRITE-ERR-RTN THRU 9000-WRITE-ERR-EXIT.
            // 011500     GO TO 0000-READ-NEXT.
            if (f1.equals("2")) {
                chkRiddup(data);
            }
        }
        // 011600 0000-MAIN-EXIT.
        // 011700     EXIT.
    }

    private void rptTitle() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "rptTitle()");
        // 013700 RPT-TITLE-RTN.
        // 013800     MOVE       SPACES            TO      REPORT-LINE.
        // 013900     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE1.

        // 003500 01 WK-TITLE-LINE1.
        // 003600    02 FILLER                       PIC X(33) VALUE SPACE.
        // 003700    02 TITLE-LABEL                  PIC X(32)
        // 003800                    VALUE " 代收比對維護錯誤檢核表二 ".
        // 003900    02 FILLER                       PIC X(21) VALUE SPACE.
        // 004000    02 FILLER                       PIC X(12)
        // 004100                    VALUE "FORM : C041 ".                         96/11/29
        String wkTitleLine1 = " ".repeat(33);
        wkTitleLine1 += formatUtil.padX(" 代收比對維護錯誤檢核表二 ", 32);
        wkTitleLine1 += " ".repeat(21);
        wkTitleLine1 += formatUtil.padX("FORM : C041 ", 12);
        rptDataList.add(formatUtil.padX(wkTitleLine1, 150));

        // 014000     MOVE       SPACES            TO      REPORT-LINE.
        // 014100     WRITE      REPORT-LINE       AFTER   1.
        rptDataList.add(formatUtil.padX(" ", 150));

        // 014200     MOVE       F-BRNO            TO      WK-PBRNO.
        // 014300     MOVE       F-FDATE           TO      WK-PDATE.
        // 014400*    MOVE       1                 TO      WK-TOTPAGE.
        // 014500     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE2.

        // 004200 01 WK-TITLE-LINE2.
        // 004300    02 FILLER                       PIC X(10)
        // 004400                              VALUE " 分行別： ".
        // 004500    02 WK-PBRNO                     PIC 9(03).
        // 004600    02 FILLER                       PIC X(05) VALUE SPACE.
        // 004700    02 FILLER                       PIC X(13)
        // 004800                              VALUE "  印表日期： ".
        // 004900    02 WK-PDATE                     PIC 99/99/99.
        // 005000    02 FILLER                       PIC X(38) VALUE SPACE.
        String wkTitleLine2 = formatUtil.padX(" 分行別： ", 10);
        wkTitleLine2 += formatUtil.padX(wkPutBrno, 3);
        wkTitleLine2 += formatUtil.padX(" ", 5);
        wkTitleLine2 += formatUtil.padX("  印表日期： ", 13);
        wkTitleLine2 += reportUtil.customFormat(wkPutFdate, "99/99/99");
        wkTitleLine2 += formatUtil.padX(" ", 38);
        rptDataList.add(formatUtil.padX(wkTitleLine2, 150));

        // 014600     MOVE       SPACES            TO      REPORT-LINE.
        // 014700     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE3.

        // 005400 01 WK-TITLE-LINE3.
        // 005500    02 FILLER                       PIC X(12)
        // 005600                              VALUE " 代收類別： ".
        // 005700    02 WK-CODE-RPT                  PIC X(06).
        String wkTitleLine3 = formatUtil.padX(" 代收類別： ", 12);
        wkTitleLine3 += formatUtil.padX(wkCode, 6);
        rptDataList.add(formatUtil.padX(wkTitleLine3, 150));

        // 014800     MOVE       SPACES            TO      REPORT-LINE.
        // 014900     WRITE      REPORT-LINE       FROM    WK-TITLE-LINE4.

        // 005800 01 WK-TITLE-LINE4.
        // 005900    02 FILLER                       PIC X(100) VALUE ALL "-".
        rptDataList.add(formatUtil.padX("-".repeat(100), 150));

        textFileUtil.writeFileContent(rptFilePath, rptDataList, BIG5);
        rptDataList = new ArrayList<>();
        // 015000 RPT-TITLE-EXIT.
        // 015100     EXIT.
    }

    private void chkRiddup(String data) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "chkRiddup()");
        // 011900 2000-CHK-RIDDUP-RTN.
        text2VoFormatter.format(data, stkDtl);
        // 012000     MOVE    STK-RCPTID         TO    WK-RCPTID.
        // 012100     MOVE    0                  TO    WK-RTNCD.
        // 012200     IF      WK-RCPTID          =     WK-RCPTID-LAST
        // 012300       MOVE  "RCPTID DUP"       TO    WK-TEXT
        // 012400       MOVE  "Y"                TO    WK-ERRCTL
        // 012500       MOVE  1                  TO    WK-RTNCD.
        String wkRcptid = stkDtl.getRcptid();
        if (rcptidSet.contains(wkRcptid)) {
            wkErrCtl = "Y";
            writeErr(data, "RCPTID DUP");
        } else {
            // 012600     MOVE    WK-RCPTID          TO    WK-RCPTID-LAST.
            rcptidSet.add(wkRcptid);
        }
        // 012700 2000-CHK-RIDDUP-EXIT.
        // 012800     EXIT.
    }

    private void writeErr(String data, String err) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "writeErr()");
        StringBuilder wkDetailLine = new StringBuilder();
        // 006300 01 WK-DETAIL-LINE.
        // 006400  03 FILLER                            PIC X(10) VALUE
        // 006500                                                 "ERR ITEM: ".
        // 006600  03 WK-TEXT                           PIC X(16).
        // 006700  03 WK-REC-P                          PIC X(60).
        wkDetailLine.append(formatUtil.padX("ERR ITEM: ", 10));
        wkDetailLine.append(formatUtil.padX(err, 16));
        wkDetailLine.append(formatUtil.padX(data, 60));
        rptDataList.add(formatUtil.padX(wkDetailLine.toString(), 150));
        textFileUtil.writeFileContent(rptFilePath, rptDataList, BIG5);
        rptDataList = new ArrayList<>();
    }

    private Map<String, String> getG2006Param(String lParam) {
        String[] paramL;
        if (lParam.isEmpty()) {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "lParam is null");
            return null;
        }
        paramL = lParam.split(";");
        if (paramL == null) {
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "paramL is null");
            return null;
        }
        // G2006:
        //  PBRNO(;),
        //  HCODE(;),
        //  LEN(;),
        //  PARAM1(;),
        //  PARAM2(;)
        Map<String, String> map = new HashMap<>();
        if (paramL.length > 0) map.put("PBRNO", paramL[0]); // 對應 PBRNO
        if (paramL.length > 1) map.put("HCODE", paramL[1]); // 對應 HCODE
        if (paramL.length > 2) map.put("LEN", paramL[2]); // 對應 LEN
        if (paramL.length > 3) map.put("PARAM1", paramL[3]); // 對應 PARAM1
        if (paramL.length > 4) map.put("PARAM2", paramL[4]); // 對應 PARAM2
        if (map.size() == 0) {
            return null;
        }

        for (String key : map.keySet()) {
            ApLogHelper.info(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "map KEY = {} ,VALUE = {}",
                    key,
                    map.get(key));
        }
        return map;
    }

    private void batchResponse() {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", null);
    }
}
