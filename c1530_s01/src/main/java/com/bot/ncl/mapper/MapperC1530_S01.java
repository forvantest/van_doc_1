/* (C) 2024 */
package com.bot.ncl.mapper;

import com.bot.ncl.adapter.in.api.CL_BATCH_I;
import com.bot.ncl.adapter.in.svc.EX_KPUTH1_I;
import com.bot.ncl.adapter.in.svc.EX_SORTKPUTH_I;
import com.bot.ncl.adapter.in.svc.PUTH__I;
import com.bot.txcontrol.mapper.MapperCase;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public interface MapperC1530_S01 extends MapperCase {

    PUTH__I mapperPUTH(CL_BATCH_I cl_batch_i);

    EX_KPUTH1_I mapperEX_KPUTH1(CL_BATCH_I cl_batch_i);

    EX_SORTKPUTH_I mapperEX_SORTKPUTH(CL_BATCH_I cl_batch_i);
}
