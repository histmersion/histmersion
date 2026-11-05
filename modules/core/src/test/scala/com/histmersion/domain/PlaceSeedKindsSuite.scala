package com.histmersion.domain

import com.histmersion.domain.catalogue.places.PlaceReview

import com.histmersion.api.catalogue.Lpf

import java.nio.file.{Files, Path}
import com.histmersion.domain.catalogue.Place

/** The committed Pleiades seed and `lint --rule place-kind` agree (2026-10-09): `db/places/build.py` derives every kind
  * with the same type rules as [[PlaceReview]], so the lint proposes no change to a freshly seeded place — a re-seed
  * never undoes a fix — and no withdrawn duplicate is left to merge.
  */
class PlaceSeedKindsSuite extends munit.FunSuite:

  private lazy val pleiades: List[com.histmersion.api.catalogue.Place.Input] =
    val root = Iterator
      .iterate(Path.of(sys.props("user.dir")).toAbsolutePath)(_.getParent)
      .takeWhile(_ != null)
      .find(d => Files.isDirectory(d.resolve("db/places")))
      .getOrElse(fail("no db/places above the working directory"))
    Lpf
      .parse(Files.readString(root.resolve("db/places/03-pleiades.json")), Place.Kind.Region)
      .fold(e => fail(e), identity)

  private def prop(in: com.histmersion.api.catalogue.Place.Input, key: String): List[String] =
    in.place.properties
      .flatMap(p => io.circe.parser.parse(p).toOption)
      .flatMap(_.hcursor.get[String](key).toOption)
      .toList
      .flatMap(_.split(",").toList)
      .map(_.trim)
      .filter(_.nonEmpty)

  test("every Pleiades place's kind is the one the place-kind rule gives it"):
    val proposals = pleiades.filter(_.place.geometry.exists(_.contains("\"Point\""))).flatMap { in =>
      val e = PlaceReview.Evidence(
        in.place.id.value,
        in.place.label,
        in.place.kind,
        prop(in, "feature_types"),
        prop(in, "time_periods"),
        in.place.period.map(_.start),
        in.place.period.map(_.end)
      )
      PlaceReview.propose(e).filter(_.fixable)
    }
    assertEquals(proposals.map(_.describe).take(10), Nil, s"${proposals.size} proposal(s) for the committed seed")

  test("the seed carries the reclassified kinds and none of Pleiades' withdrawn duplicates"):
    val byId = pleiades.map(in => in.place.id -> in.place).toMap
    assertEquals(byId.get(com.histmersion.api.catalogue.Place.Id("pleiades_330976685")).map(_.kind), Some(Place.Kind.People), "Aiantis")
    assertEquals(byId.get(com.histmersion.api.catalogue.Place.Id("pleiades_992074")).map(_.kind), Some(Place.Kind.Polity), "Viennensis (diocese)")
    assertEquals(byId.get(com.histmersion.api.catalogue.Place.Id("pleiades_580062")).map(_.kind), Some(Place.Kind.Site), "Peiraieus")
    assertEquals(byId.get(com.histmersion.api.catalogue.Place.Id("pleiades_307963010")).map(_.kind), Some(Place.Kind.Region), "Regio I (Porta Capena)")
    assert(!byId.contains(com.histmersion.api.catalogue.Place.Id("pleiades_981515")), "Withdrawn (duplicate): Creta et Cyrene")
    assert(pleiades.forall(in => PlaceReview.duplicateOf(in.place.label).isEmpty))
