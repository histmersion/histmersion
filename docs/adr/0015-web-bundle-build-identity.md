# 0015. The page and the server agree on the web bundle's build

- Status: accepted
- Date: 2026-11-21

## Context

The web page is a Scala.js bundle the server ships as a resource. A browser keeps a page open across a server upgrade,
and it caches the bundle: a page of an older build then talks to a server whose answers it cannot read, and fails on a
decode error that says nothing useful (G109, review finding W6). The bundle's address used to carry a fixed `?v=2`/`?v=3`
that someone had to remember to bump.

## Decision

- **The build's id.** The build (`build.sbt`, `webBuild`) names the linked bundle by the first 32 hex digits of its
  SHA-256, writes that id into the bundle over the placeholder `HISTMERSION-BUILD-ID-UNSET-00000` and beside it as
  `web/build-id.txt`. The placeholder is exactly as long as an id, so the source map's columns stay right after the
  rewrite. A linked bundle without the placeholder stops the build: that page would compare nothing.
- **The page's own id is read, never compared with the literal.** `PageBuild.compiled` holds the placeholder text; the
  page checks it is an id (`BuildId.known`: 32 lower-case hex digits) at run time. Comparing it with the placeholder
  literal would be folded by the Scala.js optimizer before the build writes the id, so a built page would think itself
  unbuilt. A bundle linked outside the server's build (the development loop, the tests) keeps the placeholder and then
  compares nothing.
- **Three channels, one id.** The server reads `web/build-id.txt` (`WebBundle.read`; a missing file, or one that holds
  no id, is the empty id, and the page then compares nothing) and hands the id to the page in the bundle's address
  (`?v=<id>` in `index.html`'s preload and `boot.js`, and in the server pages' script: a new build is a new address, so no
  browser runs a cached old bundle), in `/api/config` (`UiConfig.buildId`) and in the `X-Histmersion-Build` header of
  every answer (`BuildId.Header`), so an open page notices an upgrade at its next request, not only when it loads.
  `boot.js` loads the bundle with the query it was itself loaded with, the same address as the preload, so the download
  the preload started is the one that runs.
- **The page reads the header before the answer.** `ApiClient` checks the header before it decodes a body: an upgraded
  server is reported as an upgrade, not as a decode failure. A header that holds no id (`BuildId.parse`) is ignored.
- **The verdict.** Same id: nothing. Either side without an id: `Unknown`, nothing. Different ids: `Updated`, and the
  page shows "Histmersion was updated — reload" with a Reload button over the page, leaving what is shown readable. Once
  shown, the prompt stays until the page reloads; a later answer of the page's own build does not take it away.
- **Tests mask the id.** The research oracle writes the bundle's address as `?v=BUILD`, since the id changes with every
  web change.

## Consequences

No version constant to bump. A server without a linked bundle (some test class paths) serves no header and an empty
`?v=`, and its pages compare nothing. The prompt is per page: the app (`App`) and a server page opened on its own
(`StandalonePage`) both mount `PageBuild.current`.

## Code

`build.sbt` (`WebBuildPlaceholder`, `webBuild`); `api.instance.BuildId`; `web.PageBuild`, `web.ApiClient.call`,
`web.App`, `web.StandalonePage`; `server.instance.WebBundle`, `server.Composition` (the header), `server.HttpApi` (the
shell), `server.ArticleHtml.page`; `web/index.html`, `web/boot.js`, `web/style.css` (`.build-updated`); tests
`PageBuildSuite`, `WebAssetsSuite`, `SmokeSuite`, `ResearchOracleSuite`.
