package scapedot

import scapedot.Level
import dotty.tools.dotc.ast.{Trees, tpd}

trait Inspection {
  val level: Level
  val description: String
  val explanation: String
  val inspector: Inspector
}

trait Inspector {
  import tpd.*
  def continue(tree: Tree): Unit
}
