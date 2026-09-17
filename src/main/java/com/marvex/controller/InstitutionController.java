package com.marvex.controller;

import com.marvex.service.InstitutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/institutions")
@RequiredArgsConstructor
@Slf4j
public class InstitutionController {

    private final InstitutionService institutionService;

    @GetMapping
    public ResponseEntity<String> list() {
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(institutionService.getInstitutionsJson());
    }
}
