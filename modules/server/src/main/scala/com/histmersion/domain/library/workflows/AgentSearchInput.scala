package com.histmersion
package domain.library.workflows

import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.library.Filter
import com.histmersion.api.VocabularyCodecs.given
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Sort

/** `search` as an agent calls it: the filters steps use, mapped onto the same [[Filter]]. */
final case class AgentSearchInput(
    query: Option[Filter.Q] = None,
    topics: List[Filter.Topics] = Nil,
    tags: List[Filter.Tags] = Nil,
    authors: List[Filter.Authors] = Nil,
    status: List[Filter.Status] = Nil,
    from: Option[Year] = None,
    to: Option[Year] = None,
    sort: Option[Sort.Wire] = None,
    limit: Option[Filter.Limit] = None,
    offset: Option[Filter.Offset] = None
) derives JsonCodec:
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
