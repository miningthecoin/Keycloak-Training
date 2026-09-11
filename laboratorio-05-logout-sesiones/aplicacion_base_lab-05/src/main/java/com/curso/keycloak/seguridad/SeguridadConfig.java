package com.curso.keycloak.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

/**
 * LABORATORIO 04: reglas de acceso e inicio de sesión con OpenID Connect.
 *
 * Esta clase sustituye la configuración por defecto de Spring Security
 * (que protegería TODO con un formulario propio) por:
 *
 *   - Rutas públicas: la portada "/" y los recursos estáticos.
 *   - Resto de rutas: exigen usuario autenticado.
 *   - Método de autenticación: OpenID Connect contra Keycloak, con el
 *     flujo Authorization Code y PKCE (S256).
 *
 * LABORATORIO 05: cierre de sesión iniciado por la aplicación
 * (RP-Initiated Logout). Al pulsar "Cerrar sesión" se invalida la sesión
 * local de Spring Y se redirige al usuario al endpoint de logout de
 * Keycloak para que también termine la sesión SSO del realm.
 *
 * No hay ninguna clase de Keycloak aquí. Todo es Spring Security estándar.
 */
@Configuration
@EnableWebSecurity
public class SeguridadConfig {

    @Bean
    SecurityFilterChain cadenaDeFiltros(HttpSecurity http,
                                        ClientRegistrationRepository registros) throws Exception {
        http
            // ---- Autorización por ruta ------------------------------
            .authorizeHttpRequests(reglas -> reglas
                // La portada y los estáticos siguen siendo públicos
                .requestMatchers("/", "/estilos.css").permitAll()
                // Cualquier otra ruta (por ejemplo /privada) requiere login
                .anyRequest().authenticated()
            )
            // ---- Inicio de sesión con OpenID Connect ----------------
            .oauth2Login(oidc -> oidc
                // Sustituye el generador de la petición de autorización
                // por uno que añade PKCE (ver método de abajo)
                .authorizationEndpoint(endpoint -> endpoint
                    .authorizationRequestResolver(resolutorConPkce(registros))
                )
            )
            // ---- Cierre de sesión (LABORATORIO 05) -------------------
            // Spring Security ya expone POST /logout (protegido con token
            // CSRF): invalida la sesión HTTP y borra la cookie JSESSIONID.
            // Eso solo cierra la sesión LOCAL. Con este manejador, al
            // terminar el logout local el navegador es enviado a Keycloak
            // para cerrar también la sesión SSO (ver método de abajo).
            .logout(salida -> salida
                .logoutSuccessHandler(cierreDeSesionEnKeycloak(registros))
            );

        return http.build();
    }

    /**
     * PKCE (Proof Key for Code Exchange, RFC 7636).
     *
     * Spring Security lo activa solo para clientes públicos (sin secreto).
     * Nuestro cliente es confidencial (tiene secreto), pero OAuth 2.1 y la
     * guía OIDC de Keycloak recomiendan PKCE también para confidenciales:
     * protege el "code" frente a interceptación y ataques de inyección.
     *
     * El resolutor genera un "code_verifier" aleatorio, lo guarda en la
     * sesión y envía su hash SHA-256 ("code_challenge") a Keycloak. Al
     * canjear el code, envía el verifier original; si no coincide,
     * Keycloak rechaza el canje.
     */
    private OAuth2AuthorizationRequestResolver resolutorConPkce(ClientRegistrationRepository registros) {
        DefaultOAuth2AuthorizationRequestResolver resolutor =
            new DefaultOAuth2AuthorizationRequestResolver(
                registros,
                OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
        resolutor.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        return resolutor;
    }

    /**
     * LABORATORIO 05: RP-Initiated Logout (OpenID Connect).
     *
     * OidcClientInitiatedLogoutSuccessHandler es la implementación estándar
     * de Spring Security. Tras el logout local, redirige el navegador al
     * "end_session_endpoint" que Keycloak publica en su documento de
     * descubrimiento (/realms/curso/.well-known/openid-configuration),
     * añadiendo:
     *
     *   - id_token_hint: el ID Token de la sesión que se cierra. Keycloak
     *     lo usa para saber qué sesión terminar y a qué cliente pertenece,
     *     sin pedir confirmación al usuario.
     *   - post_logout_redirect_uri: adónde volver al terminar. Debe estar
     *     en "Valid post logout redirect URIs" del cliente en Keycloak.
     *
     * "{baseUrl}" lo sustituye Spring por http://localhost:8081, así que
     * el usuario acaba en la portada, ya sin sesión en ningún sitio.
     */
    private LogoutSuccessHandler cierreDeSesionEnKeycloak(ClientRegistrationRepository registros) {
        OidcClientInitiatedLogoutSuccessHandler manejador =
            new OidcClientInitiatedLogoutSuccessHandler(registros);
        manejador.setPostLogoutRedirectUri("{baseUrl}/");
        return manejador;
    }
}
