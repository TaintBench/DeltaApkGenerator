# DeltaApkGenerator
Command-line tool created for  [TaintBench](https://taintbench.github.io/taintbenchFramework).
It sanitizes an apk with respect to each taint flow specified in the [TAF-format](https://github.com/TaintBench/TaintBench/blob/master/TAF-schema.json) and creates a new APK for each taint flow. 

# Build
Build the project with Maven: ``mvn install``

# Run
Run ``java -jar delta-apk-generator-0.0.2.jar`` followed by the following command line option: 

``-apk <apk> -f <TAF-file>  -p <android platform jars>``

or 

``-dir <folder contains both apk and TAF-file> -p <android platform jars>``

The default output folder is `yourWorkingDirectory/delta_apks`. 

For each taint flow, a folder `delta_ID` is created with the ID of the taint flow. You can find the respective sanitized APK for the taint flow. A `*delta_choices.json` file is generated as manual selection is requried as multiple jimple statements can be the specified source. The `*delta_choices.json` file can be used for next run with the following options: 

``-apk <apk> -f <TAF-file>  -p <android platform jars> -c <*delta_choices.json>``
