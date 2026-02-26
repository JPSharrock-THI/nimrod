package com.nimrod;

import com.nimrod.flatbuffers.FbsReflectionHints;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;

@SpringBootApplication
@ImportRuntimeHints(FbsReflectionHints.class)
public class NimrodApplication {

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(NimrodApplication.class, args)));
    }
}
