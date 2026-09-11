package com.curso.keycloak.web;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
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
 * ESTADO TRAS EL LABORATORIO 04: /privada exige usuario autenticado
 * mediante OpenID Connect (ver seguridad/SeguridadConfig). El controlador
 * ya no decide quién entra; solo muestra quién es.
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
     * Identidad del visitante, disponible en todas las vistas con el
     * nombre "usuario".
     *
     * LABORATORIO 04: Spring inyecta el usuario autenticado por OpenID
     * Connect (OidcUser). Sus datos proceden del ID Token que emitió
     * Keycloak y que Spring ya validó (firma, emisor, audiencia, caducidad).
     * Si nadie ha iniciado sesión, el parámetro llega nulo.
     */
    @ModelAttribute("usuario")
    public String usuarioActual(@AuthenticationPrincipal OidcUser usuario) {
        if (usuario == null) {
            return "anónimo";
        }
        // "preferred_username" es el claim estándar de OIDC con el nombre
        // de usuario; Keycloak lo rellena con el username del realm.
        return usuario.getPreferredUsername();
    }

    /**
     * Indica a las vistas si hay sesión iniciada, para mostrar el enlace
     * "Iniciar sesión" solo cuando corresponde.
     */
    @ModelAttribute("autenticado")
    public boolean autenticado(@AuthenticationPrincipal OidcUser usuario) {
        return usuario != null;
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
     * LABORATORIO 04: solo llega aquí un usuario autenticado; de lo
     * contrario Spring Security ya lo redirigió a Keycloak.
     * Pendiente (laboratorio 06): restringir por rol, porque hoy CUALQUIER
     * usuario autenticado ve estos datos.
     */
    @GetMapping("/privada")
    public String privada(Model model) {
        // Envía la lista a la vista privada.html
        model.addAttribute("clientes", CLIENTES);
        return "privada";
    }
}
