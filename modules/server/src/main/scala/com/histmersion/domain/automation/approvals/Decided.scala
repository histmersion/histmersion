package com.histmersion.domain.automation.approvals

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.api.VocabularyCodecs.given
import io.circe.parser.decode
import io.circe.syntax.*
import sttp.ai.core.agent.*

import scala.jdk.CollectionConverters.*

/** What a person decided on a blocking approval step — the step's checkpoint. */
final case class Decided(approved: Decided.Approved, approval: Decided.Approval, note: Option[Decided.Note] = None)
    derives hearth.kindlings.circederivation.KindlingsCodecAsObject

object Decided:
  type Approved = Approved.T
  object Approved extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Approval = Approval.T
  object Approval extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Note = Note.T
  object Note extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
