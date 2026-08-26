package com.casino;

import com.casino.config.CasinoProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Entry point. Scheduling is enabled for the guest-session and idle-table sweepers. */
@SpringBootApplication
@EnableConfigurationProperties(CasinoProperties.class)
@EnableScheduling
public class CasinoApplication {

    public static void main(String[] args) {
        SpringApplication.run(CasinoApplication.class, args);
    }
}
