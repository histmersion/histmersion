package com.histmersion.api


import sttp.tapir.{Schema, SchemaType}
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema

/** [[FilterDocs]] (#1946): the filter is described once, and that description reaches a schema. */
class FilterDocsSuite extends munit.FunSuite:

  final case class Probe(query: Option[String] = None, tags: List[String] = Nil, other: Option[Int] = None)
      derives KindlingsCodecAsObject

  private def descriptions[T](s: Schema[T]): Map[String, Option[String]] = s.schemaType match
    case p: SchemaType.SProduct[T] @unchecked => p.fields.map(f => f.name.name -> f.schema.description).toMap
    case _                                   => Map.empty

  test("describe gives the fields the schema has their one description, and leaves the rest alone"):
    val s = FilterDocs.describe(KindlingsSchema.derived[Probe].schema)
    val d = descriptions(s)
    assertEquals(d("query"), Some(FilterDocs.query.doc))
    assertEquals(d("tags"), Some(FilterDocs.tags.doc))
    assertEquals(d("other"), None)
    assertEquals(descriptions(FilterDocs.describeBrief(KindlingsSchema.derived[Probe].schema))("tags"),
      Some(FilterDocs.tags.brief))

  test("every parameter is a REST filter parameter, named once, and says something"):
    val rest = List("q", "tag", "author", "place", "topic", "status", "yearFrom", "yearTo", "from", "to", "minLon",
      "minLat", "maxLon", "maxLat", "sort", "limit", "offset", "title", "goal")
    assertEquals(FilterDocs.all.map(_.rest), rest)
    assertEquals(FilterDocs.all.map(_.mcp).distinct.size, FilterDocs.all.size)
    assert(FilterDocs.all.forall(p => p.doc.nonEmpty && p.brief.nonEmpty && p.brief.length <= p.doc.length + 10))

  test("the paging text names the footer every list prints"):
    assert(FilterDocs.offset.doc.contains("next: offset=N"))
