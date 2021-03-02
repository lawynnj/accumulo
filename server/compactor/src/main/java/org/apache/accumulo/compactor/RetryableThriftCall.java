/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.accumulo.compactor;

import org.apache.accumulo.fate.util.UtilWaitThread;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetryableThriftCall<T> {

  private static final Logger LOG = LoggerFactory.getLogger(RetryableThriftCall.class);

  private final long start;
  private final long maxWaitTime;
  private int maxNumRetries;
  private final RetryableThriftFunction<T> function;
  private final boolean retryForever;

  /**
   * RetryableThriftCall constructor
   *
   * @param start
   *          initial wait time
   * @param maxWaitTime
   *          max wait time
   * @param maxNumRetries
   *          number of times to retry, 0 to retry forever
   * @param function
   *          function to execute
   */
  public RetryableThriftCall(long start, long maxWaitTime, int maxNumRetries,
      RetryableThriftFunction<T> function) {
    this.start = start;
    this.maxWaitTime = maxWaitTime;
    this.maxNumRetries = maxNumRetries;
    this.function = function;
    this.retryForever = (maxNumRetries == 0);
  }

  /**
   * Attempts to call the function, waiting and retrying when TException is thrown. Wait time is
   * initially set to the start time and doubled each time, up to the maximum wait time. If
   * maxNumRetries is 0, then this will retry forever. If maxNumRetries is non-zero, then a
   * RuntimeException is thrown when it has exceeded he maxNumRetries parameter.
   *
   * @return T
   * @throws RuntimeException
   *           when maximum number of retries has been exceeded
   */
  public T run() {
    long waitTime = start;
    int numRetries = 0;
    T result = null;
    do {
      try {
        result = function.execute();
      } catch (TException e) {
        LOG.error("Error in Thrift function talking to Coordinator, retrying in {}ms", waitTime);
        if (!retryForever) {
          numRetries++;
          if (numRetries > maxNumRetries) {
            throw new RuntimeException(
                "Maximum number of retries (" + this.maxNumRetries + ") attempted.");
          }
        }
      }
      UtilWaitThread.sleep(waitTime);
      if (waitTime != maxWaitTime) {
        waitTime = Math.max(waitTime * 2, maxWaitTime);
      }
    } while (null == result);
    return result;
  }

}