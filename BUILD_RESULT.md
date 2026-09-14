# Build Result - Mon Sep 14 18:12:47 UTC 2026
## ❌ FAILED - No APK
```
> Task :app:packageDebugResources FAILED
    org.xml.sax.SAXParseException; systemId: file:/home/runner/work/Bluetooth-/Bluetooth-/app/src/main/res/layout/activity_main.xml; lineNumber: 35; columnNumber: 31; Attribute "android:layout_margin" was already specified for element "LinearLayout".
    	at org.gradle.api.internal.tasks.execution.CatchExceptionTaskExecuter.execute(CatchExceptionTaskExecuter.java:36)
    org.xml.sax.SAXParseException; systemId: file:/home/runner/work/Bluetooth-/Bluetooth-/app/src/main/res/layout/activity_main.xml; lineNumber: 35; columnNumber: 31; Attribute "android:layout_margin" was already specified for element "LinearLayout".
    	at org.gradle.api.internal.tasks.execution.CatchExceptionTaskExecuter.execute(CatchExceptionTaskExecuter.java:36)
> Task :app:mergeDebugResources FAILED
* Exception is:
org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':app:packageDebugResources'.
	at org.gradle.api.internal.tasks.execution.CatchExceptionTaskExecuter.execute(CatchExceptionTaskExecuter.java:36)
Caused by: com.android.build.gradle.tasks.ResourceException: /home/runner/work/Bluetooth-/Bluetooth-/app/src/main/res/layout/activity_main.xml:35:31: Error: Attribute "android:layout_margin" was already specified for element "LinearLayout".
	at org.gradle.api.internal.tasks.execution.CatchExceptionTaskExecuter.execute(CatchExceptionTaskExecuter.java:36)
	at com.android.ide.common.resources.MergingException$Builder.build(MergingException.java:152)
Caused by: org.xml.sax.SAXParseException; systemId: file:/home/runner/work/Bluetooth-/Bluetooth-/app/src/main/res/layout/activity_main.xml; lineNumber: 35; columnNumber: 31; Attribute "android:layout_margin" was already specified for element "LinearLayout".
* Exception is:
org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':app:mergeDebugResources'.
	at org.gradle.api.internal.tasks.execution.CatchExceptionTaskExecuter.execute(CatchExceptionTaskExecuter.java:36)
Caused by: com.android.build.gradle.tasks.ResourceException: /home/runner/work/Bluetooth-/Bluetooth-/app/src/main/res/layout/activity_main.xml:35:31: Error: Attribute "android:layout_margin" was already specified for element "LinearLayout".
	at org.gradle.api.internal.tasks.execution.CatchExceptionTaskExecuter.execute(CatchExceptionTaskExecuter.java:36)
	at com.android.ide.common.resources.MergingException$Builder.build(MergingException.java:152)
Caused by: org.xml.sax.SAXParseException; systemId: file:/home/runner/work/Bluetooth-/Bluetooth-/app/src/main/res/layout/activity_main.xml; lineNumber: 35; columnNumber: 31; Attribute "android:layout_margin" was already specified for element "LinearLayout".
BUILD FAILED in 55s
---LAST 40 LINES---
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter.execute(EventFiringTaskExecuter.java:52)
	at org.gradle.execution.plan.LocalTaskNodeExecutor.execute(LocalTaskNodeExecutor.java:42)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$InvokeNodeExecutorsAction.execute(DefaultTaskExecutionGraph.java:337)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$InvokeNodeExecutorsAction.execute(DefaultTaskExecutionGraph.java:324)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:317)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:303)
	at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.execute(DefaultPlanExecutor.java:463)
	at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.run(DefaultPlanExecutor.java:380)
	at org.gradle.internal.concurrent.ExecutorPolicy$CatchAndRecordFailures.onExecute(ExecutorPolicy.java:64)
	at org.gradle.internal.concurrent.AbstractManagedExecutor$1.run(AbstractManagedExecutor.java:47)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)
Caused by: /home/runner/work/Bluetooth-/Bluetooth-/app/src/main/res/layout/activity_main.xml:35:31: Error: Attribute "android:layout_margin" was already specified for element "LinearLayout".
	at com.android.ide.common.resources.MergedResourceWriter.end(MergedResourceWriter.java:308)
	at com.android.ide.common.resources.DataMerger.mergeData(DataMerger.java:299)
	at com.android.ide.common.resources.ResourceMerger.mergeData(ResourceMerger.java:391)
	at com.android.build.gradle.tasks.MergeResources$doFullTaskAction$1$1$2.invoke(MergeResources.kt:268)
	at com.android.build.gradle.internal.tasks.Blocks.recordSpan(Blocks.java:51)
	at com.android.build.gradle.tasks.MergeResources.doFullTaskAction(MergeResources.kt:264)
	... 127 more
Caused by: /home/runner/work/Bluetooth-/Bluetooth-/app/src/main/res/layout/activity_main.xml:35:31: Error: Attribute "android:layout_margin" was already specified for element "LinearLayout".
	at com.android.ide.common.resources.MergingException$Builder.build(MergingException.java:152)
	at com.android.ide.common.resources.MergedResourceWriter.end(MergedResourceWriter.java:304)
	... 132 more
Caused by: org.xml.sax.SAXParseException; systemId: file:/home/runner/work/Bluetooth-/Bluetooth-/app/src/main/res/layout/activity_main.xml; lineNumber: 35; columnNumber: 31; Attribute "android:layout_margin" was already specified for element "LinearLayout".
	at org.apache.xerces.parsers.DOMParser.parse(Unknown Source)
	at org.apache.xerces.jaxp.DocumentBuilderImpl.parse(Unknown Source)
	at java.xml/javax.xml.parsers.DocumentBuilder.parse(DocumentBuilder.java:206)
	at android.databinding.tool.store.LayoutFileParser.stripFile(LayoutFileParser.java:469)
	at android.databinding.tool.store.LayoutFileParser.parseXml(LayoutFileParser.java:97)
	at android.databinding.tool.LayoutXmlProcessor.processSingleFile(LayoutXmlProcessor.java:161)
	at com.android.build.gradle.tasks.MergeResources$maybeCreateLayoutProcessor$1.processSingleFile(MergeResources.kt:593)
	at com.android.ide.common.resources.MergedResourceWriter.end(MergedResourceWriter.java:243)
	... 132 more

==============================================================================

BUILD FAILED in 55s
4 actionable tasks: 4 executed
```
