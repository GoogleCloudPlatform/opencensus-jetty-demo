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

package com.google.cloud.solutions.opencensus.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

/** Unit testing for class OCTestServlet */
public class OCTestServletTest {

  /** Test that the doGet method returns valid JSON. */
  @Test
  public void doGetValidJson() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    when(response.getWriter()).thenReturn(pw);
    OCTestServlet servlet = new OCTestServlet();
    servlet.doGet(request, response);
    String resStr = sw.getBuffer().toString().trim();
    Gson gson = new Gson();
    Object validJson = gson.fromJson(resStr, Object.class);
  }

  /** Test that the doPost method returns the same JSON string as was sent to it. */
  @Test
  public void doPostSameBack() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    when(response.getWriter()).thenReturn(pw);
    JsonArray numbers = new JsonArray();
    for (int i = 1; i <= 3; i++) {
      numbers.add(i);
    }
    JsonObject root = new JsonObject();
    root.add("numbers", numbers);
    String jsonStr = new Gson().toJson(root);
    BufferedReader r = new BufferedReader(new StringReader(jsonStr));
    when(request.getReader()).thenReturn(r);
    OCTestServlet servlet = new OCTestServlet();
    servlet.doPost(request, response);
    String resStr = sw.getBuffer().toString().trim();
    assertEquals(jsonStr, resStr);
  }
}
