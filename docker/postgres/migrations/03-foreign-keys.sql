-- Migration 03: Foreign Key Constraints and Referential Integrity
-- Applies relational foreign keys with ON DELETE CASCADE / SET NULL across GitBot entities.
-- Note: JPA entities retain flat UUID fields. No entity relationships or vector_store changes.

BEGIN;

-- 1. Pre-flight verification: Ensure tables, columns, and zero orphan records exist
DO $$
DECLARE
    orphan_count INTEGER;
BEGIN
    -- Check git_repositories -> users
    SELECT count(*) INTO orphan_count
    FROM git_repositories r LEFT JOIN users u ON r.user_id = u.id
    WHERE r.user_id IS NOT NULL AND u.id IS NULL;
    IF orphan_count > 0 THEN
        RAISE EXCEPTION 'Pre-flight check failed: % orphan row(s) in git_repositories.user_id referencing users.id', orphan_count;
    END IF;

    -- Check chat_sessions -> users
    SELECT count(*) INTO orphan_count
    FROM chat_sessions s LEFT JOIN users u ON s.user_id = u.id
    WHERE s.user_id IS NOT NULL AND u.id IS NULL;
    IF orphan_count > 0 THEN
        RAISE EXCEPTION 'Pre-flight check failed: % orphan row(s) in chat_sessions.user_id referencing users.id', orphan_count;
    END IF;

    -- Check chat_sessions -> git_repositories
    SELECT count(*) INTO orphan_count
    FROM chat_sessions s LEFT JOIN git_repositories r ON s.repository_id = r.id
    WHERE s.repository_id IS NOT NULL AND r.id IS NULL;
    IF orphan_count > 0 THEN
        RAISE EXCEPTION 'Pre-flight check failed: % orphan row(s) in chat_sessions.repository_id referencing git_repositories.id', orphan_count;
    END IF;

    -- Check chat_messages -> chat_sessions
    SELECT count(*) INTO orphan_count
    FROM chat_messages m LEFT JOIN chat_sessions s ON m.session_id = s.id
    WHERE m.session_id IS NOT NULL AND s.id IS NULL;
    IF orphan_count > 0 THEN
        RAISE EXCEPTION 'Pre-flight check failed: % orphan row(s) in chat_messages.session_id referencing chat_sessions.id', orphan_count;
    END IF;

    -- Check message_reports -> users
    SELECT count(*) INTO orphan_count
    FROM message_reports mr LEFT JOIN users u ON mr.user_id = u.id
    WHERE mr.user_id IS NOT NULL AND u.id IS NULL;
    IF orphan_count > 0 THEN
        RAISE EXCEPTION 'Pre-flight check failed: % orphan row(s) in message_reports.user_id referencing users.id', orphan_count;
    END IF;

    -- Check message_reports -> chat_sessions
    SELECT count(*) INTO orphan_count
    FROM message_reports mr LEFT JOIN chat_sessions s ON mr.session_id = s.id
    WHERE mr.session_id IS NOT NULL AND s.id IS NULL;
    IF orphan_count > 0 THEN
        RAISE EXCEPTION 'Pre-flight check failed: % orphan row(s) in message_reports.session_id referencing chat_sessions.id', orphan_count;
    END IF;

    -- Check message_reports -> chat_messages
    SELECT count(*) INTO orphan_count
    FROM message_reports mr LEFT JOIN chat_messages m ON mr.message_id = m.id
    WHERE mr.message_id IS NOT NULL AND m.id IS NULL;
    IF orphan_count > 0 THEN
        RAISE EXCEPTION 'Pre-flight check failed: % orphan row(s) in message_reports.message_id referencing chat_messages.id', orphan_count;
    END IF;

    -- Check chat_sessions -> chat_sessions (parent_session_id)
    SELECT count(*) INTO orphan_count
    FROM chat_sessions s LEFT JOIN chat_sessions p ON s.parent_session_id = p.id
    WHERE s.parent_session_id IS NOT NULL AND p.id IS NULL;
    IF orphan_count > 0 THEN
        RAISE EXCEPTION 'Pre-flight check failed: % orphan row(s) in chat_sessions.parent_session_id referencing chat_sessions.id', orphan_count;
    END IF;

    RAISE NOTICE 'Pre-flight check passed: zero orphan rows found across all relationships.';
END $$;

-- 2. Supporting Indexes for Foreign Keys
-- Note: Existing indexes already cover:
--   - git_repositories(user_id) via idx_git_repos_user_fullname
--   - chat_sessions(user_id) via idx_chat_sessions_user_repo_created
--   - chat_messages(session_id) via idx_chat_messages_session_created_id
--   - message_reports(user_id) via uk_user_message_report

CREATE INDEX IF NOT EXISTS idx_chat_sessions_user_created
    ON chat_sessions (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_chat_sessions_repository_id
    ON chat_sessions (repository_id);

CREATE INDEX IF NOT EXISTS idx_chat_sessions_parent_session_id
    ON chat_sessions (parent_session_id)
    WHERE parent_session_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_message_reports_session_id
    ON message_reports (session_id);

CREATE INDEX IF NOT EXISTS idx_message_reports_message_id
    ON message_reports (message_id);

-- 3. Foreign Key Constraints

-- git_repositories.user_id -> users.id (ON DELETE CASCADE)
ALTER TABLE git_repositories
    DROP CONSTRAINT IF EXISTS fk_git_repositories_user,
    ADD CONSTRAINT fk_git_repositories_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE;

-- chat_sessions.user_id -> users.id (ON DELETE CASCADE)
ALTER TABLE chat_sessions
    DROP CONSTRAINT IF EXISTS fk_chat_sessions_user,
    ADD CONSTRAINT fk_chat_sessions_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE;

-- chat_sessions.repository_id -> git_repositories.id (ON DELETE CASCADE)
ALTER TABLE chat_sessions
    DROP CONSTRAINT IF EXISTS fk_chat_sessions_repository,
    ADD CONSTRAINT fk_chat_sessions_repository
        FOREIGN KEY (repository_id)
        REFERENCES git_repositories (id)
        ON DELETE CASCADE;

-- chat_messages.session_id -> chat_sessions.id (ON DELETE CASCADE)
ALTER TABLE chat_messages
    DROP CONSTRAINT IF EXISTS fk_chat_messages_session,
    ADD CONSTRAINT fk_chat_messages_session
        FOREIGN KEY (session_id)
        REFERENCES chat_sessions (id)
        ON DELETE CASCADE;

-- message_reports.user_id -> users.id (ON DELETE CASCADE)
ALTER TABLE message_reports
    DROP CONSTRAINT IF EXISTS fk_message_reports_user,
    ADD CONSTRAINT fk_message_reports_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE;

-- message_reports.session_id -> chat_sessions.id (ON DELETE CASCADE)
ALTER TABLE message_reports
    DROP CONSTRAINT IF EXISTS fk_message_reports_session,
    ADD CONSTRAINT fk_message_reports_session
        FOREIGN KEY (session_id)
        REFERENCES chat_sessions (id)
        ON DELETE CASCADE;

-- message_reports.message_id -> chat_messages.id (ON DELETE CASCADE)
ALTER TABLE message_reports
    DROP CONSTRAINT IF EXISTS fk_message_reports_message,
    ADD CONSTRAINT fk_message_reports_message
        FOREIGN KEY (message_id)
        REFERENCES chat_messages (id)
        ON DELETE CASCADE;

-- chat_sessions.parent_session_id -> chat_sessions.id (ON DELETE SET NULL)
ALTER TABLE chat_sessions
    DROP CONSTRAINT IF EXISTS fk_chat_sessions_parent_session,
    ADD CONSTRAINT fk_chat_sessions_parent_session
        FOREIGN KEY (parent_session_id)
        REFERENCES chat_sessions (id)
        ON DELETE SET NULL;

COMMIT;
