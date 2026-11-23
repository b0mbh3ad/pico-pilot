package com.picopilot.agent

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var statusView: TextView
    private lateinit var addressView: TextView
    private lateinit var pinView: TextView
    private lateinit var rootView: TextView
    private lateinit var clientsView: TextView
    private lateinit var logView: TextView
    private lateinit var toggleButton: Button

    private val background = Color.parseColor("#0B1020")
    private val cardColor = Color.parseColor("#151B2E")
    private val cardHigh = Color.parseColor("#1D2540")
    private val accent = Color.parseColor("#6C8CFF")
    private val accentSoft = Color.parseColor("#2A3568")
    private val good = Color.parseColor("#7DE2A8")
    private val bad = Color.parseColor("#FF7B7B")
    private val textPrimary = Color.parseColor("#EAEEF9")
    private val textSecondary = Color.parseColor("#8B95AD")

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AgentState.init(this)
        window.statusBarColor = background
        window.navigationBarColor = background

        setContentView(buildUi())

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        AgentService.start(this)
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    // ------------------------------------------------------------------ UI

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(32))
            setBackgroundColor(this@MainActivity.background)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        root.addView(text("PICOPILOT", 11f, accent).apply {
            letterSpacing = 0.22f
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(text("Агент шлема", 30f, textPrimary).apply {
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(4), 0, 0)
        })
        root.addView(text("Приёмник подключений вместо ADB", 13f, textSecondary).apply {
            setPadding(0, dp(2), 0, dp(20))
        })

        // Статус
        val statusCard = card()
        statusView = text("Проверяю…", 15f, textPrimary).apply {
            setTypeface(typeface, Typeface.BOLD)
        }
        statusCard.addView(statusView)
        clientsView = text("", 12f, textSecondary).apply {
            setPadding(0, dp(4), 0, 0)
        }
        statusCard.addView(clientsView)
        root.addView(statusCard, wide(top = 0))

        // Адрес и PIN
        val infoCard = card()
        infoCard.addView(text("ПОДКЛЮЧЕНИЕ", 10f, textSecondary).apply { letterSpacing = 0.14f })
        addressView = text("—", 22f, textPrimary).apply {
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding(0, dp(6), 0, dp(14))
        }
        infoCard.addView(addressView)

        infoCard.addView(text("PIN ДЛЯ ТЕЛЕФОНА", 10f, textSecondary).apply { letterSpacing = 0.14f })
        pinView = text("------", 40f, good).apply {
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = 0.18f
            setPadding(0, dp(2), 0, dp(10))
        }
        infoCard.addView(pinView)
        infoCard.addView(
            text(
                "Введите этот PIN в приложении на телефоне один раз — дальше подключение будет само.",
                12f,
                textSecondary,
            ).apply { setLineSpacing(0f, 1.25f) },
        )
        root.addView(infoCard, wide(top = 14))

        // Кнопки
        toggleButton = Button(this).apply {
            setTextColor(this@MainActivity.background)
            setTypeface(typeface, Typeface.BOLD)
            minimumHeight = dp(50)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(accent, 14f)
            setOnClickListener {
                if (AgentState.running) {
                    AgentService.stop(this@MainActivity)
                    Toast.makeText(this@MainActivity, "Агент остановлен", Toast.LENGTH_SHORT).show()
                } else {
                    AgentService.start(this@MainActivity)
                    Toast.makeText(this@MainActivity, "Агент запускается", Toast.LENGTH_SHORT).show()
                }
                handler.postDelayed({ refresh() }, 600)
            }
        }
        root.addView(toggleButton, wide(top = 16))

        val pinButton = Button(this).apply {
            text = "Новый PIN"
            setTextColor(textPrimary)
            minimumHeight = dp(48)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(cardHigh, 14f, stroke = accentSoft)
            setOnClickListener {
                AgentState.regeneratePin()
                refresh()
                Toast.makeText(
                    this@MainActivity,
                    "PIN обновлён — введите его заново на телефоне",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
        root.addView(pinButton, wide(top = 10))

        val rootRow = card()
        rootView = text("Root: проверяю…", 13f, textSecondary)
        rootRow.addView(rootView)
        root.addView(rootRow, wide(top = 14))

        // Автозапуск
        val autoCard = card()
        val auto = CheckBox(this).apply {
            text = "Запускать при включении шлема"
            setTextColor(textPrimary)
            textSize = 14f
            isChecked = AgentState.autoStart
            buttonTintList = android.content.res.ColorStateList.valueOf(accent)
            setOnCheckedChangeListener { _, checked -> AgentState.autoStart = checked }
        }
        autoCard.addView(auto)
        root.addView(autoCard, wide(top = 10))

        val battery = Button(this).apply {
            text = "Разрешить работу в фоне"
            setTextColor(textPrimary)
            minimumHeight = dp(48)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(cardHigh, 14f, stroke = accentSoft)
            setOnClickListener { requestBatteryExemption() }
        }
        root.addView(battery, wide(top = 10))

        // Инструкция
        val help = card()
        help.addView(text("КАК ПОДКЛЮЧИТЬ", 10f, textSecondary).apply { letterSpacing = 0.14f })
        help.addView(
            text(
                "1. Оставьте агент запущенным на шлеме.\n" +
                    "2. На телефоне откройте PicoPilot и нажмите «Найти шлем».\n" +
                    "3. Выберите шлем в списке и введите PIN.\n\n" +
                    "ADB и постоянное подтверждение отладки больше не нужны. " +
                    "После сна шлема подключение восстанавливается само — перезагружать шлем не нужно.",
                13f,
                textSecondary,
            ).apply {
                setLineSpacing(0f, 1.3f)
                setPadding(0, dp(8), 0, 0)
            },
        )
        root.addView(help, wide(top = 14))

        // Логи
        val logCard = card()
        logCard.addView(text("ЖУРНАЛ", 10f, textSecondary).apply { letterSpacing = 0.14f })
        logView = text("", 10.5f, textSecondary).apply {
            setTypeface(Typeface.MONOSPACE)
            setPadding(0, dp(8), 0, 0)
            setLineSpacing(0f, 1.2f)
        }
        logCard.addView(logView)
        root.addView(logCard, wide(top = 14))

        val scroll = ScrollView(this).apply {
            setBackgroundColor(this@MainActivity.background)
            addView(root)
            isFillViewport = true
        }
        return scroll
    }

    private fun refresh() {
        val running = AgentState.running
        statusView.text = when {
            running && AgentState.root -> "● Работает с полным доступом"
            running -> "● Работает (без root)"
            else -> "○ Остановлен"
        }
        statusView.setTextColor(if (running) good else bad)

        val clients = AgentState.clients()
        clientsView.text = when {
            clients.isEmpty() && running -> "Ждёт телефон…"
            clients.isEmpty() -> "Служба выключена"
            else -> "Подключено: ${clients.joinToString(", ")}"
        }

        val ip = AgentState.ipv4Addresses().firstOrNull()
        addressView.text = if (ip != null) "$ip:${AgentState.PORT}" else "Нет Wi-Fi"

        pinView.text = AgentState.pin()
        rootView.text = when {
            AgentState.root -> "Root: есть — доступны все команды"
            running -> "Root: нет — только обычные команды"
            else -> "Root: ещё не проверен"
        }
        rootView.setTextColor(if (AgentState.root) good else textSecondary)

        toggleButton.text = if (running) "Остановить" else "Запустить"
        toggleButton.background = rounded(if (running) cardHigh else accent, 14f)
        toggleButton.setTextColor(if (running) textPrimary else this.background)

        val logs = AgentState.logs()
        logView.text = logs.joinToString("\n").ifEmpty { "Пока пусто" }
    }

    // ------------------------------------------------------------- системное

    private fun requestBatteryExemption() {
        val manager = getSystemService(POWER_SERVICE) as android.os.PowerManager
        if (manager.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, "Уже разрешено", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName")),
            )
        } catch (_: Exception) {
            runCatching {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    // ------------------------------------------------------------- мелочи

    private fun text(value: String, size: Float, color: Int): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
        }

    private fun card(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = rounded(cardColor, 18f)
        }

    private fun wide(top: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(top) }

    private fun rounded(color: Int, radius: Float, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius * resources.displayMetrics.density
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
