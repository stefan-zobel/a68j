"""perf_ab.py NAME...: compare compiled versions program by program,
alternating runs (5 each, the minimum counts), so that drift of the machine
load hits all alike.  Environment: A_JAR, A_DIR, B_JAR, B_DIR (directories
written by tools/perf_compile.sh), RCMOD (a directory with the module
Rcprelude, only for programs compiled before the Rosetta tests used the
bundled module Genie_Transput), RUNS (number of runs, default 5),
VARIANTS (the versions to compare, default "A,B"; for a third one set
VARIANTS=A,B,C and C_JAR, C_DIR).  STAT=median prints the median of the
runs instead of the minimum: some programs are bimodal (a run lands in a
fast or a slow state of the JIT), and then the minimum of a few runs
depends on luck (docs/optimizations.md, section 7).  LOG=FILE appends
all times of each program to FILE as a line of JSON."""
import os,subprocess,time,json,sys
env=dict(os.environ); env.pop('JAVA_TOOL_OPTIONS',None)
R=os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'tests', 'rosetta')
RCMOD=os.environ.get('RCMOD', '')
KEYS=os.environ.get('VARIANTS', 'A,B').split(',')
V={k: (os.environ[k + '_JAR'], os.environ[k + '_DIR']) for k in KEYS}
names=sys.argv[1:]
def run(jar,d,n):
    cls=[c[:-6] for c in os.listdir(d) if c.endswith('.class')][0]
    t0=time.monotonic(); subprocess.run(['java','-XX:+UseSerialGC','-cp',jar + (':' + RCMOD if RCMOD else '') + ':' + d,cls],capture_output=True,stdin=subprocess.DEVNULL,cwd=R,env=env); return time.monotonic()-t0
tot={k:0 for k in KEYS}
for n in names:
    times={k:[] for k in KEYS}
    for k in range(int(os.environ.get('RUNS', '5'))):
        for j in KEYS:
            times[j].append(run(V[j][0],f'{V[j][1]}/c.{n}',n))
    if os.environ.get('LOG'):
        with open(os.environ['LOG'],'a') as f:
            f.write(json.dumps({'name':n,'times':times})+'\n')
    if os.environ.get('STAT')=='median':
        best={j:sorted(t)[len(t)//2] for j,t in times.items()}
    else:
        best={j:min(t) for j,t in times.items()}
    for j in KEYS:
        tot[j]+=best[j]
    print(' '.join(f"{best[j]:.2f}" for j in KEYS) + ' ' + n, flush=True)
print("sum", {k:round(v,2) for k,v in tot.items()})
