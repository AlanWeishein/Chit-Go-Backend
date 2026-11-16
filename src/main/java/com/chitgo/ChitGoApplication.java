package com.chitgo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.chitgo.compartido.aplicacion.ReglasDelNegocio;

@SpringBootApplication
@EnableConfigurationProperties(ReglasDelNegocio.class)
public class ChitGoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChitGoApplication.class, args);
    }
}
