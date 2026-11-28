package com.histmersion
package api.automation.managers

import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Doi
import com.histmersion.api.library.Title
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.AuthorName
import com.histmersion.api.library.Source
import com.histmersion.api.automation.Goal
import com.histmersion.api.automation.Managers.Rationale
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.tolerated
import com.histmersion.lint.LintRule

final case class OaWork(
    doi: Doi,
    title: Title = Title(""),
    year: Option[Year] = None,
    authors: List[AuthorName] = Nil,
    venue: Option[Source.Venue] = None,
    goals: List[Goal.Slug] = Nil,
    why: Rationale = Rationale(""),
    foundBy: Option[OaWork.FoundBy] = None,
    acquireText: Option[OaWork.AcquireText] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object OaWork:
  type FoundBy = FoundBy.T
  object FoundBy extends Newtype[String, Pure]
  type AcquireText = AcquireText.T
  @tolerated(LintRule.G9Prefix, "pending G9 (namespaces, not prefixes)")
  object AcquireText extends Newtype[String, Pure]
