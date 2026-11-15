# Tìm sản phẩm bằng ảnh — gRPC sang embed-worker

Spec cho luồng 3 (`features/catalog/`). Đọc kèm `../../catalog.md` (#13 ảnh, #16 search) và
`../../../../embed-worker/docs/superpowers/specs/2026-11-15-embedding-worker-design.md`.

## Mục tiêu

Khách gửi một ảnh, nhận danh sách sản phẩm trông giống nhất. Vector Fashion-CLIP của ảnh query
do worker Python tính, backend gọi qua gRPC rồi tự truy vấn diskann.

**Phạm vi:** chỉ đường **đọc**. Đường ghi vector vẫn là pgmq + trigger `trg_product_image_embed`
như cũ, không đổi. Worker không ghi DB trong luồng này — gRPC server là thuần inference, stateless.

Text search vẫn là BM25 (`pg_textsearch`), **không** dùng CLIP text tower.

## Bối cảnh và ràng buộc

- Backend Spring Boot 4.1.1 / Java 21. Dùng `spring-grpc` **1.0.3** (bản dựng trên Boot 4.0.2 —
  lệch minor với 4.1.1, chấp nhận; nếu vỡ thì fallback grpc-java thuần + bean `Channel` tay).
- Ghi vector dùng thư viện `com.pgvector:pgvector:0.1.6` (`PGvector` để bind tham số), **không**
  dùng Spring AI.
- Worker chạy cùng host với backend, nên channel là plaintext h2c tới `127.0.0.1`, **không auth**.
- `diskann` của pgvectorscale không lọc được bên trong index → lọc bằng `WHERE` sẽ ăn vào recall,
  nên endpoint **không nhận bộ lọc**, chỉ top-N.
- Không có Dockerfile/compose cho worker; chạy bằng `uv`.

## Contract

`embed-worker/proto/fashion_clip/v1/embedding.proto`:

```proto
syntax = "proto3";
package fashion_clip.v1;

option java_package = "com.minhnbnt.fashionstore.grpc";
option java_multiple_files = true;

message EmbedImageRequest { bytes image = 1; }

message EmbedImageReply {
  repeated float vector = 1;  // 512 chiều, đã L2-normalize
  string model = 2;           // "fashion-clip-vit-b32"
}

service EmbeddingService {
  rpc EmbedImage(EmbedImageRequest) returns (EmbedImageReply);
}
```

Proto có mặt ở **cả hai** repo (`embed-worker/proto/...` và `backend/src/main/proto/...`).
Canonical ở worker: sửa contract thì sửa bên worker rồi copy sang. **Cả hai bên** có test so hash
với file bên kia (thiếu checkout thì test skip) — lệch là đỏ, không lệch âm thầm.

## Phía worker

- `app/grpc/embedding_service.py` — servicer. Kiểm tra size ≤ 8MB, decode Pillow, đổi RGB. Lỗi
  decode / không phải ảnh → `INVALID_ARGUMENT`; quá size → `RESOURCE_EXHAUSTED`. Trả vector
  normalize từ `EmbeddingModel.embed_images`.
- `app/grpc/server.py` — `grpc.server(ThreadPoolExecutor(4), maximum_concurrent_rpcs=4,
  options=[max_receive_message_size=8MB, max_send_message_size=8MB])`, đăng ký servicer +
  `grpc.health.v1.Health`, bind `127.0.0.1:${GRPC_PORT:-9091}`, `torch.set_num_threads(4)`.
- `main()`: start server → chạy consumer loop như cũ → SIGTERM/SIGINT dừng cả hai,
  `server.stop(grace=5)`, xong batch dở mới đóng pool/DB.
- Consumer pgmq dùng **chung** pool 4 nên tổng song song có trần. `EMBED_BATCH_SIZE` giữ 16; nếu
  p95 xấu thì hạ.
- Stub sinh bằng `scripts/gen_proto.sh` (`grpc_tools.protoc`), output `app/grpc/gen/` **commit**
  vào repo, không sinh lúc runtime.

## Phía backend

Build:
- plugin `id("com.google.protobuf") version "0.10.1"`; nếu vỡ trên Gradle 9.5.1 thì commit mã sinh
  ra và bỏ plugin.
- deps: `org.springframework.grpc:spring-grpc-client-spring-boot-starter:1.0.3`,
  `com.pgvector:pgvector:0.1.6`.
- `protobuf { protoc { artifact = "com.google.protobuf:protoc:4.36.2" } plugins { create("grpc") {
  artifact = "io.grpc:protoc-gen-grpc-java:1.77.1" } } }` (khớp grpc 1.77.1 starter kéo về).
- `options.errorprone.excludedPaths.set(".*/grpc/gen/.*")` — generated code không bị NullAway/style
  bắt lỗi.

Config (`application.yml`):

```yaml
spring:
  servlet.multipart: { max-file-size: 8MB, max-request-size: 9MB }
  grpc.client.channels.embedder:
    address: ${EMBED_GRPC_ADDRESS:127.0.0.1:9091}
    default-deadline: 5s
```

Code (đặt trong `features/catalog/search/`):

- `EmbeddingGrpcConfig` — bean `EmbeddingServiceBlockingStub` từ
  `GrpcChannelFactory.createChannel("embedder")`.
- `EmbeddingClient` — gọi stub, đổi `StatusRuntimeException` thành `ResponseStatusException`:
  `UNAVAILABLE`/`DEADLINE_EXCEEDED` → 503, `INVALID_ARGUMENT` → 400, `RESOURCE_EXHAUSTED` → 413.
  Vector sai chiều (≠512) → 502.
- `ImageSearchController` — `POST /api/search/products/by-image`, multipart `file`, `limit` 1–100
  (default 20). Content-type không phải `image/*` → 415; file rỗng → 400.
- `ImageSearchService` — chặn size lần nữa, embed, truy vấn, map.
- Repository — dựng đúng CTE `prices`/`covers` + `sort_order` như `ProductSearchRepository`:

```sql
ORDER BY pe.embedding <=> :vec LIMIT :overFetch   -- overFetch = limit * 3
```

  Điều kiện: `pe.embedding IS NOT NULL`, `p.status = 'ACTIVE'`, `s.status = 'APPROVED'`. Dedupe
  theo `product_id` giữ distance nhỏ nhất rồi cắt về `limit`. `similarity = 1 - distance`.
- DTO `ImageSearchResponse(List<Hit>)`, `Hit(id, name, slug, minPrice, defaultImageUrl,
  similarity)`. Không `total`, không phân trang.

## Giới hạn và mã lỗi

| Tình huống | Xử lý |
|---|---|
| Ảnh > 8MB | 413 (multipart limit + check service); worker `RESOURCE_EXHAUSTED` nếu vẫn lọt |
| Không decode được / không phải ảnh | worker `INVALID_ARGUMENT` → 400 |
| Vector sai chiều | 502 |
| Worker chết | `UNAVAILABLE` → 503; BM25 vẫn chạy |
| Quá 5s | `DEADLINE_EXCEEDED` → 503 |
| Burst | `maximum_concurrent_rpcs=4` + pool 4, consumer dùng chung pool |

Message size 8MB cả hai chiều. Health service để SOURCE = SERVING; backend **không** dựng readiness
gate. Log 1 dòng debug mỗi RPC (size, duration, dims), warn khi decode fail.

## Test

- Python (không cần torch): servicer với fake embedder — thành công, bytes rỗng, không phải ảnh,
  quá size, decode lỗi. Một test dựng server in-process port ngẫu nhiên
  và gọi bằng stub thật. Một test `main()` dừng được cả server lẫn consumer.
- Java: `EmbeddingClient` với `InProcessServerBuilder` (thành công + từng loại lỗi → đúng status);
  repository với Testcontainers Postgres (thứ tự, dedupe, lọc ACTIVE/APPROVED, loại NULL);
  controller slice (415/400/413/503); test so hash proto hai repo.
- E2E tay: chạy worker, search lại chính ảnh của một product → product đó phải đứng đầu.

## Rủi ro và điểm chưa làm

- **Recall:** over-fetch 3× rồi dedupe là mẹo, chưa phải lọc thật. Sản phẩm nhiều ảnh sẽ chiếm chỗ
  trong top-N.
- **Hybrid ranking** (BM25 + vector) chưa làm; hiện là hai endpoint rời.
- Chưa có ngưỡng similarity tối thiểu — ảnh query lạc đề vẫn trả đủ N kết quả.
- **CPU tranh chấp:** search và consumer dùng chung pool 4; một batch 16 ảnh làm p95 của search
  tăng.
- Không auth/TLS. Tách host là phải thêm token + TLS trước.
- Cache vector tạm hoãn: mỗi lần search đều chạy model.

## Ngoài phạm vi

- Ghi vector qua gRPC (vẫn pgmq).
- Text embedding, search đa phương thức.
- Gợi ý "sản phẩm tương tự" trên trang chi tiết sản phẩm.
