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

package io.servicecomb.saga.core;

import static io.servicecomb.saga.core.Compensation.SAGA_END_COMPENSATION;
import static io.servicecomb.saga.core.Compensation.SAGA_START_COMPENSATION;
import static io.servicecomb.saga.core.SagaTask.SAGA_END_TASK;
import static io.servicecomb.saga.core.SagaTask.SAGA_START_TASK;
import static io.servicecomb.saga.core.Transaction.SAGA_END_TRANSACTION;
import static io.servicecomb.saga.core.Transaction.SAGA_START_TRANSACTION;

public class NoOpSagaRequest implements SagaRequest {

  public static final SagaRequest SAGA_START_REQUEST = new NoOpSagaRequest(
      "saga-start",
      SAGA_START_TRANSACTION,
      SAGA_START_COMPENSATION,
      SAGA_START_TASK);

  public static final SagaRequest SAGA_END_REQUEST = new NoOpSagaRequest(
      "saga-end",
      SAGA_END_TRANSACTION,
      SAGA_END_COMPENSATION,
      SAGA_END_TASK);

  private final String id;
  private final Transaction transaction;
  private final Compensation compensation;
  private final String task;

  NoOpSagaRequest(String id, Transaction transaction, Compensation compensation, String task) {
    this.id = id;
    this.transaction = transaction;
    this.compensation = compensation;
    this.task = task;
  }

  @Override
  public Transaction transaction() {
    return transaction;
  }

  @Override
  public Compensation compensation() {
    return compensation;
  }

  @Override
  public String serviceName() {
    return "Saga";
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public String type() {
    return "nop";
  }

  @Override
  public String task() {
    return task;
  }
}
