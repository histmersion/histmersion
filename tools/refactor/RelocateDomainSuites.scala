import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets.UTF_8

class RelocateDomainSuites extends SyntacticRule("RelocateDomainSuites") {
  private val names = Set("AcquireSuite", "CandidatesSuite", "RenameSuite")
  override def fix(implicit doc: SyntacticDocument): Patch = {
    val inputPath = doc.input match {
      case Input.File(path, _) => Some(path.toNIO)
      case Input.VirtualFile(path, _) => Some(Paths.get(path))
      case _ => None
    }
    inputPath.foreach { path =>
        val source = path.toAbsolutePath.normalize
        val filename = source.getFileName.toString
        val name = filename.stripSuffix(".scala")
        val origin = Paths.get("modules/api/src/test/scalajvm/com/histmersion/api", filename).toAbsolutePath.normalize
        if (source == origin && names(name)) {
          require(doc.tree.collect { case c: Defn.Class => c.name.value } == List(name), s"Unexpected suite structure: $source")
          val destination = Paths.get("modules/core/src/test/scala/com/histmersion/api", filename)
          require(!Files.exists(destination), s"Refuse to overwrite $destination")
          Files.createDirectories(destination.getParent)
          Files.write(destination, doc.input.text.getBytes(UTF_8))
          require(Files.readString(destination, UTF_8) == doc.input.text)
          Files.delete(source)
        }
    }
    Patch.empty
  }
  override def afterComplete(): Unit = names.foreach { name =>
    require(!Files.exists(Paths.get(s"modules/api/src/test/scalajvm/com/histmersion/api/$name.scala")), s"Suite was not relocated: $name")
  }
}
