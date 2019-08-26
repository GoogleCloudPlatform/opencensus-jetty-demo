/*
Copyright 2019 Google LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package com.google.cloud.solutions.opencensus.server;

import static java.util.stream.Collectors.joining;

import com.google.cloud.MonitoredResource;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.cloud.logging.LoggingHandler;
import io.opencensus.contrib.http.util.HttpViews;
import io.opencensus.exporter.stats.stackdriver.StackdriverStatsExporter;
import io.opencensus.exporter.trace.stackdriver.StackdriverTraceConfiguration;
import io.opencensus.exporter.trace.stackdriver.StackdriverTraceExporter;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.config.TraceConfig;
import io.opencensus.trace.samplers.Samplers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Test application that shows how to instrument jetty server. */
public class OCTestServlet extends HttpServlet {
  private static final Logger LOGGER = LoggerFactory.getLogger(OCTestServlet.class);
  private static int NUM_COUNT = 1000;
  private static final long serialVersionUID = 1L;

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    response.setContentType("application/json");
    LOGGER.info("doGet");
    PrintWriter pout = response.getWriter();
    pout.print(generateJSON());
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    // Read from request
    String data = request.getReader().lines().collect(joining(System.lineSeparator()));
    LOGGER.info("doPost data length: " + data.length());
    // Send it back
    response.setContentType("application/json");
    PrintWriter pout = response.getWriter();
    pout.print(data);
  }

  // Generate sample JSON daata
  private String generateJSON() {
    JsonArray numbers = new JsonArray();
    for (int i = 1; i <= NUM_COUNT - 1; i++) {
      numbers.add(i);
    }
    JsonObject root = new JsonObject();
    root.add("numbers", numbers);
    return new Gson().toJson(root);
  }

  @Override
  public void init() throws ServletException {
    try {
      initStatsExporter();
      initTracing();
      LOGGER.info("init tracing and stats initialized");
    } catch (IOException e) {
      LOGGER.error( "Could not initialize Stackdriver exporter", e );
    }
  }

  // Register the views and Stackdriver exporter.
  private static void initStatsExporter() throws IOException {
    HttpViews.registerAllServerViews();
    StackdriverStatsExporter.createAndRegister();
  }

  private static void initTracing() {
    TraceConfig traceConfig = Tracing.getTraceConfig();
    traceConfig.updateActiveTraceParams(
        traceConfig.getActiveTraceParams().toBuilder().setSampler(Samplers.alwaysSample()).build());
    try {
      StackdriverTraceExporter.createAndRegister(StackdriverTraceConfiguration.builder().build());
    } catch (IOException e) {
      LOGGER.error( "Could not initialize tracing", e );
    }
  }
}
