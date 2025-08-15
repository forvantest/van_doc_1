/* (C) 2024 */
package com.bot.ncl.diff;

import com.bot.ncl.diff.layout.components.*;
import com.bot.ncl.diff.layout.model.Column;
import com.bot.ncl.diff.layout.model.Layout;
import com.bot.ncl.dto.entities.*;
import com.bot.ncl.jpa.entities.impl.CldtlId;
import com.bot.ncl.jpa.svc.*;
import com.bot.ncl.util.DiffParseUtil;
import com.bot.ncl.util.files.TextFileUtil;
import com.bot.txcontrol.config.logger.ApLogHelper;
import com.bot.txcontrol.eum.LogType;
import com.bot.txcontrol.exception.LogicException;
import com.bot.txcontrol.jpa.transaction.TransactionCase;
import com.bot.txcontrol.util.parse.Parse;
import com.bot.txcontrol.util.text.format.FormatUtil;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Scope("prototype")
public class ErrorDataCom {

    @Autowired private TextFileUtil textFileUtil;
    @Autowired private Parse parse;
    @Autowired private DiffParseUtil diffParse;
    @Autowired private FormatUtil formatUtil;
    @Autowired private TextFileUtil textFile;
    @Autowired private LoadDataCom loadDataCom;
    @Autowired private CldtlService cldtlService;
    @Autowired private CldmrService cldmrService;
    @Autowired private ClcmpService clcmpService;
    @Autowired private ClbafService clbafService;
    @Autowired private ClmcService clmcService;
    @Autowired private ClfeeService clfeeService;
    @Autowired private CltotService cltotService;
    @Autowired private CltmrService cltmrService;
    @Autowired private ClmrService clmrService;

    private List<String> fileContents;

    @Value("${localFile.ncl.batch.directory}")
    private String fileDir;

    private List<CltotBus> lCltot;
    private List<CldtlBus> lCldtl;
    private List<ClmrBus> lClmr;
    private List<CltmrBus> lCltmr;
    private List<ClfeeBus> lClfee;
    private List<ClmcBus> lClmc;
    private List<ClbafBus> lClbaf;
    private List<ClcmpBus> lClcmp;
    private List<CldmrBus> lCldmr;

    private List<CldtlBus> insertErrorCldtlDetail;
    private List<CldmrBus> insertErrorCldmrDetail;
    private List<ClcmpBus> insertErrorClcmpDetail;
    private List<ClbafBus> insertErrorClbafDetail;
    private List<ClmcBus> insertErrorClmcDetail;
    private List<ClfeeBus> insertErrorClfeeDetail;
    private List<CltotBus> insertErrorCltotDetail;

    private List<ClmrBus> insertErrorClmrDetail;
    private List<CltmrBus> insertErrorCltmrDetail;
    private Layout layout = null;
    private int function;
    private String tableName;
    private static final String CHARSET = "Big5";
    private String readFilePath;
    private StringBuilder sb;
    private int commitMAXSIZE = 10;
    private int nowModifyCnt = 0;

    private String code;
    private String amt;
    private String rcptid;
    private String txtype;
    private String cllbr;
    private String trmno;
    private String txtno;
    private String tlrno;
    private String entdy;
    private String time;
    private String lmtdate;
    private String userdata;
    private String sitdate;
    private String caldy;
    private String actno;
    private String serino;
    private String crdb;
    private String curcd;
    private String hcode;
    private String sourcetp;
    private String cactno;
    private String htxseq;
    private String empno;
    private String putfg;
    private String entfg;
    private String sourceip;
    private String uplfile;
    private String pbrno;
    private String cfee2;
    private String fkd;
    private String fee;
    private String feecost;
    private String vrcode;
    private String riddup;
    private String dupcyc;
    private String msg1;
    private String puttime;
    private String subfg;
    private String chktype;
    private String chkamt;
    private String unit;
    private String amtcyc;
    private String amtfg;
    private String cname;
    private String stop;
    private String holdcnt;
    private String holdcnt2;
    private String afcbv;
    private String netinfo;
    private String print;
    private String stdate;
    private String sttime;
    private String stopdate;
    private String stoptime;
    private String lkcode;
    private String flag;
    private String otherfield;
    private String atmcode;
    private String entpno;
    private String hentpno;
    private String scname;
    private String cdata;
    private String appdt;
    private String upddt;
    private String lputdt;
    private String llputdt;
    private String ulputdt;
    private String ullputdt;
    private String prtype;
    private String clsacno;
    private String clssbno;
    private String clsdtlno;
    private String ebtype;
    private String pwtype;
    private String feename;
    private String putname;
    private String rcvamt;
    private String payamt;
    private String totcnt;
    private String pamt;
    private String npamt;
    private String keyCodeFee;
    private String stamt;
    private String cfee1;
    private String cfee3;
    private String cfee4;
    private String mfee;
    private String cfeeeb;
    private String cfee003;
    private String feeCalType;
    private String putsend;
    private String putform;
    private String puttype;
    private String putaddr;
    private String putEncode;
    private String putCompress;
    private String oputtime;
    private String cyck1;
    private String cycno1;
    private String cyck2;
    private String cycno2;
    private String putdtfg;
    private String putdt;
    private String msg2;
    private String tputdt;
    private String usecnt;
    private String cnt;
    private String kfee;
    private String id;
    private String pname;
    private String sdate;
    private String stime;
    private String ldate;
    private String ltime;
    private String kdate;
    private String lflg;
    private String cdate;
    private String udate;
    private String utime;
    private String kinbr;
    private String otflag;
    private String bal;

    public <T> void writeTempErrorData(String tableName, List<T> tableList) {
        String tempFileName = "TempError" + tableName;
        writeErrorData(tableName, tableList, tempFileName);
    }

    public <T> void writeErrorData(String tableName, List<T> tableList, String fileName) {
        ApLogHelper.info(log, false, LogType.NORMAL.getCode(), "writeTmpErrorData ..");
        fileContents = new ArrayList<>();
        if (tableList.isEmpty()) return;
        switch (tableName) {
            case "CLDTL":
                lCldtl =
                        tableList.stream()
                                .map(table -> (CldtlBus) table) // 強制轉型
                                .collect(Collectors.toList());
                writeErrorCldtl();
                lCldtl.clear();
                break;
            case "CLMR":
                lClmr =
                        tableList.stream()
                                .map(table -> (ClmrBus) table) // 強制轉型
                                .collect(Collectors.toList());
                writeErrorClmr();
                lClmr.clear();
                break;
            case "CLTMR":
                lCltmr =
                        tableList.stream()
                                .map(table -> (CltmrBus) table) // 強制轉型
                                .collect(Collectors.toList());
                writeErrorCltmr();
                lCltmr.clear();
                break;
            case "CLTOT":
                lCltot =
                        tableList.stream()
                                .map(table -> (CltotBus) table) // 強制轉型
                                .collect(Collectors.toList());
                writeErrorCltot();
                lCltot.clear();
                break;
            case "CLFEE":
                lClfee =
                        tableList.stream()
                                .map(table -> (ClfeeBus) table) // 強制轉型
                                .collect(Collectors.toList());
                writeErrorClfee();
                lClfee.clear();
                break;
            case "CLMC":
                lClmc =
                        tableList.stream()
                                .map(table -> (ClmcBus) table) // 強制轉型
                                .collect(Collectors.toList());
                writeErrorClmc();
                lClmc.clear();
                break;
            case "CLBAF":
                lClbaf =
                        tableList.stream()
                                .map(table -> (ClbafBus) table) // 強制轉型
                                .collect(Collectors.toList());
                writeErrorClbaf();
                lClbaf.clear();
                break;
            case "CLCMP":
                lClcmp =
                        tableList.stream()
                                .map(table -> (ClcmpBus) table) // 強制轉型
                                .collect(Collectors.toList());
                writeErrorClcmp();
                lClcmp.clear();
                break;
            case "CLDMR":
                lCldmr =
                        tableList.stream()
                                .map(table -> (CldmrBus) table) // 強制轉型
                                .collect(Collectors.toList());
                writeErrorCldmr();
                lCldmr.clear();
                break;
            default:
                break;
        }
        tableList.clear();
        writeFile(fileName);
    }

    private void writeErrorCldmr() {
        for (CldmrBus tCldmr : lCldmr) {
            code = tCldmr.getCode();
            rcptid = tCldmr.getRcptid();
            id = tCldmr.getId();
            pname = tCldmr.getPname();
            curcd = "" + tCldmr.getCurcd();
            bal = parse.decimal2String(tCldmr.getBal(), 11, 2);
            ldate = "" + tCldmr.getLdate();
            ltime = "" + tCldmr.getLtime();
            kdate = "" + tCldmr.getKdate();
            lflg = "" + tCldmr.getLflg();
            cdate = "" + tCldmr.getCdate();
            udate = "" + tCldmr.getUdate();
            utime = "" + tCldmr.getUtime();
            kinbr = "" + tCldmr.getKinbr();
            tlrno = tCldmr.getTlrno();
            empno = tCldmr.getEmpno();

            sb = new StringBuilder();
            sb.append(formatUtil.padX(code, 6));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(rcptid, 16));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(id, 10));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(pname, 30));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(curcd, 2));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(bal, 14));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(ldate, 8));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(ltime, 6));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(kdate, 8));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(lflg, 1));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(cdate, 8));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(udate, 8));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(utime, 6));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(kinbr, 3));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(tlrno, 2));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(empno, 6));
            fileContents.add(formatUtil.padX(sb.toString(), 500));
        }
    }

    private void writeErrorClcmp() {
        for (ClcmpBus tClcmp : lClcmp) {
            code = tClcmp.getCode();
            rcptid = tClcmp.getRcptid();
            id = tClcmp.getId();
            pname = tClcmp.getPname();
            curcd = "" + tClcmp.getCurcd();
            amt = parse.decimal2String(tClcmp.getAmt(), 11, 2);
            sdate = "" + tClcmp.getSdate();
            stime = "" + tClcmp.getStime();
            ldate = "" + tClcmp.getLdate();
            ltime = "" + tClcmp.getLtime();
            kdate = "" + tClcmp.getKdate();
            lflg = "" + tClcmp.getLflg();
            cdate = "" + tClcmp.getCdate();
            udate = "" + tClcmp.getUdate();
            utime = "" + tClcmp.getUtime();
            kinbr = "" + tClcmp.getKinbr();
            tlrno = tClcmp.getTlrno();
            empno = tClcmp.getEmpno();
            otflag = tClcmp.getOtflag();

            sb = new StringBuilder();
            sb.append(formatUtil.padX(code, 6));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(rcptid, 16));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(id, 10));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(pname, 30));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(curcd, 2));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(amt, 14));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(sdate, 8));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(stime, 6));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(ldate, 8));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(ltime, 6));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(kdate, 8));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(lflg, 1));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(cdate, 8));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(udate, 8));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(utime, 6));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(kinbr, 3));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(tlrno, 2));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(empno, 6));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(otflag, 2));
            fileContents.add(formatUtil.padX(sb.toString(), 500));
        }
    }

    private void writeErrorClbaf() {
        for (ClbafBus tClbaf : lClbaf) {
            cllbr = parse.decimal2String(tClbaf.getCllbr(), 3, 0);
            entdy = parse.decimal2String(tClbaf.getEntdy(), 8, 0);
            code = tClbaf.getCode();
            pbrno = parse.decimal2String(tClbaf.getPbrno(), 3, 0);
            crdb = parse.decimal2String(tClbaf.getCrdb(), 1, 0);
            txtype = tClbaf.getTxtype();
            curcd = parse.decimal2String(tClbaf.getCurcd(), 2, 0);
            cnt = parse.decimal2String(tClbaf.getCnt(), 5, 0);
            amt = parse.decimal2String(tClbaf.getAmt(), 13, 2);
            cfee2 = parse.decimal2String(tClbaf.getCfee2(), 6, 2);
            kfee = parse.decimal2String(tClbaf.getKfee(), 8, 2);

            sb = new StringBuilder();
            sb.append(formatUtil.pad9(cllbr, 3));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(entdy, 8));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(code, 6));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(pbrno, 3));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(crdb, 1));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(txtype, 2));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(curcd, 2));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(cnt, 5));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(amt, 16));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(cfee2, 9));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(kfee, 11));
            fileContents.add(formatUtil.padX(sb.toString(), 500));
        }
    }

    private void writeErrorClmc() {
        for (ClmcBus tClmc : lClmc) {
            putname = tClmc.getPutname();
            putsend = tClmc.getPutsend();
            putform = tClmc.getPutform();
            puttype = parse.decimal2String(tClmc.getPuttype(), 2, 0);
            putaddr = tClmc.getPutaddr();
            putEncode = tClmc.getPutEncode();
            putCompress = tClmc.getPutCompress();
            oputtime = tClmc.getOputtime();
            cyck1 = parse.decimal2String(tClmc.getCyck1(), 1, 0);
            cycno1 = parse.decimal2String(tClmc.getCycno1(), 2, 0);
            cyck2 = parse.decimal2String(tClmc.getCyck2(), 1, 0);
            cycno2 = parse.decimal2String(tClmc.getCycno2(), 2, 0);
            putdtfg = parse.decimal2String(tClmc.getPutdtfg(), 1, 0);
            putdt = parse.decimal2String(tClmc.getPutdt(), 8, 0);
            msg2 = parse.decimal2String(tClmc.getMsg2(), 1, 0);
            tputdt = parse.decimal2String(tClmc.getTputdt(), 8, 0);
            usecnt = parse.decimal2String(tClmc.getUsecnt(), 4, 0);

            sb = new StringBuilder();
            sb.append(formatUtil.padX(putname, 6));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(putsend, 1));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(putform, 1));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(puttype, 2));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(putaddr, 100));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(putEncode, 1));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(putCompress, 1));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(oputtime, 1));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(cyck1, 1));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(cycno1, 2));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(cyck2, 1));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(cycno2, 2));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(putdtfg, 1));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(putdt, 8));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(msg2, 1));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(tputdt, 8));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(usecnt, 4));
            fileContents.add(formatUtil.padX(sb.toString(), 500));
        }
    }

    private void writeErrorClfee() {
        for (ClfeeBus tClfee : lClfee) {
            keyCodeFee = tClfee.getKeyCodeFee();
            txtype = tClfee.getTxtype();
            stamt = parse.decimal2String(tClfee.getStamt(), 6, 0);
            cfee1 = parse.decimal2String(tClfee.getCfee1(), 4, 2);
            cfee2 = parse.decimal2String(tClfee.getCfee2(), 3, 2);
            cfee3 = parse.decimal2String(tClfee.getCfee3(), 4, 2);
            cfee4 = parse.decimal2String(tClfee.getCfee4(), 10, 2);
            fkd = parse.decimal2String(tClfee.getFkd(), 1, 0);
            mfee = parse.decimal2String(tClfee.getMfee(), 3, 2);
            cfeeeb = parse.decimal2String(tClfee.getCfeeeb(), 3, 2);
            fee = parse.decimal2String(tClfee.getFee(), 3, 2);
            feecost = parse.decimal2String(tClfee.getFeecost(), 3, 2);
            cfee003 = parse.decimal2String(tClfee.getCfee003(), 3, 2);
            feeCalType = parse.decimal2String(tClfee.getFeeCalType(), 1, 0);

            sb = new StringBuilder();
            sb.append(formatUtil.padX(keyCodeFee, 6));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(txtype, 2));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(stamt, 6));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(cfee1, 7));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(cfee2, 6));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(cfee3, 7));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(cfee4, 13));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(fkd, 1));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(mfee, 6));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(cfeeeb, 6));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(fee, 6));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(feecost, 6));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(cfee003, 6));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(feeCalType, 1));
            fileContents.add(formatUtil.padX(sb.toString(), 500));
        }
    }

    private void writeErrorCltot() {
        for (CltotBus tCltot : lCltot) {
            code = tCltot.getCode();
            curcd = "" + tCltot.getCurcd();
            rcvamt = parse.decimal2String(tCltot.getRcvamt(), 15, 2);
            payamt = parse.decimal2String(tCltot.getPayamt(), 15, 2);
            totcnt = "" + tCltot.getTotcnt();
            pamt = parse.decimal2String(tCltot.getPamt(), 15, 2);
            npamt = parse.decimal2String(tCltot.getNpamt(), 15, 2);

            sb = new StringBuilder();
            sb.append(formatUtil.padX(code, 6));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(curcd, 2));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(rcvamt, 18));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(payamt, 18));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(totcnt, 8));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(pamt, 18));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(npamt, 18));
            fileContents.add(formatUtil.padX(sb.toString(), 500));
        }
    }

    private void writeErrorCltmr() {
        for (CltmrBus tCltmr : lCltmr) {
            code = tCltmr.getCode();
            atmcode = parse.decimal2String(tCltmr.getAtmcode(), 3, 0);
            entpno = tCltmr.getEntpno();
            hentpno = parse.decimal2String(tCltmr.getHentpno(), 8, 0);
            scname = parse.decimal2String(tCltmr.getScname(), 10, 0);
            cdata = parse.decimal2String(tCltmr.getCdata(), 1, 0);
            appdt = parse.decimal2String(tCltmr.getAppdt(), 8, 0);
            upddt = parse.decimal2String(tCltmr.getUpddt(), 8, 0);
            lputdt = parse.decimal2String(tCltmr.getLputdt(), 8, 0);
            llputdt = parse.decimal2String(tCltmr.getLlputdt(), 8, 0);
            ulputdt = parse.decimal2String(tCltmr.getUlputdt(), 8, 0);
            ullputdt = parse.decimal2String(tCltmr.getUllputdt(), 8, 0);
            prtype = tCltmr.getPrtype();
            clsacno = tCltmr.getClsacno();
            clssbno = tCltmr.getClssbno();
            clsdtlno = tCltmr.getClsdtlno();
            ebtype = tCltmr.getEbtype();
            pwtype = tCltmr.getPwtype();
            feename = tCltmr.getFeename();
            putname = tCltmr.getPutname();

            sb = new StringBuilder();
            sb.append(formatUtil.padX(code, 6));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(atmcode, 3));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(entpno, 10));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(hentpno, 8));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(scname, 10));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(cdata, 1));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(appdt, 8));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(upddt, 8));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(lputdt, 8));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(llputdt, 8));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(ulputdt, 8));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(ullputdt, 8));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(prtype, 1));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(clsacno, 6));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(clssbno, 4));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(clsdtlno, 4));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(ebtype, 10));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(pwtype, 3));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(feename, 42));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(putname, 6));
            fileContents.add(formatUtil.padX(sb.toString(), 500));
        }
    }

    private void writeErrorClmr() {
        for (ClmrBus tClmr : lClmr) {
            code = tClmr.getCode();
            pbrno = parse.decimal2String(tClmr.getPbrno(), 3, 0);
            vrcode = parse.decimal2String(tClmr.getVrcode(), 4, 0);
            riddup = parse.decimal2String(tClmr.getRiddup(), 1, 0);
            dupcyc = parse.decimal2String(tClmr.getDupcyc(), 3, 0);
            actno = parse.decimal2String(tClmr.getActno(), 12, 0);
            msg1 = parse.decimal2String(tClmr.getMsg1(), 1, 0);
            puttime = parse.decimal2String(tClmr.getPuttime(), 1, 0);
            subfg = parse.decimal2String(tClmr.getSubfg(), 1, 0);
            chktype = tClmr.getChktype();
            chkamt = parse.decimal2String(tClmr.getChkamt(), 1, 0);
            unit = parse.decimal2String(tClmr.getUnit(), 8, 2);
            amtcyc = parse.decimal2String(tClmr.getAmtcyc(), 1, 0);
            amtfg = parse.decimal2String(tClmr.getAmtfg(), 1, 0);
            amt = parse.decimal2String(tClmr.getAmt(), 15, 2);
            cname = tClmr.getCname();
            stop = parse.decimal2String(tClmr.getStop(), 2, 0);
            holdcnt = parse.decimal2String(tClmr.getHoldcnt(), 3, 0);
            holdcnt2 = parse.decimal2String(tClmr.getHoldcnt2(), 3, 0);
            afcbv = parse.decimal2String(tClmr.getAfcbv(), 1, 0);
            netinfo = tClmr.getNetinfo();
            print = parse.decimal2String(tClmr.getPrint(), 2, 0);
            stdate = parse.decimal2String(tClmr.getStdate(), 8, 0);
            sttime = parse.decimal2String(tClmr.getSttime(), 6, 0);
            stopdate = parse.decimal2String(tClmr.getStopdate(), 8, 0);
            stoptime = parse.decimal2String(tClmr.getStoptime(), 6, 0);
            crdb = parse.decimal2String(tClmr.getCrdb(), 1, 0);
            hcode = parse.decimal2String(tClmr.getHcode(), 1, 0);
            lkcode = tClmr.getLkcode();
            flag = tClmr.getFlag();
            otherfield = tClmr.getOtherField();

            sb = new StringBuilder();
            sb.append(formatUtil.padX(code, 6));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(pbrno, 3));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(vrcode, 4));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(riddup, 1));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(dupcyc, 3));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(actno, 12));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(msg1, 1));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(puttime, 1));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(subfg, 1));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(chktype, 2));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(chkamt, 1));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(unit, 11));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(amtcyc, 1));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(amtfg, 1));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(amt, 18));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(cname, 40));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(stop, 2));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(holdcnt, 3));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(holdcnt2, 3));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(afcbv, 1));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(netinfo, 20));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(print, 2));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(stdate, 8));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(sttime, 6));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(stopdate, 8));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(stoptime, 6));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(crdb, 1));
            sb.append(formatUtil.pad9("^", 1));
            sb.append(formatUtil.pad9(hcode, 1));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(lkcode, 6));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(flag, 1));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(otherfield, 500));

            fileContents.add(formatUtil.padX(sb.toString(), 500));
        }
    }

    private void writeErrorCldtl() {
        for (CldtlBus tCldtl : lCldtl) {

            code = tCldtl.getCode();
            amt = parse.decimal2String(tCldtl.getAmt(), 11, 2);
            rcptid = tCldtl.getRcptid();
            txtype = tCldtl.getTxtype();
            cllbr = parse.decimal2String(tCldtl.getCllbr(), 3, 0);
            trmno = parse.decimal2String(tCldtl.getTrmno(), 7, 0);
            txtno = parse.decimal2String(tCldtl.getTxtno(), 8, 0);
            tlrno = tCldtl.getTlrno();
            entdy = parse.decimal2String(tCldtl.getEntdy(), 8, 0);
            time = parse.decimal2String(tCldtl.getTime(), 6, 0);
            lmtdate = parse.decimal2String(tCldtl.getLmtdate(), 8, 0);
            userdata = tCldtl.getUserdata();
            sitdate = parse.decimal2String(tCldtl.getSitdate(), 8, 0);
            caldy = parse.decimal2String(tCldtl.getCaldy(), 8, 0);
            actno = tCldtl.getActno();
            serino = parse.decimal2String(tCldtl.getSerino(), 6, 0);
            crdb = parse.decimal2String(tCldtl.getCrdb(), 1, 0);
            curcd = parse.decimal2String(tCldtl.getCurcd(), 2, 0);
            hcode = parse.decimal2String(tCldtl.getHcode(), 1, 0);
            sourcetp = tCldtl.getSourcetp();
            cactno = tCldtl.getCactno();
            htxseq = parse.decimal2String(tCldtl.getHtxseq(), 15, 0);
            empno = parse.decimal2String(tCldtl.getEmpno(), 6, 0);
            putfg = parse.decimal2String(tCldtl.getPutfg(), 1, 0);
            entfg = parse.decimal2String(tCldtl.getEntfg(), 1, 0);
            sourceip = tCldtl.getSourceip();
            uplfile = tCldtl.getUplfile();
            pbrno = parse.decimal2String(tCldtl.getPbrno(), 3, 0);
            cfee2 = parse.decimal2String(tCldtl.getCfee2(), 3, 2);
            fkd = parse.decimal2String(tCldtl.getFkd(), 1, 0);
            fee = parse.decimal2String(tCldtl.getFee(), 3, 2);
            feecost = parse.decimal2String(tCldtl.getFeecost(), 3, 2);

            sb = new StringBuilder();
            sb.append(formatUtil.padX(code, 6));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(amt, 14));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(rcptid, 26));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(txtype, 2));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(cllbr, 3));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(trmno, 7));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(txtno, 8));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(tlrno, 2));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(entdy, 8));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(time, 6));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(lmtdate, 8));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(userdata, 100));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(sitdate, 8));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(caldy, 8));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(actno, 20));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(serino, 6));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(crdb, 1));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(curcd, 2));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(hcode, 1));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(sourcetp, 3));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(cactno, 16));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(htxseq, 15));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(empno, 6));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(putfg, 1));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(entfg, 1));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(sourceip, 40));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.padX(uplfile, 40));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(pbrno, 3));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(cfee2, 6));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(fkd, 1));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(fee, 6));
            sb.append(formatUtil.padX("^", 1));
            sb.append(formatUtil.pad9(feecost, 6));

            fileContents.add(formatUtil.padX(sb.toString(), 500));
        }
    }

    public void readTmpErrorFile(int function, String tableName, TransactionCase batchTransaction) {
        this.function = function;
        this.tableName = tableName;
        layout = null;
        readFilePath = fileDir + "TempError" + tableName;
        switch (this.tableName) {
            case "CLMR":
                insertErrorClmrDetail = new ArrayList<>();
                layout = new LayoutClmr();
                modifyClmrFromTmpFile(batchTransaction);
                break;
            case "CLTMR":
                insertErrorCltmrDetail = new ArrayList<>();
                layout = new LayoutCltmr();
                modifyCltmrFromTmpFile(batchTransaction);
                break;
            case "CLTOT":
                insertErrorCltotDetail = new ArrayList<>();
                layout = new LayoutCltot();
                modifyCltotFromTmpFile(batchTransaction);
                break;
            case "CLFEE":
                insertErrorClfeeDetail = new ArrayList<>();
                layout = new LayoutClfee();
                modifyClfeeFromTmpFile(batchTransaction);
                break;
            case "CLMC":
                insertErrorClmcDetail = new ArrayList<>();
                layout = new LayoutClmc();
                modifyClmcFromTmpFile(batchTransaction);
                break;
            case "CLBAF":
                insertErrorClbafDetail = new ArrayList<>();
                layout = new LayoutClbaf();
                modifyClbafFromTmpFile(batchTransaction);
                break;
            case "CLCMP":
                insertErrorClcmpDetail = new ArrayList<>();
                layout = new LayoutClcmp();
                modifyClcmpFromTmpFile(batchTransaction);
                break;
            case "CLDMR":
                insertErrorCldmrDetail = new ArrayList<>();
                layout = new LayoutCldmr();
                modifyCldmrFromTmpFile(batchTransaction);
                break;
            case "CLDTL":
                insertErrorCldtlDetail = new ArrayList<>();
                layout = new LayoutCldtl();
                modifyCldtlFromTmpFile(batchTransaction);
                break;
            default:
                break;
        }
    }

    private void modifyClmrFromTmpFile(TransactionCase batchTransaction) {
        if (!textFile.exists(readFilePath)) return;
        nowModifyCnt = 0;
        List<ClmrBus> tmplClmr = new ArrayList<>();
        List<String> lDetail = textFile.readFileContent(readFilePath, "BIG5");
        for (String line : lDetail) {
            String[] values = getValues(line, layout.getColumns(), layout.getDelimiter());
            Map<String, String> m = getKeyMap(layout.getColumns(), values);

            nowModifyCnt++;
            ClmrBus clmrBus = new ClmrBus();
            clmrBus.setCode(m.get("CODE"));
            clmrBus.setPbrno(diffParse.toInt(m.get("PBRNO")));
            clmrBus.setVrcode(diffParse.toInt(m.get("VRCODE")));
            clmrBus.setRiddup(diffParse.toInt(m.get("RIDDUP")));
            clmrBus.setActno(diffParse.toLong(m.get("ACTNO")));
            clmrBus.setMsg1(diffParse.toInt(m.get("MSG1")));
            clmrBus.setPuttime(diffParse.toInt(m.get("PUTTIME")));
            clmrBus.setChktype(diffParse.nvl(m.get("CHKTYPE"), ""));
            clmrBus.setChkamt(diffParse.toInt(m.get("CHKAMT")));
            clmrBus.setUnit(diffParse.toBigDecimal(m.get("UNIT")));
            clmrBus.setAmt(diffParse.toBigDecimal(m.get("AMT")));
            clmrBus.setCname(diffParse.nvl(m.get("CNAME"), ""));
            clmrBus.setStop(diffParse.toInt(m.get("STOP")));
            clmrBus.setAfcbv(diffParse.toInt(m.get("AFCBV")));
            clmrBus.setNetinfo(diffParse.nvl(m.get("NETINFO"), ""));
            clmrBus.setPrint(diffParse.toInt(m.get("PRINT")));
            clmrBus.setStopdate(diffParse.toInt(m.get("STOPDATE")));
            clmrBus.setStoptime(diffParse.toInt(m.get("STOPTIME")));
            clmrBus.setLkcode(diffParse.nvl(m.get("LKCODE"), ""));
            clmrBus.setDupcyc(diffParse.toInt(m.get("DUPCYC")));
            clmrBus.setSubfg(diffParse.toInt(m.get("SUBFG")));
            clmrBus.setAmtcyc(diffParse.toInt(m.get("AMTCYC")));
            clmrBus.setAmtfg(diffParse.toInt(m.get("AMTFG")));
            clmrBus.setHoldcnt(diffParse.toInt(m.get("HOLDCNT")));
            clmrBus.setHoldcnt2(diffParse.toInt(m.get("HOLDCNT2")));
            clmrBus.setStdate(diffParse.toInt(m.get("STDATE")));
            clmrBus.setSttime(diffParse.toInt(m.get("STTIME")));
            clmrBus.setCrdb(diffParse.toInt(m.get("CRDB")));
            clmrBus.setHcode(diffParse.toInt(m.get("HCODE")));
            clmrBus.setFlag(diffParse.nvl(m.get("FLAG"), ""));
            clmrBus.setOtherField(diffParse.nvl(m.get("OTHER_FIELD"), ""));
            ApLogHelper.info(
                    log, false, LogType.NORMAL.getCode(), "values = {}", Arrays.toString(values));

            tmplClmr.add(clmrBus);
            try {
                switch (this.function) {
                    case 1:
                        clmrService.insert(clmrBus);
                        break;
                    case 2:
                        clmrService.update(clmrBus);
                        break;
                    case 4:
                        clmrService.delete(clmrBus);
                        break;
                }
                if (nowModifyCnt >= commitMAXSIZE) {
                    batchTransaction.commit();
                    nowModifyCnt = 0;
                    tmplClmr.clear();
                }
            } catch (Exception e) {
                insertErrorClmrDetail.addAll(tmplClmr);
                nowModifyCnt = 0;
                tmplClmr.clear();
                batchTransaction.rollBack();
            }
        }
        if (nowModifyCnt != 0) {
            batchTransaction.commit();
        }
        textFile.deleteFile(readFilePath);
        String fileName = getWirteFileName();
        writeErrorData(this.tableName, insertErrorClmrDetail, fileName);
        lDetail.clear();
        insertErrorClmrDetail.clear();
    }

    private void modifyCltmrFromTmpFile(TransactionCase batchTransaction) {
        if (!textFile.exists(readFilePath)) return;
        nowModifyCnt = 0;
        List<CltmrBus> tmplCltmr = new ArrayList<>();
        List<String> lDetail = textFile.readFileContent(readFilePath, "BIG5");
        for (String line : lDetail) {
            String[] values = getValues(line, layout.getColumns(), layout.getDelimiter());
            Map<String, String> m = getKeyMap(layout.getColumns(), values);

            nowModifyCnt++;
            CltmrBus cltmrBus = new CltmrBus();
            cltmrBus.setCode(m.get("CODE"));
            cltmrBus.setAtmcode(diffParse.toInt(m.get("ATMCODE")));
            cltmrBus.setEntpno(diffParse.nvl(m.get("ENTPNO"), ""));
            cltmrBus.setHentpno(diffParse.toInt(m.get("HENTPNO")));
            cltmrBus.setScname(diffParse.nvl(m.get("SCNAME"), ""));
            cltmrBus.setCdata(diffParse.toInt(m.get("CDATA")));
            cltmrBus.setAppdt(diffParse.toInt(m.get("APPDT")));
            cltmrBus.setUpddt(diffParse.toInt(m.get("UPDDT")));
            cltmrBus.setLputdt(diffParse.toInt(m.get("LPUTDT")));
            cltmrBus.setLlputdt(diffParse.toInt(m.get("LLPUTDT")));
            cltmrBus.setUlputdt(diffParse.toInt(m.get("ULPUTDT")));
            cltmrBus.setUllputdt(diffParse.toInt(m.get("ULLPUTDT")));
            cltmrBus.setPrtype(diffParse.nvl(m.get("PRTYPE"), ""));
            cltmrBus.setClsacno(diffParse.nvl(m.get("CLSACNO"), ""));
            cltmrBus.setClssbno(diffParse.nvl(m.get("CLSSBNO"), ""));
            cltmrBus.setClsdtlno(diffParse.nvl(m.get("CLSDTLNO"), ""));
            cltmrBus.setEbtype(diffParse.nvl(m.get("EBTYPE"), ""));
            cltmrBus.setPwtype(diffParse.nvl(m.get("PWTYPE"), ""));
            cltmrBus.setFeename(diffParse.nvl(m.get("FEENAME"), ""));
            cltmrBus.setPutname(diffParse.nvl(m.get("PUTNAME"), ""));
            ApLogHelper.info(
                    log, false, LogType.NORMAL.getCode(), "values = {}", Arrays.toString(values));

            tmplCltmr.add(cltmrBus);
            try {
                switch (this.function) {
                    case 1:
                        cltmrService.insert(cltmrBus);
                        break;
                    case 2:
                        cltmrService.update(cltmrBus);
                        break;
                    case 4:
                        cltmrService.delete(cltmrBus);
                        break;
                }
                if (nowModifyCnt >= commitMAXSIZE) {
                    batchTransaction.commit();
                    nowModifyCnt = 0;
                    tmplCltmr.clear();
                }

            } catch (Exception e) {
                insertErrorCltmrDetail.addAll(tmplCltmr);
                nowModifyCnt = 0;
                tmplCltmr.clear();
                batchTransaction.rollBack();
            }
        }
        if (nowModifyCnt != 0) {
            batchTransaction.commit();
        }
        textFile.deleteFile(readFilePath);
        String fileName = getWirteFileName();
        writeErrorData(this.tableName, insertErrorCltmrDetail, fileName);
        lDetail.clear();
        insertErrorCltmrDetail.clear();
    }

    private void modifyCltotFromTmpFile(TransactionCase batchTransaction) {
        if (!textFile.exists(readFilePath)) return;
        nowModifyCnt = 0;
        List<CltotBus> tmplCltot = new ArrayList<>();
        List<String> lDetail = textFile.readFileContent(readFilePath, "BIG5");
        for (String line : lDetail) {
            String[] values = getValues(line, layout.getColumns(), layout.getDelimiter());
            Map<String, String> m = getKeyMap(layout.getColumns(), values);

            nowModifyCnt++;
            CltotBus cltotBus = new CltotBus();
            cltotBus.setCode(m.get("CODE"));
            cltotBus.setRcvamt(diffParse.toBigDecimal(m.get("RCVAMT")));
            cltotBus.setPayamt(diffParse.toBigDecimal(m.get("PAYAMT")));
            cltotBus.setTotcnt(diffParse.toInt(m.get("TOTCNT")));
            cltotBus.setCurcd(diffParse.toInt(m.get("CURCD")));
            ApLogHelper.info(
                    log, false, LogType.NORMAL.getCode(), "values = {}", Arrays.toString(values));

            tmplCltot.add(cltotBus);
            try {
                switch (this.function) {
                    case 1:
                        cltotService.insert(cltotBus);
                        break;
                    case 2:
                        cltotService.update(cltotBus);
                        break;
                    case 4:
                        cltotService.delete(cltotBus);
                        break;
                }
                if (nowModifyCnt >= commitMAXSIZE) {
                    batchTransaction.commit();
                    nowModifyCnt = 0;
                    tmplCltot.clear();
                }

            } catch (Exception e) {
                insertErrorCltotDetail.addAll(tmplCltot);
                nowModifyCnt = 0;
                tmplCltot.clear();
                batchTransaction.rollBack();
            }
        }
        if (nowModifyCnt != 0) {
            batchTransaction.commit();
        }
        textFile.deleteFile(readFilePath);
        String fileName = getWirteFileName();
        writeErrorData(this.tableName, insertErrorCltotDetail, fileName);
        lDetail.clear();
        insertErrorCltotDetail.clear();
    }

    private void modifyClfeeFromTmpFile(TransactionCase batchTransaction) {
        if (!textFile.exists(readFilePath)) return;
        nowModifyCnt = 0;
        List<ClfeeBus> tmplClfee = new ArrayList<>();
        List<String> lDetail = textFile.readFileContent(readFilePath, "BIG5");
        for (String line : lDetail) {
            String[] values = getValues(line, layout.getColumns(), layout.getDelimiter());
            Map<String, String> m = getKeyMap(layout.getColumns(), values);

            nowModifyCnt++;
            ClfeeBus clfeeBus = new ClfeeBus();
            clfeeBus.setKeyCodeFee(m.get("KEY_CODE_FEE"));
            clfeeBus.setCfee1(diffParse.toBigDecimal(m.get("CFEE1")));
            clfeeBus.setCfee2(diffParse.toBigDecimal(m.get("CFEE2")));
            clfeeBus.setCfee3(diffParse.toBigDecimal(m.get("CFEE3")));
            clfeeBus.setCfee4(diffParse.toBigDecimal(m.get("CFEE4")));
            clfeeBus.setFkd(diffParse.toInt(m.get("FKD")));
            clfeeBus.setMfee(diffParse.toBigDecimal(m.get("MFEE")));
            clfeeBus.setCfeeeb(diffParse.toBigDecimal(m.get("CFEEB")));
            clfeeBus.setTxtype(diffParse.nvl(m.get("TXTYPE"), "00"));
            clfeeBus.setStamt(diffParse.toInt(m.get("STAMT")));
            clfeeBus.setFee(diffParse.toBigDecimal(m.get("FEE")));
            clfeeBus.setFeecost(diffParse.toBigDecimal(m.get("FEECOST")));
            clfeeBus.setCfee003(diffParse.toBigDecimal(m.get("CFEE_003")));
            clfeeBus.setFeeCalType(diffParse.toInt(m.get("FEE_CAL_TYPE")));
            ApLogHelper.info(
                    log, false, LogType.NORMAL.getCode(), "values = {}", Arrays.toString(values));

            tmplClfee.add(clfeeBus);
            try {
                switch (this.function) {
                    case 1:
                        clfeeService.insert(clfeeBus);
                        break;
                    case 2:
                        clfeeService.update(clfeeBus);
                        break;
                    case 4:
                        clfeeService.delete(clfeeBus);
                        break;
                }
                if (nowModifyCnt >= commitMAXSIZE) {
                    batchTransaction.commit();
                    nowModifyCnt = 0;
                    tmplClfee.clear();
                }
            } catch (Exception e) {
                insertErrorClfeeDetail.addAll(tmplClfee);
                nowModifyCnt = 0;
                tmplClfee.clear();
                batchTransaction.rollBack();
            }
        }
        if (nowModifyCnt != 0) {
            batchTransaction.commit();
        }
        textFile.deleteFile(readFilePath);
        String fileName = getWirteFileName();
        writeErrorData(this.tableName, insertErrorClfeeDetail, fileName);
        lDetail.clear();
        insertErrorClfeeDetail.clear();
    }

    private void modifyClmcFromTmpFile(TransactionCase batchTransaction) {
        if (!textFile.exists(readFilePath)) return;
        nowModifyCnt = 0;
        List<ClmcBus> tmplClmc = new ArrayList<>();
        List<String> lDetail = textFile.readFileContent(readFilePath, "BIG5");
        for (String line : lDetail) {
            String[] values = getValues(line, layout.getColumns(), layout.getDelimiter());
            Map<String, String> m = getKeyMap(layout.getColumns(), values);

            nowModifyCnt++;
            ClmcBus clmcBus = new ClmcBus();
            clmcBus.setPutname(m.get("PUTNAME"));
            clmcBus.setPutsend(diffParse.nvl(m.get("PUTSEND"), ""));
            clmcBus.setPutform(diffParse.nvl(m.get("PUTFORM"), ""));
            clmcBus.setPuttype(diffParse.toInt(m.get("PUTTYPE")));
            clmcBus.setPutaddr(diffParse.nvl(m.get("PUTADDR"), ""));
            clmcBus.setCyck1(diffParse.toInt(m.get("CYCK1")));
            clmcBus.setCycno1(diffParse.toInt(m.get("CYCNO1")));
            clmcBus.setCyck2(diffParse.toInt(m.get("CYCK2")));
            clmcBus.setCycno2(diffParse.toInt(m.get("CYCNO2")));
            clmcBus.setPutdt(diffParse.toInt(m.get("PUTDT")));
            clmcBus.setMsg2(diffParse.toInt(m.get("MSG2")));
            clmcBus.setTputdt(diffParse.toInt(m.get("TPUTDT")));
            clmcBus.setPutEncode(diffParse.nvl(m.get("PUTENCODE"), ""));
            clmcBus.setPutCompress(diffParse.nvl(m.get("PUTCOMPRESS"), ""));
            clmcBus.setOputtime(diffParse.nvl(m.get("OPUTTIME"), ""));
            clmcBus.setPutdtfg(diffParse.toInt(m.get("PUTDTFG")));
            clmcBus.setUsecnt(diffParse.toInt(m.get("USECNT")));
            ApLogHelper.info(
                    log, false, LogType.NORMAL.getCode(), "values = {}", Arrays.toString(values));

            tmplClmc.add(clmcBus);
            try {
                switch (this.function) {
                    case 1:
                        clmcService.insert(clmcBus);
                        break;
                    case 2:
                        clmcService.update(clmcBus);
                        break;
                    case 4:
                        clmcService.delete(clmcBus);
                        break;
                }
                if (nowModifyCnt >= commitMAXSIZE) {
                    batchTransaction.commit();
                    nowModifyCnt = 0;
                    tmplClmc.clear();
                }
            } catch (Exception e) {
                insertErrorClmcDetail.addAll(tmplClmc);
                nowModifyCnt = 0;
                tmplClmc.clear();
                batchTransaction.rollBack();
            }
        }
        if (nowModifyCnt != 0) {
            batchTransaction.commit();
        }
        textFile.deleteFile(readFilePath);
        String fileName = getWirteFileName();
        writeErrorData(this.tableName, insertErrorClmcDetail, fileName);
        lDetail.clear();
        insertErrorClmcDetail.clear();
    }

    private void modifyClbafFromTmpFile(TransactionCase batchTransaction) {
        if (!textFile.exists(readFilePath)) return;
        nowModifyCnt = 0;
        List<ClbafBus> tmplClbaf = new ArrayList<>();
        List<String> lDetail = textFile.readFileContent(readFilePath, "BIG5");
        for (String line : lDetail) {
            String[] values = getValues(line, layout.getColumns(), layout.getDelimiter());
            Map<String, String> m = getKeyMap(layout.getColumns(), values);

            nowModifyCnt++;
            ClbafBus clbafBus = new ClbafBus();
            clbafBus.setCllbr(diffParse.toInt(m.get("CLLBR")));
            clbafBus.setEntdy(diffParse.toInt(m.get("ENTDY")));
            clbafBus.setCode(m.get("CODE"));
            clbafBus.setPbrno(diffParse.toInt(m.get("PBRNO")));
            clbafBus.setCrdb(diffParse.toInt(m.get("CRDB")));
            clbafBus.setTxtype(diffParse.nvl(m.get("TXTYPE"), " "));
            clbafBus.setCnt(diffParse.toInt(m.get("CNT")));
            clbafBus.setAmt(diffParse.toBigDecimal(m.get("AMT")));
            clbafBus.setCfee2(diffParse.toBigDecimal(m.get("CFEE2")));
            clbafBus.setKfee(diffParse.toBigDecimal(m.get("KFEE")));
            clbafBus.setCurcd(diffParse.toInt(m.get("CURCD")));
            ApLogHelper.info(
                    log, false, LogType.NORMAL.getCode(), "values = {}", Arrays.toString(values));
            tmplClbaf.add(clbafBus);
            try {
                switch (this.function) {
                    case 1:
                        clbafService.insert(clbafBus);
                        break;
                    case 2:
                        clbafService.update(clbafBus);
                        break;
                    case 4:
                        clbafService.delete(clbafBus);
                        break;
                }
                if (nowModifyCnt >= commitMAXSIZE) {
                    batchTransaction.commit();
                    nowModifyCnt = 0;
                    tmplClbaf.clear();
                }

            } catch (Exception e) {
                insertErrorClbafDetail.addAll(tmplClbaf);
                nowModifyCnt = 0;
                tmplClbaf.clear();
                batchTransaction.rollBack();
            }
        }
        if (nowModifyCnt != 0) {
            batchTransaction.commit();
        }
        textFile.deleteFile(readFilePath);
        String fileName = getWirteFileName();
        writeErrorData(this.tableName, insertErrorClbafDetail, fileName);
        lDetail.clear();
        insertErrorClbafDetail.clear();
    }

    private void modifyClcmpFromTmpFile(TransactionCase batchTransaction) {
        if (!textFile.exists(readFilePath)) return;
        List<String> lDetail = textFile.readFileContent(readFilePath, "BIG5");
        nowModifyCnt = 0;
        List<ClcmpBus> tmplClcmp = new ArrayList<>();
        for (String line : lDetail) {
            String[] values = getValues(line, layout.getColumns(), layout.getDelimiter());
            Map<String, String> m = getKeyMap(layout.getColumns(), values);
            nowModifyCnt++;
            ClcmpBus clcmpBus = new ClcmpBus();
            clcmpBus.setCode(m.get("CODE"));
            clcmpBus.setRcptid(diffParse.nvl(m.get("RCPTID"), ""));
            clcmpBus.setPname(diffParse.nvl(m.get("PNAME"), ""));
            clcmpBus.setAmt(diffParse.toBigDecimal(m.get("AMT")));
            clcmpBus.setSdate(diffParse.toInt(m.get("SDATE")));
            clcmpBus.setStime(diffParse.toInt(m.get("STIME")));
            clcmpBus.setLdate(diffParse.toInt(m.get("LDATE")));
            clcmpBus.setLtime(diffParse.toInt(m.get("LTIME")));
            clcmpBus.setKdate(diffParse.toInt(m.get("KDATE")));
            clcmpBus.setLflg(diffParse.toInt(m.get("LFLG")));
            clcmpBus.setCdate(diffParse.toInt(m.get("CDATE")));
            clcmpBus.setUdate(diffParse.toInt(m.get("UDATE")));
            clcmpBus.setUtime(diffParse.toInt(m.get("UTIME")));
            clcmpBus.setKinbr(diffParse.toInt(m.get("KINBR")));
            clcmpBus.setTlrno(diffParse.nvl(m.get("TLRNO"), ""));
            clcmpBus.setId(diffParse.nvl(m.get("ID"), ""));
            clcmpBus.setCurcd(diffParse.toInt(m.get("CURCD")));
            clcmpBus.setEmpno(diffParse.nvl(m.get("EMPNO"), ""));
            clcmpBus.setOtflag(diffParse.nvl(m.get("OTFLAG"), ""));
            ApLogHelper.info(
                    log, false, LogType.NORMAL.getCode(), "values = {}", Arrays.toString(values));

            tmplClcmp.add(clcmpBus);
            try {
                switch (this.function) {
                    case 1:
                        clcmpService.insert(clcmpBus);
                        break;
                    case 2:
                        clcmpService.update(clcmpBus);
                        break;
                    case 4:
                        clcmpService.delete(clcmpBus);
                        break;
                }
                if (nowModifyCnt >= commitMAXSIZE) {
                    batchTransaction.commit();
                    nowModifyCnt = 0;
                    tmplClcmp.clear();
                }
            } catch (Exception e) {
                insertErrorClcmpDetail.addAll(tmplClcmp);
                nowModifyCnt = 0;
                tmplClcmp.clear();
                batchTransaction.rollBack();
            }
        }
        if (nowModifyCnt != 0) {
            batchTransaction.commit();
        }
        textFile.deleteFile(readFilePath);
        String fileName = getWirteFileName();
        writeErrorData(this.tableName, insertErrorClcmpDetail, fileName);
        lDetail.clear();
        insertErrorClcmpDetail.clear();
    }

    private void modifyCldmrFromTmpFile(TransactionCase batchTransaction) {
        if (!textFile.exists(readFilePath)) return;
        List<String> lDetail = textFile.readFileContent(readFilePath, "BIG5");
        nowModifyCnt = 0;
        List<CldmrBus> tmplCldmr = new ArrayList<>();
        for (String line : lDetail) {
            String[] values = getValues(line, layout.getColumns(), layout.getDelimiter());
            Map<String, String> m = getKeyMap(layout.getColumns(), values);
            nowModifyCnt++;
            CldmrBus cldmrBus = new CldmrBus();
            cldmrBus.setCode(m.get("CODE"));
            cldmrBus.setRcptid(diffParse.nvl(m.get("RCPTID"), ""));
            cldmrBus.setPname(diffParse.nvl(m.get("PNAME"), ""));
            cldmrBus.setBal(diffParse.toBigDecimal(m.get("BAL")));
            cldmrBus.setLdate(diffParse.toInt(m.get("LDATE")));
            cldmrBus.setLtime(diffParse.toInt(m.get("LTIME")));
            cldmrBus.setKdate(diffParse.toInt(m.get("KDATE")));
            cldmrBus.setLflg(diffParse.toInt(m.get("LFLG")));
            cldmrBus.setCdate(diffParse.toInt(m.get("CDATE")));
            cldmrBus.setUdate(diffParse.toInt(m.get("UDATE")));
            cldmrBus.setUtime(diffParse.toInt(m.get("UTIME")));
            cldmrBus.setKinbr(diffParse.toInt(m.get("KINBR")));
            cldmrBus.setTlrno(diffParse.nvl(m.get("TLRNO"), ""));
            cldmrBus.setEmpno(diffParse.nvl(m.get("EMPNO"), ""));
            cldmrBus.setId(diffParse.nvl(m.get("ID"), ""));
            cldmrBus.setCurcd(diffParse.toInt(m.get("CURCD")));
            cldmrBus.setEmpno(diffParse.nvl(m.get("EMPNO"), ""));
            ApLogHelper.info(
                    log, false, LogType.NORMAL.getCode(), "values = {}", Arrays.toString(values));

            tmplCldmr.add(cldmrBus);
            try {
                switch (this.function) {
                    case 1:
                        cldmrService.insert(cldmrBus);
                        break;
                    case 2:
                        cldmrService.update(cldmrBus);
                        break;
                    case 4:
                        cldmrService.delete(cldmrBus);
                        break;
                }
                if (nowModifyCnt >= commitMAXSIZE) {
                    batchTransaction.commit();
                    nowModifyCnt = 0;
                    tmplCldmr.clear();
                }
            } catch (Exception e) {
                insertErrorCldmrDetail.addAll(tmplCldmr);
                nowModifyCnt = 0;
                tmplCldmr.clear();
                batchTransaction.rollBack();
            }
        }
        if (nowModifyCnt != 0) {
            batchTransaction.commit();
        }
        textFile.deleteFile(readFilePath);
        String fileName = getWirteFileName();
        writeErrorData(this.tableName, insertErrorCldmrDetail, fileName);
        lDetail.clear();
        insertErrorCldmrDetail.clear();
    }

    private void modifyCldtlFromTmpFile(TransactionCase batchTransaction) {
        if (!textFile.exists(readFilePath)) return;
        nowModifyCnt = 0;
        List<CldtlBus> tmplCldtl = new ArrayList<>();
        List<String> lDetail = textFile.readFileContent(readFilePath, "BIG5");
        for (String line : lDetail) {
            String[] values = getValues(line, layout.getColumns(), layout.getDelimiter());
            Map<String, String> m = getKeyMap(layout.getColumns(), values);

            nowModifyCnt++;
            CldtlBus cldtlBus = new CldtlBus();
            CldtlId cldtlId =
                    new CldtlId(
                            m.get("CODE"),
                            m.get("RCPTID"),
                            diffParse.toInt(m.get("ENTDY")),
                            diffParse.toInt(m.get("TRMNO")),
                            diffParse.toInt(m.get("TXTNO")));
            cldtlBus.setCode(m.get("CODE"));
            cldtlBus.setAmt(diffParse.toBigDecimal(m.get("AMT")));
            cldtlBus.setRcptid(diffParse.nvl(m.get("RCPTID").replaceAll("\\s+$", ""), ""));
            cldtlBus.setTxtype(diffParse.nvl(m.get("TXTYPE"), ""));
            cldtlBus.setCllbr(diffParse.toInt(m.get("CLLBR")));
            cldtlBus.setTrmno(diffParse.toInt(m.get("TRMNO")));
            cldtlBus.setTlrno(diffParse.nvl(m.get("TLRNO"), ""));
            cldtlBus.setTime(diffParse.toInt(m.get("TIME")));
            cldtlBus.setLmtdate(diffParse.toInt(m.get("LMTDATE")));
            cldtlBus.setUserdata(diffParse.nvl(m.get("USERDATA"), ""));
            cldtlBus.setSitdate(diffParse.toInt(m.get("SITDATE")));
            cldtlBus.setCaldy(diffParse.toInt(m.get("CALDY")));
            cldtlBus.setActno(diffParse.nvl(m.get("ACTNO"), ""));
            cldtlBus.setSerino(diffParse.toInt(m.get("SERINO")));
            cldtlBus.setCrdb(diffParse.toInt(m.get("CRDB")));
            cldtlBus.setEntdy(diffParse.toInt(m.get("ENTDY")));
            cldtlBus.setTxtno(diffParse.toInt(m.get("TXTNO")));
            cldtlBus.setCurcd(diffParse.toInt(m.get("CURCD")));
            cldtlBus.setHcode(diffParse.toInt(m.get("HCODE")));
            cldtlBus.setSourcetp(diffParse.nvl(m.get("SOURCETP"), ""));
            cldtlBus.setCactno(diffParse.nvl(m.get("CACTNO"), ""));
            cldtlBus.setHtxseq(diffParse.toLong(m.get("HTXSEQ")));
            cldtlBus.setEmpno(diffParse.nvl(m.get("EMPNO"), ""));
            cldtlBus.setPutfg(diffParse.toInt(m.get("PUTFG")));
            cldtlBus.setEntfg(diffParse.toInt(m.get("ENTFG")));
            cldtlBus.setSourceip(diffParse.nvl(m.get("SOURCEIP"), ""));
            cldtlBus.setUplfile(diffParse.nvl(m.get("UPLFILE"), ""));
            cldtlBus.setPbrno(diffParse.toInt(m.get("PBRNO")));
            cldtlBus.setCfee2(diffParse.toBigDecimal(m.get("CFEE2")));
            cldtlBus.setFkd(diffParse.toInt(m.get("FKD")));
            cldtlBus.setFee(diffParse.toBigDecimal(m.get("FEE")));
            cldtlBus.setFeecost(diffParse.toBigDecimal(m.get("FEECOST")));
            ApLogHelper.info(
                    log, false, LogType.NORMAL.getCode(), "values = {}", Arrays.toString(values));

            tmplCldtl.add(cldtlBus);
            try {
                switch (this.function) {
                    case 1:
                        cldtlService.insert(cldtlBus);
                        break;
                    case 2:
                        cldtlService.update(cldtlBus);
                        break;
                    case 4:
                        cldtlService.delete(cldtlBus);
                        break;
                }
                if (nowModifyCnt >= commitMAXSIZE) {
                    batchTransaction.commit();
                    nowModifyCnt = 0;
                    tmplCldtl.clear();
                }
            } catch (Exception e) {
                ApLogHelper.error(
                        log,
                        false,
                        LogType.NORMAL.getCode(),
                        "update failed,cldtlId= {} error={}",
                        cldtlId,
                        e.getMessage());
                insertErrorCldtlDetail.addAll(tmplCldtl);
                nowModifyCnt = 0;
                tmplCldtl.clear();
                batchTransaction.rollBack();
            }
        }
        if (nowModifyCnt != 0) {
            batchTransaction.commit();
        }
        textFile.deleteFile(readFilePath);
        String fileName = getWirteFileName();
        writeErrorData(this.tableName, insertErrorCldtlDetail, fileName);
        lDetail.clear();
        insertErrorCldtlDetail.clear();
    }

    private void writeFile(String fildName) {
        String writePath = fileDir + fildName;
        try {
            textFile.writeFileContent(writePath, fileContents, CHARSET);
            fileContents.clear();
        } catch (LogicException e) {
            moveErrorResponse(e);
        }
    }

    private String[] getValues(String line, List<Column> columns, String delimiter) {
        return loadDataCom.parseLine(line, columns, delimiter);
    }

    private Map<String, String> getKeyMap(List<Column> columns, String[] values) {
        return loadDataCom.createKeyMap(columns, values);
    }

    private void moveErrorResponse(LogicException e) {
        // event.setPeripheryRequest();
    }

    private String getWirteFileName() {
        String fileName = "";
        switch (this.function) {
            case 1 -> {
                fileName = "InsertError" + this.tableName;
            }
            case 2 -> {
                fileName = "UpdateError" + this.tableName;
            }
            case 4 -> {
                fileName = "DeleteError" + this.tableName;
            }
        }
        return fileName;
    }
}
