package com.evanp.f1.api.dto;

import java.util.List;

public record SessionResetResponse(List<String> clearedKeys, boolean reingestTriggered) {}
