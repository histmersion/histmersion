import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class Preview extends SyntacticRule("Preview") {
  private implicit val dialect: Dialect = dialects.Scala3
  override def afterComplete(): Unit = {
    val root = Paths.get("").toAbsolutePath.normalize
    val target = root.resolve("target/pagination-preview-v3")
    val report = root.resolve("refactor-audit/models/pagination/preview-v3")
    require(!Files.exists(target) && !Files.exists(report),"Preserve existing preview")
    Files.createDirectories(target); Files.createDirectories(report)
    def rows(p: java.nio.file.Path) = Files.readAllLines(p).asScala.drop(1).map(_.split("\t",-1).toList).toList
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val sourceHashes = rows(root.resolve("refactor-audit/models/sources.tsv"))
    sourceHashes.foreach(r => require(hash(Files.readString(Paths.get(r.head))) == r(1),"Stale source inventory"))
    val catalogue = rows(root.resolve("refactor-audit/models/types.tsv"))
    val owners = List("OffsetPage#","library/SearchResult#","library/GeoHitPage#","automation/Goal.List#",
      "catalogue/Attestation.List#","catalogue/EventList#","media/AssetLink.List#","media/MediaList#","research/Article.List#")
      .map("com/histmersion/api/" + _)
    def quote(s: String) = "\"" + s + "\""
    val configs = owners.map { owner =>
      val entries = catalogue.filter(_.head == owner); require(entries.size == 1,"Missing record owner: " + owner)
      val entry = entries.head
      val tree = Files.readString(Paths.get(entry(1))).parse[Source].get
      val definitions = tree.collect { case c: Defn.Class if c.pos.startLine + 1 == entry(2).toInt => c }
      require(definitions.size == 1,"Ambiguous record AST: " + owner)
      val c = definitions.head; require(c.ctor.paramClauses.size == 1)
      val params = c.ctor.paramClauses.head.values.map(_.name.value)
      require(params.contains("total") && params.contains("offset"))
      val fields = params.filter(Set("total","shown","offset","next"))
      val mapping = fields.map(n => quote(n) + " = " + quote("com.histmersion.api." + (if(Set("total","shown")(n)) "RowCount" else "Offset"))).mkString(", ")
      "{ owner = " + quote(owner) + ", parameters = [" + params.map(quote).mkString(", ") + "], fields = {" + mapping + "}, optionalFields = [" + fields.filter(_ == "next").map(quote).mkString + "] }"
    }
    val config = "rules = [\"file:tools/refactor/ModelTypeRewrite.scala\"]\nModelTypeRewrite {\nrecords = [\n" + configs.mkString(",\n") +
      "\n]\nresults = { \"com/histmersion/api/OffsetPage#next().\" = \"com.histmersion.api.Offset\" }\noptionalResults = [\"com/histmersion/api/OffsetPage#next().\"]\n" +
      "scalarBoundaryOwners = [\"com/histmersion/api/OffsetPage#next().\", \"com/histmersion/api/OffsetPage#isAll().\", \"com/histmersion/api/OffsetPage.header().\", \"com/histmersion/api/OffsetPage.footer().\", \"com/histmersion/api/OffsetPage.markdown().\"]\n" +
      "scalarBoundaryCalls = { \"com/histmersion/api/Plural.noun().\" = [0] }\ncodecImport = \"com.histmersion.api.json.NewtypeCodecs.given\"\n}\n"
    Files.writeString(report.resolve("rule.conf"),config)
    List("Offset","RowCount").foreach { name =>
      val source = "package com.histmersion.api\n\nimport io.github.iltotore.iron.*\nimport io.github.iltotore.iron.constraint.any.Pure\n\ntype " + name + " = " + name + ".T\nobject " + name + " extends RefinedType[Int, Pure]\n"
      source.parse[Source].get
      val path = target.resolve("modules/api/src/main/scala/com/histmersion/api/" + name + ".scala")
      require(!Files.exists(root.resolve(root.relativize(target).resolve("modules/api/src/main/scala/com/histmersion/api/" + name + ".scala"))))
      require(!Files.exists(root.resolve("modules/api/src/main/scala/com/histmersion/api/" + name + ".scala")))
      Files.createDirectories(path.getParent); Files.writeString(path,source)
    }
    println("Generating isolated pagination preview across modules")
    val args = List("scala-cli","run","--server=false","-q","-S","2.13.18","--dep","ch.epfl.scala:scalafix-cli_2.13.18:0.14.9","--main-class","scalafix.cli.Cli","--",
      "--config",report.resolve("rule.conf").toString,"--scala-version","3.10.0","--sourceroot",root.toString,"--auto-classpath","--auto-classpath-roots","target/out","--files","modules",
      "--out-from",java.util.regex.Pattern.quote(root.resolve("modules").toString + "/"),"--out-to",java.util.regex.Matcher.quoteReplacement(target.resolve("modules").toString + "/"))
    val code = new ProcessBuilder(args.asJava).redirectErrorStream(true).redirectOutput(report.resolve("generation.log").toFile).start().waitFor()
    sourceHashes.foreach(r => require(hash(Files.readString(Paths.get(r.head))) == r(1),"Application source changed during preview"))
    require(code == 0,"Preview generation failed; inspect generation.log")
    val walk = Files.walk(target)
    val files = try walk.iterator.asScala.filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala")).toList finally walk.close()
    val plan = files.map { p =>
      val relative = target.relativize(p); val original = root.resolve(relative)
      List(relative.toString,if(Files.exists(original)) hash(Files.readString(original)) else "new",hash(Files.readString(p))).mkString("\t")
    }.sorted
    Files.writeString(report.resolve("plan.tsv"),("path\tbefore-sha256\tafter-sha256" :: plan).mkString("\n") + "\n")
    Files.writeString(report.resolve("status.txt"),"GENERATED_UNCOMPILED\n" + files.size + " preview source files; all original source hashes unchanged.\nNine result records plus Pure wire Offset/RowCount definitions. Wider record fields remain proposed propagation candidates, not newly adjudicated violations.\nArithmetic, factory parameter typing, codecs and caller closure still require compilation and repair before application.\n")
    println(Files.readString(report.resolve("status.txt")))
  }
}
