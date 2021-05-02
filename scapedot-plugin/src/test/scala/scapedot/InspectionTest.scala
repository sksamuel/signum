package scapedot

import dotty.tools.dotc
import dotty.tools.dotc.Compiler
import dotty.tools.dotc.core.Contexts.{Context, ctx}
import dotty.tools.dotc.decompiler.TASTYDecompiler
import dotty.tools.io.AbstractFile
import dotty.tools.dotc.reporting.ConsoleReporter
import dotty.tools.dotc.reporting.TestingReporter
import org.scalatest.OneInstancePerTest
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.Paths
import java.io.{File, FileNotFoundException}
import java.nio.charset.StandardCharsets
import java.nio.file.Files

object ScapedotDriver extends dotc.Driver {
  override protected def newCompiler(using Context): dotc.Compiler = new Compiler
  override protected def fromTastySetup(files: List[AbstractFile])(using ctx: Context): Context = ctx
}

def writeCodeSnippetToTempFile(code: String): File = {
  val temp = Files.createTempFile("scapedot_snippet", ".scala")
  val file = Files.write(temp, code.getBytes(StandardCharsets.UTF_8)).toFile
  file.deleteOnExit()
  file
}

///**
// * Locates a jar inside the users Ivy cache.
// */
//def findIvyJar(groupId: String, artifactId: String, version: String): File = {
//  val userHome = System.getProperty("user.home")
//  val sbtHome = userHome + "/.ivy2"
//  val jarPath =
//    sbtHome + "/cache/" + groupId + "/" + artifactId + "/jars/" + artifactId + "-" + version + ".jar"
//  val file = new File(jarPath)
//  if (file.exists) file
//  else throw new FileNotFoundException(s"Could not locate [$jarPath].")
//}

// used to find the compiled files
def scalaVersion = sys.props.getOrElse("SCAPEDOT_SCALA_VERSION", "scala-3.0.0-RC3")

def compileClassPath =
  val target = Paths.get("target/")
  val classpath = target.toAbsolutePath.toString + s"/$scalaVersion/classes/"
  new File(classpath)

//def findLocalIvyJar(groupId: String, artifactId: String, version: String): File =
//  val userHome = System.getProperty("user.home")
//  val ivyHome = userHome + "/.ivy2"
//  // eg /home/sam/.ivy2/local/com.sksamuel.scapedot/scapedot-plugin_3.0.0-RC3/0.0.0-LOCAL/jars/scapedot-plugin_3.0.0-RC3.jar
//  val jarPath = ivyHome + "/local/" + groupId + "/" + artifactId + "/" + version + "/jars/" + artifactId + ".jar"
//  val file = new File(jarPath)
//  if (file.exists) file
//  else throw new FileNotFoundException(s"Could not locate [$jarPath].")

abstract class InspectionTest extends AnyFreeSpec with Matchers with OneInstancePerTest :

  def inspections: Seq[Inspection]

  def compileCodeSnippet(snippet: String): Unit =

    val file = writeCodeSnippetToTempFile(snippet)
    val sources = List(file.getPath)

    val out = Paths.get("out/").toAbsolutePath()
    if (Files.notExists(out))
      Files.createDirectory(out)

    val args = sources ++ List("-d", out.toString, "-classpath", "", "-usejavacp", s"-Xplugin:$compileClassPath")
    val reporter = new ConsoleReporter()
    ScapedotDriver.process(args.toArray, reporter)