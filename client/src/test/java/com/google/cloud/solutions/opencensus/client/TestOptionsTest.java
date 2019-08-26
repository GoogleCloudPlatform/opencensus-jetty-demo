/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.solutions.opencensus.client;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Unit tests for the TestOptions class */
public class TestOptionsTest {
  private static final String HOST = "jetty_server";
  private static final String PORT = "1234";
  private static final String BUCKET = "mybucket";

  @Test
  public void parseArgsEmpty() {
    String[] args = new String[0];
    assertThrows(IllegalArgumentException.class, () -> TestOptions.parseArgs(args));
  }

  @Test
  public void parseArgsValidMinimal() {
    String[] args = {HOST, PORT, BUCKET};
    TestOptions options = TestOptions.parseArgs(args);
    assertEquals("targetURL wrong", "http://jetty_server:1234/test", options.targetURL());
    assertEquals("bucket wrong", BUCKET, options.bucket());
  }

  @Test
  public void parseArgsValidFullSet() {
    int nThreads = 2;
    int httpTimeout = 60;
    int nIterations = 5;
    String[] args = {HOST, PORT, BUCKET, "" + nThreads, "" + httpTimeout, "" + nIterations};
    TestOptions options = TestOptions.parseArgs(args);
    assertEquals("targetURL wrong", "http://jetty_server:1234/test", options.targetURL());
    assertEquals("bucket wrong", BUCKET, options.bucket());
    assertEquals("nThreads wrong", nThreads, options.nThreads());
    assertEquals("httpTimeout wrong", httpTimeout, options.httpTimeout());
    assertEquals("nIterations wrong", nIterations, options.nIterations());
  }

  @Test
  public void parseArgsInvalid() {
    String nThreads = "5.5"; // Not valid, must be an integer
    String httpTimeout = "60";
    String[] args = {HOST, PORT, BUCKET, nThreads, httpTimeout};
    assertThrows(IllegalArgumentException.class, () -> TestOptions.parseArgs(args));
  }
}
