'use strict';
const TOKEN=window.MLBB_STUDIO_TOKEN;
const ui=id=>document.getElementById(id);
const html=s=>String(s??'').replace(/[&<>"']/g,x=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[x]));
const state={summary:null,groups:[],category:'all',assets:[],offset:0,total:0,selected:null,inspected:null,tab:'preview',viewer:null,previewBlobURL:null,textureId:null,plan:null};
const glyph={all:'▦',hero:'🦸',skill:'⚔',portrait:'▣',icon:'♜',animation:'▤',vfx:'✦',mesh:'⬡',texture:'◈',audio:'♫',rig:'♧',material:'◉',other:'▫'};
let noticeTimeout;
function notify(text,bad=false){const el=ui('toast');el.textContent=text;el.classList.remove('hidden');el.style.color=bad?'#ffadb6':'#eaf2ff';clearTimeout(noticeTimeout);noticeTimeout=setTimeout(()=>el.classList.add('hidden'),4200);}
function alertError(error){notify(error.message||String(error),true);}
async function request(path,options={}){const r=await fetch(path,{...options,headers:{'X-Studio-Token':TOKEN,...options.headers}});if(!r.ok){let msg='HTTP '+r.status;try{const d=await r.json();msg=d.error||msg;}catch{}throw Error(msg);}return r;}
async function json(path){return (await request(path)).json();}
async function post(path,body){return (await request(path,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)})).json();}
function task(fn){Promise.resolve().then(fn).catch(alertError);}
function param(values){return new URLSearchParams(values).toString();}
function validId(){if(state.selected===null)throw Error('Pilih aset dari daftar terlebih dahulu');return state.selected;}
function clearPreviewURL(){if(state.previewBlobURL)URL.revokeObjectURL(state.previewBlobURL);state.previewBlobURL=null;}
function loadInfo(text,bad=false){const el=ui('loadStatus');el.textContent=text;el.className='info '+(bad?'error':'ok');}
function categoryLabel(){if(state.category==='all')return'Semua aset';return state.groups.find(g=>g.key===state.category)?.label||state.category;}
async function loadFile(result){state.summary=result;state.selected=null;state.inspected=null;state.plan=null;ui('planItems').textContent='';ui('applyPlan').classList.add('hidden');
  state.category='all';state.offset=0;
  ui('sourcePath').value=result.path;ui('assetSearch').disabled=false;ui('reload').disabled=false;
  ui('downloadMod').disabled=true;ui('buildStatus').textContent='Repack memerlukan perubahan. Belum ada verifikasi gameplay.';
  loadInfo(`${result.filename} · ${result.objects.toLocaleString('id-ID')} aset · ${(result.bytes/1048576).toFixed(1)} MiB`);
  if(result.archive?.length){ui('bundleChoice').hidden=false;ui('bundleSelect').innerHTML=result.archive.map(item=>`<option value="${item.index}" ${item.label===result.filename?'selected':''}>${html(item.label)}</option>`).join('');}
  else ui('bundleChoice').hidden=true;
  const overview=await json('/api/mlbb/overview');state.groups=overview.groups;renderCategories();await listAssets();await updateDirty();
  notify('Mod berhasil dibaca · deteksi kategori bersifat heuristik');
}
function renderCategories(){const all=[{key:'all',label:'Semua Aset',count:state.summary?.objects||0},...state.groups];
  ui('categories').innerHTML=all.map(group=>`<button data-cat="${group.key}" class="${group.key===state.category?'current':''}"><span>${glyph[group.key]||'▫'}</span><span>${html(group.label)}</span><span class="count">${group.count}</span></button>`).join('');
  for(const btn of ui('categories').querySelectorAll('[data-cat]'))btn.onclick=()=>task(async()=>{state.category=btn.dataset.cat;state.offset=0;state.selected=null;renderCategories();await listAssets();resetInspector();});
}
async function listAssets(){if(!state.summary)return;const category=state.category==='all'?'':state.category;
  const result=await json('/api/mlbb/assets?'+param({category,search:ui('assetSearch').value,offset:state.offset}));state.assets=result.items;state.total=result.total;
  ui('listHeading').textContent=categoryLabel().toUpperCase()+` · ${result.total}`;
  ui('assetList').innerHTML=result.items.length?result.items.map(row=>`<div class="entry ${row.id===state.selected?'active':''}" data-asset="${row.id}"><span class="thumb">${glyph[row.category]||'▫'}</span><div class="grow"><b>${html(row.name)} ${row.edited?'✏️':''}</b><small>${html(row.type)} • ${html(row.kind)} • <span title="Kategori diperkirakan">${html(row.confidence)}</span></small></div><span class="tiny">#${row.id}</span></div>`).join(''):'<div class="empty">Tidak ada aset dalam kategori ini.</div>';
  for(const node of ui('assetList').querySelectorAll('[data-asset]'))node.onclick=()=>task(()=>selectAsset(Number(node.dataset.asset)));
  ui('pageLabel').textContent=`${result.total?state.offset+1:0}–${Math.min(result.total,state.offset+result.items.length)} dari ${result.total}`;
  ui('prev').disabled=state.offset===0;ui('next').disabled=state.offset+result.items.length>=result.total;
}
function resetInspector(){clearPreviewURL();state.viewer=null;state.inspected=null;ui('assetName').textContent='Pilih aset untuk memulai';ui('assetType').textContent='—';ui('assetCategory').textContent='MLBB ASSET INSPECTOR';
  ui('viewer').innerHTML='<div class="empty">Pilih item dari daftar aset.</div>';ui('editorBody').textContent='Pilih aset dahulu.';ui('detailsBody').textContent='Pilih aset dahulu.';ui('relatedBody').textContent='';ui('previewExtras').textContent='';ui('downloadAsset').disabled=true;ui('downloadJson').disabled=true;}
async function selectAsset(id){state.selected=id;state.viewer=null;state.inspected=await json('/api/inspect?'+param({id}));const data=state.inspected;
  const row=state.assets.find(r=>r.id===id)||{category:data.type,confidence:'belum diketahui'};
  ui('assetName').textContent=data.name;ui('assetType').textContent=data.type;ui('assetCategory').textContent=(row.category||'asset').toUpperCase()+' · '+row.confidence;
  ui('downloadAsset').disabled=false;ui('downloadJson').disabled=false;
  renderDetails(data);renderEditor(data);await listAssets();await showTab(state.tab);
}
function detail(label,value){return `<div class="kv"><label>${html(label)}</label><span>${html(value??'—')}</span></div>`;}
function renderDetails(d){const meta=Object.entries(d.metadata||{}).map(([k,v])=>detail(k,typeof v==='object'?JSON.stringify(v):v)).join('');
  ui('detailsBody').innerHTML=detail('Name',d.name)+detail('Unity Type',d.type)+detail('PathID',d.path_id)+detail('Ukuran',d.bytes===null?'tidak tersedia':d.bytes+' byte')+
    detail('Diubah',d.edited?'ya':'belum')+meta+
    (d.bones?`<div class="line"></div><h2>Bone (${d.bones.length})</h2><div class="small">${html(d.bones.join(' · '))}</div>`:'')+
    (d.animation?`<div class="line"></div><h2>AnimationClip</h2><pre>${html(JSON.stringify(d.animation,null,2))}</pre>`:'')+
    (d.read_error?`<div class="alert">Gagal parse: ${html(d.read_error)}</div>`:'');
}
function editableTag(title,description){return `<div class="stack"><h2>${title}</h2><div class="p-hint">${description}</div></div>`;}
function numericFields(prefix,data){return `<div class="fields">${['x','y','z'].map(axis=>`<label>${axis.toUpperCase()}<input type="number" step="0.01" id="${prefix}${axis}" value="${Number(data?.[axis]??(prefix==='s'?1:0))}"></label>`).join('')}</div>`;}
function renderEditor(d){const dest=ui('editorBody'),type=d.type;let source='';
  if(type==='Texture2D'){
    const w=Number(d.metadata?.m_Width||0),h=Number(d.metadata?.m_Height||0);
    source=editableTag('Texture / Portrait / Icon','Pilih PNG, atur fit/cover, lalu terapkan ke Texture2D. Jika sprite memakai atlas, cek koordinat UV dan ukuran sebelum repack.')+
      `<label class="pick">🖼 Pilih PNG<input id="editImage" type="file" accept="image/png"></label><div class="crop"><img id="importPreview" alt="Preview PNG" hidden><canvas id="cropPreview" style="width:100%;max-height:160px;background:#243146;border-radius:9px"></canvas></div>`+
      `<label>Mode penyesuaian<input id="imageFit" type="hidden" value="contain"><select id="imageMode"><option value="contain">Contain — tidak terpotong</option><option value="cover">Cover — memenuhi rasio (terpotong)</option><option value="stretch">Stretch — mengubah rasio</option><option value="original">Original — ukuran PNG asli</option></select></label>`+
      `<div class="small">Ukuran asli Texture2D: ${w||'?'} × ${h||'?'} piksel</div><button id="applyTexture" disabled>Terapkan PNG ke aset</button>`;
  }else if(type==='TextAsset'){
    source=editableTag('TextAsset / Data','Ubah teks yang didukung oleh TypeTree. Perubahan dapat memengaruhi struktur data internal.')+
      `<textarea id="assetText" spellcheck="false"></textarea><button id="applyText">Simpan perubahan teks</button>`;
  }else if(type==='AnimationClip'){
    source=editableTag('Animasi — Loop Setting','Hanya properti loop dari AnimationClip yang diedit secara aman. Belum ada editor keyframe/retarget gerakan dan preview skeletal.')+
      `<div class="mini-grid"><div class="stat">Sample rate<strong>${html(d.animation?.sample_rate??'?')}</strong></div><div class="stat">Event<strong>${html(d.animation?.events??'?')}</strong></div><div class="stat">Rotation curves<strong>${html(d.animation?.rotation_curves??'?')}</strong></div></div>`+
      `<label class="row"><input id="clipLoop" type="checkbox" ${d.animation?.loop?'checked':''}> Ulangi animasi (loop)</label>`+
      `<button id="applyLoop" ${d.animation?.loop===undefined?'disabled':''}>Simpan pengaturan loop</button>`;
  }else if(type==='Transform'){
    source=editableTag('Transform — Position & Scale','Memindahkan Transform dapat berdampak pada skeleton, weapon grip, dan animasi. Nilai tidak otomatis divalidasi oleh gameplay.')+
      `<h2>Posisi lokal</h2>${numericFields('p',d.transform?.m_LocalPosition)}<h2>Skala lokal</h2>${numericFields('s',d.transform?.m_LocalScale)}`+
      `<button id="applyTransform">Terapkan Transform</button>`;
  }else if(type==='Material'){
    source=editableTag('Material — Warna Shader','Editor mengubah angka RGBA pada properti warna yang memang tersimpan di material. Shader MLBB tidak disimulasikan.')+
      `<select id="colorProp"><option value="">Pilih properti warna</option>${(d.colors||[]).map(item=>`<option value="${html(item.property)}">${html(item.property)}</option>`).join('')}</select>`+
      `<div class="fields">${['r','g','b','a'].map(c=>`<label>${c.toUpperCase()}<input type="number" id="color${c}" min="0" max="16" step="0.01" value="1"></label>`).join('')}</div>`+
      `<button id="applyColor" ${(d.colors||[]).length?'':'disabled'}>Terapkan warna material</button>`;
  }else if(type==='Mesh' || type==='SkinnedMeshRenderer'){
    source=editableTag('Mesh & Skinning','Mesh dapat diekspor sebagai OBJ dan dilihat di 3D. Transplantasi mesh + skin weights + bind poses belum diimplementasikan; jangan menyalin data binary dari skeleton berbeda.')+
      `<button id="meshExport">⬇ Ekspor mesh OBJ</button><button id="rigAnalyze" class="ghost">🦴 Audit skeleton bundle</button><pre id="rigOutput" class="small">Belum dianalisis</pre>`;
  }else if(type==='AudioClip'){
    source=editableTag('Audio / Voice','Ekspor WAV tersedia. Reimport audio belum didukung karena pengkodean AudioClip dan bank eksternal dapat berbeda.')+
      `<button id="audioExport">⬇ Ekspor WAV</button>`;
  }else{
    source=editableTag('Asset / Efek / Skill Inspector','Aset ini belum punya editor visual spesifik. Tersedia ekspor data, TypeTree advanced, dan pencarian aset terkait berdasarkan nama.')+
      `<a class="advanced" href="/advanced">Buka TypeTree JSON dan editor lanjutan →</a>`;
  }
  dest.innerHTML=source;
  if(type==='TextAsset'){ui('assetText').value=d.text??'';ui('applyText').onclick=()=>task(async()=>{await post('/api/edit-text',{id:validId(),text:ui('assetText').value});await changed();notify('TextAsset diperbarui');});}
  if(type==='Texture2D')setupTextureEditor(d);
  if(type==='AnimationClip')ui('applyLoop').onclick=()=>task(async()=>{await post('/api/edit-animation',{id:validId(),loop:ui('clipLoop').checked});await changed();notify('Loop AnimationClip disimpan');});
  if(type==='Transform')ui('applyTransform').onclick=()=>task(async()=>{const values={};for(const [key,prefix] of [['m_LocalPosition','p'],['m_LocalScale','s']])values[key]=Object.fromEntries(['x','y','z'].map(x=>[x,Number(ui(prefix+x).value)]));if(!confirm('Terapkan Transform? Posisi bone/weapon dapat berubah saat runtime.'))return;await post('/api/edit-transform',{id:validId(),values});await changed();});
  if(type==='Material'){
    ui('colorProp').onchange=()=>{const item=(d.colors||[]).find(x=>x.property===ui('colorProp').value);if(item)for(const ch of ['r','g','b','a'])ui('color'+ch).value=item.rgba?.[ch]??1;};
    ui('applyColor').onclick=()=>task(async()=>{const property=ui('colorProp').value;if(!property)throw Error('Pilih properti warna dulu');const rgba=Object.fromEntries(['r','g','b','a'].map(ch=>[ch,Number(ui('color'+ch).value)]));await post('/api/edit-material-color',{id:validId(),property,rgba});await changed();});
  }
  if(type==='Mesh'||type==='SkinnedMeshRenderer'){
    ui('meshExport').onclick=()=>task(()=>downloadAsset('auto'));
    ui('rigAnalyze').onclick=()=>task(async()=>{ui('rigOutput').textContent=JSON.stringify(await json('/api/rig'),null,2);});
  }
  if(type==='AudioClip')ui('audioExport').onclick=()=>task(()=>downloadAsset('auto'));
}
function setupTextureEditor(d){let loaded=null;const fileControl=ui('editImage'),canvas=ui('cropPreview'),preview=ui('importPreview');
  function paint(){if(!loaded)return;const mode=ui('imageMode').value;const w=mode==='original'?loaded.width:Number(d.metadata?.m_Width||loaded.width);
    const h=mode==='original'?loaded.height:Number(d.metadata?.m_Height||loaded.height);
    canvas.width=w;canvas.height=h;const ctx=canvas.getContext('2d');ctx.clearRect(0,0,w,h);
    if(mode==='stretch'||mode==='original')ctx.drawImage(loaded,0,0,w,h);
    else{const ratio=mode==='cover'?Math.max(w/loaded.width,h/loaded.height):Math.min(w/loaded.width,h/loaded.height);
      const dw=loaded.width*ratio,dh=loaded.height*ratio;ctx.drawImage(loaded,(w-dw)/2,(h-dh)/2,dw,dh);}
  }
  fileControl.onchange=()=>task(async()=>{const file=fileControl.files[0];if(!file)return;if(!file.name.toLowerCase().endsWith('.png'))throw Error('Pilih PNG');const url=URL.createObjectURL(file);const img=new Image();
    try{await new Promise((resolve,reject)=>{img.onload=resolve;img.onerror=reject;img.src=url});loaded=img;paint();preview.src=url;preview.hidden=false;ui('applyTexture').disabled=false;}catch(e){URL.revokeObjectURL(url);throw e;}
  });
  ui('imageMode').onchange=paint;
  ui('applyTexture').onclick=()=>task(async()=>{if(!loaded)throw Error('Pilih gambar dulu');const id=validId();if(!confirm('Ganti Texture2D dengan hasil penyesuaian PNG?'))return;const blob=await new Promise((resolve,reject)=>canvas.toBlob(b=>b?resolve(b):reject(Error('Gagal encode PNG')),'image/png'));
    await request('/api/edit-texture?'+param({id}),{method:'POST',body:blob});await changed();notify('PNG diterapkan di memori');});
}
async function changed(){ui('downloadMod').disabled=true;const d=await json('/api/summary');state.summary=d;await updateDirty();if(state.selected!==null){const selected=state.selected;const fresh=await json('/api/inspect?'+param({id:selected}));state.inspected=fresh;renderDetails(fresh);}await listAssets();}
async function updateDirty(){const d=await json('/api/summary');const count=d.changes.length;ui('dirtyState').textContent=count?`${count} aset sudah dimodifikasi (belum dipasang ke game). ID: ${d.changes.slice(0,20).join(', ')}`:'Belum ada perubahan.';ui('dirtyState').className='info '+(count?'ok':'');}
function downloadResponse(res,name){return res.blob().then(blob=>{const a=document.createElement('a');const url=URL.createObjectURL(blob);a.href=url;
    const match=(res.headers.get('Content-Disposition')||'').match(/filename\*=UTF-8''([^;]+)/);a.download=match?decodeURIComponent(match[1]):name;
    a.click();setTimeout(()=>URL.revokeObjectURL(url),60000);});}
async function downloadAsset(mode){const id=validId();return downloadResponse(await request('/api/export?'+param({id,mode})),mode==='json'?'asset.json':'asset.bin');}
async function showTab(tab){state.tab=tab;for(const b of ui('editorTabs').querySelectorAll('[data-tab]'))b.classList.toggle('current',b.dataset.tab===tab);
  for(const key of ['preview','edit','details','links'])ui('tab-'+key).hidden=tab!==key;
  if(state.selected===null)return;
  if(tab==='preview')await drawAssetPreview(state.inspected);
  if(tab==='links'){const data=await json('/api/mlbb/related?'+param({id:state.selected}));ui('relatedBody').innerHTML=data.items.length?data.items.map(row=>`<button class="rel" data-related="${row.id}"><b>${html(row.name)}</b><small>${html(row.type)} · ${html(row.common.join(', '))}</small></button>`).join(''):'<div class="small">Tidak ditemukan nama mirip.</div>';
    for(const btn of ui('relatedBody').querySelectorAll('[data-related]'))btn.onclick=()=>task(()=>selectAsset(Number(btn.dataset.related)));
  }
}
async function drawAssetPreview(d){clearPreviewURL();state.viewer=null;ui('previewExtras').innerHTML='';const root=ui('viewer'),id=state.selected;
  if(!d){root.innerHTML='<div class="empty">Pilih aset.</div>';return;}
  if(['Texture2D','Sprite'].includes(d.type)){
    try{const response=await request('/api/preview?'+param({id})),blob=await response.blob();if(id!==state.selected)return;state.previewBlobURL=URL.createObjectURL(blob);
      root.innerHTML=`<img id="assetImage" alt="Preview ${html(d.type)}">`;ui('assetImage').src=state.previewBlobURL;
      if(d.type==='Texture2D' && ['vfx','skill'].includes(state.assets.find(x=>x.id===id)?.category))setupSpriteSheet(blob);
    }catch(error){root.innerHTML=`<div class="alert">Preview tidak tersedia: ${html(error.message)}</div>`;}
  }else if(d.type==='Mesh'){
    root.innerHTML='<div class="empty">Memuat mesh 3D …</div>';
    try{const r=await request('/api/preview?'+param({id}));const source=await r.text();if(id!==state.selected)return;
      root.innerHTML='<canvas id="modelCanvas"></canvas>';state.viewer=new MeshViewer(ui('modelCanvas'));const info=state.viewer.load(source);
      ui('previewStatus').textContent=`${info.triangles.toLocaleString('id-ID')} triangle · Seret untuk memutar · scroll untuk zoom · Mesh statis, tanpa pose skinning.`;
      if(state.textureId!==null)await applyTextureToViewer(state.textureId);
    }catch(error){root.innerHTML=`<div class="alert">Preview mesh gagal: ${html(error.message)}</div>`;}
  }else if(d.type==='AudioClip'){
    try{const r=await request('/api/export?'+param({id})),blob=await r.blob();state.previewBlobURL=URL.createObjectURL(blob);root.innerHTML='<audio controls id="audioPreview"></audio>';ui('audioPreview').src=state.previewBlobURL;}
    catch(error){root.innerHTML=`<div class="alert">${html(error.message)}</div>`;}
  }else if(d.type==='AnimationClip'){
    root.innerHTML=`<div class="empty"><div style="font-size:40px">▤</div><strong>${html(d.name)}</strong><p>Metadata animasi tersedia. Pose skeleton/kurva belum dapat divisualisasikan di runtime browser.</p></div>`;
  }else if(d.type==='SkinnedMeshRenderer'){
    root.innerHTML=`<div class="empty"><div style="font-size:40px">🦴</div> ${html(d.bones?.length||0)} tulang ditemukan.<p>Pilih Mesh tertaut untuk preview geometrinya. Animasi retarget belum tersedia.</p></div>`;
  }else{
    root.innerHTML=`<div class="empty"><div style="font-size:40px">${glyph[state.category]||'◇'}</div>${html(d.type)}<p>Gunakan tab Edit, Detail atau Terkait untuk memeriksa aset.</p></div>`;
  }
}
function setupSpriteSheet(blob){const host=ui('previewExtras');host.innerHTML=`<div class="modal-box stack"><h2>✦ Sprite-sheet / VFX preview (perkiraan)</h2><div class="columns"><label>Kolom<input id="tileCols" type="number" value="4" min="1" max="20"></label><label>Baris<input id="tileRows" type="number" value="4" min="1" max="20"></label></div><label>Frame <span id="tileNumber">1</span><input type="range" id="tileFrame" min="0" max="15" value="0"></label><canvas id="tileCanvas" style="width:180px;height:180px;max-width:100%;background:#162840;border-radius:10px"></canvas><div class="small">Memotong frame PNG; belum menjalankan ParticleSystem, shader, atau VFX engine MLBB.</div></div>`;
  const img=new Image(),url=URL.createObjectURL(blob);img.onload=()=>{const canvas=ui('tileCanvas');if(!canvas){URL.revokeObjectURL(url);return;}const paint=()=>{
    const col=Math.min(20,Math.max(1,Number(ui('tileCols').value)||1)),row=Math.min(20,Math.max(1,Number(ui('tileRows').value)||1));const slider=ui('tileFrame');slider.max=col*row-1;slider.value=Math.min(Number(slider.value),Number(slider.max));
    const index=Number(slider.value),x=index%col,y=Math.floor(index/col);ui('tileNumber').textContent=index+1;
    canvas.width=180;canvas.height=180;const ctx=canvas.getContext('2d');ctx.clearRect(0,0,180,180);ctx.drawImage(img,x*img.width/col,y*img.height/row,img.width/col,img.height/row,0,0,180,180);
  };for(const input of ['tileCols','tileRows','tileFrame'])ui(input).oninput=paint;paint();URL.revokeObjectURL(url);};img.src=url;
}
async function applyTextureToViewer(id){if(!state.viewer)throw Error('Pilih aset Mesh dahulu');const response=await request('/api/preview?'+param({id}));await state.viewer.setTexture(await response.blob());ui('previewStatus').textContent='Tekstur ditempel berdasarkan UV OBJ · material dan shader asli belum direkonstruksi.';}
async function listTextures(){const q=ui('textureFilter').value.trim(),result=await json('/api/mlbb/textures?'+param({search:q}));
  const candidates=result.items;ui('textureChoice').innerHTML='<option value="">Tanpa tekstur</option>'+candidates.map(x=>`<option value="${x.id}">${html(x.name)} (#${x.id})</option>`).join('');
  if(result.total>result.items.length)notify('Daftar terbatas ke '+result.items.length+' tekstur; ketik nama lebih spesifik.');
}
async function loadPlan(blob){const result=await(await request('/api/plan-replacements',{method:'POST',body:blob})).json();state.plan=result;
  ui('planStatus').textContent=`${result.matches.length} cocok · ${result.ambiguous.length} ambigu · ${result.skipped.length} dilewati`;
  ui('planItems').innerHTML=result.matches.map(row=>`<label class="planrow"><input type="checkbox" data-planned="${row.id}" checked><div><b>${html(row.asset)}</b><br><span class="tiny">${html(row.file)} • ${html(row.type)}</span></div></label>`).join('')+
    ((result.ambiguous.length||result.skipped.length)?`<div class="small">${html([...result.ambiguous,...result.skipped].slice(0,8).map(x=>x.file+' — '+x.reason).join('; '))}</div>`:'');
  ui('applyPlan').classList.toggle('hidden',!result.matches.length);
}
function wire(){
  ui('sourceFile').onchange=()=>task(async()=>{const file=ui('sourceFile').files[0];if(!file)return;loadInfo('Mengimpor '+file.name);const res=await request('/api/upload',{method:'POST',headers:{'X-Filename':file.name},body:file});await loadFile(await res.json());});
  ui('openPath').onclick=()=>task(async()=>{await loadFile(await post('/api/open',{path:ui('sourcePath').value.trim()}));});
  ui('selectBundle').onclick=()=>task(async()=>{await loadFile(await post('/api/archive-select',{index:Number(ui('bundleSelect').value)}));});
  let debounce;ui('assetSearch').oninput=()=>{clearTimeout(debounce);debounce=setTimeout(()=>task(async()=>{state.offset=0;await listAssets()}),250);};
  ui('reload').onclick=()=>task(()=>listAssets());ui('prev').onclick=()=>task(async()=>{state.offset=Math.max(0,state.offset-80);await listAssets()});ui('next').onclick=()=>task(async()=>{state.offset+=80;await listAssets()});
  for(const b of ui('editorTabs').querySelectorAll('[data-tab]'))b.onclick=()=>task(()=>showTab(b.dataset.tab));
  ui('downloadAsset').onclick=()=>task(()=>downloadAsset('auto'));ui('downloadJson').onclick=()=>task(()=>downloadAsset('json'));
  ui('jumpAdvanced').onclick=()=>{location.href='/advanced'};
  ui('replaceZip').onchange=()=>task(async()=>{const file=ui('replaceZip').files[0];if(!file)return;if(!state.summary)throw Error('Buka AssetBundle target dulu');await loadPlan(file);});
  ui('applyPlan').onclick=()=>task(async()=>{const ids=[...ui('planItems').querySelectorAll('[data-planned]:checked')].map(x=>Number(x.dataset.planned));if(!ids.length)throw Error('Pilih setidaknya satu asset');
    if(!confirm(`Terapkan ${ids.length} penggantian yang cocok persis?`))return;const res=await post('/api/apply-replacements',{ids});state.plan=null;ui('planItems').textContent='';ui('applyPlan').classList.add('hidden');ui('planStatus').textContent=`${res.count} aset diterapkan. Repack untuk membangun file mod.`;await changed();notify(`${res.count} aset berhasil diganti`);});
  ui('findTextures').onclick=()=>task(()=>listTextures());
  ui('applyPreviewTexture').onclick=()=>task(async()=>{const value=ui('textureChoice').value;if(!value){state.textureId=null;if(state.viewer){state.viewer.hasTexture=0;state.viewer.draw();}return;}state.textureId=Number(value);await applyTextureToViewer(state.textureId);});
  ui('capturePreview').onclick=()=>task(async()=>{if(state.viewer){state.viewer.draw();const a=document.createElement('a');a.href=state.viewer.canvas.toDataURL('image/png');a.download='MLBB_SIMULASI_PREVIEW.png';a.click();return;}
    const img=ui('assetImage');if(!img)throw Error('Preview 3D/tekstur belum tersedia');const a=document.createElement('a');a.href=img.src;a.download='MLBB_ASSET_PREVIEW.png';a.click();});
  ui('buildMod').onclick=()=>task(async()=>{if(!state.summary)throw Error('Buka AssetBundle terlebih dahulu');if(!confirm('Build AssetBundle baru? Sumber asli tidak ditimpa.'))return;
    ui('buildStatus').textContent='Membangun & verifikasi struktur…';const result=await post('/api/save',{});ui('downloadMod').disabled=false;
    ui('buildStatus').textContent=`${result.name} · ${(result.bytes/1048576).toFixed(2)} MiB · ${result.objects} objects · ${result.note}`;notify('Build selesai, verifikasi struktur PASS');});
  ui('downloadMod').onclick=()=>task(async()=>downloadResponse(await request('/api/download-output'),'MLBB_MOD.unity3d'));
  json('/api/health').then(data=>{if(String(data.unitypy).startsWith('Tidak tersedia'))loadInfo('UnityPy belum dipasang: '+data.unitypy,true);else notify('Editor lokal siap · UnityPy '+data.unitypy);}).catch(alertError);
}
wire();
