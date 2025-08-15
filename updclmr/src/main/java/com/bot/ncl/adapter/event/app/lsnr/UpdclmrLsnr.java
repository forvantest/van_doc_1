/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.Updclmr;
import com.bot.ncl.dto.entities.*;
import com.bot.ncl.jpa.entities.impl.ClfeeId;
import com.bot.ncl.jpa.entities.impl.CltotId;
import com.bot.ncl.jpa.svc.*;
import com.bot.ncl.util.FsapBatchUtil;
import com.bot.ncl.util.fileVo.FileUpdclmr;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import com.bot.txcontrol.util.text.format.Text2VoFormatter;
import java.io.File;
import java.math.BigDecimal;
import java.util.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component("UpdclmrLsnr")
@Scope("prototype")
public class UpdclmrLsnr extends BatchListenerCase<Updclmr> {

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET = "UTF-8";
    private static final String PATH_SEPARATOR = File.separator;

    @Autowired private Parse parse;

    @Autowired private FormatUtil formatUtil;

    @Autowired private TextFileUtil textFileUtil;

    @Autowired private Text2VoFormatter text2VoFormatter;

    @Autowired private ClmrService clmrService;

    @Autowired private CltmrService cltmrService;

    @Autowired private CltotService cltotService;

    @Autowired private ClfeeService clfeeService;
    @Autowired private FsapBatchUtil fsapBatchUtil;

    @Autowired private ClmcService clmcService;
    private Updclmr event;
    private List<String> dataList;

    private List<FileUpdclmr> updclmrList;

    private Map<String, String> textMap;
    FileUpdclmr fileUpdclmr;

    private String wkPutBrno;
    private String wkPutFdate;
    private String wkPutFile;
    private String[] wkParamL;

    private String wkPutDir;

    private int wkRtncd;

    private int wkCodeExist;

    private String wkCode;

    private String wkMcode;

    private int lbsdy;

    private ClmrBus queryClmrBus;

    @Override
    public void onApplicationEvent(Updclmr event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "UpdclmrLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Updclmr event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "UpdclmrLsnr run()");
        if (!init(event)) {
            batchResponse();
            return;
        }

        mainRoutine();
        batchResponse();
    }

    private Boolean init(Updclmr event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "init()");
        // 012000 0000-START-RTN.
        // 012100     MOVE          F-BRNO          TO     WK-PUT-BRNO.
        // 012200     MOVE          F-FDATE         TO     WK-PUT-FDATE.
        // 012300     MOVE          F-FILENAME      TO     WK-PUT-FILE.
        this.event = event;
        Map<String, ArrayMap> arrayMap =
                event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();
        textMap = arrayMap.get("textMap").getMapAttrMap();
        wkPutFile = textMap.get("FILENAME");
        wkRtncd = 3;
        Map<String, String> paramMap;
        paramMap = getG2006Param(textMap.get("PARAM"));
        if (paramMap == null) {
            return false;
        }
        wkPutFdate = formatUtil.pad9(textMap.get("DATE"), 8).substring(1);
        wkPutBrno = paramMap.get("PBRNO");

        // TODO: 待確認BATCH參數名稱

        // ref:
        // 007100    01 WK-PUTDIR.
        // 007200       03 FILLER                      PIC X(18)
        // 007300                      VALUE "DATA/GN/UPL/CL001/".
        // 007400       03 WK-PUT-BRNO                 PIC X(03).
        // 007500       03 FILLER                      PIC X(01) VALUE "/".
        // 007600       03 WK-PUT-FDATE                PIC X(07).
        // 007700       03 FILLER                      PIC X(01) VALUE "/".
        // 007800       03 WK-PUT-FILE                 PIC X(12).
        // 007900       03 FILLER                      PIC X(01) VALUE ".".
        wkPutDir =
                fileDir
                        + PATH_SEPARATOR
                        + wkPutBrno
                        + PATH_SEPARATOR
                        + wkPutFdate
                        + PATH_SEPARATOR
                        + wkPutFile;
        if (!textFileUtil.exists(wkPutDir)) {
            wkRtncd = 3;
            return false;
        }
        // 012400     CHANGE ATTRIBUTE FILENAME  OF FD-UPDCLMR TO WK-PUTDIR.
        // 012500     OPEN UPDATE  BOTSRDB.
        // 012600     OPEN INPUT   FD-UPDCLMR.
        // 012700     OPEN INPUT   FD-BHDATE.
        // 012800     CHANGE ATTRIBUTE DISPLAYONLYTOMCS OF MYSELF TO TRUE.
        dataList = textFileUtil.readFileContent(wkPutDir, CHARSET);

        lbsdy = event.getAggregateBuffer().getTxCom().getLbsdy();
        return true;
    }

    private void mainRoutine() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "mainRoutine()");
        // 013500 0000-MAIN-RTN.
        // 013600 0000-MAIN-LOOP.
        // 013700     READ       FD-UPDCLMR AT END   GO TO 0000-MAIN-EXIT.
        String wkFunction;
        for (String data : dataList) {
            text2VoFormatter.format(data, fileUpdclmr);
            // 013750     MOVE       CLMR-PUTNAME(3:6)   TO    WK-MCODE.               09/07/14
            wkMcode = fileUpdclmr.getPutname().substring(2, 8);
            // 013800     MOVE       CLMR-FUNCTION       TO    WK-FUNCTION.
            wkFunction = fileUpdclmr.getFunction();

            switch (wkFunction) {
                    // 013900*--ADD---
                    // 014000     IF  WK-FUNCTION                =     0
                case "0":
                    // 014150         MOVE       0               TO    WK-RTNCD
                    // 01/05/09
                    wkRtncd = 0;
                    // 014200         PERFORM    1000-CHK-RTN    THRU  1000-CHK-EXIT
                    chk1000();
                    // 014220         IF         WK-RTNCD        =     0
                    // 09/07/14
                    // 014240           PERFORM  MERGE-CHK-RTN   THRU  MERGE-CHK-EXIT
                    // 09/07/14
                    // 014290         END-IF
                    // 09/07/14
                    if (wkRtncd == 0) {
                        mergeChk();
                    }
                    // 014300         IF         WK-RTNCD        =     0
                    // 014400         PERFORM    1500-DBIN-RTN   THRU  1500-DBIN-EXIT.
                    if (wkRtncd == 0) {
                        dbin1500();
                    }
                    break;
                    // 014500*--MODY--
                    // 014600     IF  WK-FUNCTION                =     1
                case "1":
                    // 014650         MOVE       0               TO    WK-RTNCD
                    // 01/05/09
                    wkRtncd = 0;
                    // 014700         PERFORM    2000-CHK-RTN    THRU  2000-CHK-EXIT
                    chk2000();
                    // 014720         IF         WK-RTNCD        =     0
                    // 09/07/14
                    // 014740           PERFORM  MERGE-CHK-RTN   THRU  MERGE-CHK-EXIT
                    // 09/07/14
                    // 014760         END-IF
                    // 09/07/14
                    if (wkRtncd == 0) {
                        mergeChk();
                    }
                    // 014800         IF         WK-RTNCD        =     0
                    // 014850         SET DB-CLMR-IDX1 OF DB-CLMR-DDS TO BEGINNING
                    // 07/11/28
                    // 014900         PERFORM    2500-DBIN-RTN   THRU  2500-DBIN-EXIT.
                    if (wkRtncd == 0) {
                        dbin2500();
                    }
                    break;
                    // 014910*--CHANGE PBRNO--
                    // 07/11/28
                    // 014920     IF  WK-FUNCTION                =     5
                    // 07/11/28
                case "5":
                    // 014930         MOVE       0               TO    WK-RTNCD
                    // 07/11/28
                    wkRtncd = 0;
                    // 014940         PERFORM    3000-CHK-RTN    THRU  3000-CHK-EXIT
                    // 07/11/28
                    chk3000();
                    // 014950         IF         WK-RTNCD        =     0
                    // 07/11/28
                    // 014955         SET DB-CLMR-IDX1 OF DB-CLMR-DDS TO BEGINNING
                    // 07/11/28
                    // 014960         PERFORM    3500-DBIN-RTN   THRU  3500-DBIN-EXIT.
                    // 07/11/28
                    // 014980
                    // 07/11/28
                    if (wkRtncd == 0) {
                        dbin3500(wkFunction);
                    }
                    break;
                    // 015000*--DELETE--
                    // 015100     IF  WK-FUNCTION                =     9
                case "9":
                    // 015150         MOVE       0               TO    WK-RTNCD
                    // 01/05/09
                    wkRtncd = 0;
                    // 015200         PERFORM    3000-CHK-RTN    THRU  3000-CHK-EXIT
                    chk3000();
                    // 015300         IF         WK-RTNCD        =     0
                    // 015350         SET DB-CLMR-IDX1 OF DB-CLMR-DDS TO BEGINNING
                    // 07/11/28
                    // 015400         PERFORM    3500-DBIN-RTN   THRU  3500-DBIN-EXIT.
                    // 015500
                    // 07/11/28
                    // 015550
                    // 07/11/28
                    if (wkRtncd == 0) {
                        dbin3500(wkFunction);
                    }
                    break;
                default:
                    ApLogHelper.warn(
                            log,
                            false,
                            LogType.NORMAL.getCode(),
                            "unexpected wkFunction = {}",
                            wkFunction);
                    ApLogHelper.warn(log, false, LogType.NORMAL.getCode(), "data = {}", data);
                    break;
            }
            // 015600     GO TO          0000-MAIN-LOOP.
        }
        // 015700 0000-MAIN-EXIT.
        // 015800     EXIT.
    }

    private void chk1000() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "chk1000()");
        // 036400 1000-CHK-RTN.
        // 036500     MOVE       0                   TO      WK-RTNCD.
        wkRtncd = 0;
        // 036600     PERFORM    CODE-CHK-RTN        THRU    CODE-CHK-EXIT.
        codeChkRtn();
        // 036700     IF         WK-CODE-EXIST       =       1
        // 036750       MOVE     99                  TO      WK-RTNCD              09/07/14
        // 036800       GO TO    1000-CHK-EXIT.
        if (wkCodeExist == 1) {
            wkRtncd = 99;
            return;
        }
        // 036850     PERFORM    CODE-BAN-RTN        THRU    CODE-BAN-EXIT.        04/06/24
        codeBan();
        // 036900     IF         CLMR-PBRNO          =   SPACE
        // 036950       MOVE     06                  TO      WK-RTNCD              09/07/14
        // 037000       GO TO    1000-CHK-EXIT.
        if (Objects.isNull(fileUpdclmr.getPbrno()) || fileUpdclmr.getPbrno().isBlank()) {
            wkRtncd = 6;
            return;
        }
        // 037100*    IF         CLMR-ATMCODE        NOT =   SPACE                 03/12/11
        // 037200*      GO TO    1000-CHK-EXIT.                                    03/12/11
        // 037300*
        // 037400     IF         CLMR-VRCODE         NOT =   SPACE
        // 037500       PERFORM  VRCODE-BAN-RTN      THRU    VRCODE-BAN-EXIT
        // 037600       PERFORM  VRCODE-CHK-RTN      THRU    VRCODE-CHK-EXIT.
        if (notSpace(fileUpdclmr.getVrcode())) {
            vrcodeBan();
            vrcodeChk();
        }
        // 037700*
        // 037800     IF         CLMR-RIDDUP         NOT =   SPACE
        // 037900       PERFORM  RIDDUP-CHK-RTN      THRU    RIDDUP-CHK-EXIT.
        if (notSpace(fileUpdclmr.getRiddup())) {
            riddupChk();
        }
        // 038000     IF         CLMR-MSG1           NOT =   SPACE
        // 038100       PERFORM  MSG1-CHK-RTN        THRU    MSG1-CHK-EXIT.
        if (notSpace(fileUpdclmr.getMsg1())) {
            msg1Chk();
        }
        // 038200     IF         CLMR-MSG2           NOT =   SPACE
        // 038300       PERFORM  MSG2-CHK-RTN        THRU    MSG2-CHK-EXIT.
        if (notSpace(fileUpdclmr.getMsg2())) {
            msg2Chk();
        }
        // 038320     IF         CLMR-CFEE1          NOT =   SPACE                 05/07/14
        // 038330     AND        CLMR-CFEE4          NOT =   SPACE                 05/07/14
        // 038340       PERFORM  CFEE-ADCHK-RTN      THRU    CFEE-ADCHK-EXIT.      05/07/14
        if (notSpace(fileUpdclmr.getCfee1()) && notSpace(fileUpdclmr.getCfee4())) {
            cfeeAdchk();
        }
        // 038400**   IF         CLMR-CFEE2          NOT =   SPACE                 05/11/11
        // 038500**     PERFORM  CFEE2-CHK-RTN       THRU    CFEE2-CHK-EXIT.       05/11/11
        // 038600     IF         CLMR-PUTTIME        NOT =   SPACE
        // 038700       PERFORM  PUTTIME-CHK-RTN     THRU    PUTTIME-CHK-EXIT.
        if (notSpace(fileUpdclmr.getPutname())) {
            puttimeChk();
        }
        // 038800     IF         CLMR-CHKTYPE        NOT =   SPACE
        // 038900       PERFORM  CHKTYPE-CHK-RTN     THRU    CHKTYPE-CHK-EXIT.
        if (notSpace(fileUpdclmr.getChktype())) {
            chktypeChk();
        }
        // 038920     IF         CLMR-CNAME          NOT =   SPACE                 03/12/17
        // 038940       PERFORM  CNAME-CHK-RTN       THRU    CNAME-CHK-EXIT.       03/12/17
        if (notSpace(fileUpdclmr.getCname())) {
            cnameChk();
        }
        // 038960     IF         CLMR-SCNAME         NOT =   SPACE                 03/12/17
        // 038980       PERFORM  SCNAME-CHK-RTN      THRU    SCNAME-CHK-EXIT.      03/12/17
        if (notSpace(fileUpdclmr.getScname())) {
            scnameChk();
        }
        // 039000     IF         CLMR-CDATA          NOT =   SPACE
        // 039100       PERFORM  CDATA-CHK-RTN       THRU    CDATA-CHK-EXIT.
        if (notSpace(fileUpdclmr.getCdata())) {
            cdataChk();
        }
        // 039200     IF         CLMR-FKD            NOT =   SPACE
        // 039300       PERFORM  FKD-CHK-RTN         THRU    FKD-CHK-EXIT.
        if (notSpace(fileUpdclmr.getFkd())) {
            fkdChk();
        }
        // 039400     IF         CLMR-STOP           NOT =   SPACE
        // 039500       PERFORM  STOP-CHK-RTN        THRU    STOP-CHK-EXIT.
        if (notSpace(fileUpdclmr.getStop())) {
            stopChk();
        }
        // 039600     IF         CLMR-AFCBV          NOT =   SPACE
        // 039700       PERFORM  AFCBV-CHK-RTN       THRU    AFCBV-CHK-EXIT.
        if (notSpace(fileUpdclmr.getAfcbv())) {
            afcbvChk();
        }
        // 039800     IF         CLMR-CYCK1          NOT =   SPACE
        // 039900       PERFORM  CYCK1-CHK-RTN       THRU    CYCK1-CHK-EXIT.
        if (notSpace(fileUpdclmr.getCyck1())) {
            cyck1Chk();
        }
        // 040000     IF         CLMR-CYCK2          NOT =   SPACE
        // 040100       PERFORM  CYCK2-CHK-RTN       THRU    CYCK2-CHK-EXIT.
        if (notSpace(fileUpdclmr.getCyck2())) {
            cyck2Chk();
        }
        // 040200     IF         CLMR-PUTNAME        NOT =   SPACE
        // 040300       PERFORM  PUTNAME-CHK-RTN     THRU    PUTNAME-CHK-EXIT.

        // ref:
        // 068500 PUTNAME-CHK-RTN.
        // 068600    IF           CLMR-PUTNAME      =     SPACES
        // 068700        MOVE     06                TO      WK-RTNCD               09/07/14
        // 068800        GO TO    PUTNAME-CHK-EXIT.
        // 068900 PUTNAME-CHK-EXIT.
        // 069000    EXIT.
        if (!notSpace(fileUpdclmr.getPutname())) {
            wkRtncd = 6;
        }
        // 040400     IF         CLMR-PUTTYPE        NOT =   SPACE
        // 040500       PERFORM  PUTTYPE-CHK-RTN     THRU    PUTTYPE-CHK-EXIT.
        if (notSpace(fileUpdclmr.getPuttype())) {
            puttypeChk();
        }
        // 040520     IF         CLMR-MSG2           NOT =   SPACE                 02/01/08
        // 040540       PERFORM  MSG2-CHK-RTN        THRU    MSG2-CHK-EXIT.        02/01/08
        if (notSpace(fileUpdclmr.getMsg2())) {
            msg2Chk();
        }
        // 040550* 配合電子金融部增加全繳相關欄位                                  08/12/02
        // 040560     IF    CLMR-CFEEEB NOT= SPACE  OR  CLMR-EBTYPE NOT= SPACE     08/12/02
        // 040570       OR  CLMR-LKCODE NOT= SPACE                                 08/12/02
        // 040580       PERFORM  EBILL-CHK-RTN       THRU    EBILL-CHK-EXIT.       08/12/02
        if (notSpace(fileUpdclmr.getCfeeeb())
                || notSpace(fileUpdclmr.getEbtype())
                || notSpace(fileUpdclmr.getLkcode())) {
            ebillChk();
        }
        // 040585     IF         CLMR-FEENAME        NOT =   SPACE                 11/11/22
        // 040590       PERFORM  FEENAME-CHK-RTN     THRU    FEENAME-CHK-EXIT.     11/11/22
        if (notSpace(fileUpdclmr.getFeename())) {
            feenameChk();
        }
        // 040600 1000-CHK-EXIT.
        // 040700     EXIT.
    }

    private boolean notSpace(String string) {
        return !Objects.isNull(string) && !string.isBlank();
    }

    private void codeChkRtn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "codeChkRtn()");
        // 045900 CODE-CHK-RTN.
        // 046000*    0: 查無該代收類別
        // 046100*    1: 找到該代收類別
        // 046200     MOVE       1                   TO   WK-CODE-EXIST.
        // 046300     FIND DB-CLMR-IDX1 OF DB-CLMR-DDS AT DB-CLMR-CODE = CLMR-CODE 07/11/28
        // 046400       ON EXCEPTION
        // 046500       MOVE     0                   TO   WK-CODE-EXIST.
        wkCodeExist = 1;
        queryClmrBus = clmrService.findById(fileUpdclmr.getCode());
        if (Objects.isNull(queryClmrBus)) {
            wkCodeExist = 0;
        }
        // 046600 CODE-CHK-EXIT.
        // 046700     EXIT.
    }

    private void codeBan() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "codeBan()");
        // 073300 CODE-BAN-RTN.                                                    04/06/24
        // 073400    IF CLMR-CODE(1:1) = "6" OR = "8"                              04/06/24
        // 073500       MOVE 99 TO WK-RTNCD.                                       09/07/14
        String firstCode = fileUpdclmr.getCode().substring(0, 1);
        int intFirstCode = parse.isNumeric(firstCode) ? parse.string2Integer(firstCode) : 0;
        if (intFirstCode == 6 || intFirstCode == 8) {
            wkRtncd = 99;
        }
        // 073600 CODE-BAN-EXIT.                                                   04/06/24
        // 073700    EXIT.                                                         04/06/24
    }

    private void vrcodeBan() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "vrcodeBan()");
        // 046900 VRCODE-BAN-RTN.
        // 047000     MOVE       CLMR-CODE           TO   WK-CODE
        wkCode = fileUpdclmr.getCode();
        String wkCode1 = wkCode.substring(0, 1);
        String wkCode2 = wkCode.substring(1, 2);
        // 047100     IF         WK-CODE1            =    "1"
        // 047200        AND (   WK-CODE2            =    "6"  OR "7" OR "8")      01/07/18
        // 047300     MOVE       06                  TO   WK-RTNCD.                09/07/14
        if (wkCode1.equals("1")
                && (wkCode2.equals("6") || wkCode2.equals("7") || wkCode2.equals("8"))) {
            wkRtncd = 6;
        }
        // 047400     IF         CLMR-VRCODE         >    5999
        // 047500        AND     CLMR-VRCODE         <    9000                     01/07/18
        // 047600     MOVE       06                  TO   WK-RTNCD.                09/07/14
        int intVrcode =
                parse.isNumeric(fileUpdclmr.getVrcode())
                        ? parse.string2Integer(fileUpdclmr.getVrcode())
                        : 0;
        if (intVrcode > 5999 && intVrcode < 9000) {
            wkRtncd = 6;
        }
        // 047700 VRCODE-BAN-EXIT.
        // 047800     EXIT.
    }

    private void vrcodeChk() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "vrcodeChk()");
        // 048000 VRCODE-CHK-RTN.
        // 048100     MOVE       CLMR-CODE           TO    WK-CODE.
        wkCode = fileUpdclmr.getCode();
        String wkCode1 = wkCode.substring(0, 1);
        // 048400     IF         WK-CODE1            NOT = "1"
        // 048500        AND     CLMR-VRCODE         NOT = 0000
        // 048600        MOVE    06                  TO    WK-RTNCD                09/07/14
        // 048700        GO TO   VRCODE-CHK-EXIT.
        if (!wkCode1.equals("1") && !fileUpdclmr.getVrcode().equals("0000")) {
            wkRtncd = 6;
        }
        // 048800 VRCODE-CHK-EXIT.
        // 048900     EXIT.
    }

    private void riddupChk() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "riddupChk()");
        // 049100 RIDDUP-CHK-RTN.
        // 049200     IF         CLMR-RIDDUP         NOT = 0 AND NOT = 1
        // 049300       MOVE     06                  TO    WK-RTNCD                09/07/14
        // 049400       GO TO    RIDDUP-CHK-EXIT.
        int intRiddup =
                parse.isNumeric(fileUpdclmr.getRiddup())
                        ? parse.string2Integer(fileUpdclmr.getRiddup())
                        : -1;
        if (intRiddup != 0 && intRiddup != 1) {
            wkRtncd = 6;
        }
        // 049500 RIDDUP-CHK-EXIT.
        // 049600     EXIT.
    }

    private void msg1Chk() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "msg1Chk()");
        // 049800 MSG1-CHK-RTN.
        // 049900     IF         CLMR-MSG1  NOT = 0 AND NOT = 1 AND NOT = 2        00/08/25
        // 050000       MOVE     06                  TO    WK-RTNCD                09/07/14
        // 050100       GO TO    MSG1-CHK-EXIT.
        int intMsg1 =
                parse.isNumeric(fileUpdclmr.getMsg1())
                        ? parse.string2Integer(fileUpdclmr.getMsg1())
                        : -1;
        if (intMsg1 != 0 && intMsg1 != 1 && intMsg1 != 2) {
            wkRtncd = 6;
        }
        // 050200 MSG1-CHK-EXIT.
        // 050300     EXIT.
    }

    private void msg2Chk() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "msg2Chk()");
        // 050500 MSG2-CHK-RTN.
        // 050600     IF          CLMR-MSG2          NOT = 00 AND NOT = 01         02/01/08
        // 050700       MOVE     06                  TO    WK-RTNCD                09/07/14
        // 050800       GO TO    MSG2-CHK-EXIT.
        if (!fileUpdclmr.getMsg2().equals("00") && !fileUpdclmr.getMsg2().equals("01")) {
            wkRtncd = 6;
        }
        // 050900 MSG2-CHK-EXIT.
        // 051000     EXIT.
    }

    private void cfeeAdchk() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "cfeeAdchk()");
        // 051007*CLMR-CFEE1 & CLMR-CFEE4  不可同時大於 0                          05/07/14
        // 051009*--ADD--                                                          05/07/14
        // 051010 CFEE-ADCHK-RTN.                                                  05/07/14
        // 051035    IF CLMR-CFEE1 NOT = 0  AND  CLMR-CFEE4 NOT = 0                05/07/14
        // 051040       MOVE 06 TO WK-RTNCD                                        09/07/14
        // 051045    END-IF.                                                       05/07/14
        int intCfee1 =
                parse.isNumeric(fileUpdclmr.getCfee1())
                        ? parse.string2Integer(fileUpdclmr.getCfee1())
                        : 0;
        int intCfee4 =
                parse.isNumeric(fileUpdclmr.getCfee4())
                        ? parse.string2Integer(fileUpdclmr.getCfee4())
                        : 0;
        if (intCfee1 != 0 && intCfee4 != 0) {
            wkRtncd = 6;
        }
        // 051055 CFEE-ADCHK-EXIT.                                                 05/07/14
        // 051060    EXIT.                                                         05/07/14
    }

    private void puttimeChk() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "puttimeChk()");
        // 052400 PUTTIME-CHK-RTN.
        String puttime = fileUpdclmr.getPuttime();
        int intPuttime = parse.isNumeric(puttime) ? parse.string2Integer(puttime) : -1;
        // 055100     IF         CLMR-PUTTIME        NOT = 0
        // 055200                         AND        NOT = 1
        // 055300                         AND        NOT = 2
        // 055400       MOVE     06                  TO    WK-RTNCD                09/07/14
        // 055500       GO TO    PUTTIME-CHK-EXIT.
        if (intPuttime != 0 && intPuttime != 1 && intPuttime != 2) {
            wkRtncd = 6;
            return;
        }
        String cyck1 = fileUpdclmr.getCyck1();
        String cycno1 = fileUpdclmr.getCycno1();
        int intCyck1 = parse.isNumeric(cyck1) ? parse.string2Integer(cyck1) : -1;
        int intCycno1 = parse.isNumeric(cycno1) ? parse.string2Integer(cycno1) : -1;
        // 055600     IF         CLMR-PUTTIME        =     1  OR =  2
        // 055700       IF      (CLMR-CYCK1   = 1  AND CLMR-CYCNO1 = 1)            08/11/28
        // 055800       OR      (CLMR-CYCK1   = 5  AND CLMR-CYCNO1 = 3)            08/11/28
        // 055820         CONTINUE                                                 08/11/28
        // 055840       ELSE                                                       08/11/28
        // 055900         MOVE   12                  TO    WK-RTNCD                09/07/14
        // 056000         GO TO  PUTTIME-CHK-EXIT.
        if (intPuttime == 1 || intPuttime == 2) {
            if ((intCyck1 == 1 && intCycno1 == 1) || (intCyck1 == 5 && intCycno1 == 3)) {
                // CONTINUE
            } else {
                wkRtncd = 12;
                return;
            }
        }
        // 056100     IF         CLMR-PUTTIME        =     1  OR =  2
        // 056200       IF       CLMR-NETINFO        =     SPACE                   04/04/16
        // 056300         MOVE   06                  TO    WK-RTNCD.               09/07/14
        // 056400       GO  TO   PUTTIME-CHK-EXIT.
        if (intPuttime == 1 || intPuttime == 2) {
            if (Objects.isNull(fileUpdclmr.getNetinfo()) || fileUpdclmr.getNetinfo().isBlank()) {
                wkRtncd = 6;
            }
        }
        // 056500 PUTTIME-CHK-EXIT.
        // 056600     EXIT.
    }

    private void chktypeChk() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "chktypeChk()");
        // 056800 CHKTYPE-CHK-RTN.
        // 056900     IF         CLMR-CHKTYPE        NOT = 0  AND NOT = 1
        // 057000     AND NOT = 2  AND NOT = 3  AND  NOT = 4  AND NOT = 5          00/07/01
        // 057100     AND NOT = 6  AND NOT = 7  AND  NOT = 8  AND NOT = 9          00/07/01
        // 057200     AND NOT ="A" AND NOT ="B" AND  NOT ="C" AND NOT ="D"         00/07/01
        // 057300     AND NOT ="E" AND NOT ="F" AND  NOT ="G" AND NOT ="H"         00/07/01
        // 057350     AND NOT ="I" AND NOT ="X" AND  NOT ="J" AND NOT ="K"         05/12/16
        // 057400       MOVE     06                  TO    WK-RTNCD                09/07/14
        // 057500       GO TO    CHKTYPE-CHK-EXIT.
        String chktype = fileUpdclmr.getChktype();
        switch (chktype) {
            case "0",
                    "1",
                    "2",
                    "3",
                    "4",
                    "5",
                    "6",
                    "7",
                    "8",
                    "9",
                    "A",
                    "B",
                    "C",
                    "D",
                    "E",
                    "F",
                    "G",
                    "H",
                    "I",
                    "X",
                    "J",
                    "K":
                // OK
                break;
            default:
                wkRtncd = 6;
                break;
        }
        // 057600 CHKTYPE-CHK-EXIT.
        // 057700     EXIT.
    }

    private void cnameChk() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "cnameChk()");
        // 070500 CNAME-CHK-RTN.                                                   03/12/17
        String cname = fileUpdclmr.getCname();
        // 070520    IF CLMR-CNAME(1:1) = SPACE                                    03/12/17
        // 070530       DISPLAY "FIRST WORD IS SPACES, THIS IS WRONG!"             03/12/17
        // 070540       MOVE 06 TO WK-RTNCD                                        09/07/14
        // 070560       GO TO CNAME-CHK-EXIT.                                      03/12/17
        if (cname.startsWith(" ")) {
            ApLogHelper.error(
                    log, false, LogType.NORMAL.getCode(), "FIRST WORD IS SPACES, THIS IS WRONG!");
            wkRtncd = 6;
            return;
        }
        // 070600    MOVE SPACES TO WK-DATAIN-REC.                                 03/12/17
        String wkDatainRec = "";
        // 070650    MOVE 0 TO WK-DATAOUT-TYPE.                                    03/12/17
        int wkDataoutType = 0;
        // 070700    MOVE 40 TO WK-DATAIN-LENG.                                    03/12/17
        int wkDatainLeng = 40;
        // 070800    MOVE CLMR-CNAME TO WK-DATAIN-DATA.                            03/12/17
        String wkDatainData = cname;
        // 070900    CALL "DATACHECK IN SYSTEM/UTL/FMTCVT"                         03/12/17
        // 071000                    USING WK-DATAIN-REC,                          03/12/17
        // 071100                    GIVING WK-DATAOUT-TYPE.                       03/12/17
        // 071200    IF WK-DATAOUT-TYPE = 0                                        03/12/17
        // 071250       DISPLAY "THIS IS WRONG CHINESE WORD"                       03/12/17
        // 071300       MOVE 06 TO WK-RTNCD                                        09/07/14
        // 071400       GO TO CNAME-CHK-EXIT.                                      03/12/17

        // TODO: 待完成
        // from 台銀-IT-展嘉) 中文檢查 是否全形 不是時要轉成全形

        // 071500 CNAME-CHK-EXIT.                                                  03/12/17
        // 071600    EXIT.                                                         03/12/17
    }

    private void scnameChk() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "scnameChk()");
        // 071800 SCNAME-CHK-RTN.                                                  03/12/17
        // 071900    IF CLMR-SCNAME(1:1) = SPACE                                   03/12/17
        // 071950       DISPLAY "FIRST WORD IS SPACES, THIS IS WRONG!"             03/12/17
        // 072000       MOVE 06 TO WK-RTNCD                                        09/07/14
        // 072100       GO TO SCNAME-CHK-EXIT.                                     03/12/17
        String scname = fileUpdclmr.getScname();
        if (scname.startsWith(" ")) {
            ApLogHelper.error(
                    log, false, LogType.NORMAL.getCode(), "FIRST WORD IS SPACES, THIS IS WRONG!");
            wkRtncd = 6;
            return;
        }
        // 072200    MOVE SPACES TO WK-DATAIN-REC.                                 03/12/17
        String wkDatainRec = "";
        // 072250    MOVE 0 TO WK-DATAOUT-TYPE.                                    03/12/17
        int wkDataoutType = 0;
        // 072300    MOVE 10 TO WK-DATAIN-LENG.                                    03/12/17
        int wkDatainLeng = 40;
        // 072400    MOVE CLMR-SCNAME TO WK-DATAIN-DATA.                           03/12/17
        String wkDatainData = scname;
        // 072500    CALL "DATACHECK IN SYSTEM/UTL/FMTCVT"                         03/12/17
        // 072600                    USING WK-DATAIN-REC,                          03/12/17
        // 072700                    GIVING WK-DATAOUT-TYPE.                       03/12/17
        // 072800    IF WK-DATAOUT-TYPE = 0                                        03/12/17
        // 072850       DISPLAY "THIS IS WRONG CHINESE WORD"                       03/12/17
        // 072900       MOVE 06 TO WK-RTNCD                                        09/07/14
        // 072950       GO TO SCNAME-CHK-EXIT.                                     03/12/17

        // TODO: 待完成
        // from 台銀-IT-展嘉) 中文檢查 是否全形 不是時要轉成全形

        // 073000 SCNAME-CHK-EXIT.                                                 03/12/17
        // 073100    EXIT.                                                         03/12/17
    }

    private void cdataChk() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "cdataChk()");
        // 058800 CDATA-CHK-RTN.
        // 058850     IF WK-CODE1 = "3" AND WK-CODE2 = "9"                         03/12/17
        // 058900        IF CLMR-CDATA  NOT = 1 AND NOT = 2 AND                    03/12/17
        // 059000                       NOT = 3 AND NOT = 4                        03/12/17
        // 059100        MOVE     06                  TO    WK-RTNCD               09/07/14
        // 059200        GO TO    CDATA-CHK-EXIT.                                  03/12/17
        String wkCode1 = wkCode.substring(0, 1);
        String wkCode2 = wkCode.substring(1, 2);
        String cdata = fileUpdclmr.getCdata();
        if (wkCode1.equals("3") && wkCode2.equals("9")) {
            if (!cdata.equals("1")
                    && !cdata.equals("2")
                    && !cdata.equals("3")
                    && !cdata.equals("4")) {
                wkRtncd = 6;
                return;
            }
        }
        // 059220     IF CLMR-CDATA NOT = 0                                        03/12/17
        // 059230        MOVE 06 TO WK-RTNCD                                       09/07/14
        // 059240        GO TO CDATA-CHK-EXIT.                                     03/12/17
        if (!cdata.equals("0")) {
            wkRtncd = 6;
        }
        // 059300 CDATA-CHK-EXIT.
        // 059400     EXIT.
    }

    private void fkdChk() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "fkdChk()");
        // 059600 FKD-CHK-RTN.
        // 059700    IF          CLMR-FKD            NOT = 0
        // 059800                     AND            NOT = 1
        // 059900                     AND            NOT = 2
        // 060000                     AND            NOT = 3
        // 060100      MOVE      06                  TO    WK-RTNCD                09/07/14
        // 060200      GO TO     FKD-CHK-EXIT.
        String fkd = fileUpdclmr.getFkd();
        int intFkd = parse.isNumeric(fkd) ? parse.string2Integer(fkd) : -1;
        if (intFkd != 0 && intFkd != 1 && intFkd != 2 && intFkd != 3) {
            wkRtncd = 6;
        }
        // 060300 FKD-CHK-EXIT.
        // 060400     EXIT.
    }

    private void stopChk() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "stopChk()");
        // 060600 STOP-CHK-RTN.
        // 060700    IF          CLMR-STOP          NOT = 0  AND NOT = 1           01/07/18
        // 060800                       AND         NOT = 2  AND NOT = 3
        // 060900                       AND         NOT = 4  AND NOT = 5
        // 060950                       AND         NOT = 6  AND NOT = 7           00/08/25
        // 060970                       AND         NOT = 8                        02/09/13
        // 061000      MOVE      06                 TO    WK-RTNCD                 09/07/14
        // 061100      GO TO     STOP-CHK-EXIT.
        String stop = fileUpdclmr.getStop();
        int intStop = parse.isNumeric(stop) ? parse.string2Integer(stop) : -1;
        if (intStop != 0
                && intStop != 1
                && intStop != 2
                && intStop != 3
                && intStop != 4
                && intStop != 5
                && intStop != 6
                && intStop != 7
                && intStop != 8) {
            wkRtncd = 6;
        }
        // 061200 STOP-CHK-EXIT.
        // 061300     EXIT.
    }

    private void afcbvChk() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "afcbvChk()");
        // 061500 AFCBV-CHK-RTN.
        // 061600    IF          CLMR-AFCBV         NOT = 0
        // 061700                       AND         NOT = 1
        // 061800      MOVE      06                 TO    WK-RTNCD                 09/07/14
        // 061900      GO TO     AFCBV-CHK-EXIT.
        String afcbv = fileUpdclmr.getAfcbv();
        int intAfcbv = parse.isNumeric(afcbv) ? parse.string2Integer(afcbv) : -1;
        if (intAfcbv != 0 && intAfcbv != 1) {
            wkRtncd = 6;
        }
        // 062000 AFCBV-CHK-EXIT.
        // 062100     EXIT.
    }

    private void cyck1Chk() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "cyck1Chk()");
        // 062300 CYCK1-CHK-RTN.
        // 062400    IF          CLMR-CYCK1         =     0
        // 062500      IF        CLMR-CYCNO1        =     0
        // 062600        IF      CLMR-PUTDT         =     0
        // 062700          MOVE  06                 TO    WK-RTNCD                 09/07/14
        // 062800          GO TO CYCK1-CHK-EXIT
        // 062900        ELSE
        // 063000          NEXT  SENTENCE
        // 063100      ELSE
        // 063200          MOVE  06                 TO    WK-RTNCD                 09/07/14
        // 063300          GO TO CYCK1-CHK-EXIT.
        String cyck1 = fileUpdclmr.getCyck1();
        String cycno1 = fileUpdclmr.getCycno1();
        String putdt = fileUpdclmr.getPutdt();
        int intCyck1 = parse.isNumeric(cyck1) ? parse.string2Integer(cyck1) : 0;
        int intCycno1 = parse.isNumeric(cycno1) ? parse.string2Integer(cycno1) : 0;
        int intPutdt = parse.isNumeric(putdt) ? parse.string2Integer(putdt) : 0;
        if (intCyck1 == 0) {
            if (intCycno1 == 0) {
                if (intPutdt == 0) {
                    wkRtncd = 6;
                    return;
                } else {
                    // NEXT  SENTENCE
                }
            } else {
                wkRtncd = 6;
                return;
            }
        }
        // 063400    IF          CLMR-CYCK1         =     1
        // 063500      AND (     CLMR-CYCNO1        >     9 OR = 0)
        // 063600          MOVE  06                 TO    WK-RTNCD                 09/07/14
        // 063700          GO TO CYCK1-CHK-EXIT.
        if (intCyck1 == 1 && (intCycno1 > 9 || intCycno1 == 0)) {
            wkRtncd = 6;
            return;
        }
        // 063800    IF          CLMR-CYCK1         =     2
        // 063900      AND (     CLMR-CYCNO1        >     6 OR = 0)
        // 064000          MOVE  06                 TO    WK-RTNCD                 09/07/14
        // 064100          GO TO CYCK1-CHK-EXIT.
        if (intCyck1 == 2 && (intCycno1 > 6 || intCycno1 == 0)) {
            wkRtncd = 6;
            return;
        }
        // 064200    IF          CLMR-CYCK1         =     3
        // 064300      AND (     CLMR-CYCNO1        >     28  OR = 0)
        // 064400          MOVE  06                 TO    WK-RTNCD                 09/07/14
        // 064500          GO TO CYCK1-CHK-EXIT.
        if (intCyck1 == 3 && (intCycno1 > 28 || intCycno1 == 0)) {
            wkRtncd = 6;
            return;
        }
        // 064600    IF          CLMR-CYCK1         =     4
        // 064700      IF        CLMR-CYCNO1        =     10 OR 15 OR 30
        // 064800          NEXT  SENTENCE
        // 064900      ELSE
        // 065000          MOVE  06                 TO    WK-RTNCD                 09/07/14
        // 065100          GO TO CYCK1-CHK-EXIT.
        if (intCyck1 == 4) {
            if (intCycno1 == 10 || intCycno1 == 15 || intCycno1 == 30) {

            } else {
                wkRtncd = 6;
                return;
            }
        }
        // 065200    IF          CLMR-CYCK1         =     5
        // 065300      AND (     CLMR-CYCNO1        >     3   OR = 0)              99/12/24
        // 065400          MOVE  06                 TO    WK-RTNCD                 09/07/14
        // 065500          GO TO CYCK1-CHK-EXIT.
        if (intCyck1 == 5 && (intCycno1 > 3 || intCycno1 == 0)) {
            wkRtncd = 6;
        }
        // 065600 CYCK1-CHK-EXIT.
        // 065700    EXIT.
    }

    private void cyck2Chk() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "cyck2Chk()");
        // 065900 CYCK2-CHK-RTN.
        // 066000    IF          CLMR-CYCK2         NOT = 0 AND  NOT= 2
        // 066100                       AND         NOT = 3 AND  NOT= 4
        // 066200          MOVE  06                 TO    WK-RTNCD                 09/07/14
        // 066300          GO TO CYCK2-CHK-EXIT.
        String cyck2 = fileUpdclmr.getCyck2();
        int intCyck2 = parse.isNumeric(cyck2) ? parse.string2Integer(cyck2) : -1;
        // ST1 Wei : 和展嘉確認過 增加5
        if (intCyck2 != 0 && intCyck2 != 2 && intCyck2 != 3 && intCyck2 != 4 && intCyck2 != 5) {
            wkRtncd = 6;
            return;
        }
        // 066400    IF          CLMR-CYCK2         =     0
        // 066500      AND       CLMR-CYCNO2        NOT = 0
        // 066600         MOVE   06                 TO    WK-RTNCD                 09/07/14
        // 066700         GO TO  CYCK2-CHK-EXIT.
        String cycno2 = fileUpdclmr.getCycno2();
        int intCycno2 = parse.isNumeric(cycno2) ? parse.string2Integer(cycno2) : -1;
        if (intCyck2 == 0 && intCycno2 != 0) {
            wkRtncd = 6;
            return;
        }
        // 066800    IF          CLMR-CYCK2        =      3
        // 066900      AND (     CLMR-CYCNO2       >      28  OR = 0)
        // 067000         MOVE  06                 TO     WK-RTNCD                 09/07/14
        // 067100         GO TO CYCK2-CHK-EXIT.
        if (intCyck2 == 3 && (intCycno2 > 28 || intCycno2 == 0)) {
            wkRtncd = 6;
            return;
        }
        // 067200    IF          CLMR-CYCK2         =     4
        // 067300      IF        CLMR-CYCNO2        =     10 OR 15 OR 30
        // 067400          NEXT  SENTENCE
        // 067500      ELSE
        // 067600          MOVE  06                 TO    WK-RTNCD                 09/07/14
        // 067700          GO TO CYCK2-CHK-EXIT.
        if (intCyck2 == 4) {
            if (intCycno2 == 10 || intCycno2 == 20 || intCycno2 == 30) {
                // NEXT  SENTENCE
            } else {
                wkRtncd = 6;
                return;
            }
        }
        // 067800    IF          CLMR-CYCK2         =     5
        // 067900      AND (     CLMR-CYCNO2        >     3   OR = 0)              99/12/24
        // 068000          MOVE  06                 TO    WK-RTNCD                 09/07/14
        // 068100          GO TO CYCK2-CHK-EXIT.
        // ST1 Wei:原程式不會走進這裡 ,但先照原程式寫
        if (intCyck2 == 5 && (intCycno2 > 3 || intCycno2 == 0)) {
            wkRtncd = 6;
        }
        // 068200 CYCK2-CHK-EXIT.
        // 068300    EXIT.
    }

    private void puttypeChk() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "puttypeChk()");
        // 069200 PUTTYPE-CHK-RTN.
        // 069300    IF          CLMR-PUTTYPE       NOT = 02 AND NOT = 03          01/07/18
        // 069400                         AND       NOT = 05 AND NOT = 07          01/07/18
        // 069500                         AND       NOT = 10 AND NOT = 12
        // 069600                         AND       NOT = 13 AND NOT = 15
        // 069700                         AND       NOT = 17 AND NOT = 20
        // 069800                         AND       NOT = 22 AND NOT = 23
        // 069900                         AND       NOT = 25 AND NOT = 27
        // 070000        MOVE     06              TO      WK-RTNCD                 09/07/14
        // 070100        GO TO    PUTTYPE-CHK-EXIT.
        String puttype = fileUpdclmr.getPuttype();
        switch (puttype) {
            case "02", "03", "05", "07", "10", "12", "13", "15", "17", "20", "22", "23", "25", "27":
                break;
            default:
                wkRtncd = 6;
                break;
        }
        // 070200 PUTTYPE-CHK-EXIT.
        // 070300    EXIT.
    }

    private void ebillChk() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "ebillChk()");
        // 073210 EBILL-CHK-RTN.                                                   08/12/02
        // 073220                                                                  08/12/02
        // 073230    IF  CLMR-CODE(1:1) NOT= "5"                                   08/12/02
        // 073240        MOVE     06                TO      WK-RTNCD               09/07/14
        // 073250        GO TO    EBILL-CHK-EXIT.                                  08/12/02
        // 073260                                                                  08/12/02
        String code1 = "";
        try {
            code1 = fileUpdclmr.getCode().substring(0, 1);
        } catch (Exception e) {
            ApLogHelper.warn(
                    log, false, LogType.NORMAL.getCode(), "ebillChk warn={}", e.getMessage());
            code1 = " ";
        }
        if (!code1.equals("5")) {
            wkRtncd = 6;
        }
        // 073270 EBILL-CHK-EXIT.                                                  08/12/02
        // 073280    EXIT.                                                         08/12/02
    }

    private void feenameChk() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "feenameChk()");
        // 073110 FEENAME-CHK-RTN.                                                 11/11/22
        // 073112**  DISPLAY "**** FEENAME-CHK-RTN:".                              11/11/22
        // 073114**  DISPLAY "FEENAME=" CLMR-FEENAME.                              11/11/22
        // 073115    IF CLMR-FEENAME(1:1) = SPACE                                  11/11/22
        // 073120       DISPLAY "FIRST WORD IS SPACES, THIS IS WRONG!"             11/11/22
        // 073125       MOVE 06 TO WK-RTNCD                                        11/11/22
        // 073130       GO TO FEENAME-CHK-EXIT.                                    11/11/22
        String feename = fileUpdclmr.getFeename();
        String feename1;
        try {
            feename1 = feename.substring(0, 1);
        } catch (Exception e) {
            ApLogHelper.warn(
                    log, false, LogType.NORMAL.getCode(), "feenameChk warn={}", e.getMessage());
            feename1 = " ";
        }
        if (feename1.isBlank()) {
            ApLogHelper.info(
                    log, false, LogType.NORMAL.getCode(), "FIRST WORD IS SPACES, THIS IS WRONG!");
            wkRtncd = 6;
        }
        // 073135    MOVE SPACES TO WK-DATAIN-REC.                                 11/11/22
        // 073140    MOVE 0 TO WK-DATAOUT-TYPE.                                    11/11/22
        // 073145    MOVE 42 TO WK-DATAIN-LENG.                                    11/11/22
        // 073150    MOVE CLMR-FEENAME TO WK-DATAIN-DATA.                          11/11/22
        // 073155    CALL "DATACHECK IN SYSTEM/UTL/FMTCVT"                         11/11/22
        // 073160                    USING WK-DATAIN-REC,                          11/11/22
        // 073165                    GIVING WK-DATAOUT-TYPE.                       11/11/22
        // 073168**  DISPLAY "*** FEENAME-CHK WK-DATAOUT-TYPE=" WK-DATAOUT-TYPE.   11/11/22

        // TODO: 待完成
        // from 台銀-IT-展嘉) 中文檢查 是否全形 不是時要轉成全形

        // 073170    IF WK-DATAOUT-TYPE = 0                                        11/11/22
        // 073175       DISPLAY "THIS IS WRONG CHINESE WORD"                       11/11/22
        // 073180       MOVE 06 TO WK-RTNCD                                        11/11/22
        // 073185       GO TO FEENAME-CHK-EXIT.                                    11/11/22
        // 073190 FEENAME-CHK-EXIT.                                                11/11/22
        // 073195    EXIT.                                                         11/11/22
    }

    private void mergeChk() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "mergeChk()");
        // 076500 MERGE-CHK-RTN.                                                   09/07/14
        // 076550     SET  DB-CLMR-IDX1 OF DB-CLMR-DDS TO BEGINNING.               09/07/14
        // 076600                                                                  09/07/14
        // 076700     IF   WK-MCODE     =      CLMR-CODE                           09/07/14
        // 076800          GO           TO     MERGE-CHK-EXIT.                     09/07/14
        // 076900                                                                  09/07/14
        // 077000                                                                  09/07/14
        if (wkMcode.equals(fileUpdclmr.getCode())) {
            return;
        }
        // 077100     FIND DB-CLMR-IDX1   OF DB-CLMR-DDS                           09/07/14
        // 077200         AT DB-CLMR-CODE OF DB-CLMR-DDS = WK-MCODE                09/07/14
        // 077300         ON EXCEPTION                                             09/07/14
        // 077400            MOVE  09   TO     WK-RTNCD                            09/07/14
        // 077500            GO TO MERGE-CHK-EXIT.                                 09/07/14
        ClmrBus clmrBus;
        try {
            clmrBus = clmrService.findById(wkMcode);
        } catch (Exception e) {
            ApLogHelper.error(
                    log, false, LogType.NORMAL.getCode(), "mergeChk error={}", e.getMessage());
            wkRtncd = 9;
            return;
        }
        if (Objects.isNull(clmrBus)) {
            wkRtncd = 9;
            return;
        }
        CltmrBus cltmrBus;
        try {
            cltmrBus = cltmrService.findById(wkMcode);
        } catch (Exception e) {
            ApLogHelper.error(
                    log, false, LogType.NORMAL.getCode(), "mergeChk error={}", e.getMessage());
            wkRtncd = 9;
            return;
        }
        if (Objects.isNull(cltmrBus)) {
            wkRtncd = 9;
            return;
        }
        ClmcBus clmcBus;
        String dbPutname = cltmrBus.getPutname();
        try {
            clmcBus = clmcService.findById(dbPutname);
        } catch (Exception e) {
            ApLogHelper.error(
                    log, false, LogType.NORMAL.getCode(), "mergeChk error={}", e.getMessage());
            wkRtncd = 9;
            return;
        }
        if (Objects.isNull(clmcBus)) {
            wkRtncd = 9;
            return;
        }
        // 077600* 合併檔名不相符                                                  09/07/14
        // 077700     IF CLMR-PUTNAME NOT= DB-CLMR-PUTNAME OF DB-CLMR-DDS          09/07/14
        // 077800     OR CLMR-PUTTYPE NOT= DB-CLMR-PUTTYPE OF DB-CLMR-DDS          09/07/14
        // 077900        MOVE  09   TO     WK-RTNCD.                               09/07/14
        String oldPutname = formatUtil.padX(clmcBus.getPutsend(), 1);
        oldPutname += formatUtil.padX(clmcBus.getPutform(), 1);
        oldPutname += formatUtil.padX(clmcBus.getPutname(), 6);
        String filePutname = fileUpdclmr.getPutname();
        int filePuttype =
                parse.isNumeric(fileUpdclmr.getPuttype())
                        ? parse.string2Integer(fileUpdclmr.getPuttype())
                        : -1;
        if (!filePutname.equals(oldPutname) || filePuttype != clmcBus.getPuttype()) {
            wkRtncd = 9;
        }
        // 078000* 合併代收類別軋帳記號不相符                                      09/07/14
        // 078100     IF CLMR-AFCBV   NOT= DB-CLMR-AFCBV   OF DB-CLMR-DDS          09/07/14
        // 078200        MOVE  10   TO     WK-RTNCD.                               09/07/14
        int dbAfcbv = clmrBus.getAfcbv();
        int fileAfcbv =
                parse.isNumeric(fileUpdclmr.getAfcbv())
                        ? parse.string2Integer(fileUpdclmr.getAfcbv())
                        : -1;
        if (fileAfcbv != dbAfcbv) {
            wkRtncd = 10;
        }
        // 078300* 合併銷帳週期不相符                                              09/07/14
        // 078400     IF CLMR-CYCK1   NOT= DB-CLMR-CYCK1   OF DB-CLMR-DDS          09/07/14
        // 078500     OR CLMR-CYCNO1  NOT= DB-CLMR-CYCNO1  OF DB-CLMR-DDS          09/07/14
        // 078600        MOVE  11   TO     WK-RTNCD.                               09/07/14
        // 078700                                                                  09/07/14
        int dbCyck1 = clmcBus.getCyck1();
        int dbCycno1 = clmcBus.getCycno1();
        int fileCyck1 =
                parse.isNumeric(fileUpdclmr.getCyck1())
                        ? parse.string2Integer(fileUpdclmr.getCyck1())
                        : -1;
        int fileCycno1 =
                parse.isNumeric(fileUpdclmr.getCycno1())
                        ? parse.string2Integer(fileUpdclmr.getCyck1())
                        : -1;
        if (fileCyck1 != dbCyck1 || fileCycno1 != dbCycno1) {
            wkRtncd = 11;
        }
        // 078800 MERGE-CHK-EXIT.                                                  09/07/14
        // 078900     EXIT.                                                        09/07/14
    }

    private void dbin1500() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "dbin1500()");
        // 016000 1500-DBIN-RTN.
        // 016100     CREATE     DB-CLMR-DDS.
        // 016150     MOVE       LOW-VALUE         TO      DB-CLMR-DDS.            04/04/16
        ClmrBus clmrBus = new ClmrBus();
        CltmrBus cltmrBus = new CltmrBus();
        CltotBus cltotBus = new CltotBus();
        ClfeeBus clfeeBus = new ClfeeBus();
        ClmcBus clmcBus = new ClmcBus();
        // 016200     MOVE  CLMR-CODE      TO  DB-CLMR-CODE OF DB-CLMR-DDS.        07/11/28
        clmrBus.setCode(fileUpdclmr.getCode());
        cltmrBus.setCode(fileUpdclmr.getCode());
        cltotBus.setCode(fileUpdclmr.getCode());
        clfeeBus.setKeyCodeFee(fileUpdclmr.getCode());
        // 016300     MOVE  CLMR-PBRNO     TO  DB-CLMR-PBRNO OF DB-CLMR-DDS.       07/11/28
        int pbrno = parseInt(fileUpdclmr.getPbrno(), 0);
        clmrBus.setPbrno(pbrno);
        // 016400     MOVE  CLMR-ATMCODE   TO  DB-CLMR-ATMCODE OF DB-CLMR-DDS.     07/11/28
        int atmcode = parseInt(fileUpdclmr.getAtmcode(), 0);
        cltmrBus.setAtmcode(atmcode);
        // 016500     MOVE  CLMR-VRCODE    TO  DB-CLMR-VRCODE OF DB-CLMR-DDS.      07/11/28
        int vrcode = parseInt(fileUpdclmr.getVrcode(), 0);
        clmrBus.setVrcode(vrcode);
        // 016600     MOVE  CLMR-RIDDUP    TO  DB-CLMR-RIDDUP OF DB-CLMR-DDS.      07/11/28
        int riddup = parseInt(fileUpdclmr.getRiddup(), 0);
        clmrBus.setRiddup(riddup);
        // 016700     MOVE  CLMR-ACTNO     TO  DB-CLMR-ACTNO OF DB-CLMR-DDS.       07/11/28
        long actno = parseLong(fileUpdclmr.getActno(), 0);
        clmrBus.setActno(actno);
        // 016800     MOVE  CLMR-MSG1      TO  DB-CLMR-MSG1 OF DB-CLMR-DDS.        07/11/28
        int msg1 = parseInt(fileUpdclmr.getMsg1(), 0);
        clmrBus.setMsg1(msg1);
        // 016900     MOVE  CLMR-MSG2      TO  DB-CLMR-MSG2 OF DB-CLMR-DDS.        07/11/28
        int msg2 = parseInt(fileUpdclmr.getMsg2(), 0);
        clmcBus.setMsg2(msg2);
        // 017000     MOVE  CLMR-CFEE1     TO  DB-CLMR-CFEE1 OF DB-CLMR-DDS.       07/11/28
        BigDecimal cfee1 = parseBigDecimal(fileUpdclmr.getCfee1(), BigDecimal.ZERO);
        clfeeBus.setCfee1(cfee1);
        // 017100     MOVE  CLMR-CFEE2     TO  DB-CLMR-CFEE2 OF DB-CLMR-DDS.       07/11/28
        BigDecimal cfee2 = parseBigDecimal(fileUpdclmr.getCfee2(), BigDecimal.ZERO);
        clfeeBus.setCfee2(cfee2);
        // 017200     MOVE  CLMR-PUTTIME   TO  DB-CLMR-PUTTIME OF DB-CLMR-DDS.     07/11/28
        int puttime = parseInt(fileUpdclmr.getPuttime(), 0);
        clmrBus.setPuttime(puttime);
        // 017300     MOVE  CLMR-CHKTYPE   TO  DB-CLMR-CHKTYPE OF DB-CLMR-DDS.     07/11/28
        clmrBus.setChktype(fileUpdclmr.getChktype());
        // 017400     MOVE  CLMR-CHKAMT    TO  DB-CLMR-CHKAMT OF DB-CLMR-DDS.      07/11/28
        int chkamt = parseInt(fileUpdclmr.getChkamt(), 0);
        clmrBus.setChkamt(chkamt);
        // 017500     MOVE  CLMR-UNIT      TO  DB-CLMR-UNIT OF DB-CLMR-DDS.        07/11/28
        BigDecimal unit = parseBigDecimal(fileUpdclmr.getUnit(), BigDecimal.ZERO);
        clmrBus.setUnit(unit);
        // 017600     MOVE  CLMR-AMT       TO  DB-CLMR-AMT  OF DB-CLMR-DDS.        07/11/28
        BigDecimal amt = parseBigDecimal(fileUpdclmr.getAmt(), BigDecimal.ZERO);
        clmrBus.setAmt(amt);
        // 017700     MOVE  CLMR-TOTAMT    TO  DB-CLMR-TOTAMT OF DB-CLMR-DDS.      07/11/28
        BigDecimal totamt = parseBigDecimal(fileUpdclmr.getTotamt(), BigDecimal.ZERO);
        cltotBus.setRcvamt(totamt);
        // 017800     MOVE  CLMR-TOTCNT    TO  DB-CLMR-TOTCNT OF DB-CLMR-DDS.      07/11/28
        int totcnt = parseInt(fileUpdclmr.getTotcnt(), 0);
        cltotBus.setTotcnt(totcnt);
        // 017900     MOVE  CLMR-ENTPNO    TO  DB-CLMR-ENTPNO OF DB-CLMR-DDS.      07/11/28
        cltmrBus.setEntpno(fileUpdclmr.getEntpno());
        // 018000     MOVE  CLMR-HENTPNO   TO  DB-CLMR-HENTPNO OF DB-CLMR-DDS.     07/11/28
        int hentpno = parseInt(fileUpdclmr.getHentpno(), 0);
        cltmrBus.setHentpno(hentpno);
        // 018100     MOVE  CLMR-CNAME     TO  DB-CLMR-CNAME OF DB-CLMR-DDS.       07/11/28
        clmrBus.setCname(fileUpdclmr.getCname());
        // 018200     MOVE  CLMR-SCNAME    TO  DB-CLMR-SCNAME OF DB-CLMR-DDS.      07/11/28
        cltmrBus.setScname(fileUpdclmr.getScname());
        // 018300     MOVE  CLMR-CDATA     TO  DB-CLMR-CDATA  OF DB-CLMR-DDS.      07/11/28
        int cdata = parseInt(fileUpdclmr.getCdata(), 0);
        cltmrBus.setCdata(cdata);
        // 018400     MOVE  CLMR-FKD       TO  DB-CLMR-FKD OF DB-CLMR-DDS.         07/11/28
        int fkd = parseInt(fileUpdclmr.getFkd(), 0);
        clfeeBus.setFkd(fkd);
        // 018500     MOVE  CLMR-MFEE      TO  DB-CLMR-MFEE OF DB-CLMR-DDS.        07/11/28
        BigDecimal mfee = parseBigDecimal(fileUpdclmr.getMfee(), BigDecimal.ZERO);
        clfeeBus.setMfee(mfee);
        // 018600     MOVE  CLMR-STOP      TO  DB-CLMR-STOP OF DB-CLMR-DDS.        07/11/28
        int stop = parseInt(fileUpdclmr.getStop(), 0);
        clmrBus.setStop(stop);
        // 018700     MOVE  CLMR-AFCBV     TO  DB-CLMR-AFCBV OF DB-CLMR-DDS.       07/11/28
        int afcbv = parseInt(fileUpdclmr.getAfcbv(), 0);
        clmrBus.setAfcbv(afcbv);
        // 018800     MOVE  CLMR-CYCK1     TO  DB-CLMR-CYCK1 OF DB-CLMR-DDS.       07/11/28
        int cyck1 = parseInt(fileUpdclmr.getCyck1(), 0);
        clmcBus.setCyck1(cyck1);
        // 018900     MOVE  CLMR-CYCNO1    TO  DB-CLMR-CYCNO1 OF DB-CLMR-DDS.      07/11/28
        int cycno1 = parseInt(fileUpdclmr.getCycno1(), 0);
        clmcBus.setCyck1(cycno1);
        // 019000     MOVE  CLMR-CYCK2     TO  DB-CLMR-CYCK2 OF DB-CLMR-DDS.       07/11/28
        int cyck2 = parseInt(fileUpdclmr.getCyck2(), 0);
        clmcBus.setCyck1(cyck2);
        // 019100     MOVE  CLMR-CYCNO2    TO  DB-CLMR-CYCNO2 OF DB-CLMR-DDS.      07/11/28
        int cycno2 = parseInt(fileUpdclmr.getCycno2(), 0);
        clmcBus.setCyck1(cycno2);
        // 019200     MOVE  CLMR-PUTNAME   TO  DB-CLMR-PUTNAME OF DB-CLMR-DDS.     07/11/28
        String filePutname = fileUpdclmr.getPutname();
        boolean clmcError = false;
        if (filePutname.length() == 8) {
            String putsend = filePutname.substring(0, 1);
            String putform = filePutname.substring(1, 2);
            String putname = filePutname.substring(2);
            cltmrBus.setPutname(putname);
            clmcBus.setPutsend(putsend);
            clmcBus.setPutform(putform);
            clmcBus.setPutname(putname);
        } else {
            ApLogHelper.error(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "dbin1500() error putname={}",
                    filePutname);
            clmcError = true;
        }
        // 019300     MOVE  CLMR-PUTTYPE   TO  DB-CLMR-PUTTYPE OF DB-CLMR-DDS.     07/11/28
        int puttype = parseInt(fileUpdclmr.getPuttype(), 0);
        clmcBus.setPuttype(puttype);
        // 019400     MOVE  CLMR-PUTADDR   TO  DB-CLMR-PUTADDR OF DB-CLMR-DDS.     07/11/28
        clmcBus.setPutaddr(fileUpdclmr.getPutaddr());
        // 019500     MOVE  CLMR-NETINFO   TO  DB-CLMR-NETINFO OF DB-CLMR-DDS.     07/11/28
        clmrBus.setNetinfo(fileUpdclmr.getNetinfo());
        // 019600     MOVE  F-FDATE        TO  DB-CLMR-APPDT OF DB-CLMR-DDS.       07/11/28
        int fdate = parseInt(wkPutFdate, 0);
        cltmrBus.setAppdt(fdate);
        // 019700     MOVE  F-FDATE        TO  DB-CLMR-UPDDT OF DB-CLMR-DDS.       07/11/28
        cltmrBus.setUpddt(fdate);
        // 019800     MOVE  CLMR-PUTDT     TO  DB-CLMR-PUTDT OF DB-CLMR-DDS.       07/11/28
        int putdt = parseInt(fileUpdclmr.getPutdt(), 0);
        clmcBus.setPutdt(putdt);
        // 019900     MOVE  FD-BHDATE-LBSDY TO DB-CLMR-LPUTDT OF DB-CLMR-DDS.      07/11/28
        cltmrBus.setLputdt(lbsdy);
        // 020000     MOVE  FD-BHDATE-LBSDY TO DB-CLMR-LLPUTDT OF DB-CLMR-DDS.     07/11/28
        cltmrBus.setLlputdt(lbsdy);
        // 020100     MOVE  FD-BHDATE-LBSDY TO DB-CLMR-ULPUTDT OF DB-CLMR-DDS.     07/11/28
        cltmrBus.setUlputdt(lbsdy);
        // 020200     MOVE  FD-BHDATE-LBSDY TO DB-CLMR-ULLPUTDT OF DB-CLMR-DDS.    07/11/28
        cltmrBus.setUllputdt(lbsdy);
        // 020250     MOVE  CLMR-CFEE3     TO  DB-CLMR-CFEE3 OF DB-CLMR-DDS.       07/11/28
        BigDecimal cfee3 = parseBigDecimal(fileUpdclmr.getCfee3(), BigDecimal.ZERO);
        clfeeBus.setCfee3(cfee3);
        // 020270     MOVE  CLMR-CFEE4     TO  DB-CLMR-CFEE4 OF DB-CLMR-DDS.       07/11/28
        BigDecimal cfee4 = parseBigDecimal(fileUpdclmr.getCfee4(), BigDecimal.ZERO);
        clfeeBus.setCfee4(cfee4);
        // 020280     MOVE  CLMR-CFEEEB    TO  DB-CLMR-CFEEEB OF DB-CLMR-DDS.      08/12/02
        BigDecimal cfeeeb = parseBigDecimal(fileUpdclmr.getCfeeeb(), BigDecimal.ZERO);
        clfeeBus.setCfeeeb(cfeeeb);
        // 020290     MOVE  CLMR-EBTYPE    TO  DB-CLMR-EBTYPE OF DB-CLMR-DDS.      08/12/02
        cltmrBus.setEbtype(fileUpdclmr.getEbtype());
        // 020295     MOVE  CLMR-LKCODE    TO  DB-CLMR-LKCODE OF DB-CLMR-DDS.      08/12/02
        clmrBus.setLkcode(fileUpdclmr.getLkcode());
        // 020297     MOVE  CLMR-PWTYPE    TO  DB-CLMR-PWTYPE OF DB-CLMR-DDS.      08/12/02
        cltmrBus.setPwtype(fileUpdclmr.getPwtype());
        // 020298     MOVE  CLMR-FEENAME   TO  DB-CLMR-FEENAME OF DB-CLMR-DDS.     11/11/22
        cltmrBus.setFeename(fileUpdclmr.getFeename());
        // 020300     BEGIN-TRANSACTION NO-AUDIT RESTART-DST.
        // 020400     STORE    DB-CLMR-DDS.
        // 020500     END-TRANSACTION NO-AUDIT RESTART-DST.
        try {
            clmrService.insert(clmrBus);
        } catch (Exception e) {
            ApLogHelper.error(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "clmrService insert error = {}",
                    e.getMessage());
        }
        try {
            cltmrService.insert(cltmrBus);
        } catch (Exception e) {
            ApLogHelper.error(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "cltmrService insert error = {}",
                    e.getMessage());
        }
        try {
            cltotService.insert(cltotBus);
        } catch (Exception e) {
            ApLogHelper.error(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "cltotService insert error = {}",
                    e.getMessage());
        }
        try {
            clfeeService.insert(clfeeBus);
        } catch (Exception e) {
            ApLogHelper.error(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "clfeeService insert error = {}",
                    e.getMessage());
        }
        try {
            clmcService.insert(clmcBus);
        } catch (Exception e) {
            ApLogHelper.error(
                    log,
                    false,
                    LogType.NORMAL.getCode(),
                    "clmcService insert error = {}",
                    e.getMessage());
        }
        // 020600 1500-DBIN-EXIT.
        // 020700     EXIT.
    }

    private int parseInt(String s, int i) {
        return parse.isNumeric(s) ? parse.string2Integer(s) : i;
    }

    private long parseLong(String s, long l) {
        return parse.isNumeric(s) ? parse.string2Long(s) : l;
    }

    private BigDecimal parseBigDecimal(String s, BigDecimal b) {
        return parse.isNumeric(s) ? parse.string2BigDecimal(s) : b;
    }

    private void chk2000() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "chk2000()");
        // 040900 2000-CHK-RTN.
        // 041000     MOVE       0                   TO      WK-RTNCD.
        wkRtncd = 0;
        // 041100     PERFORM    CODE-CHK-RTN        THRU    CODE-CHK-EXIT.
        codeChkRtn();
        // 041200     IF         WK-CODE-EXIST       =       0
        // 041300       GO TO    2000-CHK-EXIT.
        if (wkCodeExist == 0) {
            return;
        }
        // 041400     IF         CLMR-PBRNO    NOT =  DB-CLMR-PBRNO OF DB-CLMR-DDS 07/11/28
        // 041500       GO TO    2000-CHK-EXIT.
        int pbrno = parseInt(fileUpdclmr.getPbrno(), 0);
        if (pbrno != queryClmrBus.getPbrno()) {
            return;
        }
        // 041600*    IF         CLMR-ATMCODE        NOT =   SPACE                 03/12/11
        // 041700*      GO TO    2000-CHK-EXIT.                                    03/12/11
        if (notSpace(fileUpdclmr.getAtmcode())) {
            return;
        }
        // 041900     IF         CLMR-VRCODE         NOT =   SPACE
        // 042000       PERFORM  VRCODE-BAN-RTN      THRU    VRCODE-BAN-EXIT
        // 042100       PERFORM  VRCODE-CHK-RTN      THRU    VRCODE-CHK-EXIT.
        if (notSpace(fileUpdclmr.getVrcode())) {
            vrcodeBan();
            vrcodeChk();
        }
        // 042200     IF         CLMR-RIDDUP         NOT =   SPACE
        // 042300       PERFORM  RIDDUP-CHK-RTN      THRU    RIDDUP-CHK-EXIT.
        if (notSpace(fileUpdclmr.getRiddup())) {
            riddupChk();
        }
        // 042400     IF         CLMR-MSG1           NOT =   SPACE
        // 042500       PERFORM  MSG1-CHK-RTN        THRU    MSG1-CHK-EXIT.
        if (notSpace(fileUpdclmr.getMsg1())) {
            msg1Chk();
        }
        // 042600     IF         CLMR-MSG2           NOT =   SPACE
        // 042700       PERFORM  MSG2-CHK-RTN        THRU    MSG2-CHK-EXIT.
        if (notSpace(fileUpdclmr.getMsg2())) {
            msg2Chk();
        }
        // 042720     IF         CLMR-CFEE1          NOT =   SPACE                 05/07/14
        // 042730     OR         CLMR-CFEE4          NOT =   SPACE                 05/07/14
        // 042740       PERFORM  CFEE-UPCHK-RTN      THRU    CFEE-UPCHK-EXIT.      05/07/14
        if (notSpace(fileUpdclmr.getCfee1()) || notSpace(fileUpdclmr.getCfee4())) {
            cfeeUpchk();
        }
        // 042800**   IF         CLMR-CFEE2          NOT =   SPACE                 05/11/11
        // 042900**     PERFORM  CFEE2-CHK-RTN       THRU    CFEE2-CHK-EXIT.       05/11/11
        // 043000     IF         CLMR-PUTTIME        NOT =   SPACE
        // 043100       PERFORM  PUTTIME-CHK-RTN     THRU    PUTTIME-CHK-EXIT.
        if (notSpace(fileUpdclmr.getPuttime())) {
            puttimeChk();
        }
        // 043200     IF         CLMR-CHKTYPE        NOT =   SPACE
        // 043300       PERFORM  CHKTYPE-CHK-RTN     THRU    CHKTYPE-CHK-EXIT.
        // 043340                                                                  06/07/28
        if (notSpace(fileUpdclmr.getChktype())) {
            chktypeChk();
        }
        // 043400     IF         CLMR-CHKAMT         NOT =   SPACE
        // 043500       PERFORM  CHKAMT-CHK-RTN      THRU    CHKAMT-CHK-EXIT.
        if (notSpace(fileUpdclmr.getChkamt())) {
            chkamtChk();
        }
        // 043520     IF         CLMR-CNAME          NOT =   SPACE                 03/12/17
        // 043540       PERFORM  CNAME-CHK-RTN       THRU    CNAME-CHK-EXIT.       03/12/17
        if (notSpace(fileUpdclmr.getCname())) {
            cnameChk();
        }
        // 043560     IF         CLMR-SCNAME         NOT =   SPACE                 03/12/17
        // 043580       PERFORM  SCNAME-CHK-RTN      THRU    SCNAME-CHK-EXIT.      03/12/17
        // 043590                                                                  06/07/28
        if (notSpace(fileUpdclmr.getScname())) {
            scnameChk();
        }
        // 043600     IF         CLMR-CDATA          NOT =   SPACE
        // 043700       PERFORM  CDATA-CHK-RTN       THRU    CDATA-CHK-EXIT.
        if (notSpace(fileUpdclmr.getCdata())) {
            cdataChk();
        }
        // 043800     IF         CLMR-FKD            NOT =   SPACE
        // 043900       PERFORM  FKD-CHK-RTN         THRU    FKD-CHK-EXIT.
        if (notSpace(fileUpdclmr.getFkd())) {
            fkdChk();
        }
        // 044000     IF         CLMR-STOP           NOT =   SPACE
        // 044100       PERFORM  STOP-CHK-RTN        THRU    STOP-CHK-EXIT.
        if (notSpace(fileUpdclmr.getStop())) {
            stopChk();
        }
        // 044200     IF         CLMR-AFCBV          NOT =   SPACE
        // 044300       PERFORM  AFCBV-CHK-RTN       THRU    AFCBV-CHK-EXIT.
        if (notSpace(fileUpdclmr.getAfcbv())) {
            afcbvChk();
        }
        // 044400     IF         CLMR-CYCK1          NOT =   SPACE
        // 044500       PERFORM  CYCK1-CHK-RTN       THRU    CYCK1-CHK-EXIT.
        if (notSpace(fileUpdclmr.getCyck1())) {
            cyck1Chk();
        }
        // 044600     IF         CLMR-CYCK2          NOT =   SPACE
        // 044700       PERFORM  CYCK2-CHK-RTN       THRU    CYCK2-CHK-EXIT.
        if (notSpace(fileUpdclmr.getCyck2())) {
            cyck2Chk();
        }
        // 044800     IF         CLMR-PUTNAME        NOT =   SPACE
        // 044900       PERFORM  PUTNAME-CHK-RTN     THRU    PUTNAME-CHK-EXIT.

        // ref:
        // 068500 PUTNAME-CHK-RTN.
        // 068600    IF           CLMR-PUTNAME      =     SPACES
        // 068700        MOVE     06                TO      WK-RTNCD               09/07/14
        // 068800        GO TO    PUTNAME-CHK-EXIT.
        // 068900 PUTNAME-CHK-EXIT.
        // 069000    EXIT.
        if (!notSpace(fileUpdclmr.getPutname())) {
            wkRtncd = 6;
        }
        // 045000     IF         CLMR-PUTTYPE        NOT =   SPACE
        // 045100       PERFORM  PUTTYPE-CHK-RTN     THRU    PUTTYPE-CHK-EXIT.
        if (notSpace(fileUpdclmr.getPuttype())) {
            puttypeChk();
        }
        // 045120     IF    CLMR-CFEEEB NOT= SPACE  OR  CLMR-EBTYPE NOT= SPACE     08/12/02
        // 045140       OR  CLMR-LKCODE NOT= SPACE                                 08/12/02
        // 045160       PERFORM  EBILL-CHK-RTN       THRU    EBILL-CHK-EXIT.       08/12/02
        if (notSpace(fileUpdclmr.getCfeeeb())
                || notSpace(fileUpdclmr.getEbtype())
                || notSpace(fileUpdclmr.getLkcode())) {
            ebillChk();
        }
        // 045165     IF         CLMR-FEENAME        NOT =   SPACE                 11/11/22
        // 045170       PERFORM  FEENAME-CHK-RTN     THRU    FEENAME-CHK-EXIT.     11/11/22
        if (notSpace(fileUpdclmr.getFeename())) {
            feenameChk();
        }
        // 045200 2000-CHK-EXIT.
        // 045300     EXIT.
    }

    private void cfeeUpchk() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "cfeeUpchk()");
        // 051100*--MODIFY-- 必須同時輸入 CLMR-CFEE1 & CLMR-CFEE4                  05/07/14
        // 051105 CFEE-UPCHK-RTN.                                                  05/07/14
        // 051109    IF CLMR-CFEE1 NOT = SPACES  AND  CLMR-CFEE4 NOT = SPACES      05/07/14
        // 051110       IF CLMR-CFEE1 NOT = 0  AND  CLMR-CFEE4 NOT = 0             05/07/14
        // 051115          MOVE   06  TO    WK-RTNCD                               09/07/14
        // 051120       END-IF                                                     05/07/14
        // 051125    ELSE                                                          05/07/14
        // 051127       MOVE   06  TO    WK-RTNCD                                  09/07/14
        // 051147    END-IF.                                                       05/07/14
        if (notSpace(fileUpdclmr.getCfee1()) && notSpace(fileUpdclmr.getCfee4())) {
            if (parseInt(fileUpdclmr.getCfee1(), -1) != 0
                    && parseInt(fileUpdclmr.getCfee4(), -1) != 0) {
                wkRtncd = 6;
            }
        } else {
            wkRtncd = 6;
        }
        // 051152 CFEE-UPCHK-EXIT.                                                 05/07/14
        // 051154    EXIT.                                                         05/07/14
    }

    private void chkamtChk() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "chkamtChk()");
        // 057900 CHKAMT-CHK-RTN.
        // 058000     IF         CLMR-CHKAMT         NOT = 0
        // 058100                        AND         NOT = 1
        // 058200                        AND         NOT = 2
        // 058220                        AND         NOT = 3                       11/12/23
        // 058240                        AND         NOT = 4                       11/12/23
        // 058300       MOVE     06                  TO    WK-RTNCD                09/07/14
        // 058400       GO TO    CHKAMT-CHK-EXIT.
        String chkamt = fileUpdclmr.getChkamt();
        switch (chkamt) {
            case "0", "1", "2", "3", "4":
                break;
            default:
                wkRtncd = 6;
                break;
        }
        // 058500 CHKAMT-CHK-EXIT.
        // 058600     EXIT.
    }

    private void dbin2500() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "dbin2500()");
        // 020900 2500-DBIN-RTN.
        // 021000     LOCK DB-CLMR-IDX1   OF DB-CLMR-DDS                           07/11/28
        // 021050         AT DB-CLMR-CODE OF DB-CLMR-DDS = CLMR-CODE               07/11/28
        // 021100         ON EXCEPTION
        // 021200          GO TO 2500-DBIN-EXIT.
        String code = fileUpdclmr.getCode();
        ClmrBus holdClmr = clmrService.holdById(code);
        if (Objects.isNull(holdClmr)) {
            return;
        }
        CltmrBus holdCltmr = cltmrService.holdById(code);
        if (Objects.isNull(holdCltmr)) {
            return;
        }
        CltotId cltotId = new CltotId(code, 0);
        CltotBus holdCltot = cltotService.holdById(cltotId);
        if (Objects.isNull(holdCltot)) {
            return;
        }
        ClfeeId clfeeId = new ClfeeId(code, "00", 0);
        ClfeeBus holdClfee = clfeeService.holdById(clfeeId);
        if (Objects.isNull(holdClfee)) {
            return;
        }
        String putname = fileUpdclmr.getPutname().substring(2);
        ClmcBus holdClmc = clmcService.holdById(putname);
        if (Objects.isNull(holdClmc)) {
            return;
        }
        // 021300     IF         CLMR-ATMCODE        NOT =   SPACE
        // 021400       MOVE     CLMR-ATMCODE TO DB-CLMR-ATMCODE OF DB-CLMR-DDS.   07/11/28
        if (notSpace(fileUpdclmr.getAtmcode())) {
            holdCltmr.setAtmcode(parseInt(fileUpdclmr.getAtmcode(), 0));
        }
        // 021500     IF         CLMR-VRCODE         NOT =   SPACE
        // 021600       MOVE     CLMR-VRCODE  TO DB-CLMR-VRCODE OF DB-CLMR-DDS.    07/11/28
        if (notSpace(fileUpdclmr.getVrcode())) {
            holdClmr.setVrcode(parseInt(fileUpdclmr.getVrcode(), 0));
        }
        // 021700     IF         CLMR-RIDDUP         NOT =   SPACE
        // 021800       MOVE     CLMR-RIDDUP  TO DB-CLMR-RIDDUP OF DB-CLMR-DDS.    07/11/28
        if (notSpace(fileUpdclmr.getRiddup())) {
            holdClmr.setRiddup(parseInt(fileUpdclmr.getRiddup(), 0));
        }
        // 021900     IF         CLMR-ACTNO          NOT =   SPACE
        // 022000       MOVE     CLMR-ACTNO   TO DB-CLMR-ACTNO OF DB-CLMR-DDS.     07/11/28
        if (notSpace(fileUpdclmr.getActno())) {
            holdClmr.setActno(parseLong(fileUpdclmr.getActno(), 0));
        }
        // 022100     IF         CLMR-MSG1           NOT =   SPACE
        // 022200       MOVE     CLMR-MSG1    TO DB-CLMR-MSG1 OF DB-CLMR-DDS.      07/11/28
        if (notSpace(fileUpdclmr.getMsg1())) {
            holdClmr.setMsg1(parseInt(fileUpdclmr.getMsg1(), 0));
        }
        // 022300     IF         CLMR-MSG2           NOT =   SPACE
        // 022400       MOVE     CLMR-MSG2    TO DB-CLMR-MSG2 OF DB-CLMR-DDS.      07/11/28
        if (notSpace(fileUpdclmr.getMsg2())) {
            holdClmc.setMsg2(parseInt(fileUpdclmr.getMsg2(), 0));
        }
        // 022500     IF         CLMR-CFEE1          NOT =   SPACE
        // 022600       MOVE     CLMR-CFEE1   TO DB-CLMR-CFEE1 OF DB-CLMR-DDS.     07/11/28
        if (notSpace(fileUpdclmr.getCfee1())) {
            holdClfee.setCfee1(parseBigDecimal(fileUpdclmr.getCfee1(), BigDecimal.ZERO));
        }
        // 022700     IF         CLMR-CFEE2          NOT =   SPACE
        // 022800       MOVE     CLMR-CFEE2   TO DB-CLMR-CFEE2 OF DB-CLMR-DDS.     07/11/28
        if (notSpace(fileUpdclmr.getCfee2())) {
            holdClfee.setCfee2(parseBigDecimal(fileUpdclmr.getCfee2(), BigDecimal.ZERO));
        }
        // 022900     IF         CLMR-PUTTIME        NOT =   SPACE
        // 023000       MOVE     CLMR-PUTTIME TO DB-CLMR-PUTTIME OF DB-CLMR-DDS.   07/11/28
        if (notSpace(fileUpdclmr.getPuttime())) {
            holdClmr.setPuttime(parseInt(fileUpdclmr.getPuttime(), 0));
        }
        // 023100     IF         CLMR-CHKTYPE        NOT =   SPACE
        // 023200       MOVE     CLMR-CHKTYPE TO DB-CLMR-CHKTYPE OF DB-CLMR-DDS.   07/11/28
        if (notSpace(fileUpdclmr.getChktype())) {
            holdClmr.setChktype(fileUpdclmr.getChktype());
        }
        // 023300     IF         CLMR-CHKAMT         NOT =   SPACE
        // 023400       MOVE     CLMR-CHKAMT  TO DB-CLMR-CHKAMT OF DB-CLMR-DDS.    07/11/28
        if (notSpace(fileUpdclmr.getChkamt())) {
            holdClmr.setChkamt(parseInt(fileUpdclmr.getChkamt(), 0));
        }
        // 023500     IF         CLMR-UNIT           NOT =   SPACE
        // 023600       MOVE     CLMR-UNIT    TO DB-CLMR-UNIT OF DB-CLMR-DDS.      07/11/28
        if (notSpace(fileUpdclmr.getUnit())) {
            holdClmr.setUnit(parseBigDecimal(fileUpdclmr.getUnit(), BigDecimal.ZERO));
        }
        // 023700     IF         CLMR-AMT            NOT =   SPACE
        // 023800       MOVE     CLMR-AMT     TO DB-CLMR-AMT OF DB-CLMR-DDS.       07/11/28
        if (notSpace(fileUpdclmr.getAmt())) {
            holdClmr.setAmt(parseBigDecimal(fileUpdclmr.getAmt(), BigDecimal.ZERO));
        }
        // 023900     IF         CLMR-TOTAMT         NOT =   SPACE
        // 024000       MOVE     CLMR-TOTAMT  TO DB-CLMR-TOTAMT OF DB-CLMR-DDS.    07/11/28
        if (notSpace(fileUpdclmr.getTotamt())) {
            holdCltot.setRcvamt(parseBigDecimal(fileUpdclmr.getTotamt(), BigDecimal.ZERO));
        }
        // 024100     IF         CLMR-TOTCNT         NOT =   SPACE
        // 024200       MOVE     CLMR-TOTCNT  TO DB-CLMR-TOTCNT OF DB-CLMR-DDS.    07/11/28
        if (notSpace(fileUpdclmr.getTotcnt())) {
            holdCltot.setTotcnt(parseInt(fileUpdclmr.getTotcnt(), 0));
        }
        // 024300     IF         CLMR-ENTPNO         NOT =   SPACE
        // 024400       MOVE     CLMR-ENTPNO  TO DB-CLMR-ENTPNO OF DB-CLMR-DDS.    07/11/28
        if (notSpace(fileUpdclmr.getEntpno())) {
            holdCltmr.setEntpno(fileUpdclmr.getEntpno());
        }
        // 024500     IF         CLMR-ENTPNO         NOT =   SPACE
        // 024600       MOVE     CLMR-HENTPNO TO DB-CLMR-HENTPNO OF DB-CLMR-DDS.   07/11/28
        if (notSpace(fileUpdclmr.getEntpno())) {
            holdCltmr.setHentpno(parseInt(fileUpdclmr.getHentpno(), 0));
        }
        // 024700     IF         CLMR-CNAME          NOT =   SPACE
        // 024800       MOVE     CLMR-CNAME   TO DB-CLMR-CNAME OF DB-CLMR-DDS.     07/11/28
        if (notSpace(fileUpdclmr.getCname())) {
            holdClmr.setCname(fileUpdclmr.getCname());
        }
        // 024900     IF         CLMR-SCNAME         NOT =   SPACE
        // 025000       MOVE     CLMR-SCNAME  TO DB-CLMR-SCNAME OF DB-CLMR-DDS.    07/11/28
        if (notSpace(fileUpdclmr.getScname())) {
            holdCltmr.setScname(fileUpdclmr.getScname());
        }
        // 025100     IF         CLMR-CDATA          NOT =   SPACE
        // 025200       MOVE     CLMR-CDATA   TO DB-CLMR-CDATA OF DB-CLMR-DDS.     07/11/28
        if (notSpace(fileUpdclmr.getCdata())) {
            holdCltmr.setCdata(parseInt(fileUpdclmr.getCdata(), 0));
        }
        // 025300     IF         CLMR-FKD            NOT =   SPACE
        // 025400       MOVE     CLMR-FKD     TO DB-CLMR-FKD OF DB-CLMR-DDS.       07/11/28
        if (notSpace(fileUpdclmr.getFkd())) {
            holdClfee.setFkd(parseInt(fileUpdclmr.getFkd(), 0));
        }
        // 025500     IF         CLMR-MFEE           NOT =   SPACE
        // 025600       MOVE     CLMR-MFEE    TO DB-CLMR-MFEE OF DB-CLMR-DDS.      07/11/28
        if (notSpace(fileUpdclmr.getMfee())) {
            holdClfee.setMfee(parseBigDecimal(fileUpdclmr.getMfee(), BigDecimal.ZERO));
        }
        // 025700     IF         CLMR-STOP           NOT =   SPACE
        // 025800       MOVE     CLMR-STOP    TO DB-CLMR-STOP OF DB-CLMR-DDS.      07/11/28
        if (notSpace(fileUpdclmr.getStop())) {
            holdClmr.setStop(parseInt(fileUpdclmr.getStop(), 0));
        }
        // 025900     IF         CLMR-AFCBV          NOT =   SPACE
        // 026000       MOVE     CLMR-AFCBV   TO DB-CLMR-AFCBV OF DB-CLMR-DDS.     07/11/28
        if (notSpace(fileUpdclmr.getAfcbv())) {
            holdClmr.setAfcbv(parseInt(fileUpdclmr.getAfcbv(), 0));
        }
        // 026100     IF         CLMR-CYCK1          NOT =   SPACE
        // 026200       MOVE     CLMR-CYCK1   TO DB-CLMR-CYCK1 OF DB-CLMR-DDS.     07/11/28
        if (notSpace(fileUpdclmr.getCyck1())) {
            holdClmc.setCyck1(parseInt(fileUpdclmr.getCyck1(), 0));
        }
        // 026300     IF         CLMR-CYCNO1         NOT =   SPACE
        // 026400       MOVE     CLMR-CYCNO1  TO DB-CLMR-CYCNO1 OF DB-CLMR-DDS.    07/11/28
        if (notSpace(fileUpdclmr.getCycno1())) {
            holdClmc.setCycno1(parseInt(fileUpdclmr.getCycno1(), 0));
        }
        // 026500     IF         CLMR-CYCK2          NOT =   SPACE
        // 026600       MOVE     CLMR-CYCK2   TO DB-CLMR-CYCK2 OF DB-CLMR-DDS.     07/11/28
        if (notSpace(fileUpdclmr.getCyck2())) {
            holdClmc.setCyck2(parseInt(fileUpdclmr.getCyck2(), 0));
        }
        // 026700     IF         CLMR-CYCNO2         NOT =   SPACE
        // 026800       MOVE     CLMR-CYCNO2  TO DB-CLMR-CYCNO2 OF DB-CLMR-DDS.    07/11/28
        if (notSpace(fileUpdclmr.getCycno2())) {
            holdClmc.setCycno2(parseInt(fileUpdclmr.getCycno2(), 0));
        }
        // 026900     IF         CLMR-PUTNAME        NOT =   SPACE
        // 027000       MOVE     CLMR-PUTNAME TO DB-CLMR-PUTNAME OF DB-CLMR-DDS.   07/11/28
        if (notSpace(fileUpdclmr.getPutname())) {
            holdCltmr.setPutname(fileUpdclmr.getPutname());
        }
        // 027100     IF         CLMR-PUTTYPE        NOT =   SPACE
        // 027200       MOVE     CLMR-PUTTYPE TO DB-CLMR-PUTTYPE OF DB-CLMR-DDS.   07/11/28
        if (notSpace(fileUpdclmr.getPuttype())) {
            holdClmc.setPuttype(parseInt(fileUpdclmr.getPuttype(), 0));
        }
        // 027300     IF         CLMR-PUTADDR        NOT =   SPACE
        // 027400       MOVE     CLMR-PUTADDR TO DB-CLMR-PUTADDR OF DB-CLMR-DDS.   07/11/28
        if (notSpace(fileUpdclmr.getPutaddr())) {
            holdClmc.setPutaddr(fileUpdclmr.getPutaddr());
        }
        // 027500     IF         CLMR-NETINFO        NOT =   SPACE
        // 027600       MOVE     CLMR-NETINFO TO DB-CLMR-NETINFO OF DB-CLMR-DDS.   07/11/28
        if (notSpace(fileUpdclmr.getNetinfo())) {
            holdClmr.setNetinfo(fileUpdclmr.getNetinfo());
        }
        // 027620     IF         CLMR-MSG2           NOT =   SPACE                 02/01/08
        // 027640       MOVE     CLMR-MSG2    TO DB-CLMR-MSG2 OF DB-CLMR-DDS.      07/11/28
        if (notSpace(fileUpdclmr.getMsg2())) {
            holdClmc.setMsg2(parseInt(fileUpdclmr.getMsg2(), 0));
        }
        // 027660     IF         CLMR-CFEE3          NOT =   SPACE                 02/01/08
        // 027680       MOVE     CLMR-CFEE3   TO DB-CLMR-CFEE3 OF DB-CLMR-DDS.     07/11/28
        if (notSpace(fileUpdclmr.getCfee3())) {
            holdClfee.setCfee3(parseBigDecimal(fileUpdclmr.getCfee3(), BigDecimal.ZERO));
        }
        // 027685     IF         CLMR-CFEE4          NOT =   SPACE                 05/07/14
        // 027690       MOVE     CLMR-CFEE4   TO DB-CLMR-CFEE4 OF DB-CLMR-DDS.     07/11/28
        if (notSpace(fileUpdclmr.getCfee4())) {
            holdClfee.setCfee4(parseBigDecimal(fileUpdclmr.getCfee4(), BigDecimal.ZERO));
        }
        // 027700*
        // 027720     IF         CLMR-CFEEEB         NOT =   SPACE                 08/12/02
        // 027740       MOVE     CLMR-CFEEEB   TO DB-CLMR-CFEEEB OF DB-CLMR-DDS.   08/12/02
        if (notSpace(fileUpdclmr.getCfeeeb())) {
            holdClfee.setCfeeeb(parseBigDecimal(fileUpdclmr.getCfeeeb(), BigDecimal.ZERO));
        }
        // 027760     IF         CLMR-EBTYPE         NOT =   SPACE                 08/12/02
        // 027780       MOVE     CLMR-EBTYPE   TO DB-CLMR-EBTYPE OF DB-CLMR-DDS.   08/12/02
        if (notSpace(fileUpdclmr.getEbtype())) {
            holdCltmr.setEbtype(fileUpdclmr.getEbtype());
        }
        // 027785     IF         CLMR-LKCODE         NOT =   SPACE                 08/12/02
        // 027790       MOVE     CLMR-LKCODE   TO DB-CLMR-LKCODE OF DB-CLMR-DDS.   08/12/02
        if (notSpace(fileUpdclmr.getLkcode())) {
            holdClmr.setLkcode(fileUpdclmr.getLkcode());
        }
        // 027792     IF         CLMR-PWTYPE         NOT =   SPACE                 08/12/02
        // 027794       MOVE     CLMR-PWTYPE   TO DB-CLMR-PWTYPE OF DB-CLMR-DDS.   08/12/02
        if (notSpace(fileUpdclmr.getPwtype())) {
            holdCltmr.setPwtype(fileUpdclmr.getPwtype());
        }
        // 027800     MOVE       F-FDATE       TO DB-CLMR-UPDDT OF DB-CLMR-DDS.    07/11/28
        holdCltmr.setUpddt(parseInt(wkPutFdate, 0));
        // 027900*
        // 028000     IF         CLMR-PUTDT          NOT =   SPACE                 96/11/29
        // 028100       MOVE     CLMR-PUTDT    TO DB-CLMR-PUTDT OF DB-CLMR-DDS.    07/11/28
        if (notSpace(fileUpdclmr.getPutdt())) {
            holdClmc.setPutdt(parseInt(fileUpdclmr.getPutdt(), 0));
        }
        // 028200     IF         CLMR-NETINFO        NOT =   SPACE
        // 028300       MOVE     CLMR-NETINFO  TO DB-CLMR-TPUTDT OF DB-CLMR-DDS.   07/11/28
        if (notSpace(fileUpdclmr.getNetinfo())) {
            holdClmc.setTputdt(parseInt(fileUpdclmr.getNetinfo(), 0));
        }
        // 028400     MOVE       F-FDATE       TO DB-CLMR-UPDDT OF DB-CLMR-DDS.    07/11/28
        holdCltmr.setUpddt(parseInt(wkPutFdate, 0));
        // 028405     IF         CLMR-FEENAME        NOT =   SPACE                 11/11/22
        // 028410       MOVE     CLMR-FEENAME  TO DB-CLMR-FEENAME OF DB-CLMR-DDS.  11/11/22
        if (notSpace(fileUpdclmr.getFeename())) {
            holdCltmr.setFeename(fileUpdclmr.getFeename());
        }
        // 028500     BEGIN-TRANSACTION NO-AUDIT RESTART-DST.
        // 028600     STORE    DB-CLMR-DDS.
        // 028700     END-TRANSACTION NO-AUDIT RESTART-DST.
        clmrService.update(holdClmr);
        cltmrService.update(holdCltmr);
        cltotService.update(holdCltot);
        clfeeService.update(holdClfee);
        clmcService.update(holdClmc);
        // 028800 2500-DBIN-EXIT.
        // 028900     EXIT.
    }

    private void chk3000() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "chk3000()");
        // 045500 3000-CHK-RTN.
        // 045505     IF         F-BRNO              NOT =   "102"                 96/07/18
        // 045510       MOVE     99                  TO      WK-RTNCD              09/07/14
        // 045515       GO TO    3000-CHK-EXIT.                                    96/07/18
        if (!wkPutBrno.equals("102")) {
            wkRtncd = 99;
            return;
        }
        // 045520     PERFORM    CODE-CHK-RTN        THRU    CODE-CHK-EXIT.        96/07/18
        codeChkRtn();
        // 045525     IF         WK-CODE-EXIST       =       0                     96/07/18
        // 045530       MOVE     99                  TO      WK-RTNCD              09/07/14
        // 045535       GO TO    3000-CHK-EXIT.                                    96/07/18
        if (wkCodeExist == 0) {
            wkRtncd = 99;
            return;
        }
        // 045540     IF         DB-CLMR-STOP OF DB-CLMR-DDS  NOT =   2            07/11/28
        // 045545       MOVE     99                  TO      WK-RTNCD              09/07/14
        // 045550       GO TO    3000-CHK-EXIT.                                    96/07/18
        if (queryClmrBus.getStop() != 2) {
            wkRtncd = 99;
        }
        // 045600 3000-CHK-EXIT.
        // 045700     EXIT.
    }

    private void dbin3500(String wkFunction) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "dbin3500()");
        // 029100 3500-DBIN-RTN.
        // 029110     LOCK DB-CLMR-IDX1   OF DB-CLMR-DDS                           07/11/28
        // 029115         AT DB-CLMR-CODE OF DB-CLMR-DDS = CLMR-CODE               07/11/28
        // 029120         ON EXCEPTION                                             96/07/18
        // 029130          GO TO 3500-DBIN-EXIT.                                   96/07/18
        // 029132     IF  WK-FUNCTION   =     5                                    07/11/28
        // 029134         PERFORM    3600-DBIN-RTN   THRU  3600-DBIN-EXIT.         07/11/28
        // 029136                                                                  07/11/28
        // 029140     BEGIN-TRANSACTION NO-AUDIT RESTART-DST.                      96/07/18
        // 029150     DELETE  DB-CLMR-DDS.                                         96/07/18
        // 029160     END-TRANSACTION NO-AUDIT RESTART-DST.                        96/07/18
        // 029170     IF  WK-FUNCTION   =     5                                    07/11/28
        // 029180         PERFORM    3700-DBIN-RTN   THRU  3700-DBIN-EXIT.         07/11/28
        // 029190                                                                  07/11/28
        String code = fileUpdclmr.getCode();
        ClmrBus holdClmr = clmrService.holdById(code);
        if (wkFunction.equals("5")) {
            if (Objects.isNull(holdClmr)) {
                return;
            }
            holdClmr.setPbrno(parseInt(fileUpdclmr.getPbrno(), 0));
            clmrService.update(holdClmr);
        } else {
            if (!Objects.isNull(holdClmr)) {
                clmrService.delete(holdClmr);
            }
            CltmrBus holdCltmr = cltmrService.holdById(code);
            if (!Objects.isNull(holdCltmr)) {
                cltmrService.delete(holdCltmr);
            }
            CltotId cltotId = new CltotId(code, 0);
            CltotBus holdCltot = cltotService.holdById(cltotId);
            if (!Objects.isNull(holdCltot)) {
                cltotService.delete(holdCltot);
            }
            ClfeeId clfeeId = new ClfeeId(code, "00", 0);
            ClfeeBus holdClfee = clfeeService.holdById(clfeeId);
            if (!Objects.isNull(holdClfee)) {
                clfeeService.delete(holdClfee);
            }
            String putname = fileUpdclmr.getPutname().substring(2);
            ClmcBus holdClmc = clmcService.holdById(putname);
            if (!Objects.isNull(holdClmc)) {
                clmcService.delete(holdClmc);
            }
        }
        // 029200 3500-DBIN-EXIT.
        // 029300      EXIT.
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
        Map<String, String> responseTextMap = new HashMap<>(textMap);
        responseTextMap.put("PARAM1", formatUtil.pad9("" + wkRtncd, 2));
        // 通知FSAP-BATCH NCL批次的處理結果
        fsapBatchUtil.response(this.event, "0000", "", responseTextMap);
    }
}
