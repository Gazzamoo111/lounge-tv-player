// Only implemented settings may be saved. A visible inventory entry is not a working toggle.
export const implementedSettings=Object.freeze({
 theme:{type:'enum',values:['dark','light'],default:'dark'},
 accent:{type:'enum',values:['#52DBC8','#06BFFF','#B9A1FF','#7BE3A4','#FFB86B'],default:'#52DBC8'},
 textScale:{type:'enum',values:['90','100','110','120'],default:'100'},
 liveFormat:{type:'enum',values:['m3u8','ts'],default:'m3u8'},
});
export function validateSetting(key,value){const rule=implementedSettings[key];if(!rule||!rule.values.includes(String(value)))throw Error('SETTING_NOT_IMPLEMENTED_OR_INVALID');return String(value);}
export function readSettings(storage){const result=Object.fromEntries(Object.entries(implementedSettings).map(([k,v])=>[k,v.default]));try{const saved=JSON.parse(storage.getItem('lounge.platform.settings')??'{}');for(const [k,v] of Object.entries(saved)){try{result[k]=validateSetting(k,v);}catch{/* Keep known defaults for invalid imports. */}}}catch{/* Corrupt settings do not prevent boot. */}return result;}
export function saveSetting(storage,key,value){const settings=readSettings(storage);settings[key]=validateSetting(key,value);storage.setItem('lounge.platform.settings',JSON.stringify(settings));return settings;}
