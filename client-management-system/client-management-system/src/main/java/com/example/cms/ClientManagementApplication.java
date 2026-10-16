package com.example.cms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the Client Management System.
 *
 * In IntelliJ IDEA: click the green Run arrow next to main(),
 * wait for "Started ClientManagementApplication" in the Run console,
 * then open http://localhost:8080 in your browser.
 *
 * MongoDB must already be running (default: mongodb://localhost:27017).
 */
@SpringBootApplication
public class ClientManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClientManagementApplication.class, args);
    }
}
