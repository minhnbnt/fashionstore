package com.minhnbnt.fashionstore;

import org.springframework.boot.SpringApplication;

public class TestFashionstoreApplication {

    static void main(String[] args) {
        SpringApplication.from(FashionStoreApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
