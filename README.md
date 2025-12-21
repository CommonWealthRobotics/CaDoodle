# Cadoodle Website

[https://cadoodlecad.com/](https://cadoodlecad.com/)


# CaDoodle
A free and open source CAD package using drag-and-drop shapes. 

Installers: [![Github All Releases](https://img.shields.io/github/downloads/CommonWealthRobotics/CaDoodle/total.svg)]() 

Application: [![Github All Releases](https://img.shields.io/github/downloads/CommonWealthRobotics/CaDoodle-Application/total.svg)]()

![Screen Shot](CaDoodle-Screenshot.png)

# Download

[Download and Install here](https://github.com/CommonWealthRobotics/CaDoodle/releases)

# Source Code

This repository is the installer and auto-updater. The installable releases are found here. 

The below link is for the source code of the main application itself.

https://github.com/CommonWealthRobotics/CaDoodle-Application

# Windows install

For silent local installs

```

CaDoodle-Windows-x86_64.exe /S 

```

For silent system installs

```

CaDoodle-Windows-System-x86_64.exe /S 

CaDoodle-Windows-System-x86_64.exe /S /D=D:\CustomLocation\CaDoodle\

```

# Visual Studio Code building the Java JAR file

To build the updater Java JAR file in Visual Studio Code install the following extensions:
1. Gradle for Java
2. Language Support for Java(TM) by Red Hat

Open the CaDoodleUpdater directory.

When VS Code asks to use Gradle or Maven, select Gradle.

To build the JAR file the Azul 17 Java-JavaFX JVM is required, it can be found here:
https://www.azul.com/downloads/?version=java-17-lts&package=jdk-fx#zulu

Supported platforms are Windows-x64, Linux-x64, Linux-ARM, Mac-x64 and Mac-ARM.
Extract the JVM to a path without spaces in it.

In the VS Code "settings.json" point to the files in the JVM:
```
"java.jdt.ls.java.home": "<YOUR_PATH>",
"java.configuration.runtimes": "<YOUR_PATH>"
```

Both paths can point to the same directory (the JRE is included in the JVM).
The java executable (java or java.exe) should be located in:
```"<YOUR_PATH>\\bin\\"```

Select the Gradle extension (elephant icon on the left), then select "CaDoodleUpdater/Tasks/build/build" to build the Jar file.
The CaDoodleUpdater.jar file will be located in "CaDoodleUpdater\build\libs"
