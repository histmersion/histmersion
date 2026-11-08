package com.histmersion
package domain.research

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

object Article:
  type Body = Body.T
  object Body extends RefinedType[String, Pure]
  type Lead = Lead.T
  object Lead extends RefinedType[String, Pure]
  type Note = Note.T
  object Note extends RefinedType[String, Pure]
  type Page = Page.T
  object Page extends RefinedType[String, Pure]
  type ReachSummary = ReachSummary.T
  object ReachSummary extends RefinedType[String, Pure]
  type StrictMode = StrictMode.T
  object StrictMode extends RefinedType[Boolean, Pure]
  type Kind = Kind.T
  object Kind extends RefinedType[String, Pure]

  /** An article's stable identity across renames, merges and databases (`articles.uid`). */
  type Uid = Uid.T

  object Uid extends RefinedType[java.util.UUID, Pure]
