/* (C) 2023 */
package com.bot.ncl.adapter.in.api;

import com.bot.txcontrol.adapter.RequestApiLabelCase;
import com.bot.txcontrol.adapter.RequestCase;
import com.bot.txcontrol.adapter.in.RequestBaseApi;
import com.bot.txcontrol.config.CustomSchema;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Component("BT002.CLBH.I")
@Scope("prototype")
public class BT002_CLBH_I extends RequestBaseApi implements RequestCase {
    @CustomSchema(order = 0, schema = @Schema(name = "REQ_LABEL_FAS", description = "LABEL4FAS"))
    @JsonProperty("LABEL")
    private RequestApiLabelCase label;
}
