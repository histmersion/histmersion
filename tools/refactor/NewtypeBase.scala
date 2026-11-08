import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}

/** Step 3a (G52/G53): every newtype's companion extends the project's base trait `com.histmersion.Newtype` instead of
  * Iron's `RefinedType`, so its type gets `CanEqual` and the base type's `Ordering` with no code at the use site.
  *
  *  - `object X extends RefinedType[A, C]`, `io.github.iltotore.iron.RefinedType[A, C]` and
  *    `_root_.io.github.iltotore.iron.RefinedType[A, C]` become `object X extends Newtype[A, C]` (the type arguments
  *    are left as they are);
  *  - an explicit import of `RefinedType` that nothing else in the file uses goes (the whole line, or the selector);
  *  - a file whose package clauses do not open `com.histmersion` gets `import com.histmersion.Newtype` as its first
  *    import (after the package clause when it has none).
  *
  * Only Iron's `RefinedType` is matched (by symbol). Files matching an `exclude` regex are left alone. */
object NewtypeBase {
  case class Config(exclude: List[String] = Nil, base: String = "Newtype", basePackage: String = "com.histmersion")
  object Config {
    implicit val surface: generic.Surface[Config] = generic.deriveSurface[Config]
    implicit val decoder: ConfDecoder[Config] = generic.deriveDecoder(Config()).noTypos
  }
  val refined = "io/github/iltotore/iron/RefinedType#"
  val refinedObj = "io/github/iltotore/iron/RefinedType."
}

class NewtypeBase(config: NewtypeBase.Config) extends SemanticRule("NewtypeBase") {
  import NewtypeBase._
  def this() = this(NewtypeBase.Config())

  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("NewtypeBase")(Config()).map(new NewtypeBase(_))

  private def ancestors(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: ancestors(p))
  private def inImport(t: Tree): Boolean = ancestors(t).exists(_.is[Import])

  /** The type constructor of a parent `RefinedType[A, C]` (a name or a qualified path), when it is Iron's. */
  private def refinedCtor(init: Init)(implicit doc: SemanticDocument): Option[Type.Ref] = init.tpe match {
    case ap: Type.Apply => ap.tpe match {
      case n: Type.Name if n.symbol.value == refined => Some(n)
      case s: Type.Select if s.name.symbol.value == refined => Some(s)
      case _ => None
    }
    case _ => None
  }

  override def fix(implicit doc: SemanticDocument): Patch = {
    val path = doc.input match { case Input.File(p, _) => p.toString; case Input.VirtualFile(p, _) => p; case o => o.toString }
    if (config.exclude.exists(r => r.r.findFirstIn(path).nonEmpty)) return Patch.empty
    val ctors = doc.tree.collect { case o: Defn.Object => o.templ.inits.flatMap(refinedCtor) }.flatten
    if (ctors.isEmpty) return Patch.empty
    val rewrites = ctors.map(c => Patch.replaceTree(c, config.base).atomic)

    // other uses of RefinedType (RefinedType.Mirror, ...) keep its import
    val ctorRanges = ctors.map(c => (c.pos.start, c.pos.end))
    val otherUses = doc.tree.collect {
      case n: Name if n.value == "RefinedType" && (n.symbol.value == refined || n.symbol.value == refinedObj) && !inImport(n) &&
          !ctorRanges.exists { case (a, b) => n.pos.start >= a && n.pos.end <= b } => n
    }
    val importPatches = if (otherUses.nonEmpty) Nil else doc.tree.collect {
      case imp: Import => imp.importers.flatMap { importer =>
        val mine = importer.importees.collect { case i @ Importee.Name(n) if n.value == "RefinedType" => i }
        if (mine.isEmpty) Nil
        else if (importer.importees.size == mine.size && imp.importers.size == 1) {
          // the whole line, with its line break
          val toks = doc.tree.tokens
          val end = toks.find(t => t.start >= imp.pos.end && !t.is[Token.Space]).filter(_.is[Token.LF]).map(_.end).getOrElse(imp.pos.end)
          val lineStart = imp.pos.start - imp.pos.startColumn
          List(Patch.removeTokens(toks.filter(t => t.start >= lineStart && t.end <= end)))
        } else if (importer.importees.size - mine.size == 1) {
          val keep = importer.importees.filterNot(mine.contains).head
          List(Patch.replaceTree(importer, importer.ref.syntax + "." + keep.syntax))
        } else mine.map(i => Patch.removeImportee(i))
      }
    }.flatten

    // the base trait is in scope through the package clause `com.histmersion`; elsewhere it is imported
    val opened = doc.tree.collect { case p: Pkg if p.ref.syntax == config.basePackage => p }.nonEmpty
    val addImport = if (opened) Patch.empty else {
      val pkgs = doc.tree.collect { case p: Pkg => p }
      val inner = pkgs.lastOption
      val firstImport = inner.flatMap(_.stats.collectFirst { case i: Import => i })
      val text = s"import ${config.basePackage}.${config.base}"
      (firstImport, inner) match {
        case (Some(i), _) => Patch.addLeft(i, text + "\n")
        case (None, Some(p)) => Patch.addRight(p.ref, "\n\n" + text)
        case _ => Patch.empty
      }
    }
    (rewrites ++ importPatches).asPatch + addImport
  }
}
