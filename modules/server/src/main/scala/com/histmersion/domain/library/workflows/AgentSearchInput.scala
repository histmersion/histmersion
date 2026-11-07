package com.histmersion.domain.library.workflows

import com.histmersion.api.library.Filter
import com.histmersion.api.VocabularyCodecs.given
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** `search` as an agent calls it: the filters steps use, mapped onto the same [[Filter]]. */
final case class AgentSearchInput(
    query: Option[AgentSearchInput.Query] = None,
    topics: List[AgentSearchInput.Topics] = Nil,
    tags: List[AgentSearchInput.Tags] = Nil,
    authors: List[AgentSearchInput.Authors] = Nil,
    status: List[AgentSearchInput.Status] = Nil,
    from: Option[AgentSearchInput.From] = None,
    to: Option[AgentSearchInput.To] = None,
    sort: Option[AgentSearchInput.Sort] = None,
    limit: Option[AgentSearchInput.Limit] = None,
    offset: Option[AgentSearchInput.Offset] = None
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
  type Query = com.histmersion.domain.library.search.Search.Query
  val Query: _root_.com.histmersion.domain.library.search.Search.Query.type = _root_.com.histmersion.domain.library.search.Search.Query
  type Topics = Topics.T
  object Topics extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Tags = Tags.T
  object Tags extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Authors = Authors.T
  object Authors extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Status = Status.T
  object Status extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type From = From.T
  object From extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type To = To.T
  object To extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Sort = Sort.T
  object Sort extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Limit = Limit.T
  object Limit extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Offset = com.histmersion.Offset
  val Offset: _root_.com.histmersion.Offset.type = _root_.com.histmersion.Offset

  given Schema[AgentSearchInput] = Filter.Docs.describeBrief(KindlingsSchema.derived[AgentSearchInput].schema)
