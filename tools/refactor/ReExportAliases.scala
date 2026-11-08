import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths, StandardOpenOption}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._

/** G46: re-export, don't alias. A pair
  * {{{
  *   type X = a.B.C
  *   val X: a.B.C.type = a.B.C          // with or without `_root_.` prefixes
  * }}}
  * becomes `export a.B.C` when X is C, and `export a.B.{C as X}` when it renames; consecutive pairs from the same owner share
  * one `export a.B.{C, D as Y}`. Only plain pairs: no modifiers, no type parameters, no comment inside, both halves denoting
  * the same entity. A pair whose export the compiler or SymbolOracle rejects goes into `exclusions` and stays. */
object ReExportAliases {
  case class Config(exclusions: String = "", ledger: String = "")
  object Config {
    implicit val surface: generic.Surface[Config] = generic.deriveSurface[Config]
    implicit val decoder: ConfDecoder[Config] = generic.deriveDecoder(Config()).noTypos
  }
  def relative(p: String): String = {
    val here = Paths.get("").toAbsolutePath.normalize
    val abs = Paths.get(p).toAbsolutePath.normalize
    if (abs.startsWith(here)) here.relativize(abs).toString else p
  }
  def key(sym: String): String = sym.replaceAll("[^/.#]+\\$package\\.", "").stripSuffix("#").stripSuffix(".")
}

class ReExportAliases(config: ReExportAliases.Config, excluded: Set[String]) extends SemanticRule("ReExportAliases") {
  import ReExportAliases._
  def this() = this(ReExportAliases.Config(), Set.empty)

  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("ReExportAliases")(Config()).map { cfg =>
      val ex = if (cfg.exclusions.nonEmpty && Files.isRegularFile(Paths.get(cfg.exclusions)))
        Files.readAllLines(Paths.get(cfg.exclusions), UTF_8).asScala.map(_.split("\t").head.trim).filter(l => l.nonEmpty && !l.startsWith("#")).toSet
      else Set.empty[String]
      if (cfg.ledger.nonEmpty) Option(Paths.get(cfg.ledger).getParent).foreach(Files.createDirectories(_))
      new ReExportAliases(cfg, ex)
    }
  private val lock = new Object

  /** A matched pair: the owner path (without `_root_.`), the exported name, the alias name. */
  final case class Pair(t: Defn.Type, v: Defn.Val, owner: String, exported: String, alias: String)

  override def fix(implicit doc: SemanticDocument): Patch = {
    val path = relative(doc.input match { case Input.File(p, _) => p.toString; case Input.VirtualFile(p, _) => p; case other => other.toString })
    val text = doc.input.text
    def strip(s: String): String = s.replace("_root_.", "")
    def hasComment(start: Int, end: Int): Boolean = doc.tokens.exists(t => t.is[Token.Comment] && t.start >= start && t.end <= end)
    /** Scala 3 refuses to export a member of the object the export stands in ("already a member"): such pairs stay. */
    def ownOwner(t: Tree, qual: Term.Ref): Boolean = {
      val q = qual match { case Term.Select(_, n) => n.symbol.value; case n: Term.Name => n.symbol.value; case _ => "" }
      t.parent.flatMap(_.parent).flatMap(_.parent).exists {
        case o: Defn.Object => key(o.name.symbol.value) == key(q)
        case c: Defn.Class => key(c.name.symbol.value) == key(q)
        case c: Defn.Trait => key(c.name.symbol.value) == key(q)
        case e: Defn.Enum => key(e.name.symbol.value) == key(q)
        case _ => false
      }
    }
    def pairOf(t: Defn.Type, v: Defn.Val): Option[Pair] = (t.body, v.rhs, v.decltpe, v.pats) match {
      case (tb @ Type.Select(tq, tn), vr @ Term.Select(_, vn), Some(Type.Singleton(ref)), List(Pat.Var(n)))
          if t.mods.isEmpty && v.mods.isEmpty && t.tparamClause.values.isEmpty && n.value == t.name.value &&
            strip(tb.syntax) == strip(vr.syntax) && strip(ref.syntax) == strip(vr.syntax) &&
            tn.symbol.value.nonEmpty && key(tn.symbol.value) == key(vn.symbol.value) &&
            !hasComment(t.pos.start, v.pos.end) && !excluded(path + "@" + t.pos.start) && !ownOwner(t, tq) =>
        Some(Pair(t, v, strip(tq.syntax), tn.value, t.name.value))
      case _ => None
    }
    val statLists: List[List[Stat]] = doc.tree.collect {
      case t: Template => t.body.stats
      case p: Pkg => p.body.stats
    }
    val rows = List.newBuilder[(Int, Int, String)]
    val patches = statLists.flatMap { stats =>
      // consecutive pairs, grouped while the owner stays the same and only blank space separates them
      val pairs = stats.zip(stats.drop(1)).flatMap {
        case (t: Defn.Type, v: Defn.Val) => pairOf(t, v)
        case _ => None
      }
      val groups = pairs.foldLeft(List.empty[List[Pair]]) {
        case ((g @ (last :: _)) :: rest, p) if last.owner == p.owner && !hasComment(last.v.pos.end, p.t.pos.start) &&
            text.substring(last.v.pos.end, p.t.pos.start).forall(_.isWhitespace) && (stats.indexOf(p.t) == stats.indexOf(last.v) + 1) => (p :: g) :: rest
        case (acc, p) => List(p) :: acc
      }.map(_.reverse).reverse
      groups.map { g =>
        val sels = g.map(p => if (p.exported == p.alias) p.exported else s"${p.exported} as ${p.alias}")
        val line = if (sels.size == 1 && g.head.exported == g.head.alias) s"export ${g.head.owner}.${sels.head}" else s"export ${g.head.owner}.{${sels.mkString(", ")}}"
        val start = g.head.t.pos.start
        val end = g.last.v.pos.end
        rows += ((start, end, line))
        val toks = doc.tokens.filter(t => t.start >= start && t.end <= end && t.start < end)
        (Patch.removeTokens(toks) + Patch.addLeft(toks.head, line)).atomic
      }
    }
    if (config.ledger.nonEmpty) {
      var delta = 0
      val out = rows.result().sortBy(_._1).map { case (st, en, line) =>
        val ns = st + delta
        delta += line.length - (en - st)
        List(path, "opaque", st, en, ns, ns + line.length, path + "@" + st, line).mkString("\t")
      }
      if (out.nonEmpty) lock.synchronized {
        Files.write(Paths.get(config.ledger), out.map(_ + "\n").mkString.getBytes(UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND)
      }
    }
    patches.asPatch
  }
}
