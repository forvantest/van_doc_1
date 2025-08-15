/* (C) 2024 */
package com.bot.ncl.mapper;

import com.bot.ncl.adapter.in.api.CL_BATCH_I;
import com.bot.ncl.adapter.in.svc.REMAIL_I;
import com.bot.ncl.adapter.in.svc.STAT6_SET_EMAIL_I;
import com.bot.txcontrol.mapper.MapperCase;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public interface MapperSTAT6_OKRTN extends MapperCase {

    STAT6_SET_EMAIL_I mapperSTAT6_SET_EMAIL(CL_BATCH_I cL_BATCH_I);

    REMAIL_I mapperREMAIL(CL_BATCH_I cL_BATCH_I);
}
