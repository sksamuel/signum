val ScalaVersion = "3.0.0-RC3"
val scapedotVersion = "0.10.0"

organization := "com.sksamuel.scapedot"

lazy val root = project
  .in(file("."))
  .settings(
    name := "scapedot-plugin",
    description := "Static code analysis for Scala 3",
    version := scapedotVersion,
    scalaVersion := ScalaVersion,
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.8" % "test",
    libraryDependencies += "org.scala-lang" %% "scala3-compiler" % ScalaVersion % "provided"
  )