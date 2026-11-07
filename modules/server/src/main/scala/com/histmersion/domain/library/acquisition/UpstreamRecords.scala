package com.histmersion.domain.library.acquisition

import com.histmersion.api
import com.histmersion.upstreams
import io.scalaland.chimney.dsl.*
import io.scalaland.chimney.Transformer

private given pluginYearToWire: Transformer[com.histmersion.plugin.Year, api.catalogue.Year] = y => api.catalogue.Year(y.value.toLong)
private given wireYearToPlugin: Transformer[api.catalogue.Year, com.histmersion.plugin.Year] = y => com.histmersion.plugin.Year(y.value.toInt)
private given pluginAuthorToWire: Transformer[com.histmersion.plugin.WorkKey.Authors, api.library.AuthorName] = a => api.library.AuthorName(a.value)
private given wireAuthorToPlugin: Transformer[api.library.AuthorName, com.histmersion.plugin.WorkKey.Authors] = a => com.histmersion.plugin.WorkKey.Authors(a.value)
private given pluginLicenceToWire: Transformer[com.histmersion.plugin.Licence, api.library.WorkRef.OaLicence] = l => api.library.WorkRef.OaLicence(l.value)
private given wireLicenceToPlugin: Transformer[api.library.WorkRef.OaLicence, com.histmersion.plugin.Licence] = l => com.histmersion.plugin.Licence(l.value)
private given arxivToWire: Transformer[upstreams.ArxivId, api.library.FollowupRow.Arxiv] = a => api.library.FollowupRow.Arxiv(a.value)
private given citedByToWire: Transformer[com.histmersion.domain.library.acquisition.FollowupRow.CitedBy, api.library.Acquire.CitedByCount] =
  c => api.library.Acquire.CitedByCount(c.value)
private given refAuthorToWire: Transformer[com.histmersion.domain.library.acquisition.RefRow.Authors, api.library.AuthorName] =
  a => api.library.AuthorName(a.value)
private given refRawToWire: Transformer[com.histmersion.domain.library.acquisition.RefRow.Raw, api.library.Refs.ReferenceText] =
  r => api.library.Refs.ReferenceText(r.value)

extension (w: com.histmersion.domain.library.acquisition.WorkRef)
  /** The work as the wire shows it, keyed by the citekey the corpus gives a work (not in the corpus yet). */
  def wire: api.library.WorkRef =
    w.into[api.library.WorkRef]
      .withFieldComputed(
        _.citekey,
        w =>
          Some(
            api.library.Acquire.citekey(
              w.authors.map(a => api.library.AuthorName(a.value)),
              w.year.map(y => api.catalogue.Year(y.value.toLong)),
              w.title.map(t => api.library.Title(t.value))
            )
          )
      )
      .withFieldConst(_.inDb, api.library.WorkRef.InDb(false))
      .transform

extension (w: api.library.WorkRef)
  /** The work as the upstreams' rules read it. */
  def upstream: com.histmersion.domain.library.acquisition.WorkRef =
    w.transformInto[com.histmersion.domain.library.acquisition.WorkRef]

extension (r: com.histmersion.domain.library.acquisition.FollowupRow)
  def wire: api.library.FollowupRow = r.into[api.library.FollowupRow].enableDefaultValues.transform

extension (r: com.histmersion.domain.library.acquisition.RefRow)
  def wire: api.library.RefRow = r.into[api.library.RefRow].enableDefaultValues.transform

extension (i: upstreams.archiveorg.ArchiveItem)
  def wire: api.library.ArchiveItem = i.transformInto[api.library.ArchiveItem]

extension (f: upstreams.archiveorg.ArchiveFile)
  def wire: api.library.ArchiveFile = f.transformInto[api.library.ArchiveFile]

extension (f: api.library.ArchiveFile)
  def upstream: upstreams.archiveorg.ArchiveFile = f.transformInto[upstreams.archiveorg.ArchiveFile]
