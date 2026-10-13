#!/usr/bin/env python3
"""Exercise a running isolated client. Reads secrets locally; never prints or records them."""
import argparse, json, pathlib, urllib.request, urllib.error, time
p=argparse.ArgumentParser();p.add_argument('config_dir',type=pathlib.Path);p.add_argument('--level-file',type=pathlib.Path);p.add_argument('--output',type=pathlib.Path,required=True);args=p.parse_args()
config=json.loads((args.config_dir/'commandapi.json').read_text());address=json.loads((args.config_dir/'commandapi-address.json').read_text())['url']
def call(path,body=None,auth=True):
    data=json.dumps(body).encode() if body is not None else None
    headers={'Content-Type':'application/json'}
    if auth:headers['Authorization']='Bearer '+config['token']
    req=urllib.request.Request(address+path,data=data,headers=headers)
    try:
        with urllib.request.urlopen(req,timeout=40) as r:return r.status,json.load(r)
    except urllib.error.HTTPError as e:return e.code,json.load(e)
status,info=call('/api/status');assert status==200 and info['in_world'],info
status,_=call('/api/chat',{'text':'/time set day'},False);assert status==401,status
status,valid=call('/api/chat',{'text':'/time set day'});assert status==200 and valid['result']['execution_status']=='executed',valid
status,invalid=call('/api/chat',{'text':'/commandapi_nonexistent_command'});assert status==200 and invalid['result']['execution_status']=='failed',invalid
status,job=call('/api/jobs',{'messages':['/time set day','/weather clear']});assert status==202,job
for _ in range(100):
    status,job=call('/api/jobs/'+job['id'])
    if job['state'] in ('completed','failed','cancelled'):break
    time.sleep(.1)
assert job['state']=='completed' and job['processed']==2,job
result={'minecraft':info['minecraft_version'],'mode':info['execution_mode'],'unauthenticated_status':401,
        'native_valid':valid,'native_invalid':invalid,'async_job':job,'capabilities':info['capabilities'],
        'checks':['authenticated status','unauthenticated privileged rejection','confirmed native execution','invalid native command','async ordered completion'],
        'recorded_utc':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime())}
if args.level_file:
    import gzip, subprocess
    raw=gzip.decompress(args.level_file.read_bytes());name=b'allowCommands';tag=b'\x01'+len(name).to_bytes(2,'big')+name
    result['cheats_enabled']=bool(raw[raw.index(tag)+len(tag)])
    assert not result['cheats_enabled'], 'Runtime without-cheats check requires a cheats-disabled world'
    result['checks'].append('native execution with cheats disabled')
import subprocess
result['code_commit']=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({'minecraft':result['minecraft'],'mode':result['mode'],'checks':len(result['checks'])}))
