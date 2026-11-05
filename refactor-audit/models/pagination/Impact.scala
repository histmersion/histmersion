import scalafix.v1._
import java.nio.file.{Files, Paths, Path}
import scala.jdk.CollectionConverters._
import metaconfig.{ConfDecoder, Configured, generic}

object Impact {
  case class Policy(review: String = "pagination/review-1.tsv", report: String = "pagination/impact-1")
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
}
class Impact(val policy: Impact.Policy) extends SyntacticRule("Impact") {
  def this() = this(Impact.Policy())
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("Impact")(Impact.Policy()).map(new Impact(_))
  override def afterComplete(): Unit = {
    val root = Paths.get("refactor-audit/models")
    val out = root.resolve(policy.report)
    def rows(p: Path) = Files.readAllLines(p).asScala.drop(1).map(_.split("\t", -1).toList).toList
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val sources = rows(root.resolve("sources.tsv"))
    val walk = Files.walk(Paths.get("modules").toAbsolutePath.normalize)
    val actual = try walk.iterator.asScala.filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala") &&
      (p.toString.contains("/src/main/") || p.toString.contains("/src/test/"))).map(_.toString).toSet finally walk.close()
    require(actual == sources.map(_.head).toSet,"Source universe changed; remeasure before impact analysis")
    sources.foreach(r => require(hash(Files.readString(Paths.get(r.head))) == r(1),"Stale source: " + r.head))
    val positions = rows(root.resolve("positions.tsv")).map(r => r.head -> r).toMap
    val reviewed = rows(root.resolve(policy.review))
    require(reviewed.nonEmpty && reviewed.map(_.head).distinct.size == reviewed.size && reviewed.forall(_(4) == "confirmed"))
    reviewed.foreach(r => require(hash(Files.readString(Paths.get(r(12)))) == r(13),"Stale review"))
    val symbols = reviewed.map(r => positions(r(1))(7)).toSet
    val owners = reviewed.map(r => positions(r(1))(6)).toSet
    require(owners.nonEmpty && owners.forall(_.endsWith("#")))
    val uses = rows(root.resolve("uses.tsv"))
    def scope(path: String) = if(path.contains("/src/test/")) "test" else "production"
    def module(path: String) = Paths.get("").toAbsolutePath.normalize.relativize(Paths.get(path)).toString.stripPrefix("modules/").split("/src/").head
    val specificOwners = owners.toList.sortBy(o => (-o.length,o))
    def ownerOf(target: String) = specificOwners.find(o => target.startsWith(o) || target.startsWith(o.stripSuffix("#") + "."))
    def category(target: String, owner: String): String =
      if(symbols(target)) "reviewed-member-reference"
      else if(target == owner) "record-type-reference"
      else if(target == owner.stripSuffix("#") + ".") "companion-reference"
      else if(target.startsWith(owner + "copy(") || target.startsWith(owner.stripSuffix("#") + ".apply(")) "construction-or-copy-reference"
      else "other-owner-member-reference"
    val selected = uses.flatMap(r => ownerOf(r.head).map(o => List(scope(r(1)),module(r(1)),category(r.head,o),o) ++ r))
    require(selected.nonEmpty && selected.exists(_(0) == "test"))
    val grouped = selected.groupBy(r => r.take(3)).toList.sortBy(_._1.mkString("\t")).map { case(k,rs) =>
      k ++ List(rs.size.toString,rs.map(_(5)).distinct.size.toString)
    }
    val direct = selected.filter(_(2) == "reviewed-member-reference")
    val referringOwners = direct.map(_(7)).filter(s => s.nonEmpty && !s.startsWith("local") && !s.contains("::local")).toSet
    val callerCandidates = uses.filter(r => referringOwners(r.head))
    Files.createDirectories(out)
    def frozen(name: String, content: String): Unit = {
      val p = out.resolve(name)
      if(Files.exists(p)) require(Files.readString(p) == content,"Frozen impact changed: " + name)
      else Files.writeString(p,content)
    }
    def table(name: String, header: String, data: List[List[String]]): Unit =
      frozen(name,(header :: data.map(_.mkString("\t")).sorted).mkString("\n") + "\n")
    table("references.tsv","scope\tmodule\tcategory\treviewed-record\ttarget\tpath\tline\towner\tsyntax",selected)
    table("counts.tsv","scope\tmodule\tcategory\treferences\tfiles",grouped)
    table("caller-candidates.tsv","target\tpath\tline\towner\tsyntax",callerCandidates)
    table("inputs.tsv","input\tsha256",List("sources.tsv","positions.tsv","uses.tsv",policy.review).map(n => List(n,hash(Files.readString(root.resolve(n))))))
    frozen("status.txt", "PASS: current complete source universe=" + sources.size + "\n" +
      "Reviewed record owners=" + owners.size + "; reference evidence=" + selected.size + "; files=" + selected.map(_(5)).distinct.size + "\n" +
      "Direct reviewed-member references=" + direct.size + "; one-hop caller candidates=" + callerCandidates.size + "\n" +
      "This is bounded reference impact, not complete dataflow or rewrite eligibility. Compiler synthetics, implicit conversions, generated defaults and transitive propagation require semantic AST analysis and compilation.\n" +
      "Reference rows are not additional confirmed primitive violations. No inventory refinement or application rewrite applied.\n")
    println(Files.readString(out.resolve("status.txt")))
  }
}
