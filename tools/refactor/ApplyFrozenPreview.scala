import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

object ApplyFrozenPreview {
  case class Policy(mode: String = "verify", plan: String = "refactor-audit/agent-storage/plan.tsv", preview: String = "target/agent-storage-preview", report: String = "refactor-audit/agent-storage/applied-verification.txt")
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
}
class ApplyFrozenPreview(val policy: ApplyFrozenPreview.Policy) extends SyntacticRule("ApplyFrozenPreview") {
  def this() = this(ApplyFrozenPreview.Policy())
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("ApplyFrozenPreview")(ApplyFrozenPreview.Policy()).map(new ApplyFrozenPreview(_))
  private implicit val dialect: Dialect = dialects.Scala3
  private def hash(s: String): String = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
  private val root = Paths.get("").toAbsolutePath.normalize
  private lazy val rows = {
    require(Set("apply", "verify")(policy.mode))
    val parsed = Files.readAllLines(Paths.get(policy.plan)).asScala.drop(1).map(_.split("\t", -1).toList).toList
    require(parsed.nonEmpty && parsed.forall(_.size == 3) && parsed.map(_.head).distinct.size == parsed.size)
    parsed.foreach { r =>
      val path = Paths.get(r.head)
      require((r.head.startsWith("modules/") || r.head == "build.sbt") && path.normalize.toString == r.head && !path.isAbsolute)
      val next = Files.readString(Paths.get(policy.preview).resolve(path))
      if(r.head == "build.sbt") { implicit val dialect: Dialect = dialects.Scala3.withAllowToplevelTerms(true); next.parse[Source].get }
      else next.parse[Source].get
      require(hash(next) == r(2), "Changed preview: " + path)
      if(policy.mode == "apply") require(if(r(1) == "new") !Files.exists(path) else Files.exists(path) && hash(Files.readString(path)) == r(1), "Stale plan: " + path)
      else require(Files.exists(path) && hash(Files.readString(path)) == r(2), "Applied content differs: " + path)
    }
    parsed
  }
  private val seen = java.util.concurrent.ConcurrentHashMap.newKeySet[String]()
  override def fix(implicit doc: SyntacticDocument): Patch = {
    val planned = rows
    if(policy.mode != "apply") return Patch.empty
    val file = doc.input match { case Input.File(p, _) => p.toNIO; case Input.VirtualFile(p, _) => Paths.get(p) }
    val path = root.relativize(file.toAbsolutePath.normalize).toString
    planned.find(r => r.head == path && r(1) != "new" && path != "build.sbt").map { r =>
      require(hash(doc.input.text) == r(1), "Source changed during apply: " + path)
      require(seen.add(path), "Duplicate source traversal")
      Patch.replaceTree(doc.tree, Files.readString(Paths.get(policy.preview, path)))
    }.getOrElse(Patch.empty)
  }
  override def afterComplete(): Unit = {
    if(policy.mode == "apply") {
      require(seen.asScala.toSet == rows.filter(r => r(1) != "new" && r.head != "build.sbt").map(_.head).toSet, "Incomplete plan traversal")
      rows.filter(_.head == "build.sbt").foreach { r =>
        val path = Paths.get(r.head)
        require(hash(Files.readString(path)) == r(1),"Build changed during application")
        Files.writeString(path,Files.readString(Paths.get(policy.preview,r.head)))
      }
      rows.filter(_(1) == "new").foreach { r =>
        val path = Paths.get(r.head); require(!Files.exists(path))
        Files.createDirectories(path.getParent)
        Files.writeString(path, Files.readString(Paths.get(policy.preview, r.head)))
      }
    } else {
      val out = Paths.get(policy.report); Files.createDirectories(out.getParent)
      Files.write(out, List("PASS", s"${rows.size} application files exactly match the frozen preview.").asJava)
    }
    println(s"${policy.mode}: ${rows.size} frozen preview files")
  }
}
