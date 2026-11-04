import scalafix.v1._
import scala.meta._

class AddLauncherOutputTests extends SyntacticRule("AddLauncherOutputTests") {
  private implicit val dialect: Dialect = dialects.Scala3
  private val resolver = """test("resolver exit boundary supplies errors and preserves failure status") {
    val out = List.newBuilder[String]
    val err = List.newBuilder[String]
    assertEquals(DependencyResolver.exitCode(List("nothing"), s => { out += s; () }, s => { err += s; () }), 1)
    assertEquals(out.result(), Nil)
    assert(err.result().head.startsWith("usage: pluginResolver"))
    val failures = List.newBuilder[String]
    var calls = 0
    val status = DependencyResolver.exitCode(List("nothing"), _ => (), s => {
      calls += 1
      if calls == 1 then throw new IllegalArgumentException("sink failed")
      failures += s
      ()
    })
    assertEquals(status, 3)
    assertEquals(failures.result(), List("pluginResolver: IllegalArgumentException: sink failed"))
  }"""
  private val relink = """test("relink exit boundary preserves JSON, usage and failure channels") {
    val out = List.newBuilder[String]
    val err = List.newBuilder[String]
    val emit: String => Unit = s => { out += s; () }
    val reject: String => Unit = s => { err += s; () }
    assertEquals(RelinkPlugins.exitCode(List("describe", hello.toString), emit, reject), 0)
    assertEquals(out.result(), List(PluginKit.renderJars(List(RelinkPlugins.describe(hello, loader)))))
    assertEquals(err.result(), Nil)
    out.clear()
    assertEquals(RelinkPlugins.exitCode(Nil, emit, reject), 1)
    assertEquals(out.result(), Nil)
    assert(err.result().head.startsWith("usage:"))
    err.clear()
    assertEquals(RelinkPlugins.exitCode(List("describe", hello.toString), _ => throw new IllegalArgumentException("sink failed"), reject), 3)
    assertEquals(err.result(), List("pluginTool: IllegalArgumentException: sink failed"))
  }

  tmp.test("relink exit boundary emits only the plugin set digest") { d =>
    val kit = kitWith(d, Map.empty)
    val jar = d.resolve("output.jar")
    val record = d.resolve("record.json")
    val out = List.newBuilder[String]
    val err = List.newBuilder[String]
    assertEquals(RelinkPlugins.exitCode(List("plugin-set", jar.toString, record.toString, "jvm", kit.toString, hello.toString), s => { out += s; () }, s => { err += s; () }), 0)
    val expected = PluginKit.digest(List(hello.getFileName.toString -> RelinkPlugins.sha256(hello)))
    assertEquals(out.result(), List(expected))
    assertEquals(err.result(), Nil)
    assert(Files.exists(jar))
    assert(Files.readString(record).contains(expected))
  }"""
  override def fix(implicit doc: SyntacticDocument): Patch = {
    val path = doc.input match {
      case Input.File(p, _) => p.toString
      case Input.VirtualFile(p, _) => p
      case x => x.toString
    }
    doc.tree.collect {
      case c: Defn.Class if (path.endsWith("/pluginresolver/PluginResolverSuite.scala") && c.name.value == "PluginResolverSuite") ||
          (path.endsWith("/plugins/PluginToolSuite.scala") && c.name.value == "PluginToolSuite") =>
        val code = if (c.name.value == "PluginResolverSuite") resolver else relink
        val stats = ("{\n" + code + "\n}").parse[Term].get.asInstanceOf[Term.Block].stats
        val marker = stats.head.collect { case Lit.String(s) => s }.head
        if (c.collect { case Lit.String(s) if s == marker => s }.nonEmpty) Patch.empty
        else Patch.addRight(c.templ.stats.last, "\n\n  " + code).atomic
    }.asPatch
  }
}
