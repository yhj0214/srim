package org.yhj.srim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SrimApplication {

    public static void main(String[] args) {
        SpringApplication.run(SrimApplication.class, args);
    }

}
