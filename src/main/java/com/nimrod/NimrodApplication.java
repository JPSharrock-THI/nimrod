package com.nimrod;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class NimrodApplication {

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(NimrodApplication.class, args)));
    }
}
