/* (C) 2024 */
package com.bot.ncl.mapper;

import com.bot.ncl.adapter.in.api.CL_BATCH_I;
import com.bot.ncl.adapter.in.svc.TF001_SVC_CLBAF_I;
import com.bot.ncl.adapter.in.svc.TF001_SVC_CLCMP_I;
import com.bot.ncl.adapter.in.svc.TF001_SVC_CLDMR_I;
import com.bot.ncl.adapter.in.svc.TF001_SVC_CLDTL_I;
import com.bot.ncl.adapter.in.svc.TF001_SVC_CLFEE_I;
import com.bot.ncl.adapter.in.svc.TF001_SVC_CLMC_I;
import com.bot.ncl.adapter.in.svc.TF001_SVC_CLTMR_I;
import com.bot.ncl.adapter.in.svc.TF001_SVC_CLTOT_I;
import com.bot.ncl.adapter.in.svc.TF001_SVC_I;
import com.bot.txcontrol.mapper.MapperCase;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public interface MapperTF001 extends MapperCase {

    TF001_SVC_I mapperTF001_SVC(CL_BATCH_I cL_BATCH_I);

    TF001_SVC_CLBAF_I mapperTF001_SVC_CLBAF(CL_BATCH_I cL_BATCH_I);

    TF001_SVC_CLCMP_I mapperTF001_SVC_CLCMP(CL_BATCH_I cL_BATCH_I);

    TF001_SVC_CLDMR_I mapperTF001_SVC_CLDMR(CL_BATCH_I cL_BATCH_I);

    TF001_SVC_CLDTL_I mapperTF001_SVC_CLDTL(CL_BATCH_I cL_BATCH_I);

    TF001_SVC_CLFEE_I mapperTF001_SVC_CLFEE(CL_BATCH_I cL_BATCH_I);

    TF001_SVC_CLMC_I mapperTF001_SVC_CLMC(CL_BATCH_I cL_BATCH_I);

    TF001_SVC_CLTMR_I mapperTF001_SVC_CLTMR(CL_BATCH_I cL_BATCH_I);

    TF001_SVC_CLTOT_I mapperTF001_SVC_CLTOT(CL_BATCH_I cL_BATCH_I);
}
