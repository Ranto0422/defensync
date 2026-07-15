package com.example.defensync.domain.model

/**
 * Account roles. There are only two: a professor is picked by a student to be either the
 * adviser or a panelist — that adviser/panelist assignment lives on the defense, not the account.
 */
enum class Role(val label: String) {
    STUDENT("Student"),
    PROFESSOR("Professor");

    val isFaculty: Boolean get() = this == PROFESSOR

    companion object {
        fun from(value: String?): Role =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: STUDENT
    }
}

/** Days of the week the weekly availability grid covers (Mon–Sat; no Sunday). */
enum class Weekday(val short: String, val full: String) {
    MON("Mon", "Monday"),
    TUE("Tue", "Tuesday"),
    WED("Wed", "Wednesday"),
    THU("Thu", "Thursday"),
    FRI("Fri", "Friday"),
    SAT("Sat", "Saturday");

    companion object {
        fun from(value: String?): Weekday =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MON
    }
}

/**
 * Shared time constants. Times are minutes-from-midnight (7:00 AM = 420). Availability is entered
 * as ranges at 30-minute granularity; a defense needs a continuous 3-hour window everyone shares.
 */
object TimeConfig {
    const val DAY_START = 7 * 60      // 07:00
    const val DAY_END = 21 * 60       // 21:00
    const val SLOT = 30               // granularity, minutes
    const val DEFENSE = 180           // default defense length, minutes (3 hours)
    const val MIN_DEFENSE = 60        // shortest selectable defense (1 hour)
    val MAX_DEFENSE = DAY_END - DAY_START   // longest that can still fit the day (07:00–21:00 = 14h)

    /** 30-min slot start times used for coverage/alignment and the visualization grid rows. */
    val slotStarts: List<Int> = (DAY_START until DAY_END step SLOT).toList()

    /** Valid defense start times so a [duration]-long block still fits the day. */
    fun defenseStarts(duration: Int = DEFENSE): List<Int> =
        (DAY_START..(DAY_END - duration) step SLOT).toList()

    /** Selectable times for range endpoints (inclusive of DAY_END). */
    val timeOptions: List<Int> = (DAY_START..DAY_END step SLOT).toList()

    /** "7:00 AM", "1:30 PM", "9:00 PM". */
    fun label(minutes: Int): String {
        val h24 = (minutes / 60) % 24
        val m = minutes % 60
        val period = if (h24 < 12) "AM" else "PM"
        val h = when (val hh = h24 % 12) { 0 -> 12; else -> hh }
        return "$h:${m.toString().padStart(2, '0')} $period"
    }

    /** Compact defense-length label: "3h", "1h 30m", "45m". */
    fun durationLabel(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    /**
     * Parse shorthand into minutes: "7"→420, "730"→450, "7:30"→450, "12"→720, "1"→780 (1 PM).
     * Hours 1–6 map to PM, 7–12 stay AM/noon (matches a 7 AM–9 PM scheduling window). Optional
     * "am"/"pm" suffix overrides. Keeps the exact minute and clamps to the day window.
     */
    fun parse(raw: String): Int? {
        var s = raw.trim().lowercase().replace(" ", "")
        if (s.isEmpty()) return null
        var forced: String? = null
        if (s.endsWith("am")) { forced = "am"; s = s.dropLast(2) }
        else if (s.endsWith("pm")) { forced = "pm"; s = s.dropLast(2) }

        val hour: Int
        val minute: Int
        if (s.contains(":")) {
            val parts = s.split(":")
            hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
            minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        } else {
            val digits = s.filter { it.isDigit() }
            if (digits.isEmpty()) return null
            when (digits.length) {
                1, 2 -> { hour = digits.toInt(); minute = 0 }
                3 -> { hour = digits.substring(0, 1).toInt(); minute = digits.substring(1).toInt() }
                else -> { hour = digits.substring(0, 2).toInt(); minute = digits.substring(2, 4).toInt() }
            }
        }
        if (hour !in 0..23 || minute !in 0..59) return null

        val h24 = when {
            forced == "pm" && hour < 12 -> hour + 12
            forced == "am" && hour == 12 -> 0
            forced != null -> hour
            hour in 1..6 -> hour + 12   // 1–6 → afternoon
            else -> hour                // 7–12 as typed
        }
        val total = h24 * 60 + minute   // exact minute — no snapping to the 30-min grid
        if (total < DAY_START || total > DAY_END) return null
        return total
    }
}

/** A continuous availability window within a day, in minutes-from-midnight. */
data class TimeRange(val start: Int, val end: Int) {
    val isValid: Boolean get() = end > start
    val label: String get() = "${TimeConfig.label(start)} – ${TimeConfig.label(end)}"
}

/**
 * A concrete window that works for everyone on a defense: a start time on a weekday, optionally
 * pinned to a calendar [date], for [durationMin] minutes (default 3 hours, but student-editable).
 */
data class AlignedSlot(
    val weekday: Weekday,
    val start: Int,
    /** ISO calendar date (yyyy-MM-dd) once scheduled on the calendar; null for a weekday-only pick. */
    val date: String? = null,
    val durationMin: Int = TimeConfig.DEFENSE
) {
    val end: Int get() = start + durationMin
    val label: String
        get() = "${weekday.full}  ${TimeConfig.label(start)} – ${TimeConfig.label(end)}"

    fun encode(): String = "${weekday.name}:$start"

    companion object {
        fun decode(value: String?): AlignedSlot? {
            val parts = value?.split(":") ?: return null
            if (parts.size != 2) return null
            val start = parts[1].toIntOrNull() ?: return null
            return AlignedSlot(Weekday.from(parts[0]), start)
        }
    }
}

/**
 * A single scheduled defense as shown in the shared calendar (every user sees all of these).
 * [date] is ISO yyyy-MM-dd; [start]/[end] are minutes-from-midnight.
 */
data class CalendarEntry(
    val id: String,
    val title: String,
    val adviserName: String,
    val panelistNames: List<String>,
    /** Adviser + panelist user IDs — used to detect double-booking conflicts client-side. */
    val facultyIds: List<String>,
    val date: String,
    val start: Int,
    val durationMin: Int,
    val status: DefenseStatus
) {
    val end: Int get() = start + durationMin
    /** Everyone booked by this defense (adviser + panelists). */
    val facultyNames: List<String> get() = (listOf(adviserName) + panelistNames).filter { it.isNotBlank() }
}

/** A registered user + their weekly availability (weekday name -> list of ranges). */
data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: Role = Role.STUDENT,
    val studentNumber: String? = null,
    val availability: Map<String, List<TimeRange>> = emptyMap()
) {
    fun rangesOn(day: Weekday): List<TimeRange> = availability[day.name].orEmpty()
}

/** A row in the unified inbox: either a defense group chat or a private DM. */
enum class ConversationType { GROUP, DM }

data class InboxItem(
    val type: ConversationType,
    /** defenseId for GROUP, other userId for DM. */
    val key: String,
    val title: String,
    val subtitle: String,
    val lastAt: Long,
    val unread: Int
)

/** Someone the current user can privately message, with a preview of the latest DM. */
data class Contact(
    val uid: String,
    val name: String,
    val role: Role,
    val lastMessage: String? = null,
    val lastAt: Long = 0L
)

/** A group-chat message within a defense. */
data class ChatMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val body: String,
    val createdAt: Long,
    val edited: Boolean = false
)

/** One member's answer to a defense invitation. */
enum class ResponseStatus { PENDING, ACCEPTED, DECLINED }

/** Overall lifecycle of a defense request. */
enum class DefenseStatus(val label: String) {
    SCHEDULING("Choosing a time"),
    PENDING("Awaiting responses"),
    CONFIRMED("Confirmed"),
    DECLINED("Declined"),
    CANCELLED("Cancelled");

    companion object {
        fun from(value: String?): DefenseStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: PENDING
    }
}

/**
 * A defense "group": one student, the professor assigned as adviser, and the professors assigned
 * as panelists. Each participant keeps a per-group weekly availability; the student picks an
 * aligned slot, then every member must accept before it becomes CONFIRMED.
 */
data class DefenseRequest(
    val id: String = "",
    val title: String = "",
    /** The creator/owner (always one of [studentIds]). */
    val studentId: String = "",
    val studentName: String = "",
    /** All students in the group (creator + any co-students). */
    val studentIds: List<String> = emptyList(),
    val studentNames: List<String> = emptyList(),
    val adviserId: String = "",
    val adviserName: String = "",
    val panelistIds: List<String> = emptyList(),
    val panelistNames: List<String> = emptyList(),
    /** participantUid -> (weekday name -> ranges) for THIS group only. */
    val availability: Map<String, Map<String, List<TimeRange>>> = emptyMap(),
    /** Chosen once the student confirms a time; null while still SCHEDULING. */
    val slot: AlignedSlot? = null,
    /** memberUid -> ResponseStatus, for adviser + every panelist. */
    val responses: Map<String, ResponseStatus> = emptyMap(),
    val status: DefenseStatus = DefenseStatus.SCHEDULING,
    val createdAt: Long = 0L,
    /** Unread group-chat messages for the viewing user. */
    val unreadMessages: Int = 0
) {
    /** Every faculty member who must respond. */
    val memberIds: List<String> get() = (listOf(adviserId) + panelistIds).distinct()

    /** All students (falls back to the owner if the list is empty). */
    val students: List<String> get() = studentIds.ifEmpty { listOf(studentId) }

    /** Everyone in the group: students + members. */
    val participantIds: List<String> get() = (students + memberIds).distinct()

    /** The owning student and the adviser may edit the group's membership. */
    fun canEdit(uid: String): Boolean = uid == studentId || uid == adviserId

    fun responseOf(uid: String): ResponseStatus = responses[uid] ?: ResponseStatus.PENDING

    fun nameOf(uid: String): String = when {
        uid == adviserId -> adviserName
        uid in studentIds -> studentNames.getOrNull(studentIds.indexOf(uid)) ?: studentName
        uid in panelistIds -> panelistNames.getOrNull(panelistIds.indexOf(uid)) ?: "Member"
        uid == studentId -> studentName
        else -> "Member"
    }

    /** A participant's per-group availability as weekday -> ranges (empty days dropped). */
    fun availabilityOf(uid: String): Map<Weekday, List<TimeRange>> {
        val byDay = availability[uid].orEmpty()
        return Weekday.entries
            .associateWith { day -> byDay[day.name].orEmpty() }
            .filterValues { it.isNotEmpty() }
    }

    fun rangesOf(uid: String, day: Weekday): List<TimeRange> =
        availability[uid]?.get(day.name).orEmpty()

    fun hasAvailability(uid: String): Boolean =
        availability[uid]?.values?.any { it.isNotEmpty() } == true

    /**
     * Members (adviser + panelists) who still need to enter availability. The student's schedule
     * is optional and does NOT gate scheduling — alignment is computed among members only.
     */
    val membersMissingAvailability: List<String>
        get() = memberIds.filterNot { hasAvailability(it) }

    val acceptedCount: Int get() = responses.values.count { it == ResponseStatus.ACCEPTED }
}
