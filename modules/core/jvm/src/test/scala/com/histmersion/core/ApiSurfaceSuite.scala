package com.histmersion.core

import sttp.tapir.AnyEndpoint

import java.nio.file.{Files, Path}

/** The REST surface written down once (#1946): every endpoint the core API objects define is in
  * [[Api.all]] (the list the review found unused and incomplete), and every one of them appears in
  * README's REST table — so neither the list nor the table can fall behind the definitions. */
class ApiSurfaceSuite extends munit.FunSuite:

  /** The endpoint values an API object defines, by reflection over its accessors (test-only). */
  private def defined(module: AnyRef): List[(String, AnyEndpoint)] =
    module.getClass.getMethods.toList
      .filter(m => m.getParameterCount == 0 && classOf[sttp.tapir.Endpoint[?, ?, ?, ?, ?]].isAssignableFrom(m.getReturnType))
      .map(m => m.getName -> m.invoke(module).asInstanceOf[AnyEndpoint])

  private val modules: List[(String, AnyRef)] =
    List("Api" -> Api, "OpsApi" -> OpsApi, "AgentsApi" -> AgentsApi, "ManagersApi" -> ManagersApi,
      "UiApi" -> UiApi, "EventsApi" -> EventsApi, "AcquireApi" -> AcquireApi)

  /** `GET /api/sources/{citekey}` — how an endpoint is named here and in README. */
  private def key(e: AnyEndpoint): String =
    s"${e.method.map(_.method).getOrElse("GET")} ${e.showPathTemplate(showQueryParam = None)}"

  test("every endpoint the API objects define is in Api.all, once"):
    val all     = Api.all.map(key)
    val missing = modules.flatMap((o, m) => defined(m).collect { case (n, e) if !all.contains(key(e)) => s"$o.$n (${key(e)})" })
    assert(missing.isEmpty, s"add them to Api.all (or the module's own `all`):\n${missing.distinct.mkString("\n")}")
    assertEquals(all.diff(all.distinct), Nil, "an endpoint listed twice")

  test("README's endpoint index is Api.all (`just api-docs` regenerates it)"):
    val root   = Path.of(sys.props.getOrElse("histmersion.repoRoot", "."))
    val readme = Files.readString(root.resolve("README.md"))
    assertEquals(ApiIndex.current(readme), Some(ApiIndex.markdown(Api.all)), "run `just api-docs`")

  test("the index lists each path once, with its methods in a fixed order"):
    val md = ApiIndex.markdown(List(Api.getSource, Api.putSource, Api.health))
    assert(md.startsWith("3 endpoints on 2 paths:"), md)
    assert(md.contains("| `GET` · `PUT` | `/api/sources/{citekey}` |"), md)
    assertEquals(ApiIndex.splice("a\n" + ApiIndex.Begin + "\nold\n" + ApiIndex.End + "\nb", "new"),
      Right("a\n" + ApiIndex.Begin + "\nnew\n" + ApiIndex.End + "\nb"))
    assert(ApiIndex.splice("no markers", "x").isLeft)
