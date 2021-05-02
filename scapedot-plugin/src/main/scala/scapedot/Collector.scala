package scapedot

import scala.collection.mutable.ListBuffer

class Collector {

  private val warningsBuffer = new ListBuffer[Warning]

  def warnings: Seq[Warning] = warningsBuffer.toSeq

  def report(pos: dotty.tools.dotc.util.SourcePosition,
             inspection: Inspection,
             snippet: Option[String] = None,
             adhocExplanation: Option[String] = None
            ): Unit = {

    val warning = Warning(
      id = inspection.getClass.getName,
      description = inspection.description,
      line = pos.line,
      level = inspection.level,
      sourceFileFull = "sourceFileFull",
      sourceFileNormalized = "sourceFileNormalized",
      snippet = snippet,
      explanation = inspection.explanation,
    )
    warningsBuffer.append(warning)
  }
}


final case class Warning(id: String, // the id of the inspection that generated this warning
                         description: String,
                         explanation: String, // a more detailed explanation from the inspection
                         line: Int,
                         level: Level, // the warning level
                         sourceFileFull: String,
                         sourceFileNormalized: String,
                         snippet: Option[String]
                        )