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

package io.servicecomb.saga.omega.transaction;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.servicecomb.saga.omega.context.OmegaContext;
import io.servicecomb.saga.omega.transaction.annotations.Compensable;

@Aspect
public class TransactionAspect {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final PreTransactionInterceptor preTransactionInterceptor;
  private final PostTransactionInterceptor postTransactionInterceptor;
  private final OmegaContext context;

  public TransactionAspect(MessageSender sender, OmegaContext context) {
    this.context = context;
    this.preTransactionInterceptor = new PreTransactionInterceptor(sender);
    this.postTransactionInterceptor = new PostTransactionInterceptor(sender);
  }

  @Around("execution(@io.servicecomb.saga.omega.transaction.annotations.Compensable * *(..)) && @annotation(compensable)")
  Object advise(ProceedingJoinPoint joinPoint, Compensable compensable) throws Throwable {
    Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
    LOG.debug("Intercepting compensable method {} with context {}", method.toString(), context);

    preIntercept(joinPoint);
    Object result = joinPoint.proceed();
    postIntercept();

    return result;
  }

  private void preIntercept(ProceedingJoinPoint joinPoint) {
    preTransactionInterceptor.intercept(
        context.globalTxId(),
        context.localTxId(),
        context.parentTxId(),
        joinPoint.getArgs());
  }

  private void postIntercept() {
    postTransactionInterceptor.intercept(
        context.globalTxId(),
        context.localTxId(),
        context.parentTxId());
  }
}
