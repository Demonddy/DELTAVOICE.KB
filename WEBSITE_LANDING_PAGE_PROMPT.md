# 🌟 DeltaVoice Website — Masterclass Landing Page Prompt

> **Use this prompt with your AI builder (Lovable, v0, Cursor, etc.) to create a stunning, conversion-focused landing page for the DeltaVoice keyboard app.**

---

## 🎯 Project Brief

Build a **lovable, attractive, and professional** landing page for **DeltaVoice** — an AI-powered mobile keyboard that transforms how people type, speak, and communicate across 12 languages. The site must feel premium, innovative, and emotionally resonant while showcasing every feature and service the app offers.

---

## 🎨 Visual Identity & Aesthetic

### Core Design Language: **Liquid Glass × Audio Waves**

- **Primary aesthetic:** "Liquid Glass" — frosted, translucent panels with subtle blur, soft gradients, and depth. Think: iOS Control Center meets premium audio software.
- **Color palette:**
  - **Primary accent:** `#7C52FF` (vibrant purple) and `#651FFF` (deeper purple on hover)
  - **Background gradient:** Soft purple-to-pink (`#80A18CD1` → `#80FBC2EB`) with low opacity for a dreamy, modern feel
  - **Dark surfaces:** `#111521`, `#1A1F2E`, `#0A0A0A` for contrast and depth
  - **Text:** `#E5E7EB` (light), `#9CA3AF` (muted), `#FFFFFF` (highlights)
- **Typography:** Use a distinctive, modern sans-serif — avoid Inter/Roboto. Consider: **Outfit**, **Satoshi**, **Clash Display**, or **General Sans** for headlines; a clean geometric font for body.
- **Motion:** Smooth, purposeful animations. Nothing jarring. Micro-interactions on hover and scroll. Subtle parallax where it adds depth.

---

## 🌊 Background: Audiowaves & Keyboard Motions

The landing page background must feel **alive** and **audio-forward**:

1. **Animated Audio Waveform**
   - Multiple horizontal bars (5–15) that pulse and undulate like a real-time audio visualizer
   - Bars should react subtly to scroll position or time — gentle, continuous motion
   - Use CSS `@keyframes` or a lightweight canvas/WebGL solution
   - Colors: semi-transparent purple (`rgba(124, 82, 255, 0.3)`) and pink (`rgba(251, 194, 235, 0.2)`)
   - Vary heights and speeds for organic feel — some bars faster, some slower
   - Optional: subtle glow/blur on peaks

2. **Floating Keyboard Keys**
   - Scattered, semi-transparent QWERTY keys (Q, W, E, R, T, Y, etc.) floating gently in the background
   - Keys should drift slowly (parallax-style) — some closer (larger, more opaque), some farther (smaller, more transparent)
   - Use the app’s glass key style: rounded corners, light stroke, soft fill
   - Optional: keys occasionally "press" with a subtle scale animation

3. **Layered Depth**
   - Background: dark gradient (`#0f0f12` → `#1a1525`)
   - Mid-layer: audio waves + floating keys
   - Foreground: content on glass panels with blur

---

## 📐 Page Structure & Sections

### 1. **Hero Section**
- **Headline:** Bold, emotional, benefit-driven. Example: *"Type less. Say more. In any language."* or *"Your voice, your words, one keyboard."*
- **Subheadline:** One sentence on AI + voice + 12 languages
- **CTA:** Primary button — "Download on Google Play" (or "Get DeltaVoice")
- **Hero visual:** A 3D or mockup of the keyboard UI — Liquid Glass style, showing the top bar (mic, AI, camera, clipboard) and key rows. Optional: subtle glow or reflection.
- **Trust badge:** "12 languages • 8 voice styles • AI-powered"

---

### 2. **Features Grid — All Services & Capabilities**

Present every feature in a **scannable, visual grid** (cards or icons + short copy). Group by category:

#### 🎤 **Voice & Speech**
- **Voice Input** — Speak, it types. Real-time speech-to-text.
- **Voice Recording** — Record, then choose how to process:
  - **Full Conversion** — Change language + voice (e.g., speak English, output Spanish with a different voice)
  - **Voice Cloning** — Translate while keeping *your* voice
  - **Transcript & Translate** — Get text in any of 12 languages
- **8 Voice Styles** — Adam, Aria, Roger, Sarah, Laura, Charlie, George, Liam
- **12 Languages** — English, Spanish, French, German, Italian, Portuguese, Russian, Japanese, Korean, Chinese, Arabic, Hindi
- **Upload Audio** — Process audio files from your device

#### 📹 **Video**
- **Video Recording** — Record video with audio directly from the keyboard
- **Video Translation** — Extract speech, translate, and dub with chosen voice
- **Upload Video** — Pick from gallery to process

#### 🤖 **AI Assistant**
- **AI Chat** — Smart assistant for writing, questions, translations
- **AI Writing Tools** (12 tools):
  - Grammar — Fix spelling, punctuation, clarity
  - Reply — Craft smart replies
  - Translate — Instant translation
  - Enhance Words — Better word choice and flow
  - Tone Changer — Professional, friendly, formal, casual, encouraging
  - Paraphrase — Rewrite in different words
  - Continue Writing — AI continues your text
  - Make Longer — Expand with detail
  - Summarize — Concise summary
  - Synonymous — Synonym suggestions
  - Make Shorter — Condense key points
  - Email Writer — Turn text into polished emails

#### ⌨️ **Keyboard**
- **Predictive Text** — Smart word suggestions
- **Auto-Correction** — Fix typos as you type
- **Emoji Picker** — Full emoji grid with categories
- **Calculator** — Built-in calculator
- **Dictionary** — Look up definitions and translations
- **Clipboard** — Quick access to copied content
- **Custom Themes** — Dark Purple, Midnight Blue, Forest Green, Sunset Orange, Rose Pink, Pure Dark; Premium: Galaxy, Neon
- **Customizable** — Keypress sound, haptic feedback, keyboard height

#### 👤 **Account & Plans**
- **Free Plan** — 10 voice translations/day, 3 video translations/day, 50 AI chat messages/day, basic themes
- **Premium** — Unlimited voice, video, AI chat; all premium themes

---

### 3. **How It Works** (3–4 Steps)
- Step 1: Enable DeltaVoice in settings
- Step 2: Choose your mode — type, speak, or record
- Step 3: Process with AI — translate, clone voice, or get text
- Step 4: Share or send — directly to any app

Use simple icons + short copy. Optional: animated step indicators.

---

### 4. **Social Proof**
- User testimonials (even placeholder: "Finally, a keyboard that speaks my language — literally.")
- Stats: "12 languages • 8 voices • 12 AI writing tools"
- Optional: app store rating badge

---

### 5. **Pricing / Plans**
- Free vs Premium comparison table
- Clear value props for Premium: unlimited usage, premium themes

---

### 6. **Final CTA**
- "Download DeltaVoice — Free on Google Play"
- Optional: email signup for updates

---

## 🎭 UI Components to Implement

1. **Glass Cards** — `backdrop-filter: blur()`, semi-transparent background, 1px light border, rounded corners (24–28px)
2. **Purple CTA Buttons** — Gradient or solid `#7C52FF`, hover `#651FFF`, rounded, bold
3. **Circular Icon Buttons** — Like the app’s top bar (mic, AI, camera) — circular, purple fill, white icon
4. **Pill Selectors** — Rounded pills for language/voice selectors
5. **Audio Waveform Bar** — Single bar component for "voice" or "audio" sections — animated height
6. **Floating Keyboard Key** — Reusable key visual for decorative use

---

## ✨ Micro-Interactions & Animations

- **Hover on cards:** Slight lift (translateY -4px), subtle shadow
- **Button hover:** Darker purple, slight scale (1.02)
- **Scroll-triggered reveals:** Fade-in + slide-up for sections (Intersection Observer)
- **Audio waves:** Continuous, smooth CSS or JS animation
- **Floating keys:** Slow, infinite drift (e.g., 20–40s per cycle)

---

## 📱 Responsive & Accessibility

- Mobile-first: hero stacks, grid becomes 1–2 columns
- Ensure contrast ratios (WCAG AA)
- Reduce motion for `prefers-reduced-motion`
- Semantic HTML, clear headings, alt text for images

---

## 🛠 Technical Notes (for AI Builder)

- **Framework:** React, Next.js, or vanilla HTML/CSS/JS — your choice
- **Animations:** CSS `@keyframes`, or Framer Motion, or GSAP for complex sequences
- **Audio waves:** CSS-only (multiple divs with `animation`), or Canvas, or a lightweight lib like `wavesurfer.js` (simplified)
- **Glass effect:** `backdrop-filter: blur(20px)`; fallback solid color for older browsers
- **Performance:** Lazy-load below-fold content; keep background animations lightweight

---

## 📋 Copy Suggestions (Tone: Warm, Confident, Inclusive)

- **Tagline options:** "One keyboard. Every voice." / "Speak it. Type it. Share it." / "Where voice meets text."
- **Voice section:** "Your voice, in any language — or keep it yours and just translate."
- **AI section:** "12 AI writing tools. One tap. Better words."
- **Video section:** "Record. Translate. Dub. All from your keyboard."
- **CTA:** "Get DeltaVoice — Free" / "Start typing smarter"

---

## ✅ Checklist Before Launch

- [ ] All 12 languages listed
- [ ] All 8 voice styles mentioned
- [ ] All 12 AI writing tools listed
- [ ] Voice modes (full, clone, transcript) explained
- [ ] Video recording + translation featured
- [ ] Themes (including premium) shown
- [ ] Free vs Premium comparison
- [ ] Audiowaves animation in background
- [ ] Keyboard key visuals in background
- [ ] Liquid Glass aesthetic throughout
- [ ] Mobile-responsive
- [ ] Clear primary CTA

---

*Use this prompt as your north star. Adapt wording and structure to your builder’s strengths. The goal: a landing page that feels as innovative and delightful as the DeltaVoice keyboard itself.*
