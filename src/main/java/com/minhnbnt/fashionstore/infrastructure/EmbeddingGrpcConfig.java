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
