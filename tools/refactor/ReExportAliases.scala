import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths, StandardOpenOption}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._
import java.util.concurrent.ConcurrentHashMap
import scala.meta.internal.{semanticdb => s}

/** G46: re-export, don't alias. A pair
  * {{{
  *   type X = a.B.C
  *   val X: a.B.C.type = a.B.C          // with or without `_root_.` prefixes
  * }}}
  * becomes `export a.B.C` when X is C, and `export a.B.{C as X}` when it renames; consecutive pairs from the same owner share
  * one `export a.B.{C, D as Y}`. Only plain pairs: no modifiers, no type parameters, no comment inside, both halves denoting
  * the same entity. A pair whose export the compiler or SymbolOracle rejects goes into `exclusions` and stays. */
object ReExportAliases {
  case class Config(exclusions: String = "", ledger: String = "", scope: List[String] = List(""), packageExports: Boolean = true, rewritten: String = "",
    // every alias pair (`type X = …` then `val X: ….type = …`) that stays, as `path TAB key TAB reason TAB alias`
    // (qualified-round.py adds them to the exclusions once the round is done)
    skipped: String = "",
    // pairs in the companion of a class, trait or enum are rewritten too (an export there can break a creator application
    // only through a typing cycle, which the compiler reports and qualified-round.py backs off)
    companionExports: Boolean = false,
    // `type X = Y; val X: Y.type = Y` where Y is in scope by an import or an enclosing object (not a member of the alias's
    // own object) becomes an export from the path of Y's owner
    scopeNames: Boolean = false,
    // `{scalaVersion}` is Scalafix's --scala-version: the build's (target/build-versions.properties)
    semanticdbRoots: List[String] = List("target/out/jvm/scala-{scalaVersion}", "target/out/sjs1/scala-{scalaVersion}", ".semanticdb"))
  object Config {
    implicit val surface: generic.Surface[Config] = generic.deriveSurface[Config]
    implicit val decoder: ConfDecoder[Config] = generic.deriveDecoder(Config()).noTypos
  }
  def relative(p: String): String = {
    val here = Paths.get("").toAbsolutePath.normalize
    val abs = Paths.get(p).toAbsolutePath.normalize
    if (abs.startsWith(here)) here.relativize(abs).toString else p
  }
  private val indexes = new ConcurrentHashMap[List[String], ConcurrentHashMap[String, s.SymbolInformation]]()
  /** The source file of every indexed symbol. */
  val uris = new ConcurrentHashMap[String, String]()
  /** Symbol information of every current SemanticDB document (md5 equal to the source on disk). */
  def index(roots: List[String]): ConcurrentHashMap[String, s.SymbolInformation] = indexes.computeIfAbsent(roots, r => {
    val infos = new ConcurrentHashMap[String, s.SymbolInformation]()
    val files = r.filter(x => Files.isDirectory(Paths.get(x))).flatMap { x =>
      val st = Files.walk(Paths.get(x)); try st.iterator.asScala.filter(p => p.toString.endsWith(".semanticdb") && Files.isRegularFile(p)).toList finally st.close()
    }
    def md5(u: String): String = { val p = Paths.get(u); if (!Files.isRegularFile(p)) "" else java.security.MessageDigest.getInstance("MD5").digest(Files.readAllBytes(p)).map(b => f"${b & 255}%02X").mkString }
    files.asJava.parallelStream().forEach { f =>
      s.TextDocuments.parseFrom(Files.readAllBytes(f)).documents.foreach { d =>
        if (md5(d.uri) == d.md5) d.symbols.foreach(i => if (!i.symbol.startsWith("local")) { infos.putIfAbsent(i.symbol, i); uris.putIfAbsent(i.symbol, d.uri) })
      }
    }
    infos
  })
  private val pinnedCache = new ConcurrentHashMap[List[String], Set[String]]()
  /** Keys of the values some project val has the singleton type of (`val X: a.B.type`), except the value halves of alias
    * pairs this rule rewrites (their target owner differs from their own). Such a value must stay a val: the backend crashes
    * on a singleton type of an export forwarder ("Error while emitting"). */
  /** With `known` (the value halves a previous pass really rewrote), only those are exempt from pinning. */
  def pinned(roots: List[String], scope: List[String], packageExports: Boolean, known: Option[Set[String]], companionExports: Boolean = false): Set[String] = pinnedCache.computeIfAbsent(roots ++ ("|" :: packageExports.toString :: companionExports.toString :: known.fold("-")(_.size.toString) :: scope), _ => {
    val r = roots
    val idx = index(r)
    def singleton(i: s.SymbolInformation): Option[String] = (i.signature match {
      case v: s.ValueSignature => Some(v.tpe)
      case m: s.MethodSignature if m.parameterLists.isEmpty => Some(m.returnType)
      case _ => None
    }).collect { case st: s.SingleType => st.symbol }
    idx.values.asScala.toList.flatMap { i =>
      singleton(i).filter { target =>
        val owner = Symbol(i.symbol).owner.value
        val inScope = scope.exists(p => Option(uris.get(i.symbol)).exists(_.startsWith(p)))
        val isPair = inScope && i.symbol.endsWith(".") && idx.containsKey(i.symbol.stripSuffix(".") + "#")
        // exempt only a pair this run rewrites: not a self-alias, not a cross-module package export
        val caseCompanion = Option(idx.get(owner.stripSuffix(".") + "#")).exists(c => c.kind == s.SymbolInformation.Kind.CLASS || c.kind == s.SymbolInformation.Kind.TRAIT)
        val rewritten = isPair && (companionExports || !caseCompanion) && key(Symbol(target).owner.value) != key(owner) && finalObjectIn(idx, i.symbol).exists { obj =>
          !(packageLevel(obj) && !(packageExports && Option(uris.get(obj)).exists(u => Option(uris.get(i.symbol)).exists(v => u.startsWith(moduleOf(v))))))
        }
        !(known match { case Some(k) => isPair && k(i.symbol); case None => rewritten })
      }.map(key)
    }.toSet
  })
  def finalObjectIn(idx: ConcurrentHashMap[String, s.SymbolInformation], sym: String, depth: Int = 0): Option[String] =
    if (depth > 8) None
    else Option(idx.get(sym)) match {
      case Some(i) if i.kind == s.SymbolInformation.Kind.OBJECT => Some(sym)
      case Some(i) => (i.signature match {
        case v: s.ValueSignature => Some(v.tpe)
        case m: s.MethodSignature if m.parameterLists.isEmpty => Some(m.returnType)
        case _ => None
      }) match {
        case Some(st: s.SingleType) => finalObjectIn(idx, st.symbol, depth + 1)
        case _ => None
      }
      case None => None
    }
  /** A top-level definition: owned by a package, or by a file's `<File>$package` object. */
  def packageLevel(sym: String): Boolean = { val o = Symbol(sym).owner.value; o.endsWith("/") || o.endsWith("$package.") }
  /** `modules/<m>/` or `modules/plugins/<m>/` of a source path. */
  def moduleOf(path: String): String = "^(modules/(?:plugins/|plugin-fixtures/)?[^/]+/)".r.findFirstIn(path).getOrElse(path)
  def key(sym: String): String = sym.replaceAll("\\(\\+?\\d*\\)\\.$", ".").replaceAll("[^/.#]+\\$package\\.", "").stripSuffix("#").stripSuffix(".")
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
      new ReExportAliases(cfg.copy(semanticdbRoots = cfg.semanticdbRoots.map(_.replace("{scalaVersion}", c.scalaVersion))), ex)
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
    def ownOwner(t: Tree, qual: Term.Ref): Boolean =
      ownOwnerSym(t, qual match { case Term.Select(_, n) => n.symbol.value; case n: Term.Name => n.symbol.value; case _ => "" })
    def ownOwnerSym(t: Tree, q: String): Boolean = {
      t.parent.flatMap(_.parent).flatMap(_.parent).exists {
        case o: Defn.Object => key(o.name.symbol.value) == key(q)
        case c: Defn.Class => key(c.name.symbol.value) == key(q)
        case c: Defn.Trait => key(c.name.symbol.value) == key(q)
        case e: Defn.Enum => key(e.name.symbol.value) == key(q)
        case _ => false
      }
    }
    val idx = index(config.semanticdbRoots)
    val packageExports = config.packageExports
    /** The object a value finally denotes: an alias of an alias (`val X: a.B.Y.type = a.B.Y` where `a.B.Y` is itself such a
      * val) is followed, because Scala 3 cannot re-export an export forwarder. None when the chain is not plain. */
    def finalObject(sym: String, depth: Int = 0): Option[String] =
      if (depth > 8) None
      else Option(idx.get(sym)) match {
        case Some(i) if i.kind == s.SymbolInformation.Kind.OBJECT => Some(sym)
        case Some(i) => (i.signature match {
          case v: s.ValueSignature => Some(v.tpe)
          case m: s.MethodSignature if m.parameterLists.isEmpty => Some(m.returnType)
          case _ => None
        }) match {
          case Some(st: s.SingleType) => finalObject(st.symbol, depth + 1)
          case _ => None
        }
        case None => None
      }
    def pathOf(sym: String): Option[String] = {
      val o = Symbol(sym).owner.value
      if (o.isEmpty || sym.contains("`") || o.contains("$package")) None
      else Some(o.stripSuffix("/").stripSuffix(".").replaceAll("[/.]", "."))
    }
    /** An export in the companion of a class suppresses the synthetic `apply` and the creator application `X(...)`
      * ("does not take parameters"). */
    def inCaseCompanion(t: Tree): Boolean = t.parent.flatMap(_.parent).flatMap(_.parent) match {
      case Some(o: Defn.Object) => o.parent.toList.flatMap(_.children).exists {
        case c: Defn.Class => c.name.value == o.name.value
        case c: Defn.Trait => c.name.value == o.name.value
        case c: Defn.Enum => c.name.value == o.name.value
        case _ => false
      }
      case _ => false
    }
    def pairOf(t: Defn.Type, v: Defn.Val): Option[Pair] = (t.body, v.rhs, v.decltpe, v.pats) match {
      case (tb @ Type.Select(tq, tn), vr @ Term.Select(_, vn), Some(Type.Singleton(ref)), List(Pat.Var(n)))
          if t.mods.isEmpty && v.mods.isEmpty && t.tparamClause.values.isEmpty && n.value == t.name.value &&
            strip(tb.syntax) == strip(vr.syntax) && strip(ref.syntax) == strip(vr.syntax) &&
            tn.symbol.value.nonEmpty && key(tn.symbol.value) == key(vn.symbol.value) &&
            !hasComment(t.pos.start, v.pos.end) && !excluded(path + "@" + t.pos.start) && !ownOwner(t, tq) && (config.companionExports || !inCaseCompanion(t)) =>
        finalObject(vn.symbol.value) match {
          // an export from a package (top-level newtypes) of another module crashes the backend ("Error while emitting"),
          // one from the module's own package can break creator applications of classes there: such pairs stay unless
          // `packageExports` allows the same-module ones
          case Some(obj) if packageLevel(obj) && !(packageExports && Option(uris.get(obj)).exists(_.startsWith(moduleOf(path)))) => None
          case Some(obj) if key(obj) == key(vn.symbol.value) => Some(Pair(t, v, strip(tq.syntax), tn.value, t.name.value))
          case Some(obj) => pathOf(obj).filterNot(o => ownOwnerSym(t, Symbol(obj).owner.value)).map(o => Pair(t, v, o, Symbol(obj).displayName, t.name.value))
          case None => None
        }
      // a name in scope by an import or an enclosing object: exported from the path of its owner
      case (tb: Type.Name, vr: Term.Name, Some(Type.Singleton(ref: Term.Name)), List(Pat.Var(n)))
          if config.scopeNames && t.mods.isEmpty && v.mods.isEmpty && t.tparamClause.values.isEmpty && n.value == t.name.value &&
            tb.value == vr.value && ref.value == vr.value && tb.symbol.value.nonEmpty && key(tb.symbol.value) == key(vr.symbol.value) &&
            !hasComment(t.pos.start, v.pos.end) && !excluded(path + "@" + t.pos.start) && !ownOwnerSym(t, Symbol(vr.symbol.value).owner.value) &&
            (config.companionExports || !inCaseCompanion(t)) =>
        finalObject(vr.symbol.value) match {
          case Some(obj) if packageLevel(obj) && !(packageExports && Option(uris.get(obj)).exists(_.startsWith(moduleOf(path)))) => None
          case Some(obj) => pathOf(obj).filterNot(o => ownOwnerSym(t, Symbol(obj).owner.value)).map(o => Pair(t, v, o, Symbol(obj).displayName, t.name.value))
          case None => None
        }
      case _ => None
    }
    /** Why a pair of the inventory's shape stays: pairOf's conditions in its order. */
    def keptBecause(t: Defn.Type, v: Defn.Val, pinnedVal: Boolean): String = (t.body, v.rhs, v.decltpe) match {
      case (tb @ Type.Select(tq, tn), vr @ Term.Select(_, vn), Some(Type.Singleton(ref))) =>
        if (t.mods.nonEmpty || v.mods.nonEmpty) "a modifier on the alias (an export carries none)"
        else if (t.tparamClause.values.nonEmpty) "the alias has type parameters"
        else if (!(strip(tb.syntax) == strip(vr.syntax) && strip(ref.syntax) == strip(vr.syntax))) "the type and the value spell different paths"
        else if (tn.symbol.value.isEmpty || key(tn.symbol.value) != key(vn.symbol.value)) "the type and the value denote different entities"
        else if (hasComment(t.pos.start, v.pos.end)) "a comment inside the pair"
        else if (ownOwner(t, tq)) "aliases a member of the object it stands in: Scala 3 cannot export that (\"already a member\")"
        else if (!config.companionExports && inCaseCompanion(t)) "in the companion of a class, trait or enum: an export there hides the synthetic apply"
        else if (pinnedVal) "a val elsewhere has this value's singleton type: that type of an export forwarder crashes the backend"
        else finalObject(vn.symbol.value) match {
          case Some(obj) if packageLevel(obj) => "a top-level definition of another module's package: exporting it crashes the backend"
          case Some(obj) if key(obj) != key(vn.symbol.value) && pathOf(obj).isEmpty => "an alias of a top-level or backquoted definition"
          case Some(_) => "the object it finally denotes is a member of the object the export would stand in"
          case None => "the value is no plain path to an object"
        }
      case (tb: Type.Name, vr: Term.Name, _) =>
        if (!config.scopeNames) "aliases a name in scope (scopeNames is off)"
        else if (vr.symbol.isNone || tb.symbol.isNone) "a name without a SemanticDB symbol"
        else if (ownOwnerSym(t, Symbol(vr.symbol.value).owner.value)) "aliases a member of its own object: Scala 3 cannot export that"
        else if (t.mods.nonEmpty || v.mods.nonEmpty) "a modifier on the alias (an export carries none)"
        else if (hasComment(t.pos.start, v.pos.end)) "a comment inside the pair"
        else if (!config.companionExports && inCaseCompanion(t)) "in the companion of a class, trait or enum: an export there hides the synthetic apply"
        else if (pinnedVal) "a val elsewhere has this value's singleton type: that type of an export forwarder crashes the backend"
        else finalObject(vr.symbol.value) match {
          case Some(obj) if packageLevel(obj) => "a top-level definition of another module's package: exporting it crashes the backend"
          case Some(obj) if pathOf(obj).isEmpty => "an alias of a top-level, local or backquoted definition"
          case Some(_) => "the object it finally denotes is a member of the object the export would stand in"
          case None => "the name is no plain path to an object (a local or a parameter)"
        }
      case _ => "not a plain path on both sides"
    }
    val skippedRows = List.newBuilder[String]
    val statLists: List[List[Stat]] = doc.tree.collect {
      case t: Template => t.body.stats
      case p: Pkg => p.body.stats
    }
    // pairs that stay (and other vals of a singleton type) must not point at a pair that becomes an export forwarder:
    // the backend crashes on a singleton type of a forwarder ("Error while emitting")
    val pinnedHere: Set[String] = ReExportAliases.pinned(config.semanticdbRoots, config.scope, config.packageExports,
      if (config.rewritten.nonEmpty && Files.isRegularFile(Paths.get(config.rewritten)))
        Some(Files.readAllLines(Paths.get(config.rewritten), UTF_8).asScala.map(_.trim).filter(_.nonEmpty).toSet) else None,
      config.companionExports)
    val rows = List.newBuilder[(Int, Int, String, String)]
    val patches = statLists.flatMap { stats =>
      // consecutive pairs, grouped while the owner stays the same and only blank space separates them
      val pairs = stats.zip(stats.drop(1)).flatMap {
        // the inventory's shape (GuidelineInventory G46): `type X = …` followed by `val X: ….type = …`
        case (t: Defn.Type, v: Defn.Val) if v.pats.size == 1 && (v.pats.head match { case Pat.Var(n) => n.value == t.name.value; case _ => false }) &&
            v.decltpe.exists(_.is[Type.Singleton]) =>
          val pin = pinnedHere(key(v.pats.collectFirst { case Pat.Var(n) => n.symbol.value }.getOrElse("")))
          val p = if (pin) None else pairOf(t, v)
          if (p.isEmpty && config.skipped.nonEmpty && !excluded(path + "@" + t.pos.start))
            skippedRows += List(path, path + "@" + t.pos.start, keptBecause(t, v, pin), t.syntax.replace("\t", " ").take(120)).mkString("\t")
          p
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
        rows += ((start, end, line, g.flatMap(p => p.v.pats.collect { case Pat.Var(n) => n.symbol.value }).mkString(",")))
        val toks = doc.tokens.filter(t => t.start >= start && t.end <= end && t.start < end)
        (Patch.removeTokens(toks) + Patch.addLeft(toks.head, line)).atomic
      }
    }
    if (config.ledger.nonEmpty) {
      var delta = 0
      val out = rows.result().sortBy(_._1).map { case (st, en, line, syms) =>
        val ns = st + delta
        delta += line.length - (en - st)
        List(path, "opaque", st, en, ns, ns + line.length, path + "@" + st, line, syms).mkString("\t")
      }
      if (out.nonEmpty) lock.synchronized {
        Files.write(Paths.get(config.ledger), out.map(_ + "\n").mkString.getBytes(UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND)
      }
    }
    val kept = skippedRows.result()
    if (config.skipped.nonEmpty && kept.nonEmpty) lock.synchronized {
      Files.write(Paths.get(config.skipped), kept.map(_ + "\n").mkString.getBytes(UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }
    patches.asPatch
  }
}
