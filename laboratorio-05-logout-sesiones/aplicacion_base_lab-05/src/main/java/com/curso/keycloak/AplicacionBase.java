package com.curso.keycloak;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada de la aplicación.
 *
 * @SpringBootApplication activa la autoconfiguración de Spring Boot y el
 * escaneo de componentes en el paquete com.curso.keycloak y subpaquetes.
 *
 * Ejecutar con:  mvn spring-boot:run
 * Abrir en:      http://localhost:8081
 * (Keycloak usará el puerto 8080; por eso la aplicación va en el 8081.)
 */
@SpringBootApplication
public class AplicacionBase {

    public static void main(String[] args) {
        SpringApplication.run(AplicacionBase.class, args);
    }
}
