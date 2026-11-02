package ru.cheburnet.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import ru.cheburnet.GeoPoint
import ru.cheburnet.TargetCatalog
import ru.cheburnet.Verdict
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val RU_YAW = 95f
private const val TILT_DEG = 22.0
private const val ARC_ALTITUDE = 0.22f

private val OceanDeep = Color(0xFF04121F)
private val OceanMid = Color(0xFF0A2E47)
private val OceanLight = Color(0xFF114A6B)
private val LandNeutral = Color(0xFF2E5F80)
private val LandDim = Color(0xFF1A3A4E)

private class LandDot(
    val x: Float,
    val y: Float,
    val z: Float,
    val countryId: Int,
) {
    var revealOrder: Float = 1f
}

private class World(
    val dots: List<LandDot>,
    val codeToId: Map<String, Int>,
    val centroids: Map<String, GeoPoint>,
    val maxCountryId: Int,
)

private data class Vec3(val x: Float, val y: Float, val z: Float)

private fun latLonToVec(lat: Double, lon: Double): Vec3 {
    val latRad = Math.toRadians(lat)
    val lonRad = Math.toRadians(lon)
    return Vec3(
        x = (cos(latRad) * sin(lonRad)).toFloat(),
        y = sin(latRad).toFloat(),
        z = (cos(latRad) * cos(lonRad)).toFloat(),
    )
}

private fun slerp(a: Vec3, b: Vec3, omega: Float, t: Float): Vec3 {
    val so = sin(omega.toDouble()).toFloat()
    if (so < 1e-5f) return a
    val w1 = (sin(((1 - t) * omega).toDouble()) / so).toFloat()
    val w2 = (sin((t * omega).toDouble()) / so).toFloat()
    return Vec3(a.x * w1 + b.x * w2, a.y * w1 + b.y * w2, a.z * w1 + b.z * w2)
}

private fun angularDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return Math.toDegrees(2 * asin(min(1.0, sqrt(a))))
}

private fun loadWorld(context: Context): World? {
    val mapping = runCatching {
        JSONObject(context.assets.open("countries_map.json").use { it.readBytes().decodeToString() })
    }.getOrNull() ?: return null

    val codeToId = HashMap<String, Int>(256)
    val idToCode = HashMap<Int, String>(256)
    var maxId = 0
    for (key in mapping.keys()) {
        val id = key.toIntOrNull() ?: continue
        val code = mapping.optString(key)
        if (code.isNotEmpty()) {
            codeToId[code] = id
            idToCode[id] = code
            if (id > maxId) maxId = id
        }
    }

    val bitmap = runCatching {
        context.assets.open("countries_mask.png").use { BitmapFactory.decodeStream(it) }
    }.getOrNull() ?: return null

    val w = bitmap.width
    val h = bitmap.height
    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
    bitmap.recycle()

    fun countryAt(lat: Double, lon: Double): Int {
        val x = (((lon + 180.0) / 360.0) * w).toInt().coerceIn(0, w - 1)
        val y = (((90.0 - lat) / 180.0) * h).toInt().coerceIn(0, h - 1)
        var id = 0
        for (dy in 0..1) {
            for (dx in 0..1) {
                val px = (x + dx).coerceAtMost(w - 1)
                val py = (y + dy).coerceAtMost(h - 1)
                val value = (pixels[py * w + px] shr 16) and 0xFF
                if (value != 0) id = value
            }
        }
        return id
    }

    // Равномерная спираль Фибоначчи — точки не сбиваются в широтные ряды.
    val count = 45_000
    val goldenAngle = PI * (3.0 - sqrt(5.0))
    val dots = ArrayList<LandDot>(12_000)

    for (i in 0 until count) {
        val y = 1.0 - (i + 0.5) * 2.0 / count
        val lat = Math.toDegrees(asin(y))
        val lon = Math.toDegrees((i * goldenAngle) % (2 * PI)) - 180.0
        val id = countryAt(lat, lon)
        if (id == 0) continue

        val latRad = Math.toRadians(lat)
        val lonRad = Math.toRadians(lon)
        dots += LandDot(
            x = (cos(latRad) * sin(lonRad)).toFloat(),
            y = sin(latRad).toFloat(),
            z = (cos(latRad) * cos(lonRad)).toFloat(),
            countryId = id,
        )
    }

    // Центр страны (для дуг) и порядок «проявления» точек от центра.
    val centroids = HashMap<String, GeoPoint>(256)
    val byCountry = dots.groupBy { it.countryId }
    for ((id, list) in byCountry) {
        var cx = 0.0
        var cy = 0.0
        var cz = 0.0
        for (dot in list) {
            cx += dot.x.toDouble()
            cy += dot.y.toDouble()
            cz += dot.z.toDouble()
        }
        val length = sqrt(cx * cx + cy * cy + cz * cz).coerceAtLeast(0.0001)
        cx /= length; cy /= length; cz /= length

        val centerLat = Math.toDegrees(asin(cy.coerceIn(-1.0, 1.0)))
        val centerLon = Math.toDegrees(kotlin.math.atan2(cx, cz))
        idToCode[id]?.let { centroids[it] = GeoPoint(centerLat, centerLon) }

        val maxDistance = list.maxOf {
            angularDistance(
                Math.toDegrees(asin(it.y.toDouble().coerceIn(-1.0, 1.0))),
                Math.toDegrees(kotlin.math.atan2(it.x.toDouble(), it.z.toDouble())),
                centerLat,
                centerLon,
            )
        }.coerceAtLeast(0.5)

        list.forEach {
            val lat = Math.toDegrees(asin(it.y.toDouble().coerceIn(-1.0, 1.0)))
            val lon = Math.toDegrees(kotlin.math.atan2(it.x.toDouble(), it.z.toDouble()))
            it.revealOrder = (angularDistance(lat, lon, centerLat, centerLon) / maxDistance)
                .toFloat()
                .coerceIn(0f, 1f)
        }
    }

    return World(dots = dots, codeToId = codeToId, centroids = centroids, maxCountryId = maxId)
}

@Composable
fun Globe(
    verdict: Verdict,
    litCountries: Set<String>,
    arcCountries: Set<String>,
    user: GeoPoint?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val world = remember { loadWorld(context) }

    var yaw by remember { mutableFloatStateOf(60f) }
    var tick by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var lastTouch by remember { mutableLongStateOf(0L) }
    var chebUserRotated by remember { mutableStateOf(false) }
    var focusYaw by remember { mutableFloatStateOf(0f) }
    var focusUntil by remember { mutableLongStateOf(0L) }

    val progress = remember { FloatArray((world?.maxCountryId ?: 0) + 1) }
    val time = remember { FloatArray(1) }
    val arcFade = remember { FloatArray(1) { 1f } }

    val currentVerdict by rememberUpdatedState(verdict)
    val currentLit by rememberUpdatedState(litCountries)
    val currentUser by rememberUpdatedState(user)
    val currentArcs by rememberUpdatedState(arcCountries)

    // Как только появились результаты и известна точка «вы» — доворачиваем к ней,
    // чтобы лучи было видно сразу, и через 5 секунд возвращаем вращение.
    LaunchedEffect(user, verdict) {
        val point = user ?: return@LaunchedEffect
        if (verdict != Verdict.IDLE && verdict != Verdict.OFFLINE && verdict != Verdict.CHEBURNET &&
            verdict != Verdict.WHITELIST_ONLY
        ) {
            focusYaw = point.lon.toFloat()
            focusUntil = System.nanoTime() + 5_000_000_000L
        }
    }

    LaunchedEffect(Unit) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (lastNanos == 0L) 0f else ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                lastNanos = now
                var changed = false
                val cheburnet = currentVerdict == Verdict.CHEBURNET
                val lockToRussia = cheburnet || currentVerdict == Verdict.WHITELIST_ONLY
                val noFill = currentVerdict == Verdict.WHITELIST_ONLY
                val allLit = currentVerdict == Verdict.OPEN
                val litIds = if (world == null) emptySet() else {
                    currentLit.mapNotNull { world.codeToId[it] }.toSet()
                }

                if (world != null) {
                    for (id in 1..world.maxCountryId) {
                        val target = if (!noFill && (allLit || id in litIds)) 1f else 0f
                        val value = progress[id]
                        if (abs(value - target) > 0.002f) {
                            progress[id] = if (value < target) min(target, value + dt * 0.9f) else max(target, value - dt * 0.9f)
                            changed = true
                        }
                    }
                }

                // После проверки лучи плавно гаснут за ~2,5 секунды.
                val checking = currentVerdict == Verdict.CHECKING
                if (checking && arcFade[0] < 1f) {
                    arcFade[0] = min(1f, arcFade[0] + dt * 6f)
                    changed = true
                } else if (!checking && arcFade[0] > 0f) {
                    arcFade[0] = max(0f, arcFade[0] - dt / 2.5f)
                    changed = true
                }

                val arcsVisible = currentUser != null &&
                    currentVerdict != Verdict.IDLE && currentVerdict != Verdict.OFFLINE &&
                    arcFade[0] > 0.01f &&
                    (currentArcs.isNotEmpty() || currentVerdict == Verdict.CHEBURNET ||
                        currentVerdict == Verdict.WHITELIST_ONLY)
                if (arcsVisible) {
                    time[0] = (time[0] + dt) % 1000f
                    changed = true
                }

                val idle = now - lastTouch > 2_000_000_000L
                val focusing = now < focusUntil && currentUser != null && !cheburnet
                if (!dragging && idle) {
                    if (focusing) {
                        if (abs(angleDelta(yaw, focusYaw)) > 0.15f) {
                            yaw = approach(yaw, focusYaw, dt)
                            changed = true
                        }
                    } else if (lockToRussia && !chebUserRotated) {
                        if (abs(angleDelta(yaw, RU_YAW)) > 0.15f) {
                            yaw = approach(yaw, RU_YAW, dt)
                            changed = true
                        }
                    } else if (!lockToRussia) {
                        yaw = (yaw + 6.5f * dt + 360f) % 360f
                        changed = true
                    }
                }

                if (changed) tick++
            }
        }
    }

    val redrawKey = tick
    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        dragging = true
                        lastTouch = System.nanoTime()
                    },
                    onDragEnd = {
                        dragging = false
                        lastTouch = System.nanoTime()
                    },
                    onDragCancel = {
                        dragging = false
                        lastTouch = System.nanoTime()
                    },
                ) { change, dragAmount ->
                    change.consume()
                    lastTouch = System.nanoTime()
                    if (verdict == Verdict.CHEBURNET || verdict == Verdict.WHITELIST_ONLY) chebUserRotated = true
                    yaw = (yaw - dragAmount.x / size.width * 220f + 720f) % 360f
                    tick++
                }
            },
    ) {
        drawGlobe(
            world = world,
            yawDeg = yaw,
            verdict = currentVerdict,
            litCountries = currentLit,
            arcCountries = arcCountries,
            user = currentUser,
            progress = progress,
            time = time[0],
            arcFade = arcFade[0],
            redrawKey = redrawKey,
        )
    }
}

private fun angleDelta(from: Float, to: Float): Float =
    ((to - from + 540f) % 360f) - 180f

private fun approach(current: Float, target: Float, dt: Float): Float {
    val delta = angleDelta(current, target)
    val factor = 1f - exp(-dt * 3.2f)
    return (current + delta * factor + 360f) % 360f
}

private fun DrawScope.drawGlobe(
    world: World?,
    yawDeg: Float,
    verdict: Verdict,
    litCountries: Set<String>,
    arcCountries: Set<String>,
    user: GeoPoint?,
    progress: FloatArray,
    time: Float,
    arcFade: Float,
    redrawKey: Int,
) {
    if (redrawKey < 0 || world == null) return

    val centerX = size.width / 2f
    val centerY = size.height * 0.40f
    val radius = min(size.width, size.height) * 0.44f
    val center = Offset(centerX, centerY)

    val cheburnet = verdict == Verdict.CHEBURNET
    val whitelistOnly = verdict == Verdict.WHITELIST_ONLY
    val allLit = verdict == Verdict.OPEN
    val dim = verdict == Verdict.IDLE || verdict == Verdict.OFFLINE

    val litIds = litCountries.mapNotNull { world.codeToId[it] }.toSet()
    val whiteIds = if (cheburnet) setOfNotNull(world.codeToId["RUS"]) else emptySet()
    val glowNeeded = !allLit && litIds.size in 1..12

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(OceanLight.copy(alpha = 0.55f), Color.Transparent),
            center = center,
            radius = radius * 1.22f,
        ),
        radius = radius * 1.22f,
        center = center,
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(OceanLight, OceanMid, OceanDeep),
            center = Offset(centerX - radius * 0.28f, centerY - radius * 0.32f),
            radius = radius * 1.65f,
        ),
        radius = radius,
        center = center,
    )
    drawCircle(
        color = Color(0xFF3E86AE).copy(alpha = 0.55f),
        radius = radius,
        center = center,
        style = Stroke(width = 1.4.dp.toPx()),
    )

    val yawRad = Math.toRadians(yawDeg.toDouble())
    val cy = cos(yawRad).toFloat()
    val sy = sin(yawRad).toFloat()
    val tiltRad = Math.toRadians(TILT_DEG)
    val ct = cos(tiltRad).toFloat()
    val st = sin(tiltRad).toFloat()

    val neutral = ArrayList<Offset>(12000)
    val lit = ArrayList<Offset>(12000)
    val white = ArrayList<Offset>(4000)

    for (dot in world.dots) {
        val x1 = dot.x * cy - dot.z * sy
        val z1 = dot.x * sy + dot.z * cy
        val y1 = dot.y * ct - z1 * st
        val z2 = dot.y * st + z1 * ct
        if (z2 <= 0.02f) continue

        val point = Offset(centerX + x1 * radius, centerY - y1 * radius)
        val reached = progress[dot.countryId] >= dot.revealOrder
        val isWhite = cheburnet && dot.countryId in whiteIds && reached
        val isLit = !whitelistOnly && !isWhite &&
            (allLit || dot.countryId in litIds) && reached

        when {
            isWhite -> white += point
            isLit -> lit += point
            else -> neutral += point
        }
    }

    drawPoints(
        neutral,
        PointMode.Points,
        if (dim) LandDim else LandNeutral,
        2.8.dp.toPx(),
        StrokeCap.Round,
    )

    if (lit.isNotEmpty()) {
        if (glowNeeded) {
            drawPoints(lit, PointMode.Points, AccentCyan.copy(alpha = 0.18f), 9.dp.toPx(), StrokeCap.Round)
        }
        drawPoints(lit, PointMode.Points, AccentCyan, 2.8.dp.toPx(), StrokeCap.Round)
    }
    if (white.isNotEmpty()) {
        drawPoints(white, PointMode.Points, Color.White.copy(alpha = 0.25f), 9.dp.toPx(), StrokeCap.Round)
        drawPoints(white, PointMode.Points, IceWhite, 2.8.dp.toPx(), StrokeCap.Round)
    }

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.06f), Color.Transparent),
            center = Offset(centerX - radius * 0.45f, centerY - radius * 0.5f),
            radius = radius * 0.9f,
        ),
        radius = radius,
        center = center,
    )

    if (user != null) {
        // Лучи — к странам, куда прошёл пинг (в чебурнете — к городам),
        // после проверки плавно гаснут.
        if (!dim && arcFade > 0.01f && (arcCountries.isNotEmpty() || cheburnet || whitelistOnly)) {
            val destinations = if (cheburnet || whitelistOnly) {
                TargetCatalog.cheburnetCities.map { GeoPoint(it.lat, it.lon) }
            } else {
                arcCountries.mapNotNull { world.centroids[it] }.distinct()
            }
            drawArcs(
                user = user,
                destinations = destinations,
                centerX = centerX,
                centerY = centerY,
                radius = radius,
                cy = cy,
                sy = sy,
                ct = ct,
                st = st,
                time = time,
                alpha = arcFade,
            )
        }
        drawUserMarker(user, centerX, centerY, radius, cy, sy, ct, st, time)
    }

    if (cheburnet || whitelistOnly) {
        drawCities(centerX, centerY, radius, cy, sy, ct, st)
    }
}

private fun projectPoint(
    vec: Vec3,
    lift: Float,
    centerX: Float,
    centerY: Float,
    radius: Float,
    cy: Float,
    sy: Float,
    ct: Float,
    st: Float,
): Pair<Offset, Float> {
    val k = 1f + lift
    val px = vec.x * k
    val py = vec.y * k
    val pz = vec.z * k

    val x1 = px * cy - pz * sy
    val z1 = px * sy + pz * cy
    val y1 = py * ct - z1 * st
    val z2 = py * st + z1 * ct

    return Offset(centerX + x1 * radius, centerY - y1 * radius) to z2
}

private fun DrawScope.drawArcs(
    user: GeoPoint,
    destinations: List<GeoPoint>,
    centerX: Float,
    centerY: Float,
    radius: Float,
    cy: Float,
    sy: Float,
    ct: Float,
    st: Float,
    time: Float,
    alpha: Float,
) {
    val from = latLonToVec(user.lat, user.lon)

    // Лучи рисуем только когда точка «вы» на видимой стороне глобуса.
    val (_, userZ) = projectPoint(from, 0f, centerX, centerY, radius, cy, sy, ct, st)
    if (userZ <= 0.05f) return

    val segments = 40

    destinations.forEachIndexed { index, destination ->
        val distance = angularDistance(user.lat, user.lon, destination.lat, destination.lon)
        if (distance < 2.0) return@forEachIndexed

        val to = latLonToVec(destination.lat, destination.lon)
        val dot = (from.x * to.x + from.y * to.y + from.z * to.z).coerceIn(-1f, 1f)
        val omega = acos(dot.toDouble()).toFloat()
        if (omega < 0.03f) return@forEachIndexed

        // Полная дуга — еле заметная.
        val faint = Path()
        var started = false
        for (i in 0..segments) {
            val t = i / segments.toFloat()
            val p = slerp(from, to, omega, t)
            val lift = (sin(PI * t) * ARC_ALTITUDE).toFloat()
            val (point, z) = projectPoint(p, lift, centerX, centerY, radius, cy, sy, ct, st)
            if (z > 0f) {
                if (started) faint.lineTo(point.x, point.y) else faint.moveTo(point.x, point.y)
                started = true
            } else {
                started = false
            }
        }
        drawPath(faint, AccentCyan.copy(alpha = 0.16f * alpha), style = Stroke(1.3.dp.toPx(), cap = StrokeCap.Round))

        // Бегущая яркая «пуля» с хвостом.
        val phase = ((time * 0.35f + index * 0.11f) % 1f)
        val tailStart = (phase - 0.28f).coerceAtLeast(0f)
        val bright = Path()
        var brightStarted = false
        val tailSegments = 14
        for (i in 0..tailSegments) {
            val t = tailStart + (phase - tailStart) * i / tailSegments
            val p = slerp(from, to, omega, t)
            val lift = (sin(PI * t) * ARC_ALTITUDE).toFloat()
            val (point, z) = projectPoint(p, lift, centerX, centerY, radius, cy, sy, ct, st)
            if (z > 0f) {
                if (brightStarted) bright.lineTo(point.x, point.y) else bright.moveTo(point.x, point.y)
                brightStarted = true
            } else {
                brightStarted = false
            }
        }
        drawPath(bright, AccentCyan.copy(alpha = 0.9f * alpha), style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))

        val head = slerp(from, to, omega, phase)
        val headLift = (sin(PI * phase) * ARC_ALTITUDE).toFloat()
        val (headPoint, headZ) = projectPoint(head, headLift, centerX, centerY, radius, cy, sy, ct, st)
        if (headZ > 0f) {
            drawCircle(AccentCyan.copy(alpha = 0.35f * alpha), 7.dp.toPx(), headPoint)
            drawCircle(Color.White.copy(alpha = alpha), 2.4.dp.toPx(), headPoint)
        }
    }
}

private fun DrawScope.drawUserMarker(
    user: GeoPoint,
    centerX: Float,
    centerY: Float,
    radius: Float,
    cy: Float,
    sy: Float,
    ct: Float,
    st: Float,
    time: Float,
) {
    val vec = latLonToVec(user.lat, user.lon)
    val (point, z) = projectPoint(vec, 0f, centerX, centerY, radius, cy, sy, ct, st)
    if (z <= 0f) return

    val pulse = 0.5f + 0.5f * sin((time * 2.6f).toDouble()).toFloat()
    drawCircle(AccentCyan.copy(alpha = 0.12f + 0.12f * pulse), (16.dp + 5.dp * pulse).toPx(), point)
    drawCircle(AccentCyan.copy(alpha = 0.55f), 6.dp.toPx(), point)
    drawCircle(Color.White, 2.6.dp.toPx(), point)
}

private fun DrawScope.drawCities(
    centerX: Float,
    centerY: Float,
    radius: Float,
    cy: Float,
    sy: Float,
    ct: Float,
    st: Float,
) {
    for (city in TargetCatalog.cheburnetCities) {
        val vec = latLonToVec(city.lat, city.lon)
        val (point, z) = projectPoint(vec, 0f, centerX, centerY, radius, cy, sy, ct, st)
        if (z <= 0.05f) continue

        drawCircle(AccentCyan.copy(alpha = 0.28f), radius = 18.dp.toPx(), center = point)
        drawCircle(AccentCyan.copy(alpha = 0.60f), radius = 9.dp.toPx(), center = point)
        drawCircle(Color.White, radius = 4.dp.toPx(), center = point)
    }
}
