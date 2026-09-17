package com.marvex.constants;

public final class EmailTemplates {

    private EmailTemplates() {}

    public static final String VERIFICATION_SUBJECT = "Complete your marvex registration";
    public static final String VERIFICATION_BODY =
        "Welcome to marvex!\n\nClick the link below to complete your registration and set up your payment details:\n\n%s\n\nThis link is valid for 24 hours.";

    public static final String PASSWORD_RESET_SUBJECT = "Reset your marvex password";
    public static final String PASSWORD_RESET_BODY =
        "Click the link below to reset your password:\n\n%s\n\nThis link is valid for 1 hour. If you didn't request this, ignore this email.";
}
