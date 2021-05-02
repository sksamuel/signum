def isGithubActions = sys.env.getOrElse("CI", "false") == "true"
def releaseVersion = sys.env.getOrElse("RELEASE_VERSION", "")
def isRelease = releaseVersion != ""
def githubRunNumber = sys.env.getOrElse("GITHUB_RUN_NUMBER", "")
def ossrhUsername = sys.env.getOrElse("OSSRH_USERNAME", "")
def ossrhPassword = sys.env.getOrElse("OSSRH_PASSWORD", "")
def publishVersion = if (isRelease) releaseVersion else if (isGithubActions) "0.10.0" + githubRunNumber + "-SNAPSHOT" else "0.0.0-LOCAL"


val ScalaVersion = "3.0.0-RC3"
val ScalatestVersion = "3.2.8"

lazy val commonScalaVersionSettings = Seq(
  scalaVersion := ScalaVersion
  //  crossScalaVersions := Seq("2.12.11", "2.13.5")
)

lazy val commonSettings = Seq(
  organization := "com.sksamuel.scapedot",
  description := "Static code analysis for Scala 3",
  version := publishVersion,
  resolvers ++= Seq(Resolver.mavenLocal),
  parallelExecution in Test := false,
  fork in Test := true,
  initialize := {
    System.setProperty("SCAPEDOT_SCALA_VERSION", ScalaVersion)
  },
  scalacOptions in(Compile, doc) := (scalacOptions in(Compile, doc)).value.filter(_ != "-Xfatal-warnings"),
  scalacOptions ++= Seq("-unchecked", "-deprecation", "-encoding", "utf8")
)


lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := Function.const(false),
  credentials += Credentials(
    "Sonatype Nexus Repository Manager",
    "oss.sonatype.org",
    ossrhUsername,
    ossrhPassword
  ),
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isRelease)
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
    else
      Some("snapshots" at nexus + "content/repositories/snapshots")
  }
)

lazy val commonJvmSettings = Seq(
  testOptions in Test += {
    val flag = if (isGithubActions) "-oCI" else "-oDF"
    Tests.Argument(TestFrameworks.ScalaTest, flag)
  },
  Test / fork := true,
  Test / javaOptions := Seq("-Xmx3G"),
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
  javaOptions ++= Seq("-Xms512M", "-Xmx2048M", "-XX:MaxPermSize=2048M", "-XX:+CMSClassUnloadingEnabled"),
)

lazy val pomSettings = Seq(
  homepage := Some(url("https://github.com/sksamuel/scapedot")),
  licenses := Seq("Apache 2" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  scmInfo := Some(ScmInfo(url("https://github.com/sksamuel/scapedot"), "scm:git:git@github.com:sksamuel/scapedot.git")),
  apiURL := Some(url("http://github.com/sksamuel/scapedot")),
  pomExtra := <developers>
    <developer>
      <id>sksamuel</id>
      <name>Sam Samuel</name>
      <url>https://github.com/sksamuel</url>
    </developer>
  </developers>
)

lazy val noPublishSettings = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false
)

lazy val allSettings = commonScalaVersionSettings ++
  commonJvmSettings ++
  commonSettings ++
  pomSettings ++
  publishSettings

lazy val root = project
  .settings(allSettings)
  .settings(noPublishSettings)
  .aggregate(core, plugin)

lazy val core = project
  .in(file("scapedot-core"))
  .settings(allSettings)
  .settings(
    name := "scapedot-core",
    libraryDependencies += "org.scalatest" %% "scalatest" % ScalatestVersion % "test"
  )

lazy val plugin = project
  .in(file("scapedot-plugin"))
  .dependsOn(core)
  .settings(allSettings)
  .settings(
    name := "scapedot-plugin",
    libraryDependencies += "org.scalatest" %% "scalatest" % ScalatestVersion % "test",
    libraryDependencies += "org.scala-lang" %% "scala3-compiler" % ScalaVersion % "provided"
  )