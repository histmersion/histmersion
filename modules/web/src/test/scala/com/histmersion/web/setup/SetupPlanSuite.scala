package com.histmersion.web.setup

import com.histmersion.api.settings.ConfigEdit
import com.histmersion.api.catalogue.MapArea
import com.histmersion.api.instance.{MapStartConfig, UiConfig}

class SetupPlanSuite extends munit.FunSuite:

  private val before =
    SetupChoices(email = "", finders = Map("oa-unpaywall" -> true, "arxiv" -> true), area = Some("world"))

  test("the seven steps in order, two of them optional"):
    assertEquals(SetupStep.values.length, 7)
    assertEquals(SetupStep.Welcome.next, Some(SetupStep.Papers))
    assertEquals(SetupStep.Ready.next, None)
    assertEquals(SetupStep.Welcome.back, None)
    assertEquals(SetupStep.values.filter(_.optional).toList, List(SetupStep.Agents, SetupStep.Calibre))

  test("an e-mail address, or none"):
    assertEquals(SetupPlan.email(" "), Right(""))
    assertEquals(SetupPlan.email("someone@example.org"), Right("someone@example.org"))
    assert(SetupPlan.email("someone").isLeft)

  test("the defaults change nothing but the mark that the setup was done"):
    assertEquals(SetupPlan.changes(before, before), List("setup.completed" -> Some("true")))

  test("what was chosen is written; only what differs, and always the mark"):
    val c = before.copy(
      email = "someone@example.org",
      finders = before.finders + ("arxiv" -> false),
      area = Some("-12,20,90,62"),
      agents = true,
      calibre = "~/Books",
      start = MapStartConfig("place", 37, 30, 5, "Anatolia")
    )
    val ch = SetupPlan.changes(c, before).toMap
    assertEquals(ch("acquire.contact-email"), Some("someone@example.org"))
    assertEquals(ch("plugins.arxiv.enabled"), Some("false"))
    assert(!ch.contains("plugins.oa-unpaywall.enabled"))
    assertEquals(ch("basemap.bbox"), Some("-12,20,90,62"))
    assertEquals(ch("agents.enabled"), Some("true"))
    assertEquals(ch("cli.calibre.library-dir"), Some("~/Books"))
    assertEquals(ch("ui.map-start.mode"), Some("place"))
    assertEquals(SetupPlan.changes(c, before).last, "setup.completed" -> Some("true"))

  test("the downloads: the map's area and the background data chosen, nothing else"):
    assertEquals(
      SetupPlan.downloads(before),
      List(SetupDownload.Map("world"), SetupDownload.Boundaries, SetupDownload.Events)
    )
    assertEquals(SetupPlan.downloads(before.copy(area = None, boundaries = false, events = false)), Nil)
    assert(SetupPlan.downloads(before.copy(pictures = true, morePlaces = true)).contains(SetupDownload.MorePlaces))
    val areas = List(MapArea("The whole world", "world", 8.8))
    assertEquals(SetupPlan.mapGb(before, areas), 8.8)
    assertEquals(SetupPlan.mapGb(before.copy(area = None), areas), 0.0)

  test("the areas offered and the choices the settings say now"):
    val page = ConfigEdit.Page(sections =
      List(
        ConfigEdit.Section(
          "basemap",
          fields = List(
            ConfigEdit.Field(
              "histmersion.basemap.setup-areas",
              "basemap",
              "list",
              effective = Some("[\"The whole world|world|8.8\",\"Europe|-12,20,90,62|2.0\",\"broken\"]")
            ),
            ConfigEdit.Field("histmersion.basemap.bbox", "basemap", "text", effective = Some("\"-12,20,90,62\""))
          )
        ),
        ConfigEdit.Section(
          "acquire",
          fields = List(
            ConfigEdit.Field(
              "histmersion.acquire.contact-email",
              "acquire",
              "text",
              effective = Some("\"someone@example.org\"")
            )
          )
        )
      )
    )
    assertEquals(SetupPage.areasOf(page).map(_.label), List("The whole world", "Europe"))
    val ui = UiConfig(
      "",
      1,
      1,
      1,
      1,
      1,
      1,
      1,
      1,
      com.histmersion.api.instance.DebounceConfig(1, 1, 1, 1),
      com.histmersion.api.instance.TimeConfig(0, 1, 0, 1),
      1,
      1,
      1,
      com.histmersion.api.instance.EventsUiConfig(1, 1, 1, 1)
    )
    val c = SetupPage.choicesOf(page, ui)
    assertEquals(c.email, "someone@example.org")
    assertEquals(c.area, Some("-12,20,90,62"))
    assert(!c.agents)
    assertEquals(MapArea.size(8.8), "8.8 GB")
    assertEquals(MapArea.parse("x|world|nope"), None)
