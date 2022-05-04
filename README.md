# Camunda 8 Testing Example 

The example is based on Camunda's zeebe-process-test project: https://github.com/camunda/zeebe-process-test

This example illustrates the JDK 17-based embedded Zeebe test engine. It is the faster approach and does not require Docker.
If you have to run tests with an older JDK, please refer to the JDK 8+ approach described on the zeebe-process-test project page. 


The test class [ProcessTests](./src/test/java/io/camunda/c8/test/ProcessTests.java) illustrates testing   
- the process and decision model deployment 
- the start of a process instance
- job completion
- DMN evaluation in a business rule task
- successful termination of the process instance
- validating correct DMN result

