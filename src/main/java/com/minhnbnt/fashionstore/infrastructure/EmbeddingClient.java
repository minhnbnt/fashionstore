package com.minhnbnt.fashionstore.infrastructure;

import com.google.protobuf.ByteString;
import com.minhnbnt.fashionstore.grpc.EmbedImageRequest;
import com.minhnbnt.fashionstore.grpc.EmbeddingServiceGrpc;
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
