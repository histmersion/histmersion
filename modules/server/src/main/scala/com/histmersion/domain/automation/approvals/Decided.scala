package com.histmersion
package domain.automation.approvals

import com.histmersion.util.json.JsonCodec
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.api.VocabularyCodecs.given
import sttp.ai.core.agent.*

import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.lint.{LintRule, tolerated}

/** What a person decided on a blocking approval step — the step's checkpoint. */
@tolerated(LintRule.Layer, "pending G7 (separate types per layer)")
final case class Decided(approved: Decided.Approved, approval: Decided.Approval, note: Option[Decided.Note] = None)
    derives JsonCodec

object Decided:
  type Approved = Approved.T
  object Approved extends Newtype[Boolean, Pure]
  type Approval = Approval.T
  object Approval extends Newtype[Long, Pure]
  type Note = Note.T
  object Note extends Newtype[String, Pure]
