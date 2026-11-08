package com.histmersion
package upstreams

import io.circe.parser.parse as json
import com.histmersion.upstreams.wikidata.{WikidataEntities, WikidataStatement}
import com.histmersion.upstreams.testsupport.ComparedByValue.given
import com.histmersion.upstreams.wikidata.WikidataItem

/** Wikidata's `wbgetentities` as the media collections and the host's label lookup read it: the URL of a batch of
  * items, their picture statements and Commons category, the best statements of a property. Pure.
  */
class WikidataEntitiesSuite extends munit.FunSuite:

  private val entities = json("""{"entities":{
    "Q1":{"id":"Q1","claims":{
      "P18":[
        {"mainsnak":{"snaktype":"value","datavalue":{"value":"B.jpg"}},"rank":"normal"},
        {"mainsnak":{"snaktype":"value","datavalue":{"value":"A.jpg"}},"rank":"preferred"},
        {"mainsnak":{"snaktype":"value","datavalue":{"value":"Old.jpg"}},"rank":"deprecated"}],
      "P242":[{"mainsnak":{"snaktype":"somevalue"},"rank":"normal"},
              {"mainsnak":{"snaktype":"value","datavalue":{"value":"Map.svg"}},"rank":"normal"}],
      "P31":[{"mainsnak":{"snaktype":"value","datavalue":{"value":{"id":"Q5"}}},"rank":"normal"}],
      "P373":[{"mainsnak":{"snaktype":"value","datavalue":{"value":"Mycenae"}},"rank":"normal"}]}},
    "Q2":{"id":"Q2","missing":""}}}""").toOption.get

  test("wbgetentities: the picture statements kept, deprecated and no-value ones dropped, P373 read"):
    val items = WikidataEntities
      .items(entities, Set(WikidataStatement.Property("P18"), WikidataStatement.Property("P242")))
      .toOption
      .get
    assertEquals(items.keySet, Set("Q1"), "a missing item is left out")
    val q1 = items(WikidataItem.Id("Q1"))
    assertEquals(
      q1.statements.map(s => (s.property, s.file, s.preferred)),
      List(("P18", "B.jpg", false), ("P18", "A.jpg", true), ("P242", "Map.svg", false))
    )
    assertEquals(q1.commonsCategory, Some("Mycenae"))

  test("best: the preferred statements when there are any, else the normal ones, capped"):
    val st = WikidataEntities
      .items(entities, Set(WikidataStatement.Property("P18")))
      .toOption
      .get(WikidataItem.Id("Q1"))
      .statements
    assertEquals(WikidataStatement.best(st, WikidataStatement.BestPer(2)).map(_.file), List("A.jpg"))
    val normals = st.map(_.copy(preferred = WikidataStatement.Preferred(false)))
    assertEquals(WikidataStatement.best(normals, WikidataStatement.BestPer(1)).map(_.file), List("B.jpg"))
    assertEquals(WikidataStatement.best(normals, WikidataStatement.BestPer(0)), Nil)

  test("an API error (maxlag) is a Left with its code"):
    val err = json("""{"error":{"code":"maxlag","info":"Waiting for wdqs1013: 11 seconds lagged."}}""").toOption.get
    assertEquals(
      WikidataEntities
        .items(err, Set(WikidataStatement.Property("P18")))
        .left
        .toOption
        .map(_.value.takeWhile(_ != ':')),
      Some("maxlag")
    )

  test("the wbgetentities URL: ids joined and encoded, maxlag only when set"):
    val u = WikidataEntities.url(
      WikidataEntities.Api("https://wd/w/api.php"),
      List(WikidataItem.Id("Q1"), WikidataItem.Id("Q2")),
      WikidataEntities.Maxlag(5)
    )
    assert(
      u.value.startsWith(
        "https://wd/w/api.php?action=wbgetentities&format=json&formatversion=2&props=claims&ids=Q1%7CQ2"
      ),
      u
    )
    assert(u.value.endsWith("&maxlag=5"), u)
    assert(
      !WikidataEntities
        .url(WikidataEntities.Api("https://wd/w/api.php"), List(WikidataItem.Id("Q1")), WikidataEntities.Maxlag(0))
        .value
        .contains("maxlag")
    )
