import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths}
import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters._

object ClockAccessPreview {
  case class Owner(path: String = "", clock: String = "", reads: Int = 0, contextualClass: String = "", contextualMethod: String = "", bodyOnly: Boolean = false)
  object Owner {
    implicit val surface: generic.Surface[Owner] = generic.deriveSurface[Owner]
    implicit val decoder: ConfDecoder[Owner] = generic.deriveDecoder(Owner()).noTypos
  }
  case class Caller(path: String = "", symbol: String = "", clock: String = "", count: Int = 0)
  object Caller {
    implicit val surface: generic.Surface[Caller] = generic.deriveSurface[Caller]
    implicit val decoder: ConfDecoder[Caller] = generic.deriveDecoder(Caller()).noTypos
  }
  case class Policy(report: String = "", owners: List[Owner] = Nil, callers: List[Caller] = Nil)
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
  def hash(s: String): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
}
class ClockAccessPreview(val policy: ClockAccessPreview.Policy) extends SemanticRule("ClockAccessPreview") {
  def this() = this(ClockAccessPreview.Policy())
  import ClockAccessPreview._
  private val root = Paths.get("").toAbsolutePath.normalize
  private val seen = new ConcurrentLinkedQueue[(String, String)]()
  private val changes = new ConcurrentLinkedQueue[(String, String, String)]()
  private val shapes = new ConcurrentLinkedQueue[List[String]]()
  private val references = new ConcurrentLinkedQueue[List[String]]()
  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("ClockAccessPreview")(Policy()).map { p =>
      require(p.owners.nonEmpty && p.owners.map(_.path).distinct.size == p.owners.size)
      require(!Files.exists(Paths.get(p.report)), "Preserve prior preview")
      new ClockAccessPreview(p)
    }
  override def fix(implicit doc: SemanticDocument): Patch = {
    val input = doc.input match { case Input.File(p, _) => p.toNIO; case Input.VirtualFile(p, _) => Paths.get(p) }
    val path = root.relativize(input.toAbsolutePath.normalize).toString
    val source = doc.input.text
    seen.add(path -> hash(source))
    val edits = scala.collection.mutable.ListBuffer.empty[(Int, Int, String)]
    def edit(t: Tree, start: Int, end: Int, value: String, symbol: String): Unit = {
      edits += ((start, end, value))
      shapes.add(List(path, (t.pos.startLine + 1).toString, symbol, t.syntax, t.structure, value))
    }
    val applications = doc.tree.collect { case a: Term.Apply => a }
    def inDefault(t: Tree): Boolean = t.parent.exists {
      case p: Term.Param if p.default.nonEmpty => true
      case parent => inDefault(parent)
    }
    policy.owners.find(_.path == path).foreach { owner =>
      val reads = applications.filter(a => a.argClause.values.isEmpty &&
        Set("java/time/Instant#now().", "java/lang/System#currentTimeMillis().")(a.fun.symbol.value) &&
        (!owner.bodyOnly || !inDefault(a)))
      require(reads.size == owner.reads, "Unexpected global clock reads: " + path + " " + reads.size)
      reads.foreach { a =>
        val replacement = if(a.fun.symbol.value == "java/time/Instant#now().") a.fun.syntax + "(" + owner.clock + ")"
          else owner.clock + ".millis()"
        edit(a, a.pos.start, a.pos.end, replacement, a.fun.symbol.value)
      }
      if(owner.contextualClass.nonEmpty) {
        val classes = doc.tree.collect { case c: Defn.Class if c.name.value == owner.contextualClass => c }
        require(classes.size == 1 && classes.head.ctor.paramClauses.nonEmpty)
        val cls = classes.head
        require(!cls.ctor.paramClauses.flatMap(_.values).exists(_.name.value == owner.clock))
        edit(cls.ctor, cls.ctor.pos.end, cls.ctor.pos.end, "(using " + owner.clock + ": java.time.Clock)", cls.name.symbol.value)
      }
      if(owner.contextualMethod.nonEmpty) {
        val methods = doc.tree.collect { case d: Defn.Def if d.name.symbol.value == owner.contextualMethod => d }
        require(methods.size == 1, "Expected exact clock-forwarding method: " + owner.contextualMethod)
        val method = methods.head
        val clauses = method.paramClauseGroups.flatMap(_.paramClauses)
        require(clauses.nonEmpty && !clauses.flatMap(_.values).exists(_.name.value == owner.clock))
        edit(method, clauses.last.pos.end, clauses.last.pos.end, "(using " + owner.clock + ": java.time.Clock)", method.name.symbol.value)
      }
    }
    val configuredSymbols = policy.callers.map(_.symbol).toSet
    applications.filter(a => configuredSymbols(a.fun.symbol.value)).foreach { a =>
      val matched = policy.callers.filter(c => c.path == path && c.symbol == a.fun.symbol.value)
      require(matched.size == 1, "Unconfigured constructor caller: " + path + " " + a.fun.symbol.value)
      val caller = matched.head
      require(!a.parent.exists(_.isInstanceOf[Term.Apply]), "Review existing curried/contextual call")
      edit(a, a.pos.end, a.pos.end, "(using " + caller.clock + ")", a.fun.symbol.value)
      references.add(List(path, a.fun.symbol.value, (a.pos.startLine + 1).toString))
    }
    val ordered = edits.toList.sortBy(_._1)
    require(ordered.sliding(2).forall { case List(a,b) => a._2 <= b._1; case _ => true }, "Overlapping patches")
    if(ordered.nonEmpty) {
      val next = ordered.reverse.foldLeft(source) { case(s,(a,b,v)) => s.patch(a,v,b-a) }
      implicit val dialect: Dialect = dialects.Scala3
      next.parse[Source].get
      changes.add((path, source, next))
    }
    Patch.empty
  }
  override def afterComplete(): Unit = {
    val walk = Files.walk(Paths.get("modules"))
    val expected = try walk.iterator.asScala.filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala") &&
      (p.toString.contains("/src/main/") || p.toString.contains("/src/test/"))).map(_.toString).toSet finally walk.close()
    require(seen.asScala.map(_._1).toSet == expected && seen.size == expected.size, "Incomplete source traversal")
    seen.asScala.foreach { case(p,h) => require(hash(Files.readString(Paths.get(p))) == h, "Source changed during scan") }
    require(policy.owners.forall(o => expected(o.path)))
    policy.callers.foreach(c => require(references.asScala.count(r => r.head == c.path && r(1) == c.symbol) == c.count,
      "Constructor caller count differs: " + c.path + " " + c.symbol))
    val out = Paths.get(policy.report)
    require(!Files.exists(out)); Files.createDirectories(out)
    def write(name: String, data: List[String]): Unit = Files.write(out.resolve(name), data.asJava)
    def escape(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")
    val changed = changes.asScala.toList.sortBy(_._1)
    changed.foreach { case(p,b,n) => List("before" -> b, "sources" -> n).foreach { case(d,s) =>
      val dest = out.resolve(d).resolve(p); Files.createDirectories(dest.getParent); Files.writeString(dest,s)
    }}
    write("sources.tsv", "path\tsha256" :: seen.asScala.toList.sorted.map { case(p,h) => p + "\t" + h })
    write("plan.tsv", "path\tbefore-sha256\tafter-sha256" :: changed.map { case(p,b,n) => List(p,hash(b),hash(n)).mkString("\t") })
    write("shapes.tsv", "path\tline\tsymbol\tsource\tast\treplacement" :: shapes.asScala.toList.sortBy(r => (r.head,r(1).toInt)).map(_.map(escape).mkString("\t")))
    write("callers.tsv", "path\tsymbol\tline" :: references.asScala.toList.map(_.mkString("\t")).sorted)
    write("policy.txt", List(policy.toString))
    write("status.txt", List("PREVIEW_ONLY", "source-files=" + expected.size, "changed-files=" + changed.size,
      "clock-reads=" + policy.owners.map(_.reads).sum, "verified-fixes=0"))
    println("Clock preview: " + changed.size + " files, " + policy.owners.map(_.reads).sum + " reads; no source changed")
  }
}
