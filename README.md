# flower-shop-inventory-service

Tồn kho hoa tươi: nhập **theo lô** (mỗi lô có hạn dùng riêng), xuất **FEFO** (hết hạn trước xuất trước),
giữ hàng cho đơn, sổ cái kho chỉ thêm. PostgreSQL + Flyway.

Cổng local: `8084`

## Khái niệm chính

| Khái niệm | Ý nghĩa |
|---|---|
| Lô (`stock_lot`) | Một lần nhập: SKU, số lượng, giá nhập, hạn dùng. Mã `LO_0000001` tách khỏi id kỹ thuật |
| `qty_available` | Số còn trong lô **chưa bị giữ**. Trừ ngay lúc giữ hàng, không đợi lúc giao |
| Phiếu giữ hàng (`stock_reservation`) | Một đơn giữ hàng một lần, khóa idempotency là `order_code` |
| Sổ cái (`stock_movement`) | Mỗi lần `qty_available` đổi là một dòng có dấu (+/-), ai làm, correlation id. Chỉ thêm |
| Bán được (`sellable`) | Tổng `qty_available` của lô còn hạn |

Đối soát luôn đúng: `qty_available` của mỗi lô = tổng `quantity_delta` trong sổ cái của lô đó (có test kiểm tra).

## Giữ hàng

```
HELD ──confirm──► CONFIRMED          (đơn đặt xong: giữ tới khi giao)
 │                   │
 ├──release──► RELEASED ◄──release   (đơn hủy: trả về đúng lô đã lấy)
 └──quá hold_until──► EXPIRED        (job mỗi phút, chỉ áp dụng cho HELD)
```

- **FEFO**: lấy lô hết hạn sớm nhất trước, lô đầu không đủ thì lấy tiếp lô sau. `freshUntil` (vd giờ giao hàng) bỏ qua lô hết hạn trước thời điểm đó.
- **Tất cả hoặc không**: thiếu một SKU thì cả đơn không giữ được gì (`INV-409-001`, message ghi rõ SKU nào, cần bao nhiêu, còn bao nhiêu).
- **Không bán vượt**: lô bị khóa `SELECT ... FOR UPDATE` trong lúc giữ; 20 đơn tranh nhau 10 bông thì đúng 10 đơn thành công (có test).
- **Không deadlock giữa các đơn**: khóa lô theo thứ tự SKU tăng dần.
- **Idempotent**: gọi lại cùng `orderCode` + cùng danh sách → `200` trả phiếu cũ; khác danh sách → `INV-409-002`. `confirm`/`release` gọi lại nhiều lần chỉ có tác dụng một lần.
- **Job nhả hàng** dùng `FOR UPDATE SKIP LOCKED`: chạy nhiều instance cùng lúc vẫn không nhả một phiếu hai lần.

## API

| Method | Path | Quyền |
|---|---|---|
| POST | `/api/v1/inventory/lots` `{sku, quantity, unitCost, currency, expiresAt, supplier}` | `inventory:write` |
| POST | `/api/v1/inventory/lots/{lotCode}/waste` `{quantity, reason: EXPIRED\|DAMAGED\|OTHER, note}` | `inventory:write` |
| GET | `/api/v1/inventory/lots?sku=&includeEmpty=false` (sắp theo hạn dùng) | `inventory:read` |
| GET | `/api/v1/inventory/lots/{lotCode}` · `/lots/{lotCode}/movements` | `inventory:read` |
| GET | `/api/v1/inventory/stock?sku=` → `{sellable, expired}` | `inventory:read` |
| GET | `/api/v1/inventory/availability?sku=` → `{available}` (không lộ số lượng) | public |
| POST | `/internal/v1/reservations` `{orderCode, items[{sku, quantity}], holdMinutes, freshUntil}` | `inventory:reserve` |
| GET | `/internal/v1/reservations/{orderCode}` | `inventory:reserve` |
| POST | `/internal/v1/reservations/{orderCode}/confirm` · `/release` | `inventory:reserve` |

- Nhập lô hỏi **catalog-service** (`GET /api/v1/flowers/sku/{sku}`) trước: SKU không có → `INV-422-001`, đã ngừng bán → `INV-422-002`. Gọi catalog **trước** khi mở transaction DB.
- `inventory:read`/`write`: ADMIN, SHOP_MANAGER, SHOP_STAFF. `inventory:reserve`: **chỉ service client** (order-service), không role nào của người có.
- `/internal/**` không có route ở gateway và bị gateway `denyAll()`.

Mã lỗi: `INV-400-001` hạn dùng không hợp lệ · `INV-400-002` SKU trùng trong request · `INV-404-001` không thấy lô · `INV-404-002` không thấy phiếu · `INV-409-001` không đủ hàng · `INV-409-002` mã đơn dùng lại với hàng khác · `INV-409-003` phiếu đã nhả/hết hạn · `INV-409-004` đơn đang được giữ hàng (request trùng đến cùng lúc), thử lại · `INV-422-001` SKU không có trong catalog · `INV-422-002` SKU ngừng kinh doanh · `INV-422-003` hủy nhiều hơn số còn · `INV-503-001` catalog không phản hồi.

## Cấu hình

| Khóa | Mặc định | Ý nghĩa |
|---|---|---|
| `inventory.reservation.default-hold` | `15m` | Giữ bao lâu nếu đơn không gửi `holdMinutes` |
| `inventory.reservation.expiry-interval-ms` | `60000` | Chu kỳ job nhả hàng quá hạn |
| `inventory.reservation.expiry-job-enabled` | `true` | Tắt job (test dùng) |
| `inventory.catalog.*` | `http://catalog-service`, 2s/3s | Địa chỉ + timeout gọi catalog |

Biến môi trường: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `EUREKA_URL`, `IDENTITY_JWKS_URI`.

## Giới hạn đã biết

- Tồn kho tính theo **đơn vị của SKU** (vd một bó). Chưa tách hoa lẻ / công thức bó (bó 10 hồng = 10 cành hồng).
- Nhập lô bấm hai lần sẽ tạo hai lô (chưa có Idempotency-Key cho API nhân viên).
- Phiếu đã `CONFIRMED` chưa có bước "xuất kho khi giao" riêng: hàng đã trừ từ lúc giữ, giao xong không cần đổi số.

## Chạy

Cách khuyên dùng: chạy cả hệ thống từ repo `flower-shop-workspace` (`./clone-all.sh` rồi `./run-local.sh`).

```bash
mvn verify     # cần Docker (Testcontainers bật Postgres thật)
```

## Release và Docker image

Merge PR `dev → production` thì GitHub Actions publish image `ghcr.io/flower-shop-teddy/flower-shop-inventory-service:X.Y.Z` và tạo tag `vX.Y.Z`.
Chi tiết quy trình: README của repo `flower-shop-workspace`, mục "CI/CD và release".

```bash
docker run --rm -p 8084:8084 \
  -e DB_URL=jdbc:postgresql://<host>:5432/inventory_db -e DB_USERNAME=inventory_user -e DB_PASSWORD=<...> \
  -e EUREKA_URL=http://<eureka>:8761/eureka -e IDENTITY_JWKS_URI=http://<identity>:8083/.well-known/jwks.json \
  ghcr.io/flower-shop-teddy/flower-shop-inventory-service:<version>
```

Cách đơn giản hơn để chạy cùng các service khác: `docker-compose.app.yml` ở repo workspace.
