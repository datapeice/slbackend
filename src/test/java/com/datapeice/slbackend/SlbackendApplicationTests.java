package com.datapeice.slbackend;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Disabled because it requires a connected PostgreSQL database to load context")
class SlbackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
