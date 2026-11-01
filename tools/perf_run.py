"""perf_run.py JAR OUTDIR RESULT.json [LIST]: run the programs compiled by
tools/perf_compile.sh one after the other (never in parallel: parallel JVMs
distort the times badly) and write {name: [seconds, exit code]}.  LIST is a
file with one .a68 path per line (default: all of tests/rosetta that have a
class).  The times include the start of the JVM (about 0.06 to 0.1 s)."""
import json,os,subprocess,sys,time,glob
J=sys.argv[1]; O=sys.argv[2]; res={}
env=dict(os.environ); env.pop('JAVA_TOOL_OPTIONS',None); env['LC_ALL']='C'
R=os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'tests', 'rosetta')
# RCMOD: a directory with the module Rcprelude, only for programs compiled
# before the Rosetta tests used the bundled module Genie_Transput.
RCMOD=os.environ.get('RCMOD', '')
CP=J + (':' + RCMOD if RCMOD else '')
import glob
LIST=sys.argv[4] if len(sys.argv) > 4 else None
files=open(LIST).read().split() if LIST else sorted(glob.glob(os.path.join(R, '*.a68')))
for f in files:
    n=os.path.basename(f)[:-4]; d=f'{O}/c.{n}'
    if not os.path.isdir(d): continue
    cls=[c[:-6] for c in os.listdir(d) if c.endswith('.class')]
    if not cls: continue
    t0=time.monotonic()
    try:
        p=subprocess.run(['java','-XX:+UseSerialGC','-cp',f'{CP}:{d}',cls[0]],capture_output=True,stdin=subprocess.DEVNULL,cwd=R,env=env,timeout=60)
        rc=p.returncode
    except subprocess.TimeoutExpired: rc='timeout'
    res[n]=(round(time.monotonic()-t0,3),rc)
json.dump(res,open(sys.argv[3],'w'))
