# fashionstore (backend) — AGENTS.md

Spring Boot 4 + Data JDBC + Postgres + GoTrue JWT + versitygw S3. Marketplace nhiều shop bán quần áo.

## Đọc thêm (chi tiết chia file để khỏi phình)

- `docs/structure.md` — tổ chức thư mục: `features/` phẳng hay 3 lớp, đồ chung để đâu, migration để đâu.
- `docs/code-guide.md` — luật viết code: explicit, Lombok allowlist, NullAway, TDD, annotation springdoc.
- `docs/data.md` — aggregate JDBC, schema đã chốt, search BM25, S3, test DB.
- `docs/api-endpoints.md` — danh sách API theo flow + checkbox tiến độ.

## Quy tắc nhanh

- Cấm ternary (`? :`): `if` + early `return`.
- Entity cấm `@Data`/`@Setter`/`@EqualsAndHashCode`; mặc định non-null (NullAway), nullable gắn `@Nullable`.
- Exception/message tiếng Anh. DB chỉ lưu S3 key, không lưu URL.
- Testcontainers dùng image Postgres của project, không đổi sang stock image.
- `./gradlew test` xanh rồi mới xong việc.
