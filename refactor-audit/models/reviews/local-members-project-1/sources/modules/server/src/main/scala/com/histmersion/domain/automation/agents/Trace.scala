package com.histmersion.domain.automation.agents

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.domain.automation.agents.AgentRepo
import com.histmersion.api.VocabularyCodecs.given
import io.circe.parser.decode
import io.circe.syntax.*
import sttp.ai.core.agent.*

import scala.jdk.CollectionConverters.*

object Trace:
  type MessagesCutS = MessagesCutS.T
  object MessagesCutS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type MessagesLatencies = MessagesLatencies.T
  object MessagesLatencies extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MessagesMax = MessagesMax.T
  object MessagesMax extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def messages(h: ConversationHistory, latencies: Vector[com.histmersion.domain.automation.agents.Trace.MessagesLatencies], max: com.histmersion.domain.automation.agents.Trace.MessagesMax): List[AgentRepo.MessageRecord] =
    def cut(s: com.histmersion.domain.automation.agents.Trace.MessagesCutS) = if s.length <= max then s else s.take(max) + s"… (${s.length - max} more chars)"
    // an assistant entry closes one LLM call: hand it that call's latency, in order
    val lat = latencies.iterator
    h.entries.toList
      .flatMap {
        case ConversationEntry.UserPrompt(c) =>
          List(("user", Some(cut(c)), None, None, None, None))
        case ConversationEntry.AssistantResponse(c, calls) =>
          val ms = Option.when(lat.hasNext)(lat.next())
          ("assistant", Option(c).filter(_.nonEmpty).map(cut), None, None, None, ms) ::
            calls.toList.map(tc => ("tool-call", None, Some(tc.toolName), Some(cut(tc.input)), None, None))
        case ConversationEntry.ToolResult(_, name, result) =>
          List(("tool", None, Some(name), None, Some(cut(result)), None))
        case _ => Nil
      }
      .zipWithIndex
      .map { case ((role, content, tool, input, result, ms), n) =>
        AgentRepo.MessageRecord(n + 1, role, content, tool, input, result, ms)
      }
