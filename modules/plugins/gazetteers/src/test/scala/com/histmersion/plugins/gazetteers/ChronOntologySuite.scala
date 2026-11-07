package com.histmersion.plugins.gazetteers

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.plugin.*

class ChronOntologySuite extends munit.FunSuite:

  private val fixture =
    DownloadedGazetteer.Text(scala.io.Source.fromResource("gazetteers/chronontology.json")(using scala.io.Codec.UTF8).mkString)

  private def period(a: Int, b: Int): Option[Period] = Period.from(Year(a), Year(b)).toOption

  test("transform parses valid fixture"):
    val result = ChronOntology.read(fixture)
    assert(result.isRight, result.left.getOrElse(""))
    assertEquals(result.toOption.get.length, 3) // 4th has no name → skipped

  test("IDs are prefixed with chrono_"):
    val items = ChronOntology.read(fixture).toOption.get
    assert(items.forall(_.id.value.startsWith("chrono_")))

  test("English name is preferred"):
    val items = ChronOntology.read(fixture).toOption.get
    assertEquals(items.head.label, "Middle Helladic")

  test("falls back to non-English name"):
    val items = ChronOntology.read(fixture).toOption.get
    val quartar = items.find(_.id.value == "chrono_xyz789").get
    assertEquals(quartar.label, "Quartär")

  test("period extracted from begin/end"):
    val items = ChronOntology.read(fixture).toOption.get
    val ptolemaic = items.find(_.id.value == "chrono_abc123def").get
    assertEquals(ptolemaic.period, period(-305, -30))

  test("period with notBefore/notAfter uses outer bounds"):
    val items = ChronOntology.read(fixture).toOption.get
    val mh = items.head
    assertEquals(mh.period, period(-2100, -1700))

  test("period with 'not specified' end uses start as point"):
    val items = ChronOntology.read(fixture).toOption.get
    val geo = items.find(_.id.value == "chrono_xyz789").get
    assertEquals(geo.period, period(-2588000, -2588000))

  test("political type maps to polity kind"):
    val items = ChronOntology.read(fixture).toOption.get
    val ptolemaic = items.find(_.id.value == "chrono_abc123def").get
    assertEquals(ptolemaic.kind, "polity")

  test("geological type maps to feature kind"):
    val items = ChronOntology.read(fixture).toOption.get
    val geo = items.find(_.id.value == "chrono_xyz789").get
    assertEquals(geo.kind, "feature")

  test("material_culture type maps to region kind"):
    val items = ChronOntology.read(fixture).toOption.get
    assertEquals(items.head.kind, "region")

  test("alternate names are recorded"):
    val items = ChronOntology.read(fixture).toOption.get
    val mh = items.head
    assert(mh.names.exists(n => n.name.value == "Middle Helladic" && n.lang.value == "en"))
    assert(mh.names.exists(n => n.name.value == "Mittelhelladisch" && n.lang.value == "de"))

  // the host stores the dataset's credit with every place that states no source of its own
  test("source is set"):
    val items = ChronOntology.read(fixture).toOption.get
    assert(ChronOntology.rights.credit.value.contains("ChronOntology"))
    assert(items.forall(_.source.isEmpty))

  test("properties contain chronontologyId"):
    val items = ChronOntology.read(fixture).toOption.get
    val props = items.head.properties.map(_.value).getOrElse("")
    assert(props.contains("NvftiJLCwhhO"), props)

  test("properties contain region when available"):
    val items = ChronOntology.read(fixture).toOption.get
    val ptolemaic = items.find(_.id.value == "chrono_abc123def").get
    val props = ptolemaic.properties.map(_.value).getOrElse("")
    assert(props.contains("Egypt"), props)

  test("nameless periods are skipped"):
    val items = ChronOntology.read(fixture).toOption.get
    assert(!items.exists(_.id.value == "chrono_noname1"))

  test("transform is idempotent"):
    val r1 = ChronOntology.read(fixture)
    val r2 = ChronOntology.read(fixture)
    assertEquals(r1, r2)

  test("transform returns Left for invalid JSON"):
    val result = ChronOntology.read(com.histmersion.plugins.gazetteers.DownloadedGazetteer.Text("not json"))
    assert(result.isLeft)

  test("transform returns Left for missing results key"):
    val result = ChronOntology.read(DownloadedGazetteer.Text("""{"foo": "bar"}"""))
    assert(result.isLeft)

  test("the gazetteer-chronontology plugin registers the source chronontology"):
    val h = com.histmersion.plugin.testkit.PluginHarness
      .load(com.histmersion.plugin.Plugin.Info.Id("gazetteer-chronontology"), http = com.histmersion.plugin.testkit.HttpMode.Offline, env = Map.empty)
      .fold(e => fail(e.value), identity)
    assertEquals(h.gazetteerSources, List("chronontology"))
