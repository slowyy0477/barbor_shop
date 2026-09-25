package com.ayan.salon.server.config;

import com.ayan.salon.server.service.OtpDeliveryGateway;
import com.ayan.salon.server.service.SmsDeliveryClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * OTP delivery is deliberately pluggable. Development logs a code for local QA;
 * production posts to the configured provider and fails closed when it is absent.
 */
@Configuration
public class OtpConfiguration {
    @Bean
    @Profile({"dev", "test"})
    OtpDeliveryGateway developmentOtpGateway() {
        return (phone, code) -> org.slf4j.LoggerFactory.getLogger("AyanOtpDev")
                .warn("Development OTP generated for {}: {} (never enable this profile in production)", phone, code);
    }

    /**
     * Laptop/server-owner mode. A salon that runs this server on its own machine
     * has no SMS contract, so the verification code is written to a private file
     * on that machine and to the server log. The owner reads the code to the
     * customer who is standing at the counter. Switch this off (drop the "local"
     * profile) as soon as a real provider is connected.
     */
    @Bean
    @Profile("local")
    OtpDeliveryGateway localOtpGateway(
            @Value("${ayan.auth.otp.inbox-path:}") String inboxPath,
            @Value("${ayan.auth.otp.sender:Ayan Salon}") String sender,
            @Value("${ayan.sms.provider:}") String smsProvider,
            @Value("${ayan.sms.twilio.account-sid:}") String twilioSid,
            @Value("${ayan.sms.twilio.auth-token:}") String twilioToken,
            @Value("${ayan.sms.twilio.from-number:}") String twilioFrom,
            @Value("${ayan.sms.whatsapp.token:}") String whatsappToken,
            @Value("${ayan.sms.whatsapp.phone-number-id:}") String whatsappPhoneId,
            @Value("${ayan.sms.webhook.url:}") String webhookUrl,
            @Value("${ayan.sms.webhook.token:}") String webhookToken) {
        LocalInboxOtpGateway inbox = new LocalInboxOtpGateway(inboxPath);
        SmsDeliveryClient sms = SmsDeliveryClient.from(smsProvider,
                SmsDeliveryClient.Config.from(twilioSid, twilioToken, twilioFrom, whatsappToken,
                        whatsappPhoneId, webhookUrl, webhookToken));
        // Until a provider is connected the code is written to this machine only.
        // Once it is connected the customer receives a real message and the file
        // stays behind as the counter-side safety net.
        if (!sms.isConfigured()) return inbox;
        return new SmsWithLocalFallbackOtpGateway(new SmsOtpDeliveryGateway(sms, sender), inbox);
    }

    @Bean
    @Profile("!dev & !test & !local")
    OtpDeliveryGateway productionOtpGateway(
            RestClient.Builder client,
            @Value("${ayan.auth.otp.provider-url:}") String providerUrl,
            @Value("${ayan.auth.otp.provider-token:}") String providerToken,
            @Value("${ayan.auth.otp.sender:Ayan Salon}") String sender,
            @Value("${ayan.sms.provider:}") String smsProvider,
            @Value("${ayan.sms.twilio.account-sid:}") String twilioSid,
            @Value("${ayan.sms.twilio.auth-token:}") String twilioToken,
            @Value("${ayan.sms.twilio.from-number:}") String twilioFrom,
            @Value("${ayan.sms.whatsapp.token:}") String whatsappToken,
            @Value("${ayan.sms.whatsapp.phone-number-id:}") String whatsappPhoneId,
            @Value("${ayan.sms.webhook.url:}") String webhookUrl,
            @Value("${ayan.sms.webhook.token:}") String webhookToken) {
        // A connected SMS/WhatsApp provider always wins, because a hosted salon
        // has no machine on site where a code file could be read out.
        SmsDeliveryClient sms = SmsDeliveryClient.from(smsProvider,
                SmsDeliveryClient.Config.from(twilioSid, twilioToken, twilioFrom, whatsappToken,
                        whatsappPhoneId, webhookUrl, webhookToken));
        if (sms.isConfigured()) return new SmsOtpDeliveryGateway(sms, sender);
        return new HttpOtpDeliveryGateway(client, providerUrl, providerToken, sender);
    }

    /**
     * Sends the sign-in code as a real SMS/WhatsApp message. The code is never
     * logged, and a provider outage stays a hard failure so the customer is told
     * the message could not be sent instead of waiting for a code that never comes.
     */
    static final class SmsOtpDeliveryGateway implements OtpDeliveryGateway {
        private final SmsDeliveryClient sms;
        private final String sender;

        SmsOtpDeliveryGateway(SmsDeliveryClient sms, String sender) {
            this.sms = sms;
            this.sender = sender == null || sender.isBlank() ? "Ayan Salon" : sender.trim();
        }

        @Override
        public void send(String canonicalPhone, String code) {
            sms.send(canonicalPhone, sender + ": your sign-in code is " + code
                    + ". It expires in 5 minutes. Do not share this code with anyone.");
        }
    }

    /**
     * Real message first, on-machine code file second. A customer standing at the
     * counter must never be stuck because the provider had a bad minute, and the
     * owner can always read the code out loud.
     */
    static final class SmsWithLocalFallbackOtpGateway implements OtpDeliveryGateway {
        private static final org.slf4j.Logger LOG =
                org.slf4j.LoggerFactory.getLogger("AyanOtpFallback");
        private final OtpDeliveryGateway primary;
        private final OtpDeliveryGateway fallback;

        SmsWithLocalFallbackOtpGateway(OtpDeliveryGateway primary, OtpDeliveryGateway fallback) {
            this.primary = primary;
            this.fallback = fallback;
        }

        @Override
        public void send(String canonicalPhone, String code) {
            try {
                primary.send(canonicalPhone, code);
            } catch (RuntimeException failure) {
                // Never log the code itself, only the fact that the fallback ran.
                LOG.warn("The sign-in message provider rejected the request ({}); falling back to the "
                        + "on-machine code file for {}", failure.getClass().getSimpleName(), canonicalPhone);
                fallback.send(canonicalPhone, code);
            }
        }
    }

    static final class LocalInboxOtpGateway implements OtpDeliveryGateway {
        private static final org.slf4j.Logger LOG =
                org.slf4j.LoggerFactory.getLogger("AyanOtpLocal");
        private final Path inbox;

        LocalInboxOtpGateway(String inboxPath) {
            String configured = inboxPath == null ? "" : inboxPath.trim();
            this.inbox = (configured.isEmpty() ? Path.of("logs", "otp-inbox.log") : Path.of(configured)).toAbsolutePath();
        }

        @Override
        public void send(String canonicalPhone, String code) {
            // The owner has to be able to read the code even when the service is
            // running headless, so the file is the primary channel and the log is
            // the fallback. Both stay on the salon's own machine.
            String line = Instant.now() + "\t" + canonicalPhone + "\t" + code + System.lineSeparator();
            try {
                Path parent = inbox.getParent();
                if (parent != null) Files.createDirectories(parent);
                Files.write(inbox, line.getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
            } catch (IOException failure) {
                LOG.warn("Sign-in code file {} could not be written: {}", inbox, failure.toString());
            }
            LOG.warn("Sign-in code for {} is {} - read it to the customer, then let it expire", canonicalPhone, code);
        }
    }

    static final class HttpOtpDeliveryGateway implements OtpDeliveryGateway {
        private final RestClient.Builder client;
        private final String providerUrl;
        private final String providerToken;
        private final String sender;

        HttpOtpDeliveryGateway(RestClient.Builder client, String providerUrl, String providerToken, String sender) {
            this.client = client;
            this.providerUrl = providerUrl == null ? "" : providerUrl.trim();
            this.providerToken = providerToken == null ? "" : providerToken.trim();
            this.sender = sender == null ? "Ayan Salon" : sender.trim();
        }

        @Override
        public void send(String canonicalPhone, String code) {
            if (providerUrl.isBlank() || providerToken.isBlank()) {
                throw new IllegalStateException("OTP provider is not configured; set AYAN_AUTH_OTP_PROVIDER_URL and AYAN_AUTH_OTP_PROVIDER_TOKEN");
            }
            client.build().post().uri(providerUrl)
                    .header("Authorization", "Bearer " + providerToken)
                    .body(new OtpPayload(canonicalPhone, code, sender))
                    .retrieve().toBodilessEntity();
        }

        private record OtpPayload(String phone, String code, String sender) {}
    }
}
