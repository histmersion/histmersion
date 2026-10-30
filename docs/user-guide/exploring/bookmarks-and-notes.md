# Bookmarks, notes and read marks

While you read and explore, Histmersion keeps three things for you:

- **Bookmarks** — things you want to find again: a source, an article, an event, a place, a picture, a
  research goal, a spot on the map, a view of the map with its filters, or a span of years.
- **Notes** — your own thoughts, written on a source, an article, an event, a place or a spot on the map.
  A note always remembers where you left it, and takes you back there.
- **Read marks** — which sources and articles you have read.

They are yours: they are kept in your collection, not in the shared research. The AI research agents can
*read* your bookmarks and notes, so that they know what interests you, but they can never add, change or
delete them. Only you can, here in the web page or with the [command line](../reference/cli.md)
(`histmersion bookmarks …` and `histmersion notes …`).

!!! note "If your server asks for a key"
    When Histmersion is set up to ask for an API key before any change, the bookmark, note and read
    buttons ask for the key first. Reading your bookmarks and notes never needs one.

## Bookmarking

- **A source or an article in Library**: choose it in the list, then press **Bookmark** above it in the
  reading pane. The button then says **Bookmarked**; press it again to take the bookmark away.
- **Anything in Explore's results**: every row has a ribbon button at its right-hand edge, beside "open in a
  new tab". It belongs to that row only. A filled ribbon means the item is bookmarked.
- **An item you chose in Explore** (a place, an event, a source): its detail has the same **Bookmark**
  button.
- **A spot on the map**: right-click the map there and choose **Bookmark this place**.
- **The whole view**: see [Saved views](#saved-views) below.
- **A span of years**: see [My periods](#my-periods) below.

Each thing is bookmarked once. Your bookmarks keep up with the collection: if a source is given a new
title, its bookmark shows the new one.

## Saved views

A saved view keeps Explore exactly as it is now — the filters, the span of years on the timeline, the map
layers, and where the map is and how far it is zoomed in.

1. In **Explore**, set things up as you like.
2. At the foot of the filter panel, under **Saved views**, press **Save this view…**.
3. Give it a name (a suggestion is filled in) and press **Save**.

Your saved views are listed right there, and in **Library › My library › Bookmarks**. Click one to go back
to it. The **×** beside it removes it. You can also right-click the map and choose **Bookmark this view of
the map**.

## Notes

To write a note, press **Add note** on a source or an article in Library's reading pane, or on an item you
chose in Explore. To leave a note at a spot on the map, right-click the map there and choose **Add a note
here**.

The note window has a plain text box and two tabs:

- **Write** — type your note. You can use a little formatting: `**bold**`, `*italic*`, a list with `-` at the
  start of each line, `> ` for a quotation, `[a link](https://…)`, and `[[citekey]]` to point at a source.
- **Preview** — shows what the note will look like.

Nothing is saved until you press **Save** (or Ctrl+Enter, ⌘+Enter on a Mac). **Cancel** throws your text
away. If you close the window with Escape, close the browser tab or reload the page instead, your
unsaved text is kept by this browser as a **draft**: the next time you open the note window for the same
thing, the draft is back.

Your notes appear under the item they were left on, each with when you wrote it, **Change** and **Delete**.

### Notes on the map

Notes left at a spot on the map can be shown as markers. Open **Layers** on the map and switch on **My
notes**; it is off until you switch it on. Click a note's marker (or select it with the Tab key and press
Enter) to read the note there, change it or delete it.

## Read marks

A source or an article counts as **read** only when you say so: press **Mark as read** in the reading
pane. Press **Mark as unread** to take it back. Opening something does not mark it read — so browsing
through a list with the arrow keys leaves your "not read yet" list as it was.

In Library's list, a small **dot** before a title means you have not marked it read.

## My library

At the top of Library's left-hand panel, **My library** has five shelves:

| shelf | what it lists |
|---|---|
| **Everything** | every source or article the filters select (the usual list) |
| **Bookmarks** | everything you bookmarked, newest first — sources, articles, places, events, spots on the map, saved views and periods |
| **My notes** | your notes, the most recently changed first, each with what it was left on |
| **Not read yet** | the sources (or, on the Articles tab, the articles) you have not marked read, by title |
| **Recently opened** | the sources and articles you opened lately, the last one first |

Click a row to go back to it: a source or an article opens in the reading pane beside the list; a place,
an event or a spot on the map opens in Explore at its place (and its years); a saved view opens with its
filters and its map; a research goal opens on its page.

## Menus: right click, or the keyboard

Many things have a menu of actions:

- **A row** in Explore's results or Library's list, and **the reading pane**: copy the item as plain text,
  as Markdown (a link with its title, and the `[[citekey]]` of a source), or as a link; bookmark it; open
  it in a new tab.
- **The map**: bookmark this place, add a note here, lock the map, start the map here, bookmark this view
  of the map.
- **The timeline**: lock the timeline, start the timeline at these years, add these years to my periods.

Open a menu with a right click, or with the keyboard: move to the thing with Tab and press the menu key
(or Shift+F10). The map, the timeline and the reading pane also have a **⋯** button that opens the same
menu. In a menu, the arrow keys move, Enter chooses, and Escape closes it. Every action in a menu can also
be reached without it.

### Locking the map or the timeline

**Lock the map** stops the map from moving when you drag it, scroll or use the keys — useful while you
compare things at one spot. A notice at the top of the map says it is locked; press **Unlock** there (or
use the menu again). **Lock the timeline** does the same for the timeline's years.

### Where the map and the timeline start

**Start the map here** (the map's menu) makes Histmersion always open the map at that spot and zoom. **Start
the timeline at these years** (the timeline's menu) makes the timeline open on the years it shows now.
Both are settings, saved in your [settings file](settings.md), and apply to everyone who opens
Histmersion from this server. You can change them again under **Settings › General**, or in the file itself.

### My periods

The timeline's **Zoom to period** list has, above the periods the experts date, a part called **My
periods**. To add one, show the years you want on the timeline, open the timeline's menu and choose **Add
these years to my periods…**, then give them a name. Choose a period in the list to show its years again;
the **×** beside it removes it. My periods are kept as bookmarks too, so they also appear in **My library ›
Bookmarks**.

## The width of the results

The list of results on Explore's right is wide enough for all its tabs and their counts. To change its
width, drag its left edge — or select the edge with the Tab key and use ← (wider) and → (narrower); Home
and End make it as narrow or as wide as it goes. Your browser remembers the width.
