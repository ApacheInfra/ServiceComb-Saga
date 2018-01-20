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

import static com.seanyinx.github.unit.scaffolding.Randomness.uniquify;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.servicecomb.saga.common.EventType.SagaEndedEvent;
import static org.apache.servicecomb.saga.common.EventType.TxAbortedEvent;
import static org.apache.servicecomb.saga.common.EventType.TxCompensatedEvent;
import static org.apache.servicecomb.saga.common.EventType.TxEndedEvent;
import static org.apache.servicecomb.saga.common.EventType.TxStartedEvent;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javax.annotation.PostConstruct;

import org.apache.servicecomb.saga.alpha.core.CommandRepository;
import org.apache.servicecomb.saga.alpha.core.EventScanner;
import org.apache.servicecomb.saga.alpha.core.OmegaCallback;
import org.apache.servicecomb.saga.alpha.core.TxConsistentService;
import org.apache.servicecomb.saga.alpha.core.TxEvent;
import org.apache.servicecomb.saga.alpha.core.TxEventRepository;
import org.apache.servicecomb.saga.common.EventType;
import org.apache.servicecomb.saga.pack.contract.grpc.GrpcAck;
import org.apache.servicecomb.saga.pack.contract.grpc.GrpcCompensateCommand;
import org.apache.servicecomb.saga.pack.contract.grpc.GrpcServiceConfig;
import org.apache.servicecomb.saga.pack.contract.grpc.GrpcTxEvent;
import org.apache.servicecomb.saga.pack.contract.grpc.TxEventServiceGrpc;
import org.apache.servicecomb.saga.pack.contract.grpc.TxEventServiceGrpc.TxEventServiceBlockingStub;
import org.apache.servicecomb.saga.pack.contract.grpc.TxEventServiceGrpc.TxEventServiceStub;
import org.hamcrest.core.Is;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.google.protobuf.ByteString;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {AlphaApplication.class, AlphaConfig.class},
    properties = {"alpha.server.port=8090", "alpha.event.pollingInterval=1"})
public class AlphaIntegrationTest {
  private static final int port = 8090;

  private static final ManagedChannel clientChannel = ManagedChannelBuilder
      .forAddress("localhost", port).usePlaintext(true).build();

  private final TxEventServiceStub asyncStub = TxEventServiceGrpc.newStub(clientChannel);
  private final TxEventServiceBlockingStub blockingStub = TxEventServiceGrpc.newBlockingStub(clientChannel);

  private static final String payload = "hello world";

  private final String globalTxId = UUID.randomUUID().toString();
  private final String localTxId = UUID.randomUUID().toString();
  private final String parentTxId = UUID.randomUUID().toString();
  private final String compensationMethod = getClass().getCanonicalName();
  private final String serviceName = uniquify("serviceName");
  private final String instanceId = uniquify("instanceId");

  private final GrpcServiceConfig serviceConfig = GrpcServiceConfig.newBuilder()
      .setServiceName(serviceName)
      .setInstanceId(instanceId)
      .build();

  @Autowired
  private TxEventEnvelopeRepository eventRepo;

  @Autowired
  private TxEventRepository eventRepository;

  @Autowired
  private CommandRepository commandRepository;

  @Autowired
  private CommandEntityRepository commandEntityRepository;

  @Autowired
  private OmegaCallback omegaCallback;

  @Autowired
  private Map<String, Map<String, OmegaCallback>> omegaCallbacks;

  @Autowired
  private TxConsistentService consistentService;

  private static final Queue<GrpcCompensateCommand> receivedCommands = new ConcurrentLinkedQueue<>();
  private final CompensateStreamObserver compensateResponseObserver = new CompensateStreamObserver(this::onCompensation);

  @AfterClass
  public static void tearDown() throws Exception {
    clientChannel.shutdown();
  }

  @Before
  public void before() {
    eventRepo.deleteAll();
    receivedCommands.clear();
  }

  @After
  public void after() throws Exception {
    blockingStub.onDisconnected(serviceConfig);
    deleteAllTillSuccessful();
  }

  public void deleteAllTillSuccessful() {
    boolean deleted = false;
    do {
      try {
        eventRepo.deleteAll();
        commandEntityRepository.deleteAll();
        deleted = true;
      } catch (Exception ignored) {
      }
    } while (!deleted);
  }

  @Test
  public void persistsEvent() {
    asyncStub.onConnected(serviceConfig, compensateResponseObserver);
    blockingStub.onTxEvent(someGrpcEvent(TxStartedEvent));
    // use the asynchronous stub need to wait for some time
    await().atMost(1, SECONDS).until(() -> !eventRepo.findByGlobalTxId(globalTxId).isEmpty());

    assertThat(receivedCommands.isEmpty(), is(true));

    TxEvent envelope = eventRepo.findByGlobalTxId(globalTxId).get(0);

    assertThat(envelope.serviceName(), is(serviceName));
    assertThat(envelope.instanceId(), is(instanceId));
    assertThat(envelope.globalTxId(), is(globalTxId));
    assertThat(envelope.localTxId(), is(localTxId));
    assertThat(envelope.parentTxId(), is(parentTxId));
    assertThat(envelope.type(), Is.is(TxStartedEvent.name()));
    assertThat(envelope.compensationMethod(), is(compensationMethod));
    assertThat(envelope.payloads(), is(payload.getBytes()));
  }

  @Test
  public void closeStreamOnDisconnected() {
    asyncStub.onConnected(serviceConfig, compensateResponseObserver);

    await().atMost(1, SECONDS).until(() -> omegaCallbacks.containsKey(serviceConfig.getServiceName()));

    assertThat(
        omegaCallbacks.get(serviceConfig.getServiceName()).get(serviceConfig.getInstanceId()),
        is(notNullValue()));

    blockingStub.onDisconnected(serviceConfig);
    assertThat(
        omegaCallbacks.get(serviceConfig.getServiceName()).containsKey(serviceConfig.getInstanceId()),
        is(false));

    await().atMost(1, SECONDS).until(compensateResponseObserver::isCompleted);
  }

  @Test
  public void closeStreamOfDisconnectedClientOnly() {
    asyncStub.onConnected(serviceConfig, compensateResponseObserver);
    await().atMost(1, SECONDS).until(() -> omegaCallbacks.containsKey(serviceConfig.getServiceName()));

    GrpcServiceConfig anotherServiceConfig = someServiceConfig();
    CompensateStreamObserver anotherResponseObserver = new CompensateStreamObserver();
    TxEventServiceGrpc.newStub(clientChannel).onConnected(anotherServiceConfig, anotherResponseObserver);

    await().atMost(1, SECONDS).until(() -> omegaCallbacks.containsKey(anotherServiceConfig.getServiceName()));

    blockingStub.onDisconnected(serviceConfig);

    assertThat(
        omegaCallbacks.get(anotherServiceConfig.getServiceName()).containsKey(anotherServiceConfig.getInstanceId()),
        is(true));

    assertThat(anotherResponseObserver.isCompleted(), is(false));

    TxEventServiceGrpc.newBlockingStub(clientChannel).onDisconnected(anotherServiceConfig);
  }

  @Test
  public void removeCallbackOnClientDown() throws Exception {
    asyncStub.onConnected(serviceConfig, compensateResponseObserver);
    blockingStub.onTxEvent(someGrpcEvent(TxStartedEvent));
    blockingStub.onTxEvent(someGrpcEvent(TxEndedEvent));

    omegaCallbacks.get(serviceName).get(instanceId).disconnect();

    consistentService.handle(someTxAbortEvent(serviceName, instanceId));

    await().atMost(1, SECONDS).until(() -> omegaCallbacks.get(serviceName).isEmpty());
  }

  @Test
  public void compensateImmediatelyWhenGlobalTxAlreadyAborted() throws Exception {
    asyncStub.onConnected(serviceConfig, compensateResponseObserver);
    blockingStub.onTxEvent(someGrpcEvent(TxStartedEvent));
    blockingStub.onTxEvent(someGrpcEvent(TxAbortedEvent));

    blockingStub.onTxEvent(eventOf(TxEndedEvent, localTxId, parentTxId, new byte[0], compensationMethod));
    await().atMost(1, SECONDS).until(() -> !receivedCommands.isEmpty());

    GrpcCompensateCommand command = receivedCommands.poll();
    assertThat(command.getGlobalTxId(), is(globalTxId));
    assertThat(command.getLocalTxId(), is(localTxId));
    assertThat(command.getParentTxId(), is(parentTxId));
    assertThat(command.getCompensateMethod(), is(compensationMethod));
    assertThat(command.getPayloads().toByteArray(), is(payload.getBytes()));
  }

  @Test
  public void doNotCompensateDuplicateTxOnFailure() {
    // duplicate events with same content but different timestamp
    asyncStub.onConnected(serviceConfig, compensateResponseObserver);
    blockingStub.onTxEvent(eventOf(TxStartedEvent, localTxId, parentTxId, "service a".getBytes(), "method a"));
    blockingStub.onTxEvent(eventOf(TxStartedEvent, localTxId, parentTxId, "service a".getBytes(), "method a"));
    blockingStub.onTxEvent(eventOf(TxEndedEvent, localTxId, parentTxId, new byte[0], "method a"));

    String localTxId1 = UUID.randomUUID().toString();
    String parentTxId1 = UUID.randomUUID().toString();
    blockingStub.onTxEvent(eventOf(TxStartedEvent, localTxId1, parentTxId1, "service b".getBytes(), "method b"));
    blockingStub.onTxEvent(eventOf(TxEndedEvent, localTxId1, parentTxId1, new byte[0], "method b"));

    blockingStub.onTxEvent(someGrpcEvent(TxAbortedEvent));
    await().atMost(1, SECONDS).until(() -> receivedCommands.size() > 1);

    assertThat(receivedCommands, containsInAnyOrder(
        GrpcCompensateCommand.newBuilder().setGlobalTxId(globalTxId).setLocalTxId(localTxId1).setParentTxId(parentTxId1)
            .setCompensateMethod("method b").setPayloads(ByteString.copyFrom("service b".getBytes())).build(),
        GrpcCompensateCommand.newBuilder().setGlobalTxId(globalTxId).setLocalTxId(localTxId).setParentTxId(parentTxId)
            .setCompensateMethod("method a").setPayloads(ByteString.copyFrom("service a".getBytes())).build()
    ));
  }

  @Test
  public void getCompensateCommandOnFailure() {
    asyncStub.onConnected(serviceConfig, compensateResponseObserver);
    blockingStub.onTxEvent(someGrpcEvent(TxStartedEvent));
    blockingStub.onTxEvent(someGrpcEvent(TxEndedEvent));
    await().atMost(1, SECONDS).until(() -> !eventRepo.findByGlobalTxId(globalTxId).isEmpty());

    blockingStub.onTxEvent(someGrpcEvent(TxAbortedEvent));
    await().atMost(1, SECONDS).until(() -> !receivedCommands.isEmpty());

    GrpcCompensateCommand command = receivedCommands.poll();
    assertThat(command.getGlobalTxId(), is(globalTxId));
    assertThat(command.getLocalTxId(), is(localTxId));
    assertThat(command.getParentTxId(), is(parentTxId));
    assertThat(command.getCompensateMethod(), is(compensationMethod));
    assertThat(command.getPayloads().toByteArray(), is(payload.getBytes()));
  }

  @Test
  public void compensateOnlyFailedGlobalTransaction() {
    asyncStub.onConnected(serviceConfig, compensateResponseObserver);
    blockingStub.onTxEvent(someGrpcEvent(TxStartedEvent));
    blockingStub.onTxEvent(someGrpcEvent(TxEndedEvent));

    // simulates connection from another service with different globalTxId
    GrpcServiceConfig anotherServiceConfig = someServiceConfig();
    TxEventServiceGrpc.newStub(clientChannel).onConnected(anotherServiceConfig, new CompensateStreamObserver());

    TxEventServiceBlockingStub anotherBlockingStub = TxEventServiceGrpc.newBlockingStub(clientChannel);
    anotherBlockingStub.onTxEvent(someGrpcEvent(TxStartedEvent, UUID.randomUUID().toString()));

    await().atMost(1, SECONDS).until(() -> eventRepo.count() == 3);

    blockingStub.onTxEvent(someGrpcEvent(TxAbortedEvent));
    await().atMost(1, SECONDS).until(() -> !receivedCommands.isEmpty());

    assertThat(receivedCommands.size(), is(1));
    assertThat(receivedCommands.poll().getGlobalTxId(), is(globalTxId));

    anotherBlockingStub.onDisconnected(anotherServiceConfig);
  }

  @Test
  public void doNotStartSubTxOnFailure() {
    asyncStub.onConnected(serviceConfig, compensateResponseObserver);

    blockingStub.onTxEvent(eventOf(TxStartedEvent, localTxId, parentTxId, "service a".getBytes(), "method a"));
    blockingStub.onTxEvent(someGrpcEvent(TxEndedEvent));

    blockingStub.onTxEvent(someGrpcEvent(TxAbortedEvent));

    await().atMost(1, SECONDS).until(() -> receivedCommands.size() == 1);

    String localTxId1 = UUID.randomUUID().toString();
    String parentTxId1 = UUID.randomUUID().toString();
    GrpcAck result = blockingStub
        .onTxEvent(eventOf(TxStartedEvent, localTxId1, parentTxId1, "service b".getBytes(), "method b"));

    assertThat(result.getAborted(), is(true));
  }

  @Test
  public void compensateOnlyCompletedTransactions() {
    asyncStub.onConnected(serviceConfig, compensateResponseObserver);
    blockingStub.onTxEvent(someGrpcEvent(TxStartedEvent));
    blockingStub.onTxEvent(someGrpcEvent(TxEndedEvent));

    String anotherLocalTxId1 = UUID.randomUUID().toString();
    blockingStub.onTxEvent(someGrpcEvent(TxStartedEvent, globalTxId, anotherLocalTxId1));
    blockingStub.onTxEvent(someGrpcEvent(TxEndedEvent, globalTxId, anotherLocalTxId1));
    blockingStub.onTxEvent(someGrpcEvent(TxCompensatedEvent, globalTxId, anotherLocalTxId1));

    String anotherLocalTxId2 = UUID.randomUUID().toString();
    blockingStub.onTxEvent(someGrpcEvent(TxStartedEvent, globalTxId, anotherLocalTxId2));

    await().atMost(1, SECONDS).until(() -> eventRepo.count() == 7);

    blockingStub.onTxEvent(someGrpcEvent(TxAbortedEvent, globalTxId, anotherLocalTxId2));
    await().atMost(1, SECONDS).until(() -> !receivedCommands.isEmpty());

    assertThat(receivedCommands.size(), is(1));
    assertThat(receivedCommands.poll().getGlobalTxId(), is(globalTxId));
  }

  @Test
  public void sagaEndedEventIsAlwaysInTheEnd() throws Exception {
    asyncStub.onConnected(serviceConfig, compensateResponseObserver);
    blockingStub.onTxEvent(someGrpcEvent(TxStartedEvent));
    blockingStub.onTxEvent(someGrpcEvent(TxEndedEvent));

    String anotherLocalTxId = UUID.randomUUID().toString();
    blockingStub.onTxEvent(someGrpcEvent(TxStartedEvent, globalTxId, anotherLocalTxId));
    blockingStub.onTxEvent(someGrpcEvent(TxAbortedEvent, globalTxId, anotherLocalTxId));

    blockingStub.onTxEvent(someGrpcEvent(TxEndedEvent, globalTxId, anotherLocalTxId));

    await().atMost(1, SECONDS).until(() -> eventRepo.count() == 8);
    List<TxEvent> events = eventRepo.findByGlobalTxId(globalTxId);
    assertThat(events.get(events.size() - 1).type(), is(SagaEndedEvent.name()));
  }

  private GrpcAck onCompensation(GrpcCompensateCommand command) {
    return blockingStub.onTxEvent(
        eventOf(TxCompensatedEvent,
            command.getLocalTxId(),
            command.getParentTxId(),
            new byte[0],
            command.getCompensateMethod()));
  }

  private GrpcServiceConfig someServiceConfig() {
    return GrpcServiceConfig.newBuilder()
        .setServiceName(uniquify("serviceName"))
        .setInstanceId(uniquify("instanceId"))
        .build();
  }

  private TxEvent someTxAbortEvent(String serviceName, String instanceId) {
    return new TxEvent(
        serviceName,
        instanceId,
        new Date(),
        globalTxId,
        localTxId,
        parentTxId,
        TxAbortedEvent.name(),
        compensationMethod,
        payload.getBytes());
  }

  private GrpcTxEvent someGrpcEvent(EventType type) {
    return eventOf(type, localTxId, parentTxId, payload.getBytes(), getClass().getCanonicalName());
  }

  private GrpcTxEvent someGrpcEvent(EventType type, String globalTxId) {
    return someGrpcEvent(type, globalTxId, localTxId);
  }

  private GrpcTxEvent someGrpcEvent(EventType type, String globalTxId, String localTxId) {
    return eventOf(type, globalTxId, localTxId, parentTxId, payload.getBytes(), getClass().getCanonicalName());
  }

  private GrpcTxEvent eventOf(EventType eventType, String localTxId, String parentTxId, byte[] payloads, String compensationMethod) {
    return eventOf(eventType, globalTxId, localTxId, parentTxId, payloads, compensationMethod);
  }

  private GrpcTxEvent eventOf(EventType eventType,
      String globalTxId,
      String localTxId,
      String parentTxId,
      byte[] payloads,
      String compensationMethod) {

    return GrpcTxEvent.newBuilder()
        .setServiceName(serviceName)
        .setInstanceId(instanceId)
        .setTimestamp(System.currentTimeMillis())
        .setGlobalTxId(globalTxId)
        .setLocalTxId(localTxId)
        .setParentTxId(parentTxId == null ? "" : parentTxId)
        .setType(eventType.name())
        .setCompensationMethod(compensationMethod)
        .setPayloads(ByteString.copyFrom(payloads))
        .build();
  }

  private static class CompensateStreamObserver implements StreamObserver<GrpcCompensateCommand> {
    private final Consumer<GrpcCompensateCommand> consumer;
    private boolean completed = false;

    private CompensateStreamObserver() {
      this(command -> {});
    }

    private CompensateStreamObserver(Consumer<GrpcCompensateCommand> consumer) {
      this.consumer = consumer;
    }

    @Override
    public void onNext(GrpcCompensateCommand command) {
      // intercept received command
      consumer.accept(command);
      receivedCommands.add(command);
    }

    @Override
    public void onError(Throwable t) {
    }

    @Override
    public void onCompleted() {
      completed = true;
    }

    boolean isCompleted() {
      return completed;
    }
  }

  @PostConstruct
  void init() {
    // simulates concurrent db connections
    new EventScanner(
        Executors.newSingleThreadScheduledExecutor(),
        eventRepository,
        commandRepository,
        omegaCallback,
        1).run();
  }
}
