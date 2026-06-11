package com.evanp.f1.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = TestApplication.class)
@Import(TestInfrastructureConfiguration.class)
@ActiveProfiles("test")
class F1VisualizerApplicationTests {

    @Test
    void contextLoads() {}
}
