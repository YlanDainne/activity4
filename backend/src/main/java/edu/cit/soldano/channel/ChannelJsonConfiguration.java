package edu.cit.soldano.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ChannelJsonConfiguration {
    @Bean
    ObjectMapper channelObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
