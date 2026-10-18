#!/usr/bin/env python3
from pathlib import Path
import base64, hashlib, json, math, os, select, signal, statistics, subprocess, sys, tarfile
from xml.sax.saxutils import quoteattr
root=Path(sys.argv[1]).resolve();out=Path(sys.argv[2]).resolve();out.mkdir(parents=True,exist_ok=False)
java=Path('/home/henry/.sdkman/candidates/java/21.0.8-zulu/bin')
base='0734fb23db6873f4ec64c3e3e29fc8c3e17edc7a'
env=dict(os.environ,JAVA_HOME=str(java.parent),JAVA_TOOL_OPTIONS='-Djava.awt.headless=true')
env['PATH']=str(java)+os.pathsep+env['PATH']
comparison=json.loads(Path('/tmp/graph-initial-spacing-evidence/task-1/comparison/comparison.json').read_text())
assert comparison['status']=='PASS','frozen numerical gate must pass before UI work'
mutations=json.loads(Path('/tmp/graph-initial-spacing-evidence/task-3/session-2/mutations-final/mutations.json').read_text())
assert sum(c.get('killed',False) for c in mutations['commands'])==7,'mutation gate'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def command(argv,cwd,log,environment=env,timeout=240):
    with log.open('w') as stream:
        stream.write(json.dumps(dict(argv=[str(x) for x in argv],cwd=str(cwd)))+'\n');stream.flush()
        subprocess.run([str(x) for x in argv],cwd=cwd,env=environment,stdout=stream,stderr=subprocess.STDOUT,check=True,timeout=timeout)
old=out/'baseline';old.mkdir()
archive=out/'baseline.tar'
with archive.open('wb') as f:subprocess.run(['git','-C',str(root),'archive',base],stdout=f,check=True)
with tarfile.open(archive) as f:f.extractall(old,filter='data')
classes=out/'agent-classes';classes.mkdir();source=root/'freeplane_plugin_graph/src/test/evidence/initial-spacing/SpacingUiAgent.java'
command([java/'javac','--release','8','-d',classes,source],root,out/'agent-compile.log')
manifest=out/'agent.mf';manifest.write_text('Manifest-Version: 1.0\nPremain-Class: SpacingUiAgent\n\n')
agent=out/'spacing-ui-agent.jar';command([java/'jar','cfm',agent,manifest,'-C',classes,'.'],root,out/'agent-jar.log')
fixtures=out/'fixtures';fixtures.mkdir();inputs={};MAP_A='00000000-0000-0000-0000-000000000001';MAP_B='00000000-0000-0000-0000-000000000002'
def group_node(node_id):return '<node ID='+quoteattr(node_id)+' TEXT='+quoteattr(node_id)+'><graph_group version="1"/></node>'
def linked_group(node_id,target):return '<node ID='+quoteattr(node_id)+' TEXT='+quoteattr(node_id)+'><graph_group version="1"/><arrowlink DESTINATION='+quoteattr(target)+' STARTARROW="None" ENDARROW="Default"/></node>'
def map_xml(root_id,children):return '<map version="freeplane 1.12.1"><node ID='+quoteattr(root_id)+' TEXT='+quoteattr(root_id)+'>'+children+'</node></map>'
def write_workspace(case,workspace_id,map_a,map_b,relationship=False,pins=()):
    rel=('<relationship id="00000000-0000-0000-0000-000000016071" sequence="1" source-map="'+MAP_A+'" source-node="L0" target-map="'+MAP_B+'" target-node="B0" direction="FORWARD"/>') if relationship else ''
    pin_xml=''.join('<pin map="'+m+'" node="'+n+'" x="'+str(x)+'" y="'+str(y)+'"/>' for n,m,x,y in pins)
    maps='<map id="'+MAP_A+'" sequence="1" uri="'+map_a.as_uri()+'" active="true" color="#4E79A7"/><map id="'+MAP_B+'" sequence="2" uri="'+map_b.as_uri()+'" active="true" color="#F28E2B"/>'
    fpg=fixtures/(case+'.fpg');fpg.write_text('<graph-workspace format-version="1" id="'+workspace_id+'"><maps>'+maps+'</maps><relationships>'+rel+'</relationships><pins>'+pin_xml+'</pins><viewport center-x="0.0" center-y="0.0" zoom="1.0"/><display-settings show-arrowheads="false" canvas-theme="LIGHT" remember-viewport="true" dim-unrelated-nodes="false"/></graph-workspace>')
    return fpg
cases={}
for case,workspace_id in [('nr8','00000000-0000-0000-0000-000000001601'),('sm8','00000000-0000-0000-0000-000000001602')]:
    a=fixtures/(case+'-A.mm');b=fixtures/(case+'-B-unavailable.mm')
    if case=='nr8':children=''.join(group_node('A%d'%i) for i in range(8))
    else:children=''.join(linked_group('A%d'%i,'A%d'%((i+1)%8)) for i in range(8))
    a.write_text(map_xml('A_root',children));fpg=write_workspace(case,workspace_id,a,b,relationship=False)
    inputs[case]=dict(workspaceId=workspace_id,mapAPath=str(a),mapASha256=sha(a),mapBUnavailablePath=str(b),workspacePath=str(fpg),workspaceSha256=sha(fpg),activeRegistrations=2,projectedMaps=[MAP_A])
case='nested';workspace_id='00000000-0000-0000-0000-000000001607';a=fixtures/'nested-A.mm';b=fixtures/'nested-B.mm'
left='<node ID="A_left" TEXT="A_left">'+group_node('L0')+group_node('L1')+'</node>'
right='<node ID="A_right" TEXT="A_right">'+group_node('R0')+group_node('R1')+'</node>'
a.write_text(map_xml('A_root',left+right));b.write_text(map_xml('B_root',''.join(group_node('B%d'%i) for i in range(4))))
fpg=write_workspace(case,workspace_id,a,b,relationship=True)
inputs[case]=dict(workspaceId=workspace_id,mapAPath=str(a),mapASha256=sha(a),mapBPath=str(b),mapBSha256=sha(b),workspacePath=str(fpg),workspaceSha256=sha(fpg),activeRegistrations=2,projectedMaps=[MAP_A,MAP_B],relationship=['L0',MAP_A,'B0',MAP_B],pins=[])
case='pins';workspace_id='00000000-0000-0000-0000-000000001608';a=fixtures/'pins-A.mm';b=fixtures/'pins-B.mm'
a.write_text(map_xml('A_root',left+right));b.write_text(map_xml('B_root',''.join(group_node('B%d'%i) for i in range(4))))
pins=[('L0',MAP_A,120.0,-40.0)];fpg=write_workspace(case,workspace_id,a,b,relationship=True,pins=pins)
inputs[case]=dict(workspaceId=workspace_id,mapAPath=str(a),mapASha256=sha(a),mapBPath=str(b),mapBSha256=sha(b),workspacePath=str(fpg),workspaceSha256=sha(fpg),activeRegistrations=2,projectedMaps=[MAP_A,MAP_B],relationship=['L0',MAP_A,'B0',MAP_B],pins=[['L0',MAP_A,120.0,-40.0]])
case='pin-conflict';workspace_id='00000000-0000-0000-0000-000000001609';a=fixtures/'pin-conflict-A.mm';b=fixtures/'pin-conflict-B-unavailable.mm'
a.write_text(map_xml('A_root',''.join(linked_group('n%d'%i,'n%d'%((i+1)%8)) for i in range(8))))
pins=[('n0',MAP_A,0.0,0.0),('n1',MAP_A,20.0,0.0)];fpg=write_workspace(case,workspace_id,a,b,pins=pins)
inputs[case]=dict(workspaceId=workspace_id,mapAPath=str(a),mapASha256=sha(a),mapBUnavailablePath=str(b),workspacePath=str(fpg),workspaceSha256=sha(fpg),activeRegistrations=2,projectedMaps=[MAP_A],pins=[['n0',MAP_A,0.0,0.0],['n1',MAP_A,20.0,0.0]])
manifest_path=fixtures/'input-manifest.json';manifest_path.write_text(json.dumps(inputs,sort_keys=True,indent=2)+'\n')
expected={
 'nr8':dict(nodes=[(MAP_A,'A%d'%i) for i in range(8)],enclosures=[(MAP_A,'A_root','A_root','true','EMPHATIC')],direct={(MAP_A,'A_root'):[(MAP_A,'A%d'%i) for i in range(8)]},edges=[]),
 'sm8':dict(nodes=[(MAP_A,'A%d'%i) for i in range(8)],enclosures=[(MAP_A,'A_root','A_root','true','EMPHATIC')],direct={(MAP_A,'A_root'):[(MAP_A,'A%d'%i) for i in range(8)]},edges=[('NATIVE',MAP_A,'A%d'%i,MAP_A,'A%d'%((i+1)%8),'false','true') for i in range(8)]),
 'nested':dict(nodes=[(MAP_A,x) for x in ['L0','L1','R0','R1']]+[(MAP_B,'B%d'%i) for i in range(4)],enclosures=[(MAP_A,'A_root','A_root','true','EMPHATIC'),(MAP_A,'A_left','A_left','false','SUBTLE'),(MAP_A,'A_right','A_right','false','SUBTLE'),(MAP_B,'B_root','B_root','true','EMPHATIC')],direct={(MAP_A,'A_root'):[],(MAP_A,'A_left'):[(MAP_A,'L0'),(MAP_A,'L1')],(MAP_A,'A_right'):[(MAP_A,'R0'),(MAP_A,'R1')],(MAP_B,'B_root'):[(MAP_B,'B%d'%i) for i in range(4)]},edges=[('WORKSPACE',MAP_A,'L0',MAP_B,'B0','false','true')]),
 'pins':dict(nodes=[(MAP_A,x) for x in ['L0','L1','R0','R1']]+[(MAP_B,'B%d'%i) for i in range(4)],enclosures=[(MAP_A,'A_root','A_root','true','EMPHATIC'),(MAP_A,'A_left','A_left','false','SUBTLE'),(MAP_A,'A_right','A_right','false','SUBTLE'),(MAP_B,'B_root','B_root','true','EMPHATIC')],direct={(MAP_A,'A_root'):[],(MAP_A,'A_left'):[(MAP_A,'L0'),(MAP_A,'L1')],(MAP_A,'A_right'):[(MAP_A,'R0'),(MAP_A,'R1')],(MAP_B,'B_root'):[(MAP_B,'B%d'%i) for i in range(4)]},edges=[('WORKSPACE',MAP_A,'L0',MAP_B,'B0','false','true')]),
 'pin-conflict':dict(nodes=[(MAP_A,'n%d'%i) for i in range(8)],enclosures=[(MAP_A,'A_root','A_root','true','EMPHATIC')],direct={(MAP_A,'A_root'):[(MAP_A,'n%d'%i) for i in range(8)]},edges=[('NATIVE',MAP_A,'n%d'%i,MAP_A,'n%d'%((i+1)%8),'false','true') for i in range(8)])}
receipt=dict(status='RUNNING',baseline=base,candidateHead=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),candidatePatchSha256=hashlib.sha256(subprocess.check_output(['git','diff',base,'--binary'],cwd=root)+source.read_bytes()+Path(__file__).read_bytes()).hexdigest(),agentSha256=sha(agent),inputManifestSha256=sha(manifest_path),inputs=inputs,runs={})
rfd,wfd=os.pipe();xlog=(out/'xvfb.log').open('w');xvfb=subprocess.Popen(['Xvfb','-displayfd',str(wfd),'-screen','0','1440x1000x24','-nolisten','tcp','-ac'],pass_fds=(wfd,),stdout=xlog,stderr=subprocess.STDOUT);os.close(wfd)
def projection_rows(rows,seq):
    selected=[]
    for r in rows:
        if r[0] in ('PROJECTION','NODE_META','ENCLOSURE','DIRECT','EDGE','PIN_SPEC') and int(r[1])==seq:
            selected.append(['PROJECTION',*r[3:]] if r[0]=='PROJECTION' else [r[0],*r[2:]])
    return selected
def verify_manifest(rows,seq,case):
    spec=expected[case];node_rows=[r for r in rows if r[0]=='NODE_META' and int(r[1])==seq]
    actual_nodes=[(r[2],r[3]) for r in node_rows]
    assert actual_nodes==spec['nodes'],(case,'node order/identity',actual_nodes)
    for r in node_rows:
        assert base64.b64decode(r[4]).decode()==r[3] and base64.b64decode(r[5]).decode()==r[3]
        assert r[6]=='true' and float(r[7])==1.0,(case,'label/group/prominence',r)
    enc_rows=[r for r in rows if r[0]=='ENCLOSURE' and int(r[1])==seq]
    actual_enc=[(r[2],r[3],base64.b64decode(r[4]).decode(),r[5],r[6]) for r in enc_rows]
    assert actual_enc==spec['enclosures'],(case,'enclosures',actual_enc)
    direct={key:[] for key in spec['direct']}
    for r in rows:
        if r[0]=='DIRECT' and int(r[1])==seq:direct.setdefault((r[2],r[3]),[]).append((r[4],r[5]))
    assert direct==spec['direct'],(case,'direct membership',direct)
    edge_rows=[r for r in rows if r[0]=='EDGE' and int(r[1])==seq]
    actual_edges=sorted(tuple(r[2:9]) for r in edge_rows);assert actual_edges==sorted(spec['edges']),(case,'edges',actual_edges)
    expected_pins=inputs[case].get('pins',[])
    actual_pins=[(r[2],r[3],r[4],float(r[5]),float(r[6])) for r in rows if r[0]=='PIN_SPEC' and int(r[1])==seq]
    assert actual_pins==[(m,n,'true',float(x),float(y)) for n,m,x,y in expected_pins],(case,'pin projection',actual_pins)
    p=next(r for r in rows if r[0]=='PROJECTION' and int(r[1])==seq)
    assert [int(x) for x in p[3:7]]==[8,len(spec['enclosures']),len(spec['edges']),len(expected_pins)]
    canonical=json.dumps(projection_rows(rows,seq),separators=(',',':'),ensure_ascii=False).encode()
    return hashlib.sha256(canonical).hexdigest()
def verify_pin_positions(rows,seq,case):
    expected_pins=inputs[case].get('pins',[])
    actual=[(r[2],r[3],float(r[4]),float(r[5])) for r in rows if r[0]=='PIN' and int(r[1])==seq]
    expected_values=[(m,n,float(x),float(y)) for n,m,x,y in expected_pins]
    assert actual==expected_values,(case,'pin coordinates changed',seq,actual,expected_values)
def nearest_gaps(rows,seq,case):
    positions={(r[2],r[3]):(float(r[4]),float(r[5]),float(r[6])) for r in rows if r[0]=='NODE' and int(r[1])==seq}
    assert len(positions)==8 and all(math.isfinite(v) for p in positions.values() for v in p)
    assert all(p[2]==8.0 for p in positions.values()),'rendered node radius changed'
    groups={key:list(value) for key,value in expected[case]['direct'].items()}
    gaps={}
    for group,keys in groups.items():
        if len(keys)<2:continue
        nearest=[]
        for key in keys:
            a=positions[key]
            vals=[math.hypot(a[0]-positions[k][0],a[1]-positions[k][1])-a[2]-positions[k][2] for k in keys if k!=key]
            assert vals and all(math.isfinite(v) for v in vals)
            nearest.append(min(vals))
        gaps[group[0]+'/'+group[1]]=dict(minimum=min(nearest),median=statistics.median(nearest),perNode=nearest)
    return positions,gaps
try:
    assert select.select([rfd],[],[],10)[0],'Xvfb display allocation deadline';display=':'+os.read(rfd,64).decode().strip();receipt['display']=display
    receipt['xvfbScreen']='1440x1000x24'
    for version,tree in [('baseline',old),('candidate',root)]:
        command(['gradle',':freeplane_plugin_graph:graphSmokeInstallation','--console=plain'],tree,out/(version+'-install.log'))
        if version=='baseline':
            command(['gradle',':freeplane_plugin_graph:graphSmokeInstallation','--console=plain'],tree,out/(version+'-install-steady.log'))
        plugin=tree/'BIN/plugins/org.freeplane.plugin.graph';receipt[version+'BundleHashes']={str(p.relative_to(tree)):sha(p) for p in sorted(plugin.rglob('*')) if p.is_file()};assert receipt[version+'BundleHashes']
        installedAgent=tree/'BIN/spacing-ui-agent.jar'
        installedAgent.parent.mkdir(parents=True,exist_ok=True)
        installedAgent.write_bytes(agent.read_bytes());receipt[version+'AgentPath']=str(installedAgent)
        for case in ['nr8','sm8','nested','pins','pin-conflict']:
            d=out/(version+'-'+case);d.mkdir();config=d/'config/freeplane/1.12.x';config.mkdir(parents=True);work=d/(case+'.fpg');work.write_bytes((fixtures/(case+'.fpg')).read_bytes())
            assert sha(work)==inputs[case]['workspaceSha256'];(d/'home').mkdir()
            props='create_new_map_if_no_maps_are_loaded=false\nalways_load_last_maps=false\nload_last_maps=false\nload_last_map=false\nui_elements_follow_selected_map=false\nselected_map_follows_active_window=false\ngraph_workspace_recent_workspaces='+str(work)+'\n'
            (config/'auto.properties').write_text(props)
            ui=dict(env,DISPLAY=display,HOME=str(d/'home'),XDG_CONFIG_HOME=str(d/'config'));ui.pop('JAVA_TOOL_OPTIONS',None);ui.pop('JDK_JAVA_OPTIONS',None)
            ui['JAVA_OPTS']='-Dorg.freeplane.nosplash=true -Duser.language=en -Duser.country=US -Duser.timezone=UTC -Dsun.java2d.uiScale=1.0 --add-opens=java.base/java.util=ALL-UNNAMED -Dawt.robot.screenshotMethod=x11 -javaagent:'+str(installedAgent)+'='+str(d)+','+str(work)+','+case
            log=(d/'launch.log').open('w');p=subprocess.Popen([str(tree/'BIN/freeplane.sh')],cwd=tree/'BIN',env=ui,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
            try:code=p.wait(timeout=260);assert code==0,(version,case,code);assert (d/'done.txt').read_text()=='PASS\n',(version,case,'incomplete agent trace')
            finally:
                if p.poll() is None:
                    os.killpg(p.pid,signal.SIGTERM)
                    try:p.wait(timeout=5)
                    except subprocess.TimeoutExpired:os.killpg(p.pid,signal.SIGKILL);p.wait(timeout=5)
                log.close()
            rows=[x.split('\t') for x in (d/'frames.tsv').read_text().splitlines()]
            state_by_seq={int(r[1]):r for r in rows if r[0]=='STATE'};caps=[r for r in rows if r[0]=='CAPTURE']
            diagnostic_name='pinned-diagnostic' if case=='pin-conflict' else 'first-idle'
            pinned=case in ('pins','pin-conflict')
            expected_caps=[('initial-publication','initial'),(diagnostic_name,'initial')]+([('initial-publication','reset')] if pinned else [])
            assert [(r[2],r[3]) for r in caps]==expected_caps,(version,case,'capture phases',[(r[2],r[3]) for r in caps])
            selected=[r for r in rows if r[0]=='SELECT'];assert [r[2] for r in selected[:2]]==['initial-publication',diagnostic_name]
            initial_seq=int(next(r for r in selected if r[2]=='initial-publication')[1]);diagnostic_seq=int(next(r for r in selected if r[2]==diagnostic_name)[1])
            initial_state=state_by_seq[initial_seq];diagnostic_state=state_by_seq[diagnostic_seq]
            verify_pin_positions(rows,initial_seq,case);verify_pin_positions(rows,diagnostic_seq,case)
            assert initial_state[6:8]==['false','true'] and int(initial_state[10])==8 and int(initial_state[18]) in (0, 1)
            assert diagnostic_state[6:8]==['false','true'] and int(diagnostic_state[10])==8
            if case=='pin-conflict':assert int(diagnostic_state[9])>0,'conflicting persisted pins must retain their measured nonzero residual'
            else:
                assert diagnostic_state[5]=='IDLE' and diagnostic_state[6:9]==['false','true','true']
                restart_floor=int(next(r for r in rows if r[0]=='RESTART_STATE')[1])
                same_run=[r for r in rows if r[0]=='STATE' and int(r[1])>=restart_floor and int(r[16])==int(initial_state[16]) and int(r[17])==int(initial_state[17]) and r[5]=='IDLE' and r[6:9]==['false','true','true'] and int(r[10])==8]
                assert same_run and int(same_run[0][1])==diagnostic_seq,'selected state is not exact first IDLE after witnessed healthy Restart'
            restart=next(r for r in rows if r[0]=='RESTART_STATE');assert int(restart[2])==int(initial_state[16]) and int(restart[3])==int(initial_state[17])
            initial_projection=verify_manifest(rows,initial_seq,case);diagnostic_projection=verify_manifest(rows,diagnostic_seq,case)
            assert initial_projection==diagnostic_projection
            assert int(next(r for r in caps if int(r[1])==initial_seq)[6])==int(initial_state[16])
            assert int(next(r for r in caps if int(r[1])==diagnostic_seq)[6])==int(initial_state[16])
            assert int(next(r for r in caps if int(r[1])==initial_seq)[7])==int(initial_state[17])
            assert int(next(r for r in caps if int(r[1])==diagnostic_seq)[7])==int(initial_state[17])
            restart_seq=int(restart[1]);verify_pin_positions(rows,restart_seq,case)
            if case in ('pins','pin-conflict'):
                reset_seq=int(caps[2][1]);reset_state=state_by_seq[reset_seq];reset_row=next(r for r in rows if r[0]=='RESET_STATE')
                assert int(reset_state[18])==0 and int(reset_state[16])!=int(initial_state[16]) and int(reset_state[17])!=int(initial_state[17])
                assert int(caps[2][6])==int(reset_state[16]) and int(caps[2][7])==int(reset_state[17])
                assert int(reset_row[2])==int(reset_state[16]) and int(reset_row[3])==int(reset_state[17]) and int(reset_row[4])==0
                verify_pin_positions(rows,reset_seq,case)
                reset_projection=verify_manifest(rows,reset_seq,case);assert reset_projection==initial_projection
            for state_row in (r for r in rows if r[0]=='STATE'):
                verify_pin_positions(rows,int(state_row[1]),case)
            capture_results=[]
            for cap in caps:
                seq=int(cap[1]);state=state_by_seq[seq];positions,gaps=nearest_gaps(rows,seq,case)
                assert float(cap[13])==0.0 and float(cap[14])==0.0 and float(cap[15])==1.0
                assert float(cap[18])==1.0 and float(cap[19])==1.0
                if cap[2]=='first-idle':assert cap[9]=='true' and state[5]=='IDLE'
                prefix='reset-' if cap[3]=='reset' else '';base_name=prefix+cap[2]
                capture_results.append(dict(phase=cap[2],run=cap[3],sequence=seq,generation=int(cap[4]),rawFrameIndex=int(cap[5]),workerIdentity=int(cap[6]),runToken=int(cap[7]),explicitStepCalls=int(cap[8]),idle=(cap[9]=='true'),rms=float(cap[10]),maximumMotion=float(cap[11]),stableFrames=int(cap[12]),viewport=[float(cap[i]) for i in [13,14,15]],canvasSize=[int(cap[16]),int(cap[17])],displayScale=[float(cap[18]),float(cap[19])],screenBounds=[int(cap[20]),int(cap[21])],frameIdentity=int(cap[22]),captureNanos=int(cap[23]),projectionManifestSha256=verify_manifest(rows,seq,case),gaps=gaps,screenshotSha256={kind:sha(d/(base_name+'-'+kind+'.png')) for kind in ['window','canvas']}))
            initial_publication=dict(frameIndex=int(initial_state[18]),acceptedObservationSet=[0,1],
                measuredBy='LayoutFrame.stepIndex() of the selected initial displayed worker publication',
                note='Engine frame index, not the submission-relative worker step-call count; the initial displayed publication may already include corrections')
            receipt['runs'][version+'-'+case]=dict(initialPublication=initial_publication,captures=capture_results,inputManifestSha256=sha(manifest_path),projectionManifestSha256=initial_projection,sourceRevision=base if version=='baseline' else receipt['candidateHead'],patchSha256=hashlib.sha256(b'' if version=='baseline' else subprocess.check_output(['git','diff',base,'--binary'],cwd=root)+source.read_bytes()+Path(__file__).read_bytes()).hexdigest())
    for case in ['nr8','sm8','nested','pins','pin-conflict']:
        before=receipt['runs']['baseline-'+case]['captures'];after=receipt['runs']['candidate-'+case]['captures'];assert [(x['phase'],x['run']) for x in before]==[(x['phase'],x['run']) for x in after]
        for a,b in zip(before,after):assert a['viewport']==b['viewport'] and a['canvasSize']==b['canvasSize'] and a['displayScale']==b['displayScale'] and a['viewport'][2]==1.0
        assert receipt['runs']['baseline-'+case]['projectionManifestSha256']==receipt['runs']['candidate-'+case]['projectionManifestSha256']
    assert 'diagnosticDeviations' not in receipt,'a PASS receipt must carry no deviation record'
    receipt['status']='PASS'
finally:
    os.close(rfd);xvfb.terminate()
    try:xvfb.wait(timeout=5)
    except subprocess.TimeoutExpired:xvfb.kill();xvfb.wait(timeout=5)
    xlog.close();(out/'ui-receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
assert receipt['status']=='PASS'
print('PASS: installed baseline/candidate applications; direct, connected, nested multi-map, required one-pin lifecycle and supplemental two-pin conflict captures match frozen live-projection manifests')
