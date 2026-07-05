package com.example.myagentclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/*
  @ConfigurationPropertiesScan

  application.properties
    my-agent-client.inbox.base-url=http://localhost:8025
    my-agent-client.inbox.address=support@example.com
           │
           ▼
  @ConfigurationProperties("my-agent-client.inbox")
  public class InboxProperties {
      private String baseUrl;
      private String address;
      ...
  }
           │
           ▼
  @ConfigurationPropertiesScan  ← 自動找到並注入上面的類別

  沒有這個註解，@ConfigurationProperties 的類別就不會被 Spring 管理。
 */
@EnableScheduling
@ConfigurationPropertiesScan // 自動掃描並註冊所有 @ConfigurationProperties 的類別
@SpringBootApplication // 開啟 Spring 的排程功能，讓 @Scheduled 註解生效；對應　my-agent-client.inbox.poll-interval=10000  ← 每 10 秒輪詢一次
public class MyAgentClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyAgentClientApplication.class, args);
    }

}
