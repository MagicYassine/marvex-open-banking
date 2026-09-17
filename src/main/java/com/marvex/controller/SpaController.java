package com.marvex.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    @GetMapping(value = {"/", "/login", "/register", "/dashboard", "/payment-qr", "/pay/**", "/mock-bank/**", "/complete-registration", "/forgot-password", "/reset-password", "/my-account", "/terms", "/privacy"})
    public String spa() {
        return "forward:/index.html";
    }
}
