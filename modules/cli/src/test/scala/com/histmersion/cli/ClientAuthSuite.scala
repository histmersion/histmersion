package com.histmersion.cli

import com.sun.net.httpserver.{HttpExchange, HttpServer}
import java.net.InetSocketAddress
import java.nio.file.Files

/** #767: the file downloads (`backups download`, `original`) are built outside the typed `call`, and
  * `downloadBackup` sent no bearer token. A stand-in server that wants the key on EVERY request — as
  * one behind an authenticating proxy does — answers 401 to a request without it. No database. */
class ClientAuthSuite extends munit.FunSuite:

  private val key = "s3cret"

  /** Serves `/api/backups/<file>` and `/api/sources/<ck>/original`, both only to `Bearer s3cret`. */
  private def withServer[A](body: String => A): A =
    val srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    srv.createContext("/", (ex: HttpExchange) =>
      val authed = ex.getRequestHeaders.getFirst("Authorization") == s"Bearer $key"
      val path   = ex.getRequestURI.getPath
      val (code, bytes, headers) =
        if !authed then (401, """{"message":"unauthorized"}""".getBytes, Nil)
        else if path.startsWith("/api/backups/") then (200, "DUMP".getBytes, Nil)
        else if path.endsWith("/original") then
          (200, "%PDF".getBytes, List("Content-Disposition" -> "attachment; filename=\"zz.pdf\""))
        else (404, Array.emptyByteArray, Nil)
      headers.foreach((k, v) => ex.getResponseHeaders.add(k, v))
      ex.getResponseHeaders.add("Content-Type", if code == 200 then "application/octet-stream" else "application/json")
      ex.sendResponseHeaders(code, if bytes.isEmpty then -1 else bytes.length.toLong)
      if bytes.nonEmpty then ex.getResponseBody.write(bytes)
      ex.close())
    srv.start()
    try body(s"http://127.0.0.1:${srv.getAddress.getPort}")
    finally srv.stop(0)

  test("downloadBackup sends the bearer token"):
    withServer { url =>
      val dest = Files.createTempFile("zz-backup", ".dump").toFile
      try
        val got = Client(url, Some(key)).downloadBackup("histmersion-20260101T000000Z.dump", dest)
        assert(got.isRight, got.toString)
        assertEquals(Files.readString(dest.toPath), "DUMP")
      finally dest.delete()
    }

  test("original sends the bearer token"):
    withServer { url =>
      val dest = Files.createTempFile("zz-original", ".bin").toFile
      try
        val got = Client(url, Some(key)).original("zz_ck", dest)
        assert(got.isRight, got.toString)
        assertEquals(Files.readString(dest.toPath), "%PDF")
      finally dest.delete()
    }

  test("without the token the refusal names the missing API key"):
    withServer { url =>
      val dest = Files.createTempFile("zz-backup", ".dump").toFile
      try
        val got = Client(url).downloadBackup("x.dump", dest)
        assert(got.left.exists(_.message.contains("API key")), got.toString)
      finally dest.delete()
    }
