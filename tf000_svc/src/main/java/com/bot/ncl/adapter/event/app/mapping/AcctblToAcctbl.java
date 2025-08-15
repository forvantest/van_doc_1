/* (C) 2024 */
package com.bot.ncl.adapter.event.app.mapping;

import com.bot.ncl.adapter.event.app.util.AppendUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Scope("prototype")
public class AcctblToAcctbl {
    private static final Logger logger = LogManager.getLogger();
    private final String COMMA = ",";
    private final String EMPTY = "";

    AppendUtil appendUtil = new AppendUtil();

    public void mapping(List<Map<String, String>> list) throws Exception {
        List<String> data = new ArrayList<>();
        int count = 0;
        for (Map<String, String> map : list) {
            StringBuilder sb1 = new StringBuilder("INSERT INTO ACCTBL ( ");
            StringBuilder sb2 = new StringBuilder(" VALUES ( ");

            appendUtil.append("ACNO", "ACNO", map, sb1, sb2, COMMA);
            appendUtil.append("SBNO", "SBNO", map, sb1, sb2, COMMA);
            appendUtil.append("DTLNO", "DTLNO", map, sb1, sb2, COMMA);
            appendUtil.append("CNAME", "CNAME", map, sb1, sb2, COMMA);
            appendUtil.append("CRDB", "CRDB", map, sb1, sb2, COMMA);
            appendUtil.append("SBCD", "SBCD", map, sb1, sb2, COMMA);
            appendUtil.append("DTLCD", "DTLCD", map, sb1, sb2, COMMA);
            appendUtil.append("SBAF", "SBAF", map, sb1, sb2, COMMA);
            appendUtil.append("RVSCD", "RVSCD", map, sb1, sb2, COMMA);
            appendUtil.append("RVSKIND", "RVSKIND", map, sb1, sb2, COMMA);
            appendUtil.append("TRTYPE", "TRTYPE", map, sb1, sb2, COMMA);
            appendUtil.append("BGTCTL", "BGTCTL", map, sb1, sb2, COMMA);
            appendUtil.append("CLSTYPE", "CLSTYPE", map, sb1, sb2, COMMA);
            appendUtil.append("SHARE", "AMORTIZATION", map, sb1, sb2, COMMA);
            appendUtil.append("TAXCD", "TAXCD", map, sb1, sb2, COMMA);
            appendUtil.append("BRCHK1", "BRCHK1", map, sb1, sb2, COMMA);
            appendUtil.append("BRCHK2", "BRCHK2", map, sb1, sb2, COMMA);
            appendUtil.append("BRCHK3", "BRCHK3", map, sb1, sb2, COMMA);
            appendUtil.append("BRCHK4", "BRCHK4", map, sb1, sb2, COMMA);
            appendUtil.append("BRCHK5", "BRCHK5", map, sb1, sb2, COMMA);
            appendUtil.append("BRCHK6", "BRCHK6", map, sb1, sb2, COMMA);
            appendUtil.append("BRCHK7", "BRCHK7", map, sb1, sb2, COMMA);
            appendUtil.append("BRCHK8", "BRCHK8", map, sb1, sb2, COMMA);
            appendUtil.append("BRCHK9", "BRCHK9", map, sb1, sb2, COMMA);
            appendUtil.append("BRCHK10", "BRCHK10", map, sb1, sb2, COMMA);
            appendUtil.append("BRCHK11", "BRCHK11", map, sb1, sb2, COMMA);
            appendUtil.append("BRCHK12", "BRCHK12", map, sb1, sb2, COMMA);
            appendUtil.append("STOP", "STOP", map, sb1, sb2, COMMA);
            appendUtil.append("LTXDATE", "LTXDATE", map, sb1, sb2, COMMA);
            appendUtil.append("STAXCD", "STAXCD", map, sb1, sb2, COMMA);
            appendUtil.append("34CD", "CODE34", map, sb1, sb2, COMMA);
            appendUtil.append("SELCD", "SELCD", map, sb1, sb2, COMMA);
            appendUtil.append("ENAME", "ENAME", map, sb1, sb2, COMMA);
            appendUtil.append("OSBAF", "OSBAF", map, sb1, sb2, COMMA);
            appendUtil.append("ORVSCD", "ORVSCD", map, sb1, sb2, EMPTY); // 最後一筆

            sb1.append(") ");
            sb2.append(") ;");
            sb1.append(sb2);
            data.add(sb1.toString());
            count++;
        }
    }
}
