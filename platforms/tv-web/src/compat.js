// Tizen 5.5 / Chromium 69 runtime floor. Syntax is handled by the build target.
if(!Object.fromEntries)Object.fromEntries=function(entries){const result={};for(const [key,value]of entries)Object.defineProperty(result,key,{value,writable:true,enumerable:true,configurable:true});return result;};
export const labelWords=s=>s.split('_').join(' ');
