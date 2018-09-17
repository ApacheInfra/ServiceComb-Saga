/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.servicecomb.saga.alpha.server;

import java.util.Map;
import java.util.concurrent.*;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.apache.servicecomb.saga.alpha.core.CommandRepository;
import org.apache.servicecomb.saga.alpha.core.EventScanner;
import org.apache.servicecomb.saga.alpha.core.OmegaCallback;
import org.apache.servicecomb.saga.alpha.core.PendingTaskRunner;
import org.apache.servicecomb.saga.alpha.core.PushBackOmegaCallback;
import org.apache.servicecomb.saga.alpha.core.TxConsistentService;
import org.apache.servicecomb.saga.alpha.core.TxEventRepository;
import org.apache.servicecomb.saga.alpha.core.TxTimeoutRepository;
import org.apache.servicecomb.saga.alpha.server.tcc.GrpcTccEventService;
import org.apache.servicecomb.saga.alpha.server.tcc.callback.OmegaCallbackWrapper;
import org.apache.servicecomb.saga.alpha.server.tcc.callback.TccCallbackEngine;
import org.apache.servicecomb.saga.alpha.server.tcc.TccTxEventFacade;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@EntityScan(basePackages = "org.apache.servicecomb.saga.alpha")
@Configuration
class AlphaConfig {
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
  private final ExecutorService compensateExecutors = Executors.newCachedThreadPool();


  @Bean
  Map<String, Map<String, OmegaCallback>> omegaCallbacks() {
    return new ConcurrentHashMap<>();
  }

  @Bean
  OmegaCallback omegaCallback(Map<String, Map<String, OmegaCallback>> callbacks) {
    return new PushBackOmegaCallback(callbacks,compensateExecutors);
  }

  @Bean
  TxEventRepository springTxEventRepository(TxEventEnvelopeRepository eventRepo) {
    return new SpringTxEventRepository(eventRepo);
  }

  @Bean
  CommandRepository springCommandRepository(TxEventEnvelopeRepository eventRepo, CommandEntityRepository commandRepository) {
    return new SpringCommandRepository(eventRepo, commandRepository);
  }

  @Bean
  TxTimeoutRepository springTxTimeoutRepository(TxTimeoutEntityRepository timeoutRepo) {
    return new SpringTxTimeoutRepository(timeoutRepo);
  }

  @Bean
  ScheduledExecutorService compensationScheduler() {
    return scheduler;
  }

  @Bean
  GrpcServerConfig grpcServerConfig() { return new GrpcServerConfig(); }

  @Bean
  TxConsistentService txConsistentService(
          @Value("${alpha.event.pollingInterval:500}") int eventPollingInterval,
          ScheduledExecutorService scheduler,
          TxEventRepository eventRepository,
          CommandRepository commandRepository,
          TxTimeoutRepository timeoutRepository,
          OmegaCallback omegaCallback) {
    new EventScanner(scheduler,
            eventRepository, commandRepository, timeoutRepository,
            omegaCallback, eventPollingInterval).run();
    TxConsistentService consistentService = new TxConsistentService(eventRepository);
    return consistentService;
  }

  @Bean
  TccTxEventFacade tccTxEventFacade(
          @Value("${alpha.server.storage:rdb}") String storage,
          @Qualifier("defaultTccTxEventFacade") TccTxEventFacade defaultTccTxEventFacade,
          @Qualifier("rdbTccTxEventFacade") TccTxEventFacade rdbTccTxEventFacade) {
    return "rdb".equals(storage) ? rdbTccTxEventFacade : defaultTccTxEventFacade;
  }

  @Bean
  GrpcTccEventService grpcTccEventService(TccTxEventFacade tccTxEventFacade) {
    return new GrpcTccEventService(tccTxEventFacade);
  }

  @Bean
  ServerStartable serverStartable(GrpcServerConfig serverConfig, TxConsistentService txConsistentService,
                                  Map<String, Map<String, OmegaCallback>> omegaCallbacks, GrpcTccEventService grpcTccEventService) {
    ServerStartable bootstrap = new GrpcStartable(serverConfig,
            new GrpcTxEventEndpointImpl(txConsistentService, omegaCallbacks), grpcTccEventService);
    new Thread(bootstrap::start).start();
    return bootstrap;
  }

  @PostConstruct
  void init() { }

  @PreDestroy
  void shutdown() {
    scheduler.shutdownNow();
  }
}
