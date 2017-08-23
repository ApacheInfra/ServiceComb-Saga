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

import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.servicecomb.saga.core.application.interpreter.JsonSagaRequest;

public class TransactionAbortedEvent extends SagaEvent {

  private final Exception exception;
  
  private ObjectMapper objectMapper=new ObjectMapper();

public  TransactionAbortedEvent(String sagaId, SagaRequest payload, Exception exception) {
    super(sagaId, payload);
    this.exception = exception;
  }

  @Override
  public void gatherTo(
      Map<String, SagaRequest> hangingTransactions,
      Set<String> abortedTransactions,
      Set<String> completedTransactions,
      Set<String> completedCompensations) {

    // remove from completed operations in order not to compensate it
    completedTransactions.remove(payload().id());
    abortedTransactions.add(payload().id());
    hangingTransactions.remove(payload().id());
  }
  
	@Override
	public String json() {
		try {
			//maybe need change exception type to string.
			return objectMapper.writeValueAsString(new SagaRequestException((JsonSagaRequest) payload(), exception));
		} catch (JsonProcessingException e) {
			throw new SagaException("Failed to serialize transaction: sage Id: " + payload().id() + " service name: "
					+ payload().serviceName(), e);
		}
	}

  @Override
  public String toString() {
    return "TransactionAbortedEvent{id="
        + payload().id()
        + ", operation="
        + payload().compensation()
        + "}";
  }
}
