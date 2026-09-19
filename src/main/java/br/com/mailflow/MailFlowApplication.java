package br.com.mailflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MailFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(MailFlowApplication.class, args);
    }
}

