/*
 * Copyright 2017 Huawei Technologies Co., Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.servicecomb.saga.core.actors;

import static akka.actor.ActorRef.noSender;
import static io.servicecomb.saga.core.NoOpSagaRequest.SAGA_END_REQUEST;
import static io.servicecomb.saga.core.NoOpSagaRequest.SAGA_START_REQUEST;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import io.servicecomb.saga.core.EventStore;
import io.servicecomb.saga.core.PersistentStore;
import io.servicecomb.saga.core.SagaDefinition;
import io.servicecomb.saga.core.SagaResponse;
import io.servicecomb.saga.core.SagaTaskFactory;
import io.servicecomb.saga.core.application.SagaFactory;
import io.servicecomb.saga.core.application.interpreter.FromJsonFormat;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;

public class ActorBasedSagaFactory implements SagaFactory {
  private final ActorSystem actorSystem = ActorSystem.create("saga");
  private final RequestActorBuilder actorBuilder;
  private final SagaTaskFactory sagaTaskFactory;

  public ActorBasedSagaFactory(int retryDelay,
      PersistentStore persistentStore,
      FromJsonFormat<Set<String>> childrenExtractor) {

    this.sagaTaskFactory = new SagaTaskFactory(retryDelay, persistentStore);
    this.actorBuilder = new RequestActorBuilder(actorSystem, childrenExtractor);
  }

  @Override
  public ActorBasedSaga createSaga(String requestJson, String sagaId, EventStore sagaLog, SagaDefinition definition) {

    CompletableFuture<SagaResponse> future = new CompletableFuture<>();
    ActorRef completionCallback = actorSystem.actorOf(CompletionCallbackActor.props(future));
    RequestActorContext context = actorBuilder.build(
        definition.requests(),
        sagaTaskFactory.sagaTasks(sagaId,
            requestJson,
            definition.policy(),
            sagaLog
        ),
        completionCallback);

    completionCallback.tell(context.actorOf(SAGA_END_REQUEST.id()), noSender());
    return new ActorBasedSaga(
        context.actorOf(SAGA_START_REQUEST.id()),
        completionCallback,
        future,
        sagaLog,
        new EventContextImpl(context));
  }
}
