package com.histmersion.linting

import scala.meta.*
import scalafix.v1.*

object Lints:

  def textFindings(input: Input, violations: Map[String, List[Violation]], names: Map[String, String]): List[Finding] =
    Project.ref(Project.relativeOf(input)).toList.flatMap { r =>
      violations.getOrElse(r.key, Nil).flatMap { v =>
        v.offsets.map { o =>
          val text = input.text
          val end = math.max(o + 1, text.indexOf('\n', o) match
            case -1 => text.length
            case n  => n
          )
          Finding(names.getOrElse(v.rule, v.rule), Position.Range(input, o, math.min(end, text.length)), v.detail)
        }
      }
    }

final class ToleratedLint extends SyntacticRule("ToleratedLint"):
  override def fix(using doc: SyntacticDocument): Patch =
    Tolerance.patch(Shape.findings(doc.tree, doc.tokens))
