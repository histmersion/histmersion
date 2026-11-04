import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}

class MoveControllerFiles extends SyntacticRule("MoveControllerFiles") {
  private val moves = List(
    ("agents", "AgentsHandlers", "AgentsControllers"),
    ("managers", "ManagersHandlers", "ManagersControllers")
  ).map { case (area, from, to) =>
    val dir = Paths.get("modules/server/src/main/scala/com/histmersion/domain/automation", area).toAbsolutePath.normalize
    (dir.resolve(from + ".scala"), dir.resolve(to + ".scala"), to)
  }
  moves.foreach { case (from, to, _) =>
    require(!(Files.exists(from) && Files.exists(to)), s"Destination collision: $to")
    require(Files.exists(from) || Files.exists(to), s"Missing controller: $from")
  }
  override def fix(implicit doc: SyntacticDocument): Patch = {
    val path = doc.input match {
      case Input.File(p, _) => p.toNIO.toAbsolutePath.normalize
      case Input.VirtualFile(p, _) => Paths.get(p).toAbsolutePath.normalize
    }
    moves.find(_._1 == path).foreach { case (from, to, name) =>
      val classes = doc.tree.collect { case c: Defn.Class => c.name.value }
      val objects = doc.tree.collect { case o: Defn.Object => o.name.value }
      require(classes.contains(name) && objects.contains(name), s"Rename declarations before moving $from")
      require(Files.readString(from) == doc.input.text, s"Source changed: $from")
      Files.move(from, to)
    }
    Patch.empty
  }
  override def afterComplete(): Unit = moves.foreach { case (from, to, _) =>
    require(!Files.exists(from) && Files.exists(to), s"Move incomplete: $from")
  }
}
