-- V5: Thêm trạng thái cho phiếu nhập kho
ALTER TABLE inbound_receipts
    ADD COLUMN status VARCHAR(25) NOT NULL DEFAULT 'RECEIVED';
