package com.histmersion
package util.problem

import scala.compiletime.constValueTuple
import scala.deriving.Mirror

class CasesSuite extends munit.FunSuite:
  import Problem.*
  import Text.sentence

  private inline def cases[T](using m: Mirror.SumOf[T]): Set[String] =
    constValueTuple[m.MirroredElemLabels].toList.map(label => s"$label").toSet

  private def caseOf(problem: Problem): String = problem.productPrefix

  test("the examples hold exactly one value of every case"):
    val all = Map(
      Group.Invalid -> cases[Invalid],
      Group.NotFound -> cases[NotFound],
      Group.Conflict -> cases[Conflict],
      Group.Refused -> cases[Refused],
      Group.Upstream -> cases[Upstream],
      Group.Io -> cases[Io],
      Group.Config -> cases[Config],
      Group.Plugin -> cases[Plugin],
      Group.Run -> cases[Run],
      Group.Content -> cases[Content],
      Group.Internal -> cases[Internal],
      Group.Unclassified -> Set("Unclassified"),
      Group.Stored -> cases[Stored],
      Group.Relayed -> Set("Relayed")
    )
    val shown = examples.map(p => p.group -> caseOf(p))
    assertEquals(shown.distinct.size, shown.size, "one value per case")
    assertEquals(shown.toSet, all.toList.flatMap((g, cs) => cs.map(g -> _)).toSet)
    assertEquals(cases[Problem], Group.values.map(_.productPrefix).toSet)

  test("there are eleven groups and three cases outside them"):
    assertEquals(Group.values.length, 14)
    assertEquals(examples.size, 125)

  test("damage, a flaw or an unread variant placed in a format's data is the corrupt problem of those three"):
    val at = Where("the header")
    assertEquals(Io.Damage.Foreign.in(Io.Format.PmTiles, at), Io.Corrupt(Io.Format.PmTiles, at, Io.Damage.Foreign))
    assertEquals(
      Io.Flaw.TooDeep.in(Io.Format.PmTiles, at),
      Io.Corrupt(Io.Format.PmTiles, at, Io.Damage.Structure(Io.Flaw.TooDeep))
    )
    assertEquals(
      Io.Variant.Tiled.in(Io.Format.GeoTiff, at),
      Io.Corrupt(Io.Format.GeoTiff, at, Io.Damage.Unsupported(Io.Variant.Tiled))
    )

  test("a relayed problem is its sentence"):
    assertEquals((Relayed(Sentence("the server is reindexing")): Problem).sentence, "the server is reindexing")

  test("an exception becomes an internal problem with its class and its message's first line, cut to 300"):
    val long = "x" * 400
    Problem.from(IllegalStateException(s"first line\nsecond line"), Where("backup"), Internal.Id("0a1b2c3d")) match
      case Internal.Unexpected(id, where, exception, reported) =>
        assertEquals(id, Internal.Id("0a1b2c3d"))
        assertEquals(where, Where("backup"))
        assertEquals(exception, Internal.ExceptionName("java.lang.IllegalStateException"))
        assertEquals(reported, External.Reported("java.lang.IllegalStateException: first line"))
      case other => fail(s"not unexpected: $other")
    Problem.from(RuntimeException(long), Where("x"), Internal.Id("x")) match
      case Internal.Unexpected(_, _, _, reported) => assertEquals(s"$reported".length, External.Reported.Limit)
      case other                                  => fail(s"not unexpected: $other")
    Problem.from(RuntimeException(), Where("x"), Internal.Id("x")) match
      case Internal.Unexpected(_, _, _, reported) =>
        assertEquals(reported, External.Reported("java.lang.RuntimeException"))
      case other => fail(s"not unexpected: $other")

  test("outside text is cut where it is captured"):
    assertEquals(s"${External.Stderr.tail("a" * 600 + "end\n")}".length, External.Stderr.Limit)
    assert(s"${External.Stderr.tail("a" * 600 + "end\n")}".endsWith("end"))
    assertEquals(s"${External.ReplyExcerpt.head("b" * 300)}".length, External.ReplyExcerpt.Limit)
    assertEquals(s"${External.ModelSaid.of("c" * 300)}".length, External.ModelSaid.Limit)

  test(
    "a problem found inside a part of an answer starts with that part, a proposal's place and an input's path alike"
  ):
    val finding = Where("finding 2 (Tin came by sea.)")
    assertEquals(
      (Content.Proposal(Content.Requirement.HistoricalReckoning, Where("period")): Problem).inside(finding),
      Content.Proposal(Content.Requirement.HistoricalReckoning, Where("finding 2 (Tin came by sea.): period"))
    )
    assertEquals(
      (Invalid.Missing.here(Field("quote")): Problem).inside(finding),
      Invalid.Missing(Field("quote"), Vector(Field("finding 2 (Tin came by sea.)")))
    )

  test("a problem found in a field of the request gets the field in front of its path"):
    val missing: Problem = Invalid.Missing.here(Field("dst"))
    assertEquals(
      missing.within(Field("edges")).within(Field("patch")),
      Invalid.Missing(Field("dst"), Vector(Field("patch"), Field("edges")))
    )
    val other: Problem = NotFound.Entity(TypeName("x.Y"), Raw("k"))
    assertEquals(other.within(Field("edges")), other)

  test("an upstream's failure on one part of what was asked names that part, in its sentence too"):
    val url = Upstream.Url("https://met.example/objects/7")
    val gone: Problem =
      Upstream.Http.here(Upstream.Name("met"), url, Upstream.Status(404), External.ReplyExcerpt("gone"))
    val found = gone.within(Field("object 7"))
    assertEquals(
      found,
      Upstream.Http(
        Upstream.Name("met"),
        url,
        Upstream.Status(404),
        External.ReplyExcerpt("gone"),
        Vector(Field("object 7"))
      )
    )
    assertEquals(found.sentence, s"object 7: met answered HTTP 404 at $url — gone")

  test("problems from a list: none is none, one is the problem itself, several are all of them in order"):
    val a: Problem = Invalid.Missing.here(Field("a"))
    val b: Problem = Invalid.Missing.here(Field("b"))
    assertEquals(Problems.from(Vector.empty), None)
    assertEquals(Problems.from(Vector(a)), Some(a: Problems))
    assertEquals(Problems.from(Vector(a, b)).map(_.toVector), Some(Vector(a, b)))
    assertEquals(Problems.from(Vector(a, b)), Some(Problems.of(a, b)))

  test("a reason is the sentence without the item or the input path, so that a report counts alike refusals once"):
    import Text.reason
    val notOpen = Plugin.Refusal.NotOpen(Field("isPublicDomain"), Vector(Raw("false")))
    val a = Plugin.Rejected(Plugin.Id("media-met"), Raw("248284"), notOpen)
    val b = Plugin.Rejected(Plugin.Id("media-met"), Raw("1"), notOpen)
    assertEquals(a.reason, "not Open Access (isPublicDomain is false)")
    assertEquals(a.sentence, "media-met did not take 248284: not Open Access (isPublicDomain is false)")
    assertEquals(a.reason, b.reason)
    val usage = Plugin.Refusal.NoOpenImage(Field("usage.access"), Vector(Raw("Usage conditions apply")))
    assertEquals(
      Plugin.Rejected(Plugin.Id("media-smithsonian"), Raw("k"), usage).reason,
      "no CC0 image (usage.access is Usage conditions apply)"
    )
    assertEquals(
      Plugin.Rejected(Plugin.Id("zq"), Raw("k"), Plugin.Refusal.LicenceNotAllowed(Raw("cc-by-nc"))).reason,
      "licence cc-by-nc not allowed"
    )
    assertEquals(
      Plugin.Rejected(Plugin.Id("zq"), Raw("k"), Plugin.Refusal.NotOpen(Field("rights"), Vector.empty)).reason,
      "not Open Access (rights is missing)"
    )
    assertEquals(Invalid.Missing(Field("credit"), Vector(Field("k1"))).reason, "credit is required")
    val year =
      Invalid.Unreadable(TypeName("Year"), Raw("x"), Vector(Raw("1300 BCE")), Vector(Field("from")))
    assertEquals(year.sentence, "from: 'x' is not a year (e.g. 1300 BCE)")
    assertEquals(year.reason, "'x' is not a year (e.g. 1300 BCE)")
    assertEquals(NotFound.Entity(TypeName("x.Commons.File"), Raw("File:A.jpg")).reason, "no such commons file")
    val other: Problem = Conflict.Busy(Conflict.Activity.Backup)
    assertEquals(other.reason, other.sentence)

  test("a busy Calibre GUI says to quit it; a key a plugin needs says which setting and what key"):
    assertEquals(
      Conflict.Busy(Conflict.Activity.CalibreGui).sentence,
      "the Calibre GUI is running — quit it first (calibredb needs exclusive access to the library)"
    )
    assertEquals(
      Config
        .Setting(
          SettingKey("histmersion.plugins.media-smithsonian.api-key"),
          Config.Fault.KeyNeeded(External.PluginSaid("an api.data.gov key"))
        )
        .sentence,
      "histmersion.plugins.media-smithsonian.api-key holds no key, and the plugin needs an api.data.gov key: set it to " +
        "a secret reference (\"env:<NAME>\" or a vault's, e.g. \"keepassxc:<entry>#password\"); it was not asked"
    )

  test(
    "only a missing entity, a listed one, a place's name, hand-set values, a download, a copy or a referred-to record is not found"
  ):
    assertEquals(
      examples.filter(_.isNotFound).map(_.productPrefix),
      Vector("Entity", "Copy", "Listed", "Name", "SetByHand", "Undownloaded", "Dangling")
    )
