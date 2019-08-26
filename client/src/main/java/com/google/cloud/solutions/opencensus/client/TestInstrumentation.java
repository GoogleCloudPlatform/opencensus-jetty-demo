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

import com.google.cloud.TransportOptions;
import com.google.cloud.http.HttpTransportOptions;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.cloud.storage.StorageOptions.Builder;
import io.opencensus.common.Scope;
import io.opencensus.contrib.http.util.HttpViews;
import io.opencensus.exporter.stats.stackdriver.StackdriverStatsExporter;
import io.opencensus.exporter.trace.stackdriver.StackdriverTraceConfiguration;
import io.opencensus.exporter.trace.stackdriver.StackdriverTraceExporter;
import io.opencensus.stats.Aggregation;
import io.opencensus.stats.Aggregation.Distribution;
import io.opencensus.stats.BucketBoundaries;
import io.opencensus.stats.Measure.MeasureDouble;
import io.opencensus.stats.Stats;
import io.opencensus.stats.StatsRecorder;
import io.opencensus.stats.View;
import io.opencensus.stats.View.Name;
import io.opencensus.stats.ViewManager;
import io.opencensus.tags.TagContext;
import io.opencensus.tags.TagKey;
import io.opencensus.tags.TagValue;
import io.opencensus.tags.Tagger;
import io.opencensus.tags.Tags;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.config.TraceConfig;
import io.opencensus.trace.config.TraceParams;
import io.opencensus.trace.samplers.Samplers;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Encapsulates instrumentation to be used to in running the test */
public class TestInstrumentation {
  private static final Logger LOGGER = Logger.getLogger(TestInstrumentation.class.getName());
  private static final int GCS_CONNECT_TIMEOUT = 200; // ms
  private static final int GCS_READ_TIMEOUT = 400; // ms
  private static final String SMALL_FILE = "small_file.json";
  private static final String LARGE_FILE = "large_file.json";
  private static final TagKey KEY_METHOD = TagKey.create("method");
  private static final MeasureDouble M_LATENCY_MS =
      MeasureDouble.create(
          "test_client/latency",
          "Latency in to read content from storage and send to the backend server",
          "ms");
  private static final Tagger tagger = Tags.getTagger();
  private static final StatsRecorder statsRecorder = Stats.getStatsRecorder();
  private static Storage storage;
  private static Random rand = new Random();

  /** Initializes tracing, monitoring, and storage */
  static void init() throws IOException {
    initStats();
    initTracing();
    initStorage();
  }

  /** Initializes GCS client */
  private static void initStorage() {
    Builder sBuilder = StorageOptions.newBuilder();
    HttpTransportOptions.Builder hBuilder = HttpTransportOptions.newBuilder();
    hBuilder.setConnectTimeout(GCS_CONNECT_TIMEOUT);
    hBuilder.setReadTimeout(GCS_READ_TIMEOUT);
    TransportOptions tOptions = hBuilder.build();
    sBuilder.setTransportOptions(tOptions);
    StorageOptions options = sBuilder.build();
    storage = options.getService();
  }

  // Initializes OpenCensus Stackdriver Stats exporter
  private static void initStats() throws IOException {
    // Exponential with growth factor of 1.25, rounded to nearest 1 ms.
    Aggregation latencyDist =
        Distribution.create(
            BucketBoundaries.create(
                Arrays.asList(
                    1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 9.0, 12.0, 15.0, 18.0, 23.0, 28.0, 36.0,
                    44.0, 56.0, 69.0, 87.0, 108.0, 136.0, 169.0, 212.0, 265.0, 331.0, 414.0, 517.0,
                    646.0, 808.0, 1010.0, 1262.0, 1578.0, 1972.0, 2465.0, 3081.0, 3852.0, 4815.0,
                    6019.0, 7523.0, 9404.0, 11755.0, 14694.0, 18367.0, 22959.0, 28699.0, 35873.0,
                    44842.0, 56052.0)));
    View[] views =
        new View[] {
          View.create(
              Name.create("octail/latency"),
              "Distribution of latencies",
              M_LATENCY_MS,
              latencyDist,
              Collections.unmodifiableList(Arrays.asList(KEY_METHOD)))
        };
    ViewManager vmgr = Stats.getViewManager();
    for (View view : views) {
      vmgr.registerView(view);
    }
    HttpViews.registerAllClientViews();
    StackdriverStatsExporter.createAndRegister();
  }

  // Initializes Stackdriver Trace exporter
  static void initTracing() {
    TraceConfig traceConfig = Tracing.getTraceConfig();
    TraceParams activeTraceParams = traceConfig.getActiveTraceParams();
    traceConfig.updateActiveTraceParams(
        activeTraceParams.toBuilder().setSampler(Samplers.alwaysSample()).build());
    try {
      StackdriverTraceExporter.createAndRegister(StackdriverTraceConfiguration.builder().build());
      LOGGER.info("Tracing initialized with: " + traceConfig.getActiveTraceParams().getSampler());
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "Not able to initialize tracing", e);
    }
  }

  // Record latency for a client request.
  static void recordTaggedStat(String methodValue, Double d) {
    TagContext tctx =
        tagger
            .emptyBuilder()
            .put(TestInstrumentation.KEY_METHOD, TagValue.create(methodValue))
            .build();
    try (Scope ss = tagger.withTagContext(tctx)) {
      statsRecorder.newMeasureMap().put(M_LATENCY_MS, d).record();
    }
  }

  /**
   * Gets content from GCS
   *
   * @return The contents read from the GCS object
   */
  // [START jetty_app_client_get_content]
  static byte[] getContent(String bucket) {
    BlobId blobId = null;
    int n = rand.nextInt(100);
    if (n >= 95) {
      blobId = BlobId.of(bucket, LARGE_FILE);
    } else {
      blobId = BlobId.of(bucket, SMALL_FILE);
    }
    return storage.readAllBytes(blobId);
  }
  // [END jetty_app_client_get_content]

  /**
   * Process the JSON payload returned from the server to simulate a real application in terms
   * processing time and CPU.
   *
   * @param payload The payload to process
   * @param fn The function to apply
   * @param fnName The name of the function
   */
  static void processPayload(byte[] payload, Function<Integer[], Integer> fn, String fnName) {
    String jsonString = new String(payload, StandardCharsets.UTF_8);
    try {
      JSONObject obj = new JSONObject(jsonString);
      JSONArray numArray = obj.getJSONArray("numbers");
      Integer[] num = new Integer[numArray.length()];
      for (int i = 0; i < numArray.length(); i++) {
        num[i] = numArray.optInt(i);
      }
      int result = fn.apply(num);
      LOGGER.info("Processing result " + result + " (" + fnName + ")");
    } catch (JSONException e) {
      LOGGER.log(Level.WARNING, "Exception parsing payload " + e.getMessage());
      if (payload.length < 1000) {
        LOGGER.info("jsonString: " + jsonString);
      }
    }
  }
}
