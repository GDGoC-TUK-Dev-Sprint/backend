package com.tukorea.gdgoc.koready;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class KoreadyApplication {

	public static void main(String[] args) {
		SpringApplication.run(KoreadyApplication.class, args);
	}

}
