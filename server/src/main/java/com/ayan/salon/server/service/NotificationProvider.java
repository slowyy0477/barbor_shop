package com.ayan.salon.server.service;

import com.ayan.salon.server.domain.NotificationOutbox;

/** External SMS/push adapter. It never receives a payment PIN or password. */
public interface NotificationProvider {
    void deliver(NotificationOutbox message);

    /**
     * Delivers a message with a runtime-resolved destination when one is needed.
     * The destination is intentionally not persisted in the outbox payload: the
     * dispatcher resolves the current customer phone just before delivery and
     * passes it only to the provider. Existing providers remain source-compatible
     * through this default method.
     */
    default void deliver(NotificationOutbox message, String destination) {
        deliver(message);
    }
}
