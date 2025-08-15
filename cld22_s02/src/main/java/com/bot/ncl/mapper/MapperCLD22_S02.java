/* (C) 2024 */
package com.bot.ncl.mapper;

import com.bot.ncl.adapter.in.api.CL_BATCH_I;
import com.bot.ncl.adapter.in.svc.OUTCONVAC__I;
import com.bot.ncl.adapter.in.svc.OUTCONVBAF_I;
import com.bot.txcontrol.mapper.MapperCase;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public interface MapperCLD22_S02 extends MapperCase {

    OUTCONVBAF_I mapperOUTCONVBAF(CL_BATCH_I cl_batch_i);

    OUTCONVAC__I mapperOUTCONVAC(CL_BATCH_I cl_batch_i);
}
