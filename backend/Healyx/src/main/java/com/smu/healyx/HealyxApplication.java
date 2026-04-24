package com.smu.healyx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HealyxApplication {

	public static void main(String[] args) {
		SpringApplication.run(HealyxApplication.class, args);
	}

}
