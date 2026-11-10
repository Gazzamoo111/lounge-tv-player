import{normalizeItem,itemKey}from'./library.js';
const PREFIX='lounge.native.';
export class ProfileStore{
 constructor(storage){this.storage=storage;this.state={profiles:[{id:'default',name:'Everyone',kids:false}],active:'default',data:{}};try{const v=JSON.parse(storage.getItem(PREFIX+'profiles')??'null');if(v?.profiles?.length&&v.profiles.some(p=>p.id===v.active))this.state=v;}catch{}this.channel=new EventTarget();}
 save(){this.storage.setItem(PREFIX+'profiles',JSON.stringify(this.state));this.channel.dispatchEvent(new Event('change'));}
 profile(){return this.state.profiles.find(p=>p.id===this.state.active);}
 select(id){if(!this.state.profiles.some(p=>p.id===id))throw Error('PROFILE_NOT_FOUND');this.state.active=id;this.save();}
 add(name,kids=false){if(!name.trim()||name.length>40||this.state.profiles.length>=8)throw Error('INVALID_PROFILE');const id=`p-${Date.now()}-${Math.random().toString(36).slice(2,8)}`;this.state.profiles.push({id,name:name.trim(),kids});this.save();return id;}
 data(){return this.state.data[this.state.active]??(this.state.data[this.state.active]={favorites:[],history:[],positions:{},hiddenCategories:[],sports:{teams:[],leagues:[],reminders:[],spoilerFree:false}});}
 toggleFavorite(item){const d=this.data(),key=itemKey(item),index=d.favorites.findIndex(i=>itemKey(i)===key);if(index<0)d.favorites.push(normalizeItem(item.kind,item.raw));else d.favorites.splice(index,1);this.save();}
 played(item,position=0){const d=this.data(),key=itemKey(item);d.history=[normalizeItem(item.kind,item.raw),...d.history.filter(i=>itemKey(i)!==key)].slice(0,100);if(item.kind!=='live')d.positions[key]=Math.max(0,Number(position)||0);this.save();}
 hideCategory(kind,id){const d=this.data(),key=`${kind}:${id}`;if(!d.hiddenCategories.includes(key))d.hiddenCategories.push(key);this.save();}
 restoreCategories(){this.data().hiddenCategories=[];this.save();}
 clearHistory(){this.data().history=[];this.data().positions={};this.save();}
 export(){// Deliberately excludes all source credentials, network addresses and provider URLs.
  const profiles=this.state.profiles.map(p=>({...p}));const data={};for(const p of profiles){const d=this.state.data[p.id];if(!d)continue;const clean=item=>({kind:item.kind,raw:{id:item.raw.id,stream_id:item.raw.stream_id,series_id:item.raw.series_id,name:item.raw.name,container_extension:item.raw.container_extension}});data[p.id]={favorites:d.favorites.map(clean),history:d.history.map(clean),positions:d.positions,hiddenCategories:d.hiddenCategories,sports:d.sports};}return {format:'lounge-native-profile-backup',version:1,profiles,active:this.state.active,data};
 }
 import(raw){if(raw?.format!=='lounge-native-profile-backup'||raw.version!==1||!Array.isArray(raw.profiles)||raw.profiles.length<1||raw.profiles.length>8)throw Error('INVALID_BACKUP');const safe=JSON.parse(JSON.stringify(raw));if(safe.profiles.some(p=>typeof p.id!=='string'||typeof p.name!=='string'||p.name.length>40)||!safe.profiles.some(p=>p.id===safe.active))throw Error('INVALID_BACKUP');for(const d of Object.values(safe.data??{})){if(!Array.isArray(d.favorites)||!Array.isArray(d.history)||d.favorites.length>10000||d.history.length>1000)throw Error('INVALID_BACKUP');for(const i of [...d.favorites,...d.history]){if(!['live','movies','series'].includes(i.kind)||!i.raw||typeof i.raw.name!=='string')throw Error('INVALID_BACKUP');i.raw=normalizeItem(i.kind,i.raw).raw;delete i.raw.stream_icon;delete i.raw.cover;}d.positions=d.positions??{};d.hiddenCategories=d.hiddenCategories??[];d.sports=d.sports??{teams:[],leagues:[],reminders:[]};}this.state={profiles:safe.profiles,active:safe.active,data:safe.data??{}};this.save();}
}
export class SourceVault{
 constructor({indexedDB=globalThis.indexedDB,crypto=globalThis.crypto}={}){this.idb=indexedDB;this.crypto=crypto;this.db=null;}
 async open(){if(this.db)return this.db;if(!this.idb||!this.crypto?.subtle)throw Error('ENCRYPTED_STORAGE_UNAVAILABLE');this.db=await new Promise((resolve,reject)=>{const r=this.idb.open('lounge-native-sources',1);r.onupgradeneeded=()=>{r.result.createObjectStore('records');};r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(Error('SOURCE_STORAGE_FAILED'));});return this.db;}
 async read(id){const db=await this.open();return new Promise((resolve,reject)=>{const r=db.transaction('records').objectStore('records').get(id);r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(Error('SOURCE_STORAGE_FAILED'));});}
 async write(id,value){const db=await this.open();return new Promise((resolve,reject)=>{const t=db.transaction('records','readwrite');t.objectStore('records').put(value,id);t.oncomplete=()=>resolve();t.onerror=()=>reject(Error('SOURCE_STORAGE_FAILED'));});}
 async key(){let key=await this.read('key');if(!key){key=await this.crypto.subtle.generateKey({name:'AES-GCM',length:256},false,['encrypt','decrypt']);await this.write('key',key);}return key;}
 async save(sources){const key=await this.key(),iv=this.crypto.getRandomValues(new Uint8Array(12));const encrypted=await this.crypto.subtle.encrypt({name:'AES-GCM',iv},key,new TextEncoder().encode(JSON.stringify(sources)));await this.write('sources',{iv,encrypted});}
 async load(){const record=await this.read('sources');if(!record)return [];try{const raw=await this.crypto.subtle.decrypt({name:'AES-GCM',iv:record.iv},await this.key(),record.encrypted);const data=JSON.parse(new TextDecoder().decode(raw));if(!Array.isArray(data)||data.length>16)throw Error();return data;}catch{throw Error('SOURCE_DECRYPTION_FAILED');}}
}
