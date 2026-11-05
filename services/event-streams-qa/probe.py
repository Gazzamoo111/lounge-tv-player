"""Serial single-frame probes using an explicitly configured QA account only."""
import datetime as dt
import hashlib
import json
import os
import pathlib
import subprocess
import time
import urllib.parse

OUT=pathlib.Path('outputs/event-streams-qa')

def probe(candidate,template):
    stream_id=str(candidate['providerItemId']);service=candidate['logicalServiceId']
    url=template.replace('{id}',stream_id)
    parsed=urllib.parse.urlsplit(url)
    if parsed.scheme not in ('https','http') or not parsed.hostname:raise ValueError('invalid QA stream origin')
    # One ffmpeg process, one connection, no HEAD request, no fallback burst. Never record URL/stderr.
    observed=dt.datetime.now(dt.timezone.utc).isoformat().replace('+00:00','Z')
    identity=hashlib.sha256((parsed.hostname+'|'+parsed.path+'|'+stream_id+'|'+service).encode()).hexdigest()
    observation={'providerItemId':stream_id,'serviceId':service,'observedAtUtc':observed,'streamFingerprint':identity,'status':'failed','decodedFrames':0,'httpAccessible':False,'engine':'ffmpeg-single-frame','contentVisuallyVerified':False}
    try:
        result=subprocess.run(['ffmpeg','-nostdin','-loglevel','error','-rw_timeout','5000000','-i',url,'-frames:v','1','-f','image2pipe','-vcodec','png','pipe:1'],stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=12)
        if result.returncode==0 and result.stdout.startswith(b'\x89PNG\r\n\x1a\n'):
            observation.update(status='verified',decodedFrames=1,httpAccessible=True)
        else:observation['reason']='no-decoded-frame'
    except subprocess.TimeoutExpired:observation['reason']='probe-timeout'
    except OSError:observation['reason']='decoder-unavailable'
    return observation

if __name__=='__main__':
    template=os.environ.get('QA_STREAM_URL_TEMPLATE','')
    if not template or '{id}' not in template:
        (OUT/'playback-status.json').write_text(json.dumps({'configured':False,'attempted':0,'reason':'Dedicated QA stream URL template/account is not configured. No customer credentials used.'}))
        (OUT/'health.json').write_text('{}')
        print('QA playback unavailable: no dedicated QA account configured; attempted=0')
    else:
        coverage=json.loads((OUT/'coverage.json').read_text())
        candidates={str(c['providerItemId']):c for case in coverage['cases'] for c in case['broadcastCandidates'] if case['event']['status']=='IN'}
        health={}
        for stream_id,candidate in list(candidates.items())[:10]:
            health[stream_id]=probe(candidate,template)
            time.sleep(1)
        (OUT/'health.json').write_text(json.dumps(health))
        (OUT/'playback-status.json').write_text(json.dumps({'configured':True,'attempted':len(health),'decoded':sum(h['status']=='verified' for h in health.values()),'concurrency':1}))
        print(json.dumps({'attempted':len(health),'decoded':sum(h['status']=='verified' for h in health.values()),'concurrency':1}))
