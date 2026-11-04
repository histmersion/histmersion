import scalafix.v1._
import com.typesafe.config.ConfigFactory
import java.nio.file.{Files,Paths}
import scala.jdk.CollectionConverters._

class SourceExamplePreflight extends SyntacticRule("SourceExamplePreflight") {
  override def afterComplete(): Unit = {
    val stale = List(
      "tools/refactor/capability-source-examples.conf" -> "CapabilityAccess.examples",
      "tools/refactor/source-contracts.conf" -> "SourceContracts.examples"
    ).flatMap { case(file,key) =>
      ConfigFactory.parseFile(Paths.get(file).toFile).resolve().getConfigList(key).asScala.flatMap { e =>
        val path = e.getString("path")
        val line = e.getInt("line")
        val snippet = e.getString("snippet")
        val source = Files.readAllLines(Paths.get(path)).asScala.toList
        if(source.lift(line-1).exists(_.contains(snippet))) None
        else Some(s"$file: $path:$line; snippet=$snippet; matching-lines=" +
          source.zipWithIndex.collect { case(s,i) if s.contains(snippet) => i+1 }.mkString(","))
      }
    }
    stale.foreach(println)
    require(stale.isEmpty,"Stale source example coordinates; review before starting the full scan")
    println("Source example coordinates verified; classifications remain checked by their detector runs")
  }
}
