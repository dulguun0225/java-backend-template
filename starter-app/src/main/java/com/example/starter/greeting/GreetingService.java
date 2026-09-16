package com.example.starter.greeting;

import static com.example.starter.db.Tables.GREETING;

import com.example.starter.db.tables.records.GreetingRecord;
import com.example.starter.platform.Ids;
import com.example.starter.platform.Tx;
import com.example.starter.platform.error.FieldError;
import com.example.starter.platform.error.ValidationFailed;
import com.example.starter.platform.observability.Log;
import com.example.starter.platform.observability.LogField;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

    public GreetingView create(CreateGreetingRequest request) {
        String name = validate(request);
        UUID id = Ids.newId();
        OffsetDateTime now = OffsetDateTime.now(clock);
        tx.write(dsl -> dsl.insertInto(GREETING)
                .set(GREETING.ID, id)
                .set(GREETING.NAME, name)
                .set(GREETING.CREATED_AT, now)
                .execute());
        log.info("greeting created", LogField.id("greeting_id", id));
        return new GreetingView(id, name, "Hello, " + name + "!", now);
    }

    public Optional<GreetingView> find(UUID id) {
        return tx.read(dsl -> dsl.selectFrom(GREETING).where(GREETING.ID.eq(id)).fetchOptional())
                .map(GreetingService::toView);
    }

    private static String validate(CreateGreetingRequest request) {
        String name = request.name();
        if (name == null || name.isBlank()) {
            throw new ValidationFailed(List.of(FieldError.of("/name", GreetingFieldCode.REQUIRED)));
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new ValidationFailed(List.of(FieldError.of("/name", GreetingFieldCode.TOO_LONG)));
        }
        return name.strip();
    }

    private static GreetingView toView(GreetingRecord record) {
        return new GreetingView(
                record.getId(), record.getName(), "Hello, " + record.getName() + "!", record.getCreatedAt());
    }
}
