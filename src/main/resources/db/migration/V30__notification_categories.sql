ALTER TABLE user_notification ADD COLUMN category VARCHAR(12) NOT NULL DEFAULT 'ACTIVITY';
ALTER TABLE user_notification ADD CONSTRAINT chk_notification_category
    CHECK (category IN ('ORDER', 'ACTIVITY', 'SELLING'));
CREATE INDEX idx_notification_unread ON user_notification (user_id) WHERE read_at IS NULL;
