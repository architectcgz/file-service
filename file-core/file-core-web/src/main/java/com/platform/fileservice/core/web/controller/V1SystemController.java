package com.platform.fileservice.core.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Minimal controller used to prove the web module boots independently.
 */
@RestController
@RequestMapping("/api/v1/system")
public class V1SystemController {

    @GetMapping("/ping")
    public Map<String, String> ping() {
        return Map.of(
                "module", "file-core-web",
                "status", "ok"
        );
    }
}
