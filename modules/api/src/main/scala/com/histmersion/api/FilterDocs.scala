package com.histmersion.api

import sttp.tapir.Schema
import com.histmersion.values.SourceStatus

/** [[Param.brief]] is the agents' wording, a few words: every turn of an agent step re-sends a tool's schema. */
object FilterDocs:

  final case class Param(rest: String, mcp: String, doc: String, brief: String)

  val query = Param(
    "q",
    "query",
    "Full text (websearch syntax: bare words AND-ed, \"phrase\", -exclude, or), evaluated within one ~4 000-char chunk",
    "Free text: words AND-ed, \"a phrase\", -word, or"
  )
  val tags = Param(
    "tag",
    "tags",
    "ALL required — every tag must be present on the source or one of its findings",
    "Every one required"
  )
  val authors = Param(
    "author",
    "authors",
    "ANY matches — a case-insensitive substring of an author's name",
    "Any one, a substring of an author's name"
  )
  val places = Param(
    "place",
    "places",
    "ANY matches — a place id from `places` matches by geometry and hierarchy, a name matches text",
    "Any one, a place id or name"
  )
  val topics =
    Param("topic", "topics", "ALL required — every topic must be present on the source", "Every one required")
  val status = Param(
    "status",
    "status",
    s"ANY matches — ${SourceStatus.values.map(_.wire).mkString("|")}",
    s"Any of ${SourceStatus.values.map(_.wire).mkString("|")}"
  )
  val yearFrom = Param("yearFrom", "yearFrom", "Earliest publication year (inclusive)", "Earliest publication year")
  val yearTo = Param("yearTo", "yearTo", "Latest publication year (inclusive)", "Latest publication year")
  val from = Param(
    "from",
    "from",
    "Deep-time start, a signed year (BCE negative: 1200 BCE is -1200)",
    "Period start, a signed year (BCE negative)"
  )
  val to = Param("to", "to", "Deep-time end, a signed year (BCE negative)", "Period end, a signed year")
  val minLon = Param(
    "minLon",
    "minLon",
    "Bounding box: west longitude (a west above the east crosses the antimeridian)",
    "Box: west longitude"
  )
  val minLat = Param("minLat", "minLat", "Bounding box: south latitude", "Box: south latitude")
  val maxLon = Param("maxLon", "maxLon", "Bounding box: east longitude", "Box: east longitude")
  val maxLat = Param("maxLat", "maxLat", "Bounding box: north latitude", "Box: north latitude")
  val sort = Param("sort", "sort", s"Ordering: ${Sort.all.mkString("|")}", Sort.all.mkString("|"))
  val limit =
    Param("limit", "limit", "Results per page (default: the configured page size, at most 500)", "Results per page")
  val offset = Param(
    "offset",
    "offset",
    "Skip this many results — the next page's offset is in the list's footer (`next: offset=N`)",
    "The next page's offset, from the listing's footer"
  )
  val title =
    Param("title", "title", "Case-insensitive substring of the listed item's title", "A substring of the title")
  val goal = Param("goal", "goal", "Only the sources a research goal links (its slug)", "A research goal's slug")

  val all: List[Param] =
    List(
      query,
      tags,
      authors,
      places,
      topics,
      status,
      yearFrom,
      yearTo,
      from,
      to,
      minLon,
      minLat,
      maxLon,
      maxLat,
      sort,
      limit,
      offset,
      title,
      goal
    )

  def describe[T](schema: Schema[T]): Schema[T] = describeWith(schema)(_.doc)

  def describeBrief[T](schema: Schema[T]): Schema[T] = describeWith(schema)(_.brief)

  private def describeWith[T](schema: Schema[T])(text: Param => String): Schema[T] =
    all.foldLeft(schema)((s, p) => s.modifyUnsafe[Any](p.mcp)(_.description(text(p))))
