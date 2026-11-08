package com.histmersion
package domain.automation.approvals

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.api.VocabularyCodecs.given
import io.circe.parser.decode
import io.circe.syntax.*
import sttp.ai.core.agent.*

import scala.jdk.CollectionConverters.*
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** What a person decided on a blocking approval step — the step's checkpoint. */
final case class Decided(approved: Decided.Approved, approval: Decided.Approval, note: Option[Decided.Note] = None)
    derives hearth.kindlings.circederivation.KindlingsCodecAsObject

object Decided:
  type Approved = Approved.T
  object Approved extends RefinedType[Boolean, Pure]
  type Approval = Approval.T
  object Approval extends RefinedType[Long, Pure]
  type Note = Note.T
  object Note extends RefinedType[String, Pure]
