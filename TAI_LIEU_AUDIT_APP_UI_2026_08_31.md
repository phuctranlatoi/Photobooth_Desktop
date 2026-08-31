# Tài liệu audit và hướng cải thiện giao diện PrettyBooth Desktop

Ngày rà soát: 31/08/2026  
Phạm vi: app desktop Kotlin/Compose, service trạng thái liên quan đến trải nghiệm người dùng, admin layout/frame/filter/camera, đóng gói desktop, và phần web album/photobooth trong `photobooth/`.  
Ràng buộc: tài liệu này chỉ phân tích và đề xuất. Không yêu cầu sửa logic nghiệp vụ hiện tại.

## 1. Kết luận nhanh

App đã có nền tảng khá tốt cho photobooth dạng kiosk: flow rõ, nhiều màn hình đã tách riêng, có preview bản in, có quản trị layout/frame/filter/camera, có PayOS, in hệ thống, Cloudinary/web album và QR. Điểm yếu lớn nhất hiện nay không phải thiếu tính năng, mà là giao diện chưa truyền đạt đủ trạng thái. Khi mạng/Firebase/camera/thanh toán/in/upload gặp vấn đề, khách hoặc nhân viên dễ thấy app như đang treo.

Mục tiêu cải thiện nên là: làm app nhìn hiện đại hơn, đồng nhất hơn, và quan trọng nhất là “biết nói chuyện” với người dùng đúng lúc. Mỗi màn hình cần có một quyết định chính, một trạng thái rõ, một đường lui hợp lý, và một thông điệp xử lý lỗi dễ hiểu.

Ưu tiên cao nhất:

1. Biến loading khởi động thành trạng thái có giới hạn thời gian, có fallback và có thông báo nguyên nhân.
2. Chuẩn hóa shell/top bar/progress/status để mọi màn hình cùng một hệ nhận diện.
3. Tách rõ khu vực khách dùng và khu vực nhân viên/admin dùng.
4. Làm lại các state lỗi của thanh toán, camera, in, upload để không còn cảm giác “cứ xoay mãi”.
5. Đồng bộ visual giữa app desktop và web album để khách quét QR thấy cùng một thương hiệu.

## 2. Project map liên quan UI

### App desktop chính

- `src/main/kotlin/com/phuctran/photobooth/desktop/Main.kt`: cửa vào Compose Desktop, dựng window full screen, collect state từ controller, route `SessionState` sang các screen.
- `src/main/kotlin/com/phuctran/photobooth/desktop/domain/SessionStateMachine.kt`: danh sách trạng thái phiên chụp.
- `src/main/kotlin/com/phuctran/photobooth/desktop/controller/DesktopBoothController.kt`: trung tâm điều phối layout, hiệu ứng, camera, payment, capture, render, print, upload và reset.
- `src/main/kotlin/com/phuctran/photobooth/desktop/model/BoothModels.kt`: model cho layout, effect, frame, ảnh đã chụp, kết quả export.

### Giao diện Compose

- `src/main/kotlin/com/phuctran/photobooth/desktop/ui/theme/Theme.kt`: bảng màu và typography hiện tại.
- `src/main/kotlin/com/phuctran/photobooth/desktop/ui/components/Shell.kt`: shell, top bar, progress stepper, status bar.
- `src/main/kotlin/com/phuctran/photobooth/desktop/ui/components/CoreComponents.kt`: button, panel, chip, tile ảnh, QR, camera mask, helper load ảnh.
- `src/main/kotlin/com/phuctran/photobooth/desktop/ui/components/PrintPreview.kt`: preview layout/frame/ảnh in.
- `src/main/kotlin/com/phuctran/photobooth/desktop/ui/components/QrCodeView.kt`: render QR.
- `src/main/kotlin/com/phuctran/photobooth/desktop/ui/screens/*.kt`: các màn hình khách và admin.

### Admin, thiết bị, render, upload

- `AdminScreen.kt`: quản lý frame, layout, thiết bị, calculator, filter.
- `FilterAdminView.kt`: quản lý bộ lọc màu.
- `DesktopBoothConfig.kt` và `SettingsManager.kt`: đọc/ghi `.env`, camera mode, in, album, PayOS.
- `NativeEosCaptureService.kt`, `WebcamStillCaptureService.kt`, `HotFolderCaptureService.kt`, `CliCaptureService.kt`: các nguồn camera.
- `DesktopCompositor.kt`, `DesktopVideoCompositor.kt`, `PhotoCompositor.kt`: ghép ảnh/video.
- `DesktopAlbumUploader.kt`, `DesktopWebAlbumClient.kt`, `DesktopCloudinaryClient.kt`, `LocalFileServer.kt`: upload và QR album.

### Web album/photobooth

- `photobooth/template/components/stage.start.php`: trang start web photobooth.
- `photobooth/template/components/stage.results.php`: màn hình kết quả web.
- `photobooth/template/components/gallery.php` và `gallery.images.php`: gallery.
- `photobooth/view.php`: trang viewer độc lập khi khách mở ảnh/video.
- `photobooth/assets/sass/components/*.scss`: token, button, stage, preview, gallery, viewer.
- `photobooth/src/Configuration/Section/ColorsConfiguration.php` và `UiConfiguration.php`: config màu/UI của web.

## 3. Luồng app hiện tại

`SessionState` hiện có các bước:

`IDLE -> SELECTING -> SELECTING_QUANTITY -> PAYMENT_PENDING -> PREPARING -> LIVE_VIEW/COUNTDOWN/CAPTURING -> SELECTING_PHOTOS -> EDITING -> PRINT_PENDING -> PRINTING -> DELIVERY`

Ngoài ra có `ADMIN`, `RECOVERY`, `OUT_OF_SERVICE`, `COMPOSING`.

Luồng này hợp lý cho kiosk, nhưng UI nên hiển thị nó thành một timeline thật sự thay vì mỗi màn hình tự nói riêng. Hiện `Shell.kt` có `TopBar`, `ProgressStepper`, `StatusBar`, nhưng `AppShell` chưa render chúng. Vì vậy các prop như `albumEnabled`, `printerEnabled`, `paymentConfigured` được truyền vào shell mà gần như chưa tạo giá trị trực quan.

Hướng cải thiện:

- Biến `AppShell` thành layout chung cho mọi màn hình không phải camera full-screen.
- Top bar chỉ nên hiển thị brand, bước hiện tại, và 3 trạng thái nhỏ: Camera, In, Album/QR.
- Progress stepper phải map theo enum thật (`SELECTING`, `SELECTING_QUANTITY`, `PAYMENT_PENDING`, v.v.), không dùng tên giả như `STUDIO_MODE`, `QUANTITY`, `PAYMENT`.
- `StatusBar` nên hiện message quan trọng từ `statusMessage`, không hiện đường dẫn file kỹ thuật cho khách.
- Khi vào `LIVE_VIEW`, `COUNTDOWN`, `CAPTURING`, shell có thể chuyển sang overlay tối giản: brand nhỏ, số ảnh hiện tại, trạng thái camera, nút thoát cho nhân viên.

## 4. Những điểm tốt nên giữ

- Flow nghiệp vụ đã tách theo màn hình, dễ polish từng bước mà không cần thay logic.
- `PrintPreview` được dùng ở chọn layout, chọn frame, chọn ảnh và xác nhận, rất đúng với sản phẩm in ảnh.
- `Theme.kt` đã có palette neutral/nude/mint/blue/amber/red đủ để thành design system.
- `FrameStore` và admin frame đã hỗ trợ phân loại theo print size/layout/special.
- Có nhiều nguồn camera: native Canon, webcam, hot folder, CLI. Đây là lợi thế vận hành event.
- Có local server fallback và web album, giúp app không phụ thuộc một đường phát ảnh duy nhất.
- `view.php` là trang viewer độc lập, tự chứa CSS, hợp với trường hợp public host chỉ mở route ảnh/download.

## 5. Vấn đề ưu tiên cao

### P0. Loading “Đang tải bố cục chụp...” dễ bị hiểu là treo

Vị trí chính:

- `Main.kt`: khi `isAppReady == false`, UI chỉ hiện spinner và chữ `Đang tải bố cục chụp...`.
- `DesktopBoothController.kt`: init gọi `FirebaseManager.fetchLayouts()`, refresh effects/frames/camera, start local server, start live view, rồi mới set `_isAppReady = true`.
- `BoothModels.kt`: có `DefaultLayoutModes` fallback nhưng UI khởi động chưa giải thích rõ khi fallback được dùng.

Rủi ro:

- Nếu Firebase hoặc SDK camera chậm/treo, người dùng chỉ thấy spinner.
- Nếu thiếu network hoặc service account, app có thể vẫn dùng fallback nhưng khách không hiểu app đang ở trạng thái “offline/fallback”.
- Câu loading chỉ nói về bố cục, trong khi init còn làm nhiều việc khác: camera, local server, frame/effect.

Hướng cải thiện UI:

- Tạo màn hình boot có 4 dòng trạng thái: “Đọc cấu hình”, “Tải bố cục”, “Kiểm tra camera”, “Chuẩn bị album/QR”.
- Sau 5-8 giây, không để spinner đơn độc: đổi sang card “Đang dùng cấu hình tạm” hoặc “Không tải được bố cục online”.
- Cho nhân viên 2 lựa chọn rõ: “Thử lại” và “Vào admin kiểm tra”.
- Nếu fallback layout được dùng, hiển thị badge nhỏ “Offline layout” ở admin, còn màn hình khách chỉ cần copy nhẹ: “Đang dùng bố cục mặc định”.
- Không hiển thị stack trace/chi tiết kỹ thuật cho khách; chi tiết để trong admin diagnostic.

### P0. Nút quay lại ở thanh toán có khả năng không hoạt động

Vị trí chính:

- `PaymentScreen.kt` có `onBack`.
- `Main.kt` truyền `controller.goBack()`.
- `DesktopBoothController.goBack()` chỉ xử lý `SELECTING_QUANTITY`, `EDITING`, `PRINT_PENDING`; chưa có nhánh `PAYMENT_PENDING`.

Rủi ro:

- Khách bấm quay lại ở màn thanh toán nhưng app không phản hồi.
- Đây là lỗi UX rất dễ làm người dùng nghĩ app đơ.

Hướng cải thiện:

- Cho phép `PAYMENT_PENDING -> SELECTING_QUANTITY` nếu chưa thanh toán.
- Khi payment polling đang chạy, UI cần nói rõ “Huỷ mã này và chọn lại số lượng?”.
- Nếu đã phát hiện thanh toán thành công, khóa back và chuyển bước.

### P0. QR thanh toán có thể xoay mãi nếu tạo mã lỗi

Vị trí chính:

- `PaymentService.createPaymentLink()` catch lỗi và trả `null`.
- `PaymentScreen.kt` nếu PayOS configured nhưng `paymentQrData == null` thì hiện spinner “Đang tạo mã thanh toán...”.

Rủi ro:

- PayOS sai key, lỗi mạng, lỗi amount hoặc API lỗi đều nhìn giống đang tải.

Hướng cải thiện UI:

- Cần phân biệt 3 state: `creating`, `ready`, `failed`.
- State `failed` nên có copy: “Không tạo được mã QR. Vui lòng kiểm tra mạng hoặc thanh toán tiền mặt với nhân viên.”
- Có nút “Tạo lại mã” và “Gọi nhân viên”.
- Nút xác nhận tiền mặt nên là chế độ nhân viên, không đặt như CTA luôn hiển thị cho khách.

### P0. Native Canon/EDSDK thiếu DLL làm app lỗi ở máy khác

Vị trí chính:

- `DesktopBoothConfig.kt` mặc định `useNativeSdk = true`.
- `DesktopBoothController.kt` chọn `NativeEosCaptureService` trước hot folder nếu `useNativeSdk` true.
- `NativeEosCaptureService.kt` tạo `CanonCamera()` trong service.
- `build.gradle.kts` dùng Compose native distribution và `appResourcesRootDir = app-resources`.

Rủi ro:

- Khi máy khác thiếu `EDSDK_64/EDSDK.dll`, app lỗi trước khi vào trải nghiệm chính.
- Với người dùng cuối, đây là lỗi giao diện vì app không hiện màn hình hướng dẫn/khắc phục.

Hướng cải thiện UI/tài liệu vận hành:

- Admin nên có health card “Canon SDK: OK/Thiếu DLL/Không thấy camera”.
- Nếu thiếu DLL, màn hình khách không crash; hiện “Máy ảnh đang được nhân viên kiểm tra”.
- Màn hình admin cần chỉ ra đúng vị trí đang tìm DLL và nút mở thư mục app resources.
- Trong đóng gói, tài liệu build cần ghi rõ `app-resources/EDSDK_64/EDSDK.dll` và các DLL phụ thuộc phải đi cùng bản release.

### P1. Staff/admin action đang lẫn vào guest flow

Vị trí chính:

- `PaymentScreen.kt`: “Nhân viên xác nhận tiền mặt” luôn hiện ở đáy màn hình.
- `FrameScreen.kt`: “Thêm PNG” hiện trong luồng chọn khung của khách.
- `StartScreen.kt`: admin mở bằng tap ẩn trên brand.

Rủi ro:

- Khách có thể vô tình bypass thanh toán.
- Khách thấy nút thêm PNG sẽ bối rối hoặc làm hỏng nhịp kiosk.
- Tap ẩn không có feedback khiến nhân viên khó thao tác trong sự kiện.

Hướng cải thiện:

- Tách rõ guest mode và staff mode.
- Các action nhân viên nên nằm sau long press, PIN, hoặc staff drawer.
- Guest flow chỉ giữ: chọn layout, chọn màu, chọn số bản, thanh toán, chụp, chọn ảnh, chọn khung, xác nhận, nhận QR.
- Màn hình payment có thể có nút nhỏ “Nhân viên” mở panel xác nhận tiền mặt sau PIN.
- “Thêm PNG” chỉ nên ở admin frame manager.

## 6. Định hướng visual design

### Tinh thần giao diện đề xuất

PrettyBooth Desktop nên đi theo hướng “studio kiosk hiện đại”: sạch, sáng, sang vừa phải, dễ quét mắt từ xa, không quá nhiều card trang trí. Người dùng đứng trước màn hình cảm ứng trong vài phút; giao diện cần ít chữ, CTA lớn, ảnh/live preview là trung tâm.

Từ hiện trạng, nên giữ tone nude/neutral nhưng làm nó sắc nét hơn:

- Nền: neutral sáng, rất nhẹ, không lạm dụng gradient.
- Surface: trắng/near-white, border mảnh, shadow rất tiết chế.
- Accent chính: nude/copper dùng cho CTA và trạng thái active.
- Accent phụ: mint cho thành công, amber cho cần chú ý, red cho lỗi, blue chỉ dùng cho thông tin/kết nối.
- Typography: brand có thể dùng serif/italic nếu “Le Souvenir” là concept event, còn UI điều hướng dùng sans-serif rõ nét.
- Ảnh/live camera/final preview phải là visual hero ở mọi bước quan trọng.

### Token nên chuẩn hóa

Hiện token nằm ở `Theme.kt`, nhưng chưa có scale spacing/elevation/radius rõ ràng. Nên bổ sung tài liệu design token:

- Radius: `8dp` cho control nhỏ, `12dp` cho tile, `16dp` cho panel, `24dp` chỉ dùng cho hero preview hoặc modal lớn.
- Spacing: `8/12/16/24/32/48`.
- Touch target: tối thiểu `56dp`, CTA chính `64-72dp`.
- Border: `1dp NeutralBorder`, active dùng accent + shadow nhẹ.
- Motion: bouncy click chỉ dùng cho CTA/choice lớn; không dùng cho mọi row nhỏ.
- Icon: dùng icon nhất quán cho back, print, QR, camera, album, warning, success. Tránh dùng text arrow `"←"` ở vài màn hình trong khi đã có `KioskBackButton`.

### Điều cần tránh

- Không để mỗi màn hình tự có một phong cách riêng.
- Không dùng nhiều card lồng nhau.
- Không dùng màu dark/admin quá tách biệt nếu không có lý do.
- Không dùng chữ tiếng Anh xen trong guest flow như `Camera Not Available`, `Normal`, `Black & White` nếu app hướng tới khách Việt.
- Không hiện đường dẫn kỹ thuật hoặc trạng thái debug cho khách.

## 7. Cải thiện theo màn hình

### StartScreen

Hiện trạng:

- Có live preview nền nếu camera có frame, fallback bằng photostrip graphic.
- Brand đang là “Le Souvenir”, trong khi package/project là PrettyBooth Desktop.
- Admin mở bằng tap 5 lần trên brand.

Hướng cải thiện:

- Quyết định rõ brand chính: `PrettyBooth`, `Le Souvenir`, hay tên event. Nếu `Le Souvenir` là theme event, UI admin/package vẫn nên gọi PrettyBooth.
- Start screen nên có một CTA duy nhất thật nổi: “Bắt đầu chụp”.
- Nếu camera chưa sẵn sàng, CTA đổi trạng thái thành “Đang chuẩn bị máy ảnh” thay vì cho khách bấm vào flow lỗi.
- Thêm micro status cho nhân viên ở góc: camera, bố cục, in, album. Khách không cần thấy chi tiết.
- Tap admin nên có feedback nhỏ ở lần tap 3/5, 4/5 để nhân viên biết đã nhận thao tác.

### StudioModeScreen

Hiện trạng:

- Bước 1 chọn layout bằng carousel.
- Bước 2 chọn filter/effect với live preview.
- Có bug hiển thị title layout: pager dùng page ảo 100000 nhưng title lấy `layouts.getOrNull(pagerState.currentPage)` thay vì modulo.
- Step 2 nhận `selectedLayout` nhưng chưa dùng nhiều.
- Fallback camera ghi `Camera Not Available`.

Hướng cải thiện:

- Chọn layout nên là “product card” có preview rõ: số ảnh, số ảnh được chọn, khổ in, giá.
- Sửa title carousel theo modulo để tiêu đề luôn khớp card giữa.
- Nếu chỉ có fallback layout, không dùng carousel vô hạn; hiển thị một empty/fallback state rõ.
- Bước chọn màu nên show filter trực tiếp trên live view và thumbnail preset lớn.
- Việt hóa tên effect: “Tự nhiên”, “Đen trắng”, “Film ấm”.
- Button back dùng cùng `KioskBackButton`.
- Copy fallback camera nên là tiếng Việt: “Chưa có tín hiệu máy ảnh”.

### QuantityScreen

Hiện trạng:

- Màn hình khá rõ, có preview và card số lượng.
- Quantity option phụ thuộc strip/full.

Hướng cải thiện:

- Ghi rõ logic in strip: nếu chọn 2/4/6/8 bản thì app sẽ quy đổi ra số tờ in như thế nào.
- CTA nên nói cụ thể: “Tiếp tục thanh toán 80.000đ”.
- Nếu in đang tắt, đừng gọi là “số bản in”; nên đổi copy thành “gói nhận ảnh” hoặc bỏ bước nếu không có in.
- Thêm cảnh báo nhẹ nếu máy in offline nhưng guest vẫn chọn được số bản.

### PaymentScreen

Hiện trạng:

- Có QR PayOS hoặc cash override.
- Thiếu state lỗi rõ ràng khi tạo QR thất bại.
- Back callback có thể không hoạt động do controller chưa xử lý `PAYMENT_PENDING`.
- Cash override luôn lộ ra guest flow.

Hướng cải thiện:

- Payment card có trạng thái: tạo mã, chờ thanh toán, đã nhận, lỗi.
- Thêm countdown/timestamp “Mã có hiệu lực trong ...” nếu API hỗ trợ.
- Nút “Đã chuyển khoản” không nên cần nếu polling tự động, nhưng có thể để nhân viên check thủ công trong panel.
- Cash override cần ẩn sau staff mode.
- Nếu payment chưa cấu hình, guest flow nên dùng copy “Thanh toán tại quầy” thay vì “Chưa bật thanh toán QR”.

### PrepareScreen

Hiện trạng:

- Copy nói mỗi ảnh có 3 giây đếm ngược.
- Nhưng layout model có `countdownSeconds`, có thể khác 3.

Hướng cải thiện:

- Copy lấy số giây từ `layout.countdownSeconds`.
- Hiển thị checklist ngắn: đứng trong khung, nhìn vào camera, chuẩn bị tạo dáng.
- Nếu hot folder/Canon cần thao tác ngoài, UI nên nói rõ cho nhân viên: “Sẵn sàng nhận ảnh từ thư mục/camera”.

### CaptureScreen

Hiện trạng:

- Full screen live view đẹp và đúng trọng tâm.
- Có capture mask, countdown, flash.
- Một số prop như import/clear/refresh capture sources không còn lộ trên UI.
- Nếu camera không có live view, UI chỉ hiện trạng thái chung.

Hướng cải thiện:

- Mỗi shot nên có label: “Ảnh 2/4”.
- Nếu dùng hot folder, màn hình nên hướng dẫn: “Nhân viên bấm chụp trên camera, app sẽ tự nhận ảnh”.
- Nếu dùng webcam/native Canon, có thể CTA “Chụp ngay” cho shot đầu tiên rồi tự flow.
- Thêm nút staff-only “Huỷ phiên” hoặc “Chụp lại shot này”.
- Khi camera mất tín hiệu, hiện lỗi mềm + hành động: “Thử lại camera”, “Vào admin”.
- Overlay effect hiện tại chưa phản ánh toàn bộ effect; nên đảm bảo preview thật dùng matrix/filter đồng nhất với ảnh render.

### SelectPhotosScreen

Hiện trạng:

- Có gallery ảnh chụp và preview bản in.
- Chỉ cho xác nhận khi chọn đủ số ảnh.

Hướng cải thiện:

- Thêm selected tray ngang ở dưới preview để thấy thứ tự ảnh.
- Cho phép “Chụp lại” hoặc “Bỏ phiên” trong staff mode.
- Tile ảnh nên có trạng thái loading/error thumbnail nếu file đọc chậm hoặc mất.
- Nếu chọn quá đủ, đừng chỉ dim ảnh; copy nên nói “Bỏ chọn một ảnh để đổi”.
- Preview nên luôn giữ tỷ lệ in thật và có nhãn khổ in.

### FrameScreen

Hiện trạng:

- Preview bản in bên trái, grid frame bên phải.
- Có nút “Thêm PNG” ngay trong guest flow.

Hướng cải thiện:

- Bỏ “Thêm PNG” khỏi guest flow, chuyển sang admin frame.
- Nhóm frame theo: “Mặc định”, “Sự kiện”, “Đặc biệt”.
- Frame tile cần thumbnail lớn, badge “phù hợp layout này”, “có QR”, “special”.
- Nếu không có frame, vẫn nên có lựa chọn “Không dùng khung” với preview rõ.
- Với frame nhiều, thêm filter/chip theo khổ in và event.

### ConfirmScreen

Hiện trạng:

- Có final preview, summary, CTA xác nhận in/upload.
- `ExportSummary.uploadedPhotoCount` trước khi upload dễ bị hiểu là đã upload.

Hướng cải thiện:

- Đổi copy thành “Sẽ lưu album: X ảnh gốc” trước khi xử lý.
- Hiển thị đủ: số ảnh in, số bản, khổ giấy, frame, tổng tiền đã thanh toán.
- Có checkbox staff-only nếu cần xác nhận “Máy in đã có giấy”.
- CTA chính nên là “In và tạo QR”.

### PrintingScreen

Hiện trạng:

- Spinner + status message.
- Controller có nhiều bước: tạo album, render ảnh, gửi in, upload ảnh, delivery, tạo video, upload video, complete album.

Hướng cải thiện:

- Biến thành progress pipeline 5 bước:
  1. Render ảnh in
  2. Gửi lệnh in
  3. Tải ảnh lên album
  4. Tạo mã QR
  5. Tạo video kỷ niệm
- Nếu đã có QR trước khi in/video xong, chuyển sang Delivery sớm nhưng Delivery vẫn hiển thị “Máy đang in/video đang xử lý”.
- Nếu upload lỗi, vẫn đưa QR local hoặc hướng dẫn nhận ảnh tại máy.
- Không dùng spinner đơn làm trung tâm quá lâu; dùng skeleton/progress/checkmark.

### DeliveryScreen

Hiện trạng:

- Có QR thật hoặc QR mock, summary in/upload.
- Một số callback như `onOpenAlbum`, `onOpenOutput` chưa thấy vai trò rõ trên UI.

Hướng cải thiện:

- QR là trung tâm, nhưng cần thêm thumbnail final print để khách tin đúng album của mình.
- Nếu QR local server, copy cần nói “Cùng Wi-Fi với máy photobooth”.
- Nếu QR cloud, copy nói “Quét để tải ảnh và video”.
- Hiển thị trạng thái in: “Đã gửi lệnh in”, “Đang in”, “Lỗi máy in”.
- Nút “Chụp phiên mới” nên nổi bật sau khi QR đã sẵn sàng.
- Staff actions mở album/output nên ẩn nhỏ trong góc hoặc admin drawer.

### AdminScreen

Hiện trạng:

- Có tab Frame, Layout, Thiết bị, Calculator, Filter.
- Header có status chip camera/in/album.
- Camera chip hiện `Hot folder` hoặc `Webcam`, chưa phản ánh native Canon/CLI.
- “Thoát admin” đang gọi save settings rồi exit, có thể gây bất ngờ.
- Delete frame không có confirm.
- Layout editor parse số không hợp lệ bằng cách giữ giá trị cũ, ít feedback.

Hướng cải thiện:

- Tách admin thành sidebar trái: Tổng quan, Camera, In & Album, Layout, Frame, Filter, Công cụ.
- Trang Tổng quan có health cards: Camera, Canon SDK, Firebase, PayOS, Printer, Cloudinary, Web album, Local server.
- “Thoát admin” chỉ thoát. “Lưu cài đặt” là nút riêng, có trạng thái dirty/saved.
- Camera mode phải phản ánh đủ: Native Canon, Hot folder, Webcam/DroidCam, CLI/EOS Utility.
- Frame delete cần confirm và preview tên file.
- Layout card nên có preview mini + shot/select/price/countdown/print size.
- Layout editor cần validation đỏ ngay tại input nếu nhập sai.
- Filter admin cần before/after preview ảnh mẫu, reset về mặc định, và confirm khi xóa filter.

## 8. Web album và viewer

Hiện trạng:

- Web `photobooth/` có hệ token CSS riêng dựa trên CSS variables.
- `view.php` đã tự chứa CSS để dùng độc lập, rất tốt cho public route.
- `gallery` dùng grid và PhotoSwipe.
- Theme modern hiện vẫn dùng gradient/radial ở một số chỗ; desktop app lại theo neutral/nude.

Hướng cải thiện:

- Đồng bộ token web với desktop: primary nude/copper, neutral bg, border, success/warning/error.
- Viewer sau khi quét QR nên giống cùng một sản phẩm với màn Delivery.
- Trang viewer nên có:
  - tiêu đề event/brand,
  - ảnh/video lớn,
  - nút tải xuống rõ,
  - dòng “Album hết hạn sau X ngày” nếu có dữ liệu,
  - responsive mobile ưu tiên ảnh trước, chữ sau.
- Gallery nên tránh shadow quá nặng; dùng card ảnh sạch, border mảnh, hover/focus rõ.
- Nếu album còn đang upload video, viewer/album nên có trạng thái “Video đang được xử lý”.
- Không nên cho web album dùng theme xanh mặc định nếu kiosk đang là nude/neutral.

## 9. Design system đề xuất

### Thành phần cần có

- `KioskScreenScaffold`: dùng cho mọi màn hình không full camera.
- `KioskTopBar`: brand + step + status chips.
- `KioskProgress`: map đúng từ `SessionState`.
- `HardwareStatusChip`: camera/printer/album/payment.
- `PrimaryActionBar`: CTA dưới màn hình, nhất quán.
- `StaffDrawer`: panel ẩn cho nhân viên.
- `ErrorState`: lỗi có title, mô tả, nút xử lý.
- `EmptyState`: thiếu layout/frame/camera/ảnh.
- `ProgressPipeline`: thay spinner dài ở print/upload.
- `PreviewSurface`: wrapper thống nhất cho live view/final print/QR.

### Quy tắc màn hình

- Mỗi màn hình chỉ có một CTA chính.
- Back luôn hoạt động hoặc bị ẩn có chủ đích.
- Guest copy ngắn, tiếng Việt, không lộ thuật ngữ kỹ thuật.
- Staff/admin copy có thể có chi tiết nhưng phải nằm trong admin surface.
- Preview ảnh/live camera/final print là trung tâm thị giác.
- Không dùng control kỹ thuật trong guest flow.

### Quy tắc trạng thái

Mỗi tác vụ async nên có đủ 4 trạng thái UI:

- Loading: đang làm gì.
- Success: đã xong gì, bước tiếp theo là gì.
- Empty/fallback: không có dữ liệu nhưng vẫn tiếp tục được không.
- Error: lỗi gì theo ngôn ngữ người dùng, xử lý bằng nút nào.

Áp dụng cho:

- Tải layout Firebase.
- Tạo QR PayOS.
- Camera live view.
- Chờ hot folder.
- Gửi lệnh in.
- Upload ảnh/video.
- Tạo album web.

## 10. Các đề xuất không đụng logic nghiệp vụ

Những cải thiện này có thể làm theo hướng UI layer trước:

- Dùng existing `statusMessage` để hiển thị thông tin tốt hơn trong shell/progress.
- Thêm UI state adapter chỉ đọc từ state hiện có, ví dụ `BootUiState`, `PaymentUiState`, `DeliveryUiState`.
- Không đổi thuật toán capture/render/print/upload.
- Không đổi format layout/frame/filter.
- Không đổi API PayOS/Cloudinary/web album.
- Không đổi `SessionState`, chỉ map lại progress đúng.
- Không đổi dữ liệu Firebase, chỉ hiển thị fallback/empty/error rõ hơn.

Các đề xuất có thể cần sửa logic nhẹ sau này:

- Thêm nhánh back cho `PAYMENT_PENDING`.
- Thêm timeout/failure state cho payment QR.
- Soft-disable native Canon nếu thiếu DLL.
- Chia staff mode/admin lock.
- Tạo progress pipeline từ các bước xử lý thật thay vì một chuỗi text.

## 11. Roadmap triển khai đề xuất

### Giai đoạn 1: Sửa cảm giác “app bị treo”

Mục tiêu: khách/nhân viên luôn biết app đang làm gì.

- Boot screen có checklist trạng thái.
- Payment QR có failed/retry/cash staff state.
- Camera empty/error state tiếng Việt.
- Printing pipeline thay spinner dài.
- Delivery hiển thị print/upload/video status rõ.

### Giai đoạn 2: Chuẩn hóa shell và design system

Mục tiêu: mọi màn hình giống cùng một sản phẩm.

- Kích hoạt `TopBar`, `ProgressStepper`, `StatusBar` sau khi chỉnh map state.
- Dùng một hệ button/back/chip/card.
- Chuẩn hóa spacing/radius/elevation.
- Thay text arrow bằng icon/back component.
- Đồng bộ tiếng Việt và naming effect/layout.

### Giai đoạn 3: Tách guest flow và staff/admin

Mục tiêu: khách không thấy công cụ kỹ thuật.

- Ẩn cash override sau staff/PIN.
- Đưa “Thêm PNG” về admin.
- Staff drawer cho retry camera, cancel session, open output.
- Admin dashboard health.
- Confirm trước delete frame/filter/layout.

### Giai đoạn 4: Nâng cấp admin vận hành event

Mục tiêu: nhân viên sửa lỗi nhanh trong sự kiện.

- Admin overview.
- Camera mode đầy đủ.
- Printer test.
- PayOS test.
- Web album test.
- Firebase layout sync status.
- Log ngắn thân thiện, không cần đọc terminal.

### Giai đoạn 5: Đồng bộ web album

Mục tiêu: QR experience nhìn cùng hệ với app desktop.

- Token màu web giống Compose theme.
- Viewer mobile-first, ảnh lớn, download rõ.
- Trạng thái album/video đang xử lý.
- Gallery nhẹ hơn, ít shadow, focus state rõ.

## 12. Checklist nghiệm thu UI

Trước khi coi là đẹp và sẵn sàng chạy event, nên check:

- App mở lên dưới 8 giây phải có trạng thái rõ; nếu mạng lỗi không được spinner mãi.
- Mỗi màn hình có đúng một CTA chính.
- Back hoạt động ở mọi nơi có nút back.
- Guest không thấy “Thêm PNG”, đường dẫn file, stack trace, hay thuật ngữ SDK.
- Payment lỗi phải có retry/cash staff path.
- Camera mất tín hiệu phải có thông báo tiếng Việt và hướng xử lý.
- Print/upload/video phải có progress hoặc status cụ thể.
- Preview trên màn chọn layout, chọn ảnh, chọn frame và confirm phải khớp kết quả render cuối.
- App chạy ổn ở 1920x1080 và 1366x768.
- Touch target tối thiểu 56dp.
- Text không tràn card/button.
- Web viewer trên điện thoại hiển thị ảnh/video trước, nút tải rõ.
- Màu desktop và web album nhìn cùng thương hiệu.

## 13. Ghi chú về Impeccable

Đã dùng skill Impeccable theo hướng source audit để soi UX/UI. Detector tự động trả kết quả rỗng với thư mục Compose UI, nên các nhận định trong tài liệu này dựa trên đọc source Kotlin/PHP/Sass thủ công và playbook critique. Context Impeccable cũng báo metadata `PRODUCT.md` đang ghi platform `desktop`, trong khi schema hiện chỉ nhận `web/ios/android/adaptive`; đây là lệch metadata của skill, không phải lỗi app. Nếu muốn dùng Impeccable sâu hơn cho vòng redesign sau, nên cập nhật tài liệu context trước rồi chạy audit kèm ảnh/screenshot runtime.

## 14. Ưu tiên thực tế nếu chỉ có ít thời gian

Nếu chỉ có 1 ngày:

1. Sửa boot/loading/payment/camera/printing thành state rõ.
2. Sửa bug carousel title layout và back ở payment.
3. Ẩn “Thêm PNG” và cash override khỏi guest flow.

Nếu có 3 ngày:

1. Làm shell/topbar/progress/status dùng chung.
2. Polish lại từng screen theo cùng token.
3. Làm admin overview và các empty/error state.

Nếu có 1 tuần:

1. Redesign toàn guest flow.
2. Redesign admin vận hành event.
3. Đồng bộ web viewer/gallery.
4. QA visual trên nhiều độ phân giải và mô phỏng mất mạng/mất camera/mất máy in.

## 15. Kết luận

PrettyBooth Desktop đã có phần lõi đủ mạnh. Để app đẹp và đáng tin trong sự kiện thật, cần ưu tiên “trạng thái rõ” trước “trang trí đẹp”. Một photobooth tốt phải làm khách thấy mọi thứ đang trơn tru, còn nhân viên phải biết ngay đang kẹt ở camera, thanh toán, máy in, Firebase hay upload. Khi shell, state, guest/staff boundary và web album được đồng bộ, app sẽ nhìn hiện đại hơn rất nhiều mà không cần đụng sâu vào logic nghiệp vụ.
