package io.embodit.knots.data

import android.content.Context
import io.embodit.knots.domain.model.CurrentKnot
import io.embodit.knots.domain.model.Hand
import io.embodit.knots.domain.model.KnotRecord
import io.embodit.knots.domain.model.LanguagePref
import io.embodit.knots.domain.model.Progress
import io.embodit.knots.domain.model.RopeHeight
import io.embodit.knots.domain.model.SavedPose
import io.embodit.knots.domain.model.Settings
import org.json.JSONObject

/** What the app remembers between launches (design `dataModel`): progress and settings. Offline, no account. */
interface KnotRepository {
    fun loadSettings(defaultDwell: Boolean): Settings

    fun saveSettings(settings: Settings)

    fun loadProgress(): Progress

    fun saveProgress(progress: Progress)
}

/** SharedPreferences-backed store: two small JSON documents (a few hundred bytes). */
class PrefsKnotRepository(context: Context) : KnotRepository {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun loadSettings(defaultDwell: Boolean): Settings {
        val text = prefs.getString(KEY_SETTINGS, null) ?: return Settings(dwell = defaultDwell)
        return runCatching {
            val o = JSONObject(text)
            Settings(
                language = LanguagePref.entries.firstOrNull { it.name == o.optString("lang") } ?: LanguagePref.SYSTEM,
                hand = Hand.entries.firstOrNull { it.name == o.optString("hand") } ?: Hand.RIGHT,
                hasRope = o.optBoolean("hasRope", true),
                ropeMm = o.optInt("ropeMm", 8).takeIf { it in listOf(4, 8, 11) } ?: 8,
                height = RopeHeight.byId(o.optString("height")) ?: RopeHeight.NORMAL,
                speed = o.optDouble("speed", 1.0).toFloat().takeIf { it in listOf(0.5f, 1f, 1.5f) } ?: 1f,
                dwell = o.optBoolean("dwell", defaultDwell),
                welcomeSeen = o.optBoolean("welcomeSeen", false),
            )
        }.getOrDefault(Settings(dwell = defaultDwell))
    }

    override fun saveSettings(settings: Settings) {
        val o = JSONObject()
        o.put("lang", settings.language.name)
        o.put("hand", settings.hand.name)
        o.put("hasRope", settings.hasRope)
        o.put("ropeMm", settings.ropeMm)
        o.put("height", settings.height.id)
        o.put("speed", settings.speed.toDouble())
        o.put("dwell", settings.dwell)
        o.put("welcomeSeen", settings.welcomeSeen)
        prefs.edit().putString(KEY_SETTINGS, o.toString()).apply()
    }

    override fun loadProgress(): Progress {
        val text = prefs.getString(KEY_PROGRESS, null) ?: return Progress()
        return runCatching {
            val o = JSONObject(text)
            val recs = LinkedHashMap<String, KnotRecord>()
            val r = o.optJSONObject("records") ?: JSONObject()
            for (id in r.keys()) {
                val e = r.getJSONObject(id)
                recs[id] = KnotRecord(e.optBoolean("learned"), e.optLong("learnedAt"), e.optInt("times"))
            }
            val cur = o.optJSONObject("current")?.let { CurrentKnot(it.getString("knotId"), it.getInt("step")) }
            val pose = o.optJSONObject("ropePose")?.let { SavedPose(it.getDouble("x").toFloat(), it.getDouble("y").toFloat(), it.getDouble("z").toFloat(), it.getDouble("yaw").toFloat()) }
            Progress(recs, cur, pose)
        }.getOrDefault(Progress())
    }

    override fun saveProgress(progress: Progress) {
        val o = JSONObject()
        val r = JSONObject()
        for ((id, e) in progress.records) r.put(id, JSONObject().put("learned", e.learned).put("learnedAt", e.learnedAt).put("times", e.times))
        o.put("records", r)
        progress.current?.let { c -> o.put("current", JSONObject().put("knotId", c.knotId).put("step", c.step)) }
        progress.ropePose?.let { p -> o.put("ropePose", JSONObject().put("x", p.x.toDouble()).put("y", p.y.toDouble()).put("z", p.z.toDouble()).put("yaw", p.yaw.toDouble())) }
        prefs.edit().putString(KEY_PROGRESS, o.toString()).apply()
    }

    private companion object {
        const val FILE = "knots"
        const val KEY_SETTINGS = "settings_v1"
        const val KEY_PROGRESS = "progress_v1"
    }
}
