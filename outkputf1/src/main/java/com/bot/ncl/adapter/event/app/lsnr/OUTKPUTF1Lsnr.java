/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.OUTKPUTF1;
import com.bot.ncl.dto.entities.CltmrBus;
import com.bot.ncl.jpa.svc.CltmrService;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.ncl.util.sort.ExternalSortUtil;
import com.bot.ncl.util.sort.eum.SortBy;
import com.bot.ncl.util.sort.vo.KeyRange;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import java.io.File;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("OUTKPUTF1Lsnr")
@Scope("prototype")
public class OUTKPUTF1Lsnr extends BatchListenerCase<OUTKPUTF1> {

    @Autowired private TextFileUtil textFile;
    @Autowired private FormatUtil formatUtil;
    @Autowired private CltmrService cltmrService;
    @Autowired private Parse parse;
    @Autowired private ExternalSortUtil externalSortUtil;
    private OUTKPUTF1 event;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    @Value("${fsapFile.gn.dwl.directory}")
    private String fsapfileDir;

    private Map<String, String> textMap;
    private String[] wkParamL;
    private static final String CHARSET = "UTF-8";
    private static final String CL012_FILE_PATH = "CL012"; // 目錄
    private static final String _003_FILE_PATH = "003"; // 讀檔目錄
    private static final String FILE_INPUT_NAME = "KPUTH"; // 讀檔檔名
    private static final String FILE_INPUT_NAME1 = "KPUTH1"; // 產檔檔名1
    private static final String FILE_INPUT_NAME2 = "KPUTH2"; // 產檔檔名2
    private String PATH_SEPARATOR = File.separator;
    private String wkKputhDir; // 讀檔路徑
    private String wkKputh1Dir; // 產檔路徑
    private String wkKputh2Dir; // 產檔路徑
    private StringBuilder sb = new StringBuilder();
    private List<String> fileKPUTF1Contents; //  檔案內容
    private List<String> fileKPUTF2Contents; //  檔案內容

    private String wkTaskDate;
    private String wkKdate;
    private String wkKdate1;
    private String wkKdate2;
    private String wkCode;

    // ----KPUTH----
    private String kputhRec;
    private String kputh1Rec;
    private String kputh2Rec;
    private String kputhPutfile;
    private String kputhCode;

    //    @Async("batchThreadPoolTaskExecutor") // 如需平行處理請將此行註解拿掉
    @Override
    public void onApplicationEvent(OUTKPUTF1 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTKPUTF1Lsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(OUTKPUTF1 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTKPUTF1Lsnr run");

        if (!init(event)) {
            return;
        }
        // 若FD-KPUTH檔案存在，執行0000-MAIN-RTN，讀FD-KPUTH、抄檔
        if (textFile.exists(wkKputhDir)) {
            main();
        }
    }

    private Boolean init(OUTKPUTF1 event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTKPUTF1Lsnr init");
        this.event = event;

        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        textMap = arrayMap.get("textMap").getMapAttrMap();

        Map<String, String> paramMap;
        paramMap = getG2007Param(textMap.get("PARAM"));
        if (paramMap == null) {
            return false;
        }
        wkTaskDate = textMap.get("DATE");

        // 005600     MOVE    WK-TASK-DATE        TO     WK-KDATE ,
        // 005700                                        WK-KDATE1,
        // 005800                                        WK-KDATE2.
        wkKdate = wkTaskDate;
        wkKdate1 = wkTaskDate;
        wkKdate2 = wkTaskDate;

        // 設定檔名
        //  WK-KPUTHDIR ="DATA/GN/DWL/CL012/003/"+WK-KDATE  X(07)+"/KPUTH."
        //  WK-KPUTH1DIR="DATA/GN/DWL/CL012/003/"+WK-KDATE1 9(07)+"/KPUTH1."
        //  WK-KPUTH2DIR="DATA/GN/DWL/CL012/003/"+WK-KDATE2 9(07)+"/KPUTH2."
        // 005900     CHANGE  ATTRIBUTE FILENAME  OF FD-KPUTH  TO WK-KPUTHDIR.
        // 006000     CHANGE  ATTRIBUTE FILENAME  OF FD-KPUTH1 TO WK-KPUTH1DIR.
        // 006100     CHANGE  ATTRIBUTE FILENAME  OF FD-KPUTH2 TO WK-KPUTH2DIR.
        wkKputhDir =
                fsapfileDir
                        + CL012_FILE_PATH
                        + PATH_SEPARATOR
                        + _003_FILE_PATH
                        + PATH_SEPARATOR
                        + wkKdate
                        + PATH_SEPARATOR
                        + FILE_INPUT_NAME;
        wkKputh1Dir =
                fsapfileDir
                        + CL012_FILE_PATH
                        + PATH_SEPARATOR
                        + _003_FILE_PATH
                        + PATH_SEPARATOR
                        + wkKdate1
                        + PATH_SEPARATOR
                        + FILE_INPUT_NAME1;
        wkKputh2Dir =
                fsapfileDir
                        + CL012_FILE_PATH
                        + PATH_SEPARATOR
                        + _003_FILE_PATH
                        + PATH_SEPARATOR
                        + wkKdate2
                        + PATH_SEPARATOR
                        + FILE_INPUT_NAME2;

        // 檔案內容
        fileKPUTF1Contents = new ArrayList<>();
        fileKPUTF2Contents = new ArrayList<>();
        return true;
    }

    private void main() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTKPUTF1Lsnr main");
        // 006800 0000-MAIN-RTN.

        // 開啟檔案
        // 006900     OPEN     INPUT               FD-KPUTH.
        // 007000     OPEN     OUTPUT              FD-KPUTH1.
        // 007100     OPEN     OUTPUT              FD-KPUTH2.
        // 007120     OPEN     UPDATE              BOTSRDB.
        // 007140     MOVE     SPACES      TO      WK-CODE.
        wkCode = "";
        // 007200 0000-LOOP.

        // SORT OBJ/CL/BH/OUTING/SORT/CL012N
        File tmpFile = new File(wkKputhDir);
        List<KeyRange> keyRanges = new ArrayList<>();
        keyRanges.add(new KeyRange(1, 10, SortBy.ASC));
        keyRanges.add(new KeyRange(11, 6, SortBy.ASC));
        externalSortUtil.sortingFile(tmpFile, tmpFile, keyRanges, CHARSET);

        // 循序讀取FD-KPUTH，直到檔尾，跳到0000-FLAST
        // 007300     READ   FD-KPUTH   AT  END  GO TO  0000-FLAST.
        List<String> lines = textFile.readFileContent(wkKputhDir, CHARSET);
        for (String detail : lines) {
            // 執行FILE-DTL-RTN，抄檔(挑當日上傳後需立即傳送資料，抄檔至FD-KPUTH1；其他(當日夜間併檔)，抄檔至FD-KPUTH2，並更新DB-CLMR-PRTTYP為"1")
            kputhRec = detail;
            kputhPutfile = detail.substring(0, 10);
            kputhCode = detail.substring(10, 16);
            // 007400     PERFORM FILE-DTL-RTN   THRU  FILE-DTL-EXIT .
            fileDtl();
            // LOOP讀下一筆FD-KPUTH
            //
            // 007500     GO TO 0000-LOOP.

        }

        // 007600 0000-FLAST.

        // 關閉檔案
        // 007700     CLOSE    FD-KPUTH                     .
        // 007800     CLOSE    FD-KPUTH1           WITH SAVE.
        // 007900     CLOSE    FD-KPUTH2           WITH SAVE.
        // 007950     CLOSE    BOTSRDB.
        try {
            textFile.writeFileContent(wkKputh1Dir, fileKPUTF1Contents, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        try {
            textFile.writeFileContent(wkKputh2Dir, fileKPUTF2Contents, CHARSET);
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
        // 008000 0000-MAIN-EXIT.

    }

    private void fileDtl() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTKPUTF1Lsnr fileDtl");
        // 008300 FILE-DTL-RTN.

        // 若KPUTH-PUTFILE符合以下條件(當日上傳後需立即傳送資料)，抄檔至FD-KPUTH1
        // 其他，
        //  A.代收類別不同時，執行UPD-CLMRFLG-RTN，更新DB-CLMR-PRTTYP為"1"
        //  B.抄檔至FD-KPUTH2

        // 008400     IF  ( KPUTH-PUTFILE         =       "07X0111332" )
        // 008450      OR ( KPUTH-PUTFILE         =       "17X0115962" )
        // 008470      OR ( KPUTH-PUTFILE         =       "17X0115972" )
        // 008500      OR ( KPUTH-PUTFILE         =       "17X0130859" )
        // 008600      OR ( KPUTH-PUTFILE         =       "17X0133511" )
        // 008700      OR ( KPUTH-PUTFILE         =       "02C0149074" )
        // 008800      OR ( KPUTH-PUTFILE         =       "17X0156963" )
        // 008900      OR ( KPUTH-PUTFILE         =       "17X0156973" )
        // 009000      OR ( KPUTH-PUTFILE         =       "17X0156982" )
        // 009100      OR ( KPUTH-PUTFILE         =       "17X0156882" )
        // 009200      OR ( KPUTH-PUTFILE         =       "17X0133378" )
        // 009300      OR ( KPUTH-PUTFILE         =       "17Z0198732" )
        // 009400      OR ( KPUTH-PUTFILE         =       "17Z0193082" )
        // 009500      OR ( KPUTH-PUTFILE         =       "17Z0134309" )
        // 009550      OR ( KPUTH-PUTFILE         =       "17Z0199328" )
        // 009570      OR ( KPUTH-PUTFILE         =       "17X0151742" )
        // 009590      OR ( KPUTH-PUTFILE         =       "17X0158892" )
        // 009600      OR ( KPUTH-PUTFILE(3:1)    =       "S"          )
        // 009700      OR ( KPUTH-PUTFILE(3:1)    =       "T"          )
        if ("07X0111332".equals(kputhPutfile)
                || "17X0115962".equals(kputhPutfile)
                || "17X0115972".equals(kputhPutfile)
                || "17X0130859".equals(kputhPutfile)
                || "17X0133511".equals(kputhPutfile)
                || "02C0149074".equals(kputhPutfile)
                || "17X0156963".equals(kputhPutfile)
                || "17X0156973".equals(kputhPutfile)
                || "17X0156982".equals(kputhPutfile)
                || "17X0156882".equals(kputhPutfile)
                || "17X0133378".equals(kputhPutfile)
                || "17Z0198732".equals(kputhPutfile)
                || "17Z0193082".equals(kputhPutfile)
                || "17Z0134309".equals(kputhPutfile)
                || "17Z0199328".equals(kputhPutfile)
                || "17X0151742".equals(kputhPutfile)
                || "17X0158892".equals(kputhPutfile)
                || "S".equals(kputhPutfile.length() >= 3 ? kputhPutfile.substring(2, 3) : "")
                || "T".equals(kputhPutfile.length() >= 3 ? kputhPutfile.substring(2, 3) : "")) {
            // 009800        MOVE    KPUTH-REC        TO      KPUTH1-REC
            kputh1Rec = kputhRec;
            // 009900        WRITE   KPUTH1-REC
            sb = new StringBuilder();
            sb.append(formatUtil.padX(kputh1Rec, 140));
            fileKPUTF1Contents.add(sb.toString());
        } else {
            // 010000     ELSE
            // 010005        IF KPUTH-CODE  NOT = WK-CODE
            if (!kputhCode.equals(wkCode)) {
                // 010010           MOVE KPUTH-CODE  TO WK-CODE
                wkCode = kputhCode;
                // 010015           PERFORM UPD-CLMRFLG-RTN THRU UPD-CLMRFLG-EXIT
                updClmrflg();
                // 010020        ELSE
            } else {
                // 010025           MOVE KPUTH-CODE TO WK-CODE
                wkCode = kputhCode;
                // 010030        END-IF
            }
            // 010100        MOVE    KPUTH-REC        TO      KPUTH2-REC
            kputh2Rec = kputhRec;
            // 010200        WRITE   KPUTH2-REC
            sb = new StringBuilder();
            sb.append(formatUtil.padX(kputh2Rec, 140));
            fileKPUTF2Contents.add(sb.toString());
            // 010250     END-IF.
        }
        // 010300 FILE-DTL-EXIT.
    }

    private void updClmrflg() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "OUTKPUTF1Lsnr updClmrflg");
        // 010600 UPD-CLMRFLG-RTN.
        // 將DB-CLMR-IDX1指標移至開始
        // 010700     SET  DB-CLMR-IDX1 TO BEGINNING.
        //
        // 依代收類別LOCK事業單位基本資料檔，若有誤
        //  若找不到資料，結束本段落
        //  若其他資料庫錯誤，異常，結束程式
        //
        // 010800     LOCK DB-CLMR-IDX1 AT DB-CLMR-CODE = WK-CODE
        CltmrBus tCltmr = cltmrService.holdById(wkCode);
        if (Objects.isNull(tCltmr)) {
            // 010900     ON EXCEPTION IF DMSTATUS(NOTFOUND)
            // 011000*                    DISPLAY "FINDCLMR NOT FOUND" WK-CODE
            // 011100                     GO TO UPD-CLMRFLG-EXIT
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "FINDCLMR NOT FOUND {}", wkCode);
            return;
            // 011200                  ELSE
            // 011300                     CALL SYSTEM DMTERMINATE.
        }
        // 011400     MOVE "1" TO DB-CLMR-PRTTYP.
        tCltmr.setPrtype("1");
        // BEGIN-TRANSACTION開始交易
        // 011500     BEGIN-TRANSACTION NO-AUDIT RESTART-DST.
        // STORE DB-CLMR-DDS
        // 011600     STORE  DB-CLMR-DDS.
        try {
            cltmrService.update(tCltmr);
        } catch (Exception e) {
            throw new LogicException("", "收付開始日期限輸入合理日期");
        }
        // END-TRANSACTION確認交易
        // 011700     END-TRANSACTION NO-AUDIT RESTART-DST.
        // 011800 UPD-CLMRFLG-EXIT.
    }

    private String getrocdate(int dateI) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), " getrocdate inputdate = {}", dateI);

        String date = "" + dateI;
        if (dateI > 19110101) {
            dateI = dateI - 19110000;
        }
        if (String.valueOf(dateI).length() < 7) {
            date = String.format("%07d", dateI);
        }
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), " getrocdate outputdate = {}", date);
        return date;
    }

    private void moveErrorResponse(LogicException e) {
        //        this.event.setPeripheryRequest();
    }

    private Map<String, String> getG2007Param(String lParam) {
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
        // G2007:
        //  BRNO(;),
        //  APSEQ(;),
        //  PARAM1(;),
        //  PARAM2(;),
        //  PARAM3(;),
        //  PARAM4(;),
        //  PARAM5(;),
        //  PARAM6(;)
        Map<String, String> map = new HashMap<>();
        if (paramL.length > 0) map.put("BRNO", paramL[0]); // 對應 BRNO
        if (paramL.length > 1) map.put("APSEQ", paramL[1]); // 對應 APSEQ
        if (paramL.length > 2) map.put("PARAM1", paramL[2]); // 對應 PARAM1
        if (paramL.length > 3) map.put("PARAM2", paramL[3]); // 對應 PARAM2
        if (paramL.length > 4) map.put("PARAM3", paramL[4]); // 對應 PARAM3
        if (paramL.length > 5) map.put("PARAM4", paramL[5]); // 對應 PARAM4
        if (paramL.length > 6) map.put("PARAM5", paramL[6]); // 對應 PARAM5
        if (paramL.length > 7) map.put("PARAM6", paramL[7]); // 對應 PARAM6
        if (map.size() == 0) {
            return null;
        }
        int i = 0;
        for (String key : map.keySet()) {
            i++;
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
}
