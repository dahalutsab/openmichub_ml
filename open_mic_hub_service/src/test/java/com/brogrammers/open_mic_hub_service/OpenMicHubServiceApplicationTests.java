package com.brogrammers.open_mic_hub_service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the whole application against the database it actually ships with.
 *
 * <p>This is the broadest test here and it earns its runtime: starting the context runs every
 * Flyway migration in order and then has Hibernate validate its entity mappings against the
 * result. An entity that has drifted from the schema, a migration that will not apply cleanly, or
 * a bean that cannot be constructed all fail here rather than on startup in front of someone.
 *
 * <p>It needed a database and never had one, so every run failed on a refused connection to
 * localhost. It uses the Postgres that {@code docker compose} already starts, because the
 * migrations need a real one — they use partial indexes and the pgvector extension, and no
 * in-memory substitute will take them. Testcontainers would be the tidier answer if this project
 * ran its tests on the host, but it does not: the JDK here is newer than Lombok supports, so tests
 * run inside a container that has no access to the Docker daemon.
 *
 * <p>When no database is reachable the class is skipped rather than failed. A missing dependency
 * of the environment is not a broken application, and a suite that goes red for it teaches people
 * to ignore red.
 *
 * <pre>
 *   docker compose up -d postgres    # then the class runs
 * </pre>
 */
@SpringBootTest
@EnabledIf("databaseIsReachable")
class OpenMicHubServiceApplicationTests {

    private static final String HOST = System.getenv().getOrDefault("DB_HOST", "localhost");
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("DB_PORT", "5432"));

    @SuppressWarnings("unused") // Referenced by name from @EnabledIf.
    static boolean databaseIsReachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(HOST, PORT), 1000);
            return true;
        } catch (IOException unreachable) {
            return false;
        }
    }

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("the application starts, migrations apply, and the entity mappings still match")
    void contextLoads() {
        assertThat(context).isNotNull();
    }

    @Test
    @DisplayName("social sign-in stays off when no provider credentials are configured")
    void socialSignInIsOffByDefault() {
        // The feature has to be genuinely optional. If a registration repository were built from
        // blank credentials, Spring Boot's eager validation of that namespace would refuse to
        // start the application at all — which is what happened the first time this was wired up.
        assertThat(context.getBeanNamesForType(ClientRegistrationRepository.class)).isEmpty();
    }
}
