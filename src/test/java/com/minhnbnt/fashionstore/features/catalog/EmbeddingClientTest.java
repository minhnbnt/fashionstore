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
