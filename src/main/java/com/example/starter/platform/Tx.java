package com.example.starter.platform;

import java.sql.Connection;
import java.util.function.Function;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The one way to touch the database. Every call opens an explicit, visible transaction and hands a jOOQ
 * {@link DSLContext} to the caller's lambda. {@code DSLContext} is never injectable
 * ({@code BanListArchTest.dslContextIsNeverInjected}) and {@code @Transactional} is banned
 * ({@code BanListArchTest.noRuntimeSilentAnnotations}), so SQL outside a {@code tx.*} block is
 * unwritable and there is no silent autocommit path and no proxy deciding transaction boundaries at
 * runtime.
 *
 * <p>The default is {@code write}; {@code read} marks the transaction read-only so the driver and the
 * database can refuse an accidental write.
 */
@Component
public final class Tx {

    /**
     * Detached records only: a fetched record never carries a connection, so {@code store()} and friends have
     * nothing to write through even before the ArchUnit ban. {@code ConfigDefaultsTest} pins this stays false.
     */
    static final Settings SETTINGS = new Settings().withAttachRecords(false);

    private final TransactionTemplate writeTx;
    private final TransactionTemplate readTx;
    private final DataSource dataSource;

    Tx(PlatformTransactionManager txManager, DataSource dataSource) {
        this.dataSource = dataSource;
        this.writeTx = new TransactionTemplate(txManager);
        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
    }

    public <T> T write(Function<DSLContext, T> work) {
        return run(writeTx, work);
    }

    public <T> T read(Function<DSLContext, T> work) {
        return run(readTx, work);
    }

    private <T> T run(TransactionTemplate tx, Function<DSLContext, T> work) {
        T result = tx.execute(status -> {
            Connection connection = DataSourceUtils.getConnection(dataSource);
            return work.apply(DSL.using(connection, SQLDialect.POSTGRES, SETTINGS));
        });
        if (result == null) {
            throw new IllegalStateException("transaction returned null; return a value or Optional");
        }
        return result;
    }
}
