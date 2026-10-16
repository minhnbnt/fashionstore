package com.minhnbnt.fashionstore;

import org.springframework.boot.SpringApplication;

public class TestFashionstoreApplication {

    public static void main(String[] args) {
        SpringApplication.from(FashionStoreApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
