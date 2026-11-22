# Product Q&A (1.5.3 Customer & 2.7.3 Shop) — Design Spec

**Date:** 2026-11-18  
**Scope:** End-to-end Product Q&A for Customer PDP (`1.5.3`) and Seller Center (`2.7.3`), including real-time notifications and deep-link navigation.

## 1. Intent & Scope

- **Customer Q&A (1.5.3):** Allow shoppers to browse, search, ask questions, and let verified buyers answer publicly directly on the Product Detail Page (PDP), placed below the Product Reviews section (`ProductTabs`).
- **Shop Q&A (2.7.3):** Provide a dedicated Q&A management workspace in Seller Center where shop owners and authorized CSKH staff can monitor response metrics, filter/search questions across shop products, reply (including follow-ups), edit/delete only their own Shop answers, close/reopen questions, and jump straight to new questions from real-time notifications.
- **Explicit Non-Goals:**
  - **Violation reporting & Admin Q&A moderation:** Explicitly excluded per product owner decision (`bỏ chức năng báo cáo Q&A vi phạm`).
  - Media attachments in Q&A (plain text only, 1–1,000 characters by default).
  - Upvoting/helpful voting on Q&A items.

## 2. Key Product & Architectural Decisions

1. **Fixed content limit:** `1..1000` characters. All text inputs are trimmed before validation; empty or whitespace-only content is rejected (`400 Bad Request`).
2. **Verified Buyer rule (`QNA-C03`):** Reuses `OrderService.hasDeliveredAndReceivedProduct(buyerId, productId)` — a customer is a verified buyer for a product if and only if they have at least one order containing that product with `status = DELIVERED` and `received_at IS NOT NULL` (consistent with the Reviews domain).
3. **Question status lifecycle:**
   - `UNANSWERED`: Default when created; means **no Shop answer exists yet** (even if verified buyers have replied).
   - `ANSWERED`: Automatically set when Shop submits an answer (`QNA-S04`). If Shop deletes its last remaining answer while the question is not closed, status reverts to `UNANSWERED`.
   - `CLOSED`: Set by Shop when resolved (`closed_at`, `closed_by` recorded). While closed, neither customers nor Shop can post new replies (`409 Conflict`), and customer edits/deletes are blocked. Shop may reopen a closed question (recomputing status to `ANSWERED` if any Shop answer exists, else `UNANSWERED`).
4. **Ownership & edit/delete rules (`QNA-C02`, `QNA-C06`, `QNA-S05`):**
   - **Customer question:** Only the asker (`asker_id`) can edit or delete, and only while the question has **0 answers** and is not `CLOSED`. Non-owners receive `403 Forbidden`; answered/closed questions return `409 Conflict`.
   - **Verified buyer answer:** Only the author (`author_id`, `author_role = VERIFIED_BUYER`) can edit or delete while parent question is not `CLOSED`.
   - **Shop answer:** Only shop members with `ShopPermission.REVIEW_REPLY` (or `OWNER`) can create/edit/delete answers of their own shop, and strictly where `author_role = SHOP`. Shop cannot edit/delete customer questions or customer answers (`403 Forbidden`).
5. **Shop RBAC reuse (`QNA-S01`, `QNA-S02`, `QNA-S07`):**
   - Viewing Q&A list, question detail, product filter options, and stats requires `ShopPermission.REVIEW_READ`.
   - Answering, editing/deleting Shop answers, and closing/reopening questions requires `ShopPermission.REVIEW_REPLY`.
   - Reusing `REVIEW_READ` / `REVIEW_REPLY` grants seamless access to `OWNER`, `Manager`, and `CSKH` roles without breaking existing custom roles in DB. Product filter options use a dedicated `/api/seller/qna/products` endpoint so CSKH staff without `PRODUCT_READ` never hit `403`.
6. **Notifications & Deduplication (`QNA-S06`, 2.7.3 §2.5):**
   - **New question (`QNA_NEW_QUESTION`):** Sent with `NotificationCategory.SELLING` to `shops.findActiveUserIdsByShop(shopId)`, title `"Sản phẩm có câu hỏi mới"`, body containing product name, asker display name, and truncated question snippet (max 120 chars), link `"/shop?shopId={shopId}&tab=qna&questionId={questionId}"`, `ref_id = questionId`.
   - **New answer (`QNA_NEW_ANSWER`):** Sent with `NotificationCategory.ACTIVITY` to `question.askerId` (skipped if `authorId.equals(question.askerId)`), title `"Câu hỏi của bạn có phản hồi mới"`, body indicating Shop or verified buyer name + truncated answer snippet, link `"/product/{productId}?questionId={questionId}#product-qna"`, `ref_id = answerId`.
   - **Idempotency:** Partial unique index `uq_notification_qna_dedup` on `user_notification (user_id, type, ref_id) WHERE ref_id IS NOT NULL AND type IN ('QNA_NEW_QUESTION', 'QNA_NEW_ANSWER')` combined with `INSERT ... ON CONFLICT DO NOTHING` ensures zero duplicate notifications on retries or data syncs. Deleting a question or answer removes notifications matching its `ref_id`.
7. **PDP Placement & Deep-Linking:**
   - Placed as a standalone section (`<ProductQnaSection productId={productId} />` with `id="product-qna"`) directly below `<ProductTabs product={product} />` in `ProductDetail.tsx`, keeping Q&A visible regardless of active product tab.
   - Customer notification links with `?questionId=<id>#product-qna` scroll to `#product-qna` and auto-expand/highlight the matching question.
   - Seller notification links with `/shop?shopId=<shopId>&tab=qna&questionId=<id>` open `SellerQnaTab` and directly load/open the matching question detail modal via `GET /api/seller/qna/questions/{questionId}`.

## 3. Data Model (`V46__product_qna.sql`)

```sql
CREATE TABLE product_question (
    id UUID PRIMARY KEY,
    code TEXT NOT NULL UNIQUE,
    product_id UUID NOT NULL,
    shop_id UUID NOT NULL REFERENCES shop (id) ON DELETE CASCADE,
    asker_id UUID NOT NULL REFERENCES user_profile (id) ON DELETE CASCADE,
    asker_display_name TEXT NOT NULL,
    content TEXT NOT NULL CHECK (char_length(btrim(content)) BETWEEN 1 AND 1000),
    status TEXT NOT NULL DEFAULT 'UNANSWERED'
        CHECK (status IN ('UNANSWERED', 'ANSWERED', 'CLOSED')),
    closed_at TIMESTAMPTZ,
    closed_by UUID REFERENCES user_profile (id) ON DELETE SET NULL,
    edited_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_product_question_product
        FOREIGN KEY (shop_id, product_id) REFERENCES product (shop_id, id) ON DELETE CASCADE
);

CREATE TABLE product_answer (
    id UUID PRIMARY KEY,
    question_id UUID NOT NULL REFERENCES product_question (id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES user_profile (id) ON DELETE CASCADE,
    author_display_name TEXT NOT NULL,
    author_role TEXT NOT NULL CHECK (author_role IN ('SHOP', 'VERIFIED_BUYER')),
    verified_buyer BOOLEAN NOT NULL DEFAULT FALSE,
    content TEXT NOT NULL CHECK (char_length(btrim(content)) BETWEEN 1 AND 1000),
    edited_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_product_answer_verified_role
        CHECK (
            (author_role = 'VERIFIED_BUYER' AND verified_buyer = TRUE)
            OR (author_role = 'SHOP' AND verified_buyer = FALSE)
        )
);

CREATE INDEX idx_product_question_product_created
    ON product_question (product_id, created_at DESC, id DESC);
CREATE INDEX idx_product_question_shop_status_created
    ON product_question (shop_id, status, created_at DESC, id DESC);
CREATE INDEX idx_product_question_shop_product_created
    ON product_question (shop_id, product_id, created_at DESC);
CREATE INDEX idx_product_answer_question_created
    ON product_answer (question_id, created_at ASC, id ASC);

CREATE UNIQUE INDEX uq_notification_qna_dedup
    ON user_notification (user_id, type, ref_id)
    WHERE ref_id IS NOT NULL AND type IN ('QNA_NEW_QUESTION', 'QNA_NEW_ANSWER');
```

## 4. API Surface

### Customer Endpoints (`/api/products/{productId}/questions`)
| Method & Path | Auth | Behavior |
|---|---|---|
| `GET /api/products/{productId}/questions` | Public (optional JWT) | List newest-first paginated Q&A for active product (`q`, `page=0`, `size=10`). Searches both question & answer text. Returns `canAnswerAsBuyer` & `maxContentLength`. |
| `POST /api/products/{productId}/questions` | JWT | Ask question on active product. Returns `201`. Notifies Shop (`QNA_NEW_QUESTION`). |
| `PUT /api/products/{productId}/questions/{questionId}` | JWT | Edit own unanswered, non-closed question. Returns `200`. |
| `DELETE /api/products/{productId}/questions/{questionId}` | JWT | Delete own unanswered, non-closed question & its notification. Returns `204`. |
| `POST /api/products/{productId}/questions/{questionId}/answers` | JWT | Verified buyer posts answer on non-closed question. Returns `201`. Notifies asker (`QNA_NEW_ANSWER`). |
| `PUT /api/products/{productId}/questions/{questionId}/answers/{answerId}` | JWT | Edit own `VERIFIED_BUYER` answer on non-closed question. Returns `200`. |
| `DELETE /api/products/{productId}/questions/{questionId}/answers/{answerId}` | JWT | Delete own `VERIFIED_BUYER` answer on non-closed question. Returns `204`. |

### Seller Endpoints (`/api/seller/qna`)
| Method & Path | Permission | Behavior |
|---|---|---|
| `GET /api/seller/qna/questions` | `REVIEW_READ` | Paginated shop questions with `shopId`, `status`, `productId`, `from`, `to`, `q`, `sort` (`NEWEST`/`OLDEST`), `page`, `size`. |
| `GET /api/seller/qna/questions/{questionId}` | `REVIEW_READ` | Single question detail with full answer list (supports notification deep-links). |
| `GET /api/seller/qna/products` | `REVIEW_READ` | CSKH-safe product dropdown options (`id`, `name`, `questionCount`). |
| `GET /api/seller/qna/stats` | `REVIEW_READ` | `totalQuestions`, `unansweredCount`, `answeredCount`, `closedCount`, `responseRate`. |
| `POST /api/seller/qna/questions/{questionId}/answers` | `REVIEW_REPLY` | Shop posts public answer (`SHOP`), transitions `UNANSWERED -> ANSWERED`, audits, notifies asker. Returns `201`. |
| `PUT /api/seller/qna/answers/{answerId}` | `REVIEW_REPLY` | Edit own Shop answer (`author_role = SHOP` only), audits. Returns `200`. |
| `DELETE /api/seller/qna/answers/{answerId}` | `REVIEW_REPLY` | Delete own Shop answer (`author_role = SHOP` only), reverts status to `UNANSWERED` if no Shop answers remain and not closed, audits. Returns `204`. |
| `PATCH /api/seller/qna/questions/{questionId}/close` | `REVIEW_REPLY` | Close question (`CLOSED`), audits. Returns `200`. |
| `PATCH /api/seller/qna/questions/{questionId}/reopen` | `REVIEW_REPLY` | Reopen closed question (`ANSWERED` if Shop answer exists else `UNANSWERED`), audits. Returns `200`. |
