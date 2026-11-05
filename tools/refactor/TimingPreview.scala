import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class TimingPreview extends SyntacticRule("TimingPreview") {
  override def afterComplete(): Unit = {
    implicit val dialect: Dialect = dialects.Scala3
    val timer = "com.histmersion.util.time.Timer"
    val duration = "scala.concurrent.duration.FiniteDuration"
    def millis(s: String) = s"$duration($s, scala.concurrent.duration.MILLISECONDS)"
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val frozen = Files.readAllLines(Paths.get("refactor-audit/wall-clock/tests-2/sources.tsv")).asScala.drop(1).map(_.split("\t")).map(a => a(0) -> a(1)).toMap
    val outputs = scala.collection.mutable.ListBuffer.empty[(String,String,String)]
    val evidence = scala.collection.mutable.ListBuffer.empty[String]
    var removed = 0
    def change(path: String)(f: (Source,(Tree,String) => Unit,(Int,String) => Unit) => Unit): Unit = {
      val source = Files.readString(Paths.get(path)); require(hash(source) == frozen(path))
      val tree = source.parse[Source].get
      val edits = scala.collection.mutable.ListBuffer.empty[(Int,Int,String)]
      def edit(t: Tree,s: String): Unit = { edits += ((t.pos.start,t.pos.end,s)); evidence += path + "\t" + (t.pos.startLine+1) + "\t" + t.structure.replace("\n","\\n") + "\t" + s.replace("\n","\\n") }
      f(tree,edit,(at,s) => edits += ((at,at,s)))
      val sorted = edits.toList.sortBy(_._1)
      require(sorted.sliding(2).forall { case List(a,b) => a._2 <= b._1; case _ => true })
      val next = sorted.reverse.foldLeft(source) { case(s,(a,b,v)) => s.patch(a,v,b-a) }
      val parsed = next.parse[Source].get
      def raw(t: Tree) = t.collect { case a: Term.Apply if Set("System.nanoTime","System.currentTimeMillis")(a.fun.syntax) => a }.size
      removed += raw(tree) - raw(parsed)
      outputs += ((path,source,next))
    }
    def term(t: Tree,before: String,after: String,e: (Tree,String) => Unit, count: Int = 1): Unit = {
      val shape = before.parse[Term].get.structure
      val hits = t.collect { case n: Term if n.structure == shape => n }
      require(hits.size == count,s"Unexpected count ${hits.size} for $before")
      hits.foreach(e(_,after))
    }
    def method(t: Tree,name: String): Defn.Def = {
      val hits = t.collect { case d: Defn.Def if d.name.value == name => d }; require(hits.size == 1); hits.head
    }
    def contextual(d: Defn.Def,insert: (Int,String) => Unit): Unit = insert(d.paramClauseGroups.last.paramClauses.last.pos.end,s"(using timer: $timer)")
    def ctor(t: Tree,name: String,insert: (Int,String) => Unit): Unit = {
      val cs = t.collect { case c: Defn.Class if c.name.value == name => c }; require(cs.size == 1)
      insert(cs.head.ctor.pos.end,s"(using timer: $timer)")
    }
    val domain = "modules/server/src/main/scala/com/histmersion/domain/"
    change("modules/core/src/main/scala/com/histmersion/domain/PassageFinder.scala") { (t,e,i) =>
      contextual(method(t,"find"),i)
      term(t,"System.nanoTime() + timeoutMs.max(1L) * 1_000_000L","timer.after(" + millis("timeoutMs.max(1L)") + ")",e)
      term(t,"System.nanoTime() > until","until.exceeded",e,2)
      val ps = t.collect { case p: Term.Param if p.name.value == "until" => p }; require(ps.size == 2)
      ps.foreach(p => e(p.decltpe.get,timer + ".Deadline"))
    }
    change("modules/db/src/main/scala/com/histmersion/domain/library/search/QueryRepo.scala") { (t,e,i) =>
      List("run","exec").foreach(n => e(method(t,n).paramClauseGroups.last.paramClauses.last,s"(using con: DbCon, timer: $timer)"))
      val start = t.collect { case v: Defn.Val if v.pats.exists(_.syntax == "started") => v }; require(start.size == 1)
      e(start.head.rhs,"timer.start()")
      term(t,"(System.nanoTime() - started) / 1000000L","started.elapsed.toMillis",e)
    }
    change(domain + "library/search/QueryService.scala") { (t,e,i) => contextual(method(t,"apply"),i); ctor(t,"Implementation",i) }
    change(domain + "catalogue/places/PlaceGeometry.scala") { (t,e,i) =>
      contextual(method(t,"apply"),i); ctor(t,"Implementation",i)
      term(t,"System.nanoTime() + math.max(0L, s.budgetMs) * 1000000L","timer.after(" + millis("math.max(0L, s.budgetMs)") + ")",e)
      term(t,"System.nanoTime() >= deadline","deadline.reached",e)
    }
    change(domain + "automation/agents/LatencyInterceptor.scala") { (t,e,i) =>
      ctor(t,"LatencyInterceptor",i)
      val start = t.collect { case v: Defn.Val if v.pats.exists(_.syntax == "t0") => v }; require(start.size == 1)
      e(start.head.rhs,"timer.start()")
      term(t,"((System.nanoTime() - t0) / 1000000).toInt","t0.elapsed.toMillis.toInt",e)
    }
    change(domain + "automation/agents/AgentsControllers.scala") { (t,e,i) =>
      val clock = t.collect { case v: Defn.Val if v.pats.exists(_.syntax == "clock") => v }; require(clock.size == 1)
      i(clock.head.pos.end,s"\n  val timer: $timer = site.timer\n  val pause: com.histmersion.util.time.Pause = site.pause")
      val start = t.collect { case v: Defn.Val if v.pats.exists(_.syntax == "t0") => v }; require(start.size == 1)
      e(start.head.rhs,"timer.start()")
      term(t,"(System.nanoTime() - t0) / 1000000","t0.elapsed.toMillis",e)
    }
    change(domain + "automation/autopilot/AutopilotService.scala") { (t,e,i) =>
      term(t,"System.currentTimeMillis() + 3L * 3600L * 1000L",s"agents.timer.after($duration(3L, scala.concurrent.duration.HOURS))",e)
      term(t,"System.currentTimeMillis() < deadline","!deadline.reached",e)
      term(t,"Thread.sleep(10000)","agents.pause.sleep(" + millis("10000L") + ")",e)
    }
    require(removed == 13 && outputs.size == 7)
    val out = Paths.get("refactor-audit/monotonic/preview-1"); require(!Files.exists(out)); Files.createDirectories(out)
    outputs.foreach { case(p,b,n) => List("before" -> b,"sources" -> n).foreach { case(dir,s) => val dest = out.resolve(dir).resolve(p); Files.createDirectories(dest.getParent); Files.writeString(dest,s) } }
    Files.write(out.resolve("plan.tsv"),("path\tbefore-sha256\tafter-sha256" :: outputs.toList.map { case(p,b,n) => s"$p\t${hash(b)}\t${hash(n)}" }).asJava)
    Files.write(out.resolve("shapes.tsv"),("path\tline\tast\treplacement" :: evidence.toList).asJava)
    println("Previewed all 13 timing occurrences in seven files; composition/caller propagation still required")
  }
}
