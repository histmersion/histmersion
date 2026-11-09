package com.histmersion
package domain.automation.agents

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

  def messages(
      h: ConversationHistory,
      latencies: Vector[AgentRepo.MessageRecord.LatencyMs],
      max: Int
  ): List[AgentRepo.MessageRecord] =
    def cut(s: String) = if s.length <= max then s else s.take(max) + s"… (${s.length - max} more chars)"
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
        AgentRepo.MessageRecord(
          AgentRepo.MessageRecord.N(n + 1),
          AgentRepo.MessageRecord.Role(role),
          content.map(c => AgentRepo.MessageRecord.Content(c)),
          tool.map(t => AgentRepo.MessageRecord.ToolName(t)),
          input.map(i => AgentRepo.MessageRecord.ToolInput(i)),
          result.map(r => AgentRepo.MessageRecord.ToolOutput(r)),
          ms
        )
      }
