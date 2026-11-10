-- Quy tắc Flyway: file migration đã chạy thì KHÔNG BAO GIỜ sửa. Muốn đổi thì tạo V2__..., V3__...
--
-- Hoa tươi có hạn dùng ngắn nên tồn kho quản lý THEO LÔ, xuất theo FEFO (First Expired, First Out):
-- lô hết hạn sớm nhất được xuất trước. Mỗi lần nhập hoa là một lô, có ngày hết hạn riêng.
--
-- Hàng được TRỪ khỏi qty_available ngay lúc giữ (reserve), không phải lúc giao:
-- hai đơn đặt cùng lúc không thể cùng lấy một bông hoa.

-- Mã lô cho người đọc (LO_0000001), tách khỏi id kỹ thuật. NO CYCLE: hết số thì lỗi, không cấp trùng.
CREATE SEQUENCE lot_code_seq START WITH 1 INCREMENT BY 1 MAXVALUE 9999999 NO CYCLE;

CREATE TABLE stock_lot (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lot_code      VARCHAR(20)    NOT NULL,
    -- SKU bên catalog-service. Không có FOREIGN KEY vì nằm ở DATABASE KHÁC.
    sku           VARCHAR(30)    NOT NULL,
    qty_received  INT            NOT NULL,
    qty_available INT            NOT NULL,
    unit_cost     NUMERIC(19, 4) NOT NULL,   -- giá nhập, để tính giá vốn và giá trị hoa hỏng
    currency      VARCHAR(3)     NOT NULL,
    supplier      VARCHAR(150),
    received_at   TIMESTAMPTZ    NOT NULL,
    expires_at    TIMESTAMPTZ    NOT NULL,
    version       BIGINT         NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ    NOT NULL,
    created_by    VARCHAR(100)   NOT NULL,
    updated_at    TIMESTAMPTZ,
    updated_by    VARCHAR(100),

    CONSTRAINT uk_stock_lot_code      UNIQUE (lot_code),
    CONSTRAINT ck_stock_lot_received  CHECK (qty_received > 0),
    -- Chốt chặn cuối: dù code có bug cũng không bao giờ âm kho hoặc "đẻ" thêm hàng
    CONSTRAINT ck_stock_lot_available CHECK (qty_available BETWEEN 0 AND qty_received),
    CONSTRAINT ck_stock_lot_cost      CHECK (unit_cost >= 0),
    CONSTRAINT ck_stock_lot_expiry    CHECK (expires_at > received_at)
);

-- Phục vụ đúng câu truy vấn FEFO: lô còn hàng của một SKU, sắp theo hạn dùng
CREATE INDEX idx_stock_lot_fefo ON stock_lot (sku, expires_at, id) WHERE qty_available > 0;

-- Một lần giữ hàng cho một đơn. order_code UNIQUE: order-service gọi lại (retry) không bị giữ hai lần.
CREATE TABLE stock_reservation (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_code   VARCHAR(30)  NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    hold_until   TIMESTAMPTZ  NOT NULL,      -- HELD quá thời điểm này thì tự nhả (job)
    fresh_until  TIMESTAMPTZ  NOT NULL,      -- chỉ lấy lô còn hạn sau thời điểm này (vd giờ giao hàng)
    confirmed_at TIMESTAMPTZ,
    released_at  TIMESTAMPTZ,
    version      BIGINT       NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL,
    created_by   VARCHAR(100) NOT NULL,
    updated_at   TIMESTAMPTZ,
    updated_by   VARCHAR(100),

    CONSTRAINT ck_stock_reservation_order_format CHECK (order_code ~ '^[A-Z0-9_-]{3,30}$'),
    CONSTRAINT uk_stock_reservation_order_code UNIQUE (order_code),
    CONSTRAINT ck_stock_reservation_status CHECK (status IN ('HELD', 'CONFIRMED', 'RELEASED', 'EXPIRED'))
);

-- Job nhả hàng quá hạn chỉ quét các dòng HELD
CREATE INDEX idx_stock_reservation_held ON stock_reservation (hold_until) WHERE status = 'HELD';

-- Lấy từ lô nào, bao nhiêu: một SKU có thể lấy từ nhiều lô (lô đầu không đủ thì lấy tiếp lô sau)
CREATE TABLE stock_reservation_line (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reservation_id BIGINT      NOT NULL REFERENCES stock_reservation (id),
    lot_id         BIGINT      NOT NULL REFERENCES stock_lot (id),
    sku            VARCHAR(30) NOT NULL,
    quantity       INT         NOT NULL,
    CONSTRAINT ck_reservation_line_qty CHECK (quantity > 0)
);

CREATE INDEX idx_reservation_line_reservation ON stock_reservation_line (reservation_id);

-- Sổ cái kho: CHỈ THÊM, không sửa, không xóa. Mỗi dòng là một lần qty_available của lô thay đổi.
-- Đối soát: qty_available của lô = tổng quantity_delta của lô đó.
CREATE TABLE stock_movement (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lot_id         BIGINT       NOT NULL REFERENCES stock_lot (id),
    movement_type  VARCHAR(20)  NOT NULL,
    quantity_delta INT          NOT NULL,     -- dương: vào kho, âm: ra kho
    reference      VARCHAR(100),              -- mã đơn (giữ/nhả hàng) hoặc lý do (hoa hỏng)
    actor          VARCHAR(100),              -- sub trong token: nhân viên hoặc service
    correlation_id VARCHAR(64),
    occurred_at    TIMESTAMPTZ  NOT NULL,

    CONSTRAINT ck_stock_movement_type CHECK (movement_type IN ('RECEIVE', 'RESERVE', 'RELEASE', 'EXPIRE_HOLD', 'WASTE')),
    CONSTRAINT ck_stock_movement_delta CHECK (quantity_delta <> 0)
);

CREATE INDEX idx_stock_movement_lot ON stock_movement (lot_id, id);
