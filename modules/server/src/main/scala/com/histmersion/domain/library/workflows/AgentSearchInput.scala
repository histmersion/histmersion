package com.histmersion.domain.library.workflows

import com.histmersion.api.library.Filter
import com.histmersion.api.VocabularyCodecs.given
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `search` as an agent calls it: the filters steps use, mapped onto the same [[Filter]]. */
final case class AgentSearchInput(
    query: Option[String] = None,
    topics: List[String] = Nil,
    tags: List[String] = Nil,
    authors: List[String] = Nil,
    status: List[String] = Nil,
    from: Option[Long] = None,
    to: Option[Long] = None,
    sort: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives KindlingsCodecAsObject:
  def toFilter: Filter =
    Filter.of(
      query,
      tags,
      authors,
      Nil,
      topics,
      status,
      None,
      None,
      from,
      to,
      None,
      None,
      None,
      None,
      sort,
      limit,
      offset
    )
object AgentSearchInput:
  // the agent's few words per filter field are FilterDocs' `brief` (the MCP tool and the CLI read its `doc`)
  given Schema[AgentSearchInput] = Filter.Docs.describeBrief(KindlingsSchema.derived[AgentSearchInput].schema)
