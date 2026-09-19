package com.curso.keycloak.seguridad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * LABORATORIO 12: registro de auditoría de la aplicación.
 *
 * Keycloak audita lo que ocurre en Keycloak (quién inició sesión, quién
 * falló la contraseña, qué cambió un administrador). Pero la decisión de
 * "quién vio los datos de clientes" la toma ESTA aplicación, y Keycloak no
 * la ve. PCI DSS 10.2.1.1 pide rastro del acceso a los datos de titulares
 * de tarjeta: eso solo puede registrarlo quien los sirve.
 *
 * Este componente escucha los eventos que Spring Security publica en el
 * ApplicationContext y escribe una línea por cada uno en el logger
 * "auditoria", separado del log técnico para que se pueda enviar a un
 * destino distinto (archivo propio, SIEM):
 *
 *   - AuthenticationSuccessEvent: alguien completó el inicio de sesión OIDC.
 *   - LogoutSuccessEvent:         alguien cerró sesión (POST /logout).
 *   - AuthorizationDeniedEvent:   alguien pidió una ruta para la que no
 *                                 tiene permiso (por ejemplo /privada sin
 *                                 el rol gestor-clientes).
 *
 * CORRELACIÓN. Cada línea lleva el claim "sid" (session id) del ID Token.
 * Es el MISMO identificador de sesión que Keycloak escribe en sus eventos
 * LOGIN y LOGOUT (campo "Session"). Con él, un incidente se sigue de un
 * sistema al otro: el evento LOGIN de Keycloak dice desde qué IP y con qué
 * cliente entró alguien; la línea de esta aplicación dice qué hizo después.
 *
 * QUÉ NO SE REGISTRA, A PROPÓSITO: ni el ID Token, ni el access token, ni
 * ningún secreto. Un token en un log es una credencial en un log
 * (OWASP ASVS V7.1: el registro no debe contener credenciales ni datos
 * sensibles). Se escriben solo identificadores: usuario, "sub", "sid",
 * "azp" y la ruta.
 *
 * Para que Spring publique AuthorizationDeniedEvent hace falta declarar un
 * AuthorizationEventPublisher (ver SeguridadConfig). Los otros dos eventos
 * los publica Spring Security por sí solo.
 *
 * No hay ninguna clase de Keycloak aquí: solo eventos estándar de Spring
 * Security y SLF4J.
 */
@Component
public class AuditoriaDeAcceso {

    /**
     * Logger con nombre propio, "auditoria", en lugar del nombre de la
     * clase. Así se puede enrutar aparte en la configuración de logging
     * sin tocar el código.
     */
    private static final Logger AUDITORIA = LoggerFactory.getLogger("auditoria");

    /**
     * Inicio de sesión completado. El principal es el OidcUser que Spring
     * construyó a partir del ID Token validado (laboratorio 04).
     */
    @EventListener
    public void inicioDeSesion(AuthenticationSuccessEvent evento) {
        if (evento.getAuthentication().getPrincipal() instanceof OidcUser usuario) {
            AUDITORIA.info("LOGIN usuario={} sub={} sid={} azp={}",
                    usuario.getPreferredUsername(),
                    usuario.getSubject(),
                    usuario.getIdToken().getClaimAsString("sid"),
                    usuario.getIdToken().getClaimAsString("azp"));
        }
    }

    /**
     * Cierre de sesión local (antes de redirigir a Keycloak, laboratorio 05).
     */
    @EventListener
    public void cierreDeSesion(LogoutSuccessEvent evento) {
        Authentication autenticacion = evento.getAuthentication();
        AUDITORIA.info("LOGOUT usuario={} sid={}", nombre(autenticacion), sid(autenticacion));
    }

    /**
     * Acceso denegado por las reglas de SeguridadConfig.
     *
     * Dos casos distintos, con distinto nivel:
     *   - Un usuario AUTENTICADO sin el rol necesario: es el 403 real
     *     (laboratorio 06) y se registra como WARN, con su usuario y sid.
     *   - Un visitante ANÓNIMO: Spring también publica el evento, pero acto
     *     seguido lo envía al login. No es un incidente; se registra como
     *     INFO para que la traza esté completa sin generar alarmas.
     */
    @EventListener
    public void accesoDenegado(AuthorizationDeniedEvent<?> evento) {
        Authentication autenticacion = evento.getAuthentication().get();
        String ruta = ruta(evento.getObject());

        if (autenticacion != null && autenticacion.getPrincipal() instanceof OidcUser) {
            AUDITORIA.warn("DENEGADO usuario={} sid={} ruta={}",
                    nombre(autenticacion), sid(autenticacion), ruta);
        } else {
            AUDITORIA.info("SIN_SESION ruta={} (se redirige al inicio de sesión)", ruta);
        }
    }

    // ---- Utilidades: extraen identificadores, nunca tokens ---------------

    /**
     * El "objeto" del evento es lo que se estaba autorizando. Para las
     * reglas de authorizeHttpRequests, Spring Security 6.5 publica la
     * propia petición HTTP (HttpServletRequest); en otras versiones o en
     * autorización de métodos puede ser un RequestAuthorizationContext u
     * otro objeto. Se contemplan los dos primeros y, si no, se escribe la
     * clase, para no perder la traza.
     */
    private static String ruta(Object objeto) {
        if (objeto instanceof HttpServletRequest peticion) {
            return peticion.getRequestURI();
        }
        if (objeto instanceof RequestAuthorizationContext contexto) {
            return contexto.getRequest().getRequestURI();
        }
        return objeto == null ? "-" : objeto.getClass().getSimpleName();
    }

    private static String nombre(Authentication autenticacion) {
        if (autenticacion != null && autenticacion.getPrincipal() instanceof OidcUser usuario) {
            return usuario.getPreferredUsername();
        }
        return "anónimo";
    }

    private static String sid(Authentication autenticacion) {
        if (autenticacion != null && autenticacion.getPrincipal() instanceof OidcUser usuario) {
            return usuario.getIdToken().getClaimAsString("sid");
        }
        return "-";
    }
}
