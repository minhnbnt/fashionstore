# Tìm sản phẩm bằng ảnh qua gRPC — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Khách gửi ảnh → backend gọi gRPC sang embed-worker lấy vector Fashion-CLIP → truy vấn diskann trả sản phẩm giống nhất.

**Architecture:** gRPC server mới trong worker (thuần inference, stateless, bind loopback, pool 4). Backend dùng `spring-grpc` client stub, bind vector bằng `com.pgvector:pgvector`, query top-N rồi dedupe theo product. Đường ghi vector vẫn là pgmq, không đổi.

**Tech Stack:** Python 3.14 + `grpcio` + `grpcio-tools` + Pillow; Spring Boot 4.1.1 + `spring-boot-starter-grpc-client` (do BOM quản) + `com.pgvector:pgvector:0.1.6`.

**Spec:** `docs/superpowers/specs/2026-11-15-image-search-grpc-design.md`

## Global Constraints

- Proto canonical ở `embed-worker/proto/fashion_clip/v1/embedding.proto`; bản copy ở `backend/src/main/proto/fashion_clip/v1/embedding.proto`. Sửa contract ở worker rồi copy sang.
- Vector 512 chiều, L2-normalize. Model trả sai chiều → backend 502.
- Ảnh tối đa 8MB. Deadline client 5s. Concurrency tối đa 4 (`maximum_concurrent_rpcs=4`, pool 4).
- gRPC bind `127.0.0.1:${GRPC_PORT:-9091}`, plaintext, không auth.
- Không đổi đường ghi vector (trigger + pgmq + worker consumer).
- Không dùng Spring AI.
- Vietnamese cho log/doc/comment, theo lối hiện có của cả hai repo.

## Review Focus

1. Ảnh PNG có alpha/palette, GIF, ảnh xám → `convert("RGB")` không được nổ, phải trả vector.
2. Ảnh đúng sát ngưỡng 8MB (multipart tính cả envelope) → 413, không phải 500.
3. Ảnh gần như đen (vector gần 0) → cosine distance không NaN, vẫn trả kết quả.
4. Worker trả vector sai chiều → 502, không để lỗi SQL cast.
5. Product nhiều ảnh gần nhau → mỗi product xuất hiện một lần, giữ distance nhỏ nhất.
6. Product `HIDDEN`/`BANNED` hoặc shop chưa `APPROVED` có vector → bị loại khỏi kết quả.
7. File không phải ảnh nhưng content-type `image/png` → 400, không 500.

---

## File Structure

Worker (`/home/minhnbnt/fashionstore/embed-worker`):
- `proto/fashion_clip/v1/embedding.proto` — contract.
- `scripts/gen_proto.sh` — sinh stub vào `app/grpc/gen/`.
- `app/grpc/gen/` — mã sinh, commit.
- `app/service/search_embedding.py` — validate + decode + gọi model.
- `app/grpc/embedding_service.py` — servicer, map exception → status.
- `app/grpc/server.py` — dựng server, options, health, bind, stop.
- `app/worker/main.py` — start server trước consumer, shutdown cả hai.

Backend (`/home/minhnbnt/fashionstore/backend`):
- `src/main/proto/fashion_clip/v1/embedding.proto` — bản copy.
- `build.gradle.kts` — plugin protobuf, deps, exclude errorprone.
- `src/main/resources/application.yml` — multipart limit, channel `embedder`.
- `src/main/java/com/minhnbnt/fashionstore/infrastructure/EmbeddingGrpcConfig.java`
- `src/main/java/com/minhnbnt/fashionstore/infrastructure/EmbeddingClient.java`
- `.../search/ImageSearchService.java`
- `.../search/ImageSearchRepository.java`
- `.../controller/ImageSearchController.java`
- `src/main/java/com/minhnbnt/fashionstore/dto/catalog/ImageSearchRequest.java`, `ImageSearchResponse.java`, `ImageSearchHit.java`
- `docs/catalog.md`, `docs/api-endpoints.md`, `docs/openapi.json` — cập nhật doc.

---

### Task 1: Proto + codegen + test chống lệch contract

**Files:**
- Create: `embed-worker/proto/fashion_clip/v1/embedding.proto`
- Create: `embed-worker/scripts/gen_proto.sh`
- Create: `embed-worker/app/grpc/__init__.py`
- Create: `embed-worker/tests/test_proto_sync.py`
- Modify: `embed-worker/pyproject.toml`
- Create (generated): `embed-worker/app/grpc/gen/*`

**Interfaces:**
- Produces: module `app.grpc.gen.embedding_pb2` (`EmbedImageRequest`, `EmbedImageReply`), `app.grpc.gen.embedding_pb2_grpc` (`EmbeddingServiceServicer`, `add_EmbeddingServiceServicer_to_server`, `EmbeddingServiceStub`).

- [ ] **Step 1: Viết proto**

```proto
syntax = "proto3";

package fashion_clip.v1;

option java_package = "com.minhnbnt.fashionstore.grpc";
option java_multiple_files = true;

message EmbedImageRequest {
  bytes image = 1;
}

message EmbedImageReply {
  repeated float vector = 1;  // 512 chiều, đã L2-normalize
  string model = 2;           // "fashion-clip-vit-b32"
}

service EmbeddingService {
  rpc EmbedImage(EmbedImageRequest) returns (EmbedImageReply);
}
```

- [ ] **Step 2: Thêm deps và cấu hình tool**

Trong `pyproject.toml`, thêm vào `dependencies`: `"grpcio>=1.77"`, `"grpcio-health-checking>=1.77"`; vào `dev`: `"grpcio-tools>=1.77"`. Thêm `exclude = ["app/grpc/gen"]` dưới `[tool.ruff]`.

- [ ] **Step 3: Viết script sinh stub**

`scripts/gen_proto.sh`:

```bash
#!/usr/bin/env bash
# Sinh stub Python từ proto. Chạy lại mỗi khi sửa proto.
set -euo pipefail
cd "$(dirname "$0")/.."
uv run python -m grpc_tools.protoc \
  -I proto \
  --python_out=app/grpc/gen \
  --grpc_python_out=app/grpc/gen \
  --pyi_out=app/grpc/gen \
  proto/fashion_clip/v1/embedding.proto
# protoc sinh import kiểu `from fashion_clip.v1 import ...`; app dùng `app.grpc.gen.*` nên vá lại.
sed -i 's/^from fashion_clip\.v1 import/from app.grpc.gen import/' app/grpc/gen/fashion_clip/v1/embedding_pb2_grpc.py
touch app/grpc/gen/fashion_clip/__init__.py app/grpc/gen/fashion_clip/v1/__init__.py app/grpc/gen/__init__.py
```

- [ ] **Step 4: Chạy script và kiểm tra import**

Run: `chmod +x scripts/gen_proto.sh && ./scripts/gen_proto.sh && uv sync --extra dev && uv run python -c "from app.grpc.gen import embedding_pb2_grpc, embedding_pb2; print(embedding_pb2.EmbedImageRequest)"`
Expected: in ra class `EmbedImageRequest`.

- [ ] **Step 5: Viết test chống lệch contract (phía worker)**

`tests/test_proto_sync.py`:

```python
"""Contract proto phải giống bản copy bên backend để hai bên không lệch âm thầm."""

import hashlib
from pathlib import Path

import pytest

HERE = Path(__file__).resolve().parents[1]
OWN = HERE / "proto/fashion_clip/v1/embedding.proto"
COPY = HERE.parent / "backend/src/main/proto/fashion_clip/v1/embedding.proto"


def _digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def test_proto_copy_matches() -> None:
    if not COPY.is_file():
        pytest.skip(f"bản copy backend không có ở {COPY}")
    assert _digest(OWN) == _digest(COPY), "proto đã lệch; chép bản canonical sang backend"
```

- [ ] **Step 6: Chạy test**

Run: `uv run pytest tests/test_proto_sync.py -q`
Expected: SKIP khi backend chưa có bản copy (Task 6 sẽ làm nó PASS).

- [ ] **Step 7: Commit**

```bash
git add proto scripts app/grpc pyproject.toml uv.lock tests/test_proto_sync.py
git commit -m "feat(grpc): proto EmbeddingService + script sinh stub"
```

---

### Task 2: `SearchEmbedder` — validate, decode, embed

**Files:**
- Create: `embed-worker/app/service/search_embedding.py`
- Test: `embed-worker/tests/test_search_embedding.py`

**Interfaces:**
- Consumes: `EmbeddingModel.embed_images(images) -> npt.NDArray[np.float32]` (đã có trong `app/types.py`).
- Produces: `SearchEmbedder(model, *, max_image_bytes=8_388_608)`, method `embed(data: bytes) -> list[float]`; exception `InvalidImage`, `ImageTooLarge`; hằng `EMBED_DIM = 512` dùng lại từ `app.types`.

- [ ] **Step 1: Viết test thất bại**

`tests/test_search_embedding.py`:

```python
from io import BytesIO

import numpy as np
import pytest
from PIL import Image

from app.service.search_embedding import ImageTooLarge, InvalidImage, SearchEmbedder


class StubModel:
    def __init__(self) -> None:
        self.calls = 0

    def embed_images(self, images):
        self.calls += 1
        return np.ones((len(images), 512), dtype=np.float32)


def png(color=(200, 10, 10), mode="RGB") -> bytes:
    buffer = BytesIO()
    Image.new(mode, (8, 8), color).save(buffer, format="PNG")
    return buffer.getvalue()


def test_embed_returns_512_values() -> None:
    model = StubModel()
    vector = SearchEmbedder(model).embed(png())
    assert len(vector) == 512
    assert model.calls == 1


def test_embed_accepts_transparent_png() -> None:
    model = StubModel()
    SearchEmbedder(model).embed(png((0, 0, 0, 0), mode="RGBA"))
    assert model.calls == 1


def test_empty_bytes_is_invalid() -> None:
    with pytest.raises(InvalidImage):
        SearchEmbedder(StubModel()).embed(b"")


def test_non_image_is_invalid() -> None:
    with pytest.raises(InvalidImage):
        SearchEmbedder(StubModel()).embed(b"not an image at all")


def test_oversize_is_rejected_before_decode() -> None:
    model = StubModel()
    embedder = SearchEmbedder(model, max_image_bytes=10)
    with pytest.raises(ImageTooLarge):
        embedder.embed(png())
    assert model.calls == 0
```

- [ ] **Step 2: Chạy test để thấy đỏ**

Run: `uv run pytest tests/test_search_embedding.py -q`
Expected: FAIL — `ModuleNotFoundError: app.service.search_embedding`.

- [ ] **Step 3: Viết implementation**

`app/service/search_embedding.py`:

```python
"""Ảnh query -> vector. Stateless; không đụng DB."""

import logging
from io import BytesIO

from PIL import Image, UnidentifiedImageError
from PIL.Image import Image as PILImage

from app.types import EMBED_DIM, EmbeddingModel

logger = logging.getLogger(__name__)

MAX_IMAGE_BYTES = 8 * 1024 * 1024


class InvalidImage(Exception):
    """Bytes không decode được thành ảnh."""


class ImageTooLarge(Exception):
    """Ảnh vượt ngưỡng cho phép."""


class SearchEmbedder:
    def __init__(self, model: EmbeddingModel, *, max_image_bytes: int = MAX_IMAGE_BYTES) -> None:
        self._model = model
        self._max_image_bytes = max_image_bytes

    def embed(self, data: bytes) -> list[float]:
        if not data:
            raise InvalidImage("ảnh rỗng")
        if len(data) > self._max_image_bytes:
            raise ImageTooLarge(f"ảnh {len(data)} byte vượt {self._max_image_bytes}")

        image = self._decode(data)
        vectors = self._model.embed_images([image])
        vector = [float(value) for value in vectors[0]]
        if len(vector) != EMBED_DIM:
            raise RuntimeError(f"model trả {len(vector)} chiều, cần {EMBED_DIM}")
        return vector

    @staticmethod
    def _decode(data: bytes) -> PILImage:
        try:
            with Image.open(BytesIO(data)) as opened:
                opened.load()
                return opened.convert("RGB")
        except (UnidentifiedImageError, OSError) as exc:
            raise InvalidImage(str(exc)) from exc
```

Cache LRU/TTL **tạm hoãn** (Task 3 giữ lại làm ghi chú, chưa làm).

- [ ] **Step 4: Chạy lại test**

Run: `uv run pytest tests/test_search_embedding.py -q`
Expected: PASS (5 test).

- [ ] **Step 5: Commit**

```bash
git add app/service/search_embedding.py tests/test_search_embedding.py
git commit -m "feat(grpc): SearchEmbedder validate/decode/embed ảnh query"
```

---

### Task 3: Cache vector LRU + TTL — **tạm hoãn**

Chưa làm. Khi cần: `app/service/vector_cache.py` giữ `VectorCache(max_entries=512, ttl_s=600.0,
clock=time.monotonic)` với `get`/`put` có lock; `SearchEmbedder.__init__(model, cache, *,
max_image_bytes)` băm `sha256(bytes)` làm key và chỉ cache thành công; `Settings` thêm
`grpc_cache_entries`, `grpc_cache_ttl_s`.

---

### Task 4: Servicer gRPC + map lỗi

**Files:**
- Create: `embed-worker/app/grpc/embedding_service.py`
- Test: `embed-worker/tests/test_embedding_service.py`

**Interfaces:**
- Consumes: `SearchEmbedder.embed(data)`; `InvalidImage`, `ImageTooLarge`.
- Produces: `EmbeddingServicer(embedder)`, hàm `embed_image(request, context)`; dùng trong Task 5.

- [ ] **Step 1: Viết test thất bại**

`tests/test_embedding_service.py`:

```python
import grpc
import numpy as np
import pytest
from PIL import Image
from io import BytesIO

from app.grpc.embedding_service import EmbeddingServicer
from app.grpc.gen import embedding_pb2
from app.service.search_embedding import SearchEmbedder


class StubModel:
    def embed_images(self, images):
        return np.full((len(images), 512), 1 / np.sqrt(512), dtype=np.float32)


class Context:
    def __init__(self) -> None:
        self.code = None
        self.details = None

    def set_code(self, code):
        self.code = code

    def set_details(self, details):
        self.details = details

    def abort(self, code, details):
        self.set_code(code)
        self.set_details(details)
        raise grpc.RpcError(details)


def servicer() -> EmbeddingServicer:
    return EmbeddingServicer(SearchEmbedder(StubModel()))


def png() -> bytes:
    buffer = BytesIO()
    Image.new("RGB", (8, 8), (10, 20, 30)).save(buffer, format="PNG")
    return buffer.getvalue()


def test_returns_512_values_and_model_name() -> None:
    reply = servicer().EmbedImage(embedding_pb2.EmbedImageRequest(image=png()), Context())
    assert len(reply.vector) == 512
    assert reply.model == "fashion-clip-vit-b32"


def test_empty_image_is_invalid_argument() -> None:
    context = Context()
    with pytest.raises(grpc.RpcError):
        servicer().EmbedImage(embedding_pb2.EmbedImageRequest(image=b""), context)
    assert context.code is grpc.StatusCode.INVALID_ARGUMENT


def test_broken_image_is_invalid_argument() -> None:
    context = Context()
    with pytest.raises(grpc.RpcError):
        servicer().EmbedImage(embedding_pb2.EmbedImageRequest(image=b"xx"), context)
    assert context.code is grpc.StatusCode.INVALID_ARGUMENT


def test_oversize_is_resource_exhausted() -> None:
    embedder = SearchEmbedder(StubModel(), max_image_bytes=10)
    context = Context()
    with pytest.raises(grpc.RpcError):
        EmbeddingServicer(embedder).EmbedImage(
            embedding_pb2.EmbedImageRequest(image=png()), context
        )
    assert context.code is grpc.StatusCode.RESOURCE_EXHAUSTED
```

- [ ] **Step 2: Chạy test để thấy đỏ**

Run: `uv run pytest tests/test_embedding_service.py -q`
Expected: FAIL — chưa có module.

- [ ] **Step 3: Viết implementation**

`app/grpc/embedding_service.py`:

```python
"""Servicer: bytes ảnh -> vector. Chỉ map lỗi, không chứa logic model."""

import logging

import grpc

from app.grpc.gen import embedding_pb2, embedding_pb2_grpc
from app.infrastructure.model import MODEL_NAME
from app.service.search_embedding import ImageTooLarge, InvalidImage, SearchEmbedder

logger = logging.getLogger(__name__)


class EmbeddingServicer(embedding_pb2_grpc.EmbeddingServiceServicer):
    def __init__(self, embedder: SearchEmbedder) -> None:
        self._embedder = embedder

    def EmbedImage(self, request, context):
        try:
            vector = self._embedder.embed(request.image)
        except ImageTooLarge as exc:
            context.abort(grpc.StatusCode.RESOURCE_EXHAUSTED, str(exc))
        except InvalidImage as exc:
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, str(exc))
        except Exception as exc:  # lỗi model/DB không được để gRPC trả UNKNOWN trống
            logger.exception("embed ảnh query thất bại")
            context.abort(grpc.StatusCode.INTERNAL, str(exc))

        return embedding_pb2.EmbedImageReply(vector=vector, model=MODEL_NAME)
```

- [ ] **Step 4: Chạy lại test**

Run: `uv run pytest tests/test_embedding_service.py -q`
Expected: PASS (4 test).

- [ ] **Step 5: Thêm test module-dim sai chiều**

Thêm vào `tests/test_embedding_service.py`:

```python
class WrongDimModel:
    def embed_images(self, images):
        return np.ones((len(images), 256), dtype=np.float32)


def test_wrong_model_dimension_is_internal() -> None:
    embedder = SearchEmbedder(WrongDimModel())
    context = Context()
    with pytest.raises(grpc.RpcError):
        EmbeddingServicer(embedder).EmbedImage(
            embedding_pb2.EmbedImageRequest(image=png()), context
        )
    assert context.code is grpc.StatusCode.INTERNAL
```

Run: `uv run pytest tests/test_embedding_service.py -q`
Expected: PASS (5 test).

- [ ] **Step 6: Commit**

```bash
git add app/grpc/embedding_service.py tests/test_embedding_service.py
git commit -m "feat(grpc): servicer EmbedImage + map lỗi sang status"
```

---

### Task 5: Server, health, shutdown, nối vào `main()`

**Files:**
- Create: `embed-worker/app/grpc/server.py`
- Modify: `embed-worker/app/container.py`
- Modify: `embed-worker/app/worker/main.py`
- Test: `embed-worker/tests/test_grpc_server.py`

**Interfaces:**
- Consumes: `EmbeddingServicer`.
- Produces: `serve(settings, servicer) -> grpc.Server` (đã start, port thật; trả server), `stop(server, grace_s=5)`.

- [ ] **Step 1: Viết test thất bại**

`tests/test_grpc_server.py`:

```python
import grpc
import numpy as np
import pytest
from PIL import Image
from io import BytesIO

from app.config import Settings
from app.grpc.embedding_service import EmbeddingServicer
from app.grpc.gen import embedding_pb2, embedding_pb2_grpc
from app.grpc.server import serve, stop
from app.service.search_embedding import SearchEmbedder


class StubModel:
    def embed_images(self, images):
        return np.zeros((len(images), 512), dtype=np.float32)


def png() -> bytes:
    buffer = BytesIO()
    Image.new("RGB", (8, 8)).save(buffer, format="PNG")
    return buffer.getvalue()


@pytest.fixture
def running():
    settings = Settings(_env_file=None, grpc_port=0)
    servicer = EmbeddingServicer(SearchEmbedder(StubModel()))
    server = serve(settings, servicer)
    yield server
    stop(server, grace_s=0)


def test_round_trip_over_real_socket(running) -> None:
    port = running.add_insecure_port("127.0.0.1:0")
    channel = grpc.insecure_channel(f"127.0.0.1:{port}")
    reply = embedding_pb2_grpc.EmbeddingServiceStub(channel).EmbedImage(
        embedding_pb2.EmbedImageRequest(image=png())
    )
    assert len(reply.vector) == 512


def test_health_reports_serving(running) -> None:
    from grpc_health.v1 import health_pb2, health_pb2_grpc

    port = running.add_insecure_port("127.0.0.1:0")
    channel = grpc.insecure_channel(f"127.0.0.1:{port}")
    reply = health_pb2_grpc.HealthStub(channel).Check(health_pb2.HealthCheckRequest(service=""))
    assert reply.status == health_pb2.HealthCheckResponse.SERVING
```

- [ ] **Step 2: Chạy test để thấy đỏ**

Run: `uv run pytest tests/test_grpc_server.py -q`
Expected: FAIL — chưa có `app.grpc.server`.

- [ ] **Step 3: Viết implementation**

`app/grpc/server.py`:

```python
"""Dựng gRPC server. Chỉ bind loopback: backend chạy cùng host."""

import logging
from concurrent import futures

import grpc
from grpc_health.v1 import health, health_pb2, health_pb2_grpc

from app.config import Settings
from app.grpc.embedding_service import EmbeddingServicer
from app.grpc.gen import embedding_pb2_grpc

logger = logging.getLogger(__name__)


def serve(settings: Settings, servicer: EmbeddingServicer) -> grpc.Server:
    pool = futures.ThreadPoolExecutor(
        max_workers=settings.grpc_max_concurrency, thread_name_prefix="grpc"
    )
    options = [
        ("grpc.max_receive_message_length", settings.grpc_max_image_bytes),
        ("grpc.max_send_message_length", settings.grpc_max_image_bytes),
        ("grpc.so_reuseport", 0),
    ]
    server = grpc.server(
        pool, options=options, maximum_concurrent_rpcs=settings.grpc_max_concurrency
    )
    embedding_pb2_grpc.add_EmbeddingServiceServicer_to_server(servicer, server)

    health_servicer = health.HealthServicer()
    health_pb2_grpc.add_HealthServicer_to_server(health_servicer, server)
    health_servicer.set("", health_pb2.HealthCheckResponse.SERVING)

    bound = server.add_insecure_port(f"127.0.0.1:{settings.grpc_port}")
    if bound == 0:
        raise RuntimeError(f"không bind được 127.0.0.1:{settings.grpc_port}")
    server.start()
    logger.info("gRPC server nghe 127.0.0.1:%d", bound)
    return server


def stop(server: grpc.Server, *, grace_s: float = 5) -> None:
    server.stop(grace_s).wait()
```

- [ ] **Step 4: Chạy test**

Run: `uv run pytest tests/test_grpc_server.py -q`
Expected: PASS (2 test).

- [ ] **Step 5: Nối vào container**

Trong `app/container.py`: thêm import `SearchEmbedder`; trong `from_settings` sau khi tạo `model`:

```python
        embedder = SearchEmbedder(model, max_image_bytes=settings.grpc_max_image_bytes)
```

và thêm vào `Container.__init__` (`self.embedder = embedder`) + `return cls(..., embedder=embedder)`.

- [ ] **Step 6: Sửa `main()` — start server trước, dừng cả hai**

`app/worker/main.py` (thay toàn bộ `main`):

```python
def main() -> None:
    import signal

    import torch

    from app.config import get_settings
    from app.container import Container
    from app.grpc.embedding_service import EmbeddingServicer
    from app.grpc.server import serve, stop

    logging.basicConfig(format="%(asctime)s %(levelname)s %(name)s %(message)s", level=logging.INFO)
    settings = get_settings()
    container = Container.from_settings(settings)
    torch.set_num_threads(settings.grpc_max_concurrency)

    stopping = False

    def request_stop(*_: object) -> None:
        nonlocal stopping
        stopping = True

    signal.signal(signal.SIGTERM, request_stop)
    signal.signal(signal.SIGINT, request_stop)

    container.model.load()
    server = serve(settings, EmbeddingServicer(container.embedder))
    worker = EmbeddingWorker(container.processor, batch_size=settings.embed_batch_size)
    logger.info(
        "embedding worker starting: queue=%s batch_size=%d",
        settings.queue_name,
        settings.embed_batch_size,
    )
    try:
        worker.run_forever(stop=lambda: stopping)
    finally:
        stop(server)
        container.close()
```

- [ ] **Step 7: Test `main()` dừng được, không mở port thật**

Thêm `tests/test_main.py`:

```python
import numpy as np
from PIL import Image
from io import BytesIO

import app.worker.main as main_module


class StubModel:
    def load(self) -> None:
        pass

    def embed_images(self, images):
        return np.zeros((len(images), 512), dtype=np.float32)


class StubProcessor:
    def __init__(self) -> None:
        self.calls = 0

    def process_batch(self, batch_size: int) -> int:
        self.calls += 1
        return 0


class StubContainer:
    def __init__(self) -> None:
        self.model = StubModel()
        self.processor = StubProcessor()
        from app.service.search_embedding import SearchEmbedder

        self.embedder = SearchEmbedder(self.model)
        self.closed = False

    def close(self) -> None:
        self.closed = True


def test_main_starts_server_and_stops_both(monkeypatch) -> None:
    from app.config import Settings

    container = StubContainer()
    stopped: list[bool] = []

    monkeypatch.setattr(main_module, "EmbeddingWorker", lambda *a, **k: _StopAfterOne(container))
    monkeypatch.setattr("app.container.Container.from_settings", classmethod(lambda cls, s: container))
    monkeypatch.setattr("app.grpc.server.serve", lambda settings, servicer: _Server(stopped))
    monkeypatch.setattr("app.grpc.server.stop", lambda server, grace_s=5: stopped.append(True))
    monkeypatch.setattr("app.config.get_settings", lambda: Settings(_env_file=None, grpc_port=0))
    monkeypatch.setattr("torch.set_num_threads", lambda n: None)

    main_module.main()

    assert stopped == [True]
    assert container.closed is True


class _StopAfterOne:
    def __init__(self, container) -> None:
        self.container = container

    def run_forever(self, *, stop) -> None:
        raise SystemExit  # thay bằng vòng lặp 1 nhịp bên dưới


class _Server:
    def __init__(self, stopped: list[bool]) -> None:
        self.stopped = stopped
```

Sửa `_StopAfterOne.run_forever` cho đúng một nhịp:

```python
    def run_forever(self, *, stop) -> None:
        self.container.processor.process_batch(1)
        stop()
```

Run: `uv run pytest tests/test_main.py -q`
Expected: PASS.

- [ ] **Step 8: Chạy toàn bộ test + lint + type**

Run: `uv run pytest -q && uv run ruff check . && uv run pyrefly check`
Expected: tất cả PASS, lint sạch.

- [ ] **Step 9: Commit**

```bash
git add app/grpc/server.py app/container.py app/worker/main.py tests/test_grpc_server.py tests/test_main.py
git commit -m "feat(grpc): server loopback, health, shutdown cùng consumer"
```

---

### Task 6: Backend build + proto copy + codegen

**Files:**
- Create: `backend/src/main/proto/fashion_clip/v1/embedding.proto` (copy)
- Modify: `backend/build.gradle.kts`
- Test: `backend/src/test/java/com/minhnbnt/fashionstore/features/catalog/ProtoSyncTest.java`

**Interfaces:**
- Produces: package Java `com.minhnbnt.fashionstore.grpc` với `EmbeddingServiceGrpc`, `EmbedImageRequest`, `EmbedImageReply`.

- [ ] **Step 1: Copy proto**

```bash
mkdir -p src/main/proto/fashion_clip/v1
cp ../embed-worker/proto/fashion_clip/v1/embedding.proto src/main/proto/fashion_clip/v1/
```

- [ ] **Step 2: Viết test chống lệch (phía backend)**

`src/test/java/com/minhnbnt/fashionstore/features/catalog/ProtoSyncTest.java`:

```java
package com.minhnbnt.fashionstore.features.catalog;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Proto phải giống bản canonical bên embed-worker; repo tách rời nên chỉ so khi có checkout. */
class ProtoSyncTest {

    private static final Path OWN = Path.of("src/main/proto/fashion_clip/v1/embedding.proto");
    private static final Path CANONICAL =
        Path.of("../embed-worker/proto/fashion_clip/v1/embedding.proto");

    @Test
    void copyMatchesCanonical() throws Exception {
        assumeTrue(Files.isRegularFile(CANONICAL), "không có checkout embed-worker");
        assertThat(digest(OWN)).isEqualTo(digest(CANONICAL));
    }

    private static String digest(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
```

- [ ] **Step 3: Chạy test để thấy đỏ**

Run: `./gradlew test --tests '*ProtoSyncTest*'`
Expected: FAIL — chưa khai báo plugin protobuf, `Files.readAllBytes` vẫn đọc được nhưng build chưa có stub; nếu pass sớm thì do chưa cần stub — chấp nhận, chuyển sang bước 4.

- [ ] **Step 4: Sửa `build.gradle.kts`**

Thêm plugin (kèm `import com.google.protobuf.gradle.id` ở đầu file):

```kotlin
    id("com.google.protobuf") version "0.10.1"
```

Thêm deps:

```kotlin
    implementation("org.springframework.boot:spring-boot-starter-grpc-client")
    implementation("com.pgvector:pgvector:0.1.6")

    // InProcessServerBuilder/ChannelBuilder nằm ở artifact riêng từ grpc-java 1.8x.
    testImplementation("io.grpc:grpc-inprocess")
```

Thêm block (sau `dependencies { }`):

```kotlin
protobuf {
    // Khớp protobuf-java do BOM của Boot quản (4.35.1), lệch là ProtobufRuntimeVersionException.
    protoc { artifact = "com.google.protobuf:protoc:4.35.1" }
    plugins {
        create("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:1.83.1" }
    }
    generateProtoTasks {
        // all() chạy 2 lần cho cùng task ở plugin 0.10.x nên phải tự chặn trùng.
        all().forEach { task ->
            task.plugins {
                if (findByName("grpc") == null) {
                    id("grpc")
                }
            }
        }
    }
}
```

Trong `tasks.withType<JavaCompile>().configureEach`, thêm:

```kotlin
        options.errorprone.excludedPaths.set(".*/build/generated/.*")
        options.errorprone.disableWarningsInGeneratedCode.set(true)
```

- [ ] **Step 5: Build để chắc stub sinh ra**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL. Nếu plugin nổ vì Gradle 9.5.1: bỏ plugin, chạy `./gradlew` một lần với plugin tạm, commit thư mục `build/generated/source/proto` vào `src/main/java/com/minhnbnt/fashionstore/grpc/`, rồi bỏ plugin.

- [ ] **Step 6: Chạy test**

Run: `./gradlew test --tests '*ProtoSyncTest*'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add build.gradle.kts src/main/proto src/test/java/com/minhnbnt/fashionstore/features/catalog/ProtoSyncTest.java
git commit -m "build: protobuf codegen + client gRPC + pgvector"
```

---

### Task 7: Cấu hình channel + `EmbeddingClient`

**Files:**
- Create: `backend/src/main/java/com/minhnbnt/fashionstore/infrastructure/EmbeddingGrpcConfig.java`
- Create: `backend/src/main/java/com/minhnbnt/fashionstore/infrastructure/EmbeddingClient.java`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/minhnbnt/fashionstore/features/catalog/EmbeddingClientTest.java`

**Interfaces:**
- Produces: `EmbeddingClient.embed(byte[] image) -> float[]` (đúng 512), ném `ResponseStatusException` 400/413/502/503. Bean `EmbeddingServiceBlockingStub` tên `embeddingStub`.

- [ ] **Step 1: Viết test thất bại**

`EmbeddingClientTest.java` (server in-process, không cần worker):

```java
package com.minhnbnt.fashionstore.features.catalog;

import com.google.protobuf.ByteString;
import com.minhnbnt.fashionstore.infrastructure.EmbeddingClient;
import com.minhnbnt.fashionstore.grpc.EmbedImageReply;
import com.minhnbnt.fashionstore.grpc.EmbedImageRequest;
import com.minhnbnt.fashionstore.grpc.EmbeddingServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbeddingClientTest {

    private Server server;
    private ManagedChannel channel;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final AtomicReference<EmbedImageReply> reply = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        var name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name)
            .directExecutor()
            .addService(new EmbeddingServiceGrpc.EmbeddingServiceImplBase() {
                @Override
                public void embedImage(
                    EmbedImageRequest request, StreamObserver<EmbedImageReply> observer) {
                    var error = failure.get();
                    if (error != null) {
                        observer.onError(error);
                        return;
                    }
                    observer.onNext(reply.get());
                    observer.onCompleted();
                }
            })
            .build()
            .start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    }

    @AfterEach
    void tearDown() throws Exception {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    private EmbeddingClient client() {
        return new EmbeddingClient(EmbeddingServiceGrpc.newBlockingStub(channel));
    }

    private static EmbedImageReply valid() {
        var builder = EmbedImageReply.newBuilder().setModel("m");
        for (int i = 0; i < 512; i++) {
            builder.addVector(0.01f);
        }
        return builder.build();
    }

    @Test
    void returnsVectorValues() {
        reply.set(valid());
        assertThat(client().embed(new byte[]{1, 2, 3})).hasSize(512);
    }

    @Test
    void wrongDimensionIsBadGateway() {
        reply.set(EmbedImageReply.newBuilder().setModel("m").addVector(1f).build());
        assertThatThrownBy(() -> client().embed(new byte[]{1}))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                exc -> assertThat(exc.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void unavailableIsServiceUnavailable() {
        failure.set(Status.UNAVAILABLE.withDescription("down").asRuntimeException());
        assertThatThrownBy(() -> client().embed(new byte[]{1}))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                exc -> assertThat(exc.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void deadlineIsServiceUnavailable() {
        failure.set(Status.DEADLINE_EXCEEDED.asRuntimeException());
        assertThatThrownBy(() -> client().embed(new byte[]{1}))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                exc -> assertThat(exc.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void invalidArgumentIsBadRequest() {
        failure.set(Status.INVALID_ARGUMENT.asRuntimeException());
        assertThatThrownBy(() -> client().embed(new byte[]{1}))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                exc -> assertThat(exc.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void resourceExhaustedIsPayloadTooLarge() {
        failure.set(Status.RESOURCE_EXHAUSTED.asRuntimeException());
        assertThatThrownBy(() -> client().embed(new byte[]{1}))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                exc -> assertThat(exc.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    }
}
```

- [ ] **Step 2: Chạy test để thấy đỏ**

Run: `./gradlew test --tests '*EmbeddingClientTest*'`
Expected: FAIL — chưa có `EmbeddingClient`.

- [ ] **Step 3: Viết implementation**

`EmbeddingGrpcConfig.java`:

```java
package com.minhnbnt.fashionstore.infrastructure;

import com.minhnbnt.fashionstore.grpc.EmbeddingServiceGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

@Configuration
public class EmbeddingGrpcConfig {

    @Bean
    EmbeddingServiceGrpc.EmbeddingServiceBlockingStub embeddingStub(GrpcChannelFactory channels) {
        return EmbeddingServiceGrpc.newBlockingStub(channels.createChannel("embedder"));
    }
}
```

`EmbeddingClient.java`:

```java
package com.minhnbnt.fashionstore.infrastructure;

import com.minhnbnt.fashionstore.grpc.EmbedImageRequest;
import com.minhnbnt.fashionstore.grpc.EmbeddingServiceGrpc;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Gọi embed-worker. Chỉ đổi status gRPC sang HTTP, không chứa logic search. */
@Component
@RequiredArgsConstructor
public class EmbeddingClient {

    static final int DIM = 512;

    private final EmbeddingServiceGrpc.EmbeddingServiceBlockingStub stub;

    public float[] embed(byte[] image) {
        try {
            var reply = stub.embedImage(
                EmbedImageRequest.newBuilder().setImage(ByteString.copyFrom(image)).build());
            var values = reply.getVectorList();
            if (values.size() != DIM) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "worker trả " + values.size() + " chiều, cần " + DIM);
            }
            var vector = new float[DIM];
            for (int i = 0; i < DIM; i++) {
                vector[i] = values.get(i);
            }
            return vector;
        } catch (StatusRuntimeException exc) {
            throw map(exc);
        }
    }

    private static ResponseStatusException map(StatusRuntimeException exc) {
        var code = exc.getStatus().getCode();
        if (code == Status.Code.INVALID_ARGUMENT) {
            return new ResponseStatusException(HttpStatus.BAD_REQUEST, "ảnh query không hợp lệ");
        }
        if (code == Status.Code.RESOURCE_EXHAUSTED) {
            return new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "ảnh query quá lớn");
        }
        if (code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED) {
            return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "worker embedding không sẵn sàng");
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, code.name());
    }
}
```

- [ ] **Step 4: Thêm cấu hình**

`src/main/resources/application.yml`:

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 8MB
      max-request-size: 9MB
  grpc:
    client:
      channel:
        embedder:
          target: static://${EMBED_GRPC_ADDRESS:127.0.0.1:9091}
          default:
            deadline: 5s
          inbound:
            message:
              max-size: 8MB
```

Khoá `spring.grpc.client.channel`/`target`/`default.deadline` là của spring-grpc 1.1.x (đã vào Boot 4.1);
1.0.x dùng `channels`/`address`/`default-deadline`.

- [ ] **Step 5: Chạy test**

Run: `./gradlew test --tests '*EmbeddingClientTest*'`
Expected: PASS (6 test).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/minhnbnt/fashionstore/infrastructure/EmbeddingClient.java src/main/java/com/minhnbnt/fashionstore/infrastructure/EmbeddingGrpcConfig.java src/main/resources/application.yml src/test/java/com/minhnbnt/fashionstore/features/catalog/EmbeddingClientTest.java
git commit -m "feat(search): client gRPC embed ảnh + map mã lỗi"
```

---

### Task 8: Repository ANN + dedupe

**Files:**
- Create: `backend/src/main/java/com/minhnbnt/fashionstore/features/catalog/search/ImageSearchRepository.java`
- Test: `backend/src/test/java/com/minhnbnt/fashionstore/features/catalog/ImageSearchRepositoryTest.java`

**Interfaces:**
- Produces: `ImageSearchRepository.search(float[] vector, int fetch)` → `List<Scored>(UUID productId, UUID shopId, String name, String slug, @Nullable Long minPrice, @Nullable String defaultImageKey, double distance)`. `ImageSearchService` dedupe ở Task 9.

- [ ] **Step 1: Viết test thất bại**

`ImageSearchRepositoryTest.java` (Testcontainers, seed tay; vector dựng bằng `PGvector`.
`product_embedding.product_image_id` là FK tới `product_image`, nên phải tạo `upload` + `product_image`):

```java
package com.minhnbnt.fashionstore.features.catalog;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.features.catalog.search.ImageSearchRepository;
import com.pgvector.PGvector;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Gần vector query phải lên đầu; product ẩn/banned/shop chưa duyệt phải rớt. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ImageSearchRepositoryTest {

    @Autowired
    private ImageSearchRepository repository;

    @Autowired
    private JdbcClient jdbc;

    private UUID shopId;

    @BeforeEach
    void setUp() {
        jdbc.sql("""
            TRUNCATE product_embedding, product_image, product_variant, product, upload, shop CASCADE
            """).update();
        shopId = UUID.randomUUID();
        jdbc.sql("INSERT INTO shop (id, name, slug) VALUES (:id, 'S', 's-repo')")
            .param("id", shopId).update();
    }

    private UUID product(String slug, String status, String shopStatus, float @Nullable [] vector) {
        var id = UUID.randomUUID();
        jdbc.sql("UPDATE shop SET status = :st WHERE id = :id")
            .param("st", shopStatus).param("id", shopId).update();
        jdbc.sql("""
            INSERT INTO product (id, shop_id, name, slug, status)
            VALUES (:id, :shop, 'P', :slug, :status)
            """)
            .param("id", id).param("shop", shopId).param("slug", slug).param("status", status).update();
        if (vector != null) {
            image(id, vector);
        }
        return id;
    }

    private void image(UUID productId, float[] vector) {
        var uploadId = UUID.randomUUID();
        var imageId = UUID.randomUUID();
        jdbc.sql("INSERT INTO upload (id, s3_key, content_type) VALUES (:id, :key, 'image/jpeg')")
            .param("id", uploadId).param("key", "img-" + uploadId).update();
        jdbc.sql("INSERT INTO product_image (id, product_id, upload_id) VALUES (:id, :product, :upload)")
            .param("id", imageId).param("product", productId).param("upload", uploadId).update();
        jdbc.sql("""
            INSERT INTO product_embedding (product_image_id, product_id, embedding)
            VALUES (:imageId, :productId, cast(:vec as vector))
            """)
            .param("imageId", imageId)
            .param("productId", productId)
            .param("vec", new PGvector(vector).getValue())
            .update();
    }

    @Test
    void ordersByDistanceAndKeepsBestImagePerProduct() {
        var near = product("near", "ACTIVE", "APPROVED", unit(0));
        image(near, unit(1));
        var far = product("far", "ACTIVE", "APPROVED", unit(256));

        var hits = repository.search(unit(0), 10);

        assertThat(hits.get(0).productId()).isEqualTo(near);
        assertThat(hits.get(hits.size() - 1).productId()).isEqualTo(far);
        // Repo trả cả 2 ảnh của "near"; service mới dedupe.
        assertThat(hits).extracting(ImageSearchRepository.Scored::productId).containsOnly(near, far);
    }

    @Test
    void hidesInactiveProductsAndUnapprovedShops() {
        product("hidden", "HIDDEN", "APPROVED", unit(0));
        product("banned", "BANNED", "APPROVED", unit(0));
        product("pending-shop", "ACTIVE", "PENDING", unit(0));

        assertThat(repository.search(unit(0), 10)).isEmpty();
    }

    @Test
    void skipsRowsWithoutEmbedding() {
        product("no-vector", "ACTIVE", "APPROVED", null);
        assertThat(repository.search(unit(0), 10)).isEmpty();
    }

    private static float[] unit(int shift) {
        var vector = new float[512];
        vector[shift] = 1f;
        return vector;
    }
}
```

- [ ] **Step 2: Chạy test để thấy đỏ**

Run: `./gradlew test --tests '*ImageSearchRepositoryTest*'`
Expected: FAIL — chưa có `ImageSearchRepository`.

- [ ] **Step 3: Viết implementation**

`ImageSearchRepository.java`:

```java
package com.minhnbnt.fashionstore.infrastructure;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import com.pgvector.PGvector;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** ANN top-N trên diskann. Không lọc bên trong index: lấy dư rồi service dedupe/cắt. */
@Repository
@RequiredArgsConstructor
public class ImageSearchRepository {

    private final JdbcClient jdbc;

    public record Scored(
        UUID productId,
        UUID shopId,
        String name,
        String slug,
        @Nullable Long minPrice,
        @Nullable String defaultImageKey,
        double distance
    ) {
    }

    private static final String SQL = """
        WITH prices AS (
            SELECT product_id, min(price) AS min_price FROM product_variant GROUP BY product_id
        ),
        covers AS (
            SELECT DISTINCT ON (pi.product_id) pi.product_id, u.s3_key
            FROM product_image pi
            JOIN upload u ON u.id = pi.upload_id
            ORDER BY pi.product_id, pi.sort_order
        )
        SELECT
            p.id AS id,
            p.shop_id AS shop_id,
            p.name AS name,
            p.slug AS slug,
            prices.min_price AS min_price,
            covers.s3_key AS default_image,
            pe.embedding <=> cast(:vec as vector) AS distance
        FROM product_embedding pe
        JOIN product p ON p.id = pe.product_id AND p.status = 'ACTIVE'
        JOIN shop s ON s.id = p.shop_id AND s.status = 'APPROVED'
        LEFT JOIN prices ON prices.product_id = p.id
        LEFT JOIN covers ON covers.product_id = p.id
        WHERE pe.embedding IS NOT NULL
        ORDER BY distance, p.id
        LIMIT :fetch
        """;

    public List<Scored> search(float[] vector, int fetch) {
        return jdbc.sql(SQL)
            .param("vec", new PGvector(vector).getValue())
            .param("fetch", fetch)
            .query((rs, rowNum) -> new Scored(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("shop_id"),
                rs.getString("name"),
                rs.getString("slug"),
                rs.getObject("min_price", Long.class),
                rs.getString("default_image"),
                rs.getDouble("distance")
            ))
            .list();
    }
}
```

- [ ] **Step 4: Chạy test**

Run: `./gradlew test --tests '*ImageSearchRepositoryTest*'`
Expected: PASS (3 test).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/minhnbnt/fashionstore/features/catalog/search/ImageSearchRepository.java src/test/java/com/minhnbnt/fashionstore/features/catalog/ImageSearchRepositoryTest.java
git commit -m "feat(search): repository ANN top-N trên product_embedding"
```

---

### Task 9: DTO, service, controller + doc

**Files:**
- Create: `backend/src/main/java/com/minhnbnt/fashionstore/dto/catalog/ImageSearchHit.java`
- Create: `backend/src/main/java/com/minhnbnt/fashionstore/dto/catalog/ImageSearchResponse.java`
- Create: `backend/src/main/java/com/minhnbnt/fashionstore/features/catalog/search/ImageSearchService.java`
- Create: `backend/src/main/java/com/minhnbnt/fashionstore/features/catalog/controller/ImageSearchController.java`
- Test: `backend/src/test/java/com/minhnbnt/fashionstore/features/catalog/ImageSearchControllerTest.java`
- Modify: `docs/catalog.md`, `docs/api-endpoints.md`

**Interfaces:**
- Consumes: `EmbeddingClient.embed(byte[])`, `ImageSearchRepository.search(float[], int)`, `S3UrlService.publicUrl(String)`.
- Produces: `POST /api/search/products/by-image` → `ImageSearchResponse(List<ImageSearchHit> items)`; `ImageSearchHit(UUID id, String name, String slug, @Nullable Long minPrice, @Nullable String defaultImageUrl, double similarity)`.

- [ ] **Step 1: Viết test thất bại**

`ImageSearchControllerTest.java` (`@WebMvcTest` + mock `ImageSearchService`):

```java
package com.minhnbnt.fashionstore.features.catalog;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.catalog.ImageSearchHit;
import com.minhnbnt.fashionstore.dto.catalog.ImageSearchResponse;
import com.minhnbnt.fashionstore.features.catalog.controller.ImageSearchController;
import com.minhnbnt.fashionstore.features.catalog.search.ImageSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImageSearchController.class)
@Import(SecurityConfig.class)
class ImageSearchControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ImageSearchService service;

    private static MockMultipartFile image(int size) {
        return new MockMultipartFile("file", "q.png", "image/png", new byte[size]);
    }

    @Test
    void returnsHits() throws Exception {
        when(service.search(any(), anyInt())).thenReturn(new ImageSearchResponse(List.of(
            new ImageSearchHit(UUID.randomUUID(), "P", "p", 1000L, "http://img", 0.93))));

        mvc.perform(multipart("/api/search/products/by-image").file(image(10)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].similarity").value(0.93));
    }

    @Test
    void usesDefaultLimit() throws Exception {
        when(service.search(any(), anyInt())).thenReturn(new ImageSearchResponse(List.of()));

        mvc.perform(multipart("/api/search/products/by-image").file(image(10)))
            .andExpect(status().isOk());

        verify(service).search(any(), eq(20));
    }

    @Test
    void rejectsNonImageContentType() throws Exception {
        var pdf = new MockMultipartFile("file", "q.pdf", "application/pdf", new byte[10]);

        mvc.perform(multipart("/api/search/products/by-image").file(pdf))
            .andExpect(status().isUnsupportedMediaType());

        verify(service, never()).search(any(), anyInt());
    }

    @Test
    void rejectsEmptyFile() throws Exception {
        mvc.perform(multipart("/api/search/products/by-image").file(image(0)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsTooLargeFileBeforeCalling() throws Exception {
        mvc.perform(multipart("/api/search/products/by-image").file(image(8 * 1024 * 1024 + 1)))
            .andExpect(status().isPayloadTooLarge());

        verify(service, never()).search(any(), anyInt());
    }

    @Test
    void rejectsLimitOutOfRange() throws Exception {
        mvc.perform(multipart("/api/search/products/by-image").file(image(10)).param("limit", "0"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void surfacesWorkerOutageAs503() throws Exception {
        when(service.search(any(), anyInt()))
            .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "down"));

        mvc.perform(multipart("/api/search/products/by-image").file(image(10)))
            .andExpect(status().isServiceUnavailable());
    }
}
```

- [ ] **Step 2: Chạy test để thấy đỏ**

Run: `./gradlew test --tests '*ImageSearchControllerTest*'`
Expected: FAIL — chưa có controller/DTO/service.

- [ ] **Step 3: Viết DTO**

`ImageSearchHit.java`:

```java
package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record ImageSearchHit(
    @Schema UUID id,
    @Schema String name,
    @Schema String slug,
    @Schema(description = "Giá variant thấp nhất (VND)", nullable = true) @Nullable Long minPrice,
    @Schema(description = "URL ảnh đại diện", nullable = true) @Nullable String defaultImageUrl,
    @Schema(description = "Độ giống 0..1, càng lớn càng giống") double similarity
) {
}
```

`ImageSearchResponse.java`:

```java
package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record ImageSearchResponse(@Schema List<ImageSearchHit> items) {
}
```

- [ ] **Step 4: Viết service**

`ImageSearchService.java`:

```java
package com.minhnbnt.fashionstore.infrastructure;

import com.minhnbnt.fashionstore.dto.catalog.ImageSearchHit;
import com.minhnbnt.fashionstore.dto.catalog.ImageSearchResponse;
import com.minhnbnt.fashionstore.features.upload.S3UrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;

/** Guard nội bộ độc lập với controller để test được không cần HTTP. */
@Service
@RequiredArgsConstructor
public class ImageSearchService {

    static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;
    private static final int OVER_FETCH = 3;

    private final EmbeddingClient client;
    private final ImageSearchRepository repository;
    private final S3UrlService s3Urls;

    public ImageSearchResponse search(byte[] image, int limit) {
        if (image.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ảnh rỗng");
        }
        if (image.length > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "ảnh quá lớn");
        }

        var vector = client.embed(image);
        var seen = new HashSet<UUID>();
        var items = new ArrayList<ImageSearchHit>(limit);
        for (var hit : repository.search(vector, limit * OVER_FETCH)) {
            if (!seen.add(hit.productId())) {
                continue;
            }
            items.add(new ImageSearchHit(
                hit.productId(),
                hit.name(),
                hit.slug(),
                hit.minPrice(),
                hit.defaultImageKey() == null ? null : s3Urls.publicUrl(hit.defaultImageKey()),
                1 - hit.distance()));
            if (items.size() == limit) {
                break;
            }
        }
        return new ImageSearchResponse(items);
    }
}
```

- [ ] **Step 5: Viết controller**

`ImageSearchController.java`:

```java
package com.minhnbnt.fashionstore.features.catalog.controller;

import com.minhnbnt.fashionstore.dto.catalog.ImageSearchResponse;
import com.minhnbnt.fashionstore.features.catalog.search.ImageSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Tìm hàng giống ảnh query bằng vector Fashion-CLIP")
public class ImageSearchController {

    private final ImageSearchService service;

    @PostMapping(path = "/products/by-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Tìm sản phẩm giống ảnh", description = "Chỉ hàng ACTIVE của shop APPROVED, "
        + "xếp theo cosine distance tăng dần. Không nhận bộ lọc, không phân trang.")
    @ApiResponse(responseCode = "200", description = "Tối đa `limit` sản phẩm giống nhất")
    @ApiResponse(responseCode = "400", description = "File rỗng hoặc không decode được")
    @ApiResponse(responseCode = "413", description = "Ảnh lớn hơn 8MB")
    @ApiResponse(responseCode = "415", description = "Content-type không phải image/*")
    @ApiResponse(responseCode = "503", description = "Worker embedding không sẵn sàng")
    public ImageSearchResponse byImage(
        @RequestPart("file") MultipartFile file,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        var contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "cần ảnh");
        }
        try {
            return service.search(file.getBytes(), limit);
        } catch (IOException exc) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "không đọc được file", exc);
        }
    }
}
```

- [ ] **Step 6: Chạy test**

Run: `./gradlew test --tests '*ImageSearchControllerTest*'`
Expected: PASS (7 test).

- [ ] **Step 7: Cập nhật doc**

`docs/api-endpoints.md` — thêm dòng dưới #16b:

```markdown
| [x] | 16c | POST | `/api/search/products/by-image` | Search bằng ảnh: gRPC sang embed-worker lấy vector Fashion-CLIP, ANN top-N trên `idx` diskann, dedupe theo product. Multipart `file` ≤ 8MB, `limit` 1–100 |
```

`docs/catalog.md` — thêm mục cuối:

```markdown
## #16c — Tìm bằng ảnh

`POST /api/search/products/by-image` — public, multipart `file` (≤ 8MB, chỉ `image/*`), `limit` 1–100.

Backend gửi bytes sang embed-worker qua gRPC (`EmbeddingService/EmbedImage`, deadline 5s), nhận vector
512 chiều rồi truy vấn `product_embedding` bằng `<=>` (diskann), lấy dư 3× rồi dedupe theo product.
Chỉ hàng `ACTIVE` của shop `APPROVED`. Worker chết → `503`; BM25 vẫn chạy bình thường.

Spec: `superpowers/specs/2026-11-15-image-search-grpc-design.md`.
```

- [ ] **Step 8: Chạy toàn bộ test**

Run: `./gradlew test`
Expected: PASS toàn bộ.

- [ ] **Step 9: Export lại OpenAPI**

Run: `./gradlew openApiExport`
Expected: `docs/openapi.json` có thêm path by-image.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/minhnbnt/fashionstore/dto/catalog/ImageSearchHit.java src/main/java/com/minhnbnt/fashionstore/dto/catalog/ImageSearchResponse.java src/main/java/com/minhnbnt/fashionstore/features/catalog/search/ImageSearchService.java src/main/java/com/minhnbnt/fashionstore/features/catalog/controller/ImageSearchController.java src/test/java/com/minhnbnt/fashionstore/features/catalog/ImageSearchControllerTest.java docs/catalog.md docs/api-endpoints.md docs/openapi.json
git commit -m "feat(search): endpoint tìm sản phẩm bằng ảnh"
```

---

### Task 10: E2E tay

- [ ] **Step 1: Chạy worker ở một terminal**

Run: `cd ../embed-worker && LOCAL_FILES_ONLY=true uv run python -m app.worker.main`
Expected: log `gRPC server nghe 127.0.0.1:9091`.

- [ ] **Step 2: Chạy backend**

Run: `./gradlew bootRun`
Expected: backend lên, không lỗi channel.

- [ ] **Step 3: Search lại ảnh của một product ACTIVE**

Lấy `s3_key` của ảnh bất kỳ, tải từ MinIO/S3 rồi POST:

```bash
curl -s -X POST http://localhost:8080/api/search/products/by-image \
  -F "file=@/tmp/query.jpg;type=image/jpeg" -F "limit=5" | jq '.items[0]'
```
Expected: product của chính ảnh đó đứng đầu, `similarity` cao nhất.

- [ ] **Step 4: Kiểm tra các nhánh lỗi**

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8080/api/search/products/by-image -F "file=@README.md;type=text/markdown"   # 415
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8080/api/search/products/by-image -F "file=@/tmp/fake.png;type=image/png"    # 400, file rác đổi đuôi
pkill -f "app.worker.main" && curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8080/api/search/products/by-image -F "file=@/tmp/query.jpg;type=image/jpeg"   # 503
```
Expected: `415`, `400`, `503`.

- [ ] **Step 5: Chạy lại toàn bộ kiểm tra hai repo**

```bash
cd ../embed-worker && uv run pytest -q && uv run ruff check . && uv run pyrefly check
cd ../backend && ./gradlew test
```
Expected: xanh cả hai.

---

## Self-Review

- Spec coverage: contract (T1/T6), worker servicer/validate/cache/pool/health/shutdown (T2–T5), backend build/config/client/errors (T6/T7), repository (T8), DTO/controller/service/docs (T9), e2e (T10). Ngưỡng 8MB, deadline 5s, concurrency 4, dims 502, dedupe, lọc ACTIVE/APPROVED đều có test tương ứng.
- Không còn placeholder; mọi task có test đỏ trước, code cụ thể, lệnh chạy, lệnh commit.
- Type xuyên task: `SearchEmbedder.embed`, `EmbeddingServicer`, `EmbeddingClient.embed`, `ImageSearchRepository.Scored`, `ImageSearchService.search(byte[], int)`, `ImageSearchHit` khớp nhau.
