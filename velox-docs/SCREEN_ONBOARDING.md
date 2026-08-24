# Screen: Onboarding

**Route:** `onboarding`  
**Priority:** P1 (first launch)

---

## 1. Purpose

Welcome the user, request necessary permissions, explain value, and land them in a ready Library.

Keep it short (3 pages max).

---

## 2. Page Flow

### Page 1 – Welcome
- Large Velox symbol / logo
- Title: مرحبًا بك في فيلوكس
- Subtitle: مشغل وسائط قوي وأنيق، صُمم للعربية أولاً
- Primary button: التالي

### Page 2 – Features Highlights
- 3 short points with icons:
  - تشغيل سلس لأي ملف تقريبًا
  - تصميم زجاجي داكن وواجهات مريحة
  - تحكم كامل + خبرة عربية أصيلة
- Button: التالي

### Page 3 – Permissions & Ready
- Explanation of media access (why needed)
- Primary CTA: السماح بالوصول إلى الوسائط
- Secondary: تخطي الآن (limited experience)
- After grant → trigger initial scan → go to Library

---

## 3. Visual Style

- Full-screen dark background
- Large illustrations or abstract glass shapes
- Accent used on buttons and highlights
- Smooth page indicator (dots or thin bar)
- Horizontal pager with parallax subtle motion

---

## 4. Rules

- Show only once (DataStore flag)
- Can be re-triggered from Settings → “Show intro” (optional)
- All text in Arabic by default; respect system/app language if already set
- No account creation step

---

## 5. Edge Cases

- Permission denied → explain impact + button to open system settings
- User skips → Library empty state guides them to add folders later
