package com.brogrammers.open_mic_hub_service.booking.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CallBackRequest {
    private String pidx;
    private String transaction_id;
    private String tidx;
    private Double total_amount;
    private Double amount;
    private String status;
    private String mobile;
    private String purchase_order_id;
    private String purchase_order_name;
}
