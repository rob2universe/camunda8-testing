package io.camunda.c8.test;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.process.test.api.ZeebeTestEngine;
import io.camunda.zeebe.process.test.assertions.BpmnAssert;
import io.camunda.zeebe.process.test.extension.ZeebeProcessTest;
import io.camunda.zeebe.process.test.filters.RecordStream;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@ZeebeProcessTest
public class ProcessTests {
  private ZeebeTestEngine engine;
  private ZeebeClient client;
  private RecordStream recordStream;

  private DeploymentEvent initDeployment() {
    return client.newDeployResourceCommand()
        .addResourceFromClasspath("test-process.bpmn")
        .addResourceFromClasspath("test-decision.dmn")
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
    engine.waitForIdleState(Duration.ofMillis(100));

    // Then service task and process instance should be completed
    BpmnAssert.assertThat(piEvent)
        .hasPassedElement("CallServiceTask")
        .isCompleted();
  }
}
