package com.example.starter.greeting;

import static com.example.starter.db.Tables.GREETING;

import com.example.starter.db.tables.records.GreetingRecord;
import com.example.starter.platform.Ids;
import com.example.starter.platform.Tx;
import com.example.starter.platform.error.BoundBody;
import com.example.starter.platform.error.FieldError;
import com.example.starter.platform.error.Rejected;
import com.example.starter.platform.observability.Log;
import com.example.starter.platform.observability.LogField;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Creates and reads greetings. Every database access is a visible {@code tx.write} or {@code tx.read} block
 * over generated, compile-checked jOOQ tables. Ids are assigned app-side (UUIDv7) so the insert needs no
 * {@code RETURNING} round-trip; time comes from the injected {@link Clock}.
 */
@Service
public class GreetingService {

    static final int NAME_MAX_LENGTH = 100;

    private static final Log log = Log.forClass(GreetingService.class);

    private final Tx tx;
    private final Clock clock;

    GreetingService(Tx tx, Clock clock) {
        this.tx = tx;
        this.clock = clock;
    }

    /**
     * Validates the body before the transaction opens, so a refused body runs no statement: the strict reader's
     * binding failures and the field rules below leave as one {@code validation.failed}.
     */
    public GreetingView create(BoundBody<CreateGreetingRequest> body) {
        String name = body.validate(GreetingService::validName);
        UUID id = Ids.newId();
        OffsetDateTime now = OffsetDateTime.now(clock);
        tx.write(dsl -> dsl.insertInto(GREETING)
                .set(GREETING.ID, id)
                .set(GREETING.NAME, name)
                .set(GREETING.CREATED_AT, now)
                .set(GREETING.VERSION, 1)
                .execute());
        log.info("greeting created", LogField.id("greeting_id", id));
        return new GreetingView(id, name, "Hello, " + name + "!", now);
    }

    /** The one greeting, or the coded rejection the edge renders as an RFC 9457 problem. */
    public GreetingView get(UUID id) {
        return tx.read(dsl -> dsl.selectFrom(GREETING).where(GREETING.ID.eq(id)).fetchOptional())
                .map(GreetingService::toView)
                .orElseThrow(() -> new Rejected(GreetingErrorCode.NOT_FOUND));
    }

    /** The field rules: each failure is appended to {@code errors}, and no value is produced when any is. */
    private static @Nullable String validName(CreateGreetingRequest request, List<FieldError> errors) {
        String name = request.name();
        if (name == null || name.isBlank()) {
            errors.add(FieldError.of("/name", GreetingFieldCode.REQUIRED));
            return null;
        }
        if (name.length() > NAME_MAX_LENGTH) {
            errors.add(FieldError.of("/name", GreetingFieldCode.TOO_LONG));
            return null;
        }
        return name.strip();
    }

    private static GreetingView toView(GreetingRecord record) {
        return new GreetingView(
                record.getId(), record.getName(), "Hello, " + record.getName() + "!", record.getCreatedAt());
    }
}
