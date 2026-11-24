package com.histmersion
package web.setup

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.settings.ConfigEdit
import com.histmersion.api.catalogue.{Latitude, Longitude, MapArea}
import com.histmersion.api.instance.{MapStartConfig, UiConfig}
import com.histmersion.api.instance.MapStartMode
import com.histmersion.api.instance.DebounceConfig
import com.histmersion.api.instance.TimeConfig
import com.histmersion.api.catalogue.Year
import com.histmersion.api.instance.EventsUiConfig
import com.histmersion.lint.{LintRule, tolerated}

@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
class SetupPlanSuite extends munit.FunSuite:

  private val before =
    SetupChoices(
      email = SetupChoices.Email(""),
      finders = Map(
        SetupChoices.FindersKey("oa-unpaywall") -> SetupChoices.FindersValue(true),
        SetupChoices.FindersKey("arxiv") -> SetupChoices.FindersValue(true)
      ),
      area = Some(SetupChoices.Area("world"))
    )

  test("the seven steps in order, two of them optional"):
    assertEquals(SetupStep.values.length, 7)
    assertEquals(SetupStep.Welcome.next, Some(SetupStep.Papers))
    assertEquals(SetupStep.Ready.next, None)
    assertEquals(SetupStep.Welcome.back, None)
    assertEquals(SetupStep.values.filter(_.optional.value).toList, List(SetupStep.Agents, SetupStep.Calibre))

  test("an e-mail address, or none"):
    assertEquals(SetupPlan.email(SetupChoices.Email(" ")), Right(""))
    assertEquals(SetupPlan.email(SetupChoices.Email("someone@example.org")), Right("someone@example.org"))
    assert(SetupPlan.email(SetupChoices.Email("someone")).isLeft)

  test("the defaults change nothing but the mark that the setup was done"):
    assertEquals(SetupPlan.changes(before, before), List("setup.completed" -> Some("true")))

  test("what was chosen is written; only what differs, and always the mark"):
    val c = before
      .modify(_.email)
      .setTo(SetupChoices.Email("someone@example.org"))
      .modify(_.finders)
      .setTo(before.finders + (SetupChoices.FindersKey("arxiv") -> SetupChoices.FindersValue(false)))
      .modify(_.area)
      .setTo(Some(SetupChoices.Area("-12,20,90,62")))
      .modify(_.agents)
      .setTo(SetupChoices.Agents(true))
      .modify(_.calibre)
      .setTo(SetupChoices.Calibre("~/Books"))
      .modify(_.start)
      .setTo(
        MapStartConfig(
          MapStartMode.Wire("place"),
          Latitude(37),
          Longitude(30),
          MapStartConfig.Zoom(5),
          MapStartConfig.Label("Anatolia")
        )
      )
    val ch = SetupPlan.changes(c, before).toMap
    assertEquals(ch(ConfigEdit.Field.Key("acquire.contact-email")), Some("someone@example.org"))
    assertEquals(ch(ConfigEdit.Field.Key("plugins.arxiv.enabled")), Some("false"))
    assert(!ch.contains(ConfigEdit.Field.Key("plugins.oa-unpaywall.enabled")))
    assertEquals(ch(ConfigEdit.Field.Key("basemap.bbox")), Some("-12,20,90,62"))
    assertEquals(ch(ConfigEdit.Field.Key("agents.enabled")), Some("true"))
    assertEquals(ch(ConfigEdit.Field.Key("cli.calibre.library-dir")), Some("~/Books"))
    assertEquals(ch(ConfigEdit.Field.Key("ui.map-start.mode")), Some("place"))
    assertEquals(SetupPlan.changes(c, before).last, "setup.completed" -> Some("true"))

  test("the downloads: the map's area and the background data chosen, nothing else"):
    assertEquals(
      SetupPlan.downloads(before),
      List(SetupDownload.Map(SetupDownload.Map.Area("world")), SetupDownload.Boundaries, SetupDownload.Events)
    )
    assertEquals(
      SetupPlan.downloads(
        before
          .modify(_.area)
          .setTo(None)
          .modify(_.boundaries)
          .setTo(SetupChoices.Boundaries(false))
          .modify(_.events)
          .setTo(SetupChoices.Events(false))
      ),
      Nil
    )
    assert(
      SetupPlan
        .downloads(
          before
            .modify(_.pictures)
            .setTo(SetupChoices.Pictures(true))
            .modify(_.morePlaces)
            .setTo(SetupChoices.MorePlaces(true))
        )
        .contains(SetupDownload.MorePlaces)
    )
    val areas = List(MapArea(MapArea.Label("The whole world"), MapArea.Bbox("world"), MapArea.ApproxGb(8.8)))
    assertEquals(SetupPlan.mapGb(before, areas), 8.8)
    assertEquals(SetupPlan.mapGb(before.modify(_.area).setTo(None), areas), 0.0)

  test("the areas offered and the choices the settings say now"):
    val page = ConfigEdit.Page(sections =
      List(
        ConfigEdit.Section(
          ConfigEdit.Section.Id("basemap"),
          fields = List(
            ConfigEdit.Field(
              ConfigEdit.Field.Key("histmersion.basemap.setup-areas"),
              ConfigEdit.Field.Section("basemap"),
              ConfigEdit.Field.Kind("list"),
              effective = Some(
                ConfigEdit.Field.Effective("[\"The whole world|world|8.8\",\"Europe|-12,20,90,62|2.0\",\"broken\"]")
              )
            ),
            ConfigEdit.Field(
              ConfigEdit.Field.Key("histmersion.basemap.bbox"),
              ConfigEdit.Field.Section("basemap"),
              ConfigEdit.Field.Kind("text"),
              effective = Some(ConfigEdit.Field.Effective("\"-12,20,90,62\""))
            )
          )
        ),
        ConfigEdit.Section(
          ConfigEdit.Section.Id("acquire"),
          fields = List(
            ConfigEdit.Field(
              ConfigEdit.Field.Key("histmersion.acquire.contact-email"),
              ConfigEdit.Field.Section("acquire"),
              ConfigEdit.Field.Kind("text"),
              effective = Some(ConfigEdit.Field.Effective("\"someone@example.org\""))
            )
          )
        )
      )
    )
    assertEquals(SetupPage.areasOf(page).map(_.label), List("The whole world", "Europe"))
    val ui = UiConfig(
      UiConfig.Tiles(""),
      UiConfig.TilesMaxZoom(1),
      UiConfig.MarkerRadius(1),
      UiConfig.ViewportDebounceMs(1),
      UiConfig.PageSize(1),
      UiConfig.FacetSuggestions(1),
      UiConfig.TreeDepth(1),
      UiConfig.TreeSamples(1),
      UiConfig.MaxBuckets(1),
      DebounceConfig(
        DebounceConfig.SuggestMs(1),
        DebounceConfig.SearchMs(1),
        DebounceConfig.ReloadMs(1),
        DebounceConfig.PreviewMs(1)
      ),
      TimeConfig(Year(0), Year(1), Year(0), Year(1)),
      UiConfig.NarrowMaxWidthPx(1),
      UiConfig.NarrowMaxHeightPx(1),
      UiConfig.TabletMaxWidthPx(1),
      EventsUiConfig(
        EventsUiConfig.LocalFadeSeconds(1),
        EventsUiConfig.RegionalFadeSeconds(1),
        EventsUiConfig.MapLimit(1),
        EventsUiConfig.OutlineToleranceDeg(1)
      )
    )
    val c = SetupPage.choicesOf(page, ui)
    assertEquals(c.email, "someone@example.org")
    assertEquals(c.area, Some("-12,20,90,62"))
    assert(!c.agents.value)
    assertEquals(MapArea.size(MapArea.ApproxGb(8.8)), "8.8 GB")
    assertEquals(MapArea.parse("x|world|nope"), None)
