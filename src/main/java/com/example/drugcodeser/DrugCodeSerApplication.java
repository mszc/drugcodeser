
package com.example.drugcodeser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DrugCodeSerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DrugCodeSerApplication.class, args);
    }
}
