package ir.ut.se.tinyme;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jms.annotation.EnableJms;

@SpringBootApplication
@EnableJms
public class TinyMEApplication {

	public static void main(String[] args) {
		SpringApplication.run(TinyMEApplication.class, args);
	}
}
