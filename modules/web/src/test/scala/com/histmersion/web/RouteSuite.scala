package com.histmersion.web

import com.histmersion.api.{Queue, WebPaths}

/** The web UI's addresses: every page has a path that reads back as the same page, sits under one of the
  * four places, and is one the server serves the app for; the old toolbar's windows each have exactly one
  * page now; old addresses land on their new pages. */
class RouteSuite extends munit.FunSuite:

  private val pages: List[Route] =
    List(Route.Explore) ++
      LibrarySection.values.map(Route.Library(_)) ++
      ResearchSection.values.filter(_ != ResearchSection.Activity).map(Route.Research(_)) ++
      ActivityTab.values.map(Route.Activity(_)) ++
      SettingsSection.values.map(Route.Settings(_)) ++
      List(Route.Goal("bronze-age"), Route.Run("3f2a-77"), Route.Manager("watch:bronze-age"), Route.Job("10ed4471"),
        Route.Place("pleiades:579885"), Route.Event("ev-1"), Route.Source("cline2014_year"), Route.Article("sea-peoples"))

  test("every page's path reads back as that page"):
    pages.foreach(r => assertEquals(Route.parse(r.path), Some(r), r.path))

  test("the paths the plan names"):
    assertEquals(Route.Explore.path, "/explore")
    assertEquals(Route.Library(LibrarySection.Sources).path, "/library")
    assertEquals(Route.Research(ResearchSection.Overview).path, "/research")
    assertEquals(Route.Research(ResearchSection.Goals).path, "/research/goals")
    assertEquals(Route.Goal("bronze-age").path, "/research/goals/bronze-age")
    assertEquals(Route.Activity(ActivityTab.Everything).path, "/research/activity")
    assertEquals(Route.Activity(ActivityTab.Running).path, "/research/activity/running")
    assertEquals(Route.Activity(ActivityTab.Jobs).path, "/research/activity/jobs")
    assertEquals(Route.Activity(ActivityTab.Problems).path, "/research/activity/problems")
    assertEquals(Route.Activity(ActivityTab.Runs).path, "/research/activity/runs")
    assertEquals(Route.Activity(ActivityTab.Worklog).path, "/research/activity/worklog")
    assertEquals(Route.Research(ResearchSection.Backlog).path, "/research/backlog")
    assertEquals(Route.Research(ResearchSection.Acquire).path, "/research/acquire")
    assertEquals(Route.Research(ResearchSection.Decisions).path, "/research/decisions")
    assertEquals(Route.Research(ResearchSection.Models).path, "/research/models")
    assertEquals(Route.Settings(SettingsSection.Data).path, "/settings/data")
    assertEquals(Route.parse("/settings"), Some(Route.Settings(SettingsSection.Data)))
    assertEquals(Route.Run("a b").path, "/run/a%20b")
    assertEquals(Route.parse("/run/a%20b"), Some(Route.Run("a b")))
    assertEquals(Route.Place("pleiades:579885").path, "/place/pleiades%3A579885")

  test("an address the web UI does not know is no page"):
    List("/nope", "/research/nope", "/research/activity/nope", "/library/x/y", "/settings/data/x", "/run",
      "/run/a/b", "/research/goals/a/b", "/explore/x").foreach(p => assertEquals(Route.parse(p), None, p))
    // a trailing slash is the same page
    assertEquals(Route.parse("/research/activity/"), Some(Route.Activity(ActivityTab.Everything)))

  test("every page the web UI draws is an address the server serves it for; the server's pages are not"):
    pages.foreach { r =>
      val segments = r.path.split('/').toList.filter(_.nonEmpty)
      assertEquals(WebPaths.isApp(segments), !r.serverPage, r.path)
      assert(Route.isAppPath(r.path) == !r.serverPage, r.path)
    }

  test("every page sits under one of the four places; its front page is the place's tab"):
    assertEquals(Route.Run("x").place, PlaceTab.Research)
    assertEquals(Route.Job("x").place, PlaceTab.Research)
    assertEquals(Route.Goal("x").place, PlaceTab.Research)
    assertEquals(Route.Place("x").place, PlaceTab.Explore)
    assertEquals(Route.Source("x").place, PlaceTab.Library)
    assertEquals(Route.Settings(SettingsSection.Keys).place, PlaceTab.Settings)
    PlaceTab.values.foreach(p => assertEquals(Route.front(p).place, p))

  test("every window of the old toolbar has exactly one page now, in the place the plan gives it"):
    val toolbar = List(WindowKey.Goals, WindowKey.Tasks, WindowKey.Runs, WindowKey.Managers, WindowKey.Approvals,
      WindowKey.Acquire, WindowKey.Upload, WindowKey.Merge, WindowKey.Jobs, WindowKey.Log, WindowKey.About,
      WindowKey.Models)
    val homes = toolbar.map(k => k -> Route.ofWindow(k))
    homes.foreach((k, r) => assert(r.isDefined, k.toString))
    assertEquals(homes.flatMap(_._2).distinct.size, toolbar.size, "two windows share a page")
    def placeOf(k: WindowKey) = Route.ofWindow(k).get.place
    List(WindowKey.Goals, WindowKey.Approvals, WindowKey.Managers, WindowKey.Runs, WindowKey.Models, WindowKey.Tasks,
      WindowKey.Log, WindowKey.Jobs, WindowKey.Acquire).foreach(k => assertEquals(placeOf(k), PlaceTab.Research, k.toString))
    List(WindowKey.Upload, WindowKey.Merge).foreach(k => assertEquals(placeOf(k), PlaceTab.Library, k.toString))
    assertEquals(placeOf(WindowKey.About), PlaceTab.Settings)
    // the key's prompt stays a window (a write opens it); its home is Settings › Keys
    assertEquals(Route.ofWindow(WindowKey.ApiKey), None)
    assertEquals(Route.hrefOf(WindowKey.ApiKey), "/settings/keys")
    // single items stay windows, and link to their own pages
    List(WindowKey.Run("x"), WindowKey.Goal("g"), WindowKey.Manager("m"), WindowKey.Source("s"), WindowKey.Article("a"))
      .foreach(k => assertEquals(Route.ofWindow(k), None, k.toString))

  test("a link to a window points at its page; one with no page of its own, at the map with it open"):
    assertEquals(Route.hrefOf(WindowKey.Run("3f2a")), "/run/3f2a")
    assertEquals(Route.hrefOf(WindowKey.Goal("bronze-age")), "/research/goals/bronze-age")
    assertEquals(Route.hrefOf(WindowKey.Approvals), "/research/decisions")
    assertEquals(Route.hrefOf(WindowKey.Models), "/research/models")
    assertEquals(Route.hrefOf(WindowKey.Graph("a2020_b")), "/explore#open=graph%3Aa2020_b")

  test("a list item's page is its list's, scrolled to it"):
    assertEquals(Route.queueItem(Queue.Kind.Tasks, 12), "/research/backlog?item=12")
    assertEquals(Route.queueItem(Queue.Kind.Acquire, 3), "/research/acquire?item=3")
    assertEquals(Route.queueItem(Queue.Kind.Log, 9), "/research/activity/worklog?item=9")

  test("old addresses land on their new pages; windows of single items stay open over them"):
    assertEquals(Route.legacy("/", Nil), Some(Route.Landing(Route.Explore, Nil)))
    assertEquals(Route.legacy("/acquire", Nil), Some(Route.Landing(Route.Research(ResearchSection.Acquire), Nil)))
    assertEquals(Route.legacy("/tasks", Nil), Some(Route.Landing(Route.Research(ResearchSection.Backlog), Nil)))
    assertEquals(Route.legacy("/log", Nil), Some(Route.Landing(Route.Activity(ActivityTab.Worklog), Nil)))
    assertEquals(Route.legacy("/", List(WindowKey.Goals)), Some(Route.Landing(Route.Research(ResearchSection.Goals), Nil)))
    assertEquals(Route.legacy("/", List(WindowKey.Run("r1"), WindowKey.Jobs, WindowKey.About)),
      Some(Route.Landing(Route.Activity(ActivityTab.JobList), List(WindowKey.Run("r1")))))
    assertEquals(Route.legacy("/", List(WindowKey.Source("a2020_b"))),
      Some(Route.Landing(Route.Explore, List(WindowKey.Source("a2020_b")))))
    // an old list address wins over a window named in its hash
    assertEquals(Route.legacy("/tasks", List(WindowKey.Approvals)),
      Some(Route.Landing(Route.Research(ResearchSection.Backlog), Nil)))
    // on a new page, a toolbar window in the hash is that window's page
    assertEquals(Route.legacy("/explore", List(WindowKey.Approvals, WindowKey.Goal("g"))),
      Some(Route.Landing(Route.Research(ResearchSection.Decisions), List(WindowKey.Goal("g")))))
    // a new address with nothing old in it is left alone
    assertEquals(Route.legacy("/research/goals", List(WindowKey.Run("r1"))), None)
    assertEquals(Route.legacy("/explore", Nil), None)
    // the key's prompt is a window still
    assertEquals(Route.legacy("/explore", List(WindowKey.ApiKey)), None)

  test("only a plain primary click is the page's; a modifier or another button is the browser's"):
    assert(Router.plainClick(0, meta = false, ctrl = false, shift = false, alt = false))
    assert(!Router.plainClick(0, meta = true, ctrl = false, shift = false, alt = false))
    assert(!Router.plainClick(0, meta = false, ctrl = true, shift = false, alt = false))
    assert(!Router.plainClick(0, meta = false, ctrl = false, shift = true, alt = false))
    assert(!Router.plainClick(0, meta = false, ctrl = false, shift = false, alt = true))
    assert(!Router.plainClick(1, meta = false, ctrl = false, shift = false, alt = false))

  test("the search filters as it is typed where results are shown; elsewhere Enter goes to Library"):
    assertEquals(TopBar.showsResults(Route.Explore), Some(Route.Explore))
    assertEquals(TopBar.showsResults(Route.Library(LibrarySection.Articles)), Some(Route.Library(LibrarySection.Articles)))
    assertEquals(TopBar.showsResults(Route.Library(LibrarySection.Upload)), None)
    assertEquals(TopBar.showsResults(Route.Research(ResearchSection.Goals)), None)

  test("page titles are words, never identifiers"):
    pages.foreach(r => assert(!Router.title(r).contains("_") && Router.title(r).head.isUpper, r.toString))

