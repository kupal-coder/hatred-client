package com.project.lumina.client.game.module.impl.visual

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.project.lumina.client.constructors.CheatCategory
import com.project.lumina.client.constructors.Element
import com.project.lumina.client.game.InterceptablePacket
import com.project.lumina.client.render.ESPRenderOverlayView
import com.project.lumina.client.util.AssetManager
import org.cloudburstmc.math.matrix.Matrix4f
import org.cloudburstmc.math.vector.Vector2f
import org.cloudburstmc.math.vector.Vector3f
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

class OreESPElement(
    iconResId: Int = AssetManager.getAsset("ic_diamond_stone_black_24dp")
) : Element(
    name = "OreESP",
    category = CheatCategory.Visual,
    iconResId,
    displayNameResId = AssetManager.getString("module_oreesp_display_name")
) {

    private var showCoal by boolValue("Coal", true)
    private var showIron by boolValue("Iron", true)
    private var showCopper by boolValue("Copper", true)
    private var showGold by boolValue("Gold", true)
    private var showRedstone by boolValue("Redstone", true)
    private var showLapis by boolValue("Lapis", true)
    private var showDiamond by boolValue("Diamond", true)
    private var showEmerald by boolValue("Emerald", true)
    private var showQuartz by boolValue("Quartz", true)
    private var showDebris by boolValue("Debris", true)

    private var colorCoal by colorValue("Coal Color", "#9E9E9E")
    private var colorIron by colorValue("Iron Color", "#D8A077")
    private var colorCopper by colorValue("Copper Color", "#E07B39")
    private var colorGold by colorValue("Gold Color", "#FFD700")
    private var colorRedstone by colorValue("Redstone Color", "#FF0000")
    private var colorLapis by colorValue("Lapis Color", "#3B5BFF")
    private var colorDiamond by colorValue("Diamond Color", "#4DEEEA")
    private var colorEmerald by colorValue("Emerald Color", "#00C853")
    private var colorQuartz by colorValue("Quartz Color", "#FFFFFF")
    private var colorDebris by colorValue("Debris Color", "#8D5A3B")

    private var radius by intValue("Radius", 24, 8..32)
    private var maxOres by intValue("Max Ores", 96, 16..256)
    private var scanInterval by intValue("Scan Interval", 1000, 250..2000)
    private var fov by floatValue("FOV", 110f, 40f..110f)
    private var strokeWidth by floatValue("Stroke Width", 2f, 1f..10f)
    private var cornerRadius by floatValue("Corner Radius", 4f, 0f..20f)
    private var showDistance by boolValue("Show Distance", true)
    private var showNames by boolValue("Show Names", true)

    private data class OreHit(
        val x: Int,
        val y: Int,
        val z: Int,
        val label: String,
        val color: Int
    )

    @Volatile
    private var cachedOres: List<OreHit> = emptyList()
    private val scanning = AtomicBoolean(false)
    private var lastScanTime = 0L
    private var lastScanPos = Triple(Int.MIN_VALUE, Int.MIN_VALUE, Int.MIN_VALUE)

    override fun onEnabled() {
        super.onEnabled()
        if (isSessionCreated) {
            ESPRenderOverlayView.getOrShow()
            lastScanTime = 0L
        }
    }

    override fun onDisabled() {
        super.onDisabled()
        cachedOres = emptyList()
        ESPRenderOverlayView.dismissIfUnused()
    }

    override fun onDisconnect(reason: String) {
        cachedOres = emptyList()
        lastScanTime = 0L
        lastScanPos = Triple(Int.MIN_VALUE, Int.MIN_VALUE, Int.MIN_VALUE)
    }

    override fun beforePacketBound(interceptablePacket: InterceptablePacket) {
        if (!isEnabled || !isSessionCreated) return
        if (interceptablePacket.packet !is PlayerAuthInputPacket) return
        maybeRescan()
    }

    private fun maybeRescan() {
        if (scanning.get()) return
        val now = System.currentTimeMillis()
        if (now - lastScanTime < scanInterval) return

        val player = session.localPlayer
        val px = floor(player.posX).toInt()
        val py = floor(player.posY).toInt()
        val pz = floor(player.posZ).toInt()

        val (lx, ly, lz) = lastScanPos
        val dx = px.toLong() - lx
        val dy = py.toLong() - ly
        val dz = pz.toLong() - lz
        if (cachedOres.isNotEmpty() && dx * dx + dy * dy + dz * dz < 4L) return

        if (!scanning.compareAndSet(false, true)) return
        lastScanTime = now
        lastScanPos = Triple(px, py, pz)

        val scanRadius = radius
        val scanMax = maxOres
        val enabled = enabledOreKeys()
        val colors = oreColors()
        val world = session.world
        val mapping = session.blockMapping

        thread(start = true, isDaemon = true, name = "OreESP-Scan") {
            try {
                val found = ArrayList<OreHit>(scanMax)
                outer@ for (dx in -scanRadius..scanRadius) {
                    for (dy in -scanRadius..scanRadius) {
                        for (dz in -scanRadius..scanRadius) {
                            val runtimeId = world.getBlockId(px + dx, py + dy, pz + dz)
                            if (runtimeId == 0) continue
                            val key = normalizeOreKey(mapping.getDefinition(runtimeId).identifier)
                                ?: continue
                            if (key !in enabled) continue
                            found.add(
                                OreHit(
                                    px + dx, py + dy, pz + dz,
                                    ORE_LABELS[key] ?: key,
                                    colors[key] ?: Color.WHITE
                                )
                            )
                            if (found.size >= scanMax * 2) break@outer
                        }
                    }
                }
                found.sortBy { (it.x - px) * (it.x - px) + (it.y - py) * (it.y - py) + (it.z - pz) * (it.z - pz) }
                cachedOres = if (found.size > scanMax) found.take(scanMax) else found
            } catch (_: Throwable) {
            } finally {
                scanning.set(false)
            }
        }
    }

    private fun enabledOreKeys(): Set<String> {
        val enabled = HashSet<String>()
        if (showCoal) enabled.add("coal")
        if (showIron) enabled.add("iron")
        if (showCopper) enabled.add("copper")
        if (showGold) enabled.add("gold")
        if (showRedstone) enabled.add("redstone")
        if (showLapis) enabled.add("lapis")
        if (showDiamond) enabled.add("diamond")
        if (showEmerald) enabled.add("emerald")
        if (showQuartz) enabled.add("quartz")
        if (showDebris) enabled.add("debris")
        return enabled
    }

    private fun oreColors(): Map<String, Int> {
        fun parse(hex: String): Int {
            return try {
                val clean = hex.removePrefix("#")
                when (clean.length) {
                    6 -> Color.parseColor("#FF$clean")
                    8 -> Color.parseColor("#$clean")
                    else -> Color.WHITE
                }
            } catch (_: Exception) {
                Color.WHITE
            }
        }
        return mapOf(
            "coal" to parse(colorCoal),
            "iron" to parse(colorIron),
            "copper" to parse(colorCopper),
            "gold" to parse(colorGold),
            "redstone" to parse(colorRedstone),
            "lapis" to parse(colorLapis),
            "diamond" to parse(colorDiamond),
            "emerald" to parse(colorEmerald),
            "quartz" to parse(colorQuartz),
            "debris" to parse(colorDebris)
        )
    }

    private fun normalizeOreKey(identifier: String): String? {
        var name = identifier.removePrefix("minecraft:")
        if (name.startsWith("deepslate_")) name = name.removePrefix("deepslate_")
        if (name.startsWith("lit_")) name = name.removePrefix("lit_")
        return when (name) {
            "coal_ore" -> "coal"
            "iron_ore" -> "iron"
            "copper_ore" -> "copper"
            "gold_ore", "nether_gold_ore" -> "gold"
            "redstone_ore" -> "redstone"
            "lapis_ore" -> "lapis"
            "diamond_ore" -> "diamond"
            "emerald_ore" -> "emerald"
            "quartz_ore", "nether_quartz_ore" -> "quartz"
            "ancient_debris" -> "debris"
            else -> null
        }
    }

    private fun rotateX(angle: Float): Matrix4f {
        val rad = Math.toRadians(angle.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()

        return Matrix4f.from(
            1f, 0f, 0f, 0f,
            0f, c, -s, 0f,
            0f, s, c, 0f,
            0f, 0f, 0f, 1f
        )
    }

    private fun rotateY(angle: Float): Matrix4f {
        val rad = Math.toRadians(angle.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()

        return Matrix4f.from(
            c, 0f, s, 0f,
            0f, 1f, 0f, 0f,
            -s, 0f, c, 0f,
            0f, 0f, 0f, 1f
        )
    }

    private fun worldToScreen(pos: Vector3f, viewProjMatrix: Matrix4f, screenWidth: Int, screenHeight: Int): Vector2f? {
        val w = viewProjMatrix.get(3, 0) * pos.x +
                viewProjMatrix.get(3, 1) * pos.y +
                viewProjMatrix.get(3, 2) * pos.z +
                viewProjMatrix.get(3, 3)

        if (w < 0.01f) return null

        val inverseW = 1f / w

        val screenX = screenWidth / 2f + (0.5f * ((viewProjMatrix.get(0, 0) * pos.x +
                viewProjMatrix.get(0, 1) * pos.y +
                viewProjMatrix.get(0, 2) * pos.z +
                viewProjMatrix.get(0, 3)) * inverseW) * screenWidth + 0.5f)

        val screenY = screenHeight / 2f - (0.5f * ((viewProjMatrix.get(1, 0) * pos.x +
                viewProjMatrix.get(1, 1) * pos.y +
                viewProjMatrix.get(1, 2) * pos.z +
                viewProjMatrix.get(1, 3)) * inverseW) * screenHeight + 0.5f)

        return Vector2f.from(screenX, screenY)
    }

    private fun boxPaint(color: Int): Paint {
        return Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = this@OreESPElement.strokeWidth
            isAntiAlias = true
            isDither = true
            pathEffect = CornerPathEffect(cornerRadius)
            this.color = color
        }
    }

    private fun textPaint(color: Int): Paint {
        return Paint().apply {
            style = Paint.Style.FILL
            textSize = 32f
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            this.color = color
        }
    }

    private fun textOutlinePaint(): Paint {
        return Paint().apply {
            style = Paint.Style.STROKE
            color = Color.BLACK
            textSize = 32f
            strokeWidth = 3f
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
    }

    fun render(canvas: Canvas) {
        if (!isEnabled || !isSessionCreated) return
        val ores = cachedOres
        if (ores.isEmpty()) return

        val player = session.localPlayer
        val screenWidth = canvas.width
        val screenHeight = canvas.height

        val viewProjMatrix = Matrix4f.createPerspective(
            fov,
            screenWidth.toFloat() / screenHeight, 0.1f, 256f
        )
            .mul(
                Matrix4f.createTranslation(player.vec3Position)
                    .mul(rotateY(-player.rotationYaw - 180))
                    .mul(rotateX(-player.rotationPitch))
                    .invert()
            )

        val outlinePaint = textOutlinePaint()

        ores.forEach { ore ->
            val paint = boxPaint(ore.color)
            drawOreBox(ore, viewProjMatrix, screenWidth, screenHeight, canvas, paint)
            if (showNames || showDistance) {
                drawOreInfo(ore, viewProjMatrix, screenWidth, screenHeight, canvas, textPaint(ore.color), outlinePaint)
            }
        }
    }

    private fun oreVertices(ore: OreHit): Array<Vector3f> {
        val x = ore.x.toFloat()
        val y = ore.y.toFloat()
        val z = ore.z.toFloat()
        return arrayOf(
            Vector3f.from(x, y, z),
            Vector3f.from(x, y + 1f, z),
            Vector3f.from(x + 1f, y + 1f, z),
            Vector3f.from(x + 1f, y, z),
            Vector3f.from(x, y, z + 1f),
            Vector3f.from(x, y + 1f, z + 1f),
            Vector3f.from(x + 1f, y + 1f, z + 1f),
            Vector3f.from(x + 1f, y, z + 1f)
        )
    }

    private fun drawOreBox(
        ore: OreHit,
        viewProjMatrix: Matrix4f,
        screenWidth: Int,
        screenHeight: Int,
        canvas: Canvas,
        paint: Paint
    ) {
        var minX = screenWidth.toDouble()
        var minY = screenHeight.toDouble()
        var maxX = 0.0
        var maxY = 0.0
        val screenPositions = mutableListOf<Vector2f>()

        oreVertices(ore).forEach { vertex ->
            val screenPos = worldToScreen(vertex, viewProjMatrix, screenWidth, screenHeight)
                ?: return@forEach
            screenPositions.add(screenPos)
            minX = minX.coerceAtMost(screenPos.x.toDouble())
            minY = minY.coerceAtMost(screenPos.y.toDouble())
            maxX = maxX.coerceAtLeast(screenPos.x.toDouble())
            maxY = maxY.coerceAtLeast(screenPos.y.toDouble())
        }

        if (minX >= screenWidth || minY >= screenHeight || maxX <= 0 || maxY <= 0) return
        if (screenPositions.size < 8) return

        val edges = listOf(
            0 to 1, 1 to 2, 2 to 3, 3 to 0,
            4 to 5, 5 to 6, 6 to 7, 7 to 4,
            0 to 4, 1 to 5, 2 to 6, 3 to 7
        )

        edges.forEach { (start, end) ->
            val startPos = screenPositions[start]
            val endPos = screenPositions[end]
            if (isOnScreen(startPos, canvas) && isOnScreen(endPos, canvas)) {
                val padding = paint.strokeWidth / 2
                canvas.drawLine(
                    startPos.x.coerceIn(padding, canvas.width - padding),
                    startPos.y.coerceIn(padding, canvas.height - padding),
                    endPos.x.coerceIn(padding, canvas.width - padding),
                    endPos.y.coerceIn(padding, canvas.height - padding),
                    paint
                )
            }
        }
    }

    private fun isOnScreen(pos: Vector2f, canvas: Canvas): Boolean {
        return pos.x >= 0 && pos.x <= canvas.width &&
                pos.y >= 0 && pos.y <= canvas.height
    }

    private fun drawOreInfo(
        ore: OreHit,
        viewProjMatrix: Matrix4f,
        screenWidth: Int,
        screenHeight: Int,
        canvas: Canvas,
        textPaint: Paint,
        outlinePaint: Paint
    ) {
        val topCenter = worldToScreen(
            Vector3f.from(ore.x + 0.5f, ore.y + 1f, ore.z + 0.5f),
            viewProjMatrix, screenWidth, screenHeight
        ) ?: return
        if (!isOnScreen(topCenter, canvas)) return

        val info = buildString {
            if (showNames) append(ore.label)
            if (showDistance) {
                if (isNotEmpty()) append(" | ")
                val player = session.localPlayer
                val dx = ore.x + 0.5f - player.posX
                val dy = ore.y + 0.5f - player.posY
                val dz = ore.z + 0.5f - player.posZ
                val distance = kotlin.math.sqrt((dx * dx + dy * dy + dz * dz).toDouble())
                append("%.1fm".format(distance))
            }
        }
        if (info.isEmpty()) return

        val textX = topCenter.x
        val textY = topCenter.y - 10

        val bounds = android.graphics.Rect()
        textPaint.getTextBounds(info, 0, info.length, bounds)

        val bgPaint = Paint().apply {
            color = Color.argb(180, 0, 0, 0)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val padding = 12f
        val bgRect = RectF(
            textX - bounds.width() / 2 - padding,
            textY - bounds.height() - padding,
            textX + bounds.width() / 2 + padding,
            textY + padding
        )
        canvas.drawRoundRect(bgRect, cornerRadius * 2, cornerRadius * 2, bgPaint)

        val shadowPaint = Paint().apply {
            style = Paint.Style.FILL
            color = Color.argb(100, 0, 0, 0)
            textSize = 32f
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        canvas.drawText(info, textX + 2, textY + 2, shadowPaint)
        canvas.drawText(info, textX, textY, outlinePaint)
        canvas.drawText(info, textX, textY, textPaint)
    }

    companion object {
        private val ORE_LABELS = mapOf(
            "coal" to "Coal",
            "iron" to "Iron",
            "copper" to "Copper",
            "gold" to "Gold",
            "redstone" to "Redstone",
            "lapis" to "Lapis",
            "diamond" to "Diamond",
            "emerald" to "Emerald",
            "quartz" to "Quartz",
            "debris" to "Debris"
        )
    }
}
