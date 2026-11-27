package com.histmersion
package api

import com.histmersion.testsupport.ComparedByValue.given

import sttp.tapir.{Schema, SchemaType}
import com.histmersion.util.json.JsonCodec
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import com.histmersion.api.library.Filter
import com.histmersion.lint.{tolerated, LintRule}

/** [[FilterDocs]] (#1946): the filter is described once, and that description reaches a schema. */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class FilterDocsSuite extends munit.FunSuite:

  final case class Probe(query: Option[String] = None, tags: List[String] = Nil, other: Option[Int] = None)
      derives JsonCodec

  private def descriptions[T](s: Schema[T]): Map[String, Option[String]] = s.schemaType match
    case p: SchemaType.SProduct[T] @unchecked => p.fields.map(f => f.name.name -> f.schema.description).toMap
    case _                                    => Map.empty

  test("describe gives the fields the schema has their one description, and leaves the rest alone"):
    val s = Filter.Docs.describe(KindlingsSchema.derived[Probe].schema)
    val d = descriptions(s)
    assertEquals(d("query"), Some(Filter.Docs.query.doc))
    assertEquals(d("tags"), Some(Filter.Docs.tags.doc))
    assertEquals(d("other"), None)
    assertEquals(
      descriptions(Filter.Docs.describeBrief(KindlingsSchema.derived[Probe].schema))("tags"),
      Some(Filter.Docs.tags.brief)
    )

  test("every parameter is a REST filter parameter, named once, and says something"):
    val rest = List(
      "q",
      "tag",
      "author",
      "place",
      "topic",
      "status",
      "yearFrom",
      "yearTo",
      "from",
      "to",
      "minLon",
      "minLat",
      "maxLon",
      "maxLat",
      "sort",
      "limit",
      "offset",
      "title",
      "goal"
    )
    assertEquals(Filter.Docs.all.map(_.rest), rest)
    assertEquals(Filter.Docs.all.map(_.mcp).distinct.size, Filter.Docs.all.size)
    assert(
      Filter.Docs.all.forall(p =>
        p.doc.value.nonEmpty && p.brief.value.nonEmpty && p.brief.value.length <= p.doc.value.length + 10
      )
    )

  test("the paging text names the footer every list prints"):
    assert(Filter.Docs.offset.doc.value.contains("next: offset=N"))
