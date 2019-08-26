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

import com.google.api.client.util.ExponentialBackOff;
import io.opencensus.common.Scope;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang3.time.StopWatch;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpRequest;
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;

/**
 * Test application that shows use of OpenCensus to instrument a client app.
 *
 * <p>Usage: java io.opencensus.examples.http.jetty.client.JettyTestClient \ host port bucket
 * num_threads
 */
public class JettyTestClient {
  private static final Logger LOGGER = Logger.getLogger(JettyTestClient.class.getName());
  private static final int MAX_RETRIES = 6;
  private TestOptions testOptions;

  // Use the command line as an entry point
  private JettyTestClient(TestOptions testOptions) {
    this.testOptions = testOptions;
  }

  /**
   * Runs the test for a given instance of the class by sending a series of HTTP requests to the
   * given target URL. The test data returned by the HTTP requests is processed to simulate a
   * realistic web application.
   */
  private void runTest() {
    HttpClientFactory factory = testOptions.getHttpClientFactory();
    HttpClient httpClient = factory.getHttpClient();
    try {
      httpClient.start();
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Error starting HttpClient " + e.getMessage() + " exiting");
      System.exit(1);
    }

    String targetURL = testOptions.targetURL();
    LOGGER.info("Sending requests to " + targetURL);
    Function<Integer[], Integer> count = (numbers) -> numbers.length;
    Function<Integer[], Integer> sum =
        (numbers) -> Arrays.stream(numbers).mapToInt(Integer::intValue).sum();
    for (int i = 0; i < testOptions.nIterations(); i++) {
      try {
        prepareSendProcess(httpClient, HttpMethod.GET, count, "count");
        prepareSendProcess(httpClient, HttpMethod.POST, count, "count");
        prepareSendProcess(httpClient, HttpMethod.GET, sum, "sum");
        prepareSendProcess(httpClient, HttpMethod.POST, sum, "sum");
        Thread.sleep(100);
      } catch (Exception e) {
        LOGGER.log(Level.WARNING, "Error: " + e.getMessage(), e);
      }
    }
    try {
      httpClient.stop();
    } catch (Exception e) {
      LOGGER.log(Level.WARNING, "Error stopping HttpClient " + e.getMessage());
    }
  }

  /**
   * Prepare, send, and process data from the microservice.
   *
   * <p>Retrieves data from storage and sends a single request to the given targetURL with the
   * supplied HTTP method. Sends the returned payload to a downstream processing function.
   */
  // [START jetty_app_client_prepare_send]
  private void prepareSendProcess(
      HttpClient httpClient,
      HttpMethod method,
      Function<Integer[], Integer> downStreamFn,
      String fnName)
      throws InterruptedException {
    Tracer tracer = Tracing.getTracer();
    try (Scope scope = tracer.spanBuilder("main").startScopedSpan()) {
      StopWatch s = StopWatch.createStarted();
      byte[] content = new byte[0];
      if (method == HttpMethod.POST) {
        content = TestInstrumentation.getContent(testOptions.bucket());
      }
      byte[] payload = sendWithRetry(httpClient, method, content);
      TestInstrumentation.processPayload(payload, downStreamFn, fnName);
      TestInstrumentation.recordTaggedStat(
          method.toString(), s.getTime(TimeUnit.NANOSECONDS) / 1.0e6);
    }
  }
  // [END jetty_app_client_prepare_send]

  // Sends a HTTP request to the target, returning the payload.
  // [START jetty_app_client_send_request]
  private byte[] sendRequest(HttpClient httpClient, HttpMethod method, byte[] content)
      throws InterruptedException, TimeoutException, ExecutionException, RetryableException {
    String targetURL = testOptions.targetURL();
    HttpRequest request = (HttpRequest) httpClient.newRequest(targetURL).method(method);
    if (request == null) {
      throw new RetryableException("Request is null");
    }
    if (method == HttpMethod.POST) {
      ContentProvider contentProvider =
          new StringContentProvider(new String(content, StandardCharsets.UTF_8));
      request.content(contentProvider, "application/json");
    }
    request.timeout(testOptions.httpTimeout(), TimeUnit.MILLISECONDS);
    ContentResponse response = request.send();
    int status = response.getStatus();
    LOGGER.info("Response status: " + status + ", " + method);
    if (HttpStatus.isSuccess(status)) {
      byte[] payload = response.getContent();
      LOGGER.info("Response payload: " + payload.length + " bytes");
      return payload;
    } else if (HttpStatus.isServerError(status)) {
      throw new RetryableException(response.getReason());
    }
    return new byte[0];
  }
  // [END jetty_app_client_send_request]

  // Sends a HTTP request to the target, returning the payload.
  // [START jetty_app_client_send_retry]
  private byte[] sendWithRetry(HttpClient httpClient, HttpMethod method, byte[] content)
      throws InterruptedException {
    ExponentialBackOff backoff = new ExponentialBackOff.Builder()
      .setInitialIntervalMillis(500)
      .setMaxElapsedTimeMillis(5*60*1000)
      .setMultiplier(2.0)
      .setRandomizationFactor(0.5)
      .build();
    for (int i = 0; i < MAX_RETRIES; i++) {
      try {
        return sendRequest(httpClient, method, content);
      } catch (RetryableException e) {
        LOGGER.log(Level.WARNING, "RetryableException attempt: " + (i + 1) + " " + e.getMessage());
      } catch (InterruptedException e) {
        LOGGER.log(
            Level.WARNING, "InterruptedException attempt: " + (i + 1) + " " + e.getMessage());
      } catch (TimeoutException e) {
        LOGGER.log(Level.WARNING, "TimeoutException attempt: " + (i + 1) + " " + e.getMessage());
      } catch (ExecutionException e) {
        LOGGER.log(Level.WARNING, "ExecutionException attempt: " + (i + 1) + " " + e.getMessage());
      }
      try {
        Thread.sleep(backoff.nextBackOffMillis());
      } catch(IOException e) {
        throw new RuntimeException("MaxElapsedTime exceeded");
      }
    }
    throw new RuntimeException("Max retries exceeded");
  }
  // [END jetty_app_client_send_retry]

  // Creates a client to send a stream of requests in its own thread.
  private static Callable<Void> makeCallable(TestOptions testOptions) {
    return () -> {
      String threadName = Thread.currentThread().getName();
      LOGGER.info("Starting thread " + threadName);
      JettyTestClient client = new JettyTestClient(testOptions);
      client.runTest();
      return null;
    };
  }

  // Starts a set of threads each sending a series of requests.
  static void startThreads(TestOptions testOptions) {
    List<Callable<Void>> callableList = new ArrayList<Callable<Void>>();
    for (int i = 0; i < testOptions.nThreads(); i++) {
      callableList.add(makeCallable(testOptions));
    }
    ExecutorService pool = Executors.newFixedThreadPool(testOptions.nThreads());
    try {
      pool.invokeAll(callableList, 10000, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      LOGGER.log(Level.WARNING, "Error running threads " + e.getMessage(), e);
    }
  }

  // Marks retryable HTTP requests
  private static class RetryableException extends Exception {

    RetryableException(String message) {
      super(message);
    }
  }

  /** Entry point for the program on the command line */
  public static void main(String[] args) {
    try {
      TestOptions testOptions = TestOptions.parseArgs(args);
      TestInstrumentation.init();
      startThreads(testOptions);
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "Error initializing OpenCensus " + e.getMessage(), e);
      System.exit(1);
    } catch (IllegalArgumentException e) {
      LOGGER.log(Level.WARNING, "IllegalArgumentException: " + e.getMessage(), e);
      System.exit(1);
    }
  }
}
