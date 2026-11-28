package com.histmersion
package api.automation.agents

import hearth.kindlings.optics.syntax.*
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.given
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.automation.agents.StepInfo
import io.github.iltotore.iron.constraint.any.Pure

final case class MessageInfo(
    seq: StepInfo.Seq,
    n: MessageInfo.N,
    role: MessageInfo.Role,
    content: Option[MessageInfo.Content] = None,
    toolName: Option[MessageInfo.ToolName] = None,
    toolInput: Option[MessageInfo.ToolInput] = None,
    toolResult: Option[MessageInfo.ToolOutput] = None,
    latencyMs: Option[MessageInfo.LatencyMs] = None
) derives JsonCodec,
      KindlingsSchema,
      api.json.Schema

object MessageInfo:
  type N = N.T
  object N extends Newtype[Int, Pure]
  type Role = Role.T
  object Role extends Newtype[String, Pure]
  type Content = Content.T
  object Content extends Newtype[String, Pure]
  type ToolName = ToolName.T
  object ToolName extends Newtype[String, Pure]:
    given Display[T] = Display(_.value)
  type ToolInput = ToolInput.T
  object ToolInput extends Newtype[String, Pure]
  type ToolOutput = ToolOutput.T
  object ToolOutput extends Newtype[String, Pure]
  type LatencyMs = LatencyMs.T
  object LatencyMs extends Newtype[Int, Pure]
