import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

object AssertionObservationRewrite {
  case class Entry(path: String, before: String, after: String, count: Int = 1)
  object Entry {
    implicit val surface: generic.Surface[Entry] = generic.deriveSurface[Entry]
    implicit val decoder: ConfDecoder[Entry] = generic.deriveDecoder(Entry("","","")).noTypos
  }
  case class Policy(sourceHashes: String = "", report: String = "", entries: List[Entry] = Nil)
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
}
class AssertionObservationRewrite(val policy: AssertionObservationRewrite.Policy) extends SyntacticRule("AssertionObservationRewrite") {
  def this() = this(AssertionObservationRewrite.Policy())
  private implicit val dialect: Dialect = dialects.Scala3
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("AssertionObservationRewrite")(AssertionObservationRewrite.Policy()).map(new AssertionObservationRewrite(_))
  private def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
  private lazy val frozen = {
    require(policy.entries.nonEmpty && policy.entries.forall(e => e.path.contains("/src/test/") && e.count > 0))
    require(!Files.exists(Paths.get(policy.report)))
    Files.readAllLines(Paths.get(policy.sourceHashes)).asScala.drop(1).map(_.split("\t")).map(a => a(0) -> a(1)).toMap
  }
  private val seen = scala.collection.mutable.Set.empty[String]
  override def fix(implicit doc: SyntacticDocument): Patch = {
    val input = doc.input match { case Input.File(p,_) => p.toNIO; case Input.VirtualFile(p,_) => Paths.get(p) }
    val path = Paths.get("").toAbsolutePath.relativize(input.toAbsolutePath).toString
    val entries = policy.entries.filter(_.path == path)
    if(entries.isEmpty) return Patch.empty
    require(hash(doc.input.text) == frozen(path), "Changed test source: " + path)
    require(seen.add(path))
    val observations = doc.tree.collect { case a: Term.Apply if a.fun.syntax == "assertEquals" && a.argClause.values.size >= 2 => a.argClause.values.head }
    val matches = entries.flatMap { e =>
      val shape = e.before.parse[Term].get.structure; e.after.parse[Term].get
      val ts = observations.flatMap(_.collect { case t: Term if t.structure == shape => t })
      require(ts.size == e.count,"Unexpected assertion count: " + e.before)
      ts.map(t => t -> e.after)
    }
    val sorted = matches.sortBy(_._1.pos.start)
    require(sorted.sliding(2).forall { case List(a,b) => a._1.pos.end <= b._1.pos.start; case _ => true })
    val out = Paths.get(policy.report,"before",path); Files.createDirectories(out.getParent); Files.writeString(out,doc.input.text)
    matches.map { case(t,s) => Patch.replaceTree(t,s) }.asPatch.atomic
  }
  override def afterComplete(): Unit = {
    require(seen.toSet == policy.entries.map(_.path).toSet,"Incomplete test traversal")
    seen.foreach { path => val out = Paths.get(policy.report,"after",path); Files.createDirectories(out.getParent); Files.writeString(out,Files.readString(Paths.get(path))) }
    Files.writeString(Paths.get(policy.report,"status.txt"),s"${seen.size} test files; expected arguments untouched; runtime verification pending.\n")
  }
}
