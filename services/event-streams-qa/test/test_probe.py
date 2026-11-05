import importlib.util
from pathlib import Path
import unittest
p=Path(__file__).resolve().parents[1]/'probe.py'
spec=importlib.util.spec_from_file_location('qa_probe',p);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
class SerialProbeTest(unittest.TestCase):
    def test_failed_primary_tries_backup_then_stops_after_success(self):
        calls=[]
        def probe(c,template):
            calls.append(c['providerItemId']);return {'status':'verified' if c['providerItemId']==2 else 'failed'}
        variants=[{'providerItemId':i,'logicalServiceId':'NZ:SKYSPORT1'} for i in [1,2,3]]
        case={'event':{'status':'IN'},'broadcastCandidates':[{'logicalServiceId':'NZ:SKYSPORT1','variants':variants}]}
        result=module.probe_candidates([case,case],'test-only',prober=probe,pause=lambda _:None)
        self.assertEqual(calls,[1,2]);self.assertEqual(result['2']['status'],'verified')
    def test_upcoming_events_do_not_open_streams_and_attempts_are_bounded(self):
        calls=[]
        def probe(c,template):calls.append(c['providerItemId']);return {'status':'failed'}
        variants=[{'providerItemId':i,'logicalServiceId':'NZ:SKYSPORT1'} for i in [1,2,3]]
        case={'event':{'status':'PRE'},'broadcastCandidates':[{'logicalServiceId':'NZ:SKYSPORT1','variants':variants}]}
        self.assertEqual(module.probe_candidates([case],'test-only',prober=probe,pause=lambda _:None),{})
        case['event']['status']='IN';module.probe_candidates([case],'test-only',max_attempts=2,prober=probe,pause=lambda _:None)
        self.assertEqual(calls,[1,2])
