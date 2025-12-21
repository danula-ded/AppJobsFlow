# 🎨 AppJobsFlow — Color Tokens

Минимальный набор цветовых токенов для светлой/тёмной темы и состояний. Всё лишнее и описательные комментарии убраны для краткости.

---

## 🌈 Базовая палитра

| Роль                     | Значение |
| ------------------------ | -------- |
| Акцент (градиент)        | `linear-gradient(90deg, #3B82F6 0%, #10B981 100%)` |
| Вторичный                | `#6B7280` |
| Фон (light)              | `#FFFFFF` |
| Фон (dark)               | `#111827` |
| Текст (dark on light)    | `#1F2937` |
| Текст (light on dark)    | `#F9FAFB` |
| Предупреждение           | `#FBBF24` |

---

## 🧩 Состояния

| State    | Light     | Dark      |
| -------- | --------- | --------- |
| Success  | `#10B981` | `#34D399` |
| Error    | `#EF4444` | `#F87171` |
| Warning  | `#F59E0B` | `#FBBF24` |
| Info     | `#3B82F6` | `#60A5FA` |
| Hover    | `#2563EB` | `#1E40AF` |
| Disabled | `#D1D5DB` | `#4B5563` |

---

## 🌗 Переменные тем (globals.css)

```css
@layer base {
  :root {
    --color-accent-start: #3B82F6;
    --color-accent-end: #10B981;
    --color-secondary: #6B7280;
    --color-background: #FFFFFF;
    --color-text: #1F2937;
    --color-warning: #FBBF24;
    --color-success: #10B981;
    --color-error: #EF4444;
  }

  .dark {
    --color-background: #111827;
    --color-text: #F9FAFB;
    --color-secondary: #9CA3AF;
    --color-accent-start: #2563EB;
    --color-accent-end: #059669;
    --color-warning: #F59E0B;
    --color-success: #34D399;
    --color-error: #F87171;
  }
}
```

---

## 🧭 Пример использования (Tailwind v4+)

```html
<div class="bg-[var(--color-background)] text-[var(--color-text)]">
  <button class="rounded-xl px-5 py-2 bg-gradient-to-r from-[var(--color-accent-start)] to-[var(--color-accent-end)] text-white">
    Действие
  </button>
</div>
```
