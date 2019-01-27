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

package org.apache.servicecomb.pack.alpha.server.cluster.lock.provider;


import org.apache.servicecomb.pack.alpha.server.cluster.lock.LockConfig;

import java.util.Optional;

public abstract class AbstractLockProvider implements LockProvider {

    private final LockProviderPersistence lockProviderPersistence;
    private boolean lockInitialization = Boolean.FALSE;

    protected AbstractLockProvider(LockProviderPersistence lockProviderPersistence) {
        this.lockProviderPersistence = lockProviderPersistence;
    }

    @Override
    public Optional<org.apache.servicecomb.pack.alpha.server.cluster.lock.provider.Locked> lock(LockConfig lockConfig) {
        boolean lockObtained = doLock(lockConfig);
        if (lockObtained) {
            //获得锁
            return Optional.of(new Locked(lockConfig, lockProviderPersistence));
        } else {
            return Optional.empty();
        }
    }

    protected boolean doLock(LockConfig lockConfig) {
        if (!lockInitialization) {
            if (lockProviderPersistence.initLock(lockConfig)) {
                lockInitialization = Boolean.TRUE;
                return true;
            }
            lockInitialization = Boolean.TRUE;
        }
        return lockProviderPersistence.updateLock(lockConfig);
    }

    private static class Locked implements org.apache.servicecomb.pack.alpha.server.cluster.lock.provider.Locked {
        private final LockConfig lockConfig;
        private final LockProviderPersistence lockProviderPersistence;

        Locked(LockConfig lockConfig, LockProviderPersistence lockProviderPersistence) {
            this.lockConfig = lockConfig;
            this.lockProviderPersistence = lockProviderPersistence;
        }

        @Override
        public void unlock() {
            lockProviderPersistence.unLock(lockConfig);
        }
    }

}
