import sbtassembly.AssemblyPlugin.autoImport._
import sbtassembly.PathList

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
      case PathList("META-INF", xs @ _*) if xs.lastOption.exists(_.endsWith(".SF") || _.endsWith(".DSA") || _.endsWith(".RSA")) =>
        MergeStrategy.discard
      case PathList("META-INF", "versions", xs @ _*) => MergeStrategy.first
      case PathList("module-info.class") => MergeStrategy.discard
      case x if x.endsWith("module-info.class") => MergeStrategy.discard
      case "reference.conf" => MergeStrategy.concat
      case other => MergeStrategy.first
    }
  )
