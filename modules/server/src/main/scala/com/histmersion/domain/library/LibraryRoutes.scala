package com.histmersion.domain.library

import com.histmersion.api.Api
import com.histmersion.domain.library.citations.CitationsControllers
import com.histmersion.domain.library.findings.FindingsControllers
import com.histmersion.domain.library.labels.LabelsControllers
import com.histmersion.domain.library.search.SearchControllers
import com.histmersion.domain.library.sources.SourcesControllers
import sttp.shared.Identity
import sttp.tapir.server.ServerEndpoint

/** The library's endpoints bound to its features' controllers, in the order route matching needs (`sources/{ck}/lint`
  * and `…/view` before `sources/{ck}`, `sources/{ck}/export` before the write paths). The endpoints of other owners
  * that sit between them keep their places: the articles citing a source, Markdown checked before it is stored, an id
  * resolved to a source or an article, the seed, a source's note, the chunks' rebuild.
  */
object LibraryRoutes:
  def apply(
      search: SearchControllers,
      labels: LabelsControllers,
      sources: SourcesControllers,
      findings: FindingsControllers,
      citations: CitationsControllers,
      sourceArticles: ServerEndpoint[Any, Identity],
      lintMarkdown: ServerEndpoint[Any, Identity],
      resolveId: ServerEndpoint[Any, Identity],
      seed: ServerEndpoint[Any, Identity],
      exportSource: ServerEndpoint[Any, Identity],
      reindex: ServerEndpoint[Any, Identity]
  ): List[ServerEndpoint[Any, Identity]] =
    List(
      Api.search.serverLogicSuccess[Identity]((q, limit) => search.search(q, limit)),
      Api.sources.serverLogicSuccess[Identity](f => search.sources(f)),
      Api.facets.serverLogic[Identity]((field, prefix, f) => labels.facets(field, prefix, f)),
      sourceArticles,
      Api.lintSource.serverLogic[Identity](ck => sources.lint(ck)),
      Api.fixSourceFormat.serverLogic[Identity](ck => sources.fix(ck)),
      lintMarkdown,
      Api.sourceView.serverLogic[Identity](q =>
        sources.markdown(q.citekey, q.fullText, q.section, q.heading, q.offset, q.chars)
      ),
      Api.passages.serverLogic[Identity](q => sources.passages(q)),
      resolveId,
      Api.getSource.serverLogic[Identity]((ck, body) => sources.get(ck, body)),
      Api.findingsPage.serverLogicSuccess[Identity](q => findings.page(q)),
      Api.findings.serverLogicSuccess[Identity](q => findings.feed(q)),
      Api.aggregate.serverLogic[Identity](q => search.aggregate(q)),
      Api.neighbors.serverLogicSuccess[Identity]((ck, kinds) => citations.neighbors(ck, kinds)),
      Api.graph.serverLogicSuccess[Identity]((ck, depth) => citations.graph(ck, depth)),
      Api.contradictions.serverLogicSuccess[Identity]((ck, since) => citations.contradictions(ck, since)),
      seed,
      exportSource,
      Api.putSource.serverLogic[Identity]((ck, in, strict) => sources.put(ck, in, strict)),
      Api.patchSource.serverLogic[Identity]((ck, p, strict) => sources.patch(ck, p, strict)),
      Api.renameSource.serverLogic[Identity]((ck, req) => sources.rename(ck, req)),
      Api.deleteSource.serverLogic[Identity]((ck, req) => sources.delete(ck, req)),
      Api.sourceAliases.serverLogicSuccess[Identity](_ => sources.aliases),
      Api.importNote.serverLogic[Identity](req => sources.importNote(req)),
      Api.citekeys.serverLogicSuccess[Identity](_ => sources.citekeys),
      Api.resolveEdges.serverLogic[Identity](_ => citations.resolveEdges),
      reindex
    )
