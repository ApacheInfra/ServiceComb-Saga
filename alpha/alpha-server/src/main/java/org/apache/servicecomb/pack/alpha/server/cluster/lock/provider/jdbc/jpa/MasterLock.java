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

package org.apache.servicecomb.pack.alpha.server.cluster.lock.provider.jdbc.jpa;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Date;

@Entity
@Table(name = "master_lock")
public class MasterLock {

    @Id
    private String serviceName;
    private String instanceId;
    private Date expireTime;
    private Date lockedTime;

    public MasterLock() {

    }

    public MasterLock(MasterLock masterLock) {
        this(masterLock.serviceName,
                masterLock.instanceId,
                masterLock.expireTime,
                masterLock.lockedTime);
    }

    public MasterLock(
            String serviceName,
            String instanceId,
            Date expireTime,
            Date lockedTime) {
        this.serviceName = serviceName;
        this.instanceId = instanceId;
        this.expireTime = expireTime;
        this.lockedTime = lockedTime;
    }

    public Date getExpireTime() {
        return expireTime;
    }

    public Date getLockedTime() {
        return lockedTime;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getServiceName() {
        return serviceName;
    }
}
