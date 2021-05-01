package com.sksamuel.scapedot.inspections.types

import com.sksamuel.scapedot.{Inspection, Inspector, Level}
import dotty.tools.dotc.ast.tpd

class BoundedByFinalType extends Inspection {

  override val level: Level = Level.Warning
  override val description: String = "Checks for types with upper bounds of a final type"
  override val explanation: String = "Pointless type bound. Type parameter can only be a single value"

  override val inspector: Inspector = new Inspector {

    import tpd.*

    override def continue(tree: Tree): Unit = {}
  }
}
