# Product

<!-- impeccable:product-schema 1 -->

## Platform

desktop

## Users

Primary users are event guests standing in front of a touch-screen photobooth kiosk. They need to choose a layout, pay, take photos, select the best shots, choose a frame, print, and scan a QR album with very little instruction.

Secondary users are booth operators and staff. They need to manage frames, layouts, filters, camera source, printer behavior, payment, album upload, and troubleshooting during events.

## Product Purpose

Pretty Booth Desktop runs an end-to-end photobooth session on a Windows desktop kiosk. It turns a guest session into printed output and a downloadable album while giving staff enough controls to keep the booth running.

## Positioning

The product combines kiosk flow, live camera preview, layout/frame selection, QR payment, local printing, cloud album upload, and admin tooling in one desktop Compose app.

## Operating Context

The app is used at events where guests are often moving quickly, lighting and noise can vary, and touch interactions must be obvious. The customer flow should feel visual, confident, and low-friction. The admin flow can be denser but must remain readable and safe for live operation.

## Capabilities and Constraints

The redesign must preserve existing business logic and callbacks: session state transitions, camera capture, hot folder import, native camera controls, payment completion, frame storage, layout loading, photo selection, render/print/upload, and delivery QR behavior.

The customer UI should avoid technical placeholders and use true session data where available. Admin tools may show technical details, paths, IDs, and advanced settings.

## Brand Commitments

The product name is Pretty Booth. The interface should feel modern, polished, event-ready, and trustworthy. Existing frame assets, print previews, live camera feed, and QR album behavior are product evidence and should remain central.

## Evidence on Hand

- Kotlin/Compose desktop UI in `src/main/kotlin/com/phuctran/photobooth/desktop/ui`.
- Session flow in `src/main/kotlin/com/phuctran/photobooth/desktop/domain/SessionStateMachine.kt`.
- Layout, effect, frame, capture, and export models in `src/main/kotlin/com/phuctran/photobooth/desktop/model/BoothModels.kt`.
- Existing UI audit in `TAI_LIEU_CAI_THIEN_GIAO_DIEN.md`.
- Local frame assets and runtime frame storage under `frame/` and `data/frames/` when present.

## Product Principles

Every customer screen should ask for one clear decision and show one primary action.

The most important visual should be real product evidence: live camera, captured photos, print preview, frame thumbnail, or QR.

Status text should describe what the system is actually doing, without exposing debug language to guests.

Admin screens should favor scanability, grouped controls, visible state, and safe destructive actions.

UI changes must not change the session logic.

## Accessibility & Inclusion

Touch targets should be large enough for kiosk use, text should remain legible at common desktop kiosk resolutions, and interactive controls should use clear labels and visual states.
