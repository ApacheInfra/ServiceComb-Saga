/*
 *   Copyright 2017 Huawei Technologies Co., Ltd
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package io.servicecomb.saga.spring;

import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.servicecomb.saga.core.SagaDefinition;
import io.servicecomb.saga.core.SagaEndedEvent;
import io.servicecomb.saga.core.SagaRequest;
import io.servicecomb.saga.core.SagaStartedEvent;
import io.servicecomb.saga.core.TransactionAbortedEvent;
import io.servicecomb.saga.core.TransactionEndedEvent;
import io.servicecomb.saga.core.application.GraphBuilder;
import io.servicecomb.saga.core.application.interpreter.FromJsonFormat;
import io.servicecomb.saga.core.dag.GraphCycleDetectorImpl;
import io.servicecomb.saga.core.dag.Node;
import io.servicecomb.saga.core.dag.SingleLeafDirectedAcyclicGraph;
import io.servicecomb.saga.spring.SagaController.SagaExecution;
import io.servicecomb.saga.spring.SagaController.SagaExecutionDetail;
import io.servicecomb.saga.spring.SagaController.SagaExecutionQueryResult;
import io.servicecomb.swagger.invocation.exception.InvocationException;

@Service
public class SagaExecutionQueryService {
  private final SagaEventRepo repo;
  private final FromJsonFormat<SagaDefinition> fromJsonFormat;

  private final ObjectMapper mapper = new ObjectMapper();
  private final GraphBuilder graphBuilder = new GraphBuilder(new GraphCycleDetectorImpl<>());

  @Autowired
  public SagaExecutionQueryService(SagaEventRepo repo, FromJsonFormat<SagaDefinition> fromJsonFormat) {
    this.repo = repo;
    this.fromJsonFormat = fromJsonFormat;
  }

  public SagaExecutionQueryResult querySagaExecution(String pageIndex, String pageSize,
      String startTime, String endTime) {
    List<SagaExecution> requests = new ArrayList<>();
    Page<SagaEventEntity> startEvents = repo.findByTypeAndCreationTimeBetweenOrderByIdDesc(
        SagaStartedEvent.class.getSimpleName(),
        new Date(Long.parseLong(startTime)), new Date(Long.parseLong(endTime)),
        new PageRequest(Integer.parseInt(pageIndex), Integer.parseInt(pageSize)));
    for (SagaEventEntity event : startEvents) {
      SagaEventEntity endEvent = repo
          .findFirstByTypeAndSagaId(SagaEndedEvent.class.getSimpleName(), event.sagaId());
      SagaEventEntity abortedEvent = repo
          .findFirstByTypeAndSagaId(TransactionAbortedEvent.class.getSimpleName(), event.sagaId());

      requests.add(new SagaExecution(
          event.id(),
          event.sagaId(),
          event.creationTime(),
          endEvent == null ? 0 : endEvent.creationTime(),
          endEvent == null ? "Running" : abortedEvent == null ? "OK" : "Failed"));
    }

    return new SagaExecutionQueryResult(Integer.parseInt(pageIndex), Integer.parseInt(pageSize),
        startEvents.getTotalPages(), requests);
  }

  public SagaExecutionDetail querySagaExecutionDetail(String sagaId) {
    SagaEventEntity[] entities = repo.findBySagaId(sagaId).toArray(new SagaEventEntity[0]);
    Optional<SagaEventEntity> sagaStartEvent = Arrays.stream(entities)
        .filter(entity -> SagaStartedEvent.class.getSimpleName().equals(entity.type())).findFirst();
    Map<String, List<String>> router = new HashMap<>();
    Map<String, String> status = new HashMap<>();
    Map<String, String> error = new HashMap<>();
    if (sagaStartEvent.isPresent()) {
      SagaDefinition definition = fromJsonFormat.fromJson(sagaStartEvent.get().contentJson());
      SingleLeafDirectedAcyclicGraph<SagaRequest> graph = graphBuilder
          .build(definition.requests());
      loopLoadGraphNodes(router, graph.root());

      Collection<SagaEventEntity> transactionAbortEvents = Arrays.stream(entities)
          .filter(entity -> TransactionAbortedEvent.class.getSimpleName().equals(entity.type())).collect(
              Collectors.toList());
      for (SagaEventEntity transactionAbortEvent : transactionAbortEvents) {
        try {
          JsonNode root = mapper.readTree(transactionAbortEvent.contentJson());
          String id = root.at("/request/id").asText();
          status.put(id, "Failed");
          error.put(id, root.at("/response/body").asText());
        } catch (IOException ex) {
          throw new InvocationException(INTERNAL_SERVER_ERROR, "illegal json content");
        }
      }

      Collection<SagaEventEntity> transactionEndEvents = Arrays.stream(entities)
          .filter(entity -> TransactionEndedEvent.class.getSimpleName().equals(entity.type())).collect(
              Collectors.toList());
      for (SagaEventEntity transactionEndEvent : transactionEndEvents) {
        try {
          JsonNode root = mapper.readTree(transactionEndEvent.contentJson());
          status.put(root.at("/request/id").asText(), "OK");
        } catch (IOException ex) {
          throw new InvocationException(INTERNAL_SERVER_ERROR, "illegal json content");
        }
      }
    }
    return new SagaExecutionDetail(router, status, error);
  }

  private void loopLoadGraphNodes(Map<String, List<String>> router, Node<SagaRequest> node) {
    if (isNodeValid(node)) {
      List<String> point = router.computeIfAbsent(node.value().id(), key -> new ArrayList<>());
      for (Node<SagaRequest> child : node.children()) {
        point.add(child.value().id());
        loopLoadGraphNodes(router, child);
      }
    }
  }

  private boolean isNodeValid(Node<SagaRequest> node) {
    return !node.children().isEmpty();
  }
}
