package com.curso.keycloak.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;

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
}
