package com.histmersion.web

import com.raquo.laminar.api.L.*

/** The app's tools, at the end of the filter bar: each opens its window (and shows it is open), the
  * same buttons as the map's layer switches. The key button appears only when the server wants a
  * key for writes, and is marked while one is held. */
object Toolbar:
  def element: HtmlElement =
    div(
      cls := "layer-toggles tool-btns",
      WindowToggle(Icon.Upload, "upload", WindowKey.Upload),
      WindowToggle(Icon.Jobs, "jobs", WindowKey.Jobs),
      WindowToggle(Icon.Acquire, "to acquire", WindowKey.Acquire),
      WindowToggle(Icon.Tasks, "backlog", WindowKey.Tasks),
      WindowToggle(Icon.Log, "worklog", WindowKey.Log),
      WindowToggle(Icon.Goals, "research goals", WindowKey.Goals),
      WindowToggle(Icon.Merge, "merge another collection", WindowKey.Merge),
      // the embedded agents' windows only where the server runs agents (agents.enabled)
      child.maybe <-- RunsView.enabled.signal.map(on => Option.when(on)(WindowToggle(Icon.Runs, "agent runs", WindowKey.Runs))),
      child.maybe <-- RunsView.enabled.signal.map(on => Option.when(on)(WindowToggle(Icon.Approvals, "approvals", WindowKey.Approvals))),
      child.maybe <-- Auth.requiredSignal.map(r => Option.when(r)(
        WindowToggle(Icon.Key, "API key", WindowKey.ApiKey).amend(
          cls("key-held") <-- UiState.apiKey.signal.map(_.isDefined),
          title <-- UiState.apiKey.signal.map(k =>
            if k.isDefined then "API key: set — changes are sent with it" else "API key: needed to make changes")))),
      WindowToggle(Icon.Info, "about", WindowKey.About)
    )
