package com.example.speach_recognotion_llm.ui.screen

import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.speach_recognotion_llm.data.model.DailyScore
import com.example.speach_recognotion_llm.data.model.WeeklyAnalytics
import com.example.speach_recognotion_llm.ui.viewmodel.MoodViewModel
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter

@Composable
fun AnalyticsScreen(viewModel: MoodViewModel) {
    val analytics by viewModel.analytics.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadWeeklyAnalytics()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Weekly Analytics",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        analytics?.let { data ->
            if (data.dailyMoodScores.isNotEmpty()) {
                ChartCard(title = "Mood (Valence)") {
                    LineChartView(
                        scores = data.dailyMoodScores,
                        label = "Valence",
                        lineColor = AndroidColor.parseColor("#4CAF50")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            if (data.dailyStressScores.isNotEmpty()) {
                ChartCard(title = "Stress Level") {
                    LineChartView(
                        scores = data.dailyStressScores,
                        label = "Stress",
                        lineColor = AndroidColor.parseColor("#F44336")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            if (data.emotionDistribution.isNotEmpty()) {
                ChartCard(title = "Emotion Distribution") {
                    BarChartView(distribution = data.emotionDistribution)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            if (data.weeklySummary.isNotEmpty()) {
                SummaryCardView(data)
            }
        } ?: run {
            Text(
                text = "Loading analytics...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun LineChartView(scores: List<DailyScore>, label: String, lineColor: Int) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        factory = { context ->
            LineChart(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                description.isEnabled = false
                legend.isEnabled = true
                setTouchEnabled(true)
                setDragEnabled(true)
                setScaleEnabled(false)
                axisRight.isEnabled = false

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    granularity = 1f
                    setDrawGridLines(false)
                    valueFormatter = IndexAxisValueFormatter(
                        scores.map { it.date.takeLast(5) }
                    )
                }

                axisLeft.apply {
                    setDrawGridLines(true)
                    gridColor = AndroidColor.parseColor("#E0E0E0")
                }
            }
        },
        update = { chart ->
            val entries = scores.mapIndexed { index, score ->
                Entry(index.toFloat(), score.value)
            }

            val dataSet = LineDataSet(entries, label).apply {
                color = lineColor
                setCircleColor(lineColor)
                lineWidth = 2f
                circleRadius = 4f
                setDrawValues(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
                setDrawFilled(true)
                fillColor = lineColor
                fillAlpha = 30
            }

            chart.data = LineData(dataSet)
            chart.invalidate()
        }
    )
}

@Composable
private fun BarChartView(distribution: Map<String, Int>) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        factory = { context ->
            BarChart(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                description.isEnabled = false
                legend.isEnabled = false
                setTouchEnabled(false)
                axisRight.isEnabled = false

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    granularity = 1f
                    setDrawGridLines(false)
                    valueFormatter = IndexAxisValueFormatter(distribution.keys.toList())
                    labelRotationAngle = -30f
                }

                axisLeft.apply {
                    granularity = 1f
                    axisMinimum = 0f
                    setDrawGridLines(true)
                    gridColor = AndroidColor.parseColor("#E0E0E0")
                }
            }
        },
        update = { chart ->
            val entries = distribution.entries.mapIndexed { index, entry ->
                BarEntry(index.toFloat(), entry.value.toFloat())
            }

            val colors = listOf(
                AndroidColor.parseColor("#4CAF50"),
                AndroidColor.parseColor("#2196F3"),
                AndroidColor.parseColor("#FF9800"),
                AndroidColor.parseColor("#F44336"),
                AndroidColor.parseColor("#9C27B0"),
                AndroidColor.parseColor("#009688"),
                AndroidColor.parseColor("#795548"),
            )

            val dataSet = BarDataSet(entries, "Emotions").apply {
                setColors(colors.take(entries.size))
                setDrawValues(true)
                valueTextSize = 10f
            }

            chart.data = BarData(dataSet)
            chart.invalidate()
        }
    )
}

@Composable
private fun SummaryCardView(analytics: WeeklyAnalytics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Weekly Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = analytics.weeklySummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Dominant emotion: ${analytics.dominantEmotion} | Avg stress: ${"%.0f".format(analytics.avgStress * 100)}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}
