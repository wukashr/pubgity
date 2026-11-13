package org.traanite.pubgity.config

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod

/**
 * Override OICD provider config with mock values for testing.
 */
@TestConfiguration
class TestSecurityConfig {

    @Bean
    fun clientRegistrationRepository(): ClientRegistrationRepository {
        val registration = ClientRegistration.withRegistrationId("oidc")
            .clientId("test-client")
            .clientSecret("test-secret")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("openid", "profile", "email")
            .authorizationUri("http://localhost:8090/realms/pubgity/protocol/openid-connect/auth")
            .tokenUri("http://localhost:8090/realms/pubgity/protocol/openid-connect/token")
            .jwkSetUri("http://localhost:8090/realms/pubgity/protocol/openid-connect/certs")
            .userInfoUri("http://localhost:8090/realms/pubgity/protocol/openid-connect/userinfo")
            .userNameAttributeName("preferred_username")
            .clientName("oidc")
            .build()

        return InMemoryClientRegistrationRepository(registration)
    }
}
