import scalafix.v1._
import java.nio.file.{Files, Paths, Path}
import scala.jdk.CollectionConverters._
import metaconfig.{ConfDecoder, Configured, generic}

object Review {
  case class Mapping(concept: String = "", proposed: String = "", primitive: String = "", unit: String = "",
    positions: List[String] = Nil, evidence: String = "", treatment: String = "bulk-rewrite")
  object Mapping {
    implicit val surface: generic.Surface[Mapping] = generic.deriveSurface[Mapping]
    implicit val decoder: ConfDecoder[Mapping] = generic.deriveDecoder(Mapping()).noTypos
  }
  case class Policy(report: String = "refactor-audit/models/pagination", mappings: List[Mapping] = Nil, evidencePaths: List[String] = Nil)
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
}
class Review(val policy: Review.Policy) extends SyntacticRule("Review") {
  def this() = this(Review.Policy())
  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("Review")(Review.Policy()).map(new Review(_))
  override def afterComplete(): Unit = {
    val root = Paths.get("refactor-audit/models")
    val out = Paths.get(policy.report)
    def rows(p: Path) = Files.readAllLines(p).asScala.drop(1).map(_.split("\t", -1).toList).toList
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val positions = rows(root.resolve("positions.tsv")).map(r => r.head -> r).toMap
    val leaves = rows(root.resolve("leaves.tsv"))
    val sources = rows(root.resolve("sources.tsv")).map(r => r.head -> r(1)).toMap
    val queue = rows(root.resolve("pagination-review.tsv")).map(_.head).toSet
    case class Decision(position: String, route: String, concept: String, proposed: String, unit: String, treatment: String, evidence: String, primitive: String = "scala/Int#")
    def field(owner: String, name: String, concept: String, proposed: String, unit: String, treatment: String, evidence: String, optional: Boolean = false) =
      Decision("com/histmersion/api/" + owner + "#" + name + ".:constructor-parameter", if(optional) "value/argument[0]" else "value", concept, proposed, unit, treatment, evidence)
    val offsets = List("OffsetPage", "library/SearchResult", "library/GeoHitPage", "library/Filter").map(owner =>
      field(owner,"offset","row-offset","com.histmersion.api.Offset","rows skipped from zero","bulk-rewrite","OffsetPage arithmetic and Filter parameter documentation establish row position"))
    val next = List("library/SearchResult","library/GeoHitPage").map(owner =>
      field(owner,"next","row-offset","com.histmersion.api.Offset","rows skipped from zero","bulk-rewrite","Companion of method obtains next from OffsetPage.next",true)) :+
      Decision("com/histmersion/api/OffsetPage#next().:return","value/ByNameType[0]/argument[0]","row-offset","com.histmersion.api.Offset","rows skipped from zero","bulk-rewrite","Computed as offset + shown when another page exists")
    val pagination = offsets ++ next ++ List(
      field("OffsetPage","total","row-count","com.histmersion.api.RowCount","rows","bulk-rewrite","Total number of selected rows; zero is valid"),
      field("OffsetPage","shown","row-count","com.histmersion.api.RowCount","rows","bulk-rewrite","Number of returned rows; zero is valid; not a requested page size"),
      field("library/Filter","limit","requested-page-size","com.histmersion.api.PageSize.Request","requested rows or configured-default sentinel","manual/design","Zero requests configured default; server resolves default and cap before SQL; direct positive PageSize conversion changes behavior"),
      field("library/PassageHit","offset","text-offset","com.histmersion.api.library.PassageHit.Offset","UTF-16 code units in concatenated source text","targeted-rewrite","PassageFinder passes range.start used by String.substring; not a row offset")
    )
    val decisions = if (policy.mappings.isEmpty) pagination else policy.mappings.flatMap { m =>
      require(List(m.concept,m.proposed,m.primitive,m.unit,m.evidence).forall(_.nonEmpty) && m.positions.nonEmpty)
      require(Set("bulk-rewrite", "targeted-rewrite", "manual/design")(m.treatment))
      m.positions.map { position =>
        val found = leaves.filter(r => r.head == position && r(2) == "primitive" && r(3) == m.primitive)
        require(found.size == 1, "Expected one exact primitive leaf for reviewed position: " + position)
        Decision(position,found.head(1),m.concept,m.proposed,m.unit,m.treatment,m.evidence,m.primitive)
      }
    }
    require(decisions.nonEmpty && decisions.map(d => d.position -> d.route).distinct.size == decisions.size)
    if (policy.mappings.isEmpty) require(decisions.size == 11)
    val output = decisions.map { d =>
      val p = positions.getOrElse(d.position,sys.error("Missing reviewed position: " + d.position))
      require(p(3) == "production/member" && p(9) == "public")
      require(hash(Files.readString(Paths.get(p(1)))) == sources(p(1)),"Stale inventory: " + p(1))
      require(p(2) == "api", "Wire-purpose review cannot silently classify another layer: " + d.position)
      require(leaves.count(r => r.head == d.position && r(1) == d.route && r(2) == "primitive" && r(3) == d.primitive) == 1,"Missing or ambiguous primitive leaf: " + d.position + " / " + d.route)
      List(hash(d.position + "\t" + d.route),d.position,d.route,"production","confirmed",d.concept,d.proposed,d.unit,"api","Pure",d.treatment,"none: no concrete cross-entity misuse established",p(1),sources(p(1)),p(11),queue(d.position).toString,d.evidence).mkString("\t")
    }
    val header = "id\tposition\troute\tscope\tclassification\tconcept\tproposed-type\tunit\tlayer\tconstraint\ttreatment\tphantom-decision\tpath\tsource-sha256\tast\tin-pagination-queue\tevidence"
    val ledger = (header :: output.sorted).mkString("\n") + "\n"
    val file = out.resolve("review-1.tsv")
    Files.createDirectories(out)
    if(Files.exists(file)) require(Files.readString(file) == ledger,"Frozen review changed: preserve previous event and re-review")
    else Files.writeString(file,ledger)
    val evidencePaths = decisions.map(d => positions(d.position)(1)) ++ policy.evidencePaths ++ (if(policy.mappings.nonEmpty) Nil else List(
      "modules/values/src/main/scala/com/histmersion/PageSize.scala",
      "modules/values/src/main/scala/com/histmersion/Offset.scala",
      "modules/core/src/main/scala/com/histmersion/domain/PassageFinder.scala",
      "modules/server/src/main/scala/com/histmersion/domain/library/findings/FindingsControllers.scala"))
    val evidence = ("path\tsha256" :: evidencePaths.distinct.sorted.map(p => p + "\t" + hash(Files.readString(Paths.get(p))))).mkString("\n") + "\n"
    val evidenceFile = out.resolve("source-evidence.tsv")
    if(Files.exists(evidenceFile)) require(Files.readString(evidenceFile) == evidence,"Reviewed supporting evidence changed")
    else Files.writeString(evidenceFile,evidence)
    val missed = decisions.filterNot(d => queue(d.position))
    Files.writeString(out.resolve("verification.txt"),
      "PASS: " + decisions.size + " distinct confirmed production primitive leaves, source hashes and AST evidence verified.\n" +
      decisions.groupBy(_.treatment).toList.sortBy(_._1).map { case(k,v) => k + "=" + v.size }.mkString("; ") + "; concepts=" + decisions.map(_.concept).distinct.size + ". No fixes applied.\n" +
      "Legacy pagination discovery queue omitted " + missed.size + " reviewed positions; full primitive inventory contains all reviewed leaves.\n" +
      "Purpose-selected development review, not independent precision/recall evidence.\n")
    println(Files.readString(out.resolve("verification.txt")))
  }
}
