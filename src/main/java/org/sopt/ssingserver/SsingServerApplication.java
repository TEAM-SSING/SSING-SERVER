package org.sopt.ssingserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// 매칭 재탐색처럼 주기적으로 실행되는 Spring @Scheduled 작업 활성화
@EnableScheduling
@SpringBootApplication
public class SsingServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SsingServerApplication.class, args);
    }

}
