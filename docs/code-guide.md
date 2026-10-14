# Code guide — luật viết code

## Explicit over clever

Code đọc nhiều hơn viết. Chọn bản đọc hiểu ngay lần đầu, dù dài hơn một dòng.

- **Cấm ternary (`? :`).** Viết `if` + early `return`.
- Một dòng một statement. Không nhồi nhiều bước vào một biểu thức cho gọn.
- Chỗ nào cần comment để biện minh thì chỗ đó nên đơn giản hơn.

```java
// avoid
var key = UUID.randomUUID() + "."
    + (subtype.contains("+") ? subtype.substring(0, subtype.indexOf('+')) : subtype);

// prefer
var key = UUID.randomUUID() + "." + extensionOf(contentType);
```

## Style chung

- Indent 4 space, `var` cho local, `record` cho DTO.
- Dòng code quá 120 ký tự thì xuống dòng. Annotation dài cũng xuống.
- Các bước khác nhau trong method cách nhau 1 dòng trống.
- Consistent với code xung quanh hơn sở thích cá nhân.

## Lombok — allowlist, không phải thích gì dùng nấy

- Được dùng: `@Getter`, `@RequiredArgsConstructor`, `@NonNull` (param service), `@Slf4j` (chỗ log thật).
- Cấm trên entity: `@Data`, `@EqualsAndHashCode` (hashCode đổi theo field mutable, bỏ vào Set là mất dấu), `@Setter` (phá behavior method), `@Builder` (entity dùng factory `create(...)` + `@PersistenceCreator`).

## NullAway — mặc định non-null

- Mọi param/return trong `com.minhnbnt` mặc định là non-null. Nullable thì gắn `@Nullable` (JSpecify) tường minh, không thì fail ngay lúc compile.
- NullAway chạy mức ERROR cho main, WARNING cho test. Xem cấu hình ở `build.gradle.kts`.
- `StringConcatToTextBlock` đang tắt vì Error Prone 2.50.0 tự crash trên JDK 25 — lên bản mới thì thử bật lại.

## TDD — test trước, code sau

- Không code production khi chưa có test đỏ. Đỏ phải đúng bệnh (thiếu feature, không phải typo).
- Controller test kiểu slice (`@WebMvcTest` + mock service). Service test chạy DB thật qua Testcontainers.
- Sửa xong chạy full `./gradlew test`, không chỉ file mình đụng.

## Controller & API

- Mỗi endpoint đủ annotation springdoc: `@Tag` (controller), `@Operation(summary, description)`, `@ApiResponse` từng mã, `@Parameter` cho path/query, `@Schema` từng field DTO. Thiếu là chưa xong.
- Endpoint cần auth gắn `@SecurityRequirement(name = "bearerAuth")` (scheme khai 1 lần ở `config/OpenApiConfig.java`).
- `id` user luôn lấy từ JWT sub, không tin id client gửi. Mẫu chuẩn ở controller:
  ```java
  var subject = Objects.requireNonNull(jwt.getSubject());
  var userId = UUID.fromString(subject);
  ```
- Admin check bằng `hasRole('ADMIN')` ở `SecurityConfig` (claim `app_metadata.role` map sang authority qua `JwtAuthenticationConverter`). Role theo shop (OWNER/STAFF) vẫn check tay ở `ShopAccessChecker` vì nằm trong DB.
- Exception/message trả về **tiếng Anh** hết.
- Body validate bằng `jakarta.validation` + `@Valid` (`spring-boot-starter-validation` đã có).
