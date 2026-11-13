package com.example.compliance;

import java.util.Arrays;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Entry point.
 *
 * <ul>
 *   <li>{@code java -jar compliance-evaluator.jar} starts the REST API used by the Angular UI.</li>
 *   <li>{@code java -jar compliance-evaluator.jar --cli [--transactions=FILE] [--rules=FILE]} evaluates the
 *       files, prints the report to stdout (diagnostics to stderr) and exits.</li>
 * </ul>
 */
@SpringBootApplication
public class ComplianceApplication {

    public static final String CLI_FLAG = "--cli";

    public static void main(String[] args) {
        if (Arrays.asList(args).contains(CLI_FLAG)) {
            // Keep stdout clean: no web server, no banner, no framework logging.
            int exitCode = SpringApplication.exit(new SpringApplicationBuilder(ComplianceApplication.class)
                    .web(WebApplicationType.NONE)
                    .bannerMode(Banner.Mode.OFF)
                    .logStartupInfo(false)
                    .properties("logging.level.root=ERROR")
                    .run(args));
            System.exit(exitCode);
        }
        SpringApplication.run(ComplianceApplication.class, args);
    }
}
