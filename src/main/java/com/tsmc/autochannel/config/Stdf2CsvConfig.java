package com.tsmc.autochannel.config;

import com.tsmc.autochannel.component.ProcessStarter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "stdf2csv")
public class Stdf2CsvConfig {

    private long timeoutSeconds = 300;

    public enum Converter_TYPE {
        TESTNAME, TESTNO, NONCHANNEL, NONMERGE
    }

    @Bean
    public ProcessStarter processStarter() {
        return ProcessBuilder::start;
    }

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
