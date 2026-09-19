package com.curso.keycloak.seguridad;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.authorization.AuthorizationEventPublisher;
import org.springframework.security.authorization.SpringAuthorizationEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
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
 * LABORATORIO 06: autorización basada en roles (RBAC). Los roles de realm
 * que Keycloak incluye en el ID Token (claim realm_access.roles) se
 * convierten en autoridades de Spring Security "ROLE_<rol>", y la ruta
 * /privada pasa a exigir el rol "gestor-clientes". Quien está autenticado
 * pero no tiene el rol recibe la página /denegado con HTTP 403.
 *
 * LABORATORIO 12: auditoría. Se declara un AuthorizationEventPublisher
 * para que Spring Security publique AuthorizationDeniedEvent cada vez que
 * deniega una petición; el componente AuditoriaDeAcceso lo escucha y lo
 * escribe en el logger "auditoria" (ver método publicadorDeAutorizacion).
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
                // LABORATORIO 06: /privada exige el rol de realm
                // "gestor-clientes". hasRole("x") comprueba la autoridad
                // "ROLE_x", que es la que crea el mapeador de abajo.
                // El orden importa: esta regla va ANTES de anyRequest().
                .requestMatchers("/privada").hasRole("gestor-clientes")
                // Cualquier otra ruta (por ejemplo /denegado) requiere login
                .anyRequest().authenticated()
            )
            // ---- Inicio de sesión con OpenID Connect ----------------
            .oauth2Login(oidc -> oidc
                // Sustituye el generador de la petición de autorización
                // por uno que añade PKCE (ver método de abajo)
                .authorizationEndpoint(endpoint -> endpoint
                    .authorizationRequestResolver(resolutorConPkce(registros))
                )
                // LABORATORIO 06: al terminar el login, Spring pasa las
                // autoridades del usuario por este mapeador, que añade los
                // roles de Keycloak (ver método rolesDeKeycloak).
                .userInfoEndpoint(usuario -> usuario
                    .userAuthoritiesMapper(rolesDeKeycloak())
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
            )
            // ---- Acceso denegado (LABORATORIO 06) --------------------
            // Un usuario AUTENTICADO sin el rol necesario recibe la página
            // /denegado con estado HTTP 403. Un visitante ANÓNIMO no llega
            // aquí: autenticación y autorización son comprobaciones
            // distintas, y a quien no se ha identificado Spring lo envía
            // al login (redirección), no a /denegado.
            .exceptionHandling(errores -> errores
                .accessDeniedPage("/denegado")
            );

        return http.build();
    }

    /**
     * LABORATORIO 06: de los roles de Keycloak a las autoridades de Spring.
     *
     * Tras validar el ID Token, Spring representa al usuario con una
     * autoridad OidcUserAuthority (que lleva dentro el ID Token) y una
     * autoridad "SCOPE_x" por cada scope concedido. Ninguna de ellas dice
     * qué ROLES tiene el usuario. Este mapeador:
     *
     *   1. Conserva las autoridades originales (OIDC_USER, SCOPE_*).
     *   2. Lee el claim "realm_access" del ID Token, que Keycloak rellena
     *      con el mapper "realm roles" del client scope "roles":
     *         "realm_access": { "roles": [ "gestor-clientes", ... ] }
     *   3. Añade una autoridad "ROLE_<rol>" por cada rol, que es el
     *      formato que esperan hasRole(...) y las reglas de acceso.
     *
     * Por qué se lee del ID TOKEN y no del endpoint userinfo: el ID Token
     * se emite y se firma en el momento del inicio de sesión, y Spring lo
     * guarda en la sesión local. Los roles que contiene son los que el
     * usuario tenía EN ESE INSTANTE. Si un administrador quita un rol en
     * Keycloak, la aplicación no se entera hasta que el usuario vuelve a
     * iniciar sesión y recibe un ID Token nuevo. Es lo que se observa en
     * el ejercicio de sacar a "ana" del grupo. userinfo se consulta una
     * sola vez en el login, así que tampoco daría roles "en vivo"; y
     * además Keycloak, por defecto, no incluye los roles en userinfo.
     *
     * No hay ninguna clase de Keycloak: solo un claim JSON y la API
     * estándar de Spring Security.
     */
    @Bean
    GrantedAuthoritiesMapper rolesDeKeycloak() {
        return autoridades -> {
            Set<GrantedAuthority> resultado = new HashSet<>(autoridades);
            for (GrantedAuthority autoridad : autoridades) {
                if (autoridad instanceof OidcUserAuthority oidc) {
                    Map<String, Object> realmAccess = oidc.getIdToken().getClaimAsMap("realm_access");
                    if (realmAccess != null && realmAccess.get("roles") instanceof Collection<?> roles) {
                        for (Object rol : roles) {
                            resultado.add(new SimpleGrantedAuthority("ROLE_" + rol));
                        }
                    }
                }
            }
            return resultado;
        };
    }

    /**
     * LABORATORIO 12: publicador de eventos de autorización.
     *
     * Por defecto Spring Security decide "permitido / denegado" en silencio:
     * no publica ningún evento cuando rechaza una petición. Al declarar este
     * bean, el filtro de autorización (authorizeHttpRequests) publica un
     * AuthorizationDeniedEvent por cada denegación, con el Authentication
     * del solicitante y el contexto de la petición (la ruta). Es lo que
     * AuditoriaDeAcceso convierte en una línea de auditoría.
     *
     * SpringAuthorizationEventPublisher es la implementación estándar; por
     * defecto solo publica las denegaciones (las concesiones serían
     * demasiado ruido: una por petición).
     */
    @Bean
    AuthorizationEventPublisher publicadorDeAutorizacion(ApplicationEventPublisher publicador) {
        return new SpringAuthorizationEventPublisher(publicador);
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
