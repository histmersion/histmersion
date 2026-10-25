package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.api.*
import com.histmersion.config.*

/** The shared [[Filter]] flags (plan §1.0): ONE definition, on every command that takes the filter —
  * `search`, `facets`, `histogram`, `geo-when`, `article list`, `events search`. An object of its own
  * so every command object can reach it without depending on another's initialisation order. */
object FilterFlags:
  // what each flag means is FilterDocs' (the REST parameters and the MCP tool inputs read it too)
  private def many(p: FilterDocs.Param) = Opts.options[String](p.rest, s"${p.doc} (repeatable)").orEmpty
  private def year(p: FilterDocs.Param) = Opts.option[Long](p.rest, p.doc).orNone

  val opts: Opts[Filter] = (
    many(FilterDocs.tags),
    many(FilterDocs.authors),
    many(FilterDocs.places),
    many(FilterDocs.topics),
    many(FilterDocs.status),
    Opts.option[Long]("year-from", FilterDocs.yearFrom.doc).orNone,
    Opts.option[Long]("year-to", FilterDocs.yearTo.doc).orNone,
    year(FilterDocs.from),
    year(FilterDocs.to),
    Opts.option[String]("bbox", "minLon,minLat,maxLon,maxLat (minLon > maxLon crosses the antimeridian)").orNone,
    Opts.option[String](FilterDocs.sort.rest, FilterDocs.sort.doc).orNone,
    Opts.option[Int](FilterDocs.limit.rest, FilterDocs.limit.doc).orNone,
    Opts.option[Int](FilterDocs.offset.rest, FilterDocs.offset.doc).orNone,
    Opts.option[String](FilterDocs.title.rest, FilterDocs.title.doc).orNone
  ).mapN { (tags, authors, places, topics, status, yearFrom, yearTo, from, to, bbox, sort, limit, offset, title) =>
    val b = bbox.flatMap(BBox.parse)
    Filter.of(None, tags, authors, places, topics, status, yearFrom, yearTo, from, to,
      b.map(_.minLon), b.map(_.minLat), b.map(_.maxLon), b.map(_.maxLat), sort, limit, offset, title)
  }
