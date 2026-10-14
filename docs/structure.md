# Structure — tổ chức thư mục backend

Package gốc: `com.minhnbnt.fashionstore`. Hai loại package, không lẫn lộn:

```
src/main/java/com/minhnbnt/fashionstore/
├── features/            # lát cắt dọc (vertical slice), mỗi domain 1 thư mục
│   ├── identity/        # domain đơn giản → để phẳng
│   │   ├── UserController.java
│   │   ├── UserService.java
│   │   └── UserProfileRepository.java
│   ├── shop/            # domain có luật nghiệp vụ → chia 3 lớp
│   │   ├── controller/
│   │   ├── service/
│   │   └── repository/
│   ├── catalog/
│   └── order/
├── model/               # entity dùng chung, không phụ thuộc ai
├── dto/                 # request/response records, chia theo domain: dto/identity/, dto/shop/...
├── security/            # đồ auth dùng chung (CurrentUser, ShopAccessChecker...)
├── infrastructure/      # biên hạ tầng (S3Storage, S3BucketWebsite...)
└── config/              # SecurityConfig, S3Config, OpenApiConfig...
```

## Luật duy nhất cần nhớ

- Domain **1 entity + CRUD mỏng** → để phẳng trong `features/<domain>/`, cấm tạo `controller/`, `service/`, `repository/` cho 3 file.
- Domain **có luật nghiệp vụ thật** (duyệt shop, trừ kho, máy trạng thái đơn) → chia `controller/` + `service/` + `repository/`.
- `model/` + `dto/` nằm ngoài domain, dùng chung. Service domain này cấm gọi repository domain khác — cần gì thì gọi qua service bên đó.
- Test mirror theo main: `src/test/.../features/identity/...`.
- Migration Flyway: `src/main/resources/db/migration/V<n>__<mô-tả>.sql`, chạy 1 lần, không sửa file đã merge.
