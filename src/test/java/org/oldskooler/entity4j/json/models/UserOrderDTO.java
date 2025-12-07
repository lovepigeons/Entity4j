package org.oldskooler.entity4j.json.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserOrderDTO {
    private Integer userId;
    private String userName;
    private Double rating;
    private Integer orderId;
    private Double orderTotal;
    private LocalDateTime placedAt;
}
