// Only implemented settings may be saved. A visible inventory entry is not a working toggle.
export const implementedSettings=Object.freeze({
 theme:{type:'enum',values:['dark','light'],default:'dark'},
 accent:{type:'enum',values:['#52DBC8','#06BFFF','#B9A1FF','#7BE3A4','#FFB86B'],default:'#52DBC8'},
 textScale:{type:'enum',values:['90','100','110','120'],default:'100'},
 liveFormat:{type:'enum',values:['m3u8','ts'],default:'m3u8'},
 railWidth:{type:'enum',values:['compact','normal','wide'],default:'compact'},
 columns:{type:'enum',values:['4','5','6','7'],default:'5'},
 sort:{type:'enum',values:['default','recent','name','rating','year'],default:'default'},
 aspect:{type:'enum',values:['fit','fill','stretch'],default:'fit'},
 resumePlayback:{type:'enum',values:['on','off'],default:'on'},
 autoNext:{type:'enum',values:['on','off'],default:'on'},
 seekSeconds:{type:'enum',values:['10','15','30','60'],default:'30'},
 hideAdult:{type:'enum',values:['on','off'],default:'on'},
 homeContinue:{type:'enum',values:['on','off'],default:'on'},
 homeMovies:{type:'enum',values:['on','off'],default:'on'},
 homeSeries:{type:'enum',values:['on','off'],default:'on'},
 homeSports:{type:'enum',values:['on','off'],default:'on'},
 homeFavorites:{type:'enum',values:['on','off'],default:'on'},
 spoilerFree:{type:'enum',values:['on','off'],default:'off'},
 sportsRefresh:{type:'enum',values:['60','120','300','900'],default:'120'},
 timeZone:{type:'enum',values:['Pacific/Auckland','Australia/Sydney','UTC'],default:'Pacific/Auckland'},
 timeFormat:{type:'enum',values:['12h','24h'],default:'12h'},
 sleepMinutes:{type:'enum',values:['off','15','30','60','90','120'],default:'off'},
});
export function validateSetting(key,value){const rule=implementedSettings[key];if(!rule||!rule.values.includes(String(value)))throw Error('SETTING_NOT_IMPLEMENTED_OR_INVALID');return String(value);}
export function readSettings(storage){const result=Object.fromEntries(Object.entries(implementedSettings).map(([k,v])=>[k,v.default]));try{const saved=JSON.parse(storage.getItem('lounge.platform.settings')??'{}');for(const [k,v] of Object.entries(saved)){try{result[k]=validateSetting(k,v);}catch{/* Keep known defaults for invalid imports. */}}}catch{/* Corrupt settings do not prevent boot. */}return result;}
export function saveSetting(storage,key,value){const settings=readSettings(storage);settings[key]=validateSetting(key,value);storage.setItem('lounge.platform.settings',JSON.stringify(settings));return settings;}
