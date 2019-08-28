# OpenCensus Jetty Integration Example with Stackdriver
This example demonstrates distributed tracing and metrics collection for a HTTP
client and server using the OpenCensus Java libraries with the Stackdriver
exporter running on Google Cloud Platform (GCP). The HTTP integration is based
on the
[OpenCensus Jetty integration](https://opencensus.io/guides/http/java/jetty/)
guide. There are two parts to the application: server and client. They will each
be run on separate Compute Engine virtual machines. The client will send a
continuous stream of HTTP requests to the server.  The code here is the basis
for the solution 
[Identifying causes of app latency with Stackdriver and OpenCensus](https://cloud.google.com/solutions/identifying-causes-of-app-latency-with-stackdriver-and-opencensus).

A common cause of high latency is large payload. With each HTTP request the
application will send either a small or a large payload with a 5% probability
of sending a large payload. The client sends both GET and POST requests with
the POST requests sending the same data back to the client.

The application also performs some downstream processing of the payload to
simulate 'business logic' in a typical application. These application
characteristics simulate the distribution of latency in a real application and
demonstrates tools for discovering causes of tail latency. To understand if
something specific to the application code path is responsible for latency
you may want to combine both trace and log data. The 
[OpenCensus Stackdriver Log Correlation](https://github.com/census-instrumentation/opencensus-java/tree/master/contrib/log_correlation/stackdriver)
is used by the application for this purpose.

Retrying of HTTP requests is a common strategy for overcoming occasional
request failures, such as from server restarts. Shorter timeouts can avoid the
user having to wait for a long time on a stale connection but setting the timeout
too short may not give sufficient time to process occasionally larger payloads.
The application allows setting the HTTP timeout to experiment with the tradeoff
and how the results are manifested in trace and monitoring instrumentation.

The trace and monitoring data may be viewed to analyze latency characteristics.
See if you can correlate high latency with large payload. The example also
allows for experimentation on optimizing efficiency by varying request load and
measuring the effect on latency.

## Prerequisites
The steps described here can be run on a Linux or Mac OS command line or the
GCP Cloud Shell.

- Install Java 8 or later [OpenJDK](https://openjdk.java.net/install/) and Maven.
- Select or create a GCP project. Go to the
  [Project Selector page](https://console.cloud.google.com/projectselector2/home/dashboard)
- Make sure that billing is enabled for your Google Cloud Platform project.
  [Learn how to enable billing](https://cloud.google.com/billing/docs/how-to/modify-project).
- Download and install the
  [Google Cloud SDK](https://cloud.google.com/sdk/docs/).

## Setup
Clone this repository to your environment with the command

```shell
git clone https://github.com/GoogleCloudPlatform/opencensus-jetty-demo.git
```

Edit the environment variables in the file setup.env and import them into your
development environment. Make sure that you change GOOGLE_CLOUD_PROJECT to
the project id of your project.

```shell
cd opencensus-jetty-demo
source setup.env
```

Set the GCP project to be the default

```shell
gcloud config set project $GOOGLE_CLOUD_PROJECT
```

Enable the Stackdriver, Storage, logging, and BigQuery APIs:

```shell
gcloud services enable stackdriver.googleapis.com \
 cloudtrace.googleapis.com \
 compute.googleapis.com \
 storage-api.googleapis.com \
 logging.googleapis.com \
 bigquery-json.googleapis.com
```

This example runs on Compute Engine using the credentials of the default
Compute Engine service account. This service account has sufficient permissions
to write the log, monitoring, and trace data.

Install the Java 8 OpenJDK and Maven:

```shell
sudo apt-get install maven openjdk-8-jdk -y
```

## Test Data
The code makes use of Google Cloud Storage (GCS). Create a bucket to store some
test files
```shell
gsutil mb gs://$BUCKET
```

Generate the example JSON files for the test application.

```shell
cd util
python make_json.py
```

This generates two JSON files that the test application will use to send from
client to server. Sending the large file from client to server should show
higher latency to to the payload size. Upload the files to the bucket just
created.

```shell
gsutil cp small_file.json large_file.json gs://$BUCKET/
cd ..
```

## Server
The server code and build file is contained in the `server` directory.

```shell
cd server
```

Build the war file for to run the server.

```shell
mvn clean package
```

The trace-log correlation on the server is done by a logging enhancer for the
Cloud Logging
[Logback LoggingAppender](https://cloud.google.com/logging/docs/setup/java#logback_appender),
which is configured via the file src/main/resources/logback.xml. That file is
bundled into the war archive in the Maven package command above. The logging
enhancer discovers the trace ID and adds it to the logs, which enables viewing
of log statements in the trace timelines. The project ID is also needed by\
enhancer. If you run the example code in a location other than a GCE virtual
machine you may need to modify the logback.xml file as per the
[OpenCensus Stackdriver Log Correlation](https://github.com/census-instrumentation/opencensus-java/tree/master/contrib/log_correlation/stackdriver) instructions to include the project ID.

Create a virtual machine instance to run the Jetty server:

```shell
gcloud compute instances create $SERVER_INSTANCE \
  --zone=$ZONE \
  --boot-disk-size=200GB
```

Copy the war file to the instance with SCP.

```shell
gcloud compute scp --zone=$ZONE target/jetty-server-tutorial-0.0.1.war \
  $SERVER_INSTANCE:root.war
```

SSH to the instance to configure the server.

```shell
gcloud compute ssh --zone=$ZONE $SERVER_INSTANCE
```

Install the Java 8 OpenJDK

```shell
sudo apt-get install openjdk-8-jdk -y
```

Install and configure
[Jetty](https://www.eclipse.org/jetty/documentation/current/quickstart-running-jetty.html).
First download Jetty from
[Eclipse Jetty Downloads](https://www.eclipse.org/jetty/download.html).

```shell
wget https://repo1.maven.org/maven2/org/eclipse/jetty/jetty-distribution/9.4.19.v20190610/jetty-distribution-9.4.19.v20190610.tar.gz
tar -zxf jetty-distribution-9.4.19.v20190610.tar.gz
```

Set JETTY_HOME:

```shell
export JETTY_HOME=$HOME/jetty-distribution-9.4.19.v20190610
```

Create a Jetty base with a webapps directory

```shell
export JETTY_BASE=$HOME/oc_server_demo
mkdir -p $JETTY_BASE/webapps
cp root.war $JETTY_BASE/webapps/
cd $JETTY_BASE
```

Configure the Jetty base

```shell
java -jar $JETTY_HOME/start.jar --create-startd
java -jar $JETTY_HOME/start.jar --add-to-start=http,deploy
java -jar $JETTY_HOME/start.jar --add-to-start=logging-slf4j
java -jar $JETTY_HOME/start.jar --add-to-start=slf4j-simple-impl
```

The final two of the commands above enable SF4J logging by Jetty.

Run the Jetty server with the web app

```shell
nohup java -jar $JETTY_HOME/start.jar &
```

Send a request to check that it is working

```shell
curl http://localhost:8080/
```

You should see the message 'OK' printed on a single line. If you see an error
check the Jetty output in nohup.out:

```shell
tail -f nohup.out
```

If the server started successfully you should see a message including
Server:main: Started.

Send a request that generates trace information

```shell
curl http://localhost:8080/test
```

This should return a JSON response with a series of numbers. Check that you can
see the logs written to Google Cloud Logging in the Cloud Console
[Log Viewer](https://console.cloud.google.com/logs/viewer?&resource=gce_instance).
You should see a log entry that includes the text 'doGet'.

Navigate to the [Trace timeline](https://console.cloud.google.com/traces/traces)
in the Cloud Console. You should see a few points in the scatter plot. Click
on the points and find the one for the request to the /test endpoint sent using
Curl above. It should be called Recv./test. Click the Show logs button. Notice
that there is a log entry with the text 'doGet'.

Exit the jetty_server virtual machine, returning to the Cloud Shell.

```shell
exit
```

## Client
For the purposes of this tutorial, the code will be built on the client machine.

From the shell of your development environment, create a virtual machine
instance to run the Jetty client:

```shell
gcloud compute instances create $CLIENT_INSTANCE \
  --zone=$ZONE \
  --boot-disk-size=200GB
```

Copy the repository directory tree to the instance.

```shell
cd ../..
tar -zcf opencensus-jetty-demo.tar.gz opencensus-jetty-demo/
gcloud compute scp --zone=$ZONE opencensus-jetty-demo.tar.gz $CLIENT_INSTANCE:.
```

SSH to the instance to set up the client.

```shell
gcloud compute ssh --zone=$ZONE $CLIENT_INSTANCE
```

Install the Java 8 OpenJDK and Maven, as above.

```shell
sudo apt-get install maven openjdk-8-jdk -y
```

Unzip the bundle

```shell
tar -zxf opencensus-jetty-demo.tar.gz
cd opencensus-jetty-demo
```

The client code and build file is contained in the `client` directory.

```shell
source setup.env
cd client
mvn clean package appassembler:assemble
```

Run the client application with the command

```shell
nohup target/appassembler/bin/JettyTestClient $SERVER_INSTANCE 8080 $BUCKET \
  $NUM_THREADS $HTTP_TIMEOUT &
```

This will send a continuous stream of HTTP requests to the server with name
$SERVER_INSTANCE on port 8080. The payloads will be read from $BUCKET using
$NUM_THREADS simultaneous threads with a timeout of $HTTP_TIMEOUT.

Monitor the nohup.out, checking for errors to the standard out

```shell
tail -f nohup.out
```

## Viewing Results
Log messages will be sent to Google Cloud Logging. You can view these in the
[Log Viewer](https://console.cloud.google.com/logs/viewer?expandAll=false&resource=gce_instance)
under GCE VM instances.

View trace data in the
[Trace list](https://console.cloud.google.com/traces/traces) page. The
application will send GET and POST requests alternately. Try clicking on a few
trace points in the Trace list scatter plot. Notice that the HTTP method
type is displayed in the trace detail.

Payload size can influence latency. 95% of requests will send a small payload
and 5% will send a large payload. Clicking on the Show Events button on the
Trace timeline will enable viewing of the payload size. Notice that the traces
with higher latency tend to be the ones with larger payload.

In the trace timeline click on the Show logs button to display the logs
correlated with a trace.

Try clicking on some of the high latency traces to see if any of the requests
are retried. To see how retries are displayed you may try setting the
$HTTP_TIMEOUT command line option to a lower value and restarting the client
application to see how retries are demonstrated in the trace results.

To see metrics data open the Monitoring menu and select Add your project to a
Workspace with a new workspace. Under the Resources | Metrics menu enter the
string 'opencenus' in the Find resource type and metric textfield. You will see
a list of metrics displayed as you type-ahead. Select any of the metrics to see
a chart of the metric values. Specifically, look at the metrics
opencensus/opencensus.io/octail/latency and
opencensus/opencensus.io/http/client/completed_count.

You can save the chart as part of a new dashboard.

## Effect of client CPU usage on latency
Load on both the client and server is another factor that can affect latency.
Shrinking the size of the virtual machines is a great way to optimize efficiency
if they can still operate with satisfactory latency. You can check the CPU load
by creating a chart in the
[Stackdriver moniotring](https://console.cloud.google.com/monitoring) user
 interface using the metric
[instance/cpu/utilization](https://cloud.google.com/monitoring/api/metrics_gcp#gcp-compute).
Create charts for both client and server. Increase the number of threads using
the $NUM_THREADS parameter to run the application.

When increasing the number of threads you increase the request load on both
client and server. To test the effect of client CPU on measured latency, create
another client with a smaller VM, you can repeat the test with a smaller virtual
machine instance.

From the shell of your development environment:

```shell
gcloud compute instances create $SMALL_CLIENT \
  --machine-type=g1-small \
  --zone=$ZONE \
  --boot-disk-size=200GB
```

Then follow the same instructions for the client above.

## Cleaning Up
Delete the project.

- In the GCP Console, go to the Projects page. Go to the
   [Project page](https://console.cloud.google.com/iam-admin/projects).
- In the project list, select the project you want to delete and click
  Delete.
- In the dialog, type the project ID, and then click Shut down to delete the
  project.
