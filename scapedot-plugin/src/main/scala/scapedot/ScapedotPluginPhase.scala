package scapedot

import dotty.tools.dotc.ast.{Trees, tpd}
import dotty.tools.dotc.core.Constants.Constant
import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.plugins.{PluginPhase, StandardPlugin}
import dotty.tools.dotc.transform.{Pickler, Staging}

class ScapedotPluginPhase extends PluginPhase :

  import tpd.*

  val phaseName = "scapedot"

  object ScapedotTraverser extends tpd.TreeTraverser:
    override def traverse(tree: tpd.Tree)(using context: Context): Unit = {
      println(s"enter $tree")
      traverseChildren(tree)
    }

  override val runsAfter = Set(Pickler.name)
  override val runsBefore = Set(Staging.name)

  override def transformUnit(tree: tpd.Tree)(using context: Context): tpd.Tree =
    println(s"Transform unit $tree")
    ScapedotTraverser.traverse(tree)
    super.transformUnit(tree)

end ScapedotPluginPhase

