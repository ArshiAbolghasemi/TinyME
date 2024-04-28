package ir.ut.se.tinyme.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;

@Configuration
@PropertySource("classpath:module-${spring.profiles.active}.properties")
public class Modules {

    public static final String MODULE_TEST_ACTIVE = "module.test.active";

    public static final String MODULE_TEST_INACTIVE = "module.test.inactive";

    public static final String ADDING_STOP_LIMIT_ORDER_ENTITY = "adding.stop.limit.order.entity";

    private static Environment environment;

    public Modules(Environment environment) {
        Modules.environment = environment;
    }

    public static boolean isModuleActive(String moduleName) {
        return Boolean.TRUE.equals(environment.getProperty(moduleName, Boolean.class));
    }

}