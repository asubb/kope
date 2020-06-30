# Kope

**K**otlin **K**ubernetes **Ope**rator base implementation and helper functions.

The project relies on [fabric8io kubernetes client](https://github.com/fabric8io/kubernetes-client#configuring-the-client) to do all the hard work and tackle communication, and it doesn't provide any wrapper, so to do something outside of the scope of this project you would require to use it. Also, it is quite handy for kotlin project to use a [nice DSL](https://github.com/fkorotkov/k8s-kotlin-dsl). You'll get all this when you start using Koperator in you project.  

## Launching Koperator

These are preliminary steps to run Koperator, that'll definitely be changed in the nearest future.

### Setting up the project

In your operator implementation change the gradle file `build.gradle.kts`:

```kotlin
// add application plugin
plugins {
    application
}

// configure our application
application {
      // configure main class to the Koperator one
    mainClassName = "kope.koperator.LauncherKt"
    // and define the name the scripts will be generated under (optionally)
    applicationName = "myOperator"
}

// add repository Kope is hosted in
repositories {
    // TODO add it here when it's published, for now mavenLocal() is being used and the library is built locally only.
}


// do not forget to add dependencies
dependencies {
    implementation("kope:krd:${kopeVersion}")
    implementation("kope:koperator:${kopeVersion}")
}
```

### Launcher parameters

These are parameters of the Launcher to support different use cases. All parameters are passed via command line:

* `--class=my.package.MyOperator`. The required parameter for any launch that tells where to find the operator implemnetation. Most likely a temporary parameter as there is no way it would be required to be changed on start-up and must be moved to build time. 
* `--install`. Flag telling the Launcher to install the Koperator, following the [Installation Routine](#installation-routine). When it is done the Launcher finishes it's job.
* `--uninstall`. Flag telling the Launcher to uninstall the Koperator, following the [Uninstallation Routine](#uninstallation-routine). When it is done the Launcher finishes it's job.
* `--context=TheNameOfContext`. By default the kubernetes client uses current active context found in your `~/.kube/config`, that allows you to change which one you wanna use. Also, you may alter the config by using system or environment parameters. Follow [kubernetes client documentation](https://github.com/fabric8io/kubernetes-client#configuring-the-client).

### Running via gradle

To run the operator via gradle it is as simple as that, where `--args=""` parameters encapsulates all parameters passed to the Launcher itself. Simple `Ctrl-C` stops the execution gracefully.

```bash
 ./gradlew run --args="--class=my.package.MyOperator"     
```

### Running in Intellij IDEA

To run in IDEA add the following `Run/Debug configuration`

* Type: `Kotlin`
* Main class: `kope.koperator.LauncherKt` *(despite the fact that idea doesn't see it, it works as expected)*
* Program arguments: `--class=my.package.MyOperator`

### Installation Routine

[TODO]

### Uninstallation Routine

[TODO]