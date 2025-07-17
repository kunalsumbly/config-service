package com.polarbookshop.configservice.dto;

import java.util.List;

/**
 * Request model for the rotate-keys endpoint
 */
public class RotateKeysRequest {
    private List<String> rotate;

    public List<String> getRotate() {
        return rotate;
    }

    public void setRotate(List<String> rotate) {
        this.rotate = rotate;
    }
}