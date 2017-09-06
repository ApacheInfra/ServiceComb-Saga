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

package io.servicecomb.saga.format;

import static io.servicecomb.saga.core.RecoveryPolicy.SAGA_BACKWARD_RECOVERY_POLICY;
import static io.servicecomb.saga.core.RecoveryPolicy.SAGA_FORWARD_RECOVERY_POLICY;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.servicecomb.saga.core.BackwardRecovery;
import io.servicecomb.saga.core.ForwardRecovery;
import io.servicecomb.saga.core.RecoveryPolicy;
import io.servicecomb.saga.core.SagaDefinition;
import java.util.HashMap;
import java.util.Map;

class JsonSagaDefinition implements SagaDefinition {

  static final RecoveryPolicy backwardRecovery = new BackwardRecovery();

  private static final Map<String, RecoveryPolicy> policies = new HashMap<String, RecoveryPolicy>(){{
    put(SAGA_BACKWARD_RECOVERY_POLICY, backwardRecovery);
    put(SAGA_FORWARD_RECOVERY_POLICY, new ForwardRecovery());
  }};

  private final JsonSagaRequest[] requests;
  private final RecoveryPolicy policy;

  public JsonSagaDefinition(
      @JsonProperty("policy") String policy,
      @JsonProperty("requests") JsonSagaRequest[] requests) {

    this.requests = requests;
    this.policy = policies.getOrDefault(policy, backwardRecovery);
  }

  @Override
  public RecoveryPolicy policy() {
    return policy;
  }

  @Override
  public JsonSagaRequest[] requests() {
    return requests;
  }
}
