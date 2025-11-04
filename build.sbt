import sbtassembly.AssemblyPlugin.autoImport._
import sbtassembly.PathList
import sbt.io.IO

lazy val packageWindowsImage = taskKey[java.io.File]("Builds a Windows app image ready for double-click launch on Windows.")

ThisBuild / scalaVersion := "3.3.6"
ThisBuild / version := "0.1.0"
ThisBuild / organization := "com.example"

lazy val javafxVersion = "21.0.2"
lazy val osName = System.getProperty("os.name").toLowerCase
lazy val javafxPlatform =
  if (osName.contains("win")) "win"
  else if (osName.contains("mac")) "mac"
  else "linux"

lazy val root = project
  .in(file("."))
  .settings(
    name := "Invoicer",
    Compile / run / fork := true,
    Compile / run / javaOptions ++= {
      val cp = (Compile / dependencyClasspath).value.map(_.data)
      val javafxJars = cp.filter(_.getName.startsWith("javafx"))
      if (javafxJars.nonEmpty) {
        val modulePath = javafxJars.map(_.getAbsolutePath).mkString(java.io.File.pathSeparator)
        Seq(
          "--module-path",
          modulePath,
          "--add-modules",
          "javafx.controls,javafx.graphics,javafx.base"
        )
      } else Seq.empty
    },
    libraryDependencies ++= Seq(
      "org.openjfx" % "javafx-base" % javafxVersion classifier javafxPlatform,
      "org.openjfx" % "javafx-controls" % javafxVersion classifier javafxPlatform,
      "org.openjfx" % "javafx-graphics" % javafxVersion classifier javafxPlatform,
      "org.xerial" % "sqlite-jdbc" % "3.46.0.0",
      "org.apache.pdfbox" % "pdfbox" % "2.0.30",
      "org.slf4j" % "slf4j-simple" % "1.7.36"
    ),
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Xfatal-warnings"
    ),
    Compile / mainClass := Some("invoicer.MainApp"),
    assembly / mainClass := Some("invoicer.MainApp"),
    assembly / assemblyJarName := s"${name.value}-${version.value}-fat.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
      case PathList("META-INF", "INDEX.LIST")  => MergeStrategy.discard
      case PathList("META-INF", xs @ _*)
          if xs.lastOption.exists(name =>
            name.endsWith(".SF") || name.endsWith(".DSA") || name.endsWith(".RSA")
          ) =>
        MergeStrategy.discard
      case PathList("META-INF", "versions", xs @ _*) => MergeStrategy.first
      case PathList("module-info.class") => MergeStrategy.discard
      case x if x.endsWith("module-info.class") => MergeStrategy.discard
      case "reference.conf" => MergeStrategy.concat
      case other => MergeStrategy.first
    },
    packageWindowsImage := {
      val log = streams.value.log
      val appName = name.value
      val targetDir = target.value
      val stageDir = targetDir / "jpackage-input"
      val javafxDir = stageDir / "javafx-mods"
      val outputDir = targetDir / "jpackage"

      IO.delete(stageDir)
      IO.delete(outputDir / appName)
      IO.createDirectory(stageDir)
      IO.createDirectory(javafxDir)

      val mainJar = (Compile / packageBin).value
      IO.copyFile(mainJar, stageDir / mainJar.getName)

      val runtimeDeps = (Runtime / dependencyClasspath).value
        .map(_.data)
        .filter(f => f.isFile && f.getName.endsWith(".jar"))
        .filterNot(_.getAbsolutePath == mainJar.getAbsolutePath)
        .distinct

      runtimeDeps.foreach { jar =>
        val targetFile =
          if (jar.getName.startsWith("javafx"))
            javafxDir / jar.getName
          else
            stageDir / jar.getName
        if (!targetFile.exists()) IO.copyFile(jar, targetFile, preserveLastModified = false)
      }

      val javaHome = sys.env
        .get("JAVA_HOME")
        .orElse(sys.props.get("java.home"))
        .getOrElse(sys.error("JAVA_HOME non defini. Definissez JAVA_HOME vers un JDK 17+ contenant jpackage."))

      val javaHomeDir = new java.io.File(javaHome)
      val jpackageExecutable = {
        val exeName = if (scala.util.Properties.isWin) "jpackage.exe" else "jpackage"
        val candidate = new java.io.File(new java.io.File(javaHomeDir, "bin"), exeName)
        if (!candidate.exists()) sys.error(s"Impossible de trouver $exeName dans ${candidate.getParent}.")
        candidate.getAbsolutePath
      }

      val jmodsDir = new java.io.File(javaHomeDir, "jmods")
      if (!jmodsDir.exists()) sys.error(s"Le repertoire jmods est introuvable dans ${jmodsDir.getAbsolutePath}. Utilisez un JDK complet (pas un JRE).")

      val modulePath = Seq(javafxDir.getAbsolutePath, jmodsDir.getAbsolutePath).mkString(java.io.File.pathSeparator)
      IO.createDirectory(outputDir)

      val cmd = Seq(
        jpackageExecutable,
        "--type",
        "app-image",
        "--name",
        appName,
        "--dest",
        outputDir.getAbsolutePath,
        "--input",
        stageDir.getAbsolutePath,
        "--main-jar",
        mainJar.getName,
        "--main-class",
        (Compile / mainClass).value.getOrElse(sys.error("Classe principale introuvable.")),
        "--module-path",
        modulePath,
        "--add-modules",
        "javafx.controls,javafx.graphics,javafx.base",
        "--java-options",
        "-Dprism.order=d3d"
      )

      val exit = scala.sys.process.Process(cmd).!
      if (exit != 0) sys.error(s"Echec de jpackage (code $exit).")

      val imageDir = outputDir / appName
      log.info(s"Image applicative generee dans ${imageDir.getAbsolutePath}")
      imageDir
    }
  )
