package sk.gkanocz.aisauth.shared;

import org.slf4j.Logger;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;

/**
 * Tells a "database is down" failure apart from an actual application bug, so the Discord command
 * handlers can log a single line during a Postgres outage instead of a full stack trace per
 * command (see the {@code catch (Exception e)} blocks that report the generic error message).
 */
public final class DbErrors {

    private DbErrors() {
    }

    public static boolean isUnavailable(Throwable t) {
        for (Throwable cause = t; cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (cause instanceof TransactionException || cause instanceof DataAccessException) {
                return true;
            }
        }
        return false;
    }

    /** One concise WARN line when the DB is simply down, the full ERROR + stack trace otherwise. */
    public static void report(Logger log, String message, Throwable e) {
        if (isUnavailable(e)) {
            log.warn("{} - database unavailable", message);
        } else {
            log.error(message, e);
        }
    }
}
