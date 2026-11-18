#!/usr/bin/env python3
"""Applies ReExportAliases (g46) or ShortenQualified (g47) to some modules and backs off every edit the compiler or the
SemanticDB comparison (tools/refactor/SymbolOracle.scala) rejects, until the module compiles and every occurrence denotes the
same symbol as before. Rejected edits are appended to tools/refactor/<rule>-exclusions.tsv with the reason.

usage: qualified-round.py <g46|g47> <work-dir> <compile-script> --modules modules/api ... --projects api apiJS ...

Starts from the committed sources of the modules (git checkout), so it can be rerun. Needs a SemanticDB build of the current
sources (the compile script runs sbt with -Xsemanticdb)."""
import os, re, shutil, subprocess, sys, glob, argparse

ap = argparse.ArgumentParser()
ap.add_argument('rule', choices=['g46', 'g47'])
ap.add_argument('work')
ap.add_argument('compile')
ap.add_argument('--modules', nargs='+', required=True)
ap.add_argument('--projects', nargs='+', required=True)
ap.add_argument('--rounds', type=int, default=8)
ap.add_argument('--check-modules', nargs='*', default=[], help='modules only compared, never rewritten (dependants)')
ap.add_argument('--check-projects', nargs='*', default=[])
ap.add_argument('--no-pkg', action='store_true')
a = ap.parse_args()

RULE = {'g46': 'ReExportAliases', 'g47': 'ShortenQualified'}[a.rule]
EXCL = f'tools/refactor/{a.rule}-exclusions.tsv'
work = os.path.abspath(a.work)
os.makedirs(work, exist_ok=True)
snap = os.path.join(work, 'snapshot')
ledger = os.path.join(work, 'ledger.tsv')
conf = os.path.join(work, 'rule.conf')
mods = [m.rstrip('/') for m in a.modules]
checked = mods + [m.rstrip('/') for m in a.check_modules]
# the build's Scala: sbt writes target/build-versions.properties from project/Dependencies.scala whenever it loads
SCALA = subprocess.run(['bash', 'tools/refactor/scalafix.sh', '--print', 'scala'], check=True, capture_output=True,
                       text=True).stdout.strip()
# Scalafix at the build's versions, run with the build's Scala as --scala-version
SCALAFIX = "SCALAFIX_JAVA_OPTS='-Xmx6g -Xss8m' bash tools/refactor/scalafix.sh"

def sh(cmd, log=None, timeout=3000):
    with open(log, 'w') if log else open(os.devnull, 'w') as out:
        return subprocess.run(cmd, shell=True, stdout=out, stderr=subprocess.STDOUT, timeout=timeout).returncode

def take_snapshot():
    shutil.rmtree(snap, ignore_errors=True)
    for d in glob.glob(f'target/out/*/scala-{SCALA}/*/*classes/META-INF/semanticdb'):
        for m in checked:
            src = os.path.join(d, m)
            if os.path.isdir(src):
                shutil.copytree(src, os.path.join(snap, d.replace('target/out/', '', 1), m))
    os.makedirs(os.path.join(snap, 'src'), exist_ok=True)  # the committed sources, whatever the working tree holds
    sh(f"git archive HEAD {' '.join(checked)} | tar -x -C {os.path.join(snap, 'src')}")

def exclusions():
    if not os.path.exists(EXCL): return set()
    return {l.split('\t')[0].strip() for l in open(EXCL) if l.strip() and not l.startswith('#')}

def exclude(keys, reason):
    have = exclusions()
    new = [k for k in dict.fromkeys(keys) if k not in have]
    with open(EXCL, 'a') as f:
        for k in new: f.write(f'{k}\t{reason}\n')
    return new

def write_conf():
    scope = ', '.join(f'"{m}/"' for m in mods)
    rew = os.path.join(work, 'rewritten.txt')
    cfg = {'g46': f'ReExportAliases {{ exclusions = "{EXCL}", ledger = "{ledger}", scope = [{scope}], packageExports = {"false" if a.no_pkg else "true"}, rewritten = "{rew if os.path.exists(rew) else ""}" }}',
           'g47': f'ShortenQualified {{ exclusions = "{EXCL}", ledger = "{ledger}", chainPackages = {"false" if a.no_pkg else "true"} }}'}[a.rule]
    open(conf, 'w').write(f'rules = ["file:tools/refactor/{RULE}.scala"]\n{cfg}\n')

def run_rule(n):
    sh('git checkout -- ' + ' '.join(mods))
    if True:  # the rule, its index and the snapshot need SemanticDB of the committed sources (mostly sbt cache hits)
        tasks = ' '.join(f'{p}/compile {p}/Test/compile' for p in a.projects + a.check_projects)
        sh(f'{a.compile} {a.rule}-pristine-{n} {tasks}', log=os.path.join(work, f'pristine-{n}.summary'), timeout=3000)
    files = ' '.join(f'--files {m}' for m in mods)
    rew = os.path.join(work, 'rewritten.txt')
    if a.rule == 'g46':
        # pinning needs the pairs that really get rewritten: iterate the rule until that set is stable
        if os.path.exists(rew): os.remove(rew)
        prev = None
        for it in range(6):
            sh('git checkout -- ' + ' '.join(mods))
            if os.path.exists(ledger): os.remove(ledger)
            write_conf()
            sh(f'{SCALAFIX} --config {conf} --sourceroot . --auto-classpath '
               f'--auto-classpath-roots target/out {files}', log=os.path.join(work, f'scalafix-{n}.log'), timeout=5400)
            cur = sorted({x for l in (open(ledger) if os.path.exists(ledger) else []) for x in l.rstrip('\n').split('\t')[8].split(',') if x})
            print(f'  pass {it + 1}: {len(cur)} rewritten pairs', flush=True)
            if cur == prev: break
            prev = cur
            open(rew, 'w').write('\n'.join(cur) + '\n')
        sh('git checkout -- ' + ' '.join(mods))
    if os.path.exists(ledger): os.remove(ledger)
    write_conf()
    rc = sh(f'{SCALAFIX} --config {conf} --sourceroot . --auto-classpath '
            f'--auto-classpath-roots target/out {files}', log=os.path.join(work, f'scalafix-{n}.log'), timeout=5400)
    if rc not in (0, 1):  # 1: files changed (nothing else is reported as an error)
        print(open(os.path.join(work, f'scalafix-{n}.log')).read()[-4000:])
    rows = [l.rstrip('\n').split('\t') for l in open(ledger)] if os.path.exists(ledger) else []
    return rows

def by_file(rows):
    m = {}
    for r in rows: m.setdefault(r[0], []).append(r)
    return m

def line_starts(path):
    t = open(path, encoding='utf-8').read()
    s = [0]
    for i, c in enumerate(t):
        if c == '\n': s.append(i + 1)
    return s

def keys_at(rows, path, off):
    """The edits a problem at `off` (an offset in the rewritten file) belongs to."""
    rs = rows.get(path, [])
    if a.rule == 'g46':
        hit = [r for r in rs if int(r[4]) <= off <= int(r[5])]
        return [r[6] for r in hit]
    drops = [r for r in rs if r[1] == 'drop' and int(r[4]) <= off]
    if drops:
        best = max(drops, key=lambda r: int(r[4]))
        if off - int(best[4]) < 400:
            # an edit that needs an added import: the import is the likelier cause (cycles), back it off for the file
            imported = {n for r in rs if r[1] in ('import', 'pkg') and '@import:' in r[6] for n in r[6].split('@import:', 1)[1].split(',')}
            imported |= {n for r in rs if r[1] == 'import' for n in re.findall(r'import \S+\.([^.\s]+)', r[7].replace('\\n', '\n'))}
            first = best[7].split('|', 1)[1].split(' ')[0].split('.')[0] if '|' in best[7] else ''
            return [f'{path}@import:{first}'] if first in imported else [best[6]]
    return []

def fallback_keys(rows, path):
    rs = rows.get(path, [])
    if a.rule == 'g46': return []
    pk = [r[6] for r in rs if r[1] == 'pkg']
    if pk: return pk
    imp = [r for r in rs if r[1] == 'import']
    if imp:
        names = imp[0][6].split('@import:', 1)[1].split(',')
        return [f'{path}@import:{n}' for n in names]
    return [f'{path}@*']

PROJECT = {'values': 'values', 'api': 'api', 'upstreams': 'upstreams', 'config': 'config', 'files': 'files', 'core': 'core',
           'plugin-api': 'pluginApi', 'db': 'db', 'plugin-host': 'pluginHost', 'plugin-testkit': 'pluginTestkit',
           'plugin-vaults': 'pluginVaults', 'plugins/fetchers': 'pluginsFetchers', 'plugins/events': 'pluginsEvents',
           'plugins/media': 'pluginsMedia', 'plugins/gazetteers': 'pluginsGazetteers', 'plugins/ai-http': 'pluginsAiHttp',
           'plugins/calibre': 'pluginsCalibre', 'plugin-resolver': 'pluginResolver', 'console': 'console',
           'chimney-iron': 'chimneyIron', 'chimney-iron-tests': 'chimneyIronTests', 'cli': 'cli', 'web': 'web', 'server': 'server'}

def project_of(path):
    m = re.match(r'modules/((?:plugins/)?[^/]+)/', path)
    return PROJECT.get(m.group(1)) if m else None

ERR = re.compile(r'^\[error\] -- (?:\[E\d+\] )?.*?(/\S+?\.scala):(\d+):(\d+)')

def compile_errors(log):
    out = []
    here = os.getcwd() + '/'
    for l in open(log, errors='ignore'):
        l = re.sub(r'\x1b\[[0-9;]*m', '', l)
        m = ERR.match(l)
        if m: out.append((m.group(1).replace(here, ''), int(m.group(2)), int(m.group(3))))
    return list(dict.fromkeys(out))

def main():
    for n in range(1, a.rounds + 1):
        rows = run_rule(n)
        slog = open(os.path.join(work, f'scalafix-{n}.log'), errors='ignore').read()
        if 'error:' in slog:
            print('scalafix reported errors:\n' + slog[:3000]); return 5
        if n == 1: take_snapshot()
        fr = by_file(rows)
        print(f'round {n}: {len(rows)} ledger rows in {len(fr)} files', flush=True)
        if not rows: return 0
        tasks = ' '.join(f'{p}/compile {p}/Test/compile' for p in a.projects + a.check_projects)
        clog = os.path.join(work, f'compile-{n}.log')
        sh(f'{a.compile} {a.rule}-{n} {tasks} > {clog}.summary 2>&1', timeout=3000)
        src_log = glob.glob(f'/private/tmp/claude-501/*/*/scratchpad/g47/compile-{a.rule}-{n}.log')
        clog = src_log[0] if src_log else clog
        errs = compile_errors(clog)
        unedited = [e for e in errs if not fr.get(e[0])]
        if errs and len(unedited) == len(errs):
            # errors only in files no edit touched: zinc's incremental compile can leave a companion half-known
            # ("object X does not take parameters"); a clean build of those projects decides
            projs = sorted({project_of(e[0]) for e in unedited if project_of(e[0])})
            print(f'  {len(errs)} errors in unedited files only: clean build of {projs}', flush=True)
            sh(f'{a.compile} {a.rule}-{n}-clean ' + ' '.join(f'{p}/clean {p}/compile {p}/Test/compile' for p in projs) + f' > {os.path.join(work, f"compile-{n}.log")}.summary 2>&1', timeout=3000)
            src_log = glob.glob(f'/private/tmp/claude-501/*/*/scratchpad/g47/compile-{a.rule}-{n}-clean.log')
            clog = src_log[0] if src_log else clog
            errs = compile_errors(clog)
        rcline = open(f'{os.path.join(work, f"compile-{n}.log")}.summary').read()
        if not errs and 'rc=0' not in rcline:
            # backend crashes name a class only: back off the edits inside that object
            emit = re.findall(r'Error while emitting (\S+)', re.sub(r'\x1b\[[0-9;]*m', '', open(clog, errors='ignore').read()))
            keys = []
            for cls in dict.fromkeys(emit):
                names = [x for x in cls.split('/')[-1].split('$') if x]
                for fpath, rs in fr.items():
                    if os.path.basename(fpath) != names[0] + '.scala': continue
                    lines = open(fpath, encoding='utf-8').read().split('\n')
                    ls = line_starts(fpath)
                    start, ind = 0, -1
                    for nm in names[1:]:
                        for i in range(start, len(lines)):
                            if re.search(r'\b(object|class|trait|enum)\s+' + re.escape(nm) + r'\b', lines[i]):
                                start, ind = i, len(lines[i]) - len(lines[i].lstrip()); break
                    end = len(lines)
                    if ind >= 0:
                        for i in range(start + 1, len(lines)):
                            l = lines[i]
                            if l.strip() and len(l) - len(l.lstrip()) <= ind: end = i; break
                    lo, hi = ls[start], ls[end] if end < len(ls) else 10**9
                    keys += [r[6] for r in rs if lo <= int(r[4]) < hi]
            new = exclude(keys, f'backend crash (round {n})')
            print(f'  backend crash in {list(dict.fromkeys(emit))[:6]} -> {len(new)} new exclusions', flush=True)
            if new: continue
            print('  compile failed without a located error:\n' + rcline[:2000], flush=True); return 7
        if errs:
            keys, unmapped = [], []
            pending = {}  # file -> errors not at an edit nor on an edited line
            for (p, line, col) in errs:
                if not os.path.exists(p): unmapped.append((p, line, col)); continue
                ls = line_starts(p)
                off = ls[line - 1] + col - 1 if line - 1 < len(ls) else 0
                k = keys_at(fr, p, off)
                if not k:
                    rs = [r for r in fr.get(p, []) if ls[line - 1] <= int(r[4]) < (ls[line] if line < len(ls) else 10**9)]
                    k = [r[6] for r in rs]
                if not k: pending.setdefault(p, []).append((line, col))
                keys += k
            mapped_files = {k.split('@')[0] for k in keys}
            for p, where in pending.items():
                # cascades in a file where other errors were mapped wait for the next round; otherwise the package clause
                # or the added imports are the likely cause
                if p in mapped_files: continue
                fk = fallback_keys(fr, p) if fr.get(p) else []
                if not fk: unmapped += [(p, l, c) for (l, c) in where]
                keys += fk
            if a.rule == 'g46' and unmapped:
                # an export changes typing order (a lost case-class apply, a cycle) where no edit is: back off the exports
                # whose owner path names an identifier of the failing line
                for (p, line, col) in unmapped:
                    try: src = open(p, encoding='utf-8').read().split('\n')[line - 1]
                    except Exception: continue
                    bare = set(re.findall(r'(?<![.\w])[A-Z][A-Za-z0-9_]*\b', src))
                    for r in rows:
                        owner = r[7].split(' ', 1)[1].split('.{')[0]
                        segs = owner.split('.')
                        if segs[-1][:1].islower(): continue  # a package owner: nothing in a typing order
                        if segs[-1] in bare or re.search(r'\b' + re.escape('.'.join(segs[-2:])) + r'\b', src): keys.append(r[6])
            if a.rule == 'g46' and unmapped:
                # "object N in package P does not take parameters": an export elsewhere in P changed the typing order
                text = re.sub(r'\x1b\[[0-9;]*m', '', open(clog, errors='ignore').read())
                text = re.sub(r'\n\[error\]\s+\|\s*', ' ', text)
                for pkg in set(re.findall(r'object \w+ in\s+package ([\w.]+) does not take parameters', text)):
                    d = '/' + pkg.replace('.', '/') + '/'
                    for fpath, rs in fr.items():
                        if d in fpath and '/' not in fpath.split(d, 1)[1]: keys += [r[6] for r in rs]
            new = exclude(keys, f'compile error (round {n})')
            print(f'  {len(errs)} compile errors -> {len(new)} new exclusions; unmapped: {unmapped[:10]}', flush=True)
            if not new:
                print('  no progress: fix by hand', flush=True); return 2
            continue
        out = os.path.join(work, f'oracle-{n}.tsv')
        rc = sh(f'scala-cli run --server=false -q tools/refactor/SymbolOracle.scala -- compare {snap} {ledger} {out}', log=out + '.log')
        print('  ' + open(out + '.log').read().strip()[:3000].replace('\n', '\n  '), flush=True)
        if rc != 0: print('oracle failed'); return 6
        bad = [l.rstrip('\n').split('\t') for l in open(out)] if os.path.exists(out) else []
        stale = [b for b in bad if b[1] == 'stale']
        changed = [b for b in bad if b[1] == 'changed']
        if stale: print(f'  stale: {stale[:5]}', flush=True)
        if not changed and not stale:
            print(f'done: {len(rows)} edits', flush=True); return 0
        keys = []
        for b in changed:
            p, off, offb = b[0], int(b[2]), int(b[6])
            k = keys_at(fr, p, off) if off >= 0 else []
            if not k and offb >= 0:
                rs = [r for r in fr.get(p, []) if int(r[2]) <= offb]
                if rs: k = [max(rs, key=lambda r: int(r[2]))[6]]
            keys += k or fallback_keys(fr, p)
        new = exclude(keys, f'symbol changed (round {n})')
        print(f'  {len(changed)} changed occurrences -> {len(new)} new exclusions: {changed[:3]}', flush=True)
        if not new and not stale:
            print('  no progress: fix by hand', flush=True); return 2
        if stale and not new: return 3
    return 4

sys.exit(main())
