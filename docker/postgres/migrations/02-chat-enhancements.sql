-- Migration 02: Chat System Enhancements
-- 1. Chat Sessions: add branching and public share fields
ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS parent_session_id UUID;
ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS branch_message_id UUID;
ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS share_token VARCHAR(64) UNIQUE;
ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS is_shared BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS shared_at TIMESTAMP WITH TIME ZONE;

-- 2. Chat Messages: add message status
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'COMPLETE' NOT NULL;

-- 3. Message Reports
CREATE TABLE IF NOT EXISTS message_reports (
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL,
    session_id UUID NOT NULL,
    user_id UUID NOT NULL,
    reason VARCHAR(50) NOT NULL,
    details TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_user_message_report UNIQUE (user_id, message_id)
);

CREATE INDEX IF NOT EXISTS idx_chat_sessions_share_token ON chat_sessions(share_token);
CREATE INDEX IF NOT EXISTS idx_chat_messages_session_id ON chat_messages(session_id);
CREATE INDEX IF NOT EXISTS idx_message_reports_session_id ON message_reports(session_id);
