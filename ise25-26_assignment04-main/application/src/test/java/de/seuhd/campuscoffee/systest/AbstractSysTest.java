package de.seuhd.campuscoffee.systest;

import de.seuhd.campuscoffee.api.mapper.PosDtoMapper;
import de.seuhd.campuscoffee.domain.ports.PosService;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import static de.seuhd.campuscoffee.TestUtils.configurePostgresContainers;
import static de.seuhd.campuscoffee.TestUtils.getPostgresContainer;

/**
 * Abstract base class for system tests.
 * Sets up the Spring Boot test context, manages the PostgreSQL testcontainer, and configures REST Assured.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractSysTest {
    protected static final PostgreSQLContainer<?> postgresContainer;

    static {
        // share the same testcontainers instance across all system tests
        postgresContainer = getPostgresContainer();
        postgresContainer.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        configurePostgresContainers(registry, postgresContainer);
    }

    @Autowired
    protected PosService posService;

    @Autowired
    protected PosDtoMapper posDtoMapper;

    @LocalServerPort
    private Integer port;

    @BeforeEach
    void beforeEach() {
        posService.clear();
        RestAssured.baseURI = "http://localhost:" + port;
    }

    @AfterEach
    void afterEach() {
        posService.clear();
    }
}