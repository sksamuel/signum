val ScalaVersion = "3.0.0-RC3"

lazy val root = project
  .in(file("."))
  .settings(
    name := "scapedot",
    description := "Static code analysis for Scala 3",
    version := "0.1.0",
    scalaVersion := ScalaVersion,
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.8" % "test",
    libraryDependencies += "org.scala-lang" %% "scala3-compiler" % ScalaVersion
  )
