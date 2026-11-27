import scalafix.v1._
import scala.meta._

class CreatedAtTestClock extends SyntacticRule("CreatedAtTestClock") {
  private val arity: Map[String, Int] = Map(
    "SourceService" -> 3,
    "EventService" -> 2,
    "GoalService" -> 1,
    "QueueService" -> 1,
    "PeriodService" -> 1,
    "AttestationService" -> 1,
    "EventImportService" -> 1,
    "PlaceService" -> 1,
    "AssetService" -> 1,
    "AssetLinkService" -> 1,
    "ArticleService" -> 1
  )

  private val named: Set[String] = Set("GazetteerImport.session", "GazetteerLoad.Settings")

  private def mentionsClock(a: Term.Apply): Boolean =
    a.argClause.values.exists {
      case Term.Assign(Term.Name("clock"), _) => true
      case _                                   => false
    }

  override def fix(implicit doc: SyntacticDocument): Patch = {
    val path = doc.input match {
      case Input.VirtualFile(p, _) => p
      case Input.File(p, _)        => p.toString
      case _                       => ""
    }
    if (!path.contains("/src/test/")) Patch.empty
    else {
      val positional = doc.tree.collect {
        case a: Term.Apply if arity.get(a.fun.syntax).contains(a.argClause.values.size) =>
          Patch.addRight(a.argClause.values.last, ", TestCreators.clock")
      }
      val byName = doc.tree.collect {
        case a: Term.Apply if named(a.fun.syntax) && !mentionsClock(a) && a.argClause.values.nonEmpty =>
          Patch.addRight(a.argClause.values.last, ", clock = TestCreators.clock")
      }
      val patches = positional ++ byName
      val imported = doc.tree.collect {
        case i: Importer if i.syntax == "com.histmersion.util.database.TestCreators" => i
      }.nonEmpty
      val inPackage = doc.tree.collect { case p: Pkg => p.ref.syntax }.mkString(".").endsWith("util.database")
      if (patches.isEmpty) Patch.empty
      else if (imported || inPackage) patches.asPatch
      else patches.asPatch + Patch.addGlobalImport(importer"com.histmersion.util.database.TestCreators")
    }
  }
}
