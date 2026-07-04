package com.turkcell.notificationservice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.turkcell.commonlib.saga.QuotaThresholdReached;

/**
 * Kota esik event'lerinden SMS bildirimi uretir (G1, docx senaryo 14.3).
 * Idempotency + opt-out (FR-30) {@link NotificationDispatcher}'da.
 *  - %80  -> "QUOTA_WARNING_80"  (kotaniz azaliyor)
 *  - %100 -> "QUOTA_EXCEEDED"   (kotaniz bitti; asim ucretlendirilir)
 */
@Service
public class QuotaEventHandler {

    private static final String TPL_WARNING_80 = "QUOTA_WARNING_80";
    private static final String TPL_EXCEEDED = "QUOTA_EXCEEDED";

    private final NotificationDispatcher dispatcher;

    public QuotaEventHandler(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Transactional
    public void handleThreshold(QuotaThresholdReached event) {
        String templateCode = event.thresholdPct() >= 100 ? TPL_EXCEEDED : TPL_WARNING_80;
        dispatcher.dispatch(event.eventId(), event.customerId(), templateCode, "SMS",
                "sub=" + event.subscriptionId() + " type=" + event.type() + " %" + event.thresholdPct()
                        + " kalan=" + event.remaining() + "/" + event.total() + " msisdn=" + event.msisdn());
    }
}
