package com.histmersion
package api.automation.managers

import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule
import com.histmersion.api.automation.Managers.ArticleExtend
import com.histmersion.api.automation.Managers.SourceEnrich
import com.histmersion.api.automation.Managers.ProposeArticles
import com.histmersion.api.automation.Managers.ArticleReview

enum EnqueueKind(val wire: EnqueueKind.Wire) derives CanEqual:
  case Review extends EnqueueKind(EnqueueKind.Wire("review"))
  case Extend extends EnqueueKind(EnqueueKind.Wire("extend"))
  case Enrich extends EnqueueKind(EnqueueKind.Wire("enrich"))
  case Seed extends EnqueueKind(EnqueueKind.Wire("seed"))

object EnqueueKind:
  type Wire = Wire.T
  object Wire extends Newtype[String, Pure]

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def parse(s: Wire): Option[EnqueueKind] = (s).value.trim.toLowerCase match
    case "" | "article" | ArticleReview       => Some(Review)
    case ArticleExtend                        => Some(Extend)
    case SourceEnrich | "source"              => Some(Enrich)
    case ProposeArticles | "propose" | "plan" => Some(Seed)
    case other                                => values.find(_.wire == Wire(other))
