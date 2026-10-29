package com.uniace.trackr.service;

import com.uniace.trackr.domain.Employee;
import com.uniace.trackr.domain.UserAccount;
import com.uniace.trackr.domain.enums.CaptureMode;
import com.uniace.trackr.dto.WorkEventDtos.RecordEventRequest;
import com.uniace.trackr.dto.WorkEventDtos.SyncResultItem;
import com.uniace.trackr.service.WorkEventService.BlockedEventException;
import com.uniace.trackr.service.WorkEventService.RecordResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Stores one queued offline event in its own transaction.
 *
 * <p>The transaction lives one level down, on
 * {@link WorkEventService#recordFromSync}, and is {@code REQUIRES_NEW}. That
 * placement matters: an event the server refuses outright rolls its own
 * transaction back cleanly, and this method - deliberately outside any
 * transaction - is then free to turn the refusal into a result the device can
 * act on. Catching the exception inside the transaction instead would leave a
 * transaction marked rollback-only and fail the whole batch on commit.
 */
@Service
public class OfflineEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(OfflineEventProcessor.class);

    private final WorkEventService workEventService;
    private final EventMapper mapper;

    public OfflineEventProcessor(WorkEventService workEventService, EventMapper mapper) {
        this.workEventService = workEventService;
        this.mapper = mapper;
    }

    public SyncResultItem process(UserAccount user, Employee employee, RecordEventRequest request) {
        try {
            RecordResult result = workEventService.recordFromSync(user, employee, forceOfflineMode(request));

            if (result.duplicate()) {
                return SyncResultItem.duplicate(request.eventId());
            }
            if (result.flagged()) {
                // The flags come back with the result rather than being read off
                // the entity: that transaction has already committed and closed.
                return SyncResultItem.flagged(request.eventId(),
                        "Saved. A supervisor will review this activity.",
                        mapper.toFlagViews(result.flags()));
            }
            return SyncResultItem.accepted(request.eventId());

        } catch (BlockedEventException ex) {
            // Cannot be stored at all. The device should stop retrying and show
            // the reason rather than looping forever.
            return SyncResultItem.failed(request.eventId(), ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("Could not synchronise event {} for {}", request.eventId(), employee.getEmployeeNo(), ex);
            return SyncResultItem.failed(request.eventId(),
                    "We could not save this activity. It will be tried again later.");
        }
    }

    /** Anything arriving through the sync endpoint was, by definition, captured offline. */
    private static RecordEventRequest forceOfflineMode(RecordEventRequest request) {
        if (request.captureMode() == CaptureMode.OFFLINE) {
            return request;
        }
        return new RecordEventRequest(
                request.eventId(),
                request.eventType(),
                request.eventTime(),
                request.deviceTimestamp(),
                request.latitude(),
                request.longitude(),
                request.gpsAccuracyM(),
                request.siteName(),
                request.destination(),
                request.destinationLocationId(),
                request.photoId(),
                request.deviceAssertion(),
                CaptureMode.OFFLINE,
                request.syncTimestamp(),
                request.integrityHash(),
                request.clientAppVersion());
    }
}
