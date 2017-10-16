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

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.servicecomb.saga.core.SagaException;
import io.servicecomb.saga.core.application.interpreter.FromJsonFormat;

public class ChildrenExtractor implements FromJsonFormat<Set<String>> {

  private static final String SAGA_CHILDREN = "sagaChildren";
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public Set<String> fromJson(String json) {
    try {
      return childrenOf(objectMapper.readValue(json, ObjectNode.class));
    } catch (IOException e) {
      throw new SagaException("Failed to deserialize json " + json, e);
    }
  }

  private Set<String> childrenOf(ObjectNode value) {
    Set<String> children = new HashSet<>();

    if (value.has(SAGA_CHILDREN)) {
      value.get(SAGA_CHILDREN)
          .forEach(node -> children.add(node.textValue()));
    }

    return children;
  }
}
