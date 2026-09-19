package com.curso.keycloak.web;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Controlador único de la aplicación base.
 *
 * Expone las secciones del curso:
 *
 *   GET /          Sección pública: portada informativa, abierta a cualquiera.
 *   GET /privada   Sección privada: listado de clientes con datos sensibles.
 *   GET /denegado  Página de acceso denegado (laboratorio 06).
 *
 * ESTADO TRAS EL LABORATORIO 04: /privada exige usuario autenticado
 * mediante OpenID Connect (ver seguridad/SeguridadConfig). El controlador
 * ya no decide quién entra; solo muestra quién es.
 *
 * ESTADO TRAS EL LABORATORIO 06: /privada exige además el rol de realm
 * "gestor-clientes". La decisión sigue en SeguridadConfig; aquí solo se
 * muestran los roles en la cabecera y se sirve la página /denegado.
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
     * LABORATORIO 06: nombres de los roles del usuario, para mostrarlos en
     * la cabecera ("Sesión: ana [gestor-clientes]").
     *
     * Se leen del objeto Authentication y no del OidcUser: las autoridades
     * "ROLE_*" las añade el mapeador de SeguridadConfig al Authentication,
     * mientras que el OidcUser conserva solo las originales del ID Token.
     * Se filtran las que empiezan por "ROLE_" y se quita el prefijo, que es
     * un detalle interno de Spring. Un visitante anónimo no tiene roles.
     */
    @ModelAttribute("roles")
    public List<String> roles(Authentication autenticacion) {
        if (autenticacion == null || !(autenticacion.getPrincipal() instanceof OidcUser)) {
            return List.of();
        }
        return autenticacion.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(autoridad -> autoridad.startsWith("ROLE_"))
                .map(autoridad -> autoridad.substring("ROLE_".length()))
                .sorted()
                .toList();
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
     * LABORATORIO 06: además, solo llega quien tiene el rol
     * "gestor-clientes"; los demás reciben /denegado.
     */
    @GetMapping("/privada")
    public String privada(Model model) {
        // Envía la lista a la vista privada.html
        model.addAttribute("clientes", CLIENTES);
        return "privada";
    }

    /**
     * LABORATORIO 06: página de acceso denegado.
     *
     * No se llega aquí navegando: Spring Security reenvía (forward) a esta
     * ruta cuando un usuario autenticado intenta una URL para la que no
     * tiene rol, y mantiene el estado HTTP 403 en la respuesta.
     */
    @GetMapping("/denegado")
    public String denegado() {
        return "denegado";
    }
}
