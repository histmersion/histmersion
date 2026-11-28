package com.histmersion
package api.research.critiquepairing

import com.histmersion.util.json.JsonCodec
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Citekey
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.research.CritiqueRef
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.api.json.TwoStates
import com.histmersion.api.research.CritiquePair

final case class MissedPair(
    unit: MissedPair.Unit,
    from: MissedPair.From,
    to: MissedPair.To,
    where: MissedPair.Where,
    cited: Citekey,
    missing: Citekey,
    edges: List[MissedPair.Edges],
    named: MissedPair.Named,
    mechanical: MissedPair.Mechanical
) derives JsonCodec:
  def ref: CritiqueRef = CritiqueRef(cited, missing)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def line: String =
    (s"paragraph \"$where\" names $named and cites [[$cited]] (${edges.mkString("; ")}) but not [[$missing]] — " +
      (if (mechanical).mechanical then s"cite [[$missing]] there (fixable)"
       else
         s"cite [[$missing]] there if the name means that work (code does not: the name is shared, or the article cites it nowhere else)"))

object MissedPair:

  type Unit = Unit.T
  object Unit extends Newtype[Int, Pure]
  export CritiquePair.Block.{From, To}
  type Where = Where.T
  object Where extends Newtype[String, Pure]
  type Edges = Edges.T
  object Edges extends Newtype[String, Pure]
  export Work.Names as Named
  enum Mechanical derives CanEqual:
    case Mechanical, Judged
  object Mechanical extends TwoStates[Mechanical]:
    extension (mechanical: Mechanical) def mechanical: Boolean = mechanical == Mechanical
