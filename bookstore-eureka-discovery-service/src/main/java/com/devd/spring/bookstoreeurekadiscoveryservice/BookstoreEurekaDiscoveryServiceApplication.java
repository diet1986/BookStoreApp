package com.devd.spring.bookstoreeurekadiscoveryservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * @author: Deepak Srivastav,
 * Date : 2019-05-14
 */
@SpringBootApplication
@EnableEurekaServer
public class BookstoreEurekaDiscoveryServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(BookstoreEurekaDiscoveryServiceApplication.class, args);
	}

}
