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

import com.google.auto.value.AutoValue;
import java.util.logging.Logger;

/** Encapsulates test options to be used to in running the test */
@AutoValue
public abstract class TestOptions {
  private static final Logger LOGGER = Logger.getLogger(TestOptions.class.getName());
  private static final int N_THREADS_DEFAULT = 1; // Default number of threads
  private static final int HTTP_TIMEOUT = 20; // ms
  private static final int N_ITERATIONS = 1000000;
  private static final String USAGE =
      "Usage: JettyTestClient SERVER_INSTANCE PORT BUCKET "
          + "[nThreads] [httpTimeout] [nIterations]\n"
          + "where\n"
          + "SERVER_INSTANCE Name of the GCE instance\n"
          + "PORT Port to connect on\n"
          + "bucket GCS bucket where test data is stored\n"
          + "nThreads The number of threads to execute the test with\n"
          + "httpTimeout (seconds) Tiemout for HTTP requests\n"
          + "nIterations number of iterations per thread\n";
  private HttpClientFactory factory = new OcHttpClientFactory();

  /** Use parseArgs() to creaet a TestOptions object. */
  static TestOptions create(
      String bucket, String targetURL, int nThreads, int httpTimeout, int nIterations) {
    return new AutoValue_TestOptions(bucket, targetURL, nThreads, httpTimeout, nIterations);
  }

  /**
   * Parse the command line arguments.
   *
   * <p>If there are any errors parsing the arguments then defaults will be used, except for bucket,
   * for which there is no default. The arguments are
   *
   * <p>bucket (required) GCS bucket where test data is stored
   *
   * @param args Array of command line arguments
   * @throws IllegalArgumentException If args does not conform to the USAGE description
   */
  static TestOptions parseArgs(String[] args) throws IllegalArgumentException {
    if (args.length < 3) {
      System.err.println("Only got " + args.length + " arguments");
      System.out.println(USAGE);
      throw new IllegalArgumentException();
    }
    String host = args[0];
    int port = getInt("port", args[1]);
    LOGGER.info("Setting host to " + host);
    String targetURL = "http://" + host + ":" + port + "/test";

    String bucket = args[2];
    int nThreads = N_THREADS_DEFAULT;
    if (args.length > 3) {
      nThreads = getInt("nThreads", args[3]);
    }
    int httpTimeout = HTTP_TIMEOUT;
    if (args.length > 4) {
      httpTimeout = getInt("httpTimeout", args[4]);
    }
    int nIterations = N_ITERATIONS;
    if (args.length > 5) {
      nIterations = getInt("nIterations", args[5]);
    }
    return create(bucket, targetURL, nThreads, httpTimeout, nIterations);
  }

  private static int getInt(String name, String strVal) {
    try {
      int val = Integer.parseInt(strVal);
      LOGGER.info("Setting " + name + " to " + val);
      return val;
    } catch (NumberFormatException e) {
      System.err.println("Number format error parsing " + name + ": " + e.getMessage());
      System.out.println(USAGE);
      throw new IllegalArgumentException("Number format error parsing " + name);
    }
  }

  /** @return The GCS bucket to get test data from */
  abstract String bucket();

  /** @return The target URL of the test server */
  abstract String targetURL();

  /** @return The number of threads to use in the test */
  abstract int nThreads();

  /** @return Gets the HTTP timeout, in milliseconds */
  abstract int httpTimeout();

  /** @return The number of iterations for each thread */
  abstract int nIterations();

  /** @return Factory for creating HttpClient objects */
  HttpClientFactory getHttpClientFactory() {
    return factory;
  }

  void setHttpClientFactory(HttpClientFactory factory) {
    this.factory = factory;
  }
}
