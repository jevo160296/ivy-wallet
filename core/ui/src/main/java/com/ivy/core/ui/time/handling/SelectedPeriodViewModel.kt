package com.ivy.core.ui.time.handling

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Immutable
import com.ivy.common.time.atEndOfDay
import com.ivy.common.time.dateNowLocal
import com.ivy.common.time.timeNow
import com.ivy.core.domain.FlowViewModel
import com.ivy.core.domain.action.period.SelectedPeriodFlow
import com.ivy.core.domain.action.period.SetSelectedPeriodAct
import com.ivy.core.domain.action.settings.startdayofmonth.StartDayOfMonthFlow
import com.ivy.core.domain.pure.time.*
import com.ivy.core.ui.data.period.MonthUi
import com.ivy.core.ui.data.period.monthsList
import com.ivy.core.ui.time.handling.SelectedPeriodViewModel.State
import com.ivy.core.ui.time.handling.SelectedPeriodViewModel.UiState
import com.ivy.data.time.SelectedPeriod
import com.ivy.data.time.TimeRange
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import javax.inject.Inject

@SuppressLint("StaticFieldLeak")
@HiltViewModel
class SelectedPeriodViewModel @Inject constructor(
    @ApplicationContext
    private val appContext: Context,
    private val startDayOfMonthFlow: StartDayOfMonthFlow,
    private val selectedPeriodFlow: SelectedPeriodFlow,
    private val setSelectedPeriodAct: SetSelectedPeriodAct
) : FlowViewModel<State, UiState, SelectPeriodEvent>() {

    data class State(
        val selectedPeriod: SelectedPeriod,
        val startDayOfMonth: Int,
        val months: List<MonthUi>,
    )

    @Immutable
    data class UiState(
        val startDayOfMonth: Int,
        val months: List<MonthUi>,
    )

    override fun initialState(): State = State(
        startDayOfMonth = 1,
        months = emptyList(),
        selectedPeriod = SelectedPeriod.AllTime(allTime())
    )

    override fun initialUiState(): UiState = UiState(
        startDayOfMonth = 1,
        months = emptyList(),
    )

    override fun stateFlow(): Flow<State> = combine(
        startDayOfMonthFlow(), selectedPeriodFlow()
    ) { startDayOfMonth, selectedPeriod ->
        val currentYear = dateNowLocal().year

        State(
            startDayOfMonth = startDayOfMonth,
            months = monthsList(appContext, year = currentYear - 1, currentYear = false) +
                    monthsList(appContext, year = currentYear, currentYear = true) +
                    monthsList(appContext, year = currentYear + 1, currentYear = false),
            selectedPeriod = selectedPeriod
        )
    }

    override suspend fun mapToUiState(state: State): UiState = UiState(
        startDayOfMonth = state.startDayOfMonth,
        months = state.months
    )

    override suspend fun handleEvent(event: SelectPeriodEvent) {
        val selectedPeriod = when (event) {
            SelectPeriodEvent.AllTime -> SelectedPeriod.AllTime(allTime())
            is SelectPeriodEvent.CustomRange -> SelectedPeriod.CustomRange(event.range)
            is SelectPeriodEvent.InTheLast -> toSelectedPeriod(event)
            is SelectPeriodEvent.Monthly -> dateToSelectedMonthlyPeriod(
                // TODO: Refactor that
                // 10 is a safe date in the middle of the month
                dateInPeriod = LocalDate.of(event.month.year, event.month.number, 10),
                startDayOfMonth = state.value.startDayOfMonth
            )
            SelectPeriodEvent.ResetToCurrentPeriod ->
                currentMonthlyPeriod(startDayOfMonth = state.value.startDayOfMonth)
            SelectPeriodEvent.LastYear -> yearPeriod(dateNowLocal().year - 1)
            SelectPeriodEvent.ThisYear -> yearPeriod(dateNowLocal().year)
            is SelectPeriodEvent.ShiftForward -> shiftPeriodForward()
            is SelectPeriodEvent.ShiftBackward -> shiftPeriodBackward()
        }

        setSelectedPeriodAct(selectedPeriod)
    }

    private fun toSelectedPeriod(event: SelectPeriodEvent.InTheLast): SelectedPeriod.InTheLast {
        val now = timeNow()
        val n = event.n
        return SelectedPeriod.InTheLast(
            n = n,
            unit = event.unit,
            range = TimeRange(
                // n - 1 because we count today
                // Negate: -n because we want to start from the **last** N unit
                from = shiftTime(time = now, n = -(n - 1), unit = event.unit),
                to = dateNowLocal().atEndOfDay(),
            )
        )
    }

    private fun shiftPeriodForward(): SelectedPeriod =
        when (val selected = state.value.selectedPeriod) {
            is SelectedPeriod.AllTime -> SelectedPeriod.AllTime(allTime())
            is SelectedPeriod.CustomRange -> shiftPeriod(selected.range, ShiftDirection.Forward)
            is SelectedPeriod.InTheLast -> shiftPeriod(selected.range, ShiftDirection.Forward)
            is SelectedPeriod.Monthly -> dateToSelectedMonthlyPeriod(
                dateInPeriod = selected.range.from.toLocalDate()
                    .plusMonths(1),
                startDayOfMonth = state.value.startDayOfMonth
            )
        }

    private fun shiftPeriodBackward(): SelectedPeriod =
        when (val selected = state.value.selectedPeriod) {
            is SelectedPeriod.AllTime -> SelectedPeriod.AllTime(allTime())
            is SelectedPeriod.CustomRange -> shiftPeriod(selected.range, ShiftDirection.Backward)
            is SelectedPeriod.InTheLast -> shiftPeriod(selected.range, ShiftDirection.Backward)
            is SelectedPeriod.Monthly -> dateToSelectedMonthlyPeriod(
                dateInPeriod = selected.range.from.toLocalDate()
                    .minusMonths(1),
                startDayOfMonth = state.value.startDayOfMonth
            )
        }

    private fun shiftPeriod(
        range: TimeRange,
        shiftDirection: ShiftDirection,
    ): SelectedPeriod.CustomRange {
        val lengthDays = periodLengthDays(range)
        val shiftDays = when (shiftDirection) {
            ShiftDirection.Forward -> lengthDays + 1
            ShiftDirection.Backward -> -lengthDays - 1
        }.toLong()

        return SelectedPeriod.CustomRange(
            range = TimeRange(
                from = range.from.plusDays(shiftDays),
                to = range.to.plusDays(shiftDays),
            )
        )
    }

    private enum class ShiftDirection {
        Forward, Backward
    }
}