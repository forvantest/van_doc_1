/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import static com.bot.txcontrol.mapper.MapperCase.formatUtil;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.Ex_kputh2;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FileKPUTH;
import com.bot.ncl.util.fileVo.FilePUTH;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.Text2VoFormatter;
import com.bot.txcontrol.util.text.format.Vo2TextFormatter;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("Ex_kputh2Lsnr")
@Scope("prototype")
public class Ex_kputh2Lsnr extends BatchListenerCase<Ex_kputh2> {

    @Autowired private Parse parse;

    @Autowired private TextFileUtil textFileUtil;

    @Autowired private Text2VoFormatter text2VoFormatter;
    @Autowired private FsapBatchUtil fsapBatchUtil;

    @Autowired private FileKPUTH kputh;

    @Autowired private FilePUTH puth;

    @Autowired private Vo2TextFormatter vo2TextFormatter;
    private static final String CONVF_DATA = "DATA";
    private static final String PATH_SEPARATOR = File.separator;

    // 002900 WORKING-STORAGE  SECTION.
    // 002950** FOR Y100 6->8
    // 003000 01 WK-YYMMDD                          PIC 9(08).
    private int wkYymmdd;
    // 003100 01 WK-PBRNO                           PIC 9(03).
    // private int wkPbrno;
    // 003200 01 WK-PUTDIR.
    // 003300    03 FILLER                      PIC X(22)
    // 003400                             VALUE "DATA/GN/DWL/CL012/003/".
    // 003500    03 WK-FDATE                    PIC 9(07).
    // 003600    03 FILLER                      PIC X(08) VALUE "/KPUTH1.".
    private String wkPutdir;
    private String wkFdate;
    // 003700 01 WK-TTPUTDIR                    PIC X(18)
    // 003800                             VALUE "DATA/CL/BH/TTPUTH.".
    private String wkTtputdir;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String UTF8 = "UTF-8";

    @Value("${fsapFile.gn.dwl.directory}")
    private String fsapFileGnDwlDirectory;

    @Override
    public void onApplicationEvent(Ex_kputh2 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Ex_kputh2Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Ex_kputh2 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "Ex_kputh2Lsnr run()");

        init(event);

        // 若FD-KPUTH不存在，跳至0000-END-RTN
        // 005400     IF ATTRIBUTE RESIDENT OF FD-KPUTH IS NOT = VALUE(TRUE)
        // 005500       GO TO 0000-END-RTN.
        // 005600*
        // 005700     DISPLAY "WK-PUTDIR=" WK-PUTDIR.
        if (textFileUtil.exists(wkPutdir)) {
            // 0000-MAIN-RTN
            mainRtn();
        }

        batchResponse(event);
    }

    private void init(Ex_kputh2 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "init()");
        // 開啟批次日期檔
        // 004300     OPEN INPUT   FD-BHDATE.
        // DISPLAY訊息，包含在系統訊息中
        // 004400     CHANGE ATTRIBUTE DISPLAYONLYTOMCS OF MYSELF TO TRUE.

        // 讀批次日期檔，設定本營業日變數值；若讀不到，顯示訊息，結束程式
        // 搬FD-BHDATE-TBSDY 9(08) 給 WK-YYMMDD 9(08)
        // 004500     READ    FD-BHDATE AT END DISPLAY "READ FD-BHDATE ERROR"
        // 004600        STOP RUN.
        // 004700     MOVE    FD-BHDATE-TBSDY TO     WK-YYMMDD.
        wkYymmdd = event.getAggregateBuffer().getTxCom().getTbsdy();

        // 設定FD-KPUTH檔名
        //  若WK-YYMMDD<500101，則加1000000放到WK-FDATE(WK-YYMMDD已是8碼，此處有誤???)；
        //  否則直接搬WK-YYMMDD給WK-FDATE
        // 004800     IF      WK-YYMMDD       <       500101
        // 004900       COMPUTE  WK-FDATE = 1000000 + WK-YYMMDD
        // 005000     ELSE
        // 005100       MOVE  WK-YYMMDD       TO     WK-FDATE.
        // 005200*
        wkFdate = parse.decimal2String(wkYymmdd, 7, 0);

        // WK-TTPUTDIR = "DATA/CL/BH/TTPUTH."
        // WK-PUTDIR = "DATA/GN/DWL/CL012/003/"+WK-FDATE+"/KPUTH1."
        // 005250     CHANGE  ATTRIBUTE FILENAME  OF FD-PUTH  TO WK-TTPUTDIR.
        // 005300     CHANGE  ATTRIBUTE FILENAME  OF FD-KPUTH TO WK-PUTDIR  .
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        Map<String, String> labelMap = arrayMap.get("labelMap").getMapAttrMap();

        // 設定工作日、檔名日期變數值
        String processDate =
                formatUtil.pad9(labelMap.get("PROCESS_DATE"), 8).substring(1); // 待中菲APPLE提供正確名稱

        wkTtputdir =
                fileDir + CONVF_DATA + PATH_SEPARATOR + processDate + PATH_SEPARATOR + "TTPUTH";
        wkPutdir = fsapFileGnDwlDirectory + wkFdate + "/KPUTH1";
    }

    // 0000-MAIN-RTN
    private void mainRtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "mainRtn()");
        // 開啟檔案
        // 006500     OPEN    INPUT   FD-KPUTH.
        // 006600     OPEN    EXTEND  FD-PUTH.
        // 006700 0000-MAIN-LOOP.
        List<String> kputhDataList = textFileUtil.readFileContent(wkPutdir, UTF8);

        // 循序讀取FD-KPUTH，直到檔尾GO TO 0000-MAIN-EXIT(應0000-MAIN-CLOSE)，結束程式
        // 006800     READ    FD-KPUTH AT END GO TO 0000-MAIN-CLOSE.
        // 006900*
        if (!Objects.isNull(kputhDataList) && !kputhDataList.isEmpty()) {
            List<String> puthDataList = new ArrayList<>();
            for (String kputhData : kputhDataList) {
                text2VoFormatter.format(kputhData, kputh);
                puth = new FilePUTH();
                // 搬值(KPUTH->PUTH)、寫檔FD-PUTH
                // 007000     MOVE    KPUTH-PUTFILE    TO   PUTH-PUTFILE  .
                puth.setPuttype(kputh.getPuttype());
                puth.setPutname(kputh.getPutname());
                // 007100     MOVE    KPUTH-CODE       TO   PUTH-CODE     .
                puth.setCode(kputh.getCode());
                // 007200     MOVE    KPUTH-RCPTID     TO   PUTH-RCPTID   .
                puth.setRcptid(kputh.getRcptid());
                // 007300     MOVE    KPUTH-DATE       TO   PUTH-DATE     .
                puth.setEntdy(kputh.getEntdy());
                // 007400     MOVE    KPUTH-TIME       TO   PUTH-TIME     .
                puth.setTime(kputh.getTime());
                // 007500     MOVE    KPUTH-CLLBR      TO   PUTH-CLLBR    .
                puth.setCllbr(kputh.getCllbr());
                // 007600     MOVE    KPUTH-LMTDATE    TO   PUTH-LMTDATE  .
                puth.setLmtdate(kputh.getLmtdate());
                // 007700     MOVE    KPUTH-AMT        TO   PUTH-AMT      .
                puth.setAmt(kputh.getAmt());
                // 007800     MOVE    KPUTH-USERDATA   TO   PUTH-USERDATA .
                puth.setUserdata(kputh.getUserdata());
                // 007900     MOVE    KPUTH-SITDATE    TO   PUTH-SITDATE  .
                puth.setSitdate(kputh.getSitdate());
                // 008000     MOVE    KPUTH-TXTYPE     TO   PUTH-TXTYPE   .
                puth.setTxtype(kputh.getTxtype());
                // 008100     MOVE    KPUTH-SERINO     TO   PUTH-SERINO   .
                puth.setSerino(kputh.getSerino());

                // FD-PUTH新增一筆資料
                // 008200     WRITE   PUTH-REC.
                // 008300
                String puthData = vo2TextFormatter.formatRS(puth, false);
                puthDataList.add(puthData);
                // LOOP讀下一筆
                // 008400     GO TO   0000-MAIN-LOOP.
            }
            textFileUtil.writeFileContent(wkTtputdir, puthDataList, UTF8);
        }
        // 008500
        // 008600 0000-MAIN-CLOSE.
        // 關閉檔案
        // 008700     CLOSE   FD-PUTH    WITH SAVE.
        // 008800     CLOSE   FD-KPUTH   .
        // 008900 0000-MAIN-EXIT.
        // 009000     EXIT.
    }

    private void batchResponse(Ex_kputh2 event) {
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(event, "0000", "", null);
    }
}
