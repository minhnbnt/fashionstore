-- V12__site_content.sql
-- Module quản trị: banner, popup, trang tĩnh (giới thiệu, chính sách...), FAQ.
-- V11 thuộc nhánh giám sát đơn hàng, flyway bật out-of-order nên merge thứ tự nào cũng chạy.

CREATE TABLE site_banner (
    id UUID PRIMARY KEY,
    placement TEXT NOT NULL CHECK (placement IN ('HOME_CAROUSEL', 'HOME_STRIP')),
    title TEXT NOT NULL,
    image_key TEXT NOT NULL,
    mobile_image_key TEXT,
    link_url TEXT,
    sort_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    updated_by UUID REFERENCES user_profile (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (ends_at IS NULL OR starts_at IS NULL OR ends_at > starts_at)
);
CREATE INDEX idx_site_banner_placement ON site_banner (placement, sort_order, created_at);

CREATE TABLE site_popup (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    title TEXT,
    body TEXT,
    image_key TEXT,
    cta_label TEXT,
    link_url TEXT,
    audience TEXT NOT NULL CHECK (audience IN ('ALL', 'GUEST', 'MEMBER')),
    frequency TEXT NOT NULL CHECK (frequency IN ('EVERY_VISIT', 'ONCE_PER_SESSION', 'ONCE_PER_DAY', 'ONCE')),
    delay_seconds INT NOT NULL DEFAULT 0 CHECK (delay_seconds BETWEEN 0 AND 60),
    priority INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    updated_by UUID REFERENCES user_profile (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (title IS NOT NULL OR image_key IS NOT NULL),
    CHECK (ends_at IS NULL OR starts_at IS NULL OR ends_at > starts_at)
);

CREATE TABLE site_page (
    id UUID PRIMARY KEY,
    slug TEXT NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    meta_description TEXT,
    footer_group TEXT NOT NULL CHECK (footer_group IN ('ABOUT', 'POLICY', 'SUPPORT', 'NONE')),
    sort_order INT NOT NULL DEFAULT 0,
    status TEXT NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED')),
    published_at TIMESTAMPTZ,
    updated_by UUID REFERENCES user_profile (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE faq_item (
    id UUID PRIMARY KEY,
    category TEXT NOT NULL CHECK (category IN ('ORDER', 'PAYMENT', 'SHIPPING', 'RETURN', 'ACCOUNT', 'OTHER')),
    question TEXT NOT NULL,
    answer TEXT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    published BOOLEAN NOT NULL DEFAULT TRUE,
    updated_by UUID REFERENCES user_profile (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Nội dung khởi tạo để footer có link ngay; admin sửa lại cho đúng thực tế.
INSERT INTO site_page (id, slug, title, content, meta_description, footer_group, sort_order, status, published_at)
VALUES
    (gen_random_uuid(), 'gioi-thieu', 'Giới thiệu nét',
     E'# Về nét\n\nnét là sàn thời trang tập hợp nhiều shop quần áo trong nước, giúp bạn tìm đồ mặc hằng ngày với giá rõ ràng và giao hàng toàn quốc.\n\n## Chúng tôi làm gì\n\n- Kiểm duyệt shop trước khi mở bán.\n- Gỡ sản phẩm vi phạm (hàng giả, thông tin sai lệch).\n- Hỗ trợ đổi trả khi hàng không đúng mô tả.',
     'nét là sàn thời trang nhiều shop, kiểm duyệt người bán và hỗ trợ đổi trả.', 'ABOUT', 1, 'PUBLISHED', now()),
    (gen_random_uuid(), 'chinh-sach-doi-tra', 'Chính sách đổi trả',
     E'# Chính sách đổi trả\n\n## Điều kiện\n\n- Gửi yêu cầu trong **30 ngày** kể từ khi nhận hàng.\n- Sản phẩm còn nguyên tem, mác, chưa qua sử dụng hoặc giặt.\n\n## Quy trình\n\n1. Vào **Tài khoản → Đơn hàng**, chọn đơn và gửi yêu cầu trả hàng kèm lý do.\n2. Shop xem xét và phản hồi.\n3. Khi shop nhận lại hàng, tiền được hoàn theo phương thức thanh toán ban đầu.',
     'Đổi trả trong 30 ngày, sản phẩm còn nguyên tem mác.', 'POLICY', 1, 'PUBLISHED', now()),
    (gen_random_uuid(), 'chinh-sach-van-chuyen', 'Chính sách vận chuyển',
     E'# Chính sách vận chuyển\n\n- Giao hàng toàn quốc, thanh toán khi nhận hàng (COD).\n- Mỗi shop tự đóng gói và gửi hàng; thời gian giao dự kiến 2–5 ngày làm việc.\n- Phí vận chuyển hiển thị ở bước đặt hàng.',
     'Giao hàng toàn quốc, COD, 2–5 ngày làm việc.', 'POLICY', 2, 'PUBLISHED', now()),
    (gen_random_uuid(), 'chinh-sach-bao-mat', 'Chính sách bảo mật',
     E'# Chính sách bảo mật\n\n- Chúng tôi chỉ thu thập thông tin cần để giao hàng và chăm sóc khách hàng: họ tên, số điện thoại, địa chỉ, email.\n- Thông tin chỉ chia sẻ với shop bán hàng và đơn vị vận chuyển của đơn hàng đó.\n- Bạn có thể yêu cầu xem, sửa hoặc xóa dữ liệu cá nhân qua mục Hỗ trợ.',
     'Thông tin cá nhân chỉ dùng để giao hàng và chăm sóc khách hàng.', 'POLICY', 3, 'PUBLISHED', now()),
    (gen_random_uuid(), 'dieu-khoan-su-dung', 'Điều khoản sử dụng',
     E'# Điều khoản sử dụng\n\nKhi dùng nét, bạn đồng ý:\n\n- Cung cấp thông tin chính xác khi đặt hàng.\n- Không dùng sàn cho mục đích gian lận hoặc bán hàng cấm.\n- nét có quyền khóa tài khoản, shop hoặc gỡ sản phẩm vi phạm.',
     'Điều khoản khi mua và bán trên nét.', 'POLICY', 4, 'PUBLISHED', now());

INSERT INTO faq_item (id, category, question, answer, sort_order)
VALUES
    (gen_random_uuid(), 'ORDER', 'Làm sao để đặt hàng?',
     'Chọn sản phẩm, chọn size/màu, bấm **Mua ngay** rồi điền địa chỉ giao hàng. Mỗi đơn chỉ gồm hàng của một shop.', 1),
    (gen_random_uuid(), 'ORDER', 'Tôi có hủy đơn được không?',
     'Được, khi shop chưa xác nhận đơn. Vào **Tài khoản → Đơn hàng** và chọn hủy.', 2),
    (gen_random_uuid(), 'PAYMENT', 'nét hỗ trợ những hình thức thanh toán nào?',
     'Hiện tại nét hỗ trợ thanh toán khi nhận hàng (COD).', 1),
    (gen_random_uuid(), 'SHIPPING', 'Bao lâu thì tôi nhận được hàng?',
     'Thường từ 2–5 ngày làm việc tùy khu vực. Xem thêm [Chính sách vận chuyển](/pages/chinh-sach-van-chuyen).', 1),
    (gen_random_uuid(), 'RETURN', 'Tôi muốn đổi size thì làm thế nào?',
     'Gửi yêu cầu trả hàng trong 30 ngày rồi đặt lại size phù hợp. Chi tiết ở [Chính sách đổi trả](/pages/chinh-sach-doi-tra).', 1),
    (gen_random_uuid(), 'ACCOUNT', 'Tôi quên mật khẩu?',
     'Ở trang đăng nhập, bấm **Quên mật khẩu** và làm theo hướng dẫn gửi qua email.', 1);
