package com.histmersion
package cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.api.BBox
import com.histmersion.config.*
import com.histmersion.api.library.{Filter, Sort}
import com.histmersion.api.catalogue.Year

/** The shared [[Filter]] flags (plan §1.0): ONE definition, on every command that takes the filter — `search`,
  * `facets`, `histogram`, `geo-when`, `article list`, `events search`. An object of its own so every command object can
  * reach it without depending on another's initialisation order.
  */
object FilterFlags:
  // what each flag means is FilterDocs' (the REST parameters and the MCP tool inputs read it too)
  private def many(p: Filter.Docs.Param) = Opts.options[String](p.rest.value, s"${p.doc} (repeatable)").orEmpty
  private def year(p: Filter.Docs.Param) = Opts.option[Long](p.rest.value, p.doc.value).orNone

  val opts: Opts[Filter] = (
    many(Filter.Docs.tags),
    many(Filter.Docs.authors),
    many(Filter.Docs.places),
    many(Filter.Docs.topics),
    many(Filter.Docs.status),
    Opts.option[Long]("year-from", Filter.Docs.yearFrom.doc.value).orNone,
    Opts.option[Long]("year-to", Filter.Docs.yearTo.doc.value).orNone,
    year(Filter.Docs.from),
    year(Filter.Docs.to),
    Opts.option[String]("bbox", "minLon,minLat,maxLon,maxLat (minLon > maxLon crosses the antimeridian)").orNone,
    Opts.option[String](Filter.Docs.sort.rest.value, Filter.Docs.sort.doc.value).orNone,
    Opts.option[Int](Filter.Docs.limit.rest.value, Filter.Docs.limit.doc.value).orNone,
    Opts.option[Int](Filter.Docs.offset.rest.value, Filter.Docs.offset.doc.value).orNone,
    Opts.option[String](Filter.Docs.title.rest.value, Filter.Docs.title.doc.value).orNone,
    Opts.option[String](Filter.Docs.goal.rest.value, Filter.Docs.goal.doc.value).orNone
  ).mapN {
    (tags, authors, places, topics, status, yearFrom, yearTo, from, to, bbox, sort, limit, offset, title, goal) =>
      val b = bbox.flatMap(s => BBox.parse(BBox.Text(s)))
      Filter.of(
        None,
        tags.map(Filter.Tags(_)),
        authors.map(Filter.Authors(_)),
        places.map(Filter.Places(_)),
        topics.map(Filter.Topics(_)),
        status.map(Filter.Status(_)),
        yearFrom.map(Year(_)),
        yearTo.map(Year(_)),
        from.map(Year(_)),
        to.map(Year(_)),
        b.map(_.minLon),
        b.map(_.minLat),
        b.map(_.maxLon),
        b.map(_.maxLat),
        sort.map(Sort.Wire(_)),
        limit.map(Filter.Limit(_)),
        offset.map(Filter.Offset(_)),
        title.map(Filter.Title(_)),
        goal.map(Filter.Goal(_))
      )
  }
