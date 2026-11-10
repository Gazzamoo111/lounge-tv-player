// Use the real Android brand assets; never replace them with a third-party player's logo.
import{mkdirSync,copyFileSync}from'node:fs';import{resolve}from'node:path';
const root=resolve(import.meta.dirname,'../../..'),out=resolve(import.meta.dirname,'../public');mkdirSync(out,{recursive:true});
for(const [name,source]of [['brand-mark.png','drawable-nodpi/loungetv_icon_variant_cobalt.png'],['brand-wordmark.png','drawable-nodpi/loungetv_locked_horizontal.png'],['jakarta.ttf','font/plus_jakarta_sans_variable.ttf']])copyFileSync(resolve(root,'app/src/main/res',source),resolve(out,name));
