-- SUBJECT_ROLE_DECIDED was removed from LogEventType (its Discord post duplicated the
-- Decision-channel embed, which already gets edited in place with the outcome). Any
-- subscription row still pointing at it makes every log-channel read for that guild 500
-- (Hibernate can't map an @Enumerated(STRING) column back to a constant that no longer exists).
DELETE FROM log_channel_subscription WHERE event_type = 'SUBJECT_ROLE_DECIDED';
