package com.assetstudio.mobile.ui.preview

import android.media.MediaPlayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.assetstudio.mobile.MainViewModel
import com.assetstudio.mobile.core.classes.*
import com.assetstudio.mobile.core.serialized.TypeTreeHelper
import com.assetstudio.mobile.ui.components.MeshPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

@Composable
private fun Panel(title: String, body: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Text(title, style=MaterialTheme.typography.titleMedium)
            body()
        }
    }
}
@Composable private fun Line(s: String) { Text(s, style=MaterialTheme.typography.bodySmall) }

/** Timeline keyframe dari Unity, tidak mengklaim memainkan pose skeletal MLBB. */
@Composable
fun ClipPreview(clip: AnimationClip) {
    val pos=remember(clip){clip.m_PositionCurves.firstOrNull()}
    val flt=remember(clip){clip.m_FloatCurves.firstOrNull()}
    val points=remember(clip){
        if(pos!=null)pos.curve.m_Curve.asSequence().take(1000).map{it.time to it.value.x}.toList()
        else flt?.curve?.m_Curve?.asSequence()?.take(1000)?.map{it.time to it.value}?.toList()
            ?:emptyList()
    }
    val maxKey=points.maxOfOrNull{it.first}?:0f
    val maxEvent=clip.m_Events.maxOfOrNull{it.time}?:0f
    val muscleTime=(clip.m_MuscleClip?.m_StopTime?:0f)-(clip.m_MuscleClip?.m_StartTime?:0f)
    val duration=max(0.1f,max(maxKey,max(maxEvent,muscleTime)))
    var cursor by remember(clip){mutableFloatStateOf(0f)}
    var playing by remember(clip){mutableStateOf(false)}
    LaunchedEffect(playing,duration) {
        while(playing){delay(33);cursor=(cursor+0.033f).let{if(it>=duration)0f else it}}
    }
    Panel("Timeline animasi"){
        Line("${clip.m_Name?:"Clip"} • ${"%.2f".format(duration)} detik • ${clip.m_SampleRate} fps")
        Line("Kurva posisi ${clip.m_PositionCurves.size}, rotasi ${clip.m_RotationCurves.size}, skala ${clip.m_ScaleCurves.size}, float ${clip.m_FloatCurves.size}")
        Line("Loop: ${clip.m_MuscleClip?.m_LoopTime==true} • Event: ${clip.m_Events.size}")
        if(points.size>1){
            val primary=MaterialTheme.colorScheme.primary
            val marker=MaterialTheme.colorScheme.tertiary
            Canvas(Modifier.fillMaxWidth().height(145.dp).background(MaterialTheme.colorScheme.surfaceVariant)){
                val valid=points.filter{it.first.isFinite()&&it.second.isFinite()}
                if(valid.size>1){
                    val low=valid.minOf{it.second}
                    val span=max(0.001f,valid.maxOf{it.second}-low)
                    val path=Path()
                    valid.forEachIndexed{i,p->
                        val x=p.first.coerceIn(0f,duration)/duration*size.width
                        val y=size.height*(1f-(p.second-low)/span)
                        if(i==0)path.moveTo(x,y) else path.lineTo(x,y)
                    }
                    drawPath(path,primary,style=Stroke(2.dp.toPx()))
                }
                val x=cursor/duration*size.width
                drawLine(marker,Offset(x,0f),Offset(x,size.height),1.dp.toPx())
            }
            Line("Grafik ${pos?.path?:flt?.path?:"-"}: ${if(pos!=null)"posisi X" else "nilai float"}")
        }else Line("Clip tidak menyimpan kurva posisi/float yang bisa digambar.")
        Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){
            Button(onClick={playing=!playing}){Text(if(playing)"Jeda" else "Putar timeline")}
            Line("${"%.2f".format(cursor)} / ${"%.2f".format(duration)} s")
        }
        Slider(value=cursor.coerceIn(0f,duration),valueRange=0f..duration,
            onValueChange={cursor=it;playing=false})
        clip.m_Events.take(16).forEach{Line("${"%.2f".format(it.time)}s • ${it.functionName}")}
        Line("Pratinjau keyframe, bukan gerak model 3D asli dalam game.")
    }
}

@Composable
fun RigPreview(obj: UnityObject) {
    val content=remember(obj){
        when(obj){
            is Avatar->listOf(
                "Node skeleton: "+obj.m_Avatar.m_AvatarSkeleton.m_Node.size,
                "Pose skeleton: "+obj.m_Avatar.m_AvatarSkeletonPose.m_X.size,
                "Jalur bone: "+obj.m_TOS.size
            )+obj.m_TOS.take(90).map{it.second}
            is Animator->listOf("Controller: "+(obj.m_Controller.tryGetAny()?.displayName?:"belum dimuat"),
                "Avatar: "+(obj.m_Avatar.tryGetAny()?.displayName?:"belum dimuat"),
                "Hierarki transform: "+obj.m_HasTransformHierarchy)
            is AnimatorController->listOf("Total clip: "+obj.m_AnimationClips.size)+
                obj.m_AnimationClips.take(100).map{it.tryGet<AnimationClip>()?.m_Name?:"Clip #"+it.m_PathID}
            is Animation->obj.m_Animations.take(100).map{it.tryGet<AnimationClip>()?.m_Name?:"Clip #"+it.m_PathID}
            is Transform->{
                val out=mutableListOf<String>()
                val visited=mutableSetOf<Long>()
                fun walk(t:Transform,level:Int){
                    if(level>12||out.size>=90||!visited.add(t.m_PathID))return
                    out.add("  ".repeat(level)+(t.m_GameObject.tryGet<GameObject>()?.displayName?:"Transform #"+t.m_PathID))
                    t.m_Children.forEach{it.tryGet<Transform>()?.let{child->walk(child,level+1)}}
                }
                walk(obj,0)
                listOf("Posisi: "+obj.m_LocalPosition,"Skala: "+obj.m_LocalScale)+out
            }
            else->emptyList()
        }
    }
    Panel("Pratinjau rig / referensi animasi"){
        if(content.isEmpty())Line("Tidak ada struktur yang terbaca.")
        Column(Modifier.height(260.dp).verticalScroll(rememberScrollState())) {
            content.forEach{Line(it)}
        }
        Line("Ini hierarki/metadata. Simulasi animasi skeletal belum didukung.")
    }
}

@Composable
fun HeroPreview(go: GameObject){
    val mesh=remember(go){go.m_SkinnedMeshRenderer?.m_Mesh?.tryGet<Mesh>()
        ?:go.m_MeshFilter?.m_Mesh?.tryGet<Mesh>()}
    if(mesh!=null)MeshPreview(mesh)
    Panel("Komponen hero / GameObject"){
        Line(go.displayName+" • "+go.m_Components.size+" komponen")
        go.m_Components.take(90).forEach{
            val part=it.tryGetAny()
            Line(part?.let{p->p.type.name+" — "+p.displayName}
                ?:"Referensi #"+it.m_PathID+" belum dimuat")
        }
        if(mesh==null)Line("Mesh belum dapat ditemukan; kemungkinan butuh bundle dependensi.")
    }
}

@Composable
fun MaterialPreview(vm:MainViewModel,m:Material){
    val props=m.m_SavedProperties
    var textureSlot by remember(m) { mutableIntStateOf(0) }
    val tex = remember(m, textureSlot) {
        runCatching {
            props.m_TexEnvs.getOrNull(textureSlot)?.second?.m_Texture?.tryGet<Texture2D>()
        }.getOrNull()
    }
    val bitmap by produceState<android.graphics.Bitmap?>(null, tex) {
        value = tex?.let { runCatching { vm.decodeTexture(it) }.getOrNull() }
    }
    Panel("Pratinjau material"){
        Line("Shader: "+(m.m_Shader.tryGetAny()?.displayName?:"belum dimuat"))
        Line("${props.m_TexEnvs.size} slot tekstur, ${props.m_Colors.size} warna")
        if (props.m_TexEnvs.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { textureSlot = max(0, textureSlot - 1) },
                    enabled = textureSlot > 0) { Text("Sebelum") }
                Line((textureSlot + 1).toString() + " / " + props.m_TexEnvs.size)
                OutlinedButton(onClick = {
                    textureSlot = min(props.m_TexEnvs.lastIndex, textureSlot + 1)
                }, enabled = textureSlot < props.m_TexEnvs.lastIndex) { Text("Berikut") }
            }
            Line("Slot: " + props.m_TexEnvs[textureSlot].first +
                " • " + (tex?.displayName ?: "referensi tekstur belum tersedia"))
        }
        bitmap?.let{
            Image(it.asImageBitmap(),contentDescription="Tekstur material",
                modifier=Modifier.fillMaxWidth().height(180.dp),contentScale=ContentScale.Fit)
        }
        if (bitmap == null && props.m_TexEnvs.isNotEmpty()) {
            Line("Tekstur slot ini tidak tersedia / tidak bisa didekode. " +
                "Coba slot lain atau muat bundle dependensi.")
        }
        props.m_Colors.take(20).forEach{(name,c)->
            Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){
                Box(Modifier.size(26.dp).background(Color(c.r.coerceIn(0f,1f),c.g.coerceIn(0f,1f),
                    c.b.coerceIn(0f,1f),c.a.coerceIn(0f,1f))))
                Line(name)
            }
        }
        props.m_TexEnvs.take(15).forEach{(name,env)->
            Line(name+" → "+(env.m_Texture.tryGetAny()?.displayName?:"belum dimuat"))
        }
        Line("Preview warna/tekstur; shader dan lighting MLBB tidak dijalankan.")
    }
}

private data class AudioFile(val file:File?=null,val message:String="")
@Composable
fun SoundPreview(clip:AudioClip){
    val context=LocalContext.current
    val n=remember(clip){runCatching{clip.listSamples().size}.getOrDefault(0)}
    var sample by remember(clip){mutableStateOf(0)}
    val audio by produceState(AudioFile(message="Membaca audio…"),clip,sample){
        value=withContext(Dispatchers.IO){
            try{
                if(clip.m_Size>32L*1024*1024)AudioFile(message="Audio terlalu besar untuk preview (>32 MB)")
                else{
                    val data=clip.toWav(sample)?:clip.getAudioData().takeIf{
                        it.size>=12&&String(it,0,4,Charsets.US_ASCII)=="RIFF"&&
                            String(it,8,4,Charsets.US_ASCII)=="WAVE"
                    }
                    if(data==null)AudioFile(message="Codec belum dapat didekode sebagai WAV.")
                    else File.createTempFile("mlbb_audio_",".wav",context.cacheDir).let{
                        it.writeBytes(data);AudioFile(file=it)
                    }
                }
            }catch(e:Exception){AudioFile(message="Gagal memuat audio: "+e.message)}
        }
    }
    var player by remember(clip){mutableStateOf<MediaPlayer?>(null)}
    var ready by remember(clip,sample){mutableStateOf(false)}
    var playing by remember(clip,sample){mutableStateOf(false)}
    var error by remember(clip,sample){mutableStateOf<String?>(null)}
    DisposableEffect(audio.file){
        val file=audio.file
        val mp=if(file==null)null else try{
            MediaPlayer().apply{
                setOnPreparedListener{ready=true}
                setOnCompletionListener{playing=false}
                setOnErrorListener{_,_,_->error="Pemutar Android tidak mendukung codec ini.";playing=false;true}
                setDataSource(file.absolutePath)
                prepareAsync()
            }
        }catch(e:Exception){error="Pemutar gagal: "+e.message;null}
        player=mp
        onDispose{runCatching{mp?.release()};file?.delete();player=null}
    }
    Panel("Pratinjau suara"){
        Line("${clip.m_Length} detik • ${clip.m_Channels} channel • ${clip.m_Frequency} Hz")
        if(n>1)Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedButton(onClick={sample=max(0,sample-1)},enabled=sample>0){Text("Sebelum")}
            Line("${sample+1}/$n")
            OutlinedButton(onClick={sample=min(n-1,sample+1)},enabled=sample+1<n){Text("Berikut")}
        }
        Button(onClick={
            val p=player?:return@Button
            if(playing){p.pause();playing=false}else{p.start();playing=true}
        },enabled=ready&&error==null){Text(if(playing)"Jeda" else "Putar")}
        if(error!=null)Line(error!!)
        else if(!ready)Line(audio.message.ifEmpty{"Menyiapkan pemutar…"})
    }
}

@Composable
fun AnyAssetPreview(obj:UnityObject){
    val dump by produceState("Memuat data…",obj){
        value=withContext(Dispatchers.IO){
            try{
                val tree=obj.reader.serializedType?.m_Type
                if(tree==null)"TypeTree tidak tersedia untuk "+obj.type.name+". Aset dapat diekspor mentah."
                else{obj.reader.reset();TypeTreeHelper.readTypeString(tree,obj.reader).take(50000)}
            }catch(e:Exception){"Gagal menampilkan TypeTree: "+e.message}
        }
    }
    Panel("Pratinjau data "+obj.type.name){
        Line("Isi serialized asli. Untuk ParticleSystem, VFX/shader game tidak dapat diputar.")
        SelectionContainer{
            Text(dump,fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall,
                modifier=Modifier.height(360.dp).verticalScroll(rememberScrollState()))
        }
    }
}


/**
 * Real SpriteAtlas preview. Uses the referenced Texture2D or actual packed
 * Sprite crop, not an invented preview image.
 */
@Composable
fun AtlasPreview(vm: MainViewModel, atlas: SpriteAtlas) {
    var fullAtlas by remember(atlas) { mutableStateOf(true) }
    var selected by remember(atlas) { mutableIntStateOf(0) }
    val atlasTexture = remember(atlas) {
        atlas.m_RenderDataMap.values.firstNotNullOfOrNull { entry ->
            runCatching { entry.texture.tryGet<Texture2D>() }.getOrNull()
        }
    }
    val chosenSprite = remember(atlas, selected) {
        runCatching { atlas.m_PackedSprites.getOrNull(selected)?.tryGet<Sprite>() }.getOrNull()
    }
    val bitmap by produceState<android.graphics.Bitmap?>(null, atlasTexture, chosenSprite, fullAtlas) {
        value = if (fullAtlas) {
            atlasTexture?.let { runCatching { vm.decodeTexture(it) }.getOrNull() }
        } else {
            chosenSprite?.let { runCatching { vm.decodeSprite(it) }.getOrNull() }
        }
    }
    Panel("Pratinjau SpriteAtlas") {
        Line("Sprite: " + atlas.m_PackedSprites.size + " • peta render: " +
            atlas.m_RenderDataMap.size + " • variant: " + atlas.m_IsVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { fullAtlas = true }, enabled = !fullAtlas) {
                Text("Atlas penuh")
            }
            OutlinedButton(onClick = { fullAtlas = false },
                enabled = fullAtlas && atlas.m_PackedSprites.isNotEmpty()) {
                Text("Per sprite")
            }
        }
        if (!fullAtlas && atlas.m_PackedSprites.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { selected = max(0, selected - 1) },
                    enabled = selected > 0) { Text("Sebelum") }
                Line((selected + 1).toString() + " / " + atlas.m_PackedSprites.size)
                OutlinedButton(onClick = {
                    selected = min(atlas.m_PackedSprites.lastIndex, selected + 1)
                }, enabled = selected < atlas.m_PackedSprites.lastIndex) { Text("Berikut") }
            }
            Line(chosenSprite?.displayName ?: "Sprite terkait belum dimuat.")
        }
        bitmap?.let { result ->
            Image(
                bitmap = result.asImageBitmap(),
                contentDescription = "Pratinjau SpriteAtlas asli",
                modifier = Modifier.fillMaxWidth().height(320.dp),
                contentScale = ContentScale.Fit
            )
            Line(result.width.toString() + " × " + result.height + " px")
        } ?: Line("Data gambar tidak tersedia. Muat paket dependensi " +
            "atau gunakan Sprite/Texture2D langsung.")
    }
}

/** Bone list is derived from actual SkinnedMeshRenderer PPtr references. */
@Composable
fun SkinnedBonePreview(renderer: SkinnedMeshRenderer) {
    Panel("Pratinjau bone & skin") {
        Line(renderer.m_Bones.size.toString() + " bone • " +
            renderer.m_Materials.size + " slot material • " +
            (renderer.m_BlendShapeWeights?.size ?: 0) + " bobot blend shape")
        if (renderer.m_Bones.isEmpty()) {
            Line("Daftar bone tidak tersedia dalam SkinnedMeshRenderer ini.")
        } else {
            Column(Modifier.height(240.dp).verticalScroll(rememberScrollState())) {
                renderer.m_Bones.take(96).forEachIndexed { index, ptr ->
                    val bone = remember(renderer, index) {
                        runCatching { ptr.tryGet<Transform>() }.getOrNull()
                    }
                    Line((index + 1).toString() + ". " +
                        (bone?.m_GameObject?.tryGet<GameObject>()?.displayName
                            ?: "Bone #" + ptr.m_PathID + " (belum dimuat)"))
                }
            }
        }
        Line("Daftar bone asli. Deformasi skin saat animasi belum disimulasikan.")
    }
}
