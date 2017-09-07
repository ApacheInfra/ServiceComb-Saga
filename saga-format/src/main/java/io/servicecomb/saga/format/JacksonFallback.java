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

import static io.servicecomb.saga.core.Operation.TYPE_NOP;
import static io.servicecomb.saga.core.Operation.TYPE_REST;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.servicecomb.saga.core.Fallback;
import io.servicecomb.saga.core.Operation;
import io.servicecomb.saga.format.JacksonFallback.NopJacksonFallback;
import io.servicecomb.saga.transports.TransportFactory;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    visible = true,
    property = "type")
@JsonSubTypes({
    @Type(value = JacksonRestFallback.class, name = TYPE_REST),
    @Type(value = NopJacksonFallback.class, name = TYPE_NOP)
})
public interface JacksonFallback extends Fallback, TransportAware {

  JacksonFallback NOP_TRANSPORT_AWARE_FALLBACK = new NopJacksonFallback(TYPE_NOP);

  class NopJacksonFallback implements JacksonFallback {

    private final String type;

    @JsonCreator
    public NopJacksonFallback(@JsonProperty("type") String type) {
      this.type = type;
    }

    @Override
    public String type() {
      return type;
    }

    @Override
    public Operation with(TransportFactory transport) {
      return this;
    }
  }
}
