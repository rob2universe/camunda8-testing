package io.camunda.c8.test;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.process.test.api.ZeebeTestEngine;
import io.camunda.zeebe.process.test.extension.ZeebeProcessTest;
import io.camunda.zeebe.process.test.filters.RecordStream;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static io.camunda.zeebe.process.test.assertions.BpmnAssert.assertThat;

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
    assertThat(initDeployment());
  }

  @Test
  public void testProcess() throws InterruptedException, TimeoutException {
    initDeployment();

    // When instance is started
    var piEvent = client.newCreateInstanceCommand()
        .bpmnProcessId("TestProcess")
        .latestVersion()
        .variables(Map.of("myItem", "a"))
        .send()
        .join();
    // Then instance should have passed start event and should be awaiting job completion
    assertThat(piEvent)
        .hasPassedElement("ProcessingStartedStartEvent")
        .isWaitingAtElements("CallServiceTask");

    // When job is activated
    var response = client.newActivateJobsCommand()
        .jobType("callService")
        .maxJobsToActivate(1)
        .send()
        .join();
    // Then activated job should exist
    var activatedJob = response.getJobs().get(0);
    assertThat(activatedJob);

    // When job is completed and process engine had time to continue processing
    client.newCompleteCommand(activatedJob.getKey()).send().join();
    engine.waitForIdleState(Duration.ofMillis(500));

    // Then service task, business rule task, and process instance should be completed
    assertThat(piEvent)
        .hasPassedElementsInOrder("CallServiceTask", "EvaluateBusinessRulesTask")
        .isCompleted()
        // and business rule task result should be available as process data
        .hasVariableWithValue("result", Map.of("checkedItem","a","myOutput","aa"));
    //TODO
    // the test passed and sometimes fails, probably depending on the sequence of the serialization of the Map.
    // When the test failes the error is:
    // java.lang.AssertionError: The variable 'result' does not have the expected value. The value passed in
    // ('{checkedItem=a, myOutput=aa}') is internally mapped to a JSON String that yields
    // '{"checkedItem":"a","myOutput":"aa"}'. However, the actual value (as JSON String) is
    // '{"myOutput":"aa","checkedItem":"a"}'.

  }
}