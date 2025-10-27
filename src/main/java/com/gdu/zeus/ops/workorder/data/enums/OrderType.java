package com.gdu.zeus.ops.workorder.data.enums;

public enum OrderType {
    ILLEGAL_CONSTRUCTION("违法建设巡查"),
    AERIAL_PATROL("空中巡查"),
    FOREST_FIRE_PREVENTION("护林防火"),
    ATMOSPHERIC_DETECTION("大气探测"),
    AERIAL_PHOTOGRAPHY("航空摄影"),
    AERIAL_PHOTO("空中拍照"),
    SURVEYING("测绘"),
    OTHER("其它");

    private String description;

    OrderType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
