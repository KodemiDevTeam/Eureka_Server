package com.example.service_registry;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ServiceRegistryApplicationTests {

    @Test
    void contextLoads() {
        Assertions.assertTrue(true);
    }

    @Test
    void mainMethodStartsApplication() {
        Assertions.assertDoesNotThrow(() ->
            ServiceRegistryApplication.main(new String[]{})
        );
    }
}
