package com.sksamuel.scapedot

import dotty.tools.dotc.ast.Trees.*
import dotty.tools.dotc.ast.tpd
import dotty.tools.dotc.core.Constants.Constant
import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.core.Decorators.*
import dotty.tools.dotc.core.StdNames.*
import dotty.tools.dotc.core.Symbols.*
import dotty.tools.dotc.plugins.{PluginPhase, StandardPlugin}
import dotty.tools.dotc.transform.{Pickler, Staging}

class ScapedotPluginPhase extends PluginPhase :

  import tpd.*

  val phaseName = "scapedot"

  override val runsAfter = Set(Pickler.name)
  override val runsBefore = Set(Staging.name)

  override def transformApply(tree: Apply)(implicit ctx: Context): Tree =
    tree match
      case Apply(Select(rcvr, nme.DIV), List(Literal(Constant(0))))
        if rcvr.tpe <:< defn.IntType =>
        report.error("dividing by zero", tree.pos)
      case _ =>
        ()
    tree
end ScapedotPluginPhase
