/* (C) 2023 */
package com.bot.ncl.adapter.in.svc;

import com.bot.txcontrol.adapter.RequestSvcCase;
import com.bot.txcontrol.adapter.in.RequestBaseSvc;
import com.bot.txcontrol.adapter.in.RequestLabel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Component("TF001_SVC_I")
@Scope("prototype")
public class TF001_SVC_I extends RequestBaseSvc implements RequestSvcCase {
    private RequestLabel label;
}
