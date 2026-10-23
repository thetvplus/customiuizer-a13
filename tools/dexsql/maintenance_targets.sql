-- Open one complete A13 ROM input at a time; verify its SHA-256/CRC first.
-- methods.method_kind IS NOT NULL excludes reference-only method IDs.
SELECT file_id, source_path, entry_name, class_count
FROM dex_files ORDER BY file_id;

-- services.jar: H03-H07 bundle definitions. Decode only the selected callers.
SELECT file_id, descriptor, method_kind
FROM methods WHERE method_kind IS NOT NULL AND (
  (class_descriptor = 'Lcom/android/server/display/DisplayPowerController;' AND name = 'initialize') OR
  (class_descriptor = 'Lcom/android/server/notification/NotificationManagerService;' AND name IN ('scheduleDurationReachedLocked', 'showNextToastLocked')) OR
  (class_descriptor = 'Lcom/android/server/wm/DisplayPolicy;' AND name = 'adjustWindowParamsLw') OR
  (class_descriptor = 'Lcom/android/server/vibrator/VibratorManagerService;' AND name IN ('systemReady', 'vibrate')) OR
  (class_descriptor = 'Lcom/android/server/audio/FocusRequester;' AND name IN ('handleFocusLoss', 'frameworkHandleFocusLoss')) OR
  (class_descriptor = 'Lcom/android/server/audio/AudioService$VolumeController;' AND name = 'suppressAdjustment')
) ORDER BY file_id, descriptor;

-- Home APK: leave native event ownership intact.
SELECT file_id, descriptor FROM methods WHERE method_kind IS NOT NULL AND (
  (class_descriptor = 'Lcom/miui/home/launcher/hotseats/HotSeats;' AND name = 'dispatchTouchEvent') OR
  (class_descriptor = 'Lcom/miui/home/launcher/overlay/assistant/AssistantOverlaySwipeController;' AND name = 'canInterceptTouch') OR
  (class_descriptor = 'Lcom/miui/home/launcher/Launcher;' AND name = 'getHotSeats')
) ORDER BY file_id, descriptor;

-- SecurityCenter APK: the handler is a Handler subclass, not a WeakReference subclass.
SELECT c.file_id, c.descriptor, c.superclass_descriptor, f.field_name, f.field_descriptor
FROM classes c JOIN class_fields f ON c.file_id = f.file_id AND c.class_idx = f.class_idx
WHERE c.descriptor LIKE 'Lcom/miui/powercenter/BatteryFragment$%'
  AND c.superclass_descriptor = 'Landroid/os/Handler;'
ORDER BY c.file_id, c.descriptor, f.field_name;

-- SystemUI APK: exact menu overload; never use incomplete veux SystemUI as absence evidence.
SELECT file_id, descriptor FROM methods
WHERE method_kind IS NOT NULL
  AND class_descriptor = 'Lcom/android/systemui/statusbar/notification/row/MiuiNotificationMenuRow;'
  AND name = 'onClickInfoItem'
ORDER BY file_id, descriptor;
