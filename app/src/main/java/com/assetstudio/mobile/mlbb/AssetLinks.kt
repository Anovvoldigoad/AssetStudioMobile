package com.assetstudio.mobile.mlbb

import com.assetstudio.mobile.core.classes.AssetBundle
import com.assetstudio.mobile.core.classes.ResourceManager
import com.assetstudio.mobile.core.classes.Sprite
import com.assetstudio.mobile.core.classes.Animation
import com.assetstudio.mobile.core.classes.Animator
import com.assetstudio.mobile.core.classes.AnimatorController
import com.assetstudio.mobile.core.classes.Component
import com.assetstudio.mobile.core.classes.GameObject
import com.assetstudio.mobile.core.classes.Material
import com.assetstudio.mobile.core.classes.MeshFilter
import com.assetstudio.mobile.core.classes.PPtr
import com.assetstudio.mobile.core.classes.Renderer
import com.assetstudio.mobile.core.classes.SkinnedMeshRenderer
import com.assetstudio.mobile.core.classes.SpriteAtlas
import com.assetstudio.mobile.core.classes.Transform
import com.assetstudio.mobile.core.classes.UnityObject

data class AssetReference(
    val role: String,
    val pathId: Long,
    val target: UnityObject?,
    val missing: Boolean
)
data class AssetCandidate(val id: String, val session: Long, val pathId: Long, val fileName: String)

/** Do not jump into another mod session or a duplicate path ID. */
fun uniqueAssetLink(sourceSession: Long, targetPathId: Long, targetFile: String,
    candidates: List<AssetCandidate>): String? {
    return candidates.filter {
        it.session == sourceSession && it.pathId == targetPathId &&
            it.fileName.equals(targetFile, ignoreCase = true)
    }.singleOrNull()?.id
}

/** Only actual Unity PPtr relationships; never infer from similar file names. */
fun assetReferences(obj: UnityObject, maxItems: Int = 120): List<AssetReference> {
    if (maxItems <= 0) return emptyList()
    val result = ArrayList<AssetReference>()
    fun add(label: String, ptr: PPtr) {
        if (result.size >= maxItems || ptr.isNull) return
        val target = runCatching { ptr.tryGetAny() }.getOrNull()
        result.add(AssetReference(label, ptr.m_PathID, target, target == null))
    }
    fun materials(renderer: Renderer) {
        renderer.m_Materials.take(24).forEachIndexed { index, ptr ->
            add("Material " + (index + 1), ptr)
        }
    }
    when (obj) {
        is AssetBundle -> {
            obj.m_Container.take(80).forEach { (name, info) ->
                add("Bundle: " + name.take(70), info.asset)
            }
            obj.m_PreloadTable.take(20).forEachIndexed { index, ptr ->
                add("Preload " + (index + 1), ptr)
            }
        }
        is ResourceManager -> obj.m_Container.take(90).forEach { (name, ptr) ->
            add("Resource: " + name.take(70), ptr)
        }
        is Sprite -> {
            obj.m_SpriteAtlas?.let { add("SpriteAtlas", it) }
            add("Texture", obj.m_RD.texture)
            obj.m_RD.alphaTexture?.let { add("Alpha Texture", it) }
        }
        is GameObject -> obj.m_Components.take(80).forEachIndexed { index, ptr ->
            add("Komponen " + (index + 1), ptr)
        }
        is SkinnedMeshRenderer -> {
            add("Mesh", obj.m_Mesh)
            materials(obj)
            obj.m_Bones.take(48).forEachIndexed { index, ptr ->
                add("Bone " + (index + 1), ptr)
            }
            add("GameObject", obj.m_GameObject)
        }
        is MeshFilter -> {
            add("Mesh", obj.m_Mesh)
            add("GameObject", obj.m_GameObject)
        }
        is Renderer -> {
            materials(obj)
            add("GameObject", obj.m_GameObject)
        }
        is Animator -> {
            add("Animator Controller", obj.m_Controller)
            add("Avatar", obj.m_Avatar)
            add("GameObject", obj.m_GameObject)
        }
        is AnimatorController -> obj.m_AnimationClips.take(100).forEachIndexed { index, ptr ->
            add("Clip " + (index + 1), ptr)
        }
        is Animation -> {
            obj.m_Animations.take(90).forEachIndexed { index, ptr ->
                add("Clip " + (index + 1), ptr)
            }
            add("GameObject", obj.m_GameObject)
        }
        is Material -> {
            add("Shader", obj.m_Shader)
            obj.m_SavedProperties.m_TexEnvs.take(90).forEach { (name, env) ->
                add("Tekstur " + name, env.m_Texture)
            }
        }
        is SpriteAtlas -> {
            obj.m_PackedSprites.take(90).forEachIndexed { index, ptr ->
                add("Sprite " + (index + 1), ptr)
            }
            obj.m_RenderDataMap.values.take(12).forEachIndexed { index, data ->
                add("Atlas Texture " + (index + 1), data.texture)
                add("Atlas Alpha " + (index + 1), data.alphaTexture)
            }
        }
        is Transform -> {
            add("Parent", obj.m_Father)
            obj.m_Children.take(95).forEachIndexed { index, ptr ->
                add("Anak " + (index + 1), ptr)
            }
            add("GameObject", obj.m_GameObject)
        }
        is Component -> add("GameObject", obj.m_GameObject)
    }
    return result
}
