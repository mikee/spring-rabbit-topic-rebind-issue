package com.example.rabbittopicissue;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "messaging")
public class MessagingProperties {
  private String addresses = "localhost:5672";
  private String topicExchange = " testExchange";

  private String username = "guest";
  private String password = "guest";

  public String getAddresses() {
    return addresses;
  }

  public void setAddresses(String addresses) {
    this.addresses = addresses;
  }

  public String getTopicExchange() {
    return topicExchange;
  }

  public void setTopicExchange(String topicExchange) {
    this.topicExchange = topicExchange;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}
