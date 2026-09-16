package com.example.starter.platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Config-default assertion: the seam hands out detached records, and this stays false. */
class TxSettingsTest {

    @Test
    void recordsAreNeverAttached() {
        assertThat(Tx.SETTINGS.isAttachRecords()).isFalse();
    }
}
