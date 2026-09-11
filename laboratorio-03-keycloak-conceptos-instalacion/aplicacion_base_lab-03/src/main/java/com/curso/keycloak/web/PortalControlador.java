package com.curso.keycloak.web;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Controlador único de la aplicación base.
 *
 * Expone las dos secciones del curso:
 *
 *   GET /          Sección pública: portada informativa, abierta a cualquiera.
 *   GET /privada   Sección privada: listado de clientes con datos sensibles.
 *
 * ESTADO INICIAL (aplicación base): las dos rutas responden a cualquier
 * visitante. No hay concepto de "usuario", ni de "sesión", ni de "rol".
 * Esto corresponde a la categoría OWASP A01:2021 Control de acceso roto y
 * A07:2021 Fallos de identificación y autenticación.
 *
 * Cada laboratorio modificará este controlador (o añadirá configuración)
 * para cerrar una debilidad concreta.
 */
@Controller
public class PortalControlador {

    /**
     * Datos de ejemplo en memoria. En un sistema real vendrían de una base
     * de datos; aquí se mantienen fijos para que el foco esté en Keycloak.
     */
    private static final List<Cliente> CLIENTES = List.of(
            new Cliente("Ana Torres",     "ana.torres@ejemplo.test",   "4242", 1520.75),
            new Cliente("Luis Herrera",   "luis.herrera@ejemplo.test", "0005",  310.00),
            new Cliente("Marta Salgado",  "marta.salgado@ejemplo.test","1117", 8790.10)
    );

    /**
     * Identidad del visitante, disponible en todas las vistas de este
     * controlador con el nombre "usuario" (la cabecera la muestra).
     *
     * Sin autenticación no hay identidad, así que siempre es "anónimo".
     * LABORATORIO 04: este método pasará a leer el nombre del usuario
     * autenticado por Keycloak. Es el único punto que habrá que cambiar.
     */
    @ModelAttribute("usuario")
    public String usuarioActual() {
        return "anónimo";
    }

    /**
     * Sección pública. Muestra información general del negocio ficticio.
     * Debe seguir siendo accesible sin autenticación durante todo el curso.
     */
    @GetMapping("/")
    public String publica() {
        return "publica";
    }

    /**
     * Sección privada. Lista clientes con datos sensibles.
     *
     * DEBILIDAD: cualquiera que conozca la URL /privada ve estos datos.
     * El laboratorio 04 exigirá autenticación mediante OpenID Connect y el
     * laboratorio 06 restringirá el acceso por rol.
     */
    @GetMapping("/privada")
    public String privada(Model model) {
        // Envía la lista a la vista privada.html
        model.addAttribute("clientes", CLIENTES);
        return "privada";
    }
}
