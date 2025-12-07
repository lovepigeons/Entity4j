package org.oldskooler.entity4j.json.models;

import lombok.Data;

@Data
public class User {
    private Integer id;
    private String name;
    private String status;
    private Double rating;
}
