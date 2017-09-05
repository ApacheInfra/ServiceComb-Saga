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

package io.servicecomb.saga.core.application.interpreter;

import io.servicecomb.saga.core.SagaException;
import io.servicecomb.saga.core.SagaRequest;
import io.servicecomb.saga.core.dag.GraphCycleDetector;
import io.servicecomb.saga.core.dag.Node;
import io.servicecomb.saga.core.dag.SingleLeafDirectedAcyclicGraph;
import java.io.IOException;
import java.util.Set;
import kamon.annotation.EnableKamon;
import kamon.annotation.Segment;

@EnableKamon
public class JsonRequestInterpreter {

  private final FromJsonFormat fromJsonFormat;
  private final GraphCycleDetector<SagaRequest> detector;
  private final GraphBuilder graphBuilder = new GraphBuilder();

  public JsonRequestInterpreter(FromJsonFormat fromJsonFormat, GraphCycleDetector<SagaRequest> detector) {
    this.fromJsonFormat = fromJsonFormat;
    this.detector = detector;
  }

  @Segment(name = "interpret", category = "application", library = "kamon")
  public SingleLeafDirectedAcyclicGraph<SagaRequest> interpret(String requests) {
    try {
      SagaRequest[] sagaRequests = fromJsonFormat.fromJson(requests);

      SingleLeafDirectedAcyclicGraph<SagaRequest> graph = graphBuilder.build(sagaRequests);

      detectCycle(graph);

      return graph;
    } catch (IOException e) {
      throw new SagaException("Failed to interpret JSON " + requests, e);
    }
  }

  private void detectCycle(SingleLeafDirectedAcyclicGraph<SagaRequest> graph) {
    Set<Node<SagaRequest>> jointNodes = detector.cycleJoints(graph);

    if (!jointNodes.isEmpty()) {
      throw new SagaException("Cycle detected in the request graph at nodes " + jointNodes);
    }
  }
}
