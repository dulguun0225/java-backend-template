package com.example.starter;

import com.example.starter.platform.Ids;
import com.example.starter.platform.observability.LogContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** The single deployable. Flyway migrates on startup; the schema is code the build already verified. */
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        // Establish the process-wide log fields once, before anything emits.
        LogContext.initProcess("api", Ids.newId().toString());
        SpringApplication.run(Application.class, args);
    }
}
