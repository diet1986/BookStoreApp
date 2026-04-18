package com.devd.spring.bookstorepaymentservice;

import com.stripe.Stripe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.EventListener;

/**
 * @author Deepak Srivastav, Date : 25-Jul-2020
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.devd.spring"})
@EnableFeignClients(basePackages = {"com.devd.spring"})
@EnableDiscoveryClient
public class BookstorePaymentServiceApplication {

	@Value("${service.stripeKey}")
	private String stripeKey;

	public static void main(String[] args) {
		SpringApplication.run(BookstorePaymentServiceApplication.class, args);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void initStripe() {
		Stripe.apiKey = stripeKey;
	}

}

