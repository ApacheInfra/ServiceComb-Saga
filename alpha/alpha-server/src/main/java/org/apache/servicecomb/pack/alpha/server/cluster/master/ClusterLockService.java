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

package org.apache.servicecomb.pack.alpha.server.cluster.master;

import org.apache.servicecomb.pack.alpha.core.NodeStatus;
import org.apache.servicecomb.pack.alpha.server.cluster.master.provider.LockProvider;
import org.apache.servicecomb.pack.alpha.server.cluster.master.provider.Locker;
import org.apache.servicecomb.pack.alpha.server.cluster.master.provider.jdbc.jpa.MasterLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.invoke.MethodHandles;
import java.util.Calendar;
import java.util.Date;
import java.util.Optional;

/**
 * Cluster master preemption master service
 * default based on database master_lock table implementation
 * <p>
 * Set true to enable default value false
 * alpha.cluster.master.enabled=true
 * <p>
 * Implementation type, default jdbc
 * alpha.cluster.master.type=jdbc
 * <p>
 * Lock timeout, default value 5000 millisecond
 * alpha.cluster.master.expire=5000
 */

@Component
@ConditionalOnProperty(name = "alpha.cluster.master.enabled", havingValue = "true")
@EnableScheduling
public class ClusterLockService implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private boolean locked = Boolean.FALSE;
    private boolean lockExecuted = Boolean.FALSE;
    private boolean applicationReady = Boolean.FALSE;
    private MasterLock masterLock;
    private Optional<Locker> locker;

    @Value("[${alpha.server.host}]:${alpha.server.port}")
    private String instanceId;

    @Value("${spring.application.name:servicecomb-alpha-server}")
    private String serviceName;

    @Value("${alpha.cluster.master.expire:5000}")
    private int expire;

    @Autowired
    LockProvider lockProvider;

    @Autowired
    NodeStatus nodeStatus;

    public ClusterLockService() {
        LOG.info("Initialize cluster mode");
    }

    public boolean isMasterNode() {
        return locked;
    }

    public boolean isLockExecuted() {
        return lockExecuted;
    }

    public MasterLock getMasterLock() {
        if (this.masterLock == null) {
            this.masterLock = new MasterLock();
            this.masterLock.setServiceName(serviceName);
            this.masterLock.setInstanceId(instanceId);
        }
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.add(Calendar.MILLISECOND, expire);
        this.masterLock.setExpireTime(cal.getTime());
        this.masterLock.setLockedTime(new Date());
        return this.masterLock;
    }

    public void setMasterLock(MasterLock masterLock) {
        this.masterLock = masterLock;
    }

    @Scheduled(cron = "0/1 * * * * ?")
    public void masterCheck() {
        if (applicationReady) {
            this.locker = lockProvider.lock(this.getMasterLock());
            if (this.locker.isPresent()) {
                if (!this.locked) {
                    this.locked = true;
                    nodeStatus.setTypeEnum(NodeStatus.TypeEnum.MASTER);
                    LOG.info("Master Node");
                }
                //Keep locked
            } else {
                if (this.locked || !lockExecuted) {
                    locked = false;
                    nodeStatus.setTypeEnum(NodeStatus.TypeEnum.SLAVE);
                    LOG.info("Slave Node");
                }
            }
            lockExecuted = Boolean.TRUE;
        }
    }

    public void unLock(){
        if(this.locker.isPresent()){
            this.locker.get().unlock();
        }
        lockExecuted = false;
        locked = false;
        nodeStatus.setTypeEnum(NodeStatus.TypeEnum.SLAVE);
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent applicationReadyEvent) {
        this.applicationReady = Boolean.TRUE;
    }
}
