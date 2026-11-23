# Правила ProGuard для агента. Сборка debug не минифицируется,
# но файл нужен, чтобы release-вариант тоже собирался.
-keep class com.picopilot.agent.** { *; }
