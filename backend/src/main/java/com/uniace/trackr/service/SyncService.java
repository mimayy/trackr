package com.uniace.trackr.service;

import com.uniace.trackr.domain.Employee;
import com.uniace.trackr.domain.UserAccount;
import com.uniace.trackr.domain.enums.AuditResult;
import com.uniace.trackr.dto.WorkEventDtos.RecordEventRequest;
import com.uniace.trackr.dto.WorkEventDtos.SyncResponse;
import com.uniace.trackr.dto.WorkEventDtos.SyncResultItem;
import com.uniace.trackr.security.AuthPrincipal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Uploads the offline queue.
 *
 * <p>Three properties matter here:
 *
 * <ul>
 *   <li><b>Idempotent.</b> Event ids come from the device, so a batch retried
 *       after a dropped connection produces no duplicates.</li>
 *   <li><b>Ordered.</b> Events are processed oldest first, so the workday
 *       sequence is reconstructed the way it happened.</li>
 *   <li><b>Independent.</b> Each event is stored in its own transaction, so one
 *       bad record cannot take down the rest of the batch and the device is told
 *       exactly which ones to stop retrying.</li>
 * </ul>
 */
@Service
public class SyncService {

    private final OfflineEventProcessor processor;
    private final AuditService auditService;

    public SyncService(OfflineEventProcessor processor, AuditService auditService) {
        this.processor = processor;
        this.auditService = auditService;
    }

    public SyncResponse sync(AuthPrincipal principal,
                             UserAccount user,
                             Employee employee,
                             List<RecordEventRequest> events) {

        List<RecordEventRequest> ordered = new ArrayList<>(events);
        ordered.sort(Comparator.comparing(RecordEventRequest::eventTime));

        List<SyncResultItem> results = new ArrayList<>();
        int accepted = 0;
        int flagged = 0;
        int duplicates = 0;
        int failed = 0;

        for (RecordEventRequest request : ordered) {
            SyncResultItem item = processor.process(user, employee, request);
            results.add(item);

            switch (item.status()) {
                case "ACCEPTED" -> accepted++;
                case "FLAGGED" -> flagged++;
                case "DUPLICATE" -> duplicates++;
                default -> failed++;
            }

            if ("FLAGGED".equals(item.status())) {
                auditService.record(principal, "SYNC_OFFLINE_EVENT", "WorkEvent",
                        item.eventId().toString(), employee.fullName(), AuditResult.FLAGGED, item.message());
            } else if ("FAILED".equals(item.status())) {
                auditService.record(principal, "SYNC_OFFLINE_EVENT", "WorkEvent",
                        item.eventId().toString(), employee.fullName(), AuditResult.FAILURE, item.message());
            }
        }

        return new SyncResponse(accepted, flagged, duplicates, failed, results);
    }
}
