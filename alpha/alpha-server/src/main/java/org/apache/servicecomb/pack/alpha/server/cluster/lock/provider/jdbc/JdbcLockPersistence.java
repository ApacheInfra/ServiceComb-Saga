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

package org.apache.servicecomb.pack.alpha.server.cluster.lock.provider.jdbc;

import org.apache.servicecomb.pack.alpha.server.cluster.lock.LockConfig;
import org.apache.servicecomb.pack.alpha.server.cluster.lock.provider.LockProviderPersistence;
import org.apache.servicecomb.pack.alpha.server.cluster.lock.provider.jdbc.jpa.MasterLock;
import org.apache.servicecomb.pack.alpha.server.cluster.lock.provider.jdbc.jpa.MasterLockRepository;

import java.util.Date;

class JdbcLockPersistence implements LockProviderPersistence {

    private final MasterLockRepository masterLockRepository;

    JdbcLockPersistence(MasterLockRepository masterLockRepository) {
        this.masterLockRepository = masterLockRepository;
    }

    public boolean initLock(LockConfig lockConfig) {
        MasterLock masterLock = new MasterLock(lockConfig.getServiceName(),
                lockConfig.getInstanceId(),
                lockConfig.getLockExpireTime(),
                new Date());
        return this.masterLockRepository.initLock(masterLock);
    }

    public boolean updateLock(LockConfig lockConfig) {
        MasterLock masterLock = new MasterLock(lockConfig.getServiceName(),
                lockConfig.getInstanceId(),
                lockConfig.getLockExpireTime(),
                new Date());
        return this.masterLockRepository.updateLock(masterLock);
    }

    public void unLock(LockConfig lockConfig) {
        this.masterLockRepository.unLock(lockConfig.getServiceName(),
                lockConfig.getLockExpireTime());
    }

}