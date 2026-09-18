package histdb.core

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

/** Source dependency graph (plan §1.4): D3-ready node+link structure for visualizing the citation
  * and relationship network around a source. Returned by `/api/graph/{ck}?depth=N`. */

object EdgeKind:
  val References     = "references"
  val Challenges     = "challenges"
  val Refutes        = "refutes"
  val Updates        = "updates"
  val SupersededBy   = "superseded_by"
  val Supersedes     = "supersedes"
  val Confirms       = "confirms"
  val Cites          = "cites"
  val FoundationalFor = "foundational_for"
  val CompilationOf  = "compilation_of"
  val ReviewOf       = "review_of"
  val Verifies       = "verifies"
  val all: List[String] = List(References, Challenges, Refutes, Updates, SupersededBy,
    Supersedes, Confirms, Cites, FoundationalFor, CompilationOf, ReviewOf, Verifies)

final case class GraphNode(
    id: String,
    title: Option[String] = None,
    year: Option[Int] = None,
    status: Option[String] = None,
    depth: Int = 0
) derives KindlingsCodecAsObject

object GraphNode:
  given Schema[GraphNode] = KindlingsSchema.derived[GraphNode].schema

final case class GraphLink(
    source: String,
    target: String,
    kind: String
) derives KindlingsCodecAsObject

object GraphLink:
  given Schema[GraphLink] = KindlingsSchema.derived[GraphLink].schema

final case class SourceGraph(
    root: String,
    depth: Int,
    nodes: List[GraphNode],
    links: List[GraphLink]
) derives KindlingsCodecAsObject

object SourceGraph:
  given Schema[SourceGraph] = KindlingsSchema.derived[SourceGraph].schema
