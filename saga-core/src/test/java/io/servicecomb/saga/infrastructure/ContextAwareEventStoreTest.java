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

package io.servicecomb.saga.infrastructure;

import static org.mockito.Mockito.verify;

import org.junit.Test;
import org.mockito.Mockito;

import io.servicecomb.saga.core.EventStore;
import io.servicecomb.saga.core.SagaContext;
import io.servicecomb.saga.core.SagaEvent;

public class ContextAwareEventStoreTest {
  private final EventStore underlying = Mockito.mock(EventStore.class);
  private final SagaContext context = Mockito.mock(SagaContext.class);
  private final SagaEvent sagaEvent = Mockito.mock(SagaEvent.class);

  private final ContextAwareEventStore contextAwareEventStore = new ContextAwareEventStore(underlying, context);

  @Test
  public void persistWithUnderlyingStore() throws Exception {
    contextAwareEventStore.offer(sagaEvent);

    verify(sagaEvent).gatherTo(context);
    verify(underlying).offer(sagaEvent);
  }
}