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

package io.servicecomb.saga.discovery.service.center;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import io.servicecomb.provider.springmvc.reference.RestTemplateBuilder;
import io.servicecomb.saga.transports.HttpClientTransportConfig;
import io.servicecomb.saga.transports.RestTransport;
import io.servicecomb.saga.transports.resttemplate.RestTemplateTransport;
import io.servicecomb.springboot.starter.provider.EnableServiceComb;

@EnableServiceComb
@Profile("servicecomb")
@Configuration
@AutoConfigureBefore(HttpClientTransportConfig.class)
public class ServiceCenterDiscoveryConfig {

  static final String PROTOCOL = "cse://";

  @Bean
  RestTransport restTransport() {
    return new RestTemplateTransport(RestTemplateBuilder.create(), PROTOCOL);
  }
}
