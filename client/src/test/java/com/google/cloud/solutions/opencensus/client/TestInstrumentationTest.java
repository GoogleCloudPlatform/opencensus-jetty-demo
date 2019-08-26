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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.opencensus.exporter.stats.stackdriver.StackdriverStatsExporter;
import io.opencensus.stats.Stats;
import io.opencensus.stats.View;
import io.opencensus.stats.ViewManager;
import io.opencensus.trace.Sampler;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.config.TraceConfig;
import io.opencensus.trace.samplers.Samplers;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;

/** Unit tests for the TestInstrumentation class */
public class TestInstrumentationTest {

  @BeforeAll
  static void initAll() {
    StackdriverStatsExporter.unregister();
  }

  @Test
  public void initCheck() throws Exception {
    TestInstrumentation instr = new TestInstrumentation();
    instr.init();
    ViewManager vmgr = Stats.getViewManager();
    Set<View> views = vmgr.getAllExportedViews();
    Set<String> names = new HashSet<String>();
    for (View v : views) {
      names.add(v.getName().asString());
    }
    System.out.println("Names: " + names);
    assertTrue(names.contains("octail/latency"));

    TraceConfig traceConfig = Tracing.getTraceConfig();
    Sampler sampler = traceConfig.getActiveTraceParams().getSampler();
    assertEquals(Samplers.alwaysSample(), sampler);
  }

  @AfterAll
  static void tearDownAll() {
    StackdriverStatsExporter.unregister();
  }

}
