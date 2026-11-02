package com.ribeiry.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    private final OpenApiConfig config = new OpenApiConfig();

    @Test
    void openAPI_deveCriarBeanNaoNulo() {
        assertThat(config.openAPI()).isNotNull();
    }

    @Test
    void openAPI_deveTerTituloCorreto() {
        OpenAPI api = config.openAPI();
        assertThat(api.getInfo().getTitle()).isEqualTo("Outbox Pattern Demo");
    }

    @Test
    void openAPI_deveTerVersaoCorreta() {
        OpenAPI api = config.openAPI();
        assertThat(api.getInfo().getVersion()).isEqualTo("1.0.0");
    }

    @Test
    void openAPI_deveTerDescricaoContendoOutbox() {
        OpenAPI api = config.openAPI();
        assertThat(api.getInfo().getDescription()).containsIgnoringCase("outbox");
    }
}
