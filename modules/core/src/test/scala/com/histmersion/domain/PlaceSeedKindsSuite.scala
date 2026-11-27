package com.histmersion
package domain
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.RawJson

import com.histmersion.util.problem.Text.sentence
import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.domain.catalogue.places.PlaceReview

import com.histmersion.api.catalogue.Lpf

import java.nio.file.{Files, Path}
import com.histmersion.domain.catalogue.Place
import com.histmersion.lint.{tolerated, LintRule}

/** The committed Pleiades seed and `lint --rule place-kind` agree (2026-10-09): `db/places/build.py` derives every kind
  * with the same type rules as [[PlaceReview]], so the lint proposes no change to a freshly seeded place — a re-seed
  * never undoes a fix — and no withdrawn duplicate is left to merge.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class PlaceSeedKindsSuite extends munit.FunSuite:

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private lazy val pleiades: List[api.catalogue.Place.Input] =
    val root = Iterator
      .iterate(Path.of(sys.props("user.dir")).toAbsolutePath)(_.getParent)
      .takeWhile(_ != null)
      .find(d => Files.isDirectory(d.resolve("db/places")))
      .getOrElse(fail("no db/places above the working directory"))
    Lpf
      .parse(
        Files.readString(root.resolve("db/places/03-pleiades.json")),
        api.catalogue.Place.KindValue(Place.Kind.Region)
      )
      .fold(e => fail(e.sentence), identity)

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def prop(in: api.catalogue.Place.Input, key: String): List[String] =
    in.place.properties
      .flatMap(p => RawJson.parse(p.value).toOption)
      .flatMap(_.cursor.get[String](key).toOption)
      .toList
      .flatMap(_.split(",").toList)
      .map(_.trim)
      .filter(_.nonEmpty)

  test("every Pleiades place's kind is the one the place-kind rule gives it"):
    val proposals = pleiades.filter(_.place.geometry.exists(_.value.contains("\"Point\""))).flatMap { in =>
      val e = PlaceReview.Evidence(
        PlaceReview.Evidence.Id(in.place.id.value),
        PlaceReview.Evidence.Label(in.place.label.value),
        Place.Kind.Wire(in.place.kind.value),
        prop(in, "feature_types").map(PlaceReview.Evidence.FeatureTypes(_)),
        prop(in, "time_periods").map(PlaceReview.Evidence.TimePeriods(_)),
        in.place.period.map(p => DeepTime.SignedYear(p.start.value)),
        in.place.period.map(p => DeepTime.SignedYear(p.end.value))
      )
      PlaceReview.propose(e).filter(_.fixable)
    }
    assertEquals(proposals.map(_.describe).take(10), Nil, s"${proposals.size} proposal(s) for the committed seed")

  test("the seed carries the reclassified kinds and none of Pleiades' withdrawn duplicates"):
    val byId = pleiades.map(in => in.place.id -> in.place).toMap
    assertEquals(
      byId.get(api.catalogue.Place.Id("pleiades_330976685")).map(_.kind),
      Some(Place.Kind.People),
      "Aiantis"
    )
    assertEquals(
      byId.get(api.catalogue.Place.Id("pleiades_992074")).map(_.kind),
      Some(Place.Kind.Polity),
      "Viennensis (diocese)"
    )
    assertEquals(
      byId.get(api.catalogue.Place.Id("pleiades_580062")).map(_.kind),
      Some(Place.Kind.Site),
      "Peiraieus"
    )
    assertEquals(
      byId.get(api.catalogue.Place.Id("pleiades_307963010")).map(_.kind),
      Some(Place.Kind.Region),
      "Regio I (Porta Capena)"
    )
    assert(
      !byId.contains(api.catalogue.Place.Id("pleiades_981515")),
      "Withdrawn (duplicate): Creta et Cyrene"
    )
    assert(pleiades.forall(in => PlaceReview.duplicateOf(PlaceReview.Evidence.Label(in.place.label.value)).isEmpty))
