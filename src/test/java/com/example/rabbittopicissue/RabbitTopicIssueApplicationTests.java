package com.example.rabbittopicissue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpIOException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.AutoRecoverConnectionNotCurrentlyOpenException;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.annotation.BeforeTestClass;

import java.io.IOException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(locations="classpath:test.properties")
class RabbitTopicIssueApplicationTests implements ApplicationContextAware {

  @Autowired
  AmqpAdmin rabbitAdmin;

  @Autowired
  RabbitTemplate rabbitTemplate;

  @Autowired
  ConnectionFactory connectionFactory;

  Log log = LogFactory.getLog(RabbitTopicIssueApplicationTests.class);
  private ApplicationContext applicationContext;


  String exchangeName = "test-exchange";
  String wildcardRoutingKey = "#";
  String testNameSpace = "test-rabbit";
  @Test
  void contextLoads() {
  }

  private TaskExecutor getTaskExecutor(String name, int maxConsumers) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(maxConsumers);
    executor.setMaxPoolSize(maxConsumers);
    executor.setKeepAliveSeconds(300);
    executor.setQueueCapacity(1);
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.setThreadNamePrefix(name);
    executor.setAllowCoreThreadTimeOut(true);
    executor.initialize();
    return executor;
  }

  @Test
  public void testRebindFails() {

    AtomicLong received = new AtomicLong(0L);
    AtomicLong sent = new AtomicLong(0L);
    long max = 1000;


    Queue myQueue = QueueBuilder.nonDurable().autoDelete().build();
    TopicExchange topicExchange = new TopicExchange(exchangeName,true,false);
    Binding myBinding = BindingBuilder.bind(myQueue).to(topicExchange).with(wildcardRoutingKey);
    rabbitAdmin.declareExchange(topicExchange);
    rabbitAdmin.declareQueue(myQueue);
    rabbitAdmin.declareBinding(myBinding);
    DirectMessageListenerContainer container = new DirectMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.setAutoStartup(true);
    container.setApplicationContext(applicationContext);
    container.setAmqpAdmin(rabbitAdmin);
    container.setConsumersPerQueue(1);
    container.setFailedDeclarationRetryInterval(3000);
    container.setPrefetchCount(1);
    container.setMissingQueuesFatal(true);
    container.setMismatchedQueuesFatal(true);
    container.setTaskExecutor(getTaskExecutor("test-topic-consumer", 1));
    container.setQueues(myQueue);
    container.setDefaultRequeueRejected(true);


    container.setMessageListener(message -> {
      long count = received.incrementAndGet();
    });
    container.start();

    for(int i = 0;i<(max/2);i++) {
      rabbitTemplate.convertAndSend(exchangeName, "any", "testmessage-"+sent.get());
      sent.incrementAndGet();
    }

    //Do restart
    ProcessBuilder builder = new ProcessBuilder();
    try {
      Process proc = builder.command("kubectl", "rollout", "restart", "sts/rabbitmq-server", "-n", testNameSpace).start();
      Thread.sleep(1000);
      log.error(new String(proc.getInputStream().readAllBytes()));
      log.error(new String(proc.getErrorStream().readAllBytes()));
      Process proc2 = builder.command("kubectl", "rollout", "status", "sts/rabbitmq-server", "-n", testNameSpace).start();
      log.error(new String(proc2.getInputStream().readAllBytes()));
      log.error(new String(proc2.getErrorStream().readAllBytes()));

    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
    log.info("Finished rabbit restart, sending new messages");

    while (sent.get() < max) {
      rabbitTemplate.convertAndSend(exchangeName, "any", "testmessage-"+ sent.get());
      sent.incrementAndGet();
    }

    long timeout = 600000L;
    long start = System.currentTimeMillis();
    while (received.get() < max) {
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        //throw new RuntimeException(e);
      }
      assertTrue(System.currentTimeMillis() - start < timeout, "Timeout waiting for all messages - received " +received.get() + " of " + sent.get());
    }
    assertEquals(sent.get(), received.get());

  }

  @Test
  public void testRebindSuccess() {
    AutowireCapableBeanFactory factory = applicationContext.getAutowireCapableBeanFactory();
    BeanDefinitionRegistry registry = (BeanDefinitionRegistry)  factory;

    AtomicLong received = new AtomicLong(0L);
    AtomicLong sent = new AtomicLong(0L);
    long max = 1000;

    BeanDefinitionBuilder b =
        BeanDefinitionBuilder.rootBeanDefinition(AnonymousQueue.class);
    registry.registerBeanDefinition("topicBean", b.getBeanDefinition());
    Queue someOtherQueue = factory.getBean("topicBean", AnonymousQueue.class);


    Queue myQueue = QueueBuilder.nonDurable().autoDelete().build();
    TopicExchange topicExchange = new TopicExchange(exchangeName,true,false);
    Binding myBinding = BindingBuilder.bind(myQueue).to(topicExchange).with(wildcardRoutingKey);
    rabbitAdmin.declareExchange(topicExchange);
    rabbitAdmin.declareQueue(myQueue);
    rabbitAdmin.declareBinding(myBinding);
    DirectMessageListenerContainer container = new DirectMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.setAutoStartup(true);
    container.setApplicationContext(applicationContext);
    container.setAmqpAdmin(rabbitAdmin);
    container.setConsumersPerQueue(1);
    container.setFailedDeclarationRetryInterval(3000);
    container.setPrefetchCount(1);
    container.setMissingQueuesFatal(true);
    container.setMismatchedQueuesFatal(true);
    container.setTaskExecutor(getTaskExecutor("test-topic-consumer", 1));
    container.setQueues(myQueue);
    container.setDefaultRequeueRejected(true);


    container.setMessageListener(message -> {
      long count = received.incrementAndGet();
    });
    container.start();

    for(int i = 0;i<(max/2);i++) {
      rabbitTemplate.convertAndSend(exchangeName, "any", "testmessage-"+sent.get());
      sent.incrementAndGet();
    }

    //Do restart
    ProcessBuilder builder = new ProcessBuilder();
    try {
      Process proc = builder.command("kubectl", "rollout", "restart", "sts/rabbitmq-server", "-n", testNameSpace).start();
      Thread.sleep(1000);
      log.error(new String(proc.getInputStream().readAllBytes()));
      log.error(new String(proc.getErrorStream().readAllBytes()));
      Process proc2 = builder.command("kubectl", "rollout", "status", "sts/rabbitmq-server", "-n", testNameSpace).start();
      log.error(new String(proc2.getInputStream().readAllBytes()));
      log.error(new String(proc2.getErrorStream().readAllBytes()));

    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
    log.info("Finished rabbit restart, sending new messages");

    while (sent.get() < max) {
      rabbitTemplate.convertAndSend(exchangeName, "any", "testmessage-"+ sent.get());
      sent.incrementAndGet();
    }

    long timeout = 600000L;
    long start = System.currentTimeMillis();
    while (received.get() < max) {
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        //throw new RuntimeException(e);
      }
      assertTrue(System.currentTimeMillis() - start < timeout, "Timeout waiting for all messages - received " +received.get() + " of " + sent.get());
    }
    assertEquals(sent.get(), received.get());

  }

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    this.applicationContext = applicationContext;
  }
}
