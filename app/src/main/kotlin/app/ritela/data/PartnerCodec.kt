package app.ritela.data

import app.ritela.domain.Doodle
import app.ritela.domain.DoodleOpening
import app.ritela.domain.DoodlePoint
import app.ritela.domain.DoodleStroke
import app.ritela.domain.PartnerContact
import app.ritela.domain.PartnerDevice
import app.ritela.domain.PartnerDirectory
import app.ritela.domain.PartnerIdentity
import app.ritela.domain.ShareCategory
import app.ritela.domain.ShareScope
import org.json.JSONArray
import org.json.JSONObject

object PartnerCodec {
    fun scope(scope: ShareScope): JSONObject =
        JSONObject().put("categories", JSONArray(scope.categories.map { it.name }))
            .put("sections", JSONArray(scope.sections.toList())).put(
                "tags",
                JSONObject().apply {
                    scope.tags.forEach { (id, tags) -> put(id, JSONArray(tags.toList())) }
                }
            )
    fun scope(json: JSONObject): ShareScope = ShareScope(
        strings(json.getJSONArray("categories")).map(ShareCategory::valueOf).toSet(),
        strings(json.getJSONArray("sections")).toSet(),
        json.getJSONObject("tags").let { tags ->
            tags.keys().asSequence().associateWith { strings(tags.getJSONArray(it)).toSet() }
        }
    ).also {
        require(
            it.sections.size <= 64 && it.tags.size <= 64 &&
                it.tags.values.all { tags -> tags.size <= 64 }
        )
    }
    fun strings(array: JSONArray): List<String> =
        (0 until array.length()).map { array.getString(it) }
    fun objects(array: JSONArray): List<JSONObject> =
        (0 until array.length()).map { array.getJSONObject(it) }
    fun directory(value: PartnerDirectory): String = JSONObject()
        .put(
            "identities",
            JSONArray(
                value.identities.map { identity ->
                    JSONObject()
                        .put(
                            "id",
                            identity.id
                        ).put("key", identity.publicKey).put("name", identity.name)
                        .put(
                            "grants",
                            scope(identity.grants)
                        ).put("enabled", identity.exchangeEnabled)
                        .put(
                            "devices",
                            JSONArray(
                                identity.devices.map { device ->
                                    JSONObject()
                                        .put(
                                            "id",
                                            device.id
                                        ).put(
                                            "key",
                                            device.publicKey
                                        ).put("certificate", device.certificate)
                                        .put(
                                            "local",
                                            device.localNonce
                                        ).put(
                                            "remote",
                                            device.remoteNonce
                                        ).put("enabled", device.enabled)
                                }
                            )
                        )
                }
            )
        ).put(
            "contacts",
            JSONArray(
                value.contacts.map { contact ->
                    JSONObject()
                        .put(
                            "id",
                            contact.id
                        ).put(
                            "name",
                            contact.name
                        ).put("identities", JSONArray(contact.identities.toList()))
                        .put("group", contact.group)
                }
            )
        ).toString()
    fun directory(value: String): PartnerDirectory = JSONObject(value).let { root ->
        PartnerDirectory(
            objects(root.getJSONArray("identities")).map { json ->
                PartnerIdentity(
                    json.getString("id"),
                    json.getString("key"),
                    json.getString("name"),
                    objects(json.getJSONArray("devices")).map { device ->
                        PartnerDevice(
                            device.getString("id"),
                            device.getString("key"),
                            device.getString("certificate"),
                            device.getString("local"),
                            device.getString("remote"),
                            device.getBoolean("enabled")
                        )
                    },
                    scope(json.getJSONObject("grants")),
                    json.getBoolean("enabled")
                )
            },
            objects(root.getJSONArray("contacts")).map { json ->
                PartnerContact(
                    json.getString("id"),
                    json.getString("name"),
                    strings(json.getJSONArray("identities")).toSet(),
                    json.getBoolean("group")
                )
            }
        )
    }
    fun doodle(value: Doodle): JSONObject =
        JSONObject().put("caption", value.caption).put("opening", value.opening.name)
            .put("openAt", value.openAt).put("days", value.delayDays).put(
                "strokes",
                JSONArray(
                    value.strokes.map { stroke ->
                        JSONObject().put(
                            "color",
                            stroke.color
                        ).put("width", stroke.width.toDouble()).put("eraser", stroke.eraser)
                            .put(
                                "points",
                                JSONArray(
                                    stroke.points.map { point ->
                                        JSONArray(listOf(point.x.toDouble(), point.y.toDouble()))
                                    }
                                )
                            )
                    }
                )
            )
    fun doodle(value: JSONObject): Doodle {
        val strokes = value.getJSONArray("strokes")
        require(strokes.length() in 1..256)
        var pointCount = 0
        return Doodle(
            objects(strokes).map { stroke ->
                val points = stroke.getJSONArray("points")
                pointCount += points.length()
                require(pointCount <= 20000)
                DoodleStroke(
                    (0 until points.length()).map { i ->
                        points.getJSONArray(i).let {
                            require(it.length() == 2)
                            DoodlePoint(it.getDouble(0).toFloat(), it.getDouble(1).toFloat())
                        }
                    },
                    stroke.getLong("color"),
                    stroke.getDouble("width").toFloat(),
                    stroke.getBoolean("eraser")
                )
            },
            value.getString("caption"),
            DoodleOpening.valueOf(value.getString("opening")),
            value.getLong("openAt"),
            value.getInt("days")
        )
    }
}
