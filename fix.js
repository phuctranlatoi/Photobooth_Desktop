const fs = require('fs');
const path = 'E:/HK1_2026_2027/photobooth-web-album/src/app/api/v1/public/albums/[token]/assets/[assetId]/download/route.ts';
let content = fs.readFileSync(path, 'utf8');
content = content.replace(/flags:  ttachment:\$\{filenameWithoutExt\}/g, 'flags: ttachment:');
fs.writeFileSync(path, content);
console.log('Fixed');
