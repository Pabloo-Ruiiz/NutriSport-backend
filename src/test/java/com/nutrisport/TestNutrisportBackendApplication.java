package com.nutrisport;

import org.springframework.boot.SpringApplication;

public class TestNutrisportBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(NutrisportBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
