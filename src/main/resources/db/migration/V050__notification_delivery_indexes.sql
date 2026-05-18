CREATE INDEX IF NOT EXISTS idx_notifications_unread_target
    ON notifications(recipient_user_id, type, target_type, target_id)
    WHERE is_read = FALSE;
