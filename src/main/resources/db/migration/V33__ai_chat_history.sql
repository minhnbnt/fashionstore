-- V33__ai_chat_history.sql
-- Lưu trữ lịch sử hội thoại và tin nhắn của trợ lý AI

CREATE TABLE IF NOT EXISTS ai_conversation (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_profile(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_ai_conversation_user ON ai_conversation (user_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS ai_message (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES ai_conversation(id) ON DELETE CASCADE,
    role VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    metadata JSONB,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_ai_message_conversation ON ai_message (conversation_id, sent_at ASC);
