import {cpSync,readFileSync,writeFileSync,mkdirSync}from'node:fs';
import {resolve}from'node:path';
const root=resolve(import.meta.dirname,'../../..');
for(const target of ['webos','tizen']){
 const out=resolve(import.meta.dirname,'../targets',target);mkdirSync(out,{recursive:true});cpSync(resolve(import.meta.dirname,'../dist'),out,{recursive:true});cpSync(resolve(root,'LICENSE'),resolve(out,'LICENSE'));writeFileSync(resolve(out,'SOURCE.txt'),'GPLv3-modified Lounge TV platform port. Corresponding source: https://github.com/Gazzamoo111/lounge-tv-player/tree/feature/lg-samsung-lounge-parity-20261028/platforms/tv-web\nDevelopment only; not a customer release.\n');
 if(target==='webos')writeFileSync(resolve(out,'appinfo.json'),JSON.stringify({id:'nz.loungetv.parity',version:'0.2.1',vendor:'Lounge TV',type:'web',main:'index.html',title:'Lounge TV — platform dev',icon:'brand-mark.png',largeIcon:'brand-mark.png',bgColor:'#050A14',resolution:'1920x1080',disableBackHistoryAPI:true,requiredACG:[]},null,2));
 else{
  const index=resolve(out,'index.html');writeFileSync(index,readFileSync(index,'utf8').replace('<head>','<head><script src="$WEBAPIS/webapis/webapis.js"></script>'));
  writeFileSync(resolve(out,'config.xml'),`<?xml version="1.0" encoding="UTF-8"?>\n<widget xmlns="http://www.w3.org/ns/widgets" xmlns:tizen="http://tizen.org/ns/widgets" id="https://loungetv.nz/player" version="0.2.1" viewmodes="maximized"><tizen:application id="LoungeTV01.Player" package="LoungeTV01" required_version="5.5"/><content src="index.html"/><icon src="brand-mark.png"/><name>Lounge TV — platform dev</name><access origin="*" subdomains="true"/><tizen:privilege name="http://tizen.org/privilege/internet"/><tizen:privilege name="http://tizen.org/privilege/tv.inputdevice"/><tizen:profile name="tv-samsung"/><tizen:setting screen-orientation="landscape" context-menu="disable" background-support="disable" encryption="disable" install-location="auto" hwkey-event="enable"/></widget>`);
 }
 console.log(`Prepared ${target} development files; device packaging/signing still required.`);
}
