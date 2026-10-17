package com.histmersion.cli

import cats.syntax.all.*
import com.monovore.decline.*
import com.histmersion.core.*

/** The shared [[Filter]] flags (plan §1.0): ONE definition, on every command that takes the filter —
  * `search`, `facets`, `histogram`, `geo-when`, `article list`, `events search`. An object of its own
  * so every command object can reach it without depending on another's initialisation order. */
object FilterFlags:
  val opts: Opts[Filter] = (
    Opts.options[String]("tag", "Tag that must be present (repeatable; ALL are required)").orEmpty,
    Opts.options[String]("author", "Author substring (repeatable; ANY matches)").orEmpty,
    Opts.options[String]("place", "Place/region name (repeatable; ANY matches)").orEmpty,
    Opts.options[String]("topic", "Topic that must be present (repeatable; ALL are required)").orEmpty,
    Opts.options[String]("status", s"Source status (repeatable; ANY matches)").orEmpty,
    Opts.option[Long]("year-from", "Earliest publication year").orNone,
    Opts.option[Long]("year-to", "Latest publication year").orNone,
    Opts.option[Long]("from", "Deep-time start (signed year, BCE negative)").orNone,
    Opts.option[Long]("to", "Deep-time end (signed year)").orNone,
    Opts.option[String]("bbox", "minLon,minLat,maxLon,maxLat (minLon > maxLon crosses the antimeridian)").orNone,
    Opts.option[String]("sort", s"Ordering: ${Sort.all.mkString("|")}").orNone,
    Opts.option[Int]("limit", "Max results").orNone,
    Opts.option[Int]("offset", "Skip this many results — the next page's offset is in the list's footer (`next: offset=N`)").orNone,
    Opts.option[String]("title", "Case-insensitive substring of the listed item's title").orNone
  ).mapN { (tags, authors, places, topics, status, yearFrom, yearTo, from, to, bbox, sort, limit, offset, title) =>
    val b = bbox.flatMap(BBox.parse)
    Filter.of(None, tags, authors, places, topics, status, yearFrom, yearTo, from, to,
      b.map(_.minLon), b.map(_.minLat), b.map(_.maxLon), b.map(_.maxLat), sort, limit, offset, title)
  }
