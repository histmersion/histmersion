package com.histmersion
package cli

import com.histmersion.api.Api
import com.histmersion.api.automation.Creation
import com.histmersion.api.library.{Citekey, Ingest, Source}
import sttp.client4.*
import sttp.model.Uri

class ClientStampSuite extends munit.FunSuite:

  private val agent = AgentIdentity(
    Some(Creation.Plugin("p")),
    Some(Creation.Workflow("w")),
    Some(Creation.Definition("d")),
    Some(Creation.Provider("x")),
    Some(Creation.Model("m"))
  )

  private def bodyOf[T](req: Request[T]): String = req.body match
    case StringBody(text, _, _) => text
    case other                  => fail(other.toString)

  private val upload = FunFixture[java.io.File](
    _ => java.nio.file.Files.createTempFile("upload", ".pdf").toFile,
    f => { val _ = f.delete() }
  )

  private val json = basicRequest.post(Uri.unsafeParse("http://h/api/sources/x")).body("""{"title":"T"}""")

  test("a write body is stamped via cli and with the caller's identity, keeping its content type"):
    val req = Client.stamped(Source.Patch.Request(), agent)(json.contentType("application/json"))
    val body = bodyOf(req)
    assert(body.contains("\"via\":\"cli\""), body)
    assert(body.contains("\"createdBy\":{\"plugin\":\"p\""), body)
    assert(body.contains("\"title\":\"T\""), body)
    assertEquals(req.headers.find(_.is("Content-Type")).map(_.value), Some("application/json"))

  test("a person's write says only how it came"):
    val body = bodyOf(Client.stamped(("ck", Source.Patch.Request()), AgentIdentity.Nobody)(json))
    assert(body.contains("\"via\":\"cli\""), body)
    assert(!body.contains("createdBy"), body)

  test("an input that names its own creator keeps it"):
    val own =
      Source.Patch.Request(createdBy = Some(Creation.By(Some(Creation.Plugin("own")))), via = Some(Creation.Web))
    val body = bodyOf(Client.stamped(own, agent)(json))
    assert(body.contains("\"via\":\"web\""), body)
    assert(body.contains("\"plugin\":\"own\""), body)

  upload.test("an upload's creator travels as query parameters, its body being the document"): file =>
    val params = Ingest.Params(Citekey("x"), createdBy = agent.createdBy, via = Some(Creation.Cli))
    val req = sttp.tapir.client.sttp4
      .SttpClientInterpreter()
      .toRequest(Api.ingest, Some(Uri.unsafeParse("http://h")))
      .apply((params, file))
    assertEquals(req.uri.paramsMap.get("via"), Some("cli"))
    assertEquals(req.uri.paramsMap.get("byPlugin"), Some("p"))
    assertEquals(req.uri.paramsMap.get("byModel"), Some("m"))
    assertEquals(req.uri.paramsMap.get("citekey"), Some("x"))

  test("a read is left as it is"):
    assertEquals(bodyOf(Client.stamped("ck", agent)(json)), """{"title":"T"}""")
