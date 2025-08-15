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
@Component("CONV121444_I")
@Scope("prototype")
public class CONV121444_I extends RequestBaseSvc implements RequestSvcCase {
    private RequestLabel label;
}
