package com.sksamuel.scapedot

import dotty.tools.dotc.ast.{Trees, tpd}
import dotty.tools.dotc.core.Constants.Constant
import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.core.{Decorators, StdNames, Symbols}
import dotty.tools.dotc.plugins.{PluginPhase, StandardPlugin}
import dotty.tools.dotc.transform.{Pickler, Staging}

class ScapedotPlugin extends StandardPlugin:
  val name: String = "scapedot"
  override val description: String = "scapedot static code analysis"

  def init(options: List[String]): List[PluginPhase] = {
    println("Creating plugin")
    (new ScapedotPluginPhase) :: Nil
  }