/* (C) 2024 */
package com.bot.ncl.adapter.event.app.lsnr;

import com.bot.fsap.model.grpc.common.periphery.ArrayMap;
import com.bot.ncl.adapter.event.app.evt.Cldtldwl;
import com.bot.ncl.dto.entities.CldtlbyCodeEntdyAfterBus;
import com.bot.ncl.dto.entities.ClmcBus;
import com.bot.ncl.dto.entities.ClmrBus;
import com.bot.ncl.dto.entities.CltmrBus;
import com.bot.ncl.jpa.svc.CldtlService;
import com.bot.ncl.jpa.svc.ClmcService;
import com.bot.ncl.jpa.svc.ClmrService;
import com.bot.ncl.jpa.svc.CltmrService;
import com.bot.ncl.util.fileVo.*;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.txcontrol.adapter.event.BatchListenerCase;
import com.bot.txcontrol.adapter.out.grpc.FsapSync;
import com.bot.txcontrol.buffer.TxBizDate;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.util.date.DateDto;
import com.bot.txcontrol.util.date.DateUtil;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.Vo2TextFormatter;
import java.io.File;
import java.math.BigDecimal;
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
@Component("CldtldwlLsnr")
@Scope("prototype")
public class CldtldwlLsnr extends BatchListenerCase<Cldtldwl> {

    @Autowired private Vo2TextFormatter vo2TextFormatter;

    @Autowired private TextFileUtil textFileUtil;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private static final String CHARSET = "UTF-8";

    private static final String PATH_SEPARATOR = File.separator;

    @Autowired private FsapSync fsapSync;

    @Autowired private DateUtil dateUtil;

    @Autowired private Parse parse;

    @Autowired private ClmrService clmrService;

    @Autowired private CltmrService cltmrService;

    @Autowired private ClmcService clmcService;

    @Autowired private CldtlService cldtlService;
    private static final String CONVF_DATA = "DATA";

    private Cldtldwl event;

    private String wkIncode;
    private String wkFmdate;
    private int wkIndate;
    private String wkInformat;

    private String wkFddate;

    private String wkCode;

    private TxBizDate fdClndr;

    private int wkBdate;
    private int wkEdate;

    private String wkName;
    private int wkType;
    private String wkPutfilename;
    private Map<String, String> textMap;
    private String wkPutfilef;
    private String wkPutfilen;
    private String wkPutfilem;

    private int wkFdatef;
    private int wkFdaten;

    private String wkPutdirn;
    private String wkPutdirf;
    private String wkPutdirm;

    private BigDecimal wkTotamt = BigDecimal.ZERO;
    private int wkTotcnt = 0;

    @Override
    public void onApplicationEvent(Cldtldwl event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CldtldwlLsnr");
        this.beforRun(event);
    }

    @Override
    @SneakyThrows
    protected void run(Cldtldwl event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "CldtldwlLsnr run()");

        init(event);

        // 009820* 設定代收類別                                                    09/03/23
        // 009840   PERFORM 9200-SETCODE-RTN THRU 9200-SETCODE-EXIT.               09/03/23
        setCode();

        // 009900* 設定時間                                                        08/07/04
        // 010000   IF FD-CLNDR-TBSDY NOT = WK-INDATE                              08/07/04
        // 010050      MOVE    WK-INDATE       TO   WK-FDDATE                      09/03/23
        // 010100      PERFORM 9500-SETDAY-RTN THRU 9500-SETDAY-EXIT.              08/07/04
        int tbsdy = event.getAggregateBuffer().getTxCom().getTbsdy();
        if (tbsdy != wkIndate) {
            wkFddate = parse.decimal2String(wkIndate, 7, 0);
            setDay();
        }
        // 010200   PERFORM 9600-INTERVAL-RTN THRU 9600-INTERVAL-EXIT.             08/07/04
        interval();
        // 010250   DISPLAY "WK-BDATE,WK-EDATE=" WK-BDATE WK-EDATE.                12/07/24
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkBdate={}", wkBdate);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkEdate={}", wkEdate);

        // 010300                                                                  08/07/04
        // 010400* 主檔資料設定資料名稱及資料格式                                  08/07/04
        // 010500   PERFORM 9000-DBCLMR-RTN THRU 9000-DBCLMR-EXIT.                 08/07/04
        dbclmr();
        // 010600
        // 010700* 產生明細檔案 N:160(PUTFN) F:120(PUTF)                           08/07/04
        // 010800   IF WK-INFORMAT = "N"                                           08/07/04
        // 010900      MOVE WK-PUTFILENAME     TO WK-PUTFILEN                      08/07/04
        // 011000      MOVE  FD-CLNDR-TBSDY    TO WK-FDATEN                        08/07/04
        // 011020      IF WK-FMDATE = "C" OR "T"                                   09/03/23
        // 011040         MOVE 00              TO WK-FDATEN(5:2)                   08/07/26
        // 011060      END-IF                                                      08/07/26
        // 011070      IF WK-FMDATE = "D"                                          12/07/24
        // 011080         MOVE WK-FDDATE       TO WK-FDATEN                        12/07/24
        // 011090      END-IF                                                      12/07/24
        // 011100      CHANGE  ATTRIBUTE FILENAME OF FD-PUTFN TO WK-PUTDIRN        08/07/04
        // 011200      OPEN OUTPUT  FD-PUTFN                                       08/07/04
        // 011900      PERFORM 0500-DECODE-RTN      THRU 0500-DECODE-EXIT          09/03/23
        // 011950      CLOSE FD-PUTFN WITH SAVE.                                   09/03/23
        if (wkInformat.equals("N")) {
            wkPutfilen = wkPutfilename;
            wkFdaten = fdClndr.getTbsdy();
            if (wkFmdate.equals("C") || wkFmdate.equals("T")) {
                int wkFdatenYyyy = wkFdaten / 10000;
                int wkFdatenDd = wkFdaten % 100;
                wkFdaten = wkFdatenYyyy * 10000 + wkFdatenDd;
            }
            if (wkFmdate.equals("D")) {
                wkFdaten = parse.string2Integer(wkFddate);
            }
            // ref:
            // 005400 01 WK-PUTDIRN.                                                   08/07/04
            // 005500    03 FILLER                PIC X(16)                            08/07/04
            // 005600                           VALUE "DATA/CL/BH/CONN/".              08/07/04
            // 005700    03 WK-FDATEN             PIC 9(06).                           08/07/04
            // 005800    03 FILLER                PIC X(01)                            08/07/04
            // 005900                           VALUE "/".                             08/07/04
            // 006000    03 WK-PUTFILEN           PIC X(10).                           08/07/04
            // 006100    03 FILLER                PIC X(01)                            08/07/04
            // 006200                           VALUE ".".                             08/07/04
            wkPutdirn =
                    fileDir
                            + CONVF_DATA
                            + PATH_SEPARATOR
                            + tbsdy
                            + PATH_SEPARATOR
                            + "CONN"
                            + PATH_SEPARATOR
                            + wkFdaten
                            + PATH_SEPARATOR
                            + wkPutfilen;
            if (textFileUtil.exists(wkPutdirn)) {
                textFileUtil.deleteFile(wkPutdirn);
            }
            decode();
        }

        // 012400                                                                  08/07/04
        // 012500   IF WK-INFORMAT = "F"                                           08/07/04
        // 012600      MOVE WK-PUTFILENAME     TO WK-PUTFILEF                      08/07/04
        // 012700      MOVE  FD-CLNDR-TBSDY    TO WK-FDATEF                        08/07/04
        // 012720      IF WK-FMDATE = "C"                                          08/07/26
        // 012740         MOVE 00              TO WK-FDATEF(5:2)                   08/07/26
        // 012760      END-IF                                                      08/07/26
        // 012800      CHANGE  ATTRIBUTE FILENAME OF FD-PUTF TO WK-PUTDIRF         08/07/04
        // 012900      OPEN OUTPUT  FD-PUTF                                        08/07/04
        // 013600      PERFORM 0500-DECODE-RTN      THRU 0500-DECODE-EXIT          09/03/23
        // 013650      CLOSE FD-PUTF WITH SAVE.                                    09/03/23
        // 013670                                                                  10/03/02
        if (wkInformat.equals("F")) {
            wkPutfilef = wkPutfilename;
            wkFdatef = fdClndr.getTbsdy();
            if (wkFmdate.equals("C")) {
                int wkFdatenYyyy = wkFdaten / 10000;
                int wkFdatenDd = wkFdaten % 100;
                wkFdaten = wkFdatenYyyy * 10000 + wkFdatenDd;
            }
            // ref:
            // 004400 01 WK-PUTDIRF.                                                   08/07/04
            // 004500    03 FILLER                PIC X(16)                            08/07/04
            // 004600                           VALUE "DATA/CL/BH/CONF/".              08/07/04
            // 004700    03 WK-FDATEF             PIC 9(06).                           08/07/04
            // 004800    03 FILLER                PIC X(01)                            08/07/04
            // 004900                           VALUE "/".                             08/07/04
            // 005000    03 WK-PUTFILEF           PIC X(10).                           08/07/04
            // 005100    03 FILLER                PIC X(01)                            08/07/04
            // 005200                           VALUE ".".                             08/07/04
            wkPutdirf =
                    fileDir
                            + CONVF_DATA
                            + PATH_SEPARATOR
                            + tbsdy
                            + PATH_SEPARATOR
                            + "CONF"
                            + PATH_SEPARATOR
                            + wkFdatef
                            + PATH_SEPARATOR
                            + wkPutfilef;
            if (textFileUtil.exists(wkPutdirf)) {
                textFileUtil.deleteFile(wkPutdirf);
            }
            decode();
        }
        // 013690   IF WK-INFORMAT = "M"                                           10/03/02
        // 013710      MOVE  WK-INCODE         TO WK-PUTFILEM                      10/03/02
        // 013790      CHANGE  ATTRIBUTE FILENAME OF FD-KPUTH TO WK-PUTDIRM        10/03/02
        // 013810      OPEN OUTPUT  FD-KPUTH                                       10/03/02
        // 013830      PERFORM 0500-DECODE-RTN      THRU 0500-DECODE-EXIT          10/03/02
        // 013850      CLOSE FD-KPUTH WITH SAVE.                                   10/03/02
        if (wkInformat.equals("M")) {
            wkPutfilem = wkIncode;
            // ref:
            // 006220 01 WK-PUTDIRM.                                                   10/03/02
            // 006230    03 FILLER                PIC X(17)                            10/03/02
            // 006240                           VALUE "DATA/CL/BH/ANALY/".             10/03/02
            // 006250    03 WK-PUTFILEM           PIC X(06).                           10/03/02
            // 006260    03 FILLER                PIC X(01)                            10/03/02
            // 006270                           VALUE ".".                             10/03/02
            wkPutdirm =
                    fileDir
                            + CONVF_DATA
                            + PATH_SEPARATOR
                            + tbsdy
                            + PATH_SEPARATOR
                            + "ANALY"
                            + PATH_SEPARATOR
                            + wkPutfilem;
            if (textFileUtil.exists(wkPutdirm)) {
                textFileUtil.deleteFile(wkPutdirm);
            }
            decode();
        }
        // 014100                                                                  08/07/04
        // 014200 0000-END-RTN.                                                    08/07/04
        // 014300   CLOSE FD-CLNDR.                                                08/07/04
        // 014400   CLOSE BOTSRDB.                                                 08/07/04
        // 014500   STOP RUN.                                                      08/07/04
    }

    private void init(Cldtldwl event) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "init()");
        // 008800 PROCEDURE  DIVISION USING WK-INCODE,WK-FMDATE,                   08/07/04
        // 008900                           WK-INDATE,WK-INFORMAT.                 08/07/04
        // 取得輸入參數
        Map<String, ArrayMap> arrayMap =
                this.event.getPeripheryRequest().getPayload().getPyheader().getArrayAttrMap();

        textMap = arrayMap.get("textMap").getMapAttrMap();

        wkIncode = textMap.get("WK_INCODE"); // TODO: 待確認BATCH參數名稱
        wkFmdate = textMap.get("WK_FMDATE"); // TODO: 待確認BATCH參數名稱
        wkIndate =
                parse.isNumeric(textMap.get("WK_INDATE"))
                        ? parse.string2Integer(textMap.get("WK_INDATE"))
                        : 0; // TODO: 待確認BATCH參數名稱
        wkInformat = textMap.get("WK_INFORMAT"); // TODO: 待確認BATCH參數名稱
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkIncode={}", wkIncode);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkFmdate={}", wkFmdate);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkIndate={}", wkIndate);
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkInformat={}", wkInformat);

        this.event = event;

        wkTotamt = BigDecimal.ZERO;
        wkTotcnt = 0;
    }

    private void setCode() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "setCode()");
        // 027840 9200-SETCODE-RTN.                                                09/03/23
        // 027890      MOVE   WK-INCODE       TO    WK-CODE.                       10/03/02
        // 027900      IF  WK-INCODE  =  "TPCSJA"                                  09/03/23
        // 027920          MOVE   "350003"    TO    WK-CODE                        09/03/23
        // 027925      END-IF.                                                     10/03/02
        // 027930      IF  WK-INCODE  =  "CL060M"                                  10/03/02
        // 027932          MOVE   "366AE9"    TO    WK-CODE                        10/03/02
        // 027934      END-IF.                                                     10/03/02
        // 027940 9200-SETCODE-EXIT.                                               09/03/23
        // 027960    EXIT.                                                         09/03/23
        switch (wkIncode) {
            case "TPCSJA":
                wkCode = "350003";
                break;
            case "CL060M":
                wkCode = "366AE9";
                break;
            default:
                wkCode = wkIncode;
                break;
        }
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkCode={}", wkCode);
    }

    private void setDay() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "setDay()");
        // 028000 9500-SETDAY-RTN.                                                 08/07/04
        // 028100      MOVE 1 TO WK-CLNDR-KEY.                                     08/07/04
        // 028200 9500-LOOP-S.                                                     08/07/04
        // 028300      READ FD-CLNDR INVALID KEY DISPLAY "INVALID KEY"             08/07/04
        // 028400           ,WK-CLNDR-STUS                                         08/07/04
        // 028500           CHANGE ATTRIBUTE TASKVALUE OF MYSELF TO  -1            08/07/04
        // 028600           GO TO  0000-END-RTN.                                   08/07/04
        // 028700      IF FD-CLNDR-TBSDY NOT = WK-FDDATE                           09/03/23
        // 028800         ADD 1 TO WK-CLNDR-KEY                                    08/07/04
        // 028900         GO TO 9500-LOOP-S.                                       08/07/04
        // 029000 9500-LOOP-E.                                                     08/07/04
        // 029100 9500-SETDAY-EXIT.                                                08/07/04
        // 029200      EXIT.                                                       08/07/04
        List<TxBizDate> txBizDates =
                fsapSync.sy202ForAp(event.getPeripheryRequest(), wkFddate, wkFddate);
        if (!Objects.isNull(txBizDates) && !txBizDates.isEmpty()) {
            fdClndr = txBizDates.get(0);
        }
    }

    private void interval() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "interval()");
        // 029300 9600-INTERVAL-RTN.                                               08/07/04
        // 029400      MOVE  FD-CLNDR-TBSDY    TO WK-BDATE.                        08/07/04
        // 029500      MOVE  FD-CLNDR-NBSDY    TO WK-EDATE.                        08/07/04
        wkBdate = fdClndr.getTbsdy();
        wkEdate = fdClndr.getNbsdy();
        // 029520***   DISPLAY "WK-BDATE,WK-EDATE=" WK-BDATE WK-EDATE.             12/03/31
        // 029540                                                                  12/03/31
        // 029600      IF WK-FMDATE = "A"                                          08/07/04
        // 029700* 取得前三天日曆日                                                08/07/04
        // 029800         MOVE  FD-CLNDR-TBSDY  TO WK-SETDATE                      08/07/04
        // 029900         SUBTRACT  3         FROM WK-SETDATE                      08/07/04
        // 030000         MOVE  WK-SETDATE      TO WK-CHKDATE                      08/07/04
        // 030100         PERFORM  9900-CHKDATE-RTN   THRU  9900-CHKDATE-EXIT      08/07/04
        // 030200         MOVE  WK-CHKDATE      TO WK-SETDATE                      08/07/04
        // 030300         MOVE  WK-SETDATE      TO WK-EDATE,WK-BDATE               08/07/04
        // 030400         IF  FD-CLNDR-LDYCNT   =  2                               08/07/04
        // 030500             SUBTRACT 1          FROM WK-BDATE                    08/07/04
        // 030600         END-IF                                                   08/07/04
        // 030700         IF  FD-CLNDR-LDYCNT  >=  3                               08/07/04
        // 030800             MOVE  FD-CLNDR-LBSDY  TO WK-BDATE                    08/07/04
        // 030900             SUBTRACT 2          FROM WK-BDATE                    08/07/04
        // 031000         END-IF                                                   08/07/04
        // 031100                                                                  08/07/04
        // 031200         MOVE  WK-BDATE    TO WK-CHKDATE                          08/07/04
        // 031300         PERFORM  9900-CHKDATE-RTN   THRU  9900-CHKDATE-EXIT      08/07/04
        // 031400         MOVE  WK-CHKDATE  TO WK-BDATE                            08/07/04
        // 031500      END-IF.                                                     08/07/04
        if (wkFmdate.equals("A")) {
            // 取得前三天日曆日
            DateDto dateDto = new DateDto();
            dateDto.init();
            dateDto.setDateS(fdClndr.getTbsdy());
            dateDto.setDays(-3);
            dateUtil.getCalenderDay(dateDto);
            int wkSetDate = dateDto.getDateE2Integer(false);
            wkSetDate = chkdate(wkSetDate);
            ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wkSetDate={}", wkSetDate);
            wkEdate = wkSetDate;
            wkBdate = wkSetDate;
            if (fdClndr.getLdycnt() == 2) {
                dateDto.init();
                dateDto.setDateS(wkBdate);
                dateDto.setDays(-1);
                dateUtil.getCalenderDay(dateDto);
                wkBdate = dateDto.getDateE2Integer(false);
            }
            if (fdClndr.getLdycnt() >= 2) {
                dateDto.init();
                dateDto.setDateS(wkBdate);
                dateDto.setDays(-2);
                dateUtil.getCalenderDay(dateDto);
                wkBdate = dateDto.getDateE2Integer(false);
            }
            wkBdate = chkdate(wkBdate);
        }
        // 031600      IF WK-FMDATE = "B"                                          08/07/04
        // 031700* 不含下一營業日                                                  08/07/04
        // 031800         SUBTRACT 1            FROM WK-EDATE                      08/07/04
        // 031900         MOVE  WK-EDATE          TO WK-CHKDATE                    08/07/04
        // 032000         PERFORM  9900-CHKDATE-RTN   THRU  9900-CHKDATE-EXIT      08/07/04
        // 032100         MOVE  WK-CHKDATE        TO WK-EDATE                      08/07/04
        // 032200      END-IF.                                                     08/07/04
        if (wkFmdate.equals("B")) {
            DateDto dateDto = new DateDto();
            dateDto.init();
            dateDto.setDateS(wkEdate);
            dateDto.setDays(-1);
            dateUtil.getCalenderDay(dateDto);
            wkEdate = dateDto.getDateE2Integer(false);
            wkEdate = chkdate(wkEdate);
        }
        // 032210* 上月底日到本月月底日                                            08/07/26
        // 032220      IF WK-FMDATE = "C"                                          08/07/26
        // 032230         MOVE  FD-CLNDR-LMNDY    TO WK-BDATE                      08/07/26
        // 032240         MOVE  FD-CLNDR-TMNDY    TO WK-EDATE                      08/07/26
        // 032241      END-IF.                                                     12/07/24
        if (wkFmdate.equals("C")) {
            wkBdate = fdClndr.getLmndy();
            wkEdate = fdClndr.getTmndy();
        }
        // 032242*  前一系統日                                                     12/07/24
        // 032243      IF WK-FMDATE = "D"                                          12/07/24
        // 032244         MOVE  WK-FDDATE         TO WK-EDATE                      12/07/24
        // 032245         MOVE  FUNCTION INTEGER-OF-DATE(WK-EDATE + 19110000)      12/07/24
        // 032246                                 TO WK-TEMP                       12/07/24
        // 032247         MOVE  FUNCTION DATE-OF-INTEGER(WK-TEMP - 1)              12/07/24
        // 032248                                 TO WK-BDATE                      12/07/24
        // 032249         COMPUTE        WK-BDATE =  WK-BDATE - 19110000           12/07/24
        // 032250      END-IF.                                                     08/07/26
        if (wkFmdate.equals("D")) {
            wkEdate = parse.isNumeric(wkFddate) ? parse.string2Integer(wkFddate) : 0;
            DateDto dateDto = new DateDto();
            dateDto.init();
            dateDto.setDateS(wkEdate);
            dateDto.setDays(-1);
            dateUtil.getCalenderDay(dateDto);
            wkBdate = dateDto.getDateE2Integer(false);
        }
        // 032251* 本月第一日到本月最後一日                                        10/03/02
        // 032252      IF WK-FMDATE = "M"                                          10/03/02
        // 032253         MOVE  FD-CLNDR-TMNDY    TO WK-BDATE                      10/03/02
        // 032254         MOVE  01                TO WK-BDATE(7:2)                 10/03/02
        // 032255         MOVE  FD-CLNDR-TMNDY    TO WK-EDATE                      10/03/02
        // 032256      END-IF.                                                     10/03/02
        if (wkFmdate.equals("M")) {
            wkBdate = fdClndr.getTmndy();
            wkBdate = wkBdate / 100 * 100 + 1;
            wkEdate = fdClndr.getTmndy();
        }
        // 032260* 上月最後五個營業日到本月月底營業日                              09/03/23
        // 032270      IF WK-FMDATE = "T"                                          09/03/23
        // 032280         MOVE  FD-CLNDR-LMNDY    TO WK-FDDATE                     09/03/23
        // 032282         PERFORM 9500-SETDAY-RTN THRU 9500-SETDAY-EXIT            09/03/23
        // 032284         MOVE  FD-CLNDR-L5BSDY   TO WK-BDATE                      09/03/23
        // 032290                                                                  09/03/23
        // 032292         MOVE  WK-INDATE         TO WK-FDDATE                     09/03/23
        // 032294         PERFORM 9500-SETDAY-RTN THRU 9500-SETDAY-EXIT            09/03/23
        // 032296         MOVE  FD-CLNDR-FNBSDY   TO WK-EDATE                      09/03/23
        // 032298      END-IF.                                                     09/03/23
        if (wkFmdate.equals("T")) {
            wkFddate = parse.decimal2String(fdClndr.getLmndy(), 7, 0);
            setDay();
            wkBdate = fdClndr.getL5bsdy();
            wkFddate = parse.decimal2String(wkIndate, 7, 0);
            setDay();
            wkEdate = fdClndr.getFnbsdy();
        }
        // 032300 9600-INTERVAL-EXIT.                                              08/07/04
        // 032400      EXIT.                                                       08/07/04
    }

    private int chkdate(int wkChkdate) {
        // 032500 9900-CHKDATE-RTN.                                                08/07/04
        // 032600* 日期用減的後檢查                                                08/07/04

        // ref:
        // 007000 01 WK-CHKDATE.                                                   08/07/04
        // 007100    05 WK-CHKDATE-YYYY       PIC 9(04).                           08/07/04
        // 007200    05 WK-CHKDATE-MM         PIC 9(02).                           08/07/04
        // 007300    05 WK-CHKDATE-DD         PIC 9(02).                           08/07/04
        int wkChkdateDd = wkChkdate % 100;
        int wkChkdateMm = wkChkdate / 100 % 100;
        int wkChkdateYyyy = wkChkdate / 10000;

        int lmndd = fdClndr.getLmndy() % 100;
        int tbsmm = fdClndr.getTbsdy() / 100 % 100;

        int fnbsdd = fdClndr.getFnbsdy() % 100;

        int tmndd = fdClndr.getTmndy() % 100;

        int wkCountdate;

        // 032700      IF  WK-CHKDATE-DD  >  FD-CLNDR-LMNDD   AND                  08/07/04
        // 032800          WK-CHKDATE-MM  <  FD-CLNDR-TBSMM                        08/07/04
        // 032900          SUBTRACT WK-CHKDATE-DD  FROM  100  GIVING WK-COUNTDATE  08/07/04
        // 033000          MOVE  FD-CLNDR-LMNDD      TO  WK-CHKDATE-DD             08/07/04
        // 033100          SUBTRACT WK-COUNTDATE   FROM  WK-CHKDATE-DD.            08/07/04
        if (wkChkdateDd > lmndd && wkChkdateMm < tbsmm) {
            wkCountdate = 100 - wkChkdateDd;
            wkChkdateDd = lmndd;
            wkChkdateDd -= wkCountdate;
        }

        // 033200      IF  WK-CHKDATE-DD  >  FD-CLNDR-FNBSDD  AND                  08/07/04
        // 033300          WK-CHKDATE-MM  =  FD-CLNDR-TBSMM                        08/07/04
        // 033400          SUBTRACT WK-CHKDATE-DD  FROM  100  GIVING WK-COUNTDATE  08/07/04
        // 033500          MOVE  FD-CLNDR-TMNDD      TO  WK-CHKDATE-DD             08/07/04
        // 033600          SUBTRACT WK-COUNTDATE   FROM  WK-CHKDATE-DD.            08/07/04
        // 033700                                                                  08/07/04
        if (wkChkdateDd > fnbsdd && wkChkdateMm == tbsmm) {
            wkCountdate = 100 - wkChkdateDd;
            wkChkdateDd = tmndd;
            wkChkdateDd -= wkCountdate;
        }

        // 033800      IF  WK-CHKDATE-DD  =  0 AND WK-CHKDATE-MM = FD-CLNDR-TBSMM  08/07/04
        // 033900          MOVE  FD-CLNDR-LMNDD      TO  WK-CHKDATE-DD             08/07/04
        // 034000          SUBTRACT 1              FROM  WK-CHKDATE-MM.            08/07/04
        if (wkChkdateDd == 0 && wkChkdateMm == tbsmm) {
            wkChkdateDd = lmndd;
            wkChkdateMm -= 1;
        }

        // 034100      IF  WK-CHKDATE-DD  =  0 AND WK-CHKDATE-MM > FD-CLNDR-TBSMM  08/07/04
        // 034200          MOVE  FD-CLNDR-TMNDD      TO  WK-CHKDATE-DD             08/07/04
        // 034300          SUBTRACT 1              FROM  WK-CHKDATE-MM.            08/07/04
        // 034400                                                                  08/07/04
        if (wkChkdateDd == 0 && wkChkdateMm > tbsmm) {
            wkChkdateDd = tmndd;
            wkChkdateMm -= 1;
        }

        // 034500      IF  WK-CHKDATE-MM  =  0                                     08/07/04
        // 034600          MOVE  12                  TO  WK-CHKDATE-MM             08/07/04
        // 034700          SUBTRACT 1              FROM  WK-CHKDATE-YYYY.          08/07/04
        if (wkChkdateMm == 0) {
            wkChkdateMm = 12;
            wkChkdateYyyy -= 1;
        }

        // 034800                                                                  08/07/04
        // 034900 9900-CHKDATE-EXIT.                                               08/07/04
        // 035000      EXIT.                                                       08/07/04
        return wkChkdateYyyy * 10000 + wkChkdateMm * 100 + wkChkdateDd;
    }

    private void dbclmr() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "dbclmr()");
        // 026400 9000-DBCLMR-RTN.                                                 08/07/04
        // 026500                                                                  08/07/04
        // 026600     FIND  DB-CLMR-IDX1  AT  DB-CLMR-CODE=WK-CODE                 08/07/04
        // 026700         ON  EXCEPTION                                            08/07/04
        // 026800             IF DMSTATUS(NOTFOUND)                                08/07/04
        // 026900                DISPLAY "FINDCLMR NOT FOUND" WK-CODE              08/07/04
        // 027000                CALL SYSTEM DMTERMINATE                           08/07/04
        // 027100             ELSE                                                 08/07/04
        // 027200                CALL SYSTEM DMTERMINATE.                          08/07/04
        // 027300                                                                  08/07/04
        ClmrBus clmrBus = clmrService.findById(wkCode);
        if (Objects.isNull(clmrBus)) {
            throw new LogicException("E711", "");
        }

        CltmrBus cltmrBus = cltmrService.findById(wkCode);
        if (Objects.isNull(cltmrBus)) {
            throw new LogicException("E711", "");
        }
        String putname = cltmrBus.getPutname();

        ClmcBus clmcBus = clmcService.findById(putname);
        if (Objects.isNull(clmcBus)) {
            throw new LogicException("E711", "");
        }

        // 027400     MOVE DB-CLMR-PUTNAME    TO WK-NAME.                          08/07/04
        // 027500     MOVE DB-CLMR-PUTTYPE    TO WK-TYPE.                          08/07/04
        wkName = clmcBus.getPutsend() + clmcBus.getPutform() + clmcBus.getPutname();
        wkType = clmcBus.getPuttype();

        // ref:
        // 007500 01 WK-PUTFILENAME.                                               08/07/04
        // 007600    05 WK-TYPE               PIC 9(02).                           08/07/04
        // 007700    05 WK-NAME               PIC X(08).                           08/07/04
        wkPutfilename = parse.decimal2String(wkType, 2, 0) + wkName;

        // 027600                                                                  08/07/04
        // 027700 9000-DBCLMR-EXIT.                                                08/07/04
        // 027800    EXIT.                                                         08/07/04
    }

    private void decode() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "decode()");
        // 014710 0500-DECODE-RTN.                                                 09/03/23
        // 014720    IF  WK-INCODE = "TPCSJA"                                      09/03/23
        // 014722      MOVE "350003"              TO   WK-CODE                     09/03/23
        // 014724      PERFORM 1000-GEN-CLDTL-RTN THRU 1000-GEN-CLDTL-EXIT         09/03/23
        // 014726      MOVE "510071"              TO   WK-CODE                     09/03/23
        // 014728      PERFORM 1000-GEN-CLDTL-RTN THRU 1000-GEN-CLDTL-EXIT         09/03/23
        // 014730      MOVE "530021"              TO   WK-CODE                     09/03/23
        // 014732      PERFORM 1000-GEN-CLDTL-RTN THRU 1000-GEN-CLDTL-EXIT         09/03/23
        if (wkIncode.equals("TPCSJA")) {
            wkCode = "350003";
            genCldtl();
            wkCode = "510071";
            genCldtl();
            wkCode = "530021";
            genCldtl();
        } else if (wkIncode.equals("CL060M")) {
            // 014734    ELSE IF  WK-INCODE = "CL060M"                                 10/03/02
            // 014736      MOVE "366AE9"              TO   WK-CODE                     10/03/02
            // 014737      PERFORM 1000-GEN-CLDTL-RTN THRU 1000-GEN-CLDTL-EXIT         10/03/02
            // 014738      MOVE "366AG9"              TO   WK-CODE                     10/03/02
            // 014740      PERFORM 1000-GEN-CLDTL-RTN THRU 1000-GEN-CLDTL-EXIT         10/03/02
            wkCode = "366AE9";
            genCldtl();
            wkCode = "366AG9";
            genCldtl();
        } else {
            // 014745    ELSE                                                          10/03/02
            // 014748      PERFORM 1000-GEN-CLDTL-RTN THRU 1000-GEN-CLDTL-EXIT.        10/03/02
            genCldtl();
        }
        // 014749 0500-DECODE-EXIT.                                                10/03/02
        // 014750    EXIT.                                                         09/03/23
    }

    private void genCldtl() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "genCldtl()");
        // 014800 1000-GEN-CLDTL-RTN.                                              08/07/04
        // 014820     IF WK-INFORMAT = "N"                                         09/03/23
        // 014840         PERFORM 4300-WHEAD-RTN      THRU 4300-WHEAD-EXIT.        09/03/23
        // 014860     IF WK-INFORMAT = "F"                                         09/03/23
        // 014880         PERFORM 4600-WHEAD-RTN      THRU 4600-WHEAD-EXIT.        09/03/23
        switch (wkInformat) {
            case "N":
                wheadn();
                break;
            case "F":
                wheadf();
                break;
            default:
                break;
        }

        genCldtlLoop();
        // 017300 1000-GEN-CLDTL-NEXT.                                             09/03/23
        // 017310     IF WK-INFORMAT = "N"                                         09/03/23
        // 017312        IF WK-TOTCNT = 0                                          09/03/23
        // 017314           PERFORM 3300-NODATA-RTN   THRU 3300-NODATA-EXIT        09/03/23
        // 017316        END-IF                                                    09/03/23
        // 017320        PERFORM 2300-LPUTFDATA-RTN   THRU 2300-LPUTFDATA-EXIT.    09/03/23
        // 017330                                                                  09/03/23
        if (wkInformat.equals("N")) {
            if (wkTotcnt == 0) {
                nodatan();
            }
            lputfdatan();
        }

        // 017340     IF WK-INFORMAT = "F"                                         09/03/23
        // 017342        IF WK-TOTCNT = 0                                          09/03/23
        // 017344           PERFORM 3600-NODATA-RTN   THRU 3600-NODATA-EXIT        09/03/23
        // 017346        END-IF                                                    09/03/23
        // 017350        PERFORM 2600-LPUTFDATA-RTN   THRU 2600-LPUTFDATA-EXIT.    09/03/23
        // 017360                                                                  09/03/23
        if (wkInformat.equals("F")) {
            if (wkTotcnt == 0) {
                nodataf();
            }
            lputfdataf();
        }

        // 017370     MOVE  0               TO   WK-TOTAMT.                        09/03/23
        // 017380     MOVE  0               TO   WK-TOTCNT.                        09/03/23
        wkTotamt = BigDecimal.ZERO;
        wkTotcnt = 0;

        // 017400 1000-GEN-CLDTL-EXIT.                                             08/07/04
        // 017500    EXIT.                                                         08/07/04
    }

    private void wheadn() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wheadn()");
        // 026310 4300-WHEAD-RTN.                                                  09/03/23
        // 026311     MOVE  SPACES            TO  PUTFN-REC.                       09/03/23
        // 026312     MOVE  00                TO  PUTFN-CTL.                       09/03/23
        // 026313     MOVE  WK-CODE           TO  PUTFN-CODE.                      09/03/23
        // 026314     MOVE  WK-BDATE          TO  PUTFN-BDATE.                     09/03/23
        // 026315     MOVE  WK-EDATE          TO  PUTFN-EDATE.                     09/03/23
        // 026316     WRITE    PUTFN-REC.                                          09/03/23
        FileSumPUTFN fileSumPUTFN = new FileSumPUTFN();
        fileSumPUTFN.setCtl("00");
        fileSumPUTFN.setCode(wkCode);
        fileSumPUTFN.setBdate("" + wkBdate);
        fileSumPUTFN.setEdate("" + wkEdate);
        List<String> dataList = new ArrayList<>();
        dataList.add(vo2TextFormatter.formatRS(fileSumPUTFN, false));
        textFileUtil.writeFileContent(wkPutdirn, dataList, CHARSET);
        // 026320 4300-WHEAD-EXIT.                                                 09/03/23
        // 026330    EXIT.                                                         09/03/23
    }

    private void wheadf() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "wheadf()");
        // 026350 4600-WHEAD-RTN.                                                  09/03/23
        // 026351     MOVE  SPACES            TO  PUTF-REC.                        09/03/23
        // 026352     MOVE  00                TO  PUTF-CTL.                        09/03/23
        // 026353     MOVE  WK-CODE           TO  PUTF-CODE.                       09/03/23
        // 026354     MOVE  WK-BDATE          TO  PUTF-BDATE.                      09/03/23
        // 026355     MOVE  WK-EDATE          TO  PUTF-EDATE.                      09/03/23
        // 026356     WRITE    PUTF-REC.                                           09/03/23
        FileSumPUTF fileSumPUTF = new FileSumPUTF();
        fileSumPUTF.setCtl("00");
        fileSumPUTF.setCode(wkCode);
        fileSumPUTF.setBdate("" + wkBdate);
        fileSumPUTF.setEdate("" + wkEdate);
        List<String> dataList = new ArrayList<>();
        dataList.add(vo2TextFormatter.formatRS(fileSumPUTF, false));
        textFileUtil.writeFileContent(wkPutdirf, dataList, CHARSET);
        // 026360 4600-WHEAD-EXIT.                                                 09/03/23
        // 026370    EXIT.                                                         09/03/23
    }

    private void genCldtlLoop() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "genCldtlLoop()");
        // 014900     SET   DB-CLDTL-IDX3  TO    BEGINNING.                        08/07/04
        // 015000 1000-GEN-CLDTL-LOOP.                                             08/07/04
        // 015100* 抓上一營業日至本日資料                                          08/07/04
        // 015200     FIND NEXT DB-CLDTL-IDX3  AT    DB-CLDTL-CODE=WK-CODE         08/07/04
        // 015300           AND   DB-CLDTL-DATE NOT < WK-BDATE                     08/07/04
        // 015400        ON EXCEPTION                                              08/07/04
        // 015500           IF  DMSTATUS(NOTFOUND)                                 08/07/04
        // 015600               GO TO 1000-GEN-CLDTL-NEXT                          09/03/23
        // 015700            ELSE                                                  08/07/04
        // 015800               CALL SYSTEM DMTERMINATE.                           08/07/04
        // 015900                                                                  08/07/04
        List<CldtlbyCodeEntdyAfterBus> cldtlList =
                cldtlService.findbyCodeEntdyAfter(wkCode, wkBdate, 0, 0, 2000);
        if (!Objects.isNull(cldtlList) && !cldtlList.isEmpty()) {
            for (CldtlbyCodeEntdyAfterBus cldtl : cldtlList) {
                // 015910* 判斷實際繳費日期                                                12/03/31
                // 015920     IF WK-FMDATE = "S"                                           12/03/31
                // 015930        IF  DB-CLDTL-SITDATE     < WK-BDATE  OR                   12/03/31
                // 015940            DB-CLDTL-SITDATE     NOT < WK-EDATE                   12/03/31
                // 015950            GO  TO   1000-GEN-CLDTL-LOOP                          12/03/31
                // 015960        END-IF                                                    12/03/31
                // 015970     END-IF.                                                      12/03/31
                // 015980                                                                  12/03/31
                if (wkFmdate.equals("S")) {
                    if (cldtl.getSitdate() < wkBdate || cldtl.getSitdate() >= wkEdate) {
                        continue;
                    }
                }

                // 016000* 判斷實際繳費日期                                                08/07/04
                // 016050     IF WK-FMDATE = "A" OR "B" OR "C"                             09/04/22
                // 016100        IF  DB-CLDTL-SITDATE     < WK-BDATE  OR                   09/04/22
                // 016200            DB-CLDTL-SITDATE     > WK-EDATE                       09/04/22
                // 016300            GO  TO   1000-GEN-CLDTL-LOOP                          09/04/22
                // 016400        END-IF                                                    09/04/22
                // 016405     END-IF.                                                      09/04/22
                if (wkFmdate.equals("A") || wkFmdate.equals("B") || wkFmdate.equals("C")) {
                    if (cldtl.getSitdate() < wkBdate || cldtl.getSitdate() > wkEdate) {
                        continue;
                    }
                }

                // 016410* 判斷帳務日期                                                    09/04/22
                // 016415     IF WK-FMDATE = "T" OR "M"                                    10/03/02
                // 016420        IF  DB-CLDTL-DATE     < WK-BDATE  OR                      09/04/22
                // 016430            DB-CLDTL-DATE     > WK-EDATE                          09/04/22
                // 016440            GO  TO   1000-GEN-CLDTL-LOOP                          09/04/22
                // 016442        END-IF                                                    09/04/22
                // 016444     END-IF.                                                      09/04/22
                // 016450                                                                  09/04/22
                if (wkFmdate.equals("T") || wkFmdate.equals("M")) {
                    if (cldtl.getEntdy() < wkBdate || cldtl.getEntdy() > wkEdate) {
                        continue;
                    }
                }

                // 016455     IF WK-FMDATE = "S"                                           12/03/31
                // 016460        IF  DB-CLDTL-DATE     < WK-BDATE  OR                      12/03/31
                // 016465            DB-CLDTL-DATE     > WK-EDATE                          12/03/31
                // 016470            GO  TO   1000-GEN-CLDTL-LOOP                          12/03/31
                // 016475        END-IF                                                    12/03/31
                // 016480     END-IF.                                                      12/03/31
                // 016481                                                                  12/07/24
                if (wkFmdate.equals("S")) {
                    if (cldtl.getEntdy() < wkBdate || cldtl.getEntdy() > wkEdate) {
                        continue;
                    }
                }

                // 016482     IF WK-FMDATE = "D"                                           12/07/24
                // 016483        IF  DB-CLDTL-DATE NOT = WK-BDATE                          12/07/24
                // 016484            GO  TO   1000-GEN-CLDTL-LOOP                          12/07/24
                // 016485        END-IF                                                    12/07/24
                // 016486     END-IF.                                                      12/07/24
                // 016490                                                                  12/03/31
                if (wkFmdate.equals("D")) {
                    if (cldtl.getEntdy() != wkBdate) {
                        continue;
                    }
                }

                // 016500     ADD DB-CLDTL-AMT         TO WK-TOTAMT.                       08/07/04
                // 016600     ADD 1                    TO WK-TOTCNT.                       08/07/04
                wkTotamt = wkTotamt.add(cldtl.getAmt());
                wkTotcnt++;

                // 016700     IF  WK-INFORMAT = "N"                                        08/07/04
                // 016800         PERFORM 1300-DETAIL-RTN THRU 1300-DETAIL-EXIT.           08/07/04
                if (wkInformat.equals("N")) {
                    detailN(cldtl);
                }
                // 016900     IF  WK-INFORMAT = "F"                                        08/07/04
                // 017000         PERFORM 1600-DETAIL-RTN THRU 1600-DETAIL-EXIT.           08/07/04
                if (wkInformat.equals("N")) {
                    detailF(cldtl);
                }
                // 017020     IF  WK-INFORMAT = "M"                                        10/03/02
                // 017040         PERFORM 1900-DETAIL-RTN THRU 1900-DETAIL-EXIT.           10/03/02
                if (wkInformat.equals("N")) {
                    detailM(cldtl);
                }
                // 017100                                                                  08/07/04
                // 017200     GO  TO  1000-GEN-CLDTL-LOOP.                                 08/07/04
            }
        }
    }

    private void detailN(CldtlbyCodeEntdyAfterBus cldtl) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "detailN()");
        // 017700 1300-DETAIL-RTN.                                                 08/07/04
        // 017800                                                                  08/07/04
        // 017900     MOVE  SPACES            TO  PUTFN-REC.                       08/07/04
        FilePUTFN putfn = new FilePUTFN();
        // 018000     MOVE  11                TO  PUTFN-CTL.                       08/07/04
        putfn.setCtl("11");
        // 018100     MOVE  WK-CODE           TO  PUTFN-CODE.                      08/07/04
        putfn.setCode(wkCode);
        // 018200     MOVE  DB-CLDTL-RCPTID   TO  PUTFN-RCPTID.                    08/07/04
        putfn.setRcptid(cldtl.getRcptid());
        // 018300     MOVE  DB-CLDTL-DATE     TO  PUTFN-DATE.                      08/07/04
        putfn.setEntdy(parse.decimal2String(cldtl.getEntdy(), 8, 0));
        // 018400     MOVE  DB-CLDTL-TIME     TO  PUTFN-TIME.                      08/07/04
        putfn.setTime(parse.decimal2String(cldtl.getTime(), 6, 0));
        // 018500     MOVE  DB-CLDTL-CLLBR    TO  PUTFN-CLLBR.                     08/07/04
        putfn.setCllbr(parse.decimal2String(cldtl.getCllbr(), 3, 0));
        // 018600     MOVE  DB-CLDTL-LMTDATE  TO  PUTFN-LMTDATE.                   08/07/04
        putfn.setLmtdate(parse.decimal2String(cldtl.getLmtdate(), 8, 0));
        // 018700     MOVE  DB-CLDTL-USERDATA TO  PUTFN-USERDATA.                  08/07/04
        putfn.setUserdata(cldtl.getUserdata());
        // 018800     MOVE  DB-CLDTL-SITDATE  TO  PUTFN-SITDATE.                   08/07/04
        putfn.setSitdate(parse.decimal2String(cldtl.getSitdate(), 8, 0));
        // 018900     MOVE  DB-CLDTL-TXTYPE   TO  PUTFN-TXTYPE.                    08/07/04
        putfn.setTxtype(parse.decimal2String(cldtl.getTxtype(), 1, 0));
        // 019000     MOVE  DB-CLDTL-AMT      TO  PUTFN-AMT,PUTFN-OLDAMT.          08/07/04
        putfn.setAmt(parse.decimal2String(cldtl.getAmt(), 12, 0));
        putfn.setOldamt(parse.decimal2String(cldtl.getAmt(), 10, 0));
        // 019020     IF  WK-INCODE = "13559D"                                     12/07/24
        // 019040         MOVE   DB-CLDTL-SERINO  TO PUTFN-FILLER(1:6).            12/07/24
        if (wkIncode.equals("13559D")) {
            putfn.setFiller(parse.decimal2String(cldtl.getSerino(), 6, 0));
        }
        // 019100     WRITE    PUTFN-REC.                                          08/07/04
        List<String> dataList = new ArrayList<>();
        dataList.add(vo2TextFormatter.formatRS(putfn, false));
        textFileUtil.writeFileContent(wkPutdirn, dataList, CHARSET);
        // 019200                                                                  08/07/04
        // 019300 1300-DETAIL-EXIT.                                                08/07/04
        // 019400    EXIT.                                                         08/07/04
    }

    private void detailF(CldtlbyCodeEntdyAfterBus cldtl) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "detailF()");
        // 019500 1600-DETAIL-RTN.                                                 08/07/04
        // 019600                                                                  08/07/04
        // 019700     MOVE  SPACES            TO  PUTF-REC.                        08/07/04
        FilePUTF putf = new FilePUTF();
        // 019800     MOVE  11                TO  PUTF-CTL.                        08/07/04
        putf.setCtl("11");
        // 019900     MOVE  WK-CODE           TO  PUTF-CODE.                       08/07/04
        putf.setCode(wkCode);
        // 020000     MOVE  DB-CLDTL-RCPTID   TO  PUTF-RCPTID.                     08/07/04
        putf.setRcptid(cldtl.getRcptid());
        // 020100     MOVE  DB-CLDTL-DATE     TO  PUTF-DATE.                       08/07/04
        putf.setEntdy(parse.decimal2String(cldtl.getEntdy(), 6, 0));
        // 020200     MOVE  DB-CLDTL-TIME     TO  PUTF-TIME.                       08/07/04
        putf.setTime(parse.decimal2String(cldtl.getTime(), 6, 0));
        // 020300     MOVE  DB-CLDTL-CLLBR    TO  PUTF-CLLBR.                      08/07/04
        putf.setCllbr(parse.decimal2String(cldtl.getCllbr(), 3, 0));
        // 020400     MOVE  DB-CLDTL-LMTDATE  TO  PUTF-LMTDATE.                    08/07/04
        putf.setLmtdate(parse.decimal2String(cldtl.getLmtdate(), 6, 0));
        // 020500     MOVE  DB-CLDTL-USERDATA TO  PUTF-USERDATA.                   08/07/04
        putf.setUserdata(cldtl.getUserdata());
        // 020600     MOVE  DB-CLDTL-SITDATE  TO  PUTF-SITDATE.                    08/07/04
        putf.setSitdate(parse.decimal2String(cldtl.getSitdate(), 6, 0));
        // 020700     MOVE  DB-CLDTL-TXTYPE   TO  PUTF-TXTYPE.                     08/07/04
        putf.setTxtype(parse.decimal2String(cldtl.getTxtype(), 1, 0));
        // 020800     MOVE  DB-CLDTL-AMT      TO  PUTF-AMT,PUTFN-OLDAMT.           08/07/04
        putf.setAmt(parse.decimal2String(cldtl.getAmt(), 10, 0));
        putf.setOldamt(parse.decimal2String(cldtl.getAmt(), 8, 0));
        // 020900     WRITE    PUTF-REC.                                           08/07/04
        List<String> dataList = new ArrayList<>();
        dataList.add(vo2TextFormatter.formatRS(putf, false));
        textFileUtil.writeFileContent(wkPutdirf, dataList, CHARSET);
        // 021000                                                                  08/07/04
        // 021100 1600-DETAIL-EXIT.                                                08/07/04
        // 021200    EXIT.                                                         08/07/04
    }

    private void detailM(CldtlbyCodeEntdyAfterBus cldtl) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "detailM()");
        // 021240 1900-DETAIL-RTN.                                                 10/03/02
        // 021245                                                                  10/03/02
        // 021250     MOVE  SPACES            TO  KPUTH-REC.                       10/03/02
        FileKPUTH kputh = new FileKPUTH();
        // 021252     MOVE  DB-CLDTL-CODE     TO  KPUTH-CODE.                      10/03/02
        kputh.setCode(cldtl.getCode());
        // 021254     MOVE  DB-CLDTL-AMT      TO  KPUTH-AMT.                       10/03/02
        kputh.setAmt(parse.decimal2String(cldtl.getAmt(), 10, 0));
        // 021256     MOVE  DB-CLDTL-RCPTID   TO  KPUTH-RCPTID.                    10/03/02
        kputh.setRcptid(cldtl.getRcptid());
        // 021258     MOVE  DB-CLDTL-TXTYPE   TO  KPUTH-TXTYPE.                    10/03/02
        kputh.setTxtype(parse.decimal2String(cldtl.getTxtype(), 1, 0));
        // 021260     MOVE  DB-CLDTL-CLLBR    TO  KPUTH-CLLBR.                     10/03/02
        kputh.setCllbr(parse.decimal2String(cldtl.getCllbr(), 3, 0));
        // 021266     MOVE  DB-CLDTL-DATE     TO  KPUTH-DATE.                      10/03/02
        kputh.setEntdy(parse.decimal2String(cldtl.getEntdy(), 7, 0));
        // 021268     MOVE  DB-CLDTL-TIME     TO  KPUTH-TIME.                      10/03/02
        kputh.setTime(parse.decimal2String(cldtl.getTime(), 6, 0));
        // 021270     MOVE  DB-CLDTL-LMTDATE  TO  KPUTH-LMTDATE.                   10/03/02
        kputh.setLmtdate(parse.decimal2String(cldtl.getLmtdate(), 6, 0));
        // 021272     MOVE  DB-CLDTL-USERDATA TO  KPUTH-USERDATA.                  10/03/02
        kputh.setUserdata(cldtl.getUserdata());
        // 021274     MOVE  DB-CLDTL-SITDATE  TO  KPUTH-SITDATE.                   10/03/02
        kputh.setSitdate(parse.decimal2String(cldtl.getSitdate(), 7, 0));
        // 021278     MOVE  DB-CLDTL-SERINO   TO  KPUTH-SERINO.                    10/03/02
        kputh.setSerino(parse.decimal2String(cldtl.getSerino(), 6, 0));
        // 021280                                                                  10/03/02
        // 021281* 配合公庫部需求借用欄位增加櫃員訊息                              10/03/02
        // 021282     IF   WK-INCODE = "CL060M"                                    10/03/02
        // 021283       MOVE  DB-CLDTL-TRMNO    TO  KPUTH-UPLDATE                  10/03/02
        // 021284       MOVE  DB-CLDTL-TLRNO    TO  KPUTH-PUTNAME(1:2)             10/03/02
        // 021285     END-IF.                                                      10/03/02
        if (wkIncode.equals("CL060M")) {
            kputh.setUpldate(parse.decimal2String(cldtl.getTrmno(), 6, 0));
            kputh.setPutname(cldtl.getTlrno());
        }
        // 021290     WRITE  KPUTH-REC.                                            10/03/02
        List<String> dataList = new ArrayList<>();
        dataList.add(vo2TextFormatter.formatRS(kputh, false));
        textFileUtil.writeFileContent(wkPutdirm, dataList, CHARSET);
        // 021300                                                                  08/07/04
        // 021320 1900-DETAIL-EXIT.                                                10/03/02
        // 021340    EXIT.                                                         10/03/02
    }

    private void nodatan() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "nodatan()");
        // 024300 3300-NODATA-RTN.                                                 08/07/04
        // 024400                                                                  08/07/04
        // 024500     MOVE  SPACES            TO  PUTFN-REC.                       08/07/04
        FileNoDataPUTFN putfn = new FileNoDataPUTFN();
        // 024600     MOVE  19                TO  PUTFN-CTL.                       08/07/04
        putfn.setCtl("19");
        // 024700     MOVE  WK-CODE           TO  PUTFN-CODE.                      08/07/04
        putfn.setCode(wkCode);
        // 024800     MOVE  " NO DATA"        TO  PUTFN-RCPTID.                    08/07/04
        putfn.setNodata(" NO DATA");
        // 024900     WRITE    PUTFN-REC.                                          08/07/04
        List<String> dataList = new ArrayList<>();
        dataList.add(vo2TextFormatter.formatRS(putfn, false));
        textFileUtil.writeFileContent(wkPutdirn, dataList, CHARSET);
        // 025000                                                                  08/07/04
        // 025100 3300-NODATA-EXIT.                                                08/07/04
        // 025200    EXIT.                                                         08/07/04
    }

    private void lputfdatan() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "lputfdatan()");
        // 021400 2300-LPUTFDATA-RTN.                                              08/07/04
        // 021500                                                                  08/07/04
        // 021600     MOVE  SPACES            TO  PUTFN-REC.                       08/07/04
        FileSumPUTFN putfn = new FileSumPUTFN();
        // 021700     MOVE  12                TO  PUTFN-CTL.                       08/07/04
        putfn.setCtl("12");
        // 021800     MOVE  WK-CODE           TO  PUTFN-CODE.                      08/07/04
        putfn.setCode(wkCode);
        // 021900     MOVE  WK-BDATE          TO  PUTFN-BDATE.                     08/07/04
        putfn.setBdate(parse.decimal2String(wkBdate, 8, 0));
        // 022000     MOVE  WK-EDATE          TO  PUTFN-EDATE.                     08/07/04
        putfn.setEdate(parse.decimal2String(wkEdate, 8, 0));
        // 022100     MOVE  WK-TOTCNT         TO  PUTFN-TOTCNT.                    08/07/04
        putfn.setTotcnt(parse.decimal2String(wkTotcnt, 6, 0));
        // 022200     MOVE  WK-TOTAMT         TO  PUTFN-TOTAMT.                    08/07/04
        putfn.setTotamt(parse.decimal2String(wkTotamt, 13, 0));
        // 022300     WRITE    PUTFN-REC.                                          08/07/04
        List<String> dataList = new ArrayList<>();
        dataList.add(vo2TextFormatter.formatRS(putfn, false));
        textFileUtil.writeFileContent(wkPutdirn, dataList, CHARSET);
        // 022500                                                                  08/07/04
        // 022600 2300-LPUTFDATA-EXIT.                                             08/07/04
        // 022700    EXIT.                                                         08/07/04
    }

    private void nodataf() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "nodataf()");
        // 025300 3600-NODATA-RTN.                                                 08/07/04
        // 025400
        // 025500     MOVE  SPACES            TO  PUTF-REC.                        08/07/04
        FileNoDataPUTF putf = new FileNoDataPUTF();
        // 025600     MOVE  19                TO  PUTF-CTL.                        08/07/04
        putf.setCtl("19");
        // 025700     MOVE  WK-CODE           TO  PUTF-CODE.                       08/07/04
        putf.setCode(wkCode);
        // 025800     MOVE  " NO DATA"        TO  PUTF-RCPTID.                     08/07/04
        putf.setNodata(" NO DATA");
        // 025900     WRITE    PUTF-REC.                                           08/07/04
        List<String> dataList = new ArrayList<>();
        dataList.add(vo2TextFormatter.formatRS(putf, false));
        textFileUtil.writeFileContent(wkPutdirf, dataList, CHARSET);
        // 026000                                                                  08/07/04
        // 026100 3600-NODATA-EXIT.                                                08/07/04
        // 026200    EXIT.                                                         08/07/04
    }

    private void lputfdataf() {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "lputfdataf()");
        // 022800 2600-LPUTFDATA-RTN.                                              08/07/04
        // 022900                                                                  08/07/04
        // 023000     MOVE  SPACES            TO  PUTF-REC.                        08/07/04
        FileSumPUTF putf = new FileSumPUTF();
        // 023100     MOVE  12                TO  PUTF-CTL.                        08/07/04
        putf.setCtl("12");
        // 023200     MOVE  WK-CODE           TO  PUTF-CODE.                       08/07/04
        putf.setCode(wkCode);
        // 023300     MOVE  WK-BDATE          TO  PUTF-BDATE.                      08/07/04
        putf.setBdate(parse.decimal2String(wkBdate, 6, 0));
        // 023400     MOVE  WK-EDATE          TO  PUTF-EDATE.                      08/07/04
        putf.setEdate(parse.decimal2String(wkEdate, 6, 0));
        // 023500     MOVE  WK-TOTCNT         TO  PUTF-TOTCNT.                     08/07/04
        putf.setTotcnt(parse.decimal2String(wkTotcnt, 6, 0));
        // 023600     MOVE  WK-TOTAMT         TO  PUTF-TOTAMT.                     08/07/04
        putf.setTotamt(parse.decimal2String(wkTotamt, 13, 0));
        // 023700     WRITE    PUTF-REC.
        List<String> dataList = new ArrayList<>();
        dataList.add(vo2TextFormatter.formatRS(putf, false));
        textFileUtil.writeFileContent(wkPutdirf, dataList, CHARSET);
        // 023900                                                                  08/07/04
        // 024000 2600-LPUTFDATA-EXIT.                                             08/07/04
        // 024100    EXIT.                                                         08/07/04
    }
}
