import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths, StandardOpenOption}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._
import java.util.concurrent.ConcurrentHashMap
import scala.meta.internal.{semanticdb => s}

/** G47: no fully-qualified noise.
  *  1. a single `package com.histmersion.x.y` clause becomes `package com.histmersion` / `package x.y`;
  *  2. `_root_.` goes where the rest of the path still resolves to the same symbol;
  *  3. a path rooted at `_root_` or at a project package (`com.histmersion.a.B.C`) gets the shortest spelling that resolves to the
  *     SAME symbol in its scope, adding an import of the top-level symbol when that is the shortest; with `packagePrefixes`,
  *     a path no name or import can spell keeps the shortest suffix headed by a project subpackage in scope;
  *  4. inside `T` or its companion, `T.Member` (or `a.b.T.Member`) loses the qualifier where `Member` resolves to the same symbol.
  * Resolution is a conservative model of Scala 3 scoping (locals, parameters, template members with inherited ones from the
  * project-wide SemanticDB index, imports, package members, the root imports); the compiler and a SemanticDB comparison of
  * every occurrence before and after (tools/refactor/SymbolOracle.scala) are the final check, and what they reject is listed in
  * `exclusions` and left as it is. Every edit is written to `ledger` with its old and new offsets. */
object ShortenQualified {
  case class Config(
    exclusions: String = "",
    ledger: String = "",
    // `{scalaVersion}` is Scalafix's --scala-version: the build's (target/build-versions.properties)
    semanticdbRoots: List[String] = List("target/out/jvm/scala-{scalaVersion}", "target/out/sjs1/scala-{scalaVersion}", ".semanticdb"),
    projectPackage: String = "com/histmersion/",
    chainPackages: Boolean = true,
    shorten: Boolean = true,
    addImports: Boolean = true,
    // a path rooted at `_root_` or at the project package that neither a shorter name nor an import can spell (a same-named
    // type of another layer is in scope: `api.library.Source` in a `domain.library` file) keeps the shortest suffix that
    // starts at a project subpackage the (chained) package clause puts in scope, e.g. `api.library.Source`
    packagePrefixes: Boolean = false,
    // where a path rooted at `_root_` or at the project package keeps that spelling, a row `path TAB key TAB reason TAB
    // spelling` (qualified-round.py adds them to the exclusions once the round is done)
    skipped: String = ""
  )
  object Config {
    implicit val surface: generic.Surface[Config] = generic.deriveSurface[Config]
    implicit val decoder: ConfDecoder[Config] = generic.deriveDecoder(Config()).noTypos
  }

  /** What the project-wide SemanticDB knows: symbol information, and the members of every package by name. */
  final class Index(val infos: ConcurrentHashMap[String, s.SymbolInformation], val packageMembers: Map[String, Map[String, Set[String]]], val packages: Set[String]) {
    /** Every indexed symbol by owner: export forwarders are members SemanticDB lists in no class declarations. */
    lazy val children: Map[String, List[(String, String)]] = infos.values.asScala.toList.map(i => (ownerOf(i.symbol), (i.displayName, i.symbol))).groupBy(_._1).map { case (k, v) => k -> v.map(_._2) }
  }
  private val indexes = new ConcurrentHashMap[List[String], Index]()
  def index(roots: List[String]): Index = indexes.computeIfAbsent(roots, r => buildIndex(r))

  def ownerOf(sym: String): String = Symbol(sym).owner.value
  /** The package a top-level symbol is imported from (top-level definitions live in `<File>$package`). */
  def importOwner(sym: String): String = { val o = ownerOf(sym); if (o.endsWith("$package.")) ownerOf(o) else o }
  /** A namespace object: a lowercase object (`iron.constraint.any`) that only groups definitions, like a package. */
  def namespaceObject(sym: String): Boolean = sym.endsWith(".") && !sym.endsWith("().") && Symbol(sym).displayName.headOption.exists(_.isLower)
  /** Import owners: packages, or a chain of namespace objects in a package. */
  def importablePath(owner: String): Boolean = owner.nonEmpty && owner != "_root_/" && (isPackage(owner) || namespaceObject(owner) && importablePath(ownerOf(owner)))
  def dotted(owner: String): String = owner.stripSuffix("/").stripSuffix(".").split("[/.]").map(p => Term.Name(p).syntax).mkString(".")
  def isPackage(sym: String): Boolean = sym.endsWith("/")

  private def buildIndex(roots: List[String]): Index = {
    val infos = new ConcurrentHashMap[String, s.SymbolInformation]()
    val files = roots.filter(r => Files.isDirectory(Paths.get(r))).flatMap { r =>
      val stream = Files.walk(Paths.get(r))
      try stream.iterator.asScala.filter(p => p.toString.endsWith(".semanticdb") && Files.isRegularFile(p)).toList finally stream.close()
    }
    val md5Cache = new ConcurrentHashMap[String, String]()
    def md5(uri: String): String = md5Cache.computeIfAbsent(uri, u => {
      val p = Paths.get(u)
      if (!Files.isRegularFile(p)) "" else java.security.MessageDigest.getInstance("MD5").digest(Files.readAllBytes(p)).map(b => f"${b & 255}%02X").mkString
    })
    files.asJava.parallelStream().forEach { f =>
      s.TextDocuments.parseFrom(Files.readAllBytes(f)).documents.foreach { d =>
        if (md5(d.uri) == d.md5) d.symbols.foreach(i => if (!i.symbol.startsWith("local")) infos.putIfAbsent(i.symbol, i))
      }
    }
    val members = scala.collection.mutable.Map.empty[String, scala.collection.mutable.Map[String, Set[String]]]
    val pkgs = scala.collection.mutable.Set.empty[String]
    def addPackages(p: String): Unit = if (p.nonEmpty && p != "_root_/" && p != "_empty_/" && pkgs.add(p)) {
      val o = ownerOf(p)
      addPackages(o)
      if (o.nonEmpty && o != "_root_/") members.getOrElseUpdate(o, scala.collection.mutable.Map.empty).updateWith(Symbol(p).displayName)(x => Some(x.getOrElse(Set.empty) + p))
    }
    infos.values.asScala.foreach { i =>
      val o = ownerOf(i.symbol)
      val pkg = if (isPackage(o)) Some(o) else if (o.endsWith("$package.") && isPackage(ownerOf(o))) Some(ownerOf(o)) else None
      pkg.foreach { p =>
        addPackages(p)
        members.getOrElseUpdate(p, scala.collection.mutable.Map.empty).updateWith(i.displayName)(x => Some(x.getOrElse(Set.empty) + i.symbol))
      }
    }
    new Index(infos, members.map { case (k, v) => k -> v.toMap }.toMap, pkgs.toSet)
  }

  def relative(p: String): String = {
    val here = Paths.get("").toAbsolutePath.normalize
    val abs = Paths.get(p).toAbsolutePath.normalize
    if (abs.startsWith(here)) here.relativize(abs).toString else p
  }

  /** The names the root imports (`java.lang.*`, `scala.*`, `scala.Predef.*`) bind, each with the symbol it denotes. */
  val rootTypes: Map[String, String] = Map(
    "String" -> "scala/Predef.String#", "Map" -> "scala/Predef.Map#", "Set" -> "scala/Predef.Set#", "Class" -> "scala/Predef.Class#",
    "Function" -> "scala/Predef.Function#", "List" -> "scala/package.List#", "Seq" -> "scala/package.Seq#", "IndexedSeq" -> "scala/package.IndexedSeq#",
    "Vector" -> "scala/package.Vector#", "Iterable" -> "scala/package.Iterable#", "Iterator" -> "scala/package.Iterator#",
    "Either" -> "scala/package.Either#", "Left" -> "scala/package.Left#", "Right" -> "scala/package.Right#", "BigInt" -> "scala/package.BigInt#",
    "BigDecimal" -> "scala/package.BigDecimal#", "Range" -> "scala/package.Range#", "LazyList" -> "scala/package.LazyList#",
    "StringBuilder" -> "scala/package.StringBuilder#", "Throwable" -> "scala/package.Throwable#", "Exception" -> "scala/package.Exception#",
    "Error" -> "scala/package.Error#", "RuntimeException" -> "scala/package.RuntimeException#", "Ordering" -> "scala/package.Ordering#",
    "Ordered" -> "scala/package.Ordered#", "Equiv" -> "scala/package.Equiv#", "Numeric" -> "scala/package.Numeric#",
    "Integral" -> "scala/package.Integral#", "Fractional" -> "scala/package.Fractional#", "Traversable" -> "scala/package.Traversable#",
    "Iterable" -> "scala/package.Iterable#", "NoSuchElementException" -> "scala/package.NoSuchElementException#",
    "IllegalArgumentException" -> "scala/package.IllegalArgumentException#", "IndexOutOfBoundsException" -> "scala/package.IndexOutOfBoundsException#",
    "UnsupportedOperationException" -> "scala/package.UnsupportedOperationException#", "NumberFormatException" -> "scala/package.NumberFormatException#",
    "Serializable" -> "scala/package.Serializable#", "Cloneable" -> "scala/package.Cloneable#", "Nil" -> "scala/package.Nil#",
    "Boolean" -> "scala/Boolean#", "Byte" -> "scala/Byte#", "Short" -> "scala/Short#", "Char" -> "scala/Char#", "Int" -> "scala/Int#",
    "Long" -> "scala/Long#", "Float" -> "scala/Float#", "Double" -> "scala/Double#", "Unit" -> "scala/Unit#", "Any" -> "scala/Any#",
    "AnyRef" -> "scala/AnyRef#", "AnyVal" -> "scala/AnyVal#", "Nothing" -> "scala/Nothing#", "Null" -> "scala/Null#", "Option" -> "scala/Option#",
    "Some" -> "scala/Some#", "Array" -> "scala/Array#", "Product" -> "scala/Product#", "Equals" -> "scala/Equals#",
    "PartialFunction" -> "scala/PartialFunction#", "Tuple" -> "scala/Tuple#", "Matchable" -> "scala/Matchable#", "Singleton" -> "scala/Singleton#",
    "Enumeration" -> "scala/Enumeration#", "Dynamic" -> "scala/Dynamic#", "Symbol" -> "scala/Symbol#", "Selectable" -> "scala/Selectable#",
    "IllegalStateException" -> "scala/package.IllegalStateException#", "NullPointerException" -> "scala/package.NullPointerException#",
    "ClassCastException" -> "scala/package.ClassCastException#", "ArithmeticException" -> "scala/package.ArithmeticException#",
    "InterruptedException" -> "scala/package.InterruptedException#", "ArrayIndexOutOfBoundsException" -> "scala/package.ArrayIndexOutOfBoundsException#",
    "StringIndexOutOfBoundsException" -> "scala/package.StringIndexOutOfBoundsException#", "Manifest" -> "scala/Predef.Manifest#",
    "OptManifest" -> "scala/Predef.OptManifest#", "<:<" -> "scala/`<:<`#", "=:=" -> "scala/`=:=`#", "Mirror" -> ""
  ).filter(_._2.nonEmpty)
  val rootTerms: Map[String, String] = Map(
    "List" -> "scala/package.List.", "Seq" -> "scala/package.Seq.", "IndexedSeq" -> "scala/package.IndexedSeq.", "Vector" -> "scala/package.Vector.",
    "Iterable" -> "scala/package.Iterable.", "Iterator" -> "scala/package.Iterator.", "Nil" -> "scala/package.Nil.", "Left" -> "scala/package.Left.",
    "Right" -> "scala/package.Right.", "Either" -> "scala/package.Either.", "BigInt" -> "scala/package.BigInt.", "BigDecimal" -> "scala/package.BigDecimal.",
    "Range" -> "scala/package.Range.", "LazyList" -> "scala/package.LazyList.", "StringBuilder" -> "scala/package.StringBuilder.",
    "Ordering" -> "scala/package.Ordering.", "Ordered" -> "scala/package.Ordered.", "Equiv" -> "scala/package.Equiv.", "Numeric" -> "scala/package.Numeric.",
    "Map" -> "scala/Predef.Map.", "Set" -> "scala/Predef.Set.", "print" -> "scala/Predef.print().", "println" -> "scala/Predef.println().",
    "require" -> "scala/Predef.require().", "assert" -> "scala/Predef.assert().", "assume" -> "scala/Predef.assume().",
    "identity" -> "scala/Predef.identity().", "implicitly" -> "scala/Predef.implicitly().", "summon" -> "scala/Predef.summon().",
    "locally" -> "scala/Predef.locally().", "???" -> "scala/Predef.`???`().", "classOf" -> "scala/Predef.classOf().", "valueOf" -> "scala/Predef.valueOf().",
    "util" -> "scala/util/", "collection" -> "scala/collection/", "concurrent" -> "scala/concurrent/", "io" -> "scala/io/", "math" -> "scala/math/",
    "sys" -> "scala/sys/", "reflect" -> "scala/reflect/", "annotation" -> "scala/annotation/", "compiletime" -> "scala/compiletime/",
    "deriving" -> "scala/deriving/", "quoted" -> "scala/quoted/", "runtime" -> "scala/runtime/", "jdk" -> "scala/jdk/", "ref" -> "scala/ref/",
    "beans" -> "scala/beans/", "Predef" -> "scala/Predef.", "Console" -> "scala/Console.", "Some" -> "scala/Some.", "None" -> "scala/None.",
    "Option" -> "scala/Option.", "Tuple" -> "scala/Tuple.", "Array" -> "scala/Array.", "Symbol" -> "scala/Symbol.", "Unit" -> "scala/Unit.",
    "Boolean" -> "scala/Boolean.", "Int" -> "scala/Int.", "Long" -> "scala/Long.", "Double" -> "scala/Double.", "Float" -> "scala/Float.",
    "Short" -> "scala/Short.", "Byte" -> "scala/Byte.", "Char" -> "scala/Char.", "StringContext" -> "scala/StringContext.",
    "PartialFunction" -> "scala/PartialFunction.", "Function" -> "scala/Function.", "Product" -> "scala/Product.", "Mirror" -> "",
    "Math" -> "java/lang/Math#", "System" -> "java/lang/System#", "Thread" -> "java/lang/Thread#", "Integer" -> "java/lang/Integer#",
    "Character" -> "java/lang/Character#", "Runtime" -> "java/lang/Runtime#", "ProcessBuilder" -> "java/lang/ProcessBuilder#",
    "Process" -> "java/lang/Process#"
  ).filter(_._2.nonEmpty)
  /** Owners whose members the root imports bring in, highest precedence first. */
  val rootOwners: List[String] = List("scala/Predef.", "scala/", "java/lang/")
}

class ShortenQualified(config: ShortenQualified.Config, excluded: Set[String]) extends SemanticRule("ShortenQualified") {
  import ShortenQualified._
  def this() = this(ShortenQualified.Config(), Set.empty)

  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("ShortenQualified")(Config()).map { cfg =>
      val ex = if (cfg.exclusions.nonEmpty && Files.isRegularFile(Paths.get(cfg.exclusions)))
        Files.readAllLines(Paths.get(cfg.exclusions), UTF_8).asScala.map(_.split("\t").head.trim).filter(l => l.nonEmpty && !l.startsWith("#")).toSet
      else Set.empty[String]
      if (cfg.ledger.nonEmpty) Option(Paths.get(cfg.ledger).getParent).foreach(Files.createDirectories(_))
      new ShortenQualified(cfg.copy(semanticdbRoots = cfg.semanticdbRoots.map(_.replace("{scalaVersion}", c.scalaVersion))), ex)
    }

  private val ledgerLock = new Object

  sealed trait Res
  case object Same extends Res
  case object Other extends Res
  case object NotFound extends Res

  /** A name some scope binds: the name, whether it is a type (else a term), and the symbol. */
  final case class Binding(name: String, isType: Boolean, symbol: String)

  /** `a.b.C#` and `a.b.C.` (a class and its companion, a type alias and its value) count as one entity; overloads too. */
  def key(sym: String): String = {
    val noDisamb = sym.replaceAll("\\(\\+?\\d*\\)\\.$", ".").replaceAll("[^/.#]+\\$package\\.", "")
    val k = noDisamb.stripSuffix("#").stripSuffix(".")
    if (k == "scala/Predef.String") "java/lang/String" else k
  }
  def same(a: String, b: String): Boolean = a.nonEmpty && b.nonEmpty && key(a) == key(b)

  override def fix(implicit doc: SemanticDocument): Patch = {
    val idx = index(config.semanticdbRoots)
    val path = relative(doc.input match { case Input.File(p, _) => p.toString; case Input.VirtualFile(p, _) => p; case other => other.toString })
    if (excluded(path + "@*")) return Patch.empty

    def info(sym: String): Option[s.SymbolInformation] = if (sym.isEmpty || sym.startsWith("local")) None else Option(idx.infos.get(sym))
    def symOf(t: Tree): String = t.symbol.value
    def ancestors(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: ancestors(p))
    def termSym(sym: String): Boolean = !sym.endsWith("#") && !sym.endsWith("/") && !(sym.contains("[") && sym.endsWith("]"))
    def isTypeSym(sym: String): Boolean = sym.endsWith("#") || sym.endsWith("]")

    // ---- every name the file binds or uses unqualified, for the import check -------------------------------------------
    def isDef(n: Name): Boolean = n.parent.exists { case m: Member => m.name eq n; case _ => false }
    lazy val fileDefs: Map[String, Set[String]] = doc.tree.collect {
      case n: Name if isDef(n) => n.value -> symOf(n)
    }.groupBy(_._1).map { case (k, v) => k -> v.map(_._2).toSet }
    def inImportOrPkg(t: Tree): Boolean = ancestors(t).exists {
      case _: Import => true
      case p: Pkg => p.ref.pos.start <= t.pos.start && t.pos.end <= p.ref.pos.end
      case _ => false
    }
    lazy val bareUses: Map[String, Set[String]] = doc.tree.collect {
      case n: Term.Name if !isDef(n) && !inImportOrPkg(n) && !(n.parent.exists { case Term.Select(_, nn) => nn eq n; case _ => false }) &&
          !(n.parent.exists(_.is[Term.Assign]) && n.parent.exists(_.parent.exists(_.is[Term.ArgClause]))) => n.value -> symOf(n)
      case n: Type.Name if !isDef(n) && !inImportOrPkg(n) && !(n.parent.exists { case Type.Select(_, nn) => nn eq n; case _ => false }) => n.value -> symOf(n)
    }.groupBy(_._1).map { case (k, v) => k -> v.map(_._2).filter(_.nonEmpty).toSet }

    // ---- the file's package clauses, and whether it gets chained ----------------------------------------------------------
    val topPkgs: List[Pkg] = doc.tree match { case src: Source => src.stats.collect { case p: Pkg => p }; case _ => Nil }
    val projectDots = config.projectPackage.stripSuffix("/").replace('/', '.')
    /** Chaining puts the members of the project package (subpackages `util`, `api`, ...; top-level types) in scope between the
      * file's own package and the root imports: only safe when no bare name the root imports resolve is one of them. */
    lazy val chainPkg: Option[Pkg] = topPkgs match {
      case List(p) if config.chainPackages && !excluded(path + "@pkg") && !p.body.stats.exists(_.is[Pkg]) &&
          p.ref.syntax.startsWith(projectDots + ".") && !doc.tokens.exists(t => t.is[Token.Comment] && t.pos.start >= p.ref.pos.start && t.pos.end <= p.ref.pos.end) &&
          bareUses.forall { case (n, syms) =>
            val outer = idx.packageMembers.getOrElse(config.projectPackage, Map.empty).getOrElse(n, Set.empty)
            outer.isEmpty || syms.forall(sym => !(rootOwners.contains(ownerOf(sym)) || sym.startsWith("scala/") || sym.startsWith("java/lang/")) || outer.exists(same(_, sym)))
          } => Some(p)
      case _ => None
    }
    def fullPkg(p: Pkg): String = (ancestors(p).collect { case q: Pkg => q.ref.syntax }.reverse :+ p.ref.syntax).mkString(".")
    def pkgSym(dotted: String): String = dotted.replace('.', '/') + "/"

    // ---- members of classes (declared and inherited) ------------------------------------------------------------------
    val memberCache = new java.util.HashMap[String, Option[Map[String, Set[String]]]]()
    /** The own members of a class whose parents are not all known. */
    val ownOnly = new java.util.HashMap[String, Map[String, Set[String]]]()
    /** The members of a class/object/trait by name, inherited ones included; None when some parent is unknown. */
    def members(cls: String): Option[Map[String, Set[String]]] = {
      if (memberCache.containsKey(cls)) return memberCache.get(cls)
      memberCache.put(cls, Some(Map.empty)) // cycle guard
      val res: Option[Map[String, Set[String]]] = info(cls).map(_.signature) match {
        case Some(cs: s.ClassSignature) =>
          val own: Map[String, Set[String]] = (cs.declarations.toList.flatMap(_.symlinks).flatMap(d => info(d).map(i => i.displayName -> d)) ++ idx.children.getOrElse(cls, Nil))
            .groupBy(_._1).map { case (k, v) => k -> v.map(_._2).toSet }
          val parents = cs.parents.collect { case r: s.TypeRef => r.symbol }.filterNot(p => p.startsWith("scala/") || p.startsWith("java/lang/Object#") || p.startsWith("java/io/Serializable#"))
          val inherited = parents.map(members)
          if (inherited.exists(_.isEmpty)) { ownOnly.put(cls, own); None }
          else Some(inherited.flatten.foldLeft(own) { (acc, m) => m.foldLeft(acc) { case (a, (k, v)) => a.updated(k, a.getOrElse(k, Set.empty) ++ v) } })
        case _ => None
      }
      memberCache.put(cls, res)
      res
    }
    def memberNamed(owner: String, name: String, wantType: Boolean): Option[Set[String]] =
      if (isPackage(owner) && (owner.startsWith(config.projectPackage) || idx.packages(owner)))
        Some(idx.packageMembers.getOrElse(owner, Map.empty).getOrElse(name, Set.empty).filter(m => if (wantType) isTypeSym(m) else termSym(m) || isPackage(m)))
      else if (isPackage(owner)) {
        val probe = (if (wantType) List(owner + name + "#") else List(owner + name + ".", owner + name + "/")).filter(x => Symbol(x).info.isDefined)
        if (probe.nonEmpty) Some(probe.toSet) else if (owner.startsWith("java/") || owner.startsWith("javax/")) Some(Set.empty) else None
      }
      else members(owner) match {
        case Some(m) => Some(m.getOrElse(name, Set.empty).filter(x => if (wantType) isTypeSym(x) else termSym(x)))
        case None =>
          // parents unknown: an own member of that name still decides
          val own = Option(ownOnly.get(owner)).map(_.getOrElse(name, Set.empty).filter(x => if (wantType) isTypeSym(x) else termSym(x))).getOrElse(Set.empty)
          if (own.nonEmpty) Some(own) else None
      }

    // ---- bindings that a scope introduces ------------------------------------------------------------------------------
    def patVars(p: Tree): List[Binding] = p.collect {
      case Pat.Var(n) => Binding(n.value, false, symOf(n))
      case v: Type.Var => Binding(v.name.value, true, symOf(v.name))
    }
    def statBindings(stat: Tree): List[Binding] = stat match {
      case d: Defn.Val => d.pats.flatMap(patVars)
      case d: Defn.Var => d.pats.flatMap(patVars)
      case d: Decl.Val => d.pats.flatMap(patVars)
      case d: Decl.Var => d.pats.flatMap(patVars)
      case d: Defn.Def => List(Binding(d.name.value, false, symOf(d.name)))
      case d: Decl.Def => List(Binding(d.name.value, false, symOf(d.name)))
      case d: Defn.Macro => List(Binding(d.name.value, false, symOf(d.name)))
      case d: Defn.Type => List(Binding(d.name.value, true, symOf(d.name)))
      case d: Decl.Type => List(Binding(d.name.value, true, symOf(d.name)))
      case d: Defn.Class => List(Binding(d.name.value, true, symOf(d.name)), Binding(d.name.value, false, symOf(d.name).stripSuffix("#") + "."))
      case d: Defn.Trait => List(Binding(d.name.value, true, symOf(d.name)))
      case d: Defn.Enum => List(Binding(d.name.value, true, symOf(d.name)), Binding(d.name.value, false, symOf(d.name).stripSuffix("#") + "."))
      case d: Defn.Object => List(Binding(d.name.value, false, symOf(d.name)))
      case d: Defn.Given => if (d.name.value.isEmpty) Nil else List(Binding(d.name.value, false, symOf(d.name)))
      case d: Defn.GivenAlias => if (d.name.value.isEmpty) Nil else List(Binding(d.name.value, false, symOf(d.name)))
      case d: Decl.Given => if (d.name.value.isEmpty) Nil else List(Binding(d.name.value, false, symOf(d.name)))
      case d: Defn.EnumCase => List(Binding(d.name.value, false, symOf(d.name)))
      case d: Defn.RepeatedEnumCase => d.cases.map(n => Binding(n.value, false, symOf(n)))
      case d: Defn.ExtensionGroup => d.body match {
        case b: Term.Block => b.stats.flatMap(statBindings)
        case other => statBindings(other)
      }
      case e: Export => e.importers.flatMap(i => i.importees.flatMap {
        case Importee.Name(n) => List(Binding(n.value, false, symOf(n)), Binding(n.value, true, symOf(n)))
        case Importee.Rename(f, t) => List(Binding(t.value, false, symOf(f)), Binding(t.value, true, symOf(f)))
        case _ => Nil
      })
      case _ => Nil
    }
    /** The symbol an import selector names; Scala 3 may record none, then the path is compared with the target's. */
    def dottedSym(sym: String): String = sym.stripSuffix("#").stripSuffix(".").stripSuffix("/").replaceAll("[^/.#]+\\$package\\.", "").replaceAll("[/.#]", ".")
    def importedSym(imp: Importer, n: Name, target: String): String = {
      val direct = symOf(n)
      if (direct.nonEmpty) direct
      else {
        val path = imp.ref.syntax.stripPrefix("_root_.") + "." + n.value
        if (path == dottedSym(target)) target else "?" + path
      }
    }
    /** The bindings an import statement makes for `name`: Some(symbols) (maybe empty), or None when a wildcard's members are unknown. */
    def importBinds(imp: Importer, name: String, wantType: Boolean, target: String): Option[Set[String]] = {
      val q0 = symOf(imp.ref match { case Term.Select(_, n) => n; case n => n })
      // Scala 3 records no occurrence for package names in imports: read the path as a package then
      val q = if (q0.nonEmpty) q0 else imp.ref.syntax.stripPrefix("_root_.").replace('.', '/') + "/"
      imp.importees.foldLeft(Option(Set.empty[String])) { (acc, ie) =>
        acc.flatMap { found =>
          ie match {
            case Importee.Name(n) if n.value == name => Some(found + importedSym(imp, n, target))
            case Importee.Rename(f, t) if t.value == name => Some(found + importedSym(imp, f, target))
            case _: Importee.Wildcard =>
              if (q.nonEmpty && ownerOf(target) == q && Symbol(target).displayName == name) Some(found + target)
              else if (q.isEmpty) None
              else memberNamed(q, name, wantType).map(found ++ _)
            case _ => Some(found)
          }
        }
      }
    }
    def isBinder(t: Tree): Boolean = t match {
      case _: Defn.Def | _: Decl.Def | _: Defn.Macro | _: Defn.Given | _: Defn.GivenAlias | _: Decl.Given | _: Defn.ExtensionGroup | _: Ctor.Secondary |
          _: Defn.Class | _: Defn.Trait | _: Defn.Enum | _: Defn.EnumCase | _: Defn.Type | _: Decl.Type | _: Term.Function | _: Term.ContextFunction |
          _: Term.PolyFunction | _: Type.Lambda | _: Type.PolyFunction | _: Defn.Object => true
      case _ => false
    }
    /** The parameters a binder declares (its own parameter clauses, not those of nested binders or bodies). */
    def paramBindings(b: Tree): List[Binding] = {
      def go(t: Tree): List[Binding] = t match {
        case p: Term.Param => if (p.name.value.isEmpty || p.name.value == "_") Nil else List(Binding(p.name.value, false, symOf(p.name)))
        case p: Type.Param => if (p.name.value.isEmpty || p.name.value == "_") Nil else List(Binding(p.name.value, true, symOf(p.name)))
        case _: Template | _: Term | _: Type | _: Name | _: Mod => Nil
        case other => other.children.flatMap(go)
      }
      b.children.flatMap(go)
    }
    val paramCache = new java.util.IdentityHashMap[Tree, List[Binding]]()
    def cachedParams(b: Tree): List[Binding] = { val c = paramCache.get(b); if (c != null) c else { val r = paramBindings(b); paramCache.put(b, r); r } }
    val statsCache = new java.util.IdentityHashMap[Tree, List[Binding]]()
    def statsBindings(owner: Tree, stats: List[Stat]): List[Binding] = { val c = statsCache.get(owner); if (c != null) c else { val r = stats.flatMap(statBindings); statsCache.put(owner, r); r } }

    /** Planned file-level imports: name -> symbol. */
    val plannedImports = scala.collection.mutable.LinkedHashMap.empty[String, String]
    /** Unqualified names this rewrite introduces: name -> symbols. */
    val plannedUses = scala.collection.mutable.Map.empty[String, Set[String]]

    def ownerSymbolOf(templ: Template): String = templ.parent match {
      case Some(d: Defn.Class) => symOf(d.name)
      case Some(d: Defn.Trait) => symOf(d.name)
      case Some(d: Defn.Object) => symOf(d.name)
      case Some(d: Defn.Enum) => symOf(d.name)
      case Some(d: Defn.EnumCase) => symOf(d.name)
      case Some(d: Defn.Given) => symOf(d.name)
      case _ => ""
    }

    def enumCaseNamed(t: Template, name: String): Boolean = t.body.stats.exists {
      case c: Defn.EnumCase => c.name.value == name
      case c: Defn.RepeatedEnumCase => c.cases.exists(_.value == name)
      case _ => false
    }
    /** What `name` (a type when `wantType`) denotes at `at`, compared with `target`. */
    def resolve(name: String, wantType: Boolean, target: String, at: Tree): Res = {
      def judge(syms: Set[String]): Option[Res] = if (syms.isEmpty) None else Some(if (syms.forall(same(_, target))) Same else Other)
      def fromBindings(bs: List[Binding]): Option[Res] = judge(bs.filter(b => b.name == name && b.isType == wantType).map(_.symbol).toSet)
      def importsBefore(stats: List[Tree], child: Tree): Option[Res] = {
        val imps = stats.collect { case i: Import if i.pos.end <= at.pos.start => i }.flatMap(_.importers)
        // explicit selectors take precedence over wildcards
        val explicit = imps.flatMap(i => i.importees.collect {
          case Importee.Name(n) if n.value == name => importedSym(i, n, target)
          case Importee.Rename(f, t) if t.value == name => importedSym(i, f, target)
        }).toSet
        if (explicit.nonEmpty) judge(explicit)
        else {
          val got = imps.map(i => importBinds(i, name, wantType, target))
          val known = got.flatten.flatten.toSet
          if (known.exists(x => !same(x, target))) Some(Other) // a known wildcard brings another `name`
          else if (got.exists(_.isEmpty)) None // unknown wildcard: let the compiler judge, look further out
          else judge(known)
        }
      }
      var child: Tree = at
      var inEnumCase = false
      val it = ancestors(at).iterator
      while (it.hasNext) {
        val a = it.next()
        if (a.is[Defn.EnumCase] || a.is[Defn.RepeatedEnumCase]) inEnumCase = true
        val r: Option[Res] = a match {
          // an enum case's own signature cannot refer to the enum's members ("illegal reference ... from enum case")
          case t: Template if inEnumCase && t.parent.exists(_.is[Defn.Enum]) => None
          // in an enum body the cases are visible like an import: a same-named definition further out makes them ambiguous
          case t: Template if t.parent.exists(_.is[Defn.Enum]) && enumCaseNamed(t, name) && t.parent.exists(e => resolve(name, wantType, target, e) != NotFound) => Some(Other)
          case b: Term.Block => fromBindings(statsBindings(b, b.stats)).orElse(importsBefore(b.stats, child))
          case t: Template =>
            val declared = fromBindings(statsBindings(t, t.body.stats))
            val owner = ownerSymbolOf(t)
            val inherited = if (owner.isEmpty) None else members(owner).flatMap(m => judge(m.getOrElse(name, Set.empty).filter(x => if (wantType) isTypeSym(x) else termSym(x))))
            declared.orElse(inherited).orElse(importsBefore(t.body.stats, child))
          case c: Case => fromBindings(patVars(c.pat))
          case f: Term.For => fromBindings(f.enums.flatMap { case g: Enumerator.Generator => patVars(g.pat); case v: Enumerator.Val => patVars(v.pat); case _ => Nil })
          case f: Term.ForYield => fromBindings(f.enums.flatMap { case g: Enumerator.Generator => patVars(g.pat); case v: Enumerator.Val => patVars(v.pat); case _ => Nil })
          case p: Pkg =>
            val local = fromBindings(statsBindings(p, p.body.stats))
            val imported = importsBefore(p.body.stats, child)
            val planned = if (ancestors(p).exists(_.is[Pkg])) None else plannedImports.get(name).map(s => if (same(s, target)) Same else Other)
            val pkgMembers = judge(memberNamed(pkgSym(fullPkg(p)), name, wantType).getOrElse(Set.empty))
            val outer = if (chainPkg.exists(_ eq p)) judge(memberNamed(config.projectPackage, name, wantType).getOrElse(Set.empty)) else None
            local.orElse(imported).orElse(planned).orElse(pkgMembers).orElse(outer)
          case _ if isBinder(a) => fromBindings(cachedParams(a))
          case _ => None
        }
        if (r.isDefined) return r.get
        child = a
      }
      // the root imports
      if (wantType) rootTypes.get(name) match {
        case Some(s) => return if (same(s, target)) Same else Other
        case None =>
      } else rootTerms.get(name) match {
        case Some(s) => return if (same(s, target) || s == target) Same else Other
        case None =>
      }
      if (rootOwners.contains(ownerOf(target)) && Symbol(target).displayName == name) return Same
      if (!wantType && isPackage(target) && ownerOf(target) == "_root_/" && Symbol(target).displayName == name) return Same
      if (!wantType && isPackage(target) && ownerOf(target).isEmpty && Symbol(target).displayName == name) return Same
      NotFound
    }

    lazy val fileImports: List[Importer] = doc.tree.collect { case i: Import => i.importers }.flatten
    lazy val filePackages: List[String] = topPkgs.flatMap(p => p :: p.collect { case q: Pkg => q }).map(fullPkg).map(pkgSym)

    /** May `import <pkg>.<name>` go at the top of this file, for `target`? Only when the name means nothing else anywhere in it. */
    def importable(name: String, target: String): Boolean = {
      if (!importablePath(importOwner(target)) || excluded(path + "@import:" + name)) return false
      if (plannedImports.get(name).exists(s => !same(s, target))) return false
      if (fileDefs.getOrElse(name, Set.empty).exists(s => !same(s, target))) return false
      if (bareUses.getOrElse(name, Set.empty).exists(s => !same(s, target))) return false
      if (plannedUses.getOrElse(name, Set.empty).exists(s => !same(s, target))) return false
      if (rootTypes.get(name).exists(s => !same(s, target)) || rootTerms.get(name).exists(s => !same(s, target))) return false
      if (Symbol("java/lang/" + name + "#").info.isDefined && !same("java/lang/" + name + "#", target)) return false
      if (filePackages.exists(p => idx.packageMembers.getOrElse(p, Map.empty).getOrElse(name, Set.empty).exists(s => !same(s, target)))) return false
      val imps = fileImports.map(i => importBinds(i, name, true, target).map(_ ++ importBinds(i, name, false, target).getOrElse(Set.empty)))
      if (imps.exists(_.exists(_.exists(s => !same(s, target))))) return false
      true
    }

    // ---- the chains -----------------------------------------------------------------------------------------------------
    def segments(t: Tree): Option[List[Name]] = t match {
      case n: Term.Name => Some(List(n))
      case Term.Select(q, n) => segments(q).map(_ :+ n)
      case Type.Select(q, n) => segments(q).map(_ :+ n)
      case _ => None
    }
    def outermost(t: Tree): Boolean = t.parent match {
      case Some(Term.Select(q, _)) if q eq t => false
      case Some(Type.Select(q, _)) if q eq t => false
      case _ => true
    }
    def inImport(t: Tree): Boolean = ancestors(t).exists(_.is[Import])
    def inPkgRef(t: Tree): Boolean = ancestors(t).exists { case p: Pkg => p.ref.pos.start <= t.pos.start && t.pos.end <= p.ref.pos.end; case _ => false }
    val enclosingKeys: Tree => Set[String] = t => ancestors(t).collect {
      case o: Defn.Object => key(symOf(o.name))
      case c: Defn.Class => key(symOf(c.name))
      case c: Defn.Trait => key(symOf(c.name))
      case e: Defn.Enum => key(symOf(e.name))
    }.toSet
    def isNewtypeObject(sym: String): Boolean = sym.endsWith(".") && (info(sym).map(_.signature) match {
      case Some(cs: s.ClassSignature) => cs.parents.exists { case r: s.TypeRef => r.symbol.startsWith("io/github/iltotore/iron/Refined"); case _ => false }
      case _ => false
    })

    case class Edit(start: Int, end: Int, text: String, kind: String, key: String, note: String)
    val edits = scala.collection.mutable.ListBuffer.empty[Edit]
    val skippedRows = scala.collection.mutable.ListBuffer.empty[String]
    def skip(key: String, reason: String, node: Tree): Unit =
      if (config.skipped.nonEmpty) skippedRows += List(path, key, reason, node.syntax.replace("\n", " ").replace("\t", " ").take(160)).mkString("\t")
    var patch = Patch.empty

    val chains: List[(Tree, List[Name])] = doc.tree.collect {
      case t @ (_: Term.Select | _: Type.Select) if outermost(t) && !inPkgRef(t) && !inImport(t) => segments(t).map(t -> _)
    }.flatten

    if (config.shorten) chains.foreach { case (node, names) =>
      val rooted = names.head.value == "_root_"
      val segs = if (rooted) names.tail else names
      val syms = segs.map(symOf)
      val chainKey = path + "@" + node.pos.start
      val isExport = ancestors(node).exists(_.is[Export])
      val lastIsType = segs.last.is[Type.Name]
      val n = segs.size
      if (!excluded(chainKey) && syms.forall(_.nonEmpty) && n >= 1 && !isPackage(syms.last)) {
        val projectRooted = isPackage(syms.head) && syms.head.startsWith(config.projectPackage.takeWhile(_ != '/') + "/") &&
          syms.exists(_.startsWith(config.projectPackage))
        val relativeProject = isPackage(syms.head) && syms.head.startsWith(config.projectPackage)
        val fqn = rooted || projectRooted || relativeProject
        val encl = enclosingKeys(node)
        def selfDroppable(i: Int): Boolean = (0 until i).exists(j => !isPackage(syms(j)) && encl(key(syms(j))) && !isNewtypeObject(syms(j)) && !(j + 1 < n && segs(j + 1).value == "T"))
        // the dropped qualifier must statically own what stays: `Family.CcBy.wire` is never `wire` (that is this.wire)
        def candidateOk(i: Int): Boolean = i > 0 && !isPackage(syms(i)) && (fqn || selfDroppable(i)) && key(importOwner(syms(i))) == key(syms(i - 1))
        // candidates: drop segs [0, i)
        val order = (n - 1 to 1 by -1).filter(candidateOk)
        def wantType(i: Int): Boolean = i == n - 1 && lastIsType
        val chosen: Option[(Int, Option[String])] = order.collectFirst { case i if resolve(segs(i).value, wantType(i), syms(i), node) == Same => (i, None) }
          .orElse {
            if (!fqn || !config.addImports || isExport) None
            else {
              val i0 = syms.indexWhere(s => !isPackage(s) && !namespaceObject(s))
              if (i0 <= 0) None
              else {
                val nm = segs(i0).value
                val tgt = syms(i0)
                if (importable(nm, tgt)) {
                  plannedImports.put(nm, tgt)
                  if (resolve(nm, wantType(i0), tgt, node) == Same) Some((i0, Some(nm))) else { plannedImports.remove(nm); None }
                } else None
              }
            }
          }
          .orElse {
            // a project subpackage as the head: `com.histmersion.api.library.Source` -> `api.library.Source`
            if (!config.packagePrefixes || !(rooted || projectRooted)) None
            else (n - 1 to 1 by -1).find { i =>
              isPackage(syms(i)) && syms(i).startsWith(config.projectPackage) && syms(i) != config.projectPackage &&
                key(importOwner(syms(i))) == key(syms(i - 1)) && resolve(segs(i).value, false, syms(i), node) == Same
            }.map(i => (i, None))
          }
          .orElse(if (rooted && resolve(segs.head.value, false, syms.head, node) == Same) Some((0, None)) else None)
        if (rooted || projectRooted) {
          def why: String = {
            val last = n - 1
            val nm = segs(last).value
            val named = resolve(nm, wantType(last), syms(last), node) match {
              case Other => s"`$nm` names another symbol here"
              case NotFound => s"`$nm` is not in scope here"
              case Same if last == 0 => s"`$nm` resolves only below `_root_`"
              case Same => s"`$nm` resolves, but `${segs(last - 1).value}` is not its static owner"
            }
            val i0 = syms.indexWhere(s => !isPackage(s) && !namespaceObject(s))
            val imp =
              if (isExport) "no import is added for an export"
              else if (!config.addImports) "imports are off"
              else if (i0 <= 0) "nothing on the path to import"
              else if (!importable(segs(i0).value, syms(i0))) s"no import of `${segs(i0).value}`: the file has another `${segs(i0).value}`"
              else s"an import of `${segs(i0).value}` would not resolve to it"
            val pre = if (config.packagePrefixes) "; no project subpackage on the path is in scope" else ""
            s"$named; $imp$pre"
          }
          chosen match {
            case None => skip(chainKey, why, node)
            case Some((0, _)) if projectRooted => skip(chainKey, "only `_root_.` dropped: " + why, node)
            case _ =>
          }
        }
        chosen.foreach { case (i, imp) =>
          val start = names.head.pos.start
          val end = segs(i).pos.start
          val removed = doc.input.text.substring(start, end)
          // only names, dots and blanks: a parenthesized qualifier (`(a.B).c`) or a comment stays as it is
          if (removed.matches("[\\p{L}\\p{N}_$`.\\s]*") && end > start) {
            plannedUses.update(segs(i).value, plannedUses.getOrElse(segs(i).value, Set.empty) + syms(i))
            val toks = doc.tokens.filter(t => t.start >= start && t.end <= end && t.start < end)
            patch += Patch.removeTokens(toks).atomic
            edits += Edit(start, end, "", "drop", chainKey, removed + "|" + segs.drop(i).map(_.value).mkString(".") + imp.fold("")(" +import " + _))
          } else {
            imp.foreach(plannedImports.remove)
            if (rooted || projectRooted) skip(chainKey, "the qualifier holds parentheses or a comment", node)
          }
        }
      } else if (!excluded(chainKey) && n >= 1 && (rooted || segs.head.value == config.projectPackage.takeWhile(_ != '/')) && !syms.forall(_.nonEmpty))
        skip(chainKey, "a segment has no SemanticDB symbol", node)
    }

    // ---- package chaining and imports -------------------------------------------------------------------------------
    val importLines = plannedImports.toList.map { case (nm, sym) =>
      val pkg = dotted(importOwner(sym))
      s"import $pkg.${Term.Name(nm).syntax}"
    }
    val innermostPkg: Option[Pkg] = topPkgs.lastOption.map { p =>
      def deepest(q: Pkg): Pkg = q.body.stats.collectFirst { case x: Pkg => x }.map(deepest).getOrElse(q)
      deepest(p)
    }
    val importKey = path + "@import:" + plannedImports.keys.mkString(",")
    val existingImports = innermostPkg.toList.flatMap(_.body.stats.takeWhile(_.is[Import]))
    val importsAfterPkgRef = importLines.nonEmpty && existingImports.isEmpty
    chainPkg.foreach { p =>
      val rest = p.ref.syntax.stripPrefix(projectDots + ".")
      val text = s"$projectDots\npackage $rest" + (if (importsAfterPkgRef && innermostPkg.exists(_ eq p)) "\n" + importLines.map("\n" + _).mkString else "")
      patch += Patch.replaceTree(p.ref, text).atomic
      edits += Edit(p.ref.pos.start, p.ref.pos.end, text, "pkg", path + "@pkg", p.ref.syntax)
    }
    if (importLines.nonEmpty) innermostPkg.foreach { p =>
      existingImports.lastOption match {
        case Some(last) =>
          val text = importLines.map("\n" + _).mkString
          patch += Patch.addRight(last.tokens.last, text).atomic
          edits += Edit(last.pos.end, last.pos.end, text, "import", importKey, "")
        case None if chainPkg.exists(_ eq p) => // folded into the package clause above
        case None =>
          val text = "\n" + importLines.map("\n" + _).mkString
          patch += Patch.addRight(p.ref.tokens.last, text).atomic
          edits += Edit(p.ref.pos.end, p.ref.pos.end, text, "import", importKey, "")
      }
    }

    if (config.ledger.nonEmpty && edits.nonEmpty) {
      var delta = 0
      val rows = edits.toList.sortBy(e => (e.start, e.end)).map { e =>
        val ns = e.start + delta
        delta += e.text.length - (e.end - e.start)
        val ne = e.end + delta
        List(path, e.kind, e.start, e.end, ns, ne, e.key, e.note.replace("\t", " ").replace("\n", "\\n")).mkString("\t")
      }
      ledgerLock.synchronized {
        Files.write(Paths.get(config.ledger), rows.map(_ + "\n").mkString.getBytes(UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND)
      }
    }
    if (config.skipped.nonEmpty && skippedRows.nonEmpty) ledgerLock.synchronized {
      Files.write(Paths.get(config.skipped), skippedRows.map(_ + "\n").mkString.getBytes(UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }
    patch
  }
}
