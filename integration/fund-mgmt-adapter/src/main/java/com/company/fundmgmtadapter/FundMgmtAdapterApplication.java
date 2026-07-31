package com.company.fundmgmtadapter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class FundMgmtAdapterApplication {

    public static void main(String[] args) {
        SpringApplication.run(FundMgmtAdapterApplication.class, args);
    }
}
