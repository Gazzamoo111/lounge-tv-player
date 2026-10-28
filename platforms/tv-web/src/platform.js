// Platform APIs are injected so their lifecycles can be tested without a television.
export function detectPlatform(env=globalThis){return env.webapis?.avplay?'tizen':env.webOS||env.PalmSystem?'webos':'browser';}
export class NativePlayer {
 constructor({video,env=globalThis,onState=()=>{}}){this.video=video;this.env=env;this.onState=onState;this.platform=detectPlatform(env);this.generation=0;this.playing=false;}
 async load(url,{positionMs=0}={}){
  if(!/^https?:\/\//i.test(url))throw Error('INVALID_MEDIA_URL');
  this.stop();const generation=this.generation;this.onState('loading');
  if(this.platform==='tizen'){
   const p=this.env.webapis.avplay;
   try{p.open(url);p.setDisplayRect(0,0,1920,1080);p.setDisplayMethod('PLAYER_DISPLAY_MODE_LETTER_BOX');
    await new Promise((resolve,reject)=>{p.setListener({onerror:()=>{if(generation===this.generation)this.onState('error');},onstreamcompleted:()=>{if(generation===this.generation)this.onState('ended');}});p.prepareAsync(()=>{if(generation!==this.generation){resolve();return;}try{if(positionMs)p.seekTo(positionMs);p.play();this.playing=true;this.onState('playing');resolve();}catch{reject(Error('PLAYBACK_START_FAILED'));}},()=>reject(Error('PLAYBACK_PREPARE_FAILED')));});
   }catch{if(generation===this.generation)this.onState('error');throw Error('PLAYBACK_FAILED');}
  }else{
   this.video.src=url;this.video.preload='metadata';
   if(positionMs)this.video.addEventListener('loadedmetadata',()=>{if(generation===this.generation)this.video.currentTime=positionMs/1000;},{once:true});
   try{await this.video.play();if(generation===this.generation){this.playing=true;this.onState('playing');}}catch{if(generation===this.generation)this.onState('error');throw Error('PLAYBACK_FAILED');}
  }
 }
 stop(){this.generation++;this.playing=false;if(this.platform==='tizen'){const p=this.env.webapis.avplay;try{const s=p.getState();if(['PLAYING','PAUSED','READY'].includes(s))p.stop();p.close();}catch{/* no active session */}}else{this.video.pause();this.video.removeAttribute('src');this.video.load();}}
 pause(){if(this.platform==='tizen')this.env.webapis.avplay.pause();else this.video.pause();this.playing=false;}
 async resume(){if(this.platform==='tizen')this.env.webapis.avplay.play();else await this.video.play();this.playing=true;this.onState('playing');}
 position(){return this.platform==='tizen'?this.env.webapis.avplay.getCurrentTime():Math.round((this.video.currentTime||0)*1000);}
 seek(ms){if(this.platform==='tizen')this.env.webapis.avplay.seekTo(Math.max(0,ms));else this.video.currentTime=Math.max(0,ms)/1000;}
 tracks(){return this.platform==='tizen'?this.env.webapis.avplay.getTotalTrackInfo():{audio:this.video.audioTracks??[],subtitles:this.video.textTracks??[]};}
 duration(){return this.platform==='tizen'?this.env.webapis.avplay.getDuration():Math.round((Number.isFinite(this.video.duration)?this.video.duration:0)*1000);}
 selectTrack(type,index){if(this.platform==='tizen'){this.env.webapis.avplay.setSelectTrack(type,index);return;}const tracks=type==='AUDIO'?this.video.audioTracks:this.video.textTracks;if(!tracks)throw Error('TRACK_SELECTION_UNAVAILABLE');for(let i=0;i<tracks.length;i++){if(type==='AUDIO')tracks[i].enabled=i===index;else tracks[i].mode=i===index?'showing':'disabled';}}
 aspect(mode){if(this.platform==='tizen')this.env.webapis.avplay.setDisplayMethod({fit:'PLAYER_DISPLAY_MODE_LETTER_BOX',fill:'PLAYER_DISPLAY_MODE_FULL_SCREEN',stretch:'PLAYER_DISPLAY_MODE_AUTO_ASPECT_RATIO'}[mode]??'PLAYER_DISPLAY_MODE_LETTER_BOX');else this.video.style.objectFit=mode==='fill'?'cover':mode==='stretch'?'fill':'contain';}
}
export function registerRemoteKeys(env=globalThis){if(!env.tizen?.tvinputdevice)return;for(const key of ['MediaPlay','MediaPause','MediaPlayPause','MediaStop','MediaFastForward','MediaRewind','ChannelUp','ChannelDown','ColorF0Red','ColorF1Green','ColorF2Yellow','ColorF3Blue']){try{env.tizen.tvinputdevice.registerKey(key);}catch{/* Unsupported key, keep basic D-pad available. */}}}
