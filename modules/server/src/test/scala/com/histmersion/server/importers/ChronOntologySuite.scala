package com.histmersion.server.importers

import com.histmersion.core.*

class ChronOntologySuite extends munit.FunSuite:

  private val fixture = """{
    "results": [
      {
        "resource": {
          "id": "NvftiJLCwhhO",
          "names": {"en": ["Middle Helladic"], "de": ["Mittelhelladisch"]},
          "types": ["material_culture"],
          "hasTimespan": [{
            "begin": {"notBefore": "-2100", "notAfter": "-2050"},
            "end": {"at": "-1700"}
          }]
        },
        "regions": [{"en": "Southern Aegean"}, {"en": "Europe"}]
      },
      {
        "resource": {
          "id": "abc123def",
          "names": {"en": ["Ptolemaic Period"], "ar": ["العصر البطلمي"]},
          "types": ["political"],
          "hasTimespan": [{
            "begin": {"at": "-305"},
            "end": {"at": "-30"}
          }]
        },
        "regions": [{"en": "Arab Republic of Egypt"}]
      },
      {
        "resource": {
          "id": "xyz789",
          "names": {"de": ["Quartär"]},
          "types": ["geological"],
          "hasTimespan": [{
            "begin": {"at": "-2588000"},
            "end": {"at": "not specified"}
          }]
        },
        "regions": []
      },
      {
        "resource": {
          "id": "noname1",
          "names": {},
          "types": [],
          "hasTimespan": []
        },
        "regions": []
      }
    ],
    "total": 4
  }"""

  test("transform parses valid fixture"):
    val result = ChronOntologyImporter.transform(fixture)
    assert(result.isRight, result.left.getOrElse(""))
    assertEquals(result.toOption.get.length, 3) // 4th has no name → skipped

  test("IDs are prefixed with chrono_"):
    val items = ChronOntologyImporter.transform(fixture).toOption.get
    assert(items.forall(_.place.id.startsWith("chrono_")))

  test("English name is preferred"):
    val items = ChronOntologyImporter.transform(fixture).toOption.get
    assertEquals(items.head.place.label, "Middle Helladic")

  test("falls back to non-English name"):
    val items = ChronOntologyImporter.transform(fixture).toOption.get
    val quartar = items.find(_.place.id == "chrono_xyz789").get
    assertEquals(quartar.place.label, "Quartär")

  test("period extracted from begin/end"):
    val items = ChronOntologyImporter.transform(fixture).toOption.get
    val ptolemaic = items.find(_.place.id == "chrono_abc123def").get
    assertEquals(ptolemaic.place.period, Some(Period(-305, -30)))

  test("period with notBefore/notAfter uses outer bounds"):
    val items = ChronOntologyImporter.transform(fixture).toOption.get
    val mh = items.head
    assertEquals(mh.place.period, Some(Period(-2100, -1700)))

  test("period with 'not specified' end uses start as point"):
    val items = ChronOntologyImporter.transform(fixture).toOption.get
    val geo = items.find(_.place.id == "chrono_xyz789").get
    assertEquals(geo.place.period, Some(Period(-2588000, -2588000)))

  test("political type maps to polity kind"):
    val items = ChronOntologyImporter.transform(fixture).toOption.get
    val ptolemaic = items.find(_.place.id == "chrono_abc123def").get
    assertEquals(ptolemaic.place.kind, PlaceKind.Polity)

  test("geological type maps to feature kind"):
    val items = ChronOntologyImporter.transform(fixture).toOption.get
    val geo = items.find(_.place.id == "chrono_xyz789").get
    assertEquals(geo.place.kind, PlaceKind.Feature)

  test("material_culture type maps to region kind"):
    val items = ChronOntologyImporter.transform(fixture).toOption.get
    assertEquals(items.head.place.kind, PlaceKind.Region)

  test("alternate names are recorded"):
    val items = ChronOntologyImporter.transform(fixture).toOption.get
    val mh = items.head
    assert(mh.names.exists(n => n.name == "Middle Helladic" && n.lang == "en"))
    assert(mh.names.exists(n => n.name == "Mittelhelladisch" && n.lang == "de"))

  test("source is set"):
    val items = ChronOntologyImporter.transform(fixture).toOption.get
    assert(items.forall(_.place.source.exists(_.contains("ChronOntology"))))

  test("properties contain chronontologyId"):
    val items = ChronOntologyImporter.transform(fixture).toOption.get
    val props = items.head.place.properties.getOrElse("")
    assert(props.contains("NvftiJLCwhhO"), props)

  test("properties contain region when available"):
    val items = ChronOntologyImporter.transform(fixture).toOption.get
    val ptolemaic = items.find(_.place.id == "chrono_abc123def").get
    val props = ptolemaic.place.properties.getOrElse("")
    assert(props.contains("Egypt"), props)

  test("nameless periods are skipped"):
    val items = ChronOntologyImporter.transform(fixture).toOption.get
    assert(!items.exists(_.place.id == "chrono_noname1"))

  test("transform is idempotent"):
    val r1 = ChronOntologyImporter.transform(fixture)
    val r2 = ChronOntologyImporter.transform(fixture)
    assertEquals(r1, r2)

  test("transform returns Left for invalid JSON"):
    val result = ChronOntologyImporter.transform("not json")
    assert(result.isLeft)

  test("transform returns Left for missing results key"):
    val result = ChronOntologyImporter.transform("""{"foo": "bar"}""")
    assert(result.isLeft)

  test("GazetteerImporter.byName resolves chronontology"):
    assert(GazetteerImporter.byName("chronontology").isDefined)
