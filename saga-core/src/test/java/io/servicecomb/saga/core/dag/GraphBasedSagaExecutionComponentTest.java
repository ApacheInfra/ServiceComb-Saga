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

package io.servicecomb.saga.core.dag;

import java.util.concurrent.Executors;

import io.servicecomb.saga.core.PersistentStore;
import io.servicecomb.saga.core.SagaExecutionComponentTestBase;
import io.servicecomb.saga.core.application.SagaFactory;


public class GraphBasedSagaExecutionComponentTest extends SagaExecutionComponentTestBase {

  @Override
  protected SagaFactory sagaFactory(PersistentStore eventStore) {
    return new GraphBasedSagaFactory(500, eventStore, null, Executors.newFixedThreadPool(5));
  }
}