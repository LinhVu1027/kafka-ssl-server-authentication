package com.cloud.kafkasslserverauthentication;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.KafkaListener;

@SpringBootApplication
@Slf4j
public class KafkaSslServerAuthenticationApplication {

	public static void main(String[] args) {
		SpringApplication.run(KafkaSslServerAuthenticationApplication.class, args);
	}

	@KafkaListener(id = "client", topics = "ssl")
	public void listen(String in) {
		log.info(in);
	}
}
