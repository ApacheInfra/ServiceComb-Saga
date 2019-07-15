/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.servicecomb.pack.alpha.fsm.channel;

import org.apache.servicecomb.pack.alpha.fsm.event.base.BaseEvent;
import org.apache.servicecomb.pack.alpha.fsm.metrics.MetricsService;
import org.apache.servicecomb.pack.alpha.fsm.sink.ActorEventSink;

public abstract class AbstractActorEventChannel implements ActorEventChannel {

  protected final MetricsService metricsService;
  protected final ActorEventSink actorEventSink;

  public abstract void sendTo(BaseEvent event);

  public AbstractActorEventChannel(
      ActorEventSink actorEventSink,
      MetricsService metricsService) {
    this.actorEventSink = actorEventSink;
    this.metricsService = metricsService;
  }

  public void send(BaseEvent event) {
    long begin = System.currentTimeMillis();
    metricsService.metrics().doEventReceived();
    try {
      this.sendTo(event);
      metricsService.metrics().doEventAccepted();
    } catch (Exception ex) {
      metricsService.metrics().doEventRejected();
    }
    long end = System.currentTimeMillis();
    metricsService.metrics().doEventAvgTime(end - begin);
  }

}
