package com.brogrammers.open_mic_hub_service;

import com.brogrammers.open_mic_hub_service.config.RSAKeyRecord;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableConfigurationProperties(RSAKeyRecord.class)
@EnableAsync
public class OpenMicHubServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(OpenMicHubServiceApplication.class, args);
	}

}
