package com.ayan.salon.server.config;

import com.ayan.salon.server.service.OtpDeliveryGateway;
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
            @Value("${ayan.auth.otp.inbox-path:}") String inboxPath) {
        return new LocalInboxOtpGateway(inboxPath);
    }

    @Bean
    @Profile("!dev & !test & !local")
    OtpDeliveryGateway productionOtpGateway(
            RestClient.Builder client,
            @Value("${ayan.auth.otp.provider-url:}") String providerUrl,
            @Value("${ayan.auth.otp.provider-token:}") String providerToken,
            @Value("${ayan.auth.otp.sender:Ayan Salon}") String sender) {
        return new HttpOtpDeliveryGateway(client, providerUrl, providerToken, sender);
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
