import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}

/** Row 32 (ADR 0029 §5): the wire's creator records leave the work lists' `Queue` for the api root's `Provenance`, which
  * every domain's view shows. Every reference to a moved symbol, by SemanticDB symbol, is rewritten:
  *
  *   - a qualified reference (`Queue.Creator`, `api.automation.Queue.Creation`) becomes the new name, fully qualified
  *     in an import's path and short (`Provenance`, `Provenance.Agent`, `Provenance.By`) elsewhere;
  *   - a bare reference (one imported, or seen through `Queue.*`) becomes the short new name;
  *   - an importee naming a moved symbol is removed;
  *   - a file with a rewritten reference outside an import gets `import com.histmersion.api.Provenance`.
  *
  * The definitions themselves (in `Queue.scala`) are not touched: they are moved by hand, after this rule ran on the
  * SemanticDB that still has them where they were.
  *
  *   bash tools/refactor/scalafix.sh --config tools/refactor/relocate-wire-creator.conf --sourceroot . --files modules
  */
object RelocateWireCreator {
  case class Config(
      moves: Map[String, String] = Map(
        "com/histmersion/api/automation/Queue.Creator#" -> "Provenance",
        "com/histmersion/api/automation/Queue.Creator." -> "Provenance",
        "com/histmersion/api/automation/Queue.Agent#" -> "Provenance.Agent",
        "com/histmersion/api/automation/Queue.Agent." -> "Provenance.Agent",
        "com/histmersion/api/automation/Queue.CreatedBy#" -> "Provenance.By",
        "com/histmersion/api/automation/Queue.CreatedBy." -> "Provenance.By",
        "com/histmersion/api/automation/Queue.Creation." -> "Provenance"
      ),
      qualifier: String = "com.histmersion.api.",
      newImport: String = "com.histmersion.api.Provenance"
  )
  object Config {
    implicit val surface: generic.Surface[Config] = generic.deriveSurface[Config]
    implicit val decoder: ConfDecoder[Config] = generic.deriveDecoder(Config()).noTypos
  }
}

class RelocateWireCreator(config: RelocateWireCreator.Config) extends SemanticRule("RelocateWireCreator") {
  def this() = this(RelocateWireCreator.Config())

  override def withConfiguration(c: Configuration): Configured[Rule] =
    c.conf.getOrElse("RelocateWireCreator")(RelocateWireCreator.Config()).map(new RelocateWireCreator(_))

  private def moved(t: Tree)(implicit doc: SemanticDocument): Option[String] =
    config.moves.get(t.symbol.value)

  private def inImport(t: Tree): Boolean = t.parent match {
    case Some(_: Import) => true
    case Some(p)         => inImport(p)
    case None            => false
  }

  private def isDefinitionName(n: Name): Boolean = n.parent.exists {
    case d: Defn.Class  => d.name eq n
    case d: Defn.Object => d.name eq n
    case d: Defn.Type   => d.name eq n
    case _              => false
  }

  private def isSelectedName(n: Name): Boolean = n.parent.exists {
    case s: Term.Select => s.name eq n
    case s: Type.Select => s.name eq n
    case _              => false
  }

  override def fix(implicit doc: SemanticDocument): Patch = {
    var outside = false
    val rewrites = doc.tree.collect {
      case i: Importee.Name if moved(i.name).isDefined     => Patch.removeImportee(i)
      case i: Importee.Rename if moved(i.name).isDefined   => Patch.removeImportee(i)
      case s: Term.Select if moved(s.name).isDefined =>
        val target = moved(s.name).get
        if (inImport(s)) Patch.replaceTree(s, config.qualifier + target)
        else { outside = true; Patch.replaceTree(s, target) }
      case s: Type.Select if moved(s.name).isDefined =>
        outside = true
        Patch.replaceTree(s, moved(s.name).get)
      case n: Term.Name if moved(n).isDefined && !isSelectedName(n) && !isDefinitionName(n) && !inImport(n) =>
        outside = true
        Patch.replaceTree(n, moved(n).get)
      case n: Type.Name if moved(n).isDefined && !isSelectedName(n) && !isDefinitionName(n) && !inImport(n) =>
        outside = true
        Patch.replaceTree(n, moved(n).get)
    }
    val combined = Patch.fromIterable(rewrites)
    if (outside) combined + Patch.addGlobalImport(importer"com.histmersion.api.Provenance") else combined
  }
}
