#!/usr/bin/env python3
"""Smoke-test an isolated server's explicitly enabled console HTTP mode; never prints its token."""
import argparse, hashlib, json, pathlib, re, subprocess, time, urllib.request, urllib.error
parser=argparse.ArgumentParser()
parser.add_argument('plugin_dir',type=pathlib.Path)
parser.add_argument('--artifact',type=pathlib.Path,required=True)
parser.add_argument('--target',choices=['legacy','modern'],required=True)
parser.add_argument('--paper-build',type=int,required=True)
parser.add_argument('--output',type=pathlib.Path,required=True)
args=parser.parse_args()
config=(args.plugin_dir/'config.yml').read_text()
match=re.search(r'^token:\s*[\'\"]?([A-Za-z0-9_-]+)[\'\"]?\s*$',config,re.M)
assert match, 'Requires an isolated config with a generated token'
token=match.group(1)
base=json.loads((args.plugin_dir/'commandapi-address.json').read_text())['url']
def call(path,body=None,authenticated=True):
    headers={'Content-Type':'application/json'}
    if authenticated: headers['Authorization']='Bearer '+token
    request=urllib.request.Request(base+path,data=json.dumps(body).encode() if body is not None else None,headers=headers)
    try:
        with urllib.request.urlopen(request,timeout=40) as response:return response.status,json.load(response)
    except urllib.error.HTTPError as error:return error.code,json.load(error)
status,info=call('/api/status');assert status==200 and info['execution_mode']=='server-native-console',info
status,_=call('/api/chat',{'text':'/time set day'},False);assert status==401,status
status,_=call('/api/chat',{'text':'/op forbidden'});assert status==403,status
status,result=call('/api/chat',{'text':'/time set day'});assert status==200 and result['result']['execution_status']=='dispatched',result
status,job=call('/api/jobs',{'messages':['/time set day','/weather clear']});assert status==202,job
for attempt in range(100):
    status,job=call('/api/jobs/'+job['id'])
    if job['state'] in ('completed','failed','cancelled'):break
    time.sleep(.1)
assert job['state']=='completed' and job['processed']==2,job
record={'target':args.target,'server_api':info['minecraft_version'],'paper_build':args.paper_build,
        'code_commit':subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),
        'artifact':args.artifact.name,'artifact_sha256':hashlib.sha256(args.artifact.read_bytes()).hexdigest(),
        'checks':['standalone plugin enabled','authenticated console HTTP status','unauthenticated command rejected',
                  'command policy denied forbidden root','native dispatch','ordered async completion'],
        'result':result,'job':job,'recorded_utc':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime())}
assert token not in json.dumps(record)
args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(record,indent=2)+'\n')
print(json.dumps({'target':args.target,'server_api':record['server_api'],'checks':len(record['checks'])}))
