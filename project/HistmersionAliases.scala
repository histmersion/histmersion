import kubuszok.sbt.Aliases
import sbt.*

class HistmersionAliases(matrices: Seq[ProjectMatrix], jvmProjects: Seq[Project], jsProjects: Seq[Project])
    extends Aliases(published = Seq.empty, testOnly = matrices) {

  private def ids(platform: String, scalaBinary: String): Vector[String] = {
    val plain = if scalaBinary != "3" then Nil else if platform == "JS" then jsProjects else jvmProjects
    (projectIds(matrices, platform, scalaBinary) ++ plain.map(_.id)).distinct
  }

  override def test(platform: String, scalaBinary: String): String =
    ids(platform, scalaBinary).map(id => s"$id/$testTask").mkString(" ; ")

  override def ci(platform: String, scalaBinary: String): String = {
    val all = ids(platform, scalaBinary)
    (all.map(id => s"$id/Test/compile") ++ all.map(id => s"$id/$testTask")).mkString(" ; ")
  }
}
