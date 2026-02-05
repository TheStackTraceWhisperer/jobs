package io.github.thestacktracewhisperer.jobs.reference;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Reference application demonstrating the distributed job orchestration platform.
 * Can be run as an API node or a worker node based on Spring profiles.
 */
@SpringBootApplication
public class ReferenceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReferenceApplication.class, args);
    }
}
