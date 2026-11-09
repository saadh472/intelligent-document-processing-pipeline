package com.legal.documentprocessing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication(exclude = {
    JmxAutoConfiguration.class  // Exclude JMX for faster startup
})
@EnableKafka
public class DocumentProcessingApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(DocumentProcessingApplication.class);
        // Optimize startup performance
        app.setLazyInitialization(false);  // Set to true for even faster startup, but may hide errors
        app.setLogStartupInfo(true);  // Keep startup info logging
        app.run(args);
    }
}
