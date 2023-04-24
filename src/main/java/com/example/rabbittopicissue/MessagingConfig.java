package com.example.rabbittopicissue;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ShutdownSignalException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ChannelListener;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MessagingProperties.class)
public class MessagingConfig {
  @Autowired
  private MessagingProperties properties;
  Log log = LogFactory.getLog(MessagingConfig.class);
  public MessagingProperties getProperties() {
    return properties;
  }

  public void setProperties(MessagingProperties properties) {
    this.properties = properties;
  }


  @Bean
  public RabbitTemplate rabbitTemplate() {
    RabbitTemplate rabbit = new RabbitTemplate(connectionFactory());
    // set the default..
    rabbit.setMessageConverter(new SimpleMessageConverter());
    return rabbit;
  }

  @Bean
  public RabbitAdmin rabbitAdmin() {
    RabbitAdmin admin = new RabbitAdmin(rabbitTemplate());
    admin.setRedeclareManualDeclarations(true);
    admin.setAutoStartup(false);
    return admin;
  }


  @Bean
  public ConnectionFactory connectionFactory() {
    CachingConnectionFactory cachingConnectionFactory = new CachingConnectionFactory();
    cachingConnectionFactory.setAddresses(properties.getAddresses());
    cachingConnectionFactory.setUsername(properties.getUsername());
    cachingConnectionFactory.setPassword(properties.getPassword());
    cachingConnectionFactory.setCacheMode(CachingConnectionFactory.CacheMode.CHANNEL);
    cachingConnectionFactory.addChannelListener(new ChannelListener() {
      @Override
      public void onCreate(Channel channel, boolean transactional) {
        log.info(channel.toString());
      }

      @Override
      public void onShutDown(ShutdownSignalException signal) {
        log.info("Shutdown: ", signal);
        ChannelListener.super.onShutDown(signal);
      }
    });
    return cachingConnectionFactory;
  }
}
