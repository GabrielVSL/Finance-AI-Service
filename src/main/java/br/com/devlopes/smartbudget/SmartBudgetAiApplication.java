package br.com.devlopes.smartbudget;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartBudgetAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartBudgetAiApplication.class, args);
    }
}
