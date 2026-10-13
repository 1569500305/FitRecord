package com.fitrecord.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fitrecord.app.AppContainer
import com.fitrecord.app.core.WeekUtil
import com.fitrecord.app.data.db.RecordWithType
import com.fitrecord.app.data.db.entity.BodyPartEntity
import com.fitrecord.app.data.db.entity.ExerciseTypeEntity
import com.fitrecord.app.data.db.entity.FoodItemEntity
import com.fitrecord.app.data.db.entity.UserProfileEntity
import com.fitrecord.app.data.db.seed.BodyParts
import com.fitrecord.app.data.repo.TrainingRepository
import com.fitrecord.app.domain.CalendarBuilder
import com.fitrecord.app.domain.CalorieCalculator
import com.fitrecord.app.domain.DayCell
import com.fitrecord.app.domain.FoodEquivalent
import com.fitrecord.app.domain.PeriodType
import com.fitrecord.app.domain.StatsPeriod
import com.fitrecord.app.domain.StatsUiModel
import com.fitrecord.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * 每屏一个 ViewModel，依赖统一从 AppContainer 注入（无 Hilt，见技术选型）。
 *
 * 必须 `inline + reified`：viewModelFactory 的 initializer DSL 以**具体 VM 类**为 key 注册。
 * 之前写成 `create: () -> ViewModel` 会把类型擦除为 ViewModel，导致启动时
 * "No initializer set for given class CalendarViewModel" 直接闪退（真机报闪退的根因）。
 */
internal inline fun <reified VM : ViewModel> factoryOf(crossinline create: () -> VM): ViewModelProvider.Factory =
    viewModelFactory { initializer { create() } }

// ---------------------------------------------------------------- 日历（P1）

data class MonthSummary(
    val days: Int, val segments: Int, val minutes: Int, val calories: Int?, val foodText: String,
)

data class CalendarUi(
    val month: YearMonth,
    val cells: List<DayCell>,
    val isWeekMode: Boolean,
    val selected: LocalDate,
    val summary: MonthSummary,
    val showBanner: Boolean,
    val readOnly: Boolean,
)

private data class GridKey(
    val month: YearMonth,
    val selected: LocalDate,
    val weekMode: Boolean,
    val firstDay: DayOfWeek,
    val range: IntRange,
)

class CalendarViewModel(
    private val repo: TrainingRepository,
    private val container: AppContainer,
    private val clock: () -> LocalDate,
) : ViewModel() {

    private val today get() = clock()
    private val month = MutableStateFlow(YearMonth.from(today))
    private val selected = MutableStateFlow(today)
    private val weekMode = MutableStateFlow(false)
    private val bannerDismissed = MutableStateFlow(false)

    private val initialKey = GridKey(
        YearMonth.from(today), today, false, DayOfWeek.MONDAY,
        WeekUtil.monthRange(YearMonth.from(today)),
    )

    private val key = kotlinx.coroutines.flow.combine(month, selected, weekMode, container.settings.firstDayOfWeek) { m, s, w, f ->
        GridKey(m, s, w, f, if (w) WeekUtil.weekRange(s, f) else WeekUtil.monthRange(m))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, initialKey)

    private val records = key.flatMapLatest { k -> repo.observeRecords(k.range) }
    private val weightKg = repo.observeWeightKg()
    private val foods = repo.observeFoods()

    val uiState: StateFlow<CalendarUi> = kotlinx.coroutines.flow.combine(
        key, records, foods, weightKg, bannerDismissed,
    ) { k, rows, foodList, kg, dismissed -> buildUi(k, rows, foodList, kg, dismissed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyUi())

    private fun buildUi(
        k: GridKey,
        rows: List<RecordWithType>,
        foodList: List<FoodItemEntity>,
        weight: Double?,
        dismissed: Boolean,
    ): CalendarUi {
        val byDay = rows.groupBy { it.date }
        val cells = if (k.weekMode) CalendarBuilder.weekRow(k.selected, k.firstDay, byDay, today, k.selected)
        else CalendarBuilder.monthGrid(k.month, k.firstDay, byDay, today, k.selected)

        val inRange = rows.filter { it.date in k.range }
        val caloriesInt = inRange.mapNotNull { it.calories }.takeIf { it.isNotEmpty() }
            ?.sum()?.let { Math.round(it).toInt() }

        return CalendarUi(
            month = k.month,
            cells = cells,
            isWeekMode = k.weekMode,
            selected = k.selected,
            summary = MonthSummary(
                days = byDay.keys.size,
                segments = inRange.size,
                minutes = inRange.sumOf { it.durationMin },
                calories = caloriesInt,
                foodText = FoodEquivalent.text(caloriesInt, foodList),
            ),
            showBanner = weight == null && !dismissed,
            readOnly = container.readOnly.value,
        )
    }

    private fun emptyUi() = CalendarUi(
        YearMonth.from(today), emptyList(), false, today,
        MonthSummary(0, 0, 0, null, ""), showBanner = false, readOnly = false,
    )

    fun onMonthShift(delta: Int) = viewModelScope.launch { month.update { it.plusMonths(delta.toLong()) } }
    fun onDaySelected(date: LocalDate) = viewModelScope.launch { selected.value = date }
    fun onToggleMode() = viewModelScope.launch { weekMode.update { !it } }
    fun dismissBanner() = viewModelScope.launch { bannerDismissed.value = true }

    companion object {
        fun factory(c: AppContainer, clock: () -> LocalDate = LocalDate::now) =
            factoryOf { CalendarViewModel(c.trainingRepo, c, clock) }
    }
}

// ---------------------------------------------------------------- 当日（P4）

data class DayUi(
    val date: LocalDate,
    val records: List<TrainingRepository.DayRecord>,
    val minutes: Int,
    val calories: Int?,
    val foodText: String,
    val dayNote: String,
    val partNames: Map<Long, String>,
    val pendingUndo: TrainingRepository.DayRecord?,
    val readOnly: Boolean,
) {
    val trained: Boolean get() = records.isNotEmpty()
    val segments: Int get() = records.size
}

class DayRecordViewModel(
    private val repo: TrainingRepository,
    private val container: AppContainer,
    val date: LocalDate,
) : ViewModel() {

    private val undo = MutableStateFlow<TrainingRepository.DayRecord?>(null)

    val uiState: StateFlow<DayUi> = kotlinx.coroutines.flow.combine(
        repo.observeDay(date), repo.observeFoods(), repo.observeDayNote(date),
        container.bodyPartDao.observeAll(), undo,
    ) { records, foods, note, parts, pending ->
        val calories = records.mapNotNull { it.calories }.takeIf { it.isNotEmpty() }
            ?.sum()?.let { Math.round(it).toInt() }
        DayUi(
            date = date,
            records = records,
            minutes = records.sumOf { it.durationMin },
            calories = calories,
            foodText = FoodEquivalent.text(calories, foods),
            dayNote = note,
            partNames = parts.associate { it.id to it.name },
            pendingUndo = pending,
            readOnly = container.readOnly.value,
        )
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        DayUi(date, emptyList(), 0, null, "", "", emptyMap(), null, false),
    )

    fun toggleTrained(on: Boolean) = viewModelScope.launch { repo.setTrainedToday(date, on) }

    /** 删除即生效并留快照，5 秒内可撤销（规格 5.4） */
    fun delete(id: Long) = viewModelScope.launch { undo.value = repo.delete(id) }

    fun undoDelete() = viewModelScope.launch { undo.value?.let { repo.restore(it) }; undo.value = null }

    fun dismissUndo() = viewModelScope.launch { undo.value = null }

    fun saveNote(text: String) = viewModelScope.launch { repo.saveDayNote(date, text) }

    companion object {
        fun factory(c: AppContainer, date: LocalDate) =
            factoryOf { DayRecordViewModel(c.trainingRepo, c, date) }
    }
}

// ---------------------------------------------------------------- 记录编辑（P5）

enum class EditorMode { CREATE, EDIT }

/** 用户正在敲的表单，单一可变来源（避免十个 flow 手工合并） */
private data class Draft(
    val date: LocalDate,
    val type: Long? = null,
    val minutes: Int = 0,
    val parts: Set<Long> = emptySet(),
    val note: String = "",
    val partsTouched: Boolean = false,
    val pristine: Boolean = true,
)

data class EditorUi(
    val mode: EditorMode,
    val date: LocalDate,
    val types: List<ExerciseTypeEntity>,
    val selectedType: Long?,
    val minutes: Int,
    val parts: List<Pair<Long, String>>,
    val selectedParts: Set<Long>,
    val note: String,
    val previewKcal: Int?,
    val formula: String?,
    val weightMissing: Boolean,
    val errors: List<String>,
    val dirty: Boolean,
    val readOnly: Boolean,
) {
    val canSave: Boolean get() = errors.isEmpty() && !readOnly
}

class RecordEditorViewModel(
    private val repo: TrainingRepository,
    private val container: AppContainer,
    initialDate: LocalDate,
    private val recordId: Long,
) : ViewModel() {

    private val draft = MutableStateFlow(Draft(initialDate))

    val uiState: StateFlow<EditorUi> = kotlinx.coroutines.flow.combine(
        draft, container.typeDao.observeActive(), container.bodyPartDao.observeAll(),
        container.profileDao.observeProfile(),
    ) { d, types, parts, profile -> buildUi(d, types, parts, profile) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditorUi(
            EditorMode.CREATE, initialDate, emptyList(), null, 0, emptyList(), emptySet(),
            "", null, null, false, emptyList(), false, false,
        ))

    private fun buildUi(
        d: Draft,
        types: List<ExerciseTypeEntity>,
        parts: List<BodyPartEntity>,
        profile: UserProfileEntity?,
    ): EditorUi {
        val type = types.find { it.id == d.type }
        val kcal = type?.let { CalorieCalculator.kcal(it.met, profile?.weightKg, d.minutes) }
        return EditorUi(
            mode = if (recordId < 0) EditorMode.CREATE else EditorMode.EDIT,
            date = d.date,
            types = types,
            selectedType = d.type,
            minutes = d.minutes,
            parts = parts.map { it.id to it.name },
            selectedParts = d.parts,
            note = d.note,
            previewKcal = kcal?.let { Math.round(it).toInt() },
            formula = type?.let { t -> profile?.let { p -> CalorieCalculator.explain(t.met, p.weightKg, d.minutes) } },
            weightMissing = profile == null,
            errors = validate(d),
            dirty = !d.pristine,
            readOnly = container.readOnly.value,
        )
    }

    private fun validate(d: Draft): List<String> = buildList {
        if (d.minutes !in 1..1440) add("时长需在 1-1440 分钟")
        if (d.type == null) add("请选择运动方式")
        if (d.note.length > 500) add("备注最多 500 字")
    }

    init {
        viewModelScope.launch {
            if (recordId >= 0) {
                repo.dayRecords(draft.value.date).firstOrNull { it.id == recordId }?.let { existing ->
                    draft.value = Draft(
                        date = existing.date,
                        type = existing.type.id,
                        minutes = existing.durationMin,
                        parts = existing.bodyPartIds.toSet(),
                        note = existing.note,
                        pristine = false,
                    )
                }
            } else {
                val last = container.settings.lastExerciseTypeId.first()
                if (last != null) draft.update { it.copy(type = last) }
            }
        }
    }

    fun onType(id: Long) = viewModelScope.launch {
        val defaults = BodyParts.parseIds(container.typeDao.byId(id)?.defaultBodyPartIds.orEmpty()).toSet()
        draft.update {
            it.copy(
                type = id,
                parts = if (it.partsTouched) it.parts else defaults,
                pristine = false,
            )
        }
    }

    fun onMinutes(value: Int) = viewModelScope.launch { draft.update { it.copy(minutes = value, pristine = false) } }
    fun onNote(value: String) = viewModelScope.launch { draft.update { it.copy(note = value, pristine = false) } }
    fun onDate(value: LocalDate) = viewModelScope.launch { draft.update { it.copy(date = value, pristine = false) } }

    fun onTogglePart(id: Long) = viewModelScope.launch {
        draft.update { it.copy(parts = if (id in it.parts) it.parts - id else it.parts + id, partsTouched = true, pristine = false) }
    }

    /** 返回 false 表示校验没过或被只读模式挡住 */
    suspend fun save(): Boolean {
        val d = draft.value
        if (container.readOnly.value) return false
        if (validate(d).isNotEmpty()) return false
        val type = d.type ?: return false
        if (recordId < 0) {
            repo.add(d.date, type, d.minutes, d.parts.toList(), d.note)
        } else {
            repo.update(recordId, d.date, type, d.minutes, d.parts.toList(), d.note)
        }
        container.settings.setLastExerciseTypeId(type)
        return true
    }

    companion object {
        fun factory(c: AppContainer, date: LocalDate, id: Long) =
            factoryOf { RecordEditorViewModel(c.trainingRepo, c, date, id) }
    }
}

// ---------------------------------------------------------------- 统计（P2）

data class StatsUi(val period: StatsPeriod, val model: StatsUiModel?, val canShiftForward: Boolean)

class StatsViewModel(
    private val container: AppContainer,
    private val clock: () -> LocalDate,
) : ViewModel() {

    private val periodType = MutableStateFlow(PeriodType.WEEK)
    private val anchor = MutableStateFlow(clock())

    private val period: StateFlow<StatsPeriod> = kotlinx.coroutines.flow.combine(
        periodType, anchor, container.settings.firstDayOfWeek,
    ) { t, a, f -> container.statsRepo.periodOf(t, a, f) }
        .stateIn(
            viewModelScope, SharingStarted.Eagerly,
            container.statsRepo.periodOf(PeriodType.WEEK, clock(), DayOfWeek.MONDAY),
        )

    val uiState: StateFlow<StatsUi> = kotlinx.coroutines.flow.combine(
        period, period.flatMapLatest { container.statsRepo.observeStats(it) },
    ) { p, model -> StatsUi(p, model, canShiftForward = !model.period.isCurrentOrFuture(clock())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUi(period.value, null, false))

    fun onPeriodType(index: Int) = viewModelScope.launch {
        periodType.value = if (index == 0) PeriodType.WEEK else PeriodType.MONTH
    }

    /** ‹ › 翻页；往后翻不得越过当前周期（规格 P2） */
    fun onShift(delta: Int) = viewModelScope.launch {
        if (delta > 0 && uiState.value.canShiftForward) return@launch
        anchor.value = period.value.shifted(delta).anchor
    }

    companion object {
        fun factory(c: AppContainer, clock: () -> LocalDate = LocalDate::now) =
            factoryOf { StatsViewModel(c, clock) }
    }
}

// ---------------------------------------------------------------- 身体参数（P6 / P0）

private data class BodyDraft(
    val gender: String? = null,
    val ageText: String = "",
    val heightText: String = "",
    val weightText: String = "",
)

data class BodyUi(
    val gender: String?,
    val ageText: String,
    val heightText: String,
    val weightText: String,
    val errors: List<String>,
    val hasProfile: Boolean,
)

class BodyProfileViewModel(private val container: AppContainer) : ViewModel() {

    private val draft = MutableStateFlow(BodyDraft())
    private val loaded = MutableStateFlow(false)

    val uiState: StateFlow<BodyUi> = kotlinx.coroutines.flow.combine(
        draft, container.profileDao.observeProfile(), loaded,
    ) { d, existing, isLoaded ->
        BodyUi(
            gender = d.gender ?: existing?.gender,
            ageText = d.ageText.ifBlank { existing?.age?.toString().orEmpty() },
            heightText = d.heightText.ifBlank { existing?.heightCm?.toString()?.removeSuffix(".0").orEmpty() },
            weightText = d.weightText.ifBlank { existing?.weightKg?.toString()?.removeSuffix(".0").orEmpty() },
            errors = validate(d),
            hasProfile = existing != null || isLoaded,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BodyUi(null, "", "", "", emptyList(), false))

    private fun validate(d: BodyDraft): List<String> = buildList {
        val w = d.weightText.toDoubleOrNull()
        if (w == null) add("体重为必填，否则无法计算卡路里")
        else if (w !in 20.0..300.0) add("体重需在 20–300 kg")
        d.heightText.toDoubleOrNull()?.let { if (it !in 100.0..250.0) add("身高需在 100–250 cm") }
        d.ageText.toIntOrNull()?.let { if (it !in 10..100) add("年龄需在 10–100 岁") }
    }

    fun onGender(value: String?) = viewModelScope.launch { draft.update { it.copy(gender = value) } }
    fun onAge(value: String) = viewModelScope.launch { draft.update { it.copy(ageText = value.filter { ch -> ch.isDigit() }.take(3)) } }
    fun onHeight(value: String) = viewModelScope.launch { draft.update { it.copy(heightText = value.onlyDecimal()) } }
    fun onWeight(value: String) = viewModelScope.launch { draft.update { it.copy(weightText = value.onlyDecimal()) } }

    suspend fun save(): Boolean {
        val d = draft.value
        val weight = d.weightText.toDoubleOrNull() ?: return false
        if (validate(d).isNotEmpty()) return false
        val existing = container.profileDao.profile()
        container.profileDao.upsert(
            UserProfileEntity(
                id = 0,
                gender = d.gender ?: existing?.gender,
                age = d.ageText.toIntOrNull() ?: existing?.age,
                heightCm = d.heightText.toDoubleOrNull() ?: existing?.heightCm,
                weightKg = weight,
                weeklyGoal = existing?.weeklyGoal ?: 0,   // V1 只存不显示（规格 9）
                updatedAt = System.currentTimeMillis(),
            )
        )
        loaded.value = true
        return true
    }

    private fun String.onlyDecimal(): String {
        val filtered = filter { it.isDigit() || it == '.' }
        val dot = filtered.indexOf('.')
        return if (dot < 0) filtered
        else filtered.take(dot + 1) + filtered.substring(dot + 1).replace(".", "")
    }

    companion object {
        fun factory(c: AppContainer) = factoryOf { BodyProfileViewModel(c) }
    }
}

// ---------------------------------------------------------------- 外观（P10）

data class AppearanceUi(val themeMode: ThemeMode, val firstDay: DayOfWeek)

class AppearanceViewModel(private val container: AppContainer) : ViewModel() {

    val uiState: StateFlow<AppearanceUi> = kotlinx.coroutines.flow.combine(
        container.settings.themeMode, container.settings.firstDayOfWeek,
    ) { mode, day -> AppearanceUi(mode, day) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppearanceUi(ThemeMode.SYSTEM, DayOfWeek.MONDAY))

    fun setTheme(index: Int) = viewModelScope.launch {
        container.settings.setThemeMode(ThemeMode.entries.toList()[index.coerceIn(0, 2)])
    }

    fun setFirstDay(index: Int) = viewModelScope.launch {
        container.settings.setFirstDayOfWeek(if (index == 0) DayOfWeek.MONDAY else DayOfWeek.SUNDAY)
    }

    companion object {
        fun factory(c: AppContainer) = factoryOf { AppearanceViewModel(c) }
    }
}
