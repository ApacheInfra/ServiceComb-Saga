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

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.servicecomb.saga.core.RestOperation;
import io.servicecomb.saga.core.SagaResponse;
import io.servicecomb.saga.transports.RestTransport;
import io.servicecomb.saga.transports.TransportFactory;

class JacksonRestOperation extends RestOperation implements TransportAware {

  @JsonIgnore
  private RestTransport transport;

  JacksonRestOperation(String path, String method, Map<String, Map<String, String>> params) {
    super(path, method, params);
  }

  @Override
  public JacksonRestOperation with(TransportFactory transport) {
    this.transport = transport.restTransport();
    return this;
  }

  @Override
  public SagaResponse send(String address) {
    return transport.with(address, path(), method(), params());
  }

  @Override
  public SagaResponse send(String address, SagaResponse response) {
    Map<String, Map<String, String>> updated = new HashMap<>(params());
    updated.computeIfAbsent("form", (key) -> new HashMap<>()).put("response", response.body());

    return transport.with(
        address,
        path(),
        method(),
        updated);
  }
}
