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

import static io.servicecomb.saga.core.SagaRequest.TYPE_REST;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.servicecomb.saga.core.SagaRequest;
import io.servicecomb.saga.transports.TransportFactory;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    visible = true,
    property = "type")
@JsonSubTypes({
    @Type(value = JsonRestSagaRequest.class, name = TYPE_REST)
})
public interface JsonSagaRequest extends SagaRequest {

  JsonSagaRequest with(TransportFactory transportFactory);
}
