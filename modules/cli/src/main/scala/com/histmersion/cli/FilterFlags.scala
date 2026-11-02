package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.api.BBox
import com.histmersion.config.*
import com.histmersion.api.library.Filter

/** The shared [[Filter]] flags (plan §1.0): ONE definition, on every command that takes the filter — `search`,
  * `facets`, `histogram`, `geo-when`, `article list`, `events search`. An object of its own so every command object can
  * reach it without depending on another's initialisation order.
  */
object FilterFlags:
  // what each flag means is FilterDocs' (the REST parameters and the MCP tool inputs read it too)
  private def many(p: Filter.Docs.Param) = Opts.options[String](p.rest, s"${p.doc} (repeatable)").orEmpty
  private def year(p: Filter.Docs.Param) = Opts.option[Long](p.rest, p.doc).orNone

  val opts: Opts[Filter] = (
    many(Filter.Docs.tags),
    many(Filter.Docs.authors),
    many(Filter.Docs.places),
    many(Filter.Docs.topics),
    many(Filter.Docs.status),
    Opts.option[Long]("year-from", Filter.Docs.yearFrom.doc).orNone,
    Opts.option[Long]("year-to", Filter.Docs.yearTo.doc).orNone,
    year(Filter.Docs.from),
    year(Filter.Docs.to),
    Opts.option[String]("bbox", "minLon,minLat,maxLon,maxLat (minLon > maxLon crosses the antimeridian)").orNone,
    Opts.option[String](Filter.Docs.sort.rest, Filter.Docs.sort.doc).orNone,
    Opts.option[Int](Filter.Docs.limit.rest, Filter.Docs.limit.doc).orNone,
    Opts.option[Int](Filter.Docs.offset.rest, Filter.Docs.offset.doc).orNone,
    Opts.option[String](Filter.Docs.title.rest, Filter.Docs.title.doc).orNone,
    Opts.option[String](Filter.Docs.goal.rest, Filter.Docs.goal.doc).orNone
  ).mapN {
    (tags, authors, places, topics, status, yearFrom, yearTo, from, to, bbox, sort, limit, offset, title, goal) =>
      val b = bbox.flatMap(BBox.parse)
      Filter.of(
        None,
        tags,
        authors,
        places,
        topics,
        status,
        yearFrom,
        yearTo,
        from,
        to,
        b.map(_.minLon),
        b.map(_.minLat),
        b.map(_.maxLon),
        b.map(_.maxLat),
        sort,
        limit,
        offset,
        title,
        goal
      )
  }
