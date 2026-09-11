package com.curso.keycloak.web;

/**
 * Registro (record) que representa a un cliente del negocio ficticio.
 *
 * Contiene un dato sensible simulado: los últimos cuatro dígitos de una
 * tarjeta de pago. PCI DSS (requisito 7) exige que este tipo de información
 * solo sea accesible a quien tenga una necesidad de negocio justificada.
 * En la aplicación base NADIE controla ese acceso: esa es la debilidad que
 * los laboratorios irán corrigiendo.
 *
 * Los datos son inventados; ningún número corresponde a una tarjeta real.
 */
public record Cliente(String nombre, String correo, String ultimosCuatroDigitos, double saldo) {
}
