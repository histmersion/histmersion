package com.histmersion.web.setup

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.settings.ConfigEdit
import com.histmersion.api.catalogue.MapArea
import com.histmersion.api.instance.{MapStartConfig, UiConfig}

class SetupPlanSuite extends munit.FunSuite:

  private val before =
    SetupChoices(email = com.histmersion.web.setup.SetupChoices.Email(""), finders = Map("oa-unpaywall" -> true, "arxiv" -> true), area = Some(com.histmersion.web.setup.SetupChoices.Area("world")))

  test("the seven steps in order, two of them optional"):
    assertEquals(SetupStep.values.length, 7)
    assertEquals(SetupStep.Welcome.next, Some(SetupStep.Papers))
    assertEquals(SetupStep.Ready.next, None)
    assertEquals(SetupStep.Welcome.back, None)
    assertEquals(SetupStep.values.filter(_.optional).toList, List(SetupStep.Agents, SetupStep.Calibre))

  test("an e-mail address, or none"):
    assertEquals(SetupPlan.email(com.histmersion.web.setup.SetupChoices.Email(" ")), Right(""))
    assertEquals(SetupPlan.email(com.histmersion.web.setup.SetupChoices.Email("someone@example.org")), Right("someone@example.org"))
    assert(SetupPlan.email(com.histmersion.web.setup.SetupChoices.Email("someone")).isLeft)

  test("the defaults change nothing but the mark that the setup was done"):
    assertEquals(SetupPlan.changes(before, before), List("setup.completed" -> Some("true")))

  test("what was chosen is written; only what differs, and always the mark"):
    val c = before.modify(
      _.email).setTo("someone@example.org").modify(_.finders).setTo(before.finders + ("arxiv" -> false)).modify(_.area).setTo(Some("-12,20,90,62")).modify(_.agents).setTo(true).modify(_.calibre).setTo("~/Books").modify(_.start).setTo(MapStartConfig("place", 37, 30, 5, "Anatolia")
    )
    val ch = SetupPlan.changes(c, before).toMap
    assertEquals(ch(com.histmersion.api.settings.ConfigEdit.Field.Key("acquire.contact-email")), Some("someone@example.org"))
    assertEquals(ch(com.histmersion.api.settings.ConfigEdit.Field.Key("plugins.arxiv.enabled")), Some("false"))
    assert(!ch.contains(com.histmersion.api.settings.ConfigEdit.Field.Key("plugins.oa-unpaywall.enabled")))
    assertEquals(ch(com.histmersion.api.settings.ConfigEdit.Field.Key("basemap.bbox")), Some("-12,20,90,62"))
    assertEquals(ch(com.histmersion.api.settings.ConfigEdit.Field.Key("agents.enabled")), Some("true"))
    assertEquals(ch(com.histmersion.api.settings.ConfigEdit.Field.Key("cli.calibre.library-dir")), Some("~/Books"))
    assertEquals(ch(com.histmersion.api.settings.ConfigEdit.Field.Key("ui.map-start.mode")), Some("place"))
    assertEquals(SetupPlan.changes(c, before).last, "setup.completed" -> Some("true"))

  test("the downloads: the map's area and the background data chosen, nothing else"):
    assertEquals(
      SetupPlan.downloads(before),
      List(SetupDownload.Map(com.histmersion.web.setup.SetupDownload.Map.Area("world")), SetupDownload.Boundaries, SetupDownload.Events)
    )
    assertEquals(SetupPlan.downloads(before.modify(_.area).setTo(None).modify(_.boundaries).setTo(false).modify(_.events).setTo(false)), Nil)
    assert(SetupPlan.downloads(before.modify(_.pictures).setTo(true).modify(_.morePlaces).setTo(true)).contains(SetupDownload.MorePlaces))
    val areas = List(MapArea(com.histmersion.api.catalogue.MapArea.Label("The whole world"), com.histmersion.api.catalogue.MapArea.Bbox("world"), 8.8))
    assertEquals(SetupPlan.mapGb(before, areas), 8.8)
    assertEquals(SetupPlan.mapGb(before.modify(_.area).setTo(None), areas), 0.0)

  test("the areas offered and the choices the settings say now"):
    val page = ConfigEdit.Page(sections =
      List(
        ConfigEdit.Section(
          com.histmersion.api.settings.ConfigEdit.Section.Id("basemap"),
          fields = List(
            ConfigEdit.Field(
              com.histmersion.api.settings.ConfigEdit.Field.Key("histmersion.basemap.setup-areas"),
              com.histmersion.api.settings.ConfigEdit.Field.Section("basemap"),
              com.histmersion.api.settings.ConfigEdit.Field.Kind("list"),
              effective = Some(com.histmersion.api.settings.ConfigEdit.Field.Effective("[\"The whole world|world|8.8\",\"Europe|-12,20,90,62|2.0\",\"broken\"]"))
            ),
            ConfigEdit.Field(com.histmersion.api.settings.ConfigEdit.Field.Key("histmersion.basemap.bbox"), com.histmersion.api.settings.ConfigEdit.Field.Section("basemap"), com.histmersion.api.settings.ConfigEdit.Field.Kind("text"), effective = Some(com.histmersion.api.settings.ConfigEdit.Field.Effective("\"-12,20,90,62\"")))
          )
        ),
        ConfigEdit.Section(
          com.histmersion.api.settings.ConfigEdit.Section.Id("acquire"),
          fields = List(
            ConfigEdit.Field(
              com.histmersion.api.settings.ConfigEdit.Field.Key("histmersion.acquire.contact-email"),
              com.histmersion.api.settings.ConfigEdit.Field.Section("acquire"),
              com.histmersion.api.settings.ConfigEdit.Field.Kind("text"),
              effective = Some(com.histmersion.api.settings.ConfigEdit.Field.Effective("\"someone@example.org\""))
            )
          )
        )
      )
    )
    assertEquals(SetupPage.areasOf(page).map(_.label), List("The whole world", "Europe"))
    val ui = UiConfig(
      com.histmersion.api.instance.UiConfig.Tiles(""),
      com.histmersion.api.instance.UiConfig.ViewportDebounceMs(1),
      com.histmersion.api.instance.UiConfig.PageSize(1),
      com.histmersion.api.instance.UiConfig.FacetSuggestions(1),
      com.histmersion.api.instance.UiConfig.TreeDepth(1),
      com.histmersion.api.instance.UiConfig.TreeSamples(1),
      com.histmersion.api.instance.UiConfig.MaxBuckets(1),
      com.histmersion.api.instance.UiConfig.TreeSamples(1),
      com.histmersion.api.instance.UiConfig.MaxBuckets(1),
      com.histmersion.api.instance.DebounceConfig(com.histmersion.api.instance.DebounceConfig.SuggestMs(1), com.histmersion.api.instance.DebounceConfig.SearchMs(1), com.histmersion.api.instance.DebounceConfig.ReloadMs(1), com.histmersion.api.instance.DebounceConfig.PreviewMs(1)),
      com.histmersion.api.instance.TimeConfig(com.histmersion.api.catalogue.Year(0), com.histmersion.api.catalogue.Year(1), com.histmersion.api.catalogue.Year(0), com.histmersion.api.catalogue.Year(1)),
      com.histmersion.api.instance.UiConfig.TabletMaxWidthPx(1),
      com.histmersion.api.instance.UiConfig.NarrowMaxHeightPx(1),
      com.histmersion.api.instance.UiConfig.TabletMaxWidthPx(1),
      com.histmersion.api.instance.EventsUiConfig(com.histmersion.api.instance.EventsUiConfig.LocalFadeSeconds(1), com.histmersion.api.instance.EventsUiConfig.RegionalFadeSeconds(1), com.histmersion.api.instance.EventsUiConfig.MapLimit(1), com.histmersion.api.instance.EventsUiConfig.OutlineToleranceDeg(1))
    )
    val c = SetupPage.choicesOf(page, ui)
    assertEquals(c.email, "someone@example.org")
    assertEquals(c.area, Some("-12,20,90,62"))
    assert(!c.agents)
    assertEquals(MapArea.size(8.8), "8.8 GB")
    assertEquals(MapArea.parse("x|world|nope"), None)
