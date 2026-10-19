# Legal notice

!!! note "Not legal advice"
    This page explains how the software's licence relates to what you keep in Histmersion. It is
    not legal advice. Copyright, licensing and database rights differ between countries; when in
    doubt, ask your institution's library or legal office.

## The software's licence

Histmersion's licence **has not been decided yet**. The GNU Affero General Public License,
version 3 (AGPL-3.0) is being considered. This page describes what that licence would mean, so it
is ready when the decision is made; until then, no licence has been granted.

Under the AGPL, you may use, study, change and share the software. If you **change** Histmersion
and let other people use your changed version **over a network** (for example, colleagues opening
your instance in their browser), you must offer those people the source code of your changed
version. Running an unchanged copy, for yourself or for others, does not add any obligation beyond
keeping the licence notices.

## Your content is not covered by the software's licence

The licence covers the **program**, not the material you put into it or produce with it.

- **Your articles, notes, summaries and findings** belong to whoever holds their rights under the
  normal rules — usually you (or your employer). Using Histmersion does **not** make them AGPL, and
  you may publish, keep private or license them however you choose.
- **AI-written text** (summaries, findings, article drafts made by a model) is not made AGPL either.
  Whether such text is protected by copyright at all depends on your country and on how much of it
  you wrote or changed yourself.
- **Sources you store** — papers, books, scans, their OCR text — keep their **own** copyright and
  licence. Putting them into Histmersion changes nothing about who owns them or what you may do with
  them.
- **Data imported from outside** (places, periods, events, pictures) keeps the licence of its
  provider — for example CC0, CC BY, ODC-By or ODbL. [Open data](how-to/open-data.md) and
  [Pictures](exploring/media.md) list them, with how to credit each one.

## Paid and restricted sources

You may keep sources you bought, borrowed or reach through your institution in your own
Histmersion, the same way you may keep them on your own disk — **as long as you stay within the
terms under which you got them**. In practice:

- **A personal instance** (the default: it listens only on your own computer) is like your own
  bookshelf. Only you see what is in it.
- **A shared instance** (opened to your network with `HISTMERSION_BIND` and protected with
  `HISTMERSION_API_KEY` — see [Installing](getting-started/install.md)) must be reachable **only by
  people who are themselves entitled to the sources it holds**: for example, members of the same
  institution with the same subscriptions, or people who bought the same books. Do not open an
  instance holding paid or restricted material to the public internet or to people without that
  access.
- **Backups, bundles and exports copy content.** A backup (`histmersion backup`) and a bundle
  (`histmersion bundle export`) carry the full OCR texts and the stored originals; the readable
  export (`histmersion export`) carries summaries, findings, notes and quotations. Sharing one
  shares that material: give it only to people entitled to every source in it, or leave the
  restricted works out.
- **Pictures and datasets** must be credited and reused according to their own licences, wherever
  you show them.

The software's licence does not conflict with any of this: the AGPL governs how the program may be
shared, while each source's own terms govern who may read the source.

## See also

- [Responsible use](responsible-use.md) — legal acquisition, respecting each site's terms, and how
  to treat AI-written text.
- [Open-access papers](how-to/open-access-papers.md) and [Books and Calibre](how-to/books-and-calibre.md)
  — how sources come in legally.
