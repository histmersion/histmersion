package com.histmersion.server.agents

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.Agents.*
import com.histmersion.api.VocabularyCodecs.given
import io.circe.parser.decode
import io.circe.syntax.*
import sttp.ai.core.agent.*

import scala.jdk.CollectionConverters.*

/** What a person decided on a blocking approval step — the step's checkpoint. */
final case class Decided(approved: Boolean, approval: Long, note: Option[String] = None)
    derives hearth.kindlings.circederivation.KindlingsCodecAsObject
