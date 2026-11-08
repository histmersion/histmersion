package com.histmersion
package domain.research

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.any.Pure

object Article:
  type Body = Body.T
  object Body extends Newtype[String, Pure]
  type Lead = Lead.T
  object Lead extends Newtype[String, Pure]
  type Note = Note.T
  object Note extends Newtype[String, Pure]
  type Page = Page.T
  object Page extends Newtype[String, Pure]
  type ReachSummary = ReachSummary.T
  object ReachSummary extends Newtype[String, Pure]
  type StrictMode = StrictMode.T
  object StrictMode extends Newtype[Boolean, Pure]
  type Kind = Kind.T
  object Kind extends Newtype[String, Pure]

  /** An article's stable identity across renames, merges and databases (`articles.uid`). */
  type Uid = Uid.T

  object Uid extends Newtype[java.util.UUID, Pure]
