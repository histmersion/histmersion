package com.histmersion.cli

import com.histmersion.api.automation.GoalsExport
import com.sun.net.httpserver.{HttpExchange, HttpServer}
import java.net.InetSocketAddress
import java.nio.file.{Files, Path}

/** Task #906: `queue export <dir>` and `export <dir>` (both through [[Ops.writeMeta]]) write `GOALS.md` beside the
  * three queue files, and `queue import <dir>` ([[Ops.readMeta]]) never sends it anywhere. A stand-in server answers
  * the export routes and records every request. No database.
  */
class MetaFilesSuite extends munit.FunSuite:

  private def withServer[A](body: (Client.ServerUrl, () => List[String]) => A): A =
    val seen = java.util.concurrent.ConcurrentLinkedQueue[String]()
    val srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    srv.createContext(
      "/",
      (ex: HttpExchange) =>
        val path = ex.getRequestURI.getPath
        seen.add(s"${ex.getRequestMethod} $path")
        val (code, text, ctype) = path match
          case "/api/export/goals" => (200, s"${GoalsExport.Header}\n\ngoals body\n", "text/plain; charset=UTF-8")
          case p if p.startsWith("/api/queue/") && p.endsWith("/export") =>
            (200, s"# ${p.split('/')(3)}\n", "text/plain; charset=UTF-8")
          case p if p.startsWith("/api/queue/") && p.endsWith("/import") =>
            (200, s"""{"list":"${p.split('/')(3)}","items":1,"notes":0,"replaced":0}""", "application/json")
          case _ => (404, """{"message":"no route"}""", "application/json")
        val bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        ex.getResponseHeaders.add("Content-Type", ctype)
        ex.sendResponseHeaders(code, bytes.length.toLong)
        ex.getResponseBody.write(bytes)
        ex.close()
    )
    srv.start()
    try body(Client.ServerUrl(s"http://127.0.0.1:${srv.getAddress.getPort}"), () => seen.toArray.toList.map(_.toString))
    finally srv.stop(0)

  private def tempDir(): Path = Files.createTempDirectory("zz-meta")

  test("writeMeta writes GOALS.md beside ACQUIRE.md, TASKS.md and LOG.md"):
    withServer { (url, _) =>
      val dir = tempDir().resolve("meta") // not there yet: created on the way
      val got = Ops.writeMeta(Client(url), dir)
      assertEquals(got, Right(List("ACQUIRE.md", "TASKS.md", "LOG.md", "GOALS.md")))
      assertEquals(Files.readString(dir.resolve("GOALS.md")), s"${GoalsExport.Header}\n\ngoals body\n")
      assertEquals(Files.readString(dir.resolve("TASKS.md")), "# tasks\n")
    }

  test("a server without the goals export fails the write instead of leaving a stale GOALS.md unnoticed"):
    val srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    srv.createContext(
      "/",
      (ex: HttpExchange) =>
        val path = ex.getRequestURI.getPath
        val queue = path.startsWith("/api/queue/") && path.endsWith("/export")
        val (code, bytes) = if queue then (200, "x\n".getBytes) else (404, """{"message":"no route"}""".getBytes)
        ex.sendResponseHeaders(code, bytes.length.toLong)
        ex.getResponseBody.write(bytes)
        ex.close()
    )
    srv.start()
    try assert(Ops.writeMeta(Client(Client.ServerUrl(s"http://127.0.0.1:${srv.getAddress.getPort}")), tempDir()).isLeft)
    finally srv.stop(0)

  test("readMeta imports the three queue files and never sends GOALS.md"):
    withServer { (url, seen) =>
      val dir = tempDir()
      List("ACQUIRE.md", "TASKS.md", "LOG.md", "GOALS.md").foreach(f => Files.writeString(dir.resolve(f), "- [ ] x\n"))
      val got = Ops.readMeta(Client(url), dir, replace = com.histmersion.api.Api.Overwrite(true))
      assert(got.isRight, got.toString)
      assertEquals(
        seen(),
        List("POST /api/queue/acquire/import", "POST /api/queue/tasks/import", "POST /api/queue/log/import")
      )
      assert(!got.toOption.get.exists(_.contains("GOALS")), got.toString)
    }
