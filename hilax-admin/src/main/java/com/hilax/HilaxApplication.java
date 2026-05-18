package com.hilax;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

/**
 * Application bootstrap.
 *
 * @author hilax
 */
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class HilaxApplication
{
    public static void main(String[] args)
    {
        // System.setProperty("spring.devtools.restart.enabled", "false");
        SpringApplication.run(HilaxApplication.class, args);
        System.out.println("Hilax started successfully.");
    }
}