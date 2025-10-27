package com.gdu.zeus.ops.workorder.data.enums;

public enum PatrolResult {
    PHOTO("照片"), VIDEO("视频");
    private final String description;
    PatrolResult(String description) { this.description = description; }
    public String getDescription() {
        return description;
    }
}
