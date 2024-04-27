package ir.ut.se.tinyme.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Getter
@Configuration
@PropertySource("classpath:module-${spring.profiles.active}.properties")
public class Modules {

    @Value("${module.test.active}")
    private boolean testModuleActive;

    @Value("${module.test.inactive}")
    private boolean testModuleInactive;

}