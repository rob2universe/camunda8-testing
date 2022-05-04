package io.camunda.c8.test;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.process.test.api.ZeebeTestEngine;
import io.camunda.zeebe.process.test.assertions.BpmnAssert;
import io.camunda.zeebe.process.test.extension.ZeebeProcessTest;
import io.camunda.zeebe.process.test.filters.RecordStream;
import io.grpc.internal.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@ZeebeProcessTest
public class ProcessTests {
  private ZeebeTestEngine engine;
  private ZeebeClient client;
  private RecordStream recordStream;

  private DeploymentEvent initDeployment() {
    return client.newDeployResourceCommand()
        .addResourceFromClasspath("process.bpmn")
        .addResourceFromClasspath("decision.dmn")
        .send()
        .join();
  }

  @Test
  public void testDeployment() {
    BpmnAssert.assertThat(initDeployment());
  }

  @Test
  public void testProcess() throws InterruptedException, TimeoutException {
    initDeployment();

    // When instance is started
    var piEvent = client.newCreateInstanceCommand()
        .bpmnProcessId("TestProcess")
        .latestVersion()
        .variables(Map.of("myItem","a"))
        .send()
        .join();
    // Then instance should have passed start event and should be awaiting job completion
    BpmnAssert.assertThat(piEvent)
        .hasPassedElement("ProcessingStartedStartEvent")
        .isWaitingAtElements("CallServiceTask");

    // When job is activate
    var response = client.newActivateJobsCommand()
        .jobType("callService")
        .maxJobsToActivate(1)
        .send()
        .join();
    // Then activated job should exist
    var activatedJob = response.getJobs().get(0);
    BpmnAssert.assertThat(activatedJob);

    // When job is completed and process engine had time to continue processing
    client.newCompleteCommand(activatedJob.getKey()).send().join();
    engine.waitForIdleState(Duration.ofMillis(1000));

    // Then service task, business rule task, and process instance should be completed
    BpmnAssert.assertThat(piEvent)
        .hasPassedElementsInOrder("CallServiceTask","EvaluateBusinessRulesTask")
        .isCompleted()
        // and business rule task result should be available as process data
        .hasVariableWithValue("result","aa");
  }
}
