import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class PaginationTestConsumers extends SyntacticRule("PaginationTestConsumers") {
  private val expected = Map(
    "modules/db/src/test/scala/com/histmersion/domain/catalogue/evidence/EvidenceFixtures.scala" -> 1,
    "modules/db/src/test/scala/com/histmersion/db/InteractiveQueriesSuite.scala" -> 2
  )
  private val seen = scala.collection.mutable.Set.empty[String]
  override def fix(implicit doc: SyntacticDocument): Patch = {
    val input = doc.input match { case Input.File(p,_) => p.toNIO; case Input.VirtualFile(p,_) => Paths.get(p) }
    val path = Paths.get("").toAbsolutePath.relativize(input.toAbsolutePath).toString
    if(!expected.contains(path)) return Patch.empty
    require(seen.add(path))
    val frozen = Files.readAllLines(Paths.get("refactor-audit/models/pagination/build-v5/sources.tsv")).asScala.drop(1).map(_.split("\t")).map(a => a(0) -> a(1)).toMap
    val hash = java.security.MessageDigest.getInstance("SHA-256").digest(doc.input.text.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    require(hash == frozen(path))
    val patches = if(path.endsWith("EvidenceFixtures.scala")) doc.tree.collect {
      case d: Defn.Def if d.name.value == "leadRows" => d.decltpe.get match {
        case p: Type.Tuple if p.args.size == 2 && p.args.head.syntax == "Int" => Patch.replaceTree(p.args.head,"com.histmersion.RowCount")
        case _ => sys.error("Unexpected fixture result")
      }
    } else doc.tree.collect {
      case a: Term.Apply if a.fun.syntax == "Place.Counts" =>
        val args = a.argClause.values.collect { case Term.Assign(n: Term.Name,v: Term.Select) if Set("articles","events")(n.value) && v.name.value == "total" => v }
        require(args.size == 2)
        args.map(v => Patch.addRight(v,".value"))
    }.flatten
    require(patches.size == expected(path))
    patches.asPatch.atomic
  }
  override def afterComplete(): Unit = require(seen.toSet == expected.keySet)
}
