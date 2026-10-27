#!/usr/bin/env python3
"""Tests of build.py's transformations (#1064). Stdlib only:

    python3 -m unittest db/gazetteer/test_build.py
"""
import gzip, io, json, os, re, sys, tempfile, unittest, zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build  # noqa: E402


def gn_row(gid, name, lat, lon, fclass, fcode, cc="TR", population=0, ascii_name=None):
    cols = [str(gid), name, ascii_name or name, "", str(lat), str(lon), fclass, fcode, cc, "", "", "", "", "",
            str(population), "", "", "Europe/Istanbul", "2026-10-01"]
    return "\t".join(cols)


def alt_row(aid, gid, lang, name, historic="", frm="", to=""):
    return "\t".join([str(aid), str(gid), lang, name, "", "", "", historic, frm, to])


def features(path):
    with gzip.open(path, "rt", encoding="utf-8") as fh:
        return [json.loads(line) for line in fh]


class GeoNames(unittest.TestCase):
    ROWS = [
        gn_row(1, "Boğazkale", 40.02, 34.61, "P", "PPLA2", population=1500, ascii_name="Bogazkale"),
        gn_row(2, "Hamlet", 40.1, 34.7, "P", "PPL"),                          # a bare dot: dropped
        gn_row(3, "Old Hamlet", 40.2, 34.8, "P", "PPL"),                      # kept: a historic name
        gn_row(4, "Hotel Hattusa", 40.03, 34.62, "S", "HTL"),                 # a hotel: dropped
        gn_row(5, "Hattusa ruins", 40.02, 34.61, "S", "ANS"),                 # an ancient site: kept
        gn_row(6, "Somewhere far", 40.0, -100.0, "P", "PPLA"),                # outside the old window: kept
        gn_row(7, "Quarter", 40.0, 34.0, "P", "PPLX"),                        # a section: dropped
        gn_row(8, "Linked village", 40.0, 35.0, "P", "PPL"),                  # kept: a Wikidata link
    ]
    ALTS = [
        alt_row(10, 1, "hit", "Ḫattuša"),
        alt_row(11, 1, "tr", "Boğazköy", historic="1", to="1937"),
        alt_row(12, 1, "link", "https://en.wikipedia.org/wiki/Bo%C4%9Fazkale"),
        alt_row(13, 1, "zh", "博阿兹卡莱"),
        alt_row(14, 3, "en", "Ancient Hamlet", historic="1"),
        alt_row(15, 8, "wkdt", "Q999"),
        alt_row(16, 1, "en", "Mikoyan", frm="1935", to="1956"),
    ]

    def build(self, scope=build.DEFAULT_SCOPE):
        d = tempfile.mkdtemp()
        a, n, out = (os.path.join(d, f) for f in ("all.zip", "alt.zip", "out.ndjson.gz"))
        with zipfile.ZipFile(a, "w") as z:
            z.writestr("allCountries.txt", "\n".join(self.ROWS) + "\n")
        with zipfile.ZipFile(n, "w") as z:
            z.writestr("alternateNamesV2.txt", "\n".join(self.ALTS) + "\n")
        stats = build.geonames(a, n, out, scope)
        return stats, out

    def test_filters_kinds_and_bare_hamlets_worldwide(self):
        stats, out = self.build()
        ids = [f["@id"] for f in features(out)]
        self.assertEqual(ids, [f"https://sws.geonames.org/{i}/" for i in (1, 3, 5, 6, 8)],
                         "by kind, never by place: the seat in the Americas is kept")
        self.assertEqual(stats["kept"], 5)
        self.assertEqual(stats["scope"], {"bbox": "world", "langs": "all"})

    def test_a_regional_extract_is_clipped_to_its_bbox_and_languages(self):
        stats, out = self.build(build.Scope(build.WINDOW, build.WINDOW_LANGS))
        fs = features(out)
        self.assertEqual([f["@id"] for f in fs], [f"https://sws.geonames.org/{i}/" for i in (1, 3, 5, 8)])
        names = {(n["toponym"], n.get("lang", "")) for n in fs[0]["names"]}
        self.assertNotIn(("博阿兹卡莱", "zh"), names, "a language outside the regional list")
        self.assertIn(("Ḫattuša", "hit"), names)

    def test_names_languages_and_dates(self):
        _, out = self.build()
        f = {x["@id"]: x for x in features(out)}["https://sws.geonames.org/1/"]
        names = {(n["toponym"], n.get("lang", "")): n for n in f["names"]}
        self.assertIn(("Ḫattuša", "hit"), names)
        self.assertIn(("Bogazkale", ""), names)
        self.assertIn(("博阿兹卡莱", "zh"), names, "every language is kept by default")
        self.assertFalse(any("wikipedia" in n["toponym"] for n in f["names"]), "a link is no name")
        self.assertEqual(names[("Boğazköy", "tr")]["when"], {"timespans": [{"end": {"in": "1937"}}]})
        self.assertEqual(names[("Mikoyan", "en")]["when"], {"timespans": [{"start": {"in": "1935"}, "end": {"in": "1956"}}]})
        self.assertEqual(f["properties"]["kind"], "site")
        self.assertTrue(f["properties"]["modern"])
        self.assertEqual(f["geometry"], {"type": "Point", "coordinates": [34.61, 40.02]})

    def test_wikidata_link(self):
        _, out = self.build()
        f = {x["@id"]: x for x in features(out)}["https://sws.geonames.org/8/"]
        self.assertEqual(f["links"], [{"type": "exactMatch", "identifier": "wd:Q999"}])

    def test_deterministic_bytes(self):
        _, a = self.build()
        _, b = self.build()
        self.assertEqual(build.sha256(a), build.sha256(b))


class Wikidata(unittest.TestCase):
    def write(self, d, name, header, rows):
        with open(os.path.join(d, name + ".tsv"), "w", encoding="utf-8") as fh:
            fh.write("\t".join("?" + h for h in header) + "\n")
            for r in rows:
                fh.write("\t".join(r) + "\n")

    def build(self, scope=build.DEFAULT_SCOPE):
        d = tempfile.mkdtemp()
        e = "<http://www.wikidata.org/entity/{}>"
        self.write(d, "core", ["item", "root", "lon", "lat"], [
            [e.format("Q5684"), e.format("Q15661340"), "44.42415", "32.5363"],
            [e.format("Q5684"), e.format("Q486972"), "44.42111", "32.5425"],
            [e.format("Q1"), e.format("Q486972"), "30.0", "40.0"],     # a plain settlement: dropped
            [e.format("Q2"), e.format("Q486972"), "31.0", "41.0"],     # a settlement with a GeoNames id: kept
            [e.format("Q3"), e.format("Q839954"), "-72.54583", "-13.16333"],  # an archaeological site in the Andes
        ])
        self.write(d, "labels", ["item", "name"], [
            [e.format("Q5684"), '"Babylon"@en'], [e.format("Q5684"), '"Βαβυλών"@grc'],
            [e.format("Q1"), '"Nowhere"@en'], [e.format("Q2"), '"Linked"@en'], [e.format("Q5684"), '"巴比伦"@zh'],
            [e.format("Q3"), '"Machu Picchu"@mul'], [e.format("Q3"), '"Machu Pikchu"@qu']])
        self.write(d, "aliases", ["item", "name"], [[e.format("Q5684"), '"Babili \\"Gate of God\\""@en']])
        self.write(d, "time_P571", ["item", "t"], [[e.format("Q5684"), "-2200-01-01T00:00:00Z"]])
        for p in ("P576", "P580", "P582"):
            self.write(d, f"time_{p}", ["item", "t"], [])
        self.write(d, "id_P1667", ["item", "v"], [])
        self.write(d, "native", ["item", "name"], [])
        self.write(d, "id_P1566", ["item", "v"], [[e.format("Q5684"), '"98228"'], [e.format("Q2"), '"123"']])
        self.write(d, "id_P1584", ["item", "v"], [[e.format("Q5684"), '"893951"']])
        self.write(d, "official", ["item", "name", "start", "end"], [[e.format("Q2"), '"Linkedgrad"@ru', "1920-01-01T00:00:00Z", "1991-01-01T00:00:00Z"]])
        out = os.path.join(d, "out.ndjson.gz")
        return build.wikidata(d, out, scope), out

    def test_items_names_dates_links(self):
        stats, out = self.build()
        fs = {f["@id"].rsplit("/", 1)[1]: f for f in features(out)}
        self.assertEqual(sorted(fs), ["Q2", "Q3", "Q5684"])
        b = fs["Q5684"]
        self.assertEqual(b["properties"]["kind"], "site", "ancient city outranks settlement")
        self.assertEqual(b["properties"]["class"], "Q15661340")
        self.assertEqual(b["when"], {"timespans": [{"start": {"in": "-2201"}}]}, "XSD 1.1 year -2200 is 2201 BCE")
        self.assertNotIn("modern", b["properties"])
        names = {(n["toponym"], n.get("lang", "")) for n in b["names"]}
        self.assertIn(("Βαβυλών", "grc"), names)
        self.assertIn(('Babili "Gate of God"', "en"), names)
        self.assertIn(("巴比伦", "zh"), names, "every language is kept by default")
        self.assertEqual(b["links"], [{"identifier": "gn:98228", "type": "exactMatch"},
                                      {"identifier": "pl:893951", "type": "closeMatch"}])
        self.assertEqual(b["geometry"]["coordinates"], [44.42111, 32.5425], "the smallest of several points")
        q2 = {(n["toponym"], n.get("lang", "")): n for n in fs["Q2"]["names"]}
        self.assertEqual(q2[("Linkedgrad", "ru")]["when"], {"timespans": [{"start": {"in": "1920"}, "end": {"in": "1991"}}]})
        self.assertTrue(fs["Q2"]["properties"]["modern"], "undated: never drawn on the time-sliced map")

    def test_every_continent_and_language_by_default(self):
        _, out = self.build()
        mp = {f["@id"].rsplit("/", 1)[1]: f for f in features(out)}["Q3"]
        self.assertEqual(mp["properties"]["title"], "Machu Picchu", "no English label: the 'mul' one")
        self.assertEqual({(n["toponym"], n.get("lang", "")) for n in mp["names"]},
                         {("Machu Picchu", ""), ("Machu Pikchu", "qu")}, "'mul' is a name in no particular language")

    def test_a_regional_extract_from_a_world_fetch(self):
        stats, out = self.build(build.Scope(build.WINDOW, build.WINDOW_LANGS))
        fs = {f["@id"].rsplit("/", 1)[1]: f for f in features(out)}
        self.assertEqual(sorted(fs), ["Q2", "Q5684"], "the Andes are outside the window")
        self.assertNotIn(("巴比伦", "zh"), {(n["toponym"], n.get("lang", "")) for n in fs["Q5684"]["names"]})
        self.assertEqual(stats["scope"]["bbox"], "-25,5,100,72")


class WikidataTimesAndDescriptions(unittest.TestCase):
    """The rebuilt extract (2026-10-09): precision, rank, the century guard, descriptions."""
    E = "<http://www.wikidata.org/entity/{}>"
    R = "<http://wikiba.se/ontology#{}>"

    def build(self):
        d = tempfile.mkdtemp()
        e, r = self.E, self.R
        w = Wikidata.write
        w(self, d, "core", ["item", "root", "lon", "lat"], [
            [e.format("Q131839713"), e.format("Q3024240"), "-3.48", "40.88"],   # Kingdom of Patones
            [e.format("Q5684"), e.format("Q15661340"), "44.42", "32.54"],        # Babylon
            [e.format("Q12560"), e.format("Q3024240"), "32.0", "39.0"],          # Ottoman Empire
            [e.format("Q7"), e.format("Q3024240"), "10.0", "45.0"],              # a century typed as a year
        ])
        w(self, d, "labels", ["item", "name"], [
            [e.format("Q131839713"), '"Kingdom of Patones"@en'], [e.format("Q5684"), '"Babylon"@en'],
            [e.format("Q12560"), '"Ottoman Empire"@en'], [e.format("Q7"), '"Seven"@en']])
        w(self, d, "aliases", ["item", "name"], [])
        w(self, d, "time_P571", ["item", "t", "prec", "rank"], [
            [e.format("Q5684"), "-2200-01-01T00:00:00Z", "6", r.format("NormalRank")],
            [e.format("Q5684"), "-1893-01-01T00:00:00Z", "9", r.format("DeprecatedRank")],
            [e.format("Q12560"), "1300-01-01T00:00:00Z", "9", r.format("NormalRank")],
            [e.format("Q12560"), "1299-08-05T00:00:00Z", "11", r.format("PreferredRank")],
            [e.format("Q7"), "0008-01-01T00:00:00Z", "9", r.format("NormalRank")],
        ])
        w(self, d, "time_P576", ["item", "t", "prec", "rank"], [
            [e.format("Q131839713"), "1769-08-03T00:00:00Z", "11", r.format("NormalRank")],
            [e.format("Q12560"), "1922-11-01T00:00:00Z", "11", r.format("NormalRank")],
            [e.format("Q7"), "1750-01-01T00:00:00Z", "7", r.format("NormalRank")],
        ])
        for p in ("P580", "P582"):
            w(self, d, f"time_{p}", ["item", "t", "prec", "rank"], [])
        for p in ("P1566", "P1584", "P1667"):
            w(self, d, f"id_{p}", ["item", "v"], [])
        w(self, d, "official", ["item", "name", "start", "end"], [])
        w(self, d, "native", ["item", "name"], [[e.format("Q131839713"), '"Reino de Patones"@es']])
        w(self, d, "descriptions", ["item", "d"], [
            [e.format("Q131839713"), '"former country in Modern Day Spain"@en'],
            [e.format("Q131839713"), '"microestado de Europa (siglo VIII-1769)"@es'],
            [e.format("Q131839713"), '"ancien micro Etat européen"@fr'],
            [e.format("Q7"), '"a state of the 8th century"@en'],
            [e.format("Q12560"), '"imperium osmańskie"@pl'], [e.format("Q12560"), '"Osmanlı"@tr'],
            [e.format("Q12560"), '"国家"@zh'],
        ])
        out = os.path.join(d, "out.ndjson.gz")
        stats = build.wikidata(d, out)
        return stats, {f["@id"].rsplit("/", 1)[1]: f for f in features(out)}

    def test_end_only_stays_end_only(self):
        _, fs = self.build()
        self.assertEqual(fs["Q131839713"]["when"], {"timespans": [{"end": {"in": "1769"}}]},
                         "no inception: the start is left out (unknown), never guessed")

    def test_precision_becomes_a_range(self):
        _, fs = self.build()
        self.assertEqual(fs["Q5684"]["when"], {"timespans": [{"start": {"earliest": "-3000", "latest": "-2001"}}]},
                         "a millennium (precision 6) is 3000-2001 BCE; the deprecated -1893 is ignored")
        self.assertEqual(fs["Q7"]["when"]["timespans"][0]["end"], {"earliest": "1701", "latest": "1800"})

    def test_preferred_rank_wins(self):
        _, fs = self.build()
        self.assertEqual(fs["Q12560"]["when"], {"timespans": [{"start": {"in": "1299"}, "end": {"in": "1922"}}]})

    def test_century_typed_as_year_is_dropped(self):
        stats, fs = self.build()
        self.assertNotIn("start", fs["Q7"]["when"]["timespans"][0], "8 stated to the year beside an 8th-century description")
        self.assertEqual(stats["century"], 1)

    def test_descriptions_english_and_native_language(self):
        stats, fs = self.build()
        self.assertEqual(fs["Q131839713"]["descriptions"], [
            {"value": "former country in Modern Day Spain", "lang": "en"},
            {"value": "microestado de Europa (siglo VIII-1769)", "lang": "es"}])
        self.assertNotIn("descriptions", fs["Q5684"])
        self.assertEqual(fs["Q12560"]["descriptions"], [{"value": "imperium osmańskie", "lang": "pl"}],
                         "no English and no native label: the first corpus language")
        self.assertEqual(stats["described"], 3)


class Helpers(unittest.TestCase):
    def test_ranges(self):
        self.assertEqual(build.wd_range(1769, 11), (1769, 1769))
        self.assertEqual(build.wd_range(1765, 8), (1760, 1769))
        self.assertEqual(build.wd_range(1750, 7), (1701, 1800))
        self.assertEqual(build.wd_range(1800, 7), (1701, 1800))
        self.assertEqual(build.wd_range(-500, 7), (-500, -401))
        self.assertEqual(build.wd_range(-2201, 6), (-3000, -2001))
        self.assertEqual(build.wd_range(1500, None), (1500, 1500))

    def test_century_guard(self):
        self.assertTrue(build.century_as_year(8, 9, [1769], []))
        self.assertTrue(build.century_as_year(11, 9, [], ["founded in the 11th century"]))
        self.assertFalse(build.century_as_year(79, 9, [400], []), "79 beside 400: plausible")
        self.assertFalse(build.century_as_year(-30, 9, [640], []), "30 BCE-640 CE: Roman Cyrenaica, not a century")
        self.assertFalse(build.century_as_year(50, 9, [640], []), "50 CE-640: no 50th century")
        self.assertFalse(build.century_as_year(12, 9, [600], []), "a 12th century would end after 1101")
        self.assertFalse(build.century_as_year(50, 9, [], ["a town of the 1st century"]), "inside the century named")
        self.assertFalse(build.century_as_year(40, 9, [400], ["deposits continuing into the 4th century CE"]),
                         "a year beside another century the text mentions is no century number")
        self.assertFalse(build.century_as_year(8, 7, [1769], []), "a century precision value is a century already")
        self.assertEqual(build.centuries_named(["microestado (siglo VIII-1769)", "XIe siècle"]), {8, 11})

    def test_years(self):
        self.assertEqual(build.year_of("201907"), 2019)
        self.assertEqual(build.year_of("1935"), 1935)
        self.assertEqual(build.year_of("-500"), -500)
        self.assertIsNone(build.year_of("soon"))
        self.assertEqual(build.wd_year("-0499-01-01T00:00:00Z"), -500)
        self.assertEqual(build.wd_year("0000-01-01T00:00:00Z"), -1)
        self.assertEqual(build.wd_year("1453-05-29T00:00:00Z"), 1453)

    def test_literals(self):
        self.assertEqual(build.literal('"Ἀθῆναι"@grc'), ("Ἀθῆναι", "grc"))
        self.assertEqual(build.literal('"a\\tb \\u00e9"@EN'), ("a\tb é", "en"))
        self.assertEqual(build.qid("<http://www.wikidata.org/entity/Q42>"), "Q42")

    def test_pin(self):
        d = tempfile.mkdtemp()
        f = os.path.join(d, "x.ndjson.gz")
        build.write_ndjson_gz(f, [{"@id": "a"}, {"@id": "b"}])
        m = os.path.join(d, "extracts.json")
        e = build.pin(m, "geonames", f, ["version=v1", "url="])
        self.assertEqual((e["features"], e["version"], e["url"]), (2, "v1", ""))
        with open(m) as fh:
            self.assertEqual(json.load(fh)["extracts"][0]["sha256"], build.sha256(f))


class FetchBackoff(unittest.TestCase):
    """QLever reports a query that ran out of time as HTTP 429 (#1403 item 5): ask in pages, halving the
    page on each further timeout; a plain 429 waits and asks again; both bounded."""
    Q = "SELECT DISTINCT ?item ?v WHERE { ?item <p> ?v . }"
    TIMEOUT = build.HttpFailure(429, '{"exception":"Timeout: the query has timed out after 60s"}')

    @staticmethod
    def tsv(rows):
        return ("?item\t?v\n" + "".join(f"<q{r}>\t{r}\n" for r in rows)).encode()

    def endpoint(self, total, too_big_above=None, script=()):
        """A fake endpoint holding `total` rows: a whole query, or a page bigger than `too_big_above`,
        times out; `script` failures are raised first, in order."""
        calls, script = [], list(script)

        def post(text):
            calls.append(text)
            if script:
                raise script.pop(0)
            if "LIMIT" not in text:
                if too_big_above is not None:
                    raise self.TIMEOUT
                return self.tsv(range(total))
            limit = int(text.split("LIMIT ")[1].split()[0])
            offset = int(text.split("OFFSET ")[1].split()[0])
            if too_big_above is not None and limit > too_big_above:
                raise self.TIMEOUT
            return self.tsv(range(offset, min(total, offset + limit)))
        return post, calls

    def run_fetch(self, post, **kw):
        out, waits = io.BytesIO(), []
        n = build.fetch_query(self.Q, post, out, sleep=waits.append, log=lambda m: None, **kw)
        return out.getvalue().decode(), n, waits

    def test_classify(self):
        self.assertEqual(build.classify(429, "Query timed out"), "timeout")
        self.assertEqual(build.classify(429, '{"exception":"Timeout after 60s"}'), "timeout")
        self.assertEqual(build.classify(503, "operation was cancelled"), "timeout")
        self.assertEqual(build.classify(500, '{"exception": "Tried to allocate 250 kB, but only 180.3 kB were available"}'),
                         "timeout", "out of memory: the query is too big, ask for less")
        self.assertEqual(build.classify(500, "Internal Server Error"), "fatal")
        self.assertEqual(build.classify(429, "Too Many Requests"), "busy")
        self.assertEqual(build.classify(503, ""), "busy")
        self.assertEqual(build.classify(400, "timed out"), "fatal", "a 400 is the query's fault, never retried")
        self.assertEqual(build.classify(404, ""), "fatal")

    def test_paged_orders_by_every_projected_variable(self):
        self.assertEqual(build.paged(self.Q, 10, 20), self.Q + " ORDER BY ?item ?v LIMIT 10 OFFSET 20")
        with self.assertRaises(ValueError):
            build.paged("ASK { ?s ?p ?o }", 1, 0)

    def test_whole_query_when_it_fits(self):
        post, calls = self.endpoint(5)
        text, n, waits = self.run_fetch(post)
        self.assertEqual((n, len(calls), waits), (1, 1, []))
        self.assertEqual(text, self.tsv(range(5)).decode())

    def test_timeout_switches_to_pages_and_halves_them(self):
        post, calls = self.endpoint(23, too_big_above=5)
        text, n, waits = self.run_fetch(post, first_page=20, min_page=2)
        self.assertEqual(text, self.tsv(range(23)).decode(), "every row once, one header, in order")
        self.assertEqual(waits, [], "a timeout asks for less at once — it does not wait")
        limits = [c.split("LIMIT ")[1].split()[0] for c in calls if "LIMIT" in c]
        self.assertEqual(limits[:3], ["20", "10", "5"], "whole → 20 → 10 → 5 rows")
        self.assertTrue(all(l == "5" for l in limits[2:]))
        self.assertEqual(n, 5, "pages of 5: 0, 5, 10, 15, 20")

    def test_a_result_cut_off_while_exported_is_a_timeout_not_a_result(self):
        cut = (b"?item\t?name\n<x>\t\"a\"@en\n<y>\t\"\xd5\n !!!!>># An error has occurred while exporting the query "
               b"result. Unfortunately due to limitations in the HTTP 1.1 protocol, there is no better way to report "
               b"this than to append it to the incomplete result. The error message was:\nOperation timed out.")
        self.assertEqual(build.export_failure(cut), "Operation timed out.")
        self.assertIsNone(build.export_failure(b"?item\n<x>\n"))
        answers = [cut, b"?item\t?name\n<x>\t1\n"]
        asked = []

        def post(q):
            asked.append(q)
            return answers.pop(0)
        out = io.BytesIO()
        build.fetch_query("SELECT ?item ?name WHERE { ?item ?p ?name }", post, out, sleep=lambda s: None, log=lambda m: None)
        self.assertEqual(out.getvalue(), b"?item\t?name\n<x>\t1\n", "nothing of the cut-off answer is kept")
        self.assertIn("LIMIT 250000 OFFSET 0", asked[1], "a timeout while exporting asks in pages")

    def test_split_band_halves_the_longer_side(self):
        self.assertEqual(build.split_band(build.WORLD), [(-180, -90, 0, 90), (0, -90, 180, 90)])
        self.assertEqual(build.split_band((0, -90, 180, 90)), [(0, -90, 90, 90), (90, -90, 180, 90)])
        self.assertEqual(build.split_band((0, 0, 10, 40)), [(0, 0, 10, 20), (0, 20, 10, 40)])

    def test_a_band_is_open_on_its_inner_east_and_north_edges(self):
        q = build.wd_scope(build.DEFAULT_SCOPE, (0, -90, 180, 0))
        self.assertIn("?lon >= 0 && ?lon <= 180 && ?lat >= -90 && ?lat < 0", q, "the world's own edge is closed")
        self.assertIn("BIND(geof:longitude", build.wd_queries(build.DEFAULT_SCOPE, (0, 0, 10, 10))["labels"])
        self.assertEqual(build.wd_queries()["core"].count("BIND(geof:longitude"), 1)
        self.assertEqual(build.wd_queries(band=(0, 0, 10, 10))["core"].count("BIND(geof:longitude"), 1)

    def test_a_query_too_big_for_the_world_is_asked_for_each_half(self):
        points = {"q1": (-100, 40), "q2": (10, 50), "q3": (0, 0), "q4": (150, -30), "q5": (179.9, 89)}
        calls = []

        def post(text):
            calls.append(text)
            m = re.search(r"\?lon >= (\S+) && \?lon (<=?) (\S+) && \?lat >= (\S+) && \?lat (<=?) (\S+)\)", text)
            if not m:
                raise build.HttpFailure(429, "Operation timed out")
            w, eop, e, so, nop, n = m.groups()
            w, e, so, n = float(w), float(e), float(so), float(n)
            if e - w > 90:
                raise build.HttpFailure(429, "Operation timed out. Last operation: Query planning")
            inside = [q for q, (lo, la) in points.items()
                      if lo >= w and (lo <= e if eop == "<=" else lo < e) and la >= so and (la <= n if nop == "<=" else la < n)]
            return ("?item\t?name\n" + "".join(f"<{q}>\t\"{q}\"@en\n" for q in inside)).encode()
        out = io.BytesIO()
        asked = build.fetch_banded("labels", build.DEFAULT_SCOPE, post, out, sleep=lambda s: None, log=lambda m: None)
        lines = out.getvalue().decode().splitlines()
        self.assertEqual(lines[0], "?item\t?name", "one header")
        self.assertEqual(sorted(l.split("\t")[0] for l in lines[1:]), ["<q1>", "<q2>", "<q3>", "<q4>", "<q5>"],
                         "every row once — the point on the 0° meridian and the equator too")
        self.assertEqual(asked, 4, "world → two halves → four quarters of 90°")
        self.assertFalse(any("LIMIT" in c for c in calls), "bands are asked whole, not in pages")

    def test_timeout_below_the_minimum_page_gives_up(self):
        post, _ = self.endpoint(100, too_big_above=1)
        with self.assertRaises(RuntimeError) as e:
            self.run_fetch(post, first_page=8, min_page=4)
        self.assertIn("minimum 4", str(e.exception))

    def test_busy_waits_retry_after_then_a_growing_pause(self):
        post, calls = self.endpoint(3, script=[build.HttpFailure(429, "Too Many Requests", "7"),
                                               build.HttpFailure(503, "busy")])
        text, n, waits = self.run_fetch(post)
        self.assertEqual(waits, [7, 120], "Retry-After first, then 60 s × the attempt")
        self.assertEqual(text, self.tsv(range(3)).decode())
        self.assertNotIn("LIMIT", calls[-1], "busy is no reason to page")

    def test_retries_are_bounded(self):
        post, calls = self.endpoint(3, script=[build.HttpFailure(429, "slow down")] * 10)
        with self.assertRaises(RuntimeError):
            self.run_fetch(post, retries=3)
        self.assertEqual(len(calls), 4, "the first request and 3 retries")

    def test_fatal_is_not_retried(self):
        post, calls = self.endpoint(3, script=[build.HttpFailure(400, "parse error")])
        with self.assertRaises(RuntimeError):
            self.run_fetch(post)
        self.assertEqual(len(calls), 1)

    def test_wikidata_fetch_writes_each_query_through_the_backoff(self):
        d = tempfile.mkdtemp()
        post, calls = self.endpoint(2)
        meta = build.wikidata_fetch("http://127.0.0.1:9/", d, pause=0, post=post, sleep=lambda s: None)
        self.assertEqual(sorted(meta["files"]), sorted(build.wd_queries()))
        self.assertEqual(len(calls), len(build.wd_queries()))
        again = build.wikidata_fetch("http://127.0.0.1:9/", d, pause=0, post=post, sleep=lambda s: None)
        self.assertEqual(len(calls), len(build.wd_queries()), "resumable: nothing asked twice")
        self.assertEqual(again["files"], meta["files"])


class Tgn(unittest.TestCase):
    """Getty TGN from its explicit N-Triples export (#1403): a fixture shaped like the real members."""
    G = "http://vocab.getty.edu/"

    def triples(self, rows):
        return "".join(f"{s} {p} {o} .\n" for s, p, o in rows)

    def build(self, scope=build.DEFAULT_SCOPE):
        g, gvp = self.G, "<http://vocab.getty.edu/ontology#"
        t = lambda i: f"<{g}tgn/{i}>"
        term = lambda i: f"<{g}tgn/term/{i}>"
        typ, pref, alt = build._P_TYPE, build._P_PREF, build._P_ALT
        subjects = [
            (t(7001393), typ, gvp + "AdminPlaceConcept>"), (t(7001393), build._P_PTYPE, f"<{g}aat/300008347>"),
            (t(7001393), pref, term("42382-en")), (t(7001393), alt, term("1001436872-grc")),
            (t(7001393), alt, term("165794-la")), (t(7001393), alt, term("156761")), (t(7001393), alt, term("1-zh")),
            (t(7001393), build._P_NOTE, f"<{g}tgn/scopeNote/38678>"),
            (t(7000001), typ, gvp + "AdminPlaceConcept>"), (t(7000001), build._P_PTYPE, f"<{g}aat/300008347>"),
            (t(7000001), pref, term("2-en")),                                   # a plain town: dropped
            (t(7000002), typ, gvp + "AdminPlaceConcept>"), (t(7000002), build._P_PTYPE, f"<{g}aat/300000810>"),
            (t(7000002), pref, term("3-en")),                                   # archaeological site: kept
            (t(7000003), typ, gvp + "PhysPlaceConcept>"), (t(7000003), pref, term("4-en")),
            (t(7000003), alt, term("5-grc")),                                   # a river with an ancient name
            (t(7000004), typ, gvp + "AdminPlaceConcept>"), (t(7000004), pref, term("6-la")),   # outside the old window
            (t(7030632), typ, gvp + "Facet>"),
        ]
        terms = [
            (term("42382-en"), build._P_LITERAL, '"Athens"@en'),
            (term("1001436872-grc"), build._P_LITERAL, '"Ἀθῆναι"@grc'),
            (term("165794-la"), build._P_LITERAL, '"Athenae"@la'),
            (term("165794-la"), build._P_START, '"-0200"^^<http://www.w3.org/2001/XMLSchema#gYear>'),
            (term("165794-la"), build._P_END, '"9999"^^<http://www.w3.org/2001/XMLSchema#gYear>'),
            (term("165794-la"), build._P_HISTORIC, "<http://vocab.getty.edu/historic/historic>"),
            (term("156761"), build._P_LITERAL, '"Athenai"'),
            (term("1-zh"), build._P_LITERAL, '"雅典"@zh'),
            (term("2-en"), build._P_LITERAL, '"Newtown"@en'),
            (term("3-en"), build._P_LITERAL, '"Old Mound"@en'),
            (term("4-en"), build._P_LITERAL, '"Kizilirmak"@en'),
            (term("5-grc"), build._P_LITERAL, '"Ἅλυς"@grc'),
            (term("6-la"), build._P_LITERAL, '"Far away"@la'),
        ]
        coords = []
        for i, (lat, lon) in {7001393: (37.983333, 23.733333), 7000001: (40.0, 30.0), 7000002: (41.0, 31.0),
                              7000003: (41.7, 35.9), 7000004: (-30.0, 150.0)}.items():
            coords += [(f"<{g}tgn/{i}-place>", build._P_LAT, f'"{lat}"^^<http://www.w3.org/2001/XMLSchema#decimal>'),
                       (f"<{g}tgn/{i}-place>", build._P_LONG, f'"{lon}"^^<http://www.w3.org/2001/XMLSchema#decimal>')]
        notes = [(f"<{g}tgn/scopeNote/38678>", build._P_VALUE, '"Was an ancient metropolis."@en')]
        aat_subjects = [(f"<{g}aat/300008347>", build._P_PREF_GVP, f"<{g}aat/term/1000-en>"),
                        (f"<{g}aat/300000810>", pref, f"<{g}aat/term/1001-en>"),
                        (f"<{g}aat/300000810>", pref, f"<{g}aat/term/1002-de>")]
        aat_terms = [(f"<{g}aat/term/1000-en>", build._P_LITERAL, '"inhabited places"@en'),
                     (f"<{g}aat/term/1001-en>", build._P_LITERAL, '"archaeological sites"@en'),
                     (f"<{g}aat/term/1002-de>", build._P_LITERAL, '"archäologische Stätte"@de')]
        d = tempfile.mkdtemp()
        tz, az, out = (os.path.join(d, f) for f in ("tgn.zip", "aat.zip", "tgn.ndjson.gz"))
        # as in the real export: a concept's label links sit in the Terms file, its note link with the notes
        links = [r for r in subjects if r[1] in (pref, alt)]
        note_links = [r for r in subjects if r[1] == build._P_NOTE]
        subjects = [r for r in subjects if r not in links and r not in note_links]
        with zipfile.ZipFile(tz, "w") as z:
            z.writestr("TGNOut_1Subjects.nt", self.triples(subjects))
            z.writestr("TGNOut_2Terms.nt", self.triples(terms + links))
            z.writestr("TGNOut_Coordinates.nt", self.triples(coords))
            z.writestr("TGNOut_ScopeNotes.nt", self.triples(note_links + notes))
        with zipfile.ZipFile(az, "w") as z:
            z.writestr("AATOut_1Subjects.nt", "")
            z.writestr("AATOut_2Terms.nt", self.triples(aat_terms + aat_subjects))
        stats = build.tgn(tz, az, out, scope)
        return stats, {f["@id"].rsplit("/", 1)[1]: f for f in features(out)}, out

    def test_classes_and_the_keep_rule(self):
        stats, fs, _ = self.build()
        self.assertEqual(sorted(fs), ["7000002", "7000003", "7000004", "7001393"],
                         "a plain town is dropped and the facet is no candidate; the place in Australia is kept")
        self.assertEqual((stats["window"], stats["candidates"], stats["kept"]), (5, 5, 4))

    def test_a_regional_extract(self):
        stats, fs, _ = self.build(build.Scope(build.WINDOW, build.WINDOW_LANGS))
        self.assertEqual(sorted(fs), ["7000002", "7000003", "7001393"], "the far-away place is no candidate")
        self.assertEqual((stats["window"], stats["candidates"], stats["kept"]), (4, 4, 3))
        self.assertNotIn(("雅典", "zh"), {(n["toponym"], n.get("lang", "")) for n in fs["7001393"]["names"]})

    def test_athens(self):
        _, fs, _ = self.build()
        a = fs["7001393"]
        self.assertEqual(a["@id"], "http://vocab.getty.edu/tgn/7001393")
        self.assertEqual(a["properties"], {"title": "Athens", "kind": "site", "source": build.TGN_SOURCE,
                                           "modern": True, "placeType": "inhabited places"})
        self.assertEqual(a["geometry"], {"type": "Point", "coordinates": [23.73333, 37.98333]})
        names = {(n["toponym"], n.get("lang", "")): n for n in a["names"]}
        self.assertEqual(names[("Athenae", "la")]["when"], {"timespans": [{"start": {"in": "-200"}}]},
                         "200 BCE on; 9999 is Getty's 'the present', an open end")
        self.assertTrue(names[("Athenae", "la")]["historic"])
        self.assertIn(("Ἀθῆναι", "grc"), names)
        self.assertIn(("Athenai", ""), names)
        self.assertIn(("雅典", "zh"), names, "every language is kept by default")
        self.assertEqual(a["descriptions"], [{"value": "Was an ancient metropolis.", "lang": "en"}])
        self.assertEqual(a["types"], [{"identifier": "aat:300008347", "label": "inhabited places"}])
        self.assertNotIn("links", a, "TGN carries no links: it joins by its own id")

    def test_kinds(self):
        _, fs, _ = self.build()
        self.assertEqual(fs["7000002"]["properties"]["kind"], "site")
        self.assertEqual(fs["7000002"]["properties"]["placeType"], "archaeological sites")
        self.assertEqual(fs["7000003"]["properties"]["kind"], "feature", "a physical place is a feature")
        self.assertEqual(build.tgn_kind({"AdminPlaceConcept"}, "former nation/state/empire"), "polity")
        self.assertEqual(build.tgn_kind({"AdminPlaceConcept"}, "historical region"), "region")
        self.assertEqual(build.tgn_kind({"PhysAdminPlaceConcept"}, "islands"), "site")

    def test_helpers(self):
        self.assertEqual(build.tgn_year('"-0200"^^<http://www.w3.org/2001/XMLSchema#gYear>'), -200)
        self.assertEqual(build.tgn_year('"1453"^^<x>'), 1453)
        self.assertIsNone(build.tgn_year('"9999"^^<x>'))
        self.assertIsNone(build.tgn_year('"-9999"^^<x>'))
        self.assertEqual(build.tgn_lang("el-Latn"), "", "a transliteration is in no language")
        self.assertEqual(build.tgn_lang("grc"), "grc")
        self.assertEqual(build.tgn_lang("zh-Hant"), "zh")
        self.assertEqual(build.getty_id("<http://vocab.getty.edu/tgn/term/165794-la>", "tgn/term"), "165794-la")
        self.assertIsNone(build.getty_id("<http://vocab.getty.edu/aat/300008347>", "tgn"))

    def test_deterministic_bytes(self):
        _, _, a = self.build()
        _, _, b = self.build()
        self.assertEqual(build.sha256(a), build.sha256(b))


class Scopes(unittest.TestCase):
    """The world and every language by default; `--bbox` / `--langs` for a regional extract."""

    def test_parse_bbox(self):
        self.assertEqual(build.parse_bbox(None), build.WORLD)
        self.assertEqual(build.parse_bbox("world"), build.WORLD)
        self.assertEqual(build.parse_bbox("window"), (-25.0, 5.0, 100.0, 72.0))
        self.assertEqual(build.parse_bbox("-10, 30, 40, 60"), (-10.0, 30.0, 40.0, 60.0))
        for bad in ("1,2,3", "10,0,5,1", "0,10,5,1", "-200,0,10,10", "a,b,c,d"):
            with self.assertRaises(ValueError, msg=bad):
                build.parse_bbox(bad)

    def test_parse_langs(self):
        self.assertIsNone(build.parse_langs(None))
        self.assertIsNone(build.parse_langs("all"))
        self.assertEqual(build.parse_langs("window"), build.WINDOW_LANGS)
        self.assertEqual(build.parse_langs("en, zh"), frozenset({"en", "zh"}))

    def test_scope(self):
        world = build.Scope()
        self.assertTrue(world.world)
        self.assertTrue(world.contains(-72.5, -13.2) and world.contains(151.2, -33.9) and world.contains(116.4, 39.9))
        self.assertEqual((world.lang("zh"), world.lang("mul"), world.lang("")), ("zh", "", ""))
        win = build.Scope(build.WINDOW, build.WINDOW_LANGS)
        self.assertFalse(win.contains(-72.5, -13.2))
        self.assertEqual((win.lang("zh"), win.lang("grc"), win.lang("mul")), (None, "grc", ""))
        self.assertEqual(win.describe(), {"bbox": "-25,5,100,72", "langs": len(build.WINDOW_LANGS)})

    def test_historical_languages_of_every_continent(self):
        for code in ("grc", "lzh", "sa", "gez", "nci", "emy", "qwc", "kaw"):
            self.assertIn(code, build.HISTORICAL_LANGS)

    def test_world_queries_carry_no_bbox_and_no_language_filter(self):
        q = build.wd_queries()
        self.assertNotIn("FILTER", q["labels"])
        self.assertNotIn("FILTER", q["core"])
        self.assertIn("?lon", q["core"])
        self.assertIn("FILTER(LANG(?d) IN (", q["descriptions"], "descriptions in every language do not finish")
        self.assertIn('"zh"', q["descriptions"])
        r = build.wd_queries(build.Scope(build.WINDOW, build.WINDOW_LANGS))
        self.assertIn("?lon >= -25 && ?lon <= 100 && ?lat >= 5 && ?lat <= 72", r["labels"])
        self.assertIn('FILTER(LANG(?name) IN ("akk"', r["labels"])
        self.assertNotIn('"zh"', r["labels"])


class OutputDir(unittest.TestCase):
    """Built extracts go where the server reads them by default: the XDG cache directory (#1403)."""

    def test_cache_dir_follows_xdg(self):
        self.assertEqual(build.cache_dir({"XDG_CACHE_HOME": "/x", "HOME": "/h"}), "/x/histmersion/gazetteer")
        self.assertEqual(build.cache_dir({"HOME": "/h"}), "/h/.cache/histmersion/gazetteer")
        self.assertEqual(build.cache_dir({"XDG_CACHE_HOME": " ", "HOME": "/h"}), "/h/.cache/histmersion/gazetteer")

    def test_out_path(self):
        d = tempfile.mkdtemp()
        self.assertEqual(build.out_path("a.ndjson.gz", env={"XDG_CACHE_HOME": d}),
                         os.path.join(d, "histmersion", "gazetteer", "a.ndjson.gz"))
        self.assertTrue(os.path.isdir(os.path.join(d, "histmersion", "gazetteer")))
        self.assertEqual(build.out_path("a.ndjson.gz", out_dir=os.path.join(d, "o")), os.path.join(d, "o", "a.ndjson.gz"))
        self.assertEqual(build.out_path("./a.ndjson.gz"), "./a.ndjson.gz", "a directory part: as given")

    def test_split_options(self):
        self.assertEqual(build.split_options(["geonames", "--bbox", "window", "a", "--langs=en,zh", "--out", "/d"]),
                         ({"bbox": "window", "langs": "en,zh", "out": "/d"}, ["geonames", "a"]))
        self.assertEqual(build.split_options(["x"]), ({}, ["x"]))

    def test_split_out(self):
        self.assertEqual(build.split_out(["geonames", "--out", "/d", "a", "b"]), ("/d", ["geonames", "a", "b"]))
        self.assertEqual(build.split_out(["x", "--out=/e"]), ("/e", ["x"]))
        self.assertEqual(build.split_out(["x"]), (None, ["x"]))


if __name__ == "__main__":
    unittest.main()
