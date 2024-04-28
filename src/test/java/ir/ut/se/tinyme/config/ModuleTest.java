package ir.ut.se.tinyme.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext
public class ModuleTest {
    @Autowired
    private Modules modules;

    @Test
    void test_module_test_active_is_active() {
        assertThat(modules.isModuleActive(Modules.MODULE_TEST_ACTIVE)).isTrue();
    }

    @Test
    void test_module_test_not_active_is_not_active() {
        assertThat(modules.isModuleActive(Modules.MODULE_TEST_INACTIVE)).isFalse();
    }
}
