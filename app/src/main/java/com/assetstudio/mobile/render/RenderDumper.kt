package com.assetstudio.mobile.render

import com.assetstudio.mobile.core.classes.Material
import com.assetstudio.mobile.core.classes.Mesh
import com.assetstudio.mobile.core.classes.MeshFilter
import com.assetstudio.mobile.core.classes.Shader
import com.assetstudio.mobile.core.classes.ShaderCompilerPlatform
import com.assetstudio.mobile.core.classes.SkinnedMeshRenderer
import com.assetstudio.mobile.core.classes.Texture2D
import com.assetstudio.mobile.core.classes.UnityTexEnv

/*
 * 渲染资产文本转储器（对应 AssetStudio 的 Material/Shader 导出文本信息）：
 * - Material：着色器引用、纹理槽（引用解析出贴图名）、标量/颜色属性
 * - Shader：属性表、子着色器/Pass 结构、平台编译块、关键词
 * - SkinnedMeshRenderer：网格引用、骨骼列表、材质槽、混合形状权重
 * - MeshFilter：网格引用
 *
 * 输出为等宽可读文本，用于详情页查看、导出与 MCP 工具返回。
 */
object RenderDumper {

    // ============================ Material ============================

    fun dumpMaterial(material: Material): String {
        val sb = StringBuilder()
        sb.appendLine("Material: ${material.displayName}")
        sb.appendLine("PathID: ${material.m_PathID}")

        // 着色器引用
        val shader = material.m_Shader.tryGet<Shader>()
        if (shader != null) {
            sb.appendLine("Shader: ${shader.m_ParsedForm?.m_Name ?: shader.m_Name} (fileID=${material.m_Shader.m_FileID}, pathID=${material.m_Shader.m_PathID})")
        } else {
            sb.appendLine("Shader: <Belum terbaca> (fileID=${material.m_Shader.m_FileID}, pathID=${material.m_Shader.m_PathID})")
        }
        sb.appendLine()

        // 已保存属性
        val props = material.m_SavedProperties
        sb.appendLine("--- Slot tekstur (${props.m_TexEnvs.size}) ---")
        if (props.m_TexEnvs.isEmpty()) {
            sb.appendLine("  (Tidak ada)")
        }
        for ((name, env) in props.m_TexEnvs) {
            sb.appendLine(dumpTexEnv(name, env))
        }

        props.m_Ints?.let { ints ->
            sb.appendLine()
            sb.appendLine("--- Properti bilangan bulat (${ints.size}) ---")
            if (ints.isEmpty()) sb.appendLine("  (Tidak ada)")
            for ((name, v) in ints) {
                sb.appendLine("  $name = $v")
            }
        }

        sb.appendLine()
        sb.appendLine("--- Properti desimal (${props.m_Floats.size}) ---")
        if (props.m_Floats.isEmpty()) sb.appendLine("  (Tidak ada)")
        for ((name, v) in props.m_Floats) {
            sb.appendLine("  $name = ${fmt(v)}")
        }

        sb.appendLine()
        sb.appendLine("--- Properti warna (${props.m_Colors.size}) ---")
        if (props.m_Colors.isEmpty()) sb.appendLine("  (Tidak ada)")
        for ((name, c) in props.m_Colors) {
            sb.appendLine("  $name = RGBA(${fmt(c.r)}, ${fmt(c.g)}, ${fmt(c.b)}, ${fmt(c.a)}) #${hex(c)}")
        }
        return sb.toString()
    }

    private fun dumpTexEnv(name: String, env: UnityTexEnv): String {
        val tex = env.m_Texture.tryGet<Texture2D>()
        val ref = if (env.m_Texture.isNull) {
            "(Kosong)"
        } else if (tex != null) {
            "「${tex.m_Name}」${tex.m_Width}x${tex.m_Height} ${tex.m_TextureFormat}"
        } else {
            "Belum terbaca (fileID=${env.m_Texture.m_FileID}, pathID=${env.m_Texture.m_PathID})"
        }
        return "  $name → $ref  [scale(${fmt(env.m_Scale.x)}, ${fmt(env.m_Scale.y)}) offset(${fmt(env.m_Offset.x)}, ${fmt(env.m_Offset.y)})]"
    }

    // ============================ 渲染器组件 ============================

    /** SkinnedMeshRenderer 转储：网格引用、骨骼、材质槽、混合形状权重 */
    fun dumpSkinnedMeshRenderer(smr: SkinnedMeshRenderer): String {
        val sb = StringBuilder()
        sb.appendLine("SkinnedMeshRenderer: ${smr.displayName}")
        sb.appendLine("PathID: ${smr.m_PathID}")
        sb.appendLine()

        dumpMeshRef(sb, smr.m_Mesh)

        // 骨骼
        sb.appendLine("--- Tulang (${smr.m_Bones.size}) ---")
        if (smr.m_Bones.isEmpty()) sb.appendLine("  (Tidak ada)")
        smr.m_Bones.forEachIndexed { i, bone ->
            sb.appendLine("  [$i] ${dumpPtr(bone)}")
        }

        // 材质槽
        dumpMaterialSlots(sb, smr.m_Materials)

        // 混合形状权重
        smr.m_BlendShapeWeights?.let { weights ->
            sb.appendLine()
            sb.appendLine("--- Bobot blend shape (${weights.size}) ---")
            if (weights.isEmpty()) sb.appendLine("  (Tidak ada)")
            weights.forEachIndexed { i, w ->
                sb.appendLine("  [$i] = ${fmt(w)}")
            }
        }
        return sb.toString()
    }

    /** MeshFilter 转储：网格引用 */
    fun dumpMeshFilter(mf: MeshFilter): String {
        val sb = StringBuilder()
        sb.appendLine("MeshFilter: ${mf.displayName}")
        sb.appendLine("PathID: ${mf.m_PathID}")
        sb.appendLine()
        dumpMeshRef(sb, mf.m_Mesh)
        return sb.toString()
    }

    /** 网格引用段：解析成功附顶点/子网格/三角形统计，失败给出原始 PPtr 便于人工定位 */
    private fun dumpMeshRef(sb: StringBuilder, ptr: com.assetstudio.mobile.core.classes.PPtr) {
        sb.appendLine("--- Referensi mesh ---")
        if (ptr.isNull) {
            sb.appendLine("  (Kosong)")
        } else {
            val mesh = ptr.tryGet<Mesh>()
            if (mesh != null) {
                val triangles = mesh.m_SubMeshes.sumOf { (it.indexCount / 3).toLong() }
                sb.appendLine(
                    "  '${mesh.displayName}'${mesh.m_VertexCount} titik / ${mesh.m_SubMeshes.size} submesh / $triangles Segitiga" +
                        "  [fileID=${ptr.m_FileID}, pathID=${ptr.m_PathID}]"
                )
            } else {
                sb.appendLine("  Belum terbaca (fileID=${ptr.m_FileID}, pathID=${ptr.m_PathID}) (menunjuk ke file lain yang belum dimuat)")
            }
        }
        sb.appendLine()
    }

    /** 材质槽段：逐槽解析材质名，未解析给出原始 PPtr */
    private fun dumpMaterialSlots(sb: StringBuilder, materials: Array<com.assetstudio.mobile.core.classes.PPtr>) {
        sb.appendLine("--- Slot material (${materials.size}) ---")
        if (materials.isEmpty()) sb.appendLine("  (Tidak ada)")
        materials.forEachIndexed { i, mat ->
            sb.appendLine("  [$i] ${dumpPtr(mat)}")
        }
    }

    /** PPtr 单行描述：优先解析出对象名 */
    private fun dumpPtr(ptr: com.assetstudio.mobile.core.classes.PPtr): String {
        if (ptr.isNull) return "(Kosong)"
        val target = ptr.tryGetAny()
        return if (target != null) {
            "「${target.displayName}」[fileID=${ptr.m_FileID}, pathID=${ptr.m_PathID}]"
        } else {
            "Belum terbaca (fileID=${ptr.m_FileID}, pathID=${ptr.m_PathID})"
        }
    }

    // ============================ Shader ============================

    fun dumpShader(shader: Shader): String {
        val sb = StringBuilder()
        sb.appendLine("Shader: ${shader.m_Name}")
        sb.appendLine("PathID: ${shader.m_PathID}")

        val parsed = shader.m_ParsedForm
        if (parsed == null) {
            // 5.4 及以下：脚本 + 子程序块
            sb.appendLine("Format: versi lama (Unity 5.4 atau lebih lama)")
            shader.m_Script?.let { sb.appendLine("Ukuran skrip: ${it.size} byte") }
            shader.m_SubProgramBlob?.let { sb.appendLine("Blok subprogram: ${it.size} byte") }
            return sb.toString()
        }

        sb.appendLine()
        sb.appendLine("--- Daftar properti (${parsed.m_PropInfo.m_Props.size}) ---")
        if (parsed.m_PropInfo.m_Props.isEmpty()) sb.appendLine("  (Tidak ada)")
        for (p in parsed.m_PropInfo.m_Props) {
            val def = when (p.m_Type) {
                com.assetstudio.mobile.core.classes.SerializedPropertyType.Texture ->
                    "Tekstur bawaan: ${p.m_DefTexture.m_DefaultName}'(${p.m_DefTexture.m_TexDim.name})"
                else ->
                    "(${p.m_DefValue.joinToString(", ") { fmt(it) }})"
            }
            sb.appendLine("  ${p.m_Type.name.padEnd(7)} ${p.m_Name} = $def")
            if (p.m_Attributes.isNotEmpty()) {
                sb.appendLine("           Properti: ${p.m_Attributes.joinToString(", ")}")
            }
        }

        // 关键词（2021.2+）
        parsed.m_KeywordNames?.let { keywords ->
            sb.appendLine()
            sb.appendLine("--- Kata kunci (${keywords.size}) ---")
            if (keywords.isEmpty()) sb.appendLine("  (Tidak ada)")
            keywords.forEachIndexed { i, kw ->
                val flags = parsed.m_KeywordFlags?.getOrNull(i)?.toInt() ?: 0
                sb.appendLine("  $kw (flags=$flags)")
            }
        }

        // 子着色器 / Pass
        sb.appendLine()
        sb.appendLine("--- Subshader (${parsed.m_SubShaders.size}) ---")
        parsed.m_SubShaders.forEachIndexed { si, sub ->
            sb.appendLine()
            sb.appendLine("[Subshader $si] Jumlah pass: ${sub.m_Passes.size}, LOD ${sub.m_LOD}")
            for ((k, v) in sub.m_Tags.tags) {
                sb.appendLine("  Tag: $k = $v")
            }
            sub.m_Passes.forEachIndexed { pi, pass ->
                val name = pass.m_Name.ifEmpty { pass.m_UseName.ifEmpty { "-" } }
                sb.appendLine("  Pass $pi「$name」type=${pass.m_Type.name} " +
                    "programMask=${pass.m_ProgramMask} instancing=${pass.m_HasInstancingVariant}")
                // 各阶段子程序统计
                sb.appendLine("    Program vertex: ${pass.progVertex.m_SubPrograms.size} · Fragment: ${pass.progFragment.m_SubPrograms.size}" +
                    (pass.progGeometry.m_SubPrograms.takeIf { it.isNotEmpty() }?.let { " · Geometry: ${it.size}" } ?: "") +
                    (pass.progHull.m_SubPrograms.takeIf { it.isNotEmpty() }?.let { " · Hull: ${it.size}" } ?: "") +
                    (pass.progDomain.m_SubPrograms.takeIf { it.isNotEmpty() }?.let { " · Domain: ${it.size}" } ?: ""))
                for ((k, v) in pass.m_Tags.tags) {
                    sb.appendLine("    Tag: $k = $v")
                }
            }
        }

        // 编译平台块
        sb.appendLine()
        sb.appendLine("--- Platform kompilasi (${shader.platforms.size}) ---")
        if (shader.platforms.isEmpty()) sb.appendLine("  (Tidak ada)")
        for ((i, platform) in shader.platforms.withIndex()) {
            val offsets = shader.offsets.getOrNull(i)
            val compLen = shader.compressedLengths.getOrNull(i)
            val decompLen = shader.decompressedLengths.getOrNull(i)
            val variants = offsets?.size ?: 0
            val totalComp = compLen?.sum() ?: 0L
            val totalDecomp = decompLen?.sum() ?: 0L
            sb.appendLine("  ${platform.name}: $variants varian, terkompresi ${fmtBytes(totalComp)}, setelah diekstrak ${fmtBytes(totalDecomp)}")
        }
        shader.compressedBlob?.let {
            sb.appendLine("Blok data terkompresi: ${it.size} byte")
        }

        // 后备/自定义编辑器
        if (parsed.m_FallbackName.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Fallback: ${parsed.m_FallbackName}")
        }
        if (parsed.m_CustomEditorName.isNotEmpty()) {
            sb.appendLine("Editor khusus: ${parsed.m_CustomEditorName}")
        }
        return sb.toString()
    }

    // ============================ 通用 ============================

    private fun fmt(v: Float): String = if (v == v.toLong().toFloat()) {
        v.toLong().toString()
    } else {
        "%.4f".format(v).trimEnd('0').trimEnd('.')
    }

    private fun hex(c: com.assetstudio.mobile.core.math.Color): String {
        fun b(v: Float) = ((v.coerceIn(0f, 1f)) * 255 + 0.5f).toInt()
        return "%02X%02X%02X%02X".format(b(c.r), b(c.g), b(c.b), b(c.a))
    }

    private fun fmtBytes(v: Long): String = when {
        v >= 1 shl 20 -> "%.1f MB".format(v / 1048576.0)
        v >= 1 shl 10 -> "%.1f KB".format(v / 1024.0)
        else -> "$v B"
    }
}
