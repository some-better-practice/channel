package com.tsmc.autochannel.config;

import com.tsmc.autochannel.service.JSchFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!mock")
public class SftpConfig {

    @Bean
    public JSchFactory jschFactory() {
        return com.jcraft.jsch.JSch::new;
    }
}
