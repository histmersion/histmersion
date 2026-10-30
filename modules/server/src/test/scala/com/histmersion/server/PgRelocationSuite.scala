package com.histmersion.server

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

class PgRelocationSuite extends munit.FunSuite:

  private def b(s: String) = s.getBytes(UTF_8)
  private val from = b("/build/env/default")

  test("text: every occurrence is replaced"):
    val (out, tooLong) =
      PgRelocation.patch(b("prefix=/build/env/default\nlibdir=/build/env/default/lib\n"), from, b("/app/pg"))
    assertEquals(String(out, UTF_8), "prefix=/app/pg\nlibdir=/app/pg/lib\n")
    assertEquals(tooLong, 0)

  test("binary: a padded path constant is rewritten in place, nothing after it moves"):
    val slot = b("/build/env/default/lib") ++ Array.fill[Byte](20)(0)
    val in = b("HEAD") ++ Array[Byte](0) ++ slot ++ b("TAIL") ++ Array[Byte](0)
    val (out, tooLong) = PgRelocation.patch(in, from, b("/somewhere/much/longer/pg"))
    assertEquals(out.length, in.length)
    assertEquals(tooLong, 0)
    val strings = String(out, UTF_8).split('\u0000').filter(_.nonEmpty).toList
    assertEquals(strings, List("HEAD", "/somewhere/much/longer/pg/lib", "TAIL"))
    assertEquals(out.takeRight(5).toList, (b("TAIL") :+ 0.toByte).toList)

  test("binary: a string that would not fit is left alone and counted"):
    val in = Array[Byte](0) ++ b("/build/env/default/bin") ++ Array[Byte](0) ++ b("X") ++ Array[Byte](0)
    val (out, tooLong) = PgRelocation.patch(in, from, b("/a/path/that/is/far/too/long/to/fit/here"))
    assertEquals(tooLong, 1)
    assertEquals(out.toList, in.toList)

  test("binary: several occurrences in one string (the configure flags) all move"):
    val s = b("'--prefix=/build/env/default' '--libdir=/build/env/default/lib'")
    val in = Array[Byte](0) ++ s ++ Array[Byte](0)
    val (out, _) = PgRelocation.patch(in, from, b("/p"))
    assertEquals(String(out, UTF_8).split('\u0000').filter(_.nonEmpty).toList, List("'--prefix=/p' '--libdir=/p/lib'"))

  test("relocate: follows the marker, updates it, and is a no-op once home"):
    val pg = Files.createTempDirectory("pgreloc")
    try
      val bin = Files.createDirectories(pg.resolve("bin"))
      Files.writeString(pg.resolve(PgRelocation.Marker), "/build/env/default")
      Files.write(
        bin.resolve("pg_config"),
        b("x") ++ Array[Byte](0) ++ b("/build/env/default/share") ++ Array.fill[Byte](236)(0)
      ) // a 260-byte slot, as conda pads them
      Files.writeString(bin.resolve("tool-config"), "prefix=/build/env/default\n")
      Files.writeString(bin.resolve("unrelated"), "nothing to see")
      val here = pg.toRealPath().toString
      val r = PgRelocation.relocate(pg, macOS = false)
      assertEquals(r, Some(PgRelocation.Result(files = 2, signed = 0, tooLong = 0)))
      assertEquals(Files.readString(pg.resolve(PgRelocation.Marker)), here)
      assertEquals(Files.readString(bin.resolve("tool-config")), s"prefix=$here\n")
      assert(String(Files.readAllBytes(bin.resolve("pg_config")), UTF_8).contains(s"$here/share\u0000"))
      assertEquals(PgRelocation.relocate(pg, macOS = false), None)
    finally deleteTree(pg)

  test("relocate: no marker means a pixi environment used in place — untouched"):
    val pg = Files.createTempDirectory("pgreloc")
    try assertEquals(PgRelocation.relocate(pg, macOS = false), None)
    finally deleteTree(pg)

  private def deleteTree(p: Path): Unit =
    val w = Files.walk(p)
    try
      w.sorted(java.util.Comparator.reverseOrder()).forEach { f =>
        val _ = Files.deleteIfExists(f)
      }
    finally w.close()
