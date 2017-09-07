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

package io.servicecomb.saga.spring;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static io.servicecomb.saga.core.NoOpSagaRequest.SAGA_START_REQUEST;
import static java.util.Collections.singletonMap;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.servicecomb.saga.core.CompensationEndedEvent;
import io.servicecomb.saga.core.CompensationStartedEvent;
import io.servicecomb.saga.core.PersistentStore;
import io.servicecomb.saga.core.SagaEndedEvent;
import io.servicecomb.saga.core.SagaRequest;
import io.servicecomb.saga.core.SagaRequestImpl;
import io.servicecomb.saga.core.SagaResponse;
import io.servicecomb.saga.core.SagaStartedEvent;
import io.servicecomb.saga.core.SuccessfulSagaResponse;
import io.servicecomb.saga.core.ToJsonFormat;
import io.servicecomb.saga.core.TransactionAbortedEvent;
import io.servicecomb.saga.core.TransactionEndedEvent;
import io.servicecomb.saga.core.TransactionFailedException;
import io.servicecomb.saga.core.TransactionStartedEvent;
import io.servicecomb.saga.format.JacksonFallback;
import io.servicecomb.saga.format.JacksonRestCompensation;
import io.servicecomb.saga.format.JacksonRestTransaction;
import io.servicecomb.saga.format.SagaEventFormat;
import io.servicecomb.saga.spring.SagaRecoveryTest.EventPopulatingConfig;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.junit4.SpringRunner;
import wiremock.org.apache.http.HttpStatus;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {SagaSpringApplication.class, EventPopulatingConfig.class})
public class SagaRecoveryTest {

  @ClassRule
  public static final WireMockRule wireMockRule = new WireMockRule(8090);

  private static String request(final String name) {
    return "  {\n"
        + "    \"id\": \"request-" + name + "\",\n"
        + "    \"type\": \"rest\",\n"
        + "    \"serviceName\": \"localhost:8090\",\n"
        + "    \"transaction\": {\n"
        + "      \"method\": \"post\",\n"
        + "      \"path\": \"/rest/" + name + "\",\n"
        + "      \"params\": {\n"
        + "        \"form\": {\n"
        + "          \"foo\": \"" + name + "\"\n"
        + "        }\n"
        + "      }\n"
        + "    },\n"
        + "    \"compensation\": {\n"
        + "      \"method\": \"delete\",\n"
        + "      \"path\": \"/rest/" + name + "\",\n"
        + "      \"params\": {\n"
        + "        \"query\": {\n"
        + "          \"bar\": \"" + name + "\"\n"
        + "        }\n"
        + "      }\n"
        + "    }\n"
        + "  }\n";
  }

  private static final String requestY = "[\n"
      + request("yyy1")
      + ","
      + request("yyy2")
      + ","
      + request("yyy3")
      + "]\n";

  private static final String sagaY = "{\"policy\": \"ForwardRecovery\",\"requests\": " + requestY + "}";

  @BeforeClass
  public static void setUp() throws Exception {
    stubFor(delete(urlPathEqualTo("/rest/yyy1"))
        .withQueryParam("bar", containing("yyy1"))
        .willReturn(
            aResponse()
                .withStatus(HttpStatus.SC_OK)
                .withBody("success")));
  }

  @Test
  public void recoverIncompleteSagasFromSagaLog() throws Exception {
    verify(exactly(0), postRequestedFor(urlPathEqualTo("/rest/xxx")));

    verify(exactly(0), postRequestedFor(urlPathEqualTo("/rest/yyy1")));
    verify(exactly(1), deleteRequestedFor(urlPathEqualTo("/rest/yyy1")));

    verify(exactly(0), postRequestedFor(urlPathEqualTo("/rest/yyy2")));
    verify(exactly(0), deleteRequestedFor(urlPathEqualTo("/rest/yyy2")));

    verify(exactly(0), postRequestedFor(urlPathEqualTo("/rest/yyy3")));
    verify(exactly(0), deleteRequestedFor(urlPathEqualTo("/rest/yyy3")));
  }

  @Configuration
  static class EventPopulatingConfig {

    private static final String DONT_CARE = "{}";

    private final SagaRequest request1 = sagaRequest("yyy1");
    private final SagaRequest request2 = sagaRequest("yyy2");
    private final SagaRequest request3 = sagaRequest("yyy3");

    private final SagaResponse response1 = new SuccessfulSagaResponse(200, "succeeded, yyy1");
    private final SagaResponse response2 = new SuccessfulSagaResponse(200, "succeeded, yyy2");

    @Primary
    @Bean
    PersistentStore persistentStore(SagaEventRepo repo, ToJsonFormat toJsonFormat, SagaEventFormat sagaEventFormat) {
      repo.save(new SagaEventEntity("xxx", SagaStartedEvent.class.getSimpleName(), DONT_CARE));
      repo.save(new SagaEventEntity("xxx", TransactionStartedEvent.class.getSimpleName(), DONT_CARE));
      repo.save(new SagaEventEntity("xxx", TransactionEndedEvent.class.getSimpleName(), DONT_CARE));
      repo.save(new SagaEventEntity("xxx", SagaEndedEvent.class.getSimpleName(), DONT_CARE));

      PersistentStore store = new JpaPersistentStore(repo, toJsonFormat, sagaEventFormat);

      store.offer(new SagaStartedEvent("yyy", sagaY, SAGA_START_REQUEST));
      store.offer(new TransactionStartedEvent("yyy", request1));
      store.offer(new TransactionEndedEvent("yyy", request1, response1));

      store.offer(new TransactionStartedEvent("yyy", request2));
      store.offer(new TransactionEndedEvent("yyy", request2, response2));

      store.offer(new TransactionStartedEvent("yyy", request3));
      store.offer(new TransactionAbortedEvent("yyy", request3, new TransactionFailedException("oops")));

      store.offer(new CompensationStartedEvent("yyy", request2));
      store.offer(new CompensationEndedEvent("yyy", request2, response2));

      return store;
    }

    private SagaRequestImpl sagaRequest(final String name) {
      return new SagaRequestImpl(
          "request-" + name,
          "localhost:8080",
          "rest",
          new JacksonRestTransaction("/rest/" + name, "post", singletonMap("query", singletonMap("foo", name))),
          new JacksonRestCompensation("rest/" + name, "delete", singletonMap("query", singletonMap("bar", name))),
          JacksonFallback.NOP_TRANSPORT_AWARE_FALLBACK);
    }
  }
}
