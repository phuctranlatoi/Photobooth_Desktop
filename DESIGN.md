---
title: Pretty Booth Desktop UI Design
generated: 2026-08-27
status: active
scope: Kotlin Compose Desktop kiosk and admin UI
---

# Design

<!-- impeccable:design-schema 1 -->

## Product Direction

Pretty Booth Desktop should feel like a premium event-studio console: bright, confident, tactile, and trustworthy. The guest flow is a kiosk experience, so each screen should make one main decision obvious and keep real visual evidence at the center: live camera, captured photos, print preview, selected frame, payment QR, or album QR.

The admin flow should feel denser and more operational, but still polished. Staff need scanable controls, clear status, safe destructive actions, and enough technical detail to troubleshoot during an event.

## Design Principles

- One screen, one decision, one primary action.
- Real booth evidence is always more valuable than decoration.
- Status should be human-readable for guests and precise enough for staff.
- Touch targets must be large, stable, and easy to hit on a kiosk.
- Admin controls may be compact, but customer screens should breathe.
- UI polish must not change session, camera, payment, printing, upload, frame, or layout logic.

## Visual System

### Color

The interface uses a clean neutral foundation with warm event-studio accents.

- Background: soft light neutral for customer and admin screens.
- Main text: deep ink, not pure black, for softer kiosk readability.
- Secondary text: cool gray for metadata, helper copy, and status details.
- Primary accent: rose-clay/nude for the brand action color.
- Success: mint green for ready/online/complete states.
- Info: blue for camera and neutral system indicators.
- Warning: amber for pending or attention states.
- Error/destructive: red for delete and failure actions.
- Camera surfaces: near-black only where it helps live preview and media stand out.

Avoid returning to a mostly dark UI, a single-color purple/blue theme, or decorative gradient blobs. The booth should feel modern because of hierarchy, spacing, media, and control clarity.

### Typography

Use the shared `KioskTypography` scale from the Compose theme.

- Display text is reserved for the start screen and major completion moments.
- Screen titles should be clear but not oversized.
- Body text should stay readable from standing distance.
- Buttons use strong weight and clear labels.
- Letter spacing remains zero.
- Do not use technical placeholder copy in guest-facing screens.

### Layout

Customer screens follow a two-zone pattern when possible:

- Left or main zone: the current decision or state.
- Right or supporting zone: print preview, QR, live preview, or summary.

The app shell provides a consistent top status bar with:

- Pretty Booth brand.
- Current session step.
- Actual camera/album/printer/payment status.
- Short live status message.

Panel radius should stay restrained and professional. Cards are for repeated items, previews, modals, and framed tools only. Page sections should remain open layouts, not nested card stacks.

## Components

Use the shared components in `CoreComponents.kt` before creating new per-screen styling.

- `KioskPrimaryButton`: main CTA for the current screen.
- `KioskSecondaryButton`: secondary safe actions.
- `KioskBackButton`: navigation back affordance.
- `StatusChip`: compact state display in shell and admin.
- `PanelBox`: framed content such as QR, preview, or grouped controls.
- `SectionHeader`: title, subtitle, and optional badge for screen sections.
- `MomentTile`: photo/layout/frame selectable media card.
- `QuantityCard`, `OutputRow`, `ChoiceRow`, `FrameChoice`: domain-specific controls.

Buttons should use clear verbs in Vietnamese. Icon buttons should use Compose/Lucide-style icons when available; avoid text-only glyph hacks unless the icon library lacks a suitable symbol.

## Customer Flow

### Start

The start screen is the first brand signal. It should show `Pretty Booth`, the promise `Chụp ảnh lấy ngay`, one primary CTA, and either live camera evidence or a refined fallback photostrip. Admin access remains hidden behind repeated brand taps.

### Studio Mode

Layout and effect selection should feel visual and calm. The screen should show large preview cards, a clear selected state, and one continue action. Empty layout states should be explicit and recoverable.

### Quantity

Print quantity selection should use large touch cards, a simple price/order summary, and the print preview. The selected quantity must be obvious without relying only on text.

### Payment

Payment should show the amount, selected options, QR payment area, and payment readiness. Staff override stays visible but secondary. If payment QR data is not configured, the UI should be honest without breaking the flow.

### Prepare and Capture

Prepare should calm the guest before the camera sequence. Capture should prioritize the live view, shot count, countdown, and clear capture action. Debug camera language must stay out of the guest path.

### Select Photos

Selection should combine a generous gallery with the print preview. The CTA remains disabled until enough photos are selected. Selected photos should have a visible accent border and selected state.

### Frame

Frame selection should keep the print preview beside the frame grid. Custom PNG upload remains available as a secondary action. Frame cards should show thumbnail, title, tone, and selected state.

### Confirm

Confirmation should read like a final checklist, not a technical export screen. The guest sees the print preview, chosen layout/frame/effect, copy count, and one primary action to print/create QR.

### Printing

Printing should reassure the guest that work is happening. Show a concise pipeline: compose image, create video, send printer, upload album. The status message may reflect real backend progress, but UI should remain stable.

### Delivery

Delivery is the payoff screen. It should show completion, QR album, output summary, and a clear return-home action. Album opening is secondary.

## Admin Flow

Admin screens use the same palette but may be denser. The header should show operational status chips and a clear exit action. Tabs are short labels:

- Khung
- Bố cục
- Cài đặt
- Công cụ
- Màu ảnh

Admin cards should use light panels, restrained borders, readable metadata, and clear selected/ready states. Destructive actions require confirmation or clear red affordance.

## Motion and Interaction

Motion should be light and purposeful:

- Button press scale is subtle.
- Selection changes should feel immediate.
- Avoid decorative animation that competes with camera or QR.
- Layout dimensions should stay stable so controls do not jump while state changes.

## Accessibility

- Maintain strong contrast on text, status chips, and buttons.
- Avoid relying on color alone for selected/disabled states.
- Keep touch controls large enough for event guests.
- Keep long labels from overflowing inside buttons/cards.
- Do not use tiny technical copy on customer screens.

## Logic Boundary

This redesign is UI-only. It must preserve existing callbacks, state machine behavior, services, payment completion, camera capture paths, frame/layout persistence, rendering, printing, album upload, and delivery QR logic.

Allowed changes:

- Compose layout, typography, color, component styling, button labels, and status presentation.
- Passing already-existing state into UI components for display.
- Adding design documentation and Impeccable metadata.

Avoid changes:

- Business rules.
- Session transition logic.
- Camera, payment, print, upload, storage, or frame-rendering algorithms.
- File formats and persisted model semantics.

## Implementation Map

- Theme and tokens: `src/main/kotlin/com/phuctran/photobooth/desktop/ui/theme/Theme.kt`
- Shared UI kit: `src/main/kotlin/com/phuctran/photobooth/desktop/ui/components/CoreComponents.kt`
- App shell and status bar: `src/main/kotlin/com/phuctran/photobooth/desktop/ui/components/Shell.kt`
- Print preview styling: `src/main/kotlin/com/phuctran/photobooth/desktop/ui/components/PrintPreview.kt`
- Guest screens: `src/main/kotlin/com/phuctran/photobooth/desktop/ui/screens/*Screen.kt`
- Admin screens: `src/main/kotlin/com/phuctran/photobooth/desktop/ui/screens/AdminScreen.kt` and `FilterAdminView.kt`
- App state wiring for display-only props: `src/main/kotlin/com/phuctran/photobooth/desktop/Main.kt`

## Future Polish

- Add screenshot regression checks once the Compose app can be launched reliably in the local environment.
- Replace the deprecated back-arrow icon with the current Compose auto-mirrored icon API.
- Add a small operator-only diagnostic drawer instead of exposing technical status in guest copy.
- Create a preview mode with sample camera/photos/QR data for design QA without hardware.
