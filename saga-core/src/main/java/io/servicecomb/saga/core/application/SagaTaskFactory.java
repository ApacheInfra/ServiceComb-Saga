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

package io.servicecomb.saga.core.application;

import static io.servicecomb.saga.core.SagaTask.SAGA_END_TASK;
import static io.servicecomb.saga.core.SagaTask.SAGA_REQUEST_TASK;
import static io.servicecomb.saga.core.SagaTask.SAGA_START_TASK;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.servicecomb.saga.core.EventStore;
import io.servicecomb.saga.core.FallbackPolicy;
import io.servicecomb.saga.core.LoggingRecoveryPolicy;
import io.servicecomb.saga.core.PersistentLog;
import io.servicecomb.saga.core.PersistentStore;
import io.servicecomb.saga.core.RecoveryPolicy;
import io.servicecomb.saga.core.RequestProcessTask;
import io.servicecomb.saga.core.SagaEndTask;
import io.servicecomb.saga.core.SagaEvent;
import io.servicecomb.saga.core.SagaLog;
import io.servicecomb.saga.core.SagaStartTask;
import io.servicecomb.saga.core.SagaTask;

class SagaTaskFactory {
  private final FallbackPolicy fallbackPolicy;
  private final RetrySagaLog retrySagaLog;

  SagaTaskFactory(int retryDelay, PersistentStore persistentStore) {
    fallbackPolicy = new FallbackPolicy(retryDelay);
    retrySagaLog = new RetrySagaLog(persistentStore, retryDelay);
  }

  Map<String, SagaTask> sagaTasks(String sagaId,
      String requestJson,
      RecoveryPolicy recoveryPolicy,
      EventStore sagaLog,
      PersistentStore persistentStore) {

    SagaLog compositeSagaLog = compositeSagaLog(sagaLog, persistentStore);

    return new HashMap<String, SagaTask>() {{
      put(SAGA_START_TASK, new SagaStartTask(sagaId, requestJson, compositeSagaLog));

      SagaLog retrySagaLog = compositeSagaLog(sagaLog, SagaTaskFactory.this.retrySagaLog);
      put(SAGA_REQUEST_TASK,
          new RequestProcessTask(sagaId, retrySagaLog, new LoggingRecoveryPolicy(recoveryPolicy), fallbackPolicy));
      put(SAGA_END_TASK, new SagaEndTask(sagaId, retrySagaLog));
    }};
  }

  private CompositeSagaLog compositeSagaLog(SagaLog sagaLog, PersistentLog persistentLog) {
    return new CompositeSagaLog(sagaLog, persistentLog);
  }

  static class RetrySagaLog implements PersistentLog {

    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final PersistentStore persistentStore;
    private final int retryDelay;

    RetrySagaLog(PersistentStore persistentStore, int retryDelay) {
      this.persistentStore = persistentStore;
      this.retryDelay = retryDelay;
    }

    @Override
    public void offer(SagaEvent sagaEvent) {
      boolean success = false;
      do {
        try {
          persistentStore.offer(sagaEvent);
          success = true;
          log.info("Persisted saga event {} successfully", sagaEvent);
        } catch (Exception e) {
          log.error("Failed to persist saga event {}", sagaEvent, e);
          sleep(retryDelay);
        }
      } while (!success && !isInterrupted());
    }

    private boolean isInterrupted() {
      return Thread.currentThread().isInterrupted();
    }

    private void sleep(int delay) {
      try {
        Thread.sleep(delay);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }
}