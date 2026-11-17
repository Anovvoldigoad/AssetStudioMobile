package com.assetstudio.mobile.mlbb

import com.assetstudio.mobile.core.ClassIDType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MlbbCategoryTest {
    @Test fun routeIsSafe() {
        assertEquals(MlbbCategory.ALL, MlbbCategory.fromRoute("../anything"))
        assertEquals(MlbbCategory.SKILL, MlbbCategory.fromRoute("skill"))
        assertEquals(MlbbCategory.ALL, MlbbCategory.fromRoute(null))
    }
    @Test fun textureAndMeshDoNotCross() {
        assertTrue(MlbbCategory.MESH.accepts(ClassIDType.Mesh, "Hero_Body"))
        assertFalse(MlbbCategory.MESH.accepts(ClassIDType.Texture2D, "Hero_Body"))
        assertTrue(MlbbCategory.TEXTURE.accepts(ClassIDType.Texture2D, "Hero_Body"))
    }
    @Test fun skillRequiresRecognizableNameAndType() {
        assertTrue(MlbbCategory.SKILL.accepts(ClassIDType.AnimationClip, "Hero_Attack01"))
        assertTrue(MlbbCategory.SKILL.accepts(ClassIDType.MonoBehaviour, "SkillData"))
        assertFalse(MlbbCategory.SKILL.accepts(ClassIDType.AnimationClip, "Idle"))
        assertFalse(MlbbCategory.SKILL.accepts(ClassIDType.Texture2D, "skill_icon"))
    }
    @Test fun iconsPortraitAnimationAndVfx() {
        assertTrue(MlbbCategory.ICON.accepts(ClassIDType.Sprite, "Hero_Skill_Icon"))
        assertTrue(MlbbCategory.PORTRAIT.accepts(ClassIDType.Texture2D, "Hero_Loading_01"))
        assertTrue(MlbbCategory.ANIMATION.accepts(ClassIDType.AnimatorController, "controller"))
        assertTrue(MlbbCategory.VFX.accepts(ClassIDType.ParticleSystem, "unnamed"))
        assertTrue(MlbbCategory.RIG.accepts(ClassIDType.SkinnedMeshRenderer, "rig"))
    }
}
