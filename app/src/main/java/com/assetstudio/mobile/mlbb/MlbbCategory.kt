package com.assetstudio.mobile.mlbb

import com.assetstudio.mobile.core.ClassIDType

/** Heuristik navigasi MLBB, bukan mapping hero/skill yang terverifikasi. */
enum class MlbbCategory(
    val title: String,
    val description: String,
    val glyph: String
) {
    ALL("Semua aset", "Browser Unity lengkap", "📦"),
    HERO("Hero & Skin", "Model dan komponen karakter", "🦸"),
    SKILL("Skill & Attack", "Aset skill/serangan", "⚔️"),
    PORTRAIT("Portrait", "Potret dan loading art", "🖼️"),
    ICON("Icon & UI", "Ikon skill dan HUD", "🎯"),
    ANIMATION("Animasi", "Clip dan controller", "🎞️"),
    VFX("Efek & VFX", "Particle dan efek", "✨"),
    MESH("Mesh 3D", "Geometri dan renderer", "🧊"),
    TEXTURE("Texture", "Gambar dan sprite", "🎨"),
    AUDIO("Audio & SFX", "Klip suara dan sumber", "🎵"),
    VIDEO("Video", "VideoClip dan cutscene tersimpan", "🎬"),
    RIG("Rig & Bone", "Avatar, transform, skin", "🦴"),
    MATERIAL("Material", "Material dan shader", "💎");

    companion object {
        fun fromRoute(route: String?): MlbbCategory =
            values().firstOrNull { it.name.equals(route, ignoreCase = true) } ?: ALL
    }

    fun accepts(type: ClassIDType, name: String): Boolean {
        val n = name.lowercase()
        fun named(vararg words: String) = words.any { n.contains(it) }
        val image = type in setOf(ClassIDType.Texture2D, ClassIDType.Sprite, ClassIDType.SpriteAtlas)
        val model = type in setOf(
            ClassIDType.Mesh, ClassIDType.MeshFilter, ClassIDType.MeshRenderer,
            ClassIDType.SkinnedMeshRenderer
        )
        val motion = type in setOf(
            ClassIDType.AnimationClip, ClassIDType.Animation, ClassIDType.Animator,
            ClassIDType.AnimatorController, ClassIDType.AnimatorOverrideController,
            ClassIDType.RuntimeAnimatorController
        )
        return when (this) {
            ALL -> true
            HERO -> (model || type == ClassIDType.Avatar || type == ClassIDType.GameObject) &&
                (type == ClassIDType.SkinnedMeshRenderer || type == ClassIDType.Avatar ||
                    named("hero", "skin", "character", "body", "weapon", "head", "sword"))
            SKILL -> (motion || type in setOf(
                ClassIDType.MonoBehaviour, ClassIDType.TextAsset, ClassIDType.AudioClip,
                ClassIDType.ParticleSystem, ClassIDType.GameObject
            )) && named("skill", "attack", "spell", "ability", "ult", "combo", "buff", "cast", "hit")
            PORTRAIT -> image && named(
                "portrait", "loading", "avatar", "profile", "herohead", "hero_head", "headicon", "card"
            )
            ICON -> image && named(
                "icon", "hud", "button", "btn", "skill", "buff", "badge", "ui_", "atlas"
            )
            ANIMATION -> motion
            VFX -> type in setOf(
                ClassIDType.ParticleSystem, ClassIDType.ParticleSystemRenderer,
                ClassIDType.TrailRenderer, ClassIDType.LineRenderer
            ) || (type in setOf(ClassIDType.Mesh, ClassIDType.Material, ClassIDType.Shader,
                ClassIDType.Texture2D, ClassIDType.AnimationClip) &&
                named("fx_", "vfx", "effect", "particle", "trail", "slash"))
            MESH -> model
            TEXTURE -> image || type == ClassIDType.Texture2DArray
            AUDIO -> type in setOf(ClassIDType.AudioClip, ClassIDType.AudioSource)
            VIDEO -> type in setOf(ClassIDType.VideoClip, ClassIDType.MovieTexture)
            RIG -> type in setOf(
                ClassIDType.Avatar, ClassIDType.Transform, ClassIDType.SkinnedMeshRenderer
            )
            MATERIAL -> type in setOf(ClassIDType.Material, ClassIDType.Shader)
        }
    }
}
