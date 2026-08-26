# Tài liệu cải thiện giao diện Pretty Booth Desktop

Ngày rà soát: 25/08/2026  
Phạm vi: giao diện desktop Kotlin/Compose, công cụ admin/layout, tài nguyên frame và module web `photobooth/` liên quan đến album/theme.

## 1. Tóm tắt điều hành

Project hiện có nền tảng UI khá đầy đủ cho một kiosk photobooth: có flow chọn layout, chọn màu ảnh, chọn số bản in, thanh toán QR, chụp, chọn ảnh, chọn frame, xác nhận, in/upload và trả QR album. Điểm mạnh nhất là flow nghiệp vụ đã rõ, có live view, có preview bản in, có frame custom, có admin quản lý layout/frame/filter/camera.

Tuy nhiên giao diện đang bị lệch giữa các màn hình: chỗ sáng tối giản, chỗ full-bleed camera, chỗ dark admin, chỗ dùng nút nền trắng, chỗ dùng accent nude. Một số trạng thái đang hiển thị cứng như `Cloud Album ON`, `Printer READY`; một số placeholder như `POSE GUIDE`, `Camera Not Available`, `Không có QR`; một số icon đang là ký tự text như `←`. Vì đây là kiosk dùng tại sự kiện, giao diện cần cho người dùng cảm giác tin cậy, nhanh, sang và không phải suy nghĩ nhiều.

Hướng cải thiện đề xuất:

- Chuẩn hóa design system desktop: màu, chữ, spacing, nút, icon, chip trạng thái, panel, preview.
- Biến mỗi màn hình khách hàng thành một quyết định chính, một CTA chính và một trạng thái rõ.
- Dùng hình ảnh thật/live view/final preview làm trung tâm, giảm trang trí trừu tượng.
- Đồng bộ nhận diện desktop với album/web bằng token màu và copywriting.
- Tách UX khách hàng khỏi UX nhân viên/admin: khách hàng ít chữ, admin nhiều thông tin nhưng gọn, có cảnh báo và xác nhận an toàn.

## 2. Nguồn đã rà soát

### Desktop Kotlin/Compose

- `src/main/kotlin/com/phuctran/photobooth/desktop/Main.kt`: điều phối state và route màn hình.
- `src/main/kotlin/com/phuctran/photobooth/desktop/domain/SessionStateMachine.kt`: trạng thái phiên chụp.
- `src/main/kotlin/com/phuctran/photobooth/desktop/model/BoothModels.kt`: layout, effect, frame, captured moment, export summary.
- `src/main/kotlin/com/phuctran/photobooth/desktop/ui/theme/Theme.kt`: token màu hiện tại.
- `src/main/kotlin/com/phuctran/photobooth/desktop/ui/components/Shell.kt`: app shell, top bar, progress stepper, status bar.
- `src/main/kotlin/com/phuctran/photobooth/desktop/ui/components/CoreComponents.kt`: button, panel, row, tile, QR, capture overlay.
- `src/main/kotlin/com/phuctran/photobooth/desktop/ui/components/PrintPreview.kt`: preview bản in.
- `src/main/kotlin/com/phuctran/photobooth/desktop/ui/components/QrCodeView.kt`: QR thanh toán.
- `src/main/kotlin/com/phuctran/photobooth/desktop/ui/screens/*.kt`: toàn bộ màn hình khách hàng và admin.
- `src/main/kotlin/com/phuctran/photobooth/desktop/engine/LayoutCalculatorTool.kt`: UI công cụ tạo layout/frame.
- `src/main/kotlin/com/phuctran/photobooth/desktop/storage/FrameStore.kt`: cấu trúc lưu frame.
- `src/main/kotlin/com/phuctran/photobooth/desktop/controller/DesktopBoothController.kt`: status message, payment, capture, upload, print.

### Module web/PHP

- `photobooth/template/components/*.php`: start, result, action button, gallery, filter, preview.
- `photobooth/assets/sass/framework.scss`: entry Sass.
- `photobooth/assets/sass/components/_root.scss`: CSS variables nền.
- `photobooth/assets/sass/components/_button.scss`: token và style button web.
- `photobooth/assets/sass/components/_stage.scss`: stage full-screen.
- `photobooth/assets/sass/components/_preview.scss`: preview camera.
- `photobooth/assets/sass/components/_gallery.scss`: gallery album.
- `photobooth/assets/sass/themes/_modern.scss`: theme modern.
- `photobooth/src/Configuration/Section/ColorsConfiguration.php`: default màu web.
- `photobooth/src/Configuration/Section/UiConfiguration.php`: cấu hình style/button/start text.

### Tài nguyên hình ảnh

- Thư mục `frame/` có 75 file `.png`, nhiều file frame/punched kích thước lớn.
- `FrameStore` lưu frame runtime theo cấu trúc `data/frames/<printSizeLabel>/<layoutId>/<Standard|Special>/...`.

## 3. Mục tiêu trải nghiệm

### Khách chụp ảnh

Người dùng thường đứng trước kiosk, có ít thời gian, có thể dùng màn hình cảm ứng và đang ở môi trường ồn/đông. Giao diện cần:

- Nhìn 1 giây là biết phải bấm gì.
- Nút chính lớn, rõ, có phản hồi khi chạm.
- Mỗi bước chỉ yêu cầu một quyết định.
- Luôn biết đang ở bước nào và còn bao lâu.
- Khi camera, thanh toán, upload hoặc in có vấn đề, thông báo phải trấn an và hướng dẫn hành động tiếp theo.

### Nhân viên vận hành

Nhân viên cần kiểm tra máy ảnh, máy in, frame, layout, filter, hot folder, payment và album. Giao diện admin cần:

- Nhiều thông tin hơn màn khách hàng nhưng vẫn dễ quét.
- Tách nhóm theo tác vụ: vận hành, nội dung, thiết bị, layout/frame, filter.
- Hành động nguy hiểm như xóa layout/frame phải có xác nhận rõ.
- Có trạng thái thật của camera, printer, album, payment, Firebase, local server.

### Chủ booth/thương hiệu

Giao diện cần nhìn đẹp trên ảnh/video quay lại tại sự kiện. Nên ưu tiên:

- Visual thật: live camera, ảnh mẫu, frame thật, final print.
- Ít placeholder kỹ thuật.
- Copy tiếng Việt tự nhiên.
- Màu thương hiệu nhất quán giữa desktop và album/web.

## 4. Đánh giá hiện trạng

### Điểm mạnh

- Flow desktop đã đầy đủ end-to-end.
- `Main.kt` tách state rõ theo `SessionState`.
- Có `PrintPreview` dùng lại ở nhiều màn.
- Có `BouncyButton` tạo cảm giác chạm tốt.
- Màn chọn filter đã dùng live view full-bleed, đúng tinh thần photobooth.
- Capture screen có crop guide theo aspect ratio layout.
- Delivery có QR thật qua `RealQr`.
- Admin có nhiều chức năng quan trọng: frame, layout, settings, camera native, layout calculator, filter editor.
- Web module có hệ CSS variables phong phú, stage full-screen và gallery responsive.

### Vấn đề chính

- Chưa có design system đủ mạnh: màu, radius, shadow, nút và typography rải rác trong từng file.
- Top bar hiển thị trạng thái cứng, chưa phản ánh config thật: `Cloud Album ON`, `Printer READY`.
- Progress tổng bị bỏ trong `AppShell`, trong khi nhiều screen vẫn dùng badge bước thủ công và số bước chưa nhất quán.
- CTA chính không thống nhất: có màn dùng `AccentNude`, có màn dùng `NeutralPanel`, có màn dùng `Button` mặc định.
- Một số icon dùng text `←` thay vì icon thực.
- Start screen nhận `liveViewBitmap` nhưng không dùng; phần visual là minh họa tự vẽ và hiệu ứng gradient/orb, chưa phải tín hiệu sản phẩm thật.
- Admin pha theme sáng/tối: nền chung sáng nhưng card layout/frame lại tối `#27273A`, chữ nhóm frame có chỗ màu trắng trên nền sáng.
- Màn chọn ảnh thiếu preview final print bên cạnh, nên người dùng khó hình dung ảnh đã chọn sẽ lên bản in thế nào.
- Màn in/upload chưa dùng `statusMessage` chi tiết từ controller, nên người dùng chỉ thấy trạng thái chung.
- QR có hai implementation: `QrCodeView` và `RealQr`, dễ lệch style/behavior.
- Filter admin có slider nhưng chưa có preview trực tiếp kết quả màu.
- Layout carousel có lỗi hiển thị title: `pagerState.currentPage` là index ảo rất lớn, nhưng bottom title đang gọi `layouts.getOrNull(pagerState.currentPage)`, nên thường không hiện title. Cần map modulo như phần page item.

## 5. Nguyên tắc thiết kế đề xuất

### 5.1 Một màn hình, một quyết định chính

Mỗi màn khách hàng nên có:

- Một tiêu đề ngắn.
- Một câu phụ tối đa 1 dòng.
- Một vùng hình ảnh/preview chính.
- Một CTA chính.
- Một nút quay lại phụ nếu cần.

Ví dụ:

- Start: quyết định duy nhất là bắt đầu.
- Studio layout: chọn bố cục.
- Studio effect: chọn màu ảnh.
- Quantity: chọn số bản in.
- Payment: quét QR hoặc nhân viên xác nhận tiền mặt.
- Capture: bấm chụp, sau đó xem countdown.
- Select photos: chọn đủ số ảnh.
- Frame: chọn khung.
- Confirm: xác nhận in và tạo QR.
- Delivery: quét QR, về trang chủ.

### 5.2 Hình ảnh thật là trung tâm

Photobooth là sản phẩm thị giác. Giao diện nên hạn chế minh họa trừu tượng khi đã có live camera, frame thật, ảnh vừa chụp hoặc bản in final.

Ưu tiên thứ tự visual:

1. Live camera.
2. Ảnh vừa chụp.
3. Preview bản in với frame thật.
4. Ảnh mẫu branded.
5. Minh họa/vector chỉ dùng khi chưa có dữ liệu.

### 5.3 Trạng thái phải là trạng thái thật

Không nên hiển thị `Printer READY` nếu config tắt print hoặc chưa kiểm tra máy in. Không nên hiển thị `Cloud Album ON` nếu thiếu key upload. Status nên đến từ controller/config:

- Album: `Online`, `Offline`, `Local fallback`, `Chưa cấu hình`.
- Printer: `Sẵn sàng`, `Tắt`, `Chưa kiểm tra`, `Lỗi`.
- Camera: `Live`, `Chưa kết nối`, `Hot folder`, `Native Canon`.
- Payment: `PayOS`, `Tiền mặt`, `Chưa cấu hình`.

### 5.4 Nút chính phải luôn nhận diện được

CTA chính nên luôn dùng cùng một component và màu:

- Primary: nền brand/accent, chữ trắng, cao 56-80dp.
- Secondary: nền trắng hoặc trong suốt, border rõ.
- Danger: đỏ, chỉ trong admin hoặc dialog xác nhận.
- Staff-only: không đặt như CTA khách hàng, nên cần long press/PIN hoặc nằm trong vùng staff.

### 5.5 Admin là công cụ, không phải kiosk

Màn khách hàng nên giàu visual, ít chữ. Admin nên:

- Nhiều bảng, chip, search, filter, tab.
- Ít animation.
- Density cao hơn nhưng vẫn rõ.
- Có warning/error/success state.
- Có breadcrumb hoặc sidebar thay vì tab ngang quá dài.

## 6. Design system đề xuất cho desktop

### 6.1 Token màu

Theme hiện tại:

- `NeutralBg = #F7F8FA`
- `NeutralPanel = #FFFFFF`
- `NeutralText = #1A1A24`
- `NeutralMuted = #A1A5AB`
- `NeutralBorder = #E5E7EB`
- `AccentNude = #DAB39A`
- `AccentNudeLight = #FAF5F0`

Đề xuất mở rộng thành palette có đủ vai trò:

| Token | Màu đề xuất | Vai trò |
| --- | --- | --- |
| `Bg` | `#F6F7F9` | Nền app sáng, sạch |
| `Surface` | `#FFFFFF` | Panel/card/form |
| `SurfaceAlt` | `#F1F3F6` | Row, chip, empty state |
| `TextPrimary` | `#17171F` | Chữ chính |
| `TextSecondary` | `#626A75` | Chữ phụ, metadata |
| `TextMuted` | `#8C939E` | Hint, placeholder |
| `Border` | `#DDE2EA` | Border thường |
| `Brand` | `#C9896B` | CTA chính, selected state |
| `BrandSoft` | `#FFF1EA` | Nền selected nhẹ |
| `Ink` | `#252228` | Nút tối, overlay chữ |
| `Success` | `#16A34A` | Print/upload OK |
| `Info` | `#2563EB` | Album/network info |
| `Warning` | `#D97706` | Chờ, cấu hình thiếu |
| `Danger` | `#DC2626` | Xóa, lỗi nghiêm trọng |
| `CameraBlack` | `#050507` | Capture/live view |

Lưu ý: `AccentNude` đang đẹp nhưng dễ bị nhạt nếu dùng quá nhiều. Nên dùng làm brand/CTA/select, còn nền và text giữ trung tính. Không để toàn UI chỉ là các biến thể nude/beige.

### 6.2 Typography

Đề xuất dùng scale cố định, không scale theo viewport:

| Style | Cỡ | Weight | Dùng cho |
| --- | ---: | ---: | --- |
| `Display` | 56sp | Black | Start hero, countdown phụ |
| `ScreenTitle` | 36sp | Black | Tiêu đề màn chính |
| `PanelTitle` | 24sp | Bold | Tiêu đề panel |
| `SectionTitle` | 18sp | Bold | Nhóm trong admin |
| `BodyLarge` | 18sp | Medium | Mô tả kiosk |
| `Body` | 16sp | Regular | Text thường |
| `Caption` | 12-13sp | Medium | Badge, metadata |
| `ButtonLarge` | 20sp | Black | CTA kiosk |
| `Button` | 16sp | Bold | Nút thường |

Tiếng Việt nên tránh viết hoa toàn bộ quá nhiều. Chỉ CTA quan trọng có thể viết hoa. Ví dụ:

- Tốt: `Bắt đầu chụp`, `Tiếp tục`, `In và tạo QR`.
- Chỉ dùng uppercase cho nhãn ngắn: `HOÀN TẤT`, `QR`.

### 6.3 Spacing và layout

Sử dụng lưới 8dp:

- Screen padding: 24-32dp.
- Khoảng cách giữa panel chính: 16-24dp.
- Padding panel: 24dp.
- Padding row/card: 12-16dp.
- Gap trong toolbar: 8-12dp.
- Touch target: tối thiểu 56dp, CTA kiosk 64-80dp.

Không nên lồng card trong card. Với các vùng lớn, dùng layout full-width hoặc panel độc lập. Card chỉ dùng cho item lặp lại như frame, layout, ảnh, filter.

### 6.4 Radius và shadow

Hiện code dùng nhiều radius 20-24dp và shadow 24dp. Nên giảm để UI tinh gọn hơn:

- Button/control: 8-12dp.
- Panel: 12-16dp.
- Media preview lớn: 16dp.
- QR/ticket: 16-20dp nếu muốn cảm giác mềm.
- Item card trong admin: 8dp.
- Shadow: nhẹ, không dùng shadow đen alpha 0.5 trên nền sáng.

Đề xuất shadow:

- Panel: `0 8 24` alpha 0.08.
- Floating action trên camera: alpha 0.18.
- Selected card: border brand 2dp + nền soft, ít cần shadow.

### 6.5 Component cần chuẩn hóa

Nên tạo/đổi các component trong `ui/components`:

- `KioskPrimaryButton`: CTA lớn, nhất quán.
- `KioskSecondaryButton`: quay lại/hủy.
- `KioskIconButton`: nút icon thật, có content description.
- `StatusChip`: trạng thái thiết bị/album/printer.
- `StepRail` hoặc `KioskStepper`: hiển thị bước thật dựa trên `SessionState`.
- `ScreenHeader`: badge bước, title, subtitle, optional action.
- `MediaPanel`: khung preview ảnh/live view/fallback.
- `EmptyState`: icon, title, mô tả, action phụ.
- `ErrorState`: lỗi có hướng xử lý.
- `QrCard`: dùng chung cho thanh toán và delivery.
- `PrintPreviewPanel`: wrap `PrintPreview` với caption và loading/error state.
- `FrameThumbnailCard`: thumbnail frame, tag Standard/Special, layout/size.
- `FilterPreviewCard`: filter chip có màu và preview.
- `ConfirmDialog`: dùng cho xóa layout/frame và staff override.

### 6.6 Icon

Thay text icon bằng Material Icons/Compose Icons:

- `←` -> `Icons.Default.ArrowBack`
- `+ Thêm` -> icon add + text
- `Xóa` -> trash/delete icon
- `In` -> print icon
- `QR` -> QR/icon nếu có hoặc custom vector gọn
- `Mở thư mục` -> folder icon
- `Camera` -> camera icon

Nút chỉ có icon cần tooltip hoặc content description. Nút khách hàng chính nên có icon + text nếu lệnh chưa quá rõ.

### 6.7 Motion

Giữ `BouncyButton` vì hợp màn cảm ứng, nhưng motion cần thống nhất:

- Press scale: 0.96-0.98, duration nhanh.
- Screen transition: crossfade 250-350ms.
- Countdown: scale nhẹ + shadow, không giật.
- Flash capture: trắng 50ms vào, fade 300-500ms ra.
- Loading/in/upload: progress theo bước, tránh spinner vô hạn nếu có thể.

## 7. Kiến trúc thông tin đề xuất

### 7.1 Flow khách hàng

Flow hiện tại nên giữ, nhưng rename/nhóm lại cho rõ:

1. `IDLE`: Trang chào.
2. `SELECTING`: Chọn gói chụp gồm bố cục và màu.
3. `SELECTING_QUANTITY`: Chọn số bản in.
4. `PAYMENT_PENDING`: Thanh toán.
5. `PREPARING`: Chuẩn bị tạo dáng.
6. `LIVE_VIEW`, `COUNTDOWN`, `CAPTURING`: Chụp ảnh.
7. `SELECTING_PHOTOS`: Chọn ảnh in.
8. `EDITING`: Chọn frame.
9. `PRINT_PENDING`: Xác nhận bản in.
10. `PRINTING`: Render, in, upload, tạo QR.
11. `DELIVERY`: Nhận ảnh và tải album.

### 7.2 Flow admin

Nên tách thành nhóm:

- Dashboard: trạng thái camera, printer, album, payment, local server.
- Nội dung: layout, frame, filter.
- Thiết bị: camera source, Canon controls, hot folder, printer.
- Công cụ: layout/frame calculator.
- Cấu hình: env, booth ID, album expiry, payment.

Tab ngang hiện có 5 tab, tên dài. Nếu thêm nữa sẽ chật. Sidebar trái hoặc segmented navigation hai cấp sẽ hợp hơn.

### 7.3 Desktop và web

Desktop là kiosk vận hành. Web `photobooth/` có stage/gallery/theme và có thể là module album hoặc legacy UI. Nên đồng bộ:

- Màu brand.
- Typography/copy cơ bản.
- Button radius.
- QR card style.
- Gallery/album completion state.

Mapping token gợi ý:

| Desktop | Web CSS variable |
| --- | --- |
| `Brand` | `--primary-color` |
| `BrandSoft` | `--primary-light-color` |
| `Ink` | `--secondary-color` |
| `TextPrimary` | `--font-secondary` hoặc custom |
| `Surface` | `--box-color` |
| `Border` | `--border-color` |
| `Success` | `--success-color` |
| `Warning` | `--warning-color` |
| `Danger` | `--error-color` |

## 8. Cải thiện từng màn hình desktop

### 8.1 AppShell và TopBar

File liên quan: `Shell.kt`, `Main.kt`, `DesktopBoothController.kt`.

Hiện trạng:

- `AppShell` luôn có top bar cao 64dp.
- `ProgressStepper` đã bị comment là removed.
- Top bar hiển thị `PHOTOBOOTH KIOSK`, `Session: STATE`.
- Chip `Cloud Album ON` và `Printer READY` là hard-code.

Cải thiện:

- Top bar nên có 3 vùng: brand, step/status, thiết bị.
- Chỉ hiển thị top bar ở các màn cần điều hướng. Capture full-screen có thể dùng overlay riêng.
- Trạng thái thiết bị lấy từ config/controller:
  - Album: `Cloud`, `Local`, `Off`.
  - Printer: `Ready`, `Off`, `Check`.
  - Camera: `Live`, `Hot folder`, `No camera`.
- Thêm step indicator compact: `Bước 3/8 - Thanh toán`.
- Ẩn `Session: STATE` khỏi khách hàng, chuyển vào admin/debug. Nếu cần debug, dùng long press logo hoặc shortcut staff.

Đề xuất copy:

- Brand: `Pretty Booth`
- Step text: `Chọn màu ảnh`, `Thanh toán`, `Đang chụp`
- Status chip: `Camera live`, `Album cloud`, `Máy in tắt`

### 8.2 StartScreen

File liên quan: `StartScreen.kt`.

Hiện trạng:

- Nhận `liveViewBitmap` nhưng không dùng.
- Trái là text `PHOTOBOOTH`, `STUDIO EDITION`, mô tả, pill thiết bị.
- Phải là minh họa photostrip tự vẽ, gradient, orb, watermark `STUDIO`.
- Admin mở bằng tap 5 lần vào chữ `PHOTOBOOTH`.

Cải thiện:

- Dùng live view nếu có: bên phải là camera preview mềm, có crop nhẹ hoặc frame overlay.
- Nếu không có live view, dùng carousel frame/ảnh mẫu thật từ `frame/` hoặc ảnh demo.
- Giảm chữ: title, subtitle, CTA.
- CTA chính đổi thành `Bắt đầu chụp`.
- Pill thiết bị chỉ hiện trạng thái thật.
- Admin shortcut nên có vùng staff kín hơn: long press logo 2 giây hoặc tap 5 lần + feedback nhỏ cho nhân viên. Không nên làm người dùng vô tình vào admin.
- Loại bỏ decorative orbs nếu thay được bằng visual thật. Nếu vẫn giữ minh họa, nên dùng ảnh frame thật để booth nhìn thương mại hơn.

Layout đề xuất:

- Trái 45%: logo, headline, CTA.
- Phải 55%: live camera hoặc frame/photo strip preview.
- Dưới CTA: 2-3 trust chips: `In lấy ngay`, `Tải ảnh bằng QR`, `Album giữ 7 ngày`.

### 8.3 StudioModeScreen - chọn layout

File liên quan: `StudioModeScreen.kt`, `PrintPreview.kt`.

Hiện trạng:

- Có step 1 `Bố cục`, step 2 `Màu ảnh`.
- Layout dùng `HorizontalPager` vô hạn.
- Preview dùng `PrintPreview` với empty moments.
- Click item giữa để sang step 2.
- Bottom title đang dùng `layouts.getOrNull(pagerState.currentPage)`, có thể không hiện do page index ảo.

Cải thiện:

- Sửa title bottom bằng modulo:
  - `val actualIndex = pagerState.currentPage % layouts.size`
  - `val currentLayout = layouts.getOrNull(actualIndex)`
- Mỗi layout preview cần có metadata rõ:
  - Tên layout.
  - Khổ in.
  - Chụp bao nhiêu ảnh.
  - Chọn bao nhiêu ảnh.
  - Giá cơ bản.
- Thêm state khi Firebase không tải được:
  - `Chưa tải được bố cục`
  - `Thử tải lại`
  - `Dùng layout mặc định`
- Nên có nút `Tiếp tục` riêng thay vì chỉ click card giữa, vì người dùng kiosk quen bấm CTA. Có thể vẫn cho click card.
- Với layout nhiều frame, có thể show tag `5x15`, `10x15`, `15x20`, `Special`.

### 8.4 StudioModeScreen - chọn filter/màu ảnh

File liên quan: `StudioModeScreen.kt`, `BoothModels.kt`, `DesktopImageProcessor.kt`.

Hiện trạng:

- Live view full-bleed rất tốt.
- Filter selector ở bottom dạng card ngang.
- Nếu không có camera, hiển thị nền đen và text `Camera Not Available`.

Cải thiện:

- Dịch fallback: `Camera chưa sẵn sàng`.
- Fallback cần hướng dẫn:
  - `Kiểm tra cáp/nguồn máy ảnh`
  - `Mở Admin` chỉ cho staff hoặc `Thử lại`
- Card filter nên có preview nhỏ hoặc swatch có tên filter, mô tả ngắn.
- Selected filter cần rõ hơn: border trắng + check icon + brand background.
- Thêm `Normal`, `Đen trắng`, `Vintage` bằng tiếng Việt nếu khách hàng Việt là chính.
- CTA `Tiếp tục` nên cùng style primary button.
- Back button dùng icon thật, nền kính nhẹ trên camera.

### 8.5 QuantityScreen

File liên quan: `QuantityScreen.kt`.

Hiện trạng:

- Panel trái chọn 1-4 bản in.
- Bên phải là `PrintPreview`.
- CTA `Thanh toán` đang dùng nền `NeutralPanel`, chưa nổi bật.

Cải thiện:

- CTA `Thanh toán` dùng primary brand.
- Card số lượng nên to hơn nếu màn cảm ứng: 120-140dp.
- Thêm price breakdown:
  - `Giá gói`
  - `Số bản in`
  - `Tổng`
- Nếu có chính sách giá theo bản in khác `basePrice * quantity`, cần tách config.
- Chữ phụ nên nói rõ: `File mềm luôn gồm toàn bộ ảnh đã chụp`.
- Bên phải preview nên có caption `Bản in dự kiến`.

### 8.6 PaymentScreen

File liên quan: `PaymentScreen.kt`, `PaymentService.kt`.

Hiện trạng:

- Center ticket 420dp, QR PayOS nếu configured.
- Nếu chưa configured: `Hệ thống thanh toán lỗi`.
- Staff override `Nhân viên: Thu tiền mặt` nằm bottom, click là paid.

Cải thiện:

- Không để staff override quá dễ bấm. Nên dùng một trong:
  - Long press 2 giây.
  - PIN nhân viên.
  - Chỉ hiện khi bật staff mode/admin.
- Trạng thái QR:
  - `Đang tạo mã`
  - `Chờ thanh toán`
  - `Đã nhận thanh toán`
  - `Mã hết hạn, tạo lại`
  - `Không cấu hình PayOS, gọi nhân viên`
- Hiển thị mã đơn nhỏ để đối soát.
- QR card nên có viền và quiet zone ổn định, không resize.
- Thêm text an toàn: `Vui lòng giữ màn hình này đến khi hệ thống tự chuyển bước`.
- Back button dùng icon thật.

### 8.7 PrepareScreen

File liên quan: `PrepareScreen.kt`, `CoreComponents.kt`.

Hiện trạng:

- Dùng `CameraPoseGuide` placeholder text `POSE GUIDE`.
- Text `Mỗi tấm có 3 giây`.

Cải thiện:

- Thay placeholder bằng pose guide thật:
  - Live camera mờ + crop frame.
  - Hoặc hình minh họa dáng đứng theo layout.
  - Hoặc preview khung ảnh đã chọn nếu chọn frame trước trong tương lai.
- Thêm countdown chuẩn bị 3-5 giây trước vào chụp.
- Dùng copy thân thiện:
  - `Sẵn sàng nhé`
  - `Nhìn vào camera, ảnh đầu tiên bắt đầu sau 3 giây`
- Nếu layout có `shotCount`, hiển thị `Sẽ chụp 4 ảnh`.

### 8.8 CaptureScreen

File liên quan: `CaptureScreen.kt`.

Hiện trạng:

- Full-screen black, live view, crop overlay, progress pill.
- Button chụp dạng shutter tròn.
- Countdown lớn 240sp.
- Flash effect khi capture.
- Nếu camera devices empty: `Camera chưa sẵn sàng`.

Cải thiện:

- Progress nên hiển thị ảnh đang chuẩn bị chụp: `Ảnh 1/4`, `Ảnh 2/4`, thay vì chỉ captured count nếu gây cảm giác 0/4 trước lần đầu.
- Thêm trạng thái đang lưu với animation ngắn: `Đang lưu ảnh 2`.
- Crop overlay nên có label nhỏ `Vùng ảnh sẽ in`.
- Nếu capture fail, hiện:
  - `Không chụp được ảnh`
  - `Chụp lại`
  - `Gọi nhân viên`
- Có thể thêm retake sau mỗi ảnh cho staff mode, nhưng với flow kiosk nhanh thì mặc định không hỏi.
- Capture button chỉ hiện khi state thật sự chờ bấm, không cần gồm `IDLE` hoặc `SELECTING_QUANTITY`.
- Nếu đang quay video phụ, hiển thị chấm đỏ/recording nhỏ.

### 8.9 SelectPhotosScreen

File liên quan: `SelectPhotosScreen.kt`, `MomentTile`.

Hiện trạng:

- Header + CTA.
- Grid ảnh 4 cột.
- Khi đã chọn ít nhất một ảnh, ảnh chưa chọn bị dim.
- CTA enabled khi chọn đủ.

Cải thiện:

- Không dim toàn bộ ảnh chưa chọn khi chưa đủ số lượng, vì người dùng sẽ tưởng ảnh bị khóa. Chỉ dim khi đã chọn đủ, hoặc dùng overlay nhẹ `Chọn thêm`.
- Thêm preview bản in bên phải hoặc sticky bottom selected tray:
  - `Ảnh in: 2/4`
  - Thứ tự chọn hiện rõ.
- Cho phép bỏ chọn dễ thấy.
- Nếu capturedMoments ít hơn layout.selectCount, có empty/error state:
  - `Chưa đủ ảnh để in`
  - `Chụp lại`
- CTA copy: `Tiếp tục chọn khung`.
- Ảnh nên có kích thước ổn định để không nhảy layout.

### 8.10 FrameScreen

File liên quan: `FrameScreen.kt`, `FrameChoice`, `FrameStore.kt`.

Hiện trạng:

- Trái preview final, phải grid frame 2 cột.
- Có nút `Thêm PNG`.
- Frame custom load thumbnail nếu có.

Cải thiện:

- Nhóm frame theo:
  - `Tất cả`
  - `Chuẩn`
  - `Sự kiện`
  - `Mới thêm`
- Hiển thị tag:
  - `5 x 15`
  - `layout id`
  - `Special`
- Nếu frame không phù hợp layout/print size, không show hoặc show disabled kèm lý do.
- Nút `Thêm PNG` chỉ nên hiện trong admin/staff mode, không phải flow khách hàng.
- CTA `Xem bản in` nên là primary brand.
- Thêm empty state:
  - `Chưa có khung cho bố cục này`
  - `Dùng khung mặc định`
- Thumbnail frame lớn cần cache ổn định, tránh load lại gây giật.

### 8.11 ConfirmScreen

File liên quan: `ConfirmScreen.kt`.

Hiện trạng:

- Trái preview final.
- Phải summary output: in, upload ảnh, upload video, style, frame.
- CTA `In và tạo QR` dùng nền trắng.

Cải thiện:

- Đổi title: `Kiểm tra lần cuối`.
- CTA primary: `In và tạo QR`.
- Summary nên phân cấp:
  - `Bản in`: số ảnh và số bản.
  - `Album`: toàn bộ ảnh gốc và video.
  - `Phong cách`: filter, frame.
- Nếu printer disabled, CTA copy nên là `Tạo ảnh và QR`.
- Nếu album chưa cấu hình, warning chip: `QR sẽ dùng local server` hoặc `Không có QR`.
- Có thể thêm checkbox staff-only `Đã kiểm tra giấy in` nếu booth vận hành thủ công.

### 8.12 PrintingScreen

File liên quan: `PrintingScreen.kt`, `DesktopBoothController.kt`.

Hiện trạng:

- Spinner + `Đang in ảnh`.
- Info pill: layout, frame, `Đang tạo album`.
- Controller thực ra có status chi tiết: render, print, upload, hoàn tất.

Cải thiện:

- Dùng `statusMessage` từ controller để hiển thị trạng thái thật.
- Tách pipeline thành step:
  - `Ghép ảnh`
  - `Gửi máy in`
  - `Upload album`
  - `Tạo QR`
- Mỗi step có trạng thái: waiting, active, done, failed.
- Nếu print disabled: step `Lưu file in` thay vì `Gửi máy in`.
- Nếu upload fail nhưng local fallback có QR, hiển thị rõ.
- Không để spinner vô hạn nếu lỗi.

### 8.13 DeliveryScreen

File liên quan: `DeliveryScreen.kt`, `RealQr`.

Hiện trạng:

- `HOÀN TẤT!`, hướng dẫn nhận ảnh/tải file mềm.
- QR card nếu có `summary.qrUrl`.
- TextButton mở output nếu có.
- CTA về trang chủ.

Cải thiện:

- Thêm preview final print hoặc thumbnail album để người dùng tự tin QR đúng.
- QR card có caption rõ:
  - `Quét để tải ảnh`
  - `Album giữ trong 7 ngày` nếu dùng `albumExpiresInDays`.
- Nếu local fallback: `Điện thoại cần cùng Wi-Fi với booth`.
- Nếu không có QR: show staff action, không chỉ `Không có QR`.
- Có auto-return sau 60-90 giây với progress bar nhỏ.
- CTA `Về trang chủ` primary, có thể thêm `Mở album` staff/debug.

## 9. Cải thiện Admin UX

### 9.1 AdminScreen tổng thể

File liên quan: `AdminScreen.kt`.

Hiện trạng:

- Header `Quản trị hệ thống`.
- 5 tab ngang: frame, layout, cài đặt, tạo layout/frame, màu sắc.
- Một số tab dùng theme sáng, tab calculator dùng dark theme riêng.

Cải thiện:

- Đổi sang sidebar trái để mở rộng:
  - Dashboard
  - Frame
  - Layout
  - Filter màu
  - Thiết bị
  - Công cụ
  - Cấu hình
- Header top admin có status chips:
  - Camera
  - Printer
  - Album
  - Payment
  - AppData path
- Dùng cùng token màu với desktop nhưng density cao hơn.
- Card admin dùng nền trắng, border nhẹ, radius 8dp. Không dùng card tối trừ khi toàn admin chuyển sang dark mode nhất quán.
- Text kỹ thuật đặt trong monospace hoặc metadata row.

### 9.2 Quản lý frame

Hiện trạng:

- Load custom frames từ app data.
- Group theo print size/layout.
- Card tối, thumbnail, path, nút xóa.
- Xóa file trực tiếp bằng `Files.deleteIfExists`.

Cải thiện:

- Search frame theo tên/path/layout.
- Filter: print size, layout, Standard/Special.
- Thumbnail theo aspect ratio thật, có fallback.
- Xóa cần dialog:
  - Tên frame.
  - Đường dẫn.
  - Cảnh báo không hoàn tác.
  - Nút `Xóa frame` danger.
- Có thể chuyển vào recycle/backup folder trước khi xóa hẳn.
- Có nút `Mở thư mục frame`.
- Hiển thị số frame trong từng nhóm.

### 9.3 Quản lý layout

Hiện trạng:

- List layout, ID, size, shot count, description, xóa có confirm inline.

Cải thiện:

- Thêm preview mini layout bằng `MiniLayoutPreview`.
- Search theo ID/title/print size.
- Filter theo print size.
- Hiển thị nguồn: Firebase, fallback, local/generated.
- Khi xóa layout, kiểm tra frame đang target layout đó và cảnh báo.
- Thêm action `Xem chi tiết slots`.

### 9.4 Cài đặt thiết bị

Hiện trạng:

- Toggle Windows print.
- Radio webcam/hot folder.
- Hot folder path.
- Native Canon camera params nếu service tồn tại.

Cải thiện:

- Tách thành card:
  - Máy in.
  - Nguồn camera.
  - Canon camera.
  - Album/payment.
- Có nút `Kiểm tra` cho mỗi nhóm.
- Camera source nên có trạng thái:
  - `Đang dùng Native SDK`
  - `Đang dùng Hot Folder`
  - `Đang dùng Webcam`
- Native params nên có loading/error state thay vì `N/A` trống.
- `Lưu cài đặt` sticky footer.
- Sau khi lưu, show banner `Cần khởi động lại app` với action staff.

### 9.5 FilterAdminView

File liên quan: `FilterAdminView.kt`, `BoothModels.kt`.

Hiện trạng:

- List filter trái, form/sliders phải.
- Chưa có preview.
- Slider label màu trắng dù nền phải có thể sáng, dễ lỗi tương phản.

Cải thiện:

- Thêm preview live hoặc ảnh mẫu:
  - Ảnh gốc.
  - Ảnh sau filter.
- Sliders có reset về default.
- Hiển thị range và value theo đơn vị dễ hiểu:
  - `Độ bão hòa 100%`
  - `Tương phản 120%`
  - `Độ sáng +5`
- Có nút `Nhân bản filter`.
- Có trạng thái unsaved changes.
- Save button sticky.
- Chặn xóa filter đang được chọn mặc định nếu không có filter thay thế.

### 9.6 Layout Calculator Tool

File liên quan: `LayoutCalculatorTool.kt`, `SlotDetectionEngine.kt`.

Hiện trạng:

- Dark tool, chọn PNG, detect slot, preview frame punched, xuất Firebase/local frame.
- Có code legacy text.
- Có overlay đỏ/yellow/green để debug.

Cải thiện:

- Biến thành wizard:
  1. Chọn frame PNG.
  2. Kiểm tra lỗ ảnh.
  3. Nhập metadata layout/frame.
  4. Lưu layout Firebase.
  5. Lưu frame local.
- Tách preview dành cho designer và debug overlay:
  - Tab `Preview`
  - Tab `Debug`
- Validation trước khi lưu:
  - `layoutId` slug hợp lệ.
  - `frameId` slug hợp lệ.
  - print size chuẩn.
  - event name nếu special.
- Sau khi lưu, show toast/banner và path.
- `Copy Code` chuyển vào khu vực advanced.

## 10. Cải thiện module web/album

### 10.1 Theme/token

Web đã có hệ CSS variables tốt. Nên tạo một theme Pretty Booth riêng thay vì dùng default xanh:

- `primary`: brand nude/rose clay.
- `primary_light`: nền soft.
- `secondary`: ink/dark.
- `highlight`: brand sáng hơn.
- `font`: text trên nền tối.
- `font_secondary`: text trên nền sáng.
- `panel`: surface/card.
- `border`: border nhẹ.

Nếu album web là trải nghiệm khách hàng sau khi quét QR, nó nên nhìn cùng họ với desktop.

### 10.2 Stage/result

Web `stage--result` dùng ảnh nền blur và ảnh chính phía trên. Desktop delivery có thể học pattern này:

- Nền là final photo blur nhẹ.
- QR và text nằm trong panel trắng.
- CTA nằm dưới.

Nếu vẫn dùng web result, nên:

- Đảm bảo button labels tiếng Việt đầy đủ.
- QR, gallery, download có icon nhất quán.
- Button mode `modern_squared` có label đủ dễ hiểu.

### 10.3 Gallery

Web gallery có grid responsive. Đề xuất:

- Thêm header brand `Pretty Booth`.
- Thêm album metadata: ngày, số ảnh, thời hạn.
- CTA tải tất cả nếu backend hỗ trợ.
- Empty state khi album chưa ready.
- Loading state khi Cloudinary/album đang finalize.

## 11. Backlog triển khai theo ưu tiên

### P0 - Sửa nhanh, ảnh hưởng lớn

| Việc | File chính | Kết quả |
| --- | --- | --- |
| Sửa title layout carousel bằng modulo | `StudioModeScreen.kt` | Tên layout luôn hiện đúng |
| Chuẩn hóa CTA primary ở Quantity, Frame, Confirm | `QuantityScreen.kt`, `FrameScreen.kt`, `ConfirmScreen.kt` | Người dùng biết nút chính |
| Thay `←` bằng icon `ArrowBack` | `PaymentScreen.kt`, `StudioModeScreen.kt` | UI chuyên nghiệp hơn |
| TopBar dùng status thật hoặc bỏ chip cứng | `Shell.kt`, `Main.kt` | Không đánh lừa trạng thái thiết bị |
| Dịch fallback `Camera Not Available` | `StudioModeScreen.kt` | Copy thống nhất tiếng Việt |
| Không dim ảnh chưa chọn quá sớm | `CoreComponents.kt`, `SelectPhotosScreen.kt` | Chọn ảnh dễ hiểu hơn |

### P1 - Design system nền

| Việc | File chính | Kết quả |
| --- | --- | --- |
| Mở rộng token màu/theme | `Theme.kt` | Có đủ success/warning/error/info |
| Tạo button/icon/status component | `CoreComponents.kt` hoặc file mới | Giảm lặp style |
| Tạo `ScreenHeader`, `StepIndicator` dùng chung | `CoreComponents.kt`, `Shell.kt` | Bước nhất quán |
| Gộp QR component | `QrCodeView.kt`, `CoreComponents.kt` | Thanh toán/delivery cùng style |
| Chuẩn hóa radius/shadow/padding | Toàn `ui/screens` | UI gọn và đồng nhất |

### P2 - Polish flow khách hàng

| Việc | File chính | Kết quả |
| --- | --- | --- |
| Start dùng live view hoặc ảnh/frame thật | `StartScreen.kt` | First screen có tín hiệu sản phẩm |
| Layout screen có metadata và CTA riêng | `StudioModeScreen.kt` | Chọn bố cục rõ hơn |
| Filter selector có preview/check icon | `StudioModeScreen.kt` | Chọn màu trực quan hơn |
| Payment có timeout/status/staff override an toàn | `PaymentScreen.kt` | Giảm rủi ro thao tác nhầm |
| Prepare có pose guide thật | `PrepareScreen.kt`, `CaptureScreen.kt` | Người dùng chuẩn bị tốt hơn |
| Select photos có final preview/tray | `SelectPhotosScreen.kt` | Thấy ngay ảnh in sẽ ra sao |
| Printing có pipeline progress | `PrintingScreen.kt`, `Main.kt`, controller | Trạng thái in/upload rõ |
| Delivery có final preview + auto return | `DeliveryScreen.kt` | Trải nghiệm hoàn tất đẹp hơn |

### P3 - Admin và vận hành

| Việc | File chính | Kết quả |
| --- | --- | --- |
| Admin sidebar + dashboard status | `AdminScreen.kt` | Dễ vận hành |
| Frame manager có search/filter/dialog xóa | `AdminScreen.kt`, `FrameStore.kt` | Quản lý frame an toàn |
| Layout manager có mini preview | `AdminScreen.kt`, `PrintPreview.kt` | Dễ nhận diện layout |
| Filter editor có preview before/after | `FilterAdminView.kt` | Tạo filter chính xác |
| Layout calculator thành wizard | `LayoutCalculatorTool.kt` | Designer dùng dễ hơn |
| Settings có check thiết bị | `AdminScreen.kt`, services | Giảm lỗi trước sự kiện |

### P4 - Đồng bộ web/album

| Việc | File chính | Kết quả |
| --- | --- | --- |
| Tạo Pretty Booth web theme | `photobooth/assets/sass`, config theme | Album cùng thương hiệu |
| Gallery header/empty/loading state | `photobooth/template/components/gallery.php`, Sass | Album đẹp hơn |
| Result/QR copy tiếng Việt | Templates/lang | Khách hiểu rõ hơn |

## 12. Checklist QA giao diện

### Kích thước màn hình

- 1366 x 768: không overlap, CTA vẫn trong viewport.
- 1920 x 1080: nội dung không quá loãng, preview đủ lớn.
- 1280 x 720: kiosk mini không mất footer/CTA.
- Nếu có màn dọc: kiểm tra layout portrait hoặc khóa orientation.

### Trạng thái dữ liệu

- Firebase layout tải thành công.
- Firebase layout fail, dùng fallback.
- Không có frame custom.
- Có nhiều frame custom lớn.
- QR album Cloudinary thành công.
- Album upload fail, local fallback.
- Không có QR.
- PayOS configured.
- PayOS chưa configured.
- Printer enabled.
- Printer disabled.
- Camera live view available.
- Camera missing.
- Hot folder mode.
- Native Canon busy/N/A.

### Tương tác cảm ứng

- Touch target tối thiểu 56dp.
- Nút chính không sát mép màn hình.
- Scroll list không cản click item.
- Horizontal pager không bị nhầm với click.
- Staff override không thể bấm nhầm.

### Visual

- Text không tràn khỏi button/card.
- Chữ trên live camera có scrim đủ đọc.
- QR có quiet zone, scan được từ khoảng cách thực tế.
- Preview bản in giữ đúng aspect ratio.
- Frame thumbnail không bóp méo.
- Loading/error/empty state không dùng placeholder kỹ thuật.

### Copywriting

- Không trộn Anh/Việt ở màn khách hàng.
- Không dùng `output`, `N/A`, `Camera Not Available` cho khách.
- Lỗi nói rõ hành động tiếp theo.
- Admin có thể dùng thuật ngữ kỹ thuật, nhưng nên giải thích ngắn.

## 13. Copywriting đề xuất

### Màn khách hàng

| Hiện tại | Đề xuất |
| --- | --- |
| `CHẠM ĐỂ BẮT ĐẦU` | `Bắt đầu chụp` |
| `STUDIO EDITION` | `Chụp ảnh lấy ngay` hoặc tên brand |
| `Camera Not Available` | `Camera chưa sẵn sàng` |
| `Thanh toán` | `Thanh toán` |
| `Nhân viên: Thu tiền mặt` | `Xác nhận tiền mặt` trong staff mode |
| `Xem bản in` | `Xem bản in` hoặc `Tiếp tục` |
| `Xác nhận output` | `Kiểm tra lần cuối` |
| `In và tạo QR` | `In và tạo QR` |
| `Không có QR` | `Chưa tạo được QR, vui lòng gọi nhân viên` |
| `VỀ TRANG CHỦ` | `Về trang chủ` |

### Admin

| Hiện tại | Đề xuất |
| --- | --- |
| `Quản lý màu sắc` | `Bộ lọc màu` |
| `Tạo Layout/Frame` | `Công cụ tạo layout` |
| `Mã Bố cục` | `Layout ID` hoặc `Mã layout` |
| `Khung sự kiện đặc biệt` | `Frame sự kiện` |
| `Thiết lập 1 chiều` | `Gửi lệnh xuống máy ảnh` |

## 14. Gợi ý cấu trúc file sau khi refactor

Hiện `CoreComponents.kt` đang chứa quá nhiều thứ: button, panel, image loading, QR, moment tile, capture overlay, utility format. Nên tách dần:

```text
ui/
  theme/
    Theme.kt
    Tokens.kt
  components/
    Buttons.kt
    Status.kt
    ScreenHeader.kt
    Stepper.kt
    PrintPreview.kt
    QrCard.kt
    MediaStates.kt
    MomentTile.kt
    FrameCards.kt
    AdminComponents.kt
  screens/
    ...
```

Lợi ích:

- Dễ chuẩn hóa style.
- Dễ test từng component.
- Giảm import trùng lặp.
- UI screens chỉ còn mô tả layout, không chứa quá nhiều helper.

## 15. Rủi ro và lưu ý kỹ thuật

- Không nên thay đổi flow state lớn ngay khi đang gần demo. Hãy polish component/màn hiện tại trước.
- Frame PNG lớn có thể gây lag khi load thumbnail. Nên cache thumbnail async, có placeholder và giới hạn kích thước.
- `FrameStore` đang dùng file name làm `id`; nếu hai frame cùng tên ở folder khác nhau có thể đụng ID. UI admin nên hiển thị path và có ID ổn định hơn.
- `QrCodeView` và `RealQr` nên gộp để tránh khác kích thước/margin.
- `statusMessage` hiện có nhưng chưa hiển thị rộng rãi. Khi đưa vào UI, cần tránh spam text quá kỹ thuật cho khách hàng.
- Staff/admin action trong flow khách hàng nên được bảo vệ. Thanh toán tiền mặt một click là tiện nhưng dễ sai.
- Web module là một app riêng, có nhiều cấu hình sẵn. Không nên sửa đại trà Sass legacy nếu chưa xác định nó có đang deploy thật hay chỉ là tham khảo.

## 16. Lộ trình khuyến nghị

### Giai đoạn 1: Dọn nhanh để đẹp ngay

Thời gian dự kiến: 0.5-1 ngày.

- Sửa bug title carousel.
- Đồng bộ CTA primary.
- Thay text arrow bằng icon.
- Dịch các fallback khách hàng.
- TopBar không hiển thị status cứng.
- SelectPhotos không dim quá sớm.

### Giai đoạn 2: Chuẩn hóa design system

Thời gian dự kiến: 1-2 ngày.

- Mở rộng `Theme.kt`.
- Tách components.
- Tạo `QrCard`, `StatusChip`, `ScreenHeader`, `KioskButton`.
- Update toàn bộ màn khách hàng dùng component chung.

### Giai đoạn 3: Polish trải nghiệm kiosk

Thời gian dự kiến: 2-4 ngày.

- Start dùng live view/frame thật.
- Layout/filter selection có metadata/preview rõ.
- Prepare/capture có guide và error recovery.
- Select/Frame/Confirm có final preview tốt hơn.
- Printing/Delivery có progress và trạng thái album rõ.

### Giai đoạn 4: Admin vận hành

Thời gian dự kiến: 3-5 ngày.

- Admin dashboard.
- Frame/layout search/filter.
- Filter preview.
- Calculator wizard.
- Device checks.

### Giai đoạn 5: Đồng bộ web và QA

Thời gian dự kiến: 1-3 ngày.

- Pretty Booth web theme.
- Gallery/album polish.
- Test các trạng thái thiếu camera/printer/network/payment.
- Chụp screenshot trước/sau.

## 17. Definition of Done cho UI đẹp

Một vòng cải thiện UI được coi là đạt khi:

- Màn khách hàng có cùng ngôn ngữ màu, nút, chữ, icon.
- Mỗi màn có một CTA chính rõ.
- Không còn placeholder kỹ thuật ở flow khách hàng.
- Trạng thái thiết bị không hiển thị sai sự thật.
- QR scan được ổn định.
- Preview bản in đúng aspect ratio và không giật khi đổi frame/layout.
- Admin thao tác xóa/lưu có xác nhận và feedback.
- Chạy qua checklist no camera/no printer/no network vẫn có hướng xử lý.
- Có screenshot desktop 1366x768 và 1920x1080 cho các màn chính.

## 18. Kết luận

Project không thiếu chức năng. Việc cần nhất là biến các chức năng đã có thành một trải nghiệm có nhịp: nhìn đẹp từ màn đầu, chọn nhanh ở mỗi bước, chụp không lo, nhận ảnh rõ ràng, admin vận hành tự tin. Nếu làm theo thứ tự P0 -> P1 -> P2 trước, giao diện sẽ đẹp và chuyên nghiệp hơn thấy rõ mà chưa cần thay đổi kiến trúc nghiệp vụ lớn.
