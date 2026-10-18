package com.minhnbnt.fashionstore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FashionStoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(FashionStoreApplication.class, args);
    }
}
