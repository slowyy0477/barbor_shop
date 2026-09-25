package com.ayan.salon.server.service;

/** Provider boundary for OTP delivery. Implementations must never persist or log the code. */
public interface OtpDeliveryGateway {
    void send(String canonicalPhone, String code);
}
