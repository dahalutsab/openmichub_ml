package com.brogrammers.open_mic_hub_service;

import com.brogrammers.open_mic_hub_service.config.RSAKeyRecord;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(RSAKeyRecord.class)
@EnableAsync
@EnableScheduling
public class OpenMicHubServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(OpenMicHubServiceApplication.class, args);
	}

}
