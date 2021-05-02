package scapedot.inspections.collections

import scapedot.{Inspection, Inspector, Level}
import dotty.tools.dotc.ast.{Trees, tpd}

class ArrayEquals extends Inspection :

  override val description: String = "Checks for comparison of arrays using == which tests for reference"
  override val level: Level = Level.Info
  override val explanation = "Array equals is not an equality check. Use a.deep == b.deep or convert to another collection type"
  override val inspector = new Inspector {
    import tpd.*
    override def inspect(tree: Tree): Unit =
      tree match {
        case Apply(fun, args) => println("hello")
      }

  }

//
//        override def inspect(tree: Tree): Unit = {
//          tree match {
//            case Apply(Select(lhs, TermName("$eq$eq") | TermName("$bang$eq")), List(rhs))
//              if isArray(lhs) && isArray(rhs) =>
//              context.warn(tree.pos, self)
//            case _ => continue(tree)
//          }
//        }
//      }
