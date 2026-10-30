package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import io.circe.Codec
import sttp.tapir.Schema
import com.histmersion.values.SourceStatus

final case class ArticleFilter(
    query: Option[String] = None,
    topics: List[String] = Nil,
    tags: List[String] = Nil,
    status: List[String] = Nil,
    places: List[String] = Nil,
    authors: List[String] = Nil,
    from: Option[Long] = None,
    to: Option[Long] = None,
    extraQueries: List[String] = Nil
):
  def isEmpty: Boolean =
    query.forall(_.trim.isEmpty) && topics.isEmpty && tags.isEmpty && status.isEmpty && places.isEmpty &&
      authors.isEmpty && from.isEmpty && to.isEmpty

  def toFilter: Filter =
    Filter.of(
      q = query,
      tags = tags,
      authors = authors,
      places = places,
      topics = topics,
      status = status,
      from = from,
      to = to
    )

  def line: String =
    def list(xs: List[String]) = xs.map(ArticleFilter.quoted).mkString("[", ", ", "]")
    val parts = query.map(q => s"query: ${ArticleFilter.quoted(q)}").toList ++
      Option.when(topics.nonEmpty)(s"topics: ${list(topics)}") ++
      Option.when(tags.nonEmpty)(s"tags: ${list(tags)}") ++
      Option.when(status.nonEmpty)(s"status: ${list(status)}") ++
      Option.when(places.nonEmpty)(s"places: ${list(places)}") ++
      Option.when(authors.nonEmpty)(s"authors: ${list(authors)}") ++
      from.map(f => s"from: $f") ++ to.map(t => s"to: $t")
    s"search {${parts.mkString(", ")}}"

object ArticleFilter:
  given Schema[ArticleFilter] = KindlingsSchema
    .derived[ArticleFilter]
    .schema
    .description("The filter the article is compiled from; an empty one ({}) clears it")
    .modify(_.query)(_.description("Free text the sources must match (the search query)"))
    .modify(_.topics)(_.description("Topics the sources must all have"))
    .modify(_.tags)(_.description("Tags the sources must all have"))
    .modify(_.status)(_.description("Statuses, any of (current, contested, …)"))
    .modify(_.places)(_.description("Places, any of (a gazetteer id or a name)"))
    .modify(_.authors)(_.description("Authors, any of (a name substring)"))
    .modify(_.from)(_.description("Deep-time window start, signed year (BCE negative)"))
    .modify(_.to)(_.description("Deep-time window end, signed year (BCE negative)"))
    .modify(_.extraQueries)(
      _.description(
        "Further free-text searches the writer ran; reported in the Method note, not part of the set every source of which must be cited or given a reason"
      )
    )

  given Codec.AsObject[ArticleFilter] = StrictJson.codec(KindlingsCodecAsObject.derived[ArticleFilter])

  private[api] def quoted(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  def normalise(f: ArticleFilter): Either[String, Option[ArticleFilter]] =
    def clean(xs: List[String]) = xs.flatMap(_.split(",")).map(_.trim).filter(_.nonEmpty).distinct
    val status = clean(f.status).map(_.toLowerCase(java.util.Locale.ROOT))
    val unknown = status.filterNot(s => SourceStatus.values.exists(_.wire == s))
    val tidy = ArticleFilter(
      f.query.map(_.trim).filter(_.nonEmpty),
      clean(f.topics),
      clean(f.tags),
      status,
      clean(f.places),
      clean(f.authors),
      f.from,
      f.to,
      f.extraQueries.map(_.trim).filter(_.nonEmpty).distinct
    )
    if unknown.nonEmpty then
      Left(
        s"unknown status ${unknown.map(s => s"'$s'").mkString(", ")} — one of ${SourceStatus.values.map(_.wire).mkString(", ")}"
      )
    else if tidy.from.zip(tidy.to).exists(_ > _) then
      Left(s"the filter's from (${tidy.from.get}) is after its to (${tidy.to.get})")
    else if tidy.isEmpty && tidy.extraQueries.nonEmpty then
      Left("extraQueries without a filter: give the query, topics or tags the article is compiled from")
    else Right(Option.when(!tidy.isEmpty)(tidy))
