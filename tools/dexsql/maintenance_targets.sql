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

-- SecurityCenter: app details and battery handler. Compare only validated DEX entries.
SELECT d.entry_name, m.method_descriptor
FROM class_methods m JOIN dex_files d USING (file_id)
WHERE (m.class_descriptor = 'Lcom/miui/appmanager/ApplicationsDetailsActivity;' AND m.method_name IN ('initView', 'onClick'))
   OR (m.class_descriptor LIKE 'Lcom/miui/powercenter/BatteryFragment$%;' AND m.method_name = 'handleMessage')
ORDER BY d.entry_name, m.method_descriptor;

-- SystemUI: #19 and #14 direct definitions. Keep the declaring DEX in the result.
SELECT d.entry_name, m.method_descriptor
FROM class_methods m JOIN dex_files d USING (file_id)
WHERE (m.class_descriptor IN (
        'Lcom/android/systemui/statusbar/phone/MiuiDripLeftStatusBarIconControllerImpl;',
        'Lcom/android/systemui/statusbar/phone/StatusBarIconControllerImpl;')
       AND m.method_name = 'setMobileIcons')
   OR (m.class_descriptor = 'Lcom/android/systemui/statusbar/StatusBarMobileView;'
       AND m.method_name IN ('initViewState', 'updateState', 'applyDarknessInternal'))
   OR (m.class_descriptor = 'Lcom/android/systemui/statusbar/MiuiStatusBarIconViewHelper;' AND m.method_name = 'transformResId')
   OR m.method_descriptor IN (
        'Lcom/miui/maml/util/ZipResourceLoader;-><init>(Ljava/lang/String;)V',
        'Lcom/android/systemui/plugins/PluginEnablerImpl;->isEnabled(Landroid/content/ComponentName;)Z')
ORDER BY d.entry_name, m.method_descriptor;

-- Confirm source-side wiring only from CRC- and header-verified defining entries.
SELECT DISTINCT d.entry_name, method, callee
FROM method_calls JOIN dex_files d USING (file_id)
WHERE (method_class = 'Lcom/android/systemui/statusbar/StatusBarMobileView;'
       AND method_name IN ('initViewState', 'updateState') AND callee = 'Landroid/widget/ImageView;->setImageResource(I)V')
   OR (method_class = 'Lcom/android/systemui/globalactions/MiuiGlobalActions;'
       AND callee = 'Lcom/miui/maml/util/ZipResourceLoader;-><init>(Ljava/lang/String;)V')
   OR (method_class LIKE 'Lcom/miui/powercenter/BatteryFragment$%;' AND method_name = 'handleMessage'
       AND callee = 'Landroid/os/Handler;->handleMessage(Landroid/os/Message;)V')
ORDER BY d.entry_name, method, callee;

-- SystemUI: no-auto-hide. Compare old declarations with the phone entry and
-- its parent. Decode the new targets to distinguish automatic and explicit removal.
SELECT d.entry_name, m.method_descriptor
FROM class_methods m JOIN dex_files d USING (file_id)
WHERE m.method_descriptor IN (
    'Lcom/android/systemui/statusbar/phone/HeadsUpManagerPhone$HeadsUpEntryPhone;->updateEntry(Z)V',
    'Lcom/android/systemui/statusbar/AlertingNotificationManager$AlertEntry;->updateEntry(Z)V',
    'Lcom/android/systemui/statusbar/AlertingNotificationManager$AlertEntry;->removeAutoRemovalCallbacks()V',
    'Lcom/android/systemui/statusbar/AlertingNotificationManager$AlertEntry;->removeAsSoonAsPossible()V',
    'Lcom/android/systemui/statusbar/notification/row/ExpandableNotificationRow;->isDozing()Z',
    'Lcom/android/systemui/statusbar/policy/HeadsUpManager$HeadsUpEntry;->updateEntry(Z)V'
) OR (m.class_descriptor = 'Lcom/android/systemui/statusbar/policy/HeadsUpManager;'
    AND m.method_name IN ('removeHeadsUpNotification', 'removeOldHeadsUpNotification', 'onExpandingFinished'))
ORDER BY m.method_descriptor;

-- Home / Global Launcher: scope declarations to the actual host DEX.
SELECT d.entry_name, m.method_descriptor
FROM class_methods m JOIN dex_files d USING (file_id)
WHERE (m.class_descriptor = 'Lcom/miui/home/launcher/shortcuts/ShortcutMenuManager;'
       AND m.method_name = 'startAppDetailsActivity')
   OR (m.class_descriptor = 'Lcom/miui/home/launcher/util/Utilities;'
       AND m.method_name = 'startDetailsActivityForInfo')
   OR (m.class_descriptor = 'Lcom/miui/home/launcher/Folder;'
       AND m.method_name IN ('updateViewsLayout', 'resetViewsLayoutParams'))
   OR (m.class_descriptor = 'Lcom/miui/home/launcher/DeviceConfig;'
       AND m.method_name IN ('calcHotSeatsHeight', 'calcHotSeatsMarginTop', 'calcHotSeatsMarginBottom'))
ORDER BY m.method_descriptor;

SELECT DISTINCT d.entry_name, method, callee
FROM method_calls JOIN dex_files d USING (file_id)
WHERE (callee_class = 'Lcom/miui/home/launcher/shortcuts/ShortcutMenuManager;'
       AND callee_name = 'startAppDetailsActivity')
   OR (callee_class = 'Lcom/miui/home/launcher/Folder;'
       AND callee_name = 'updateViewsLayout')
ORDER BY d.entry_name, method;

SELECT d.entry_name, f.field_descriptor
FROM class_fields f JOIN dex_files d USING (file_id)
WHERE f.field_descriptor IN (
    'Lcom/android/systemui/statusbar/AlertingNotificationManager$AlertEntry;->mEntry:Lcom/android/systemui/statusbar/notification/collection/NotificationEntry;',
    'Lcom/android/systemui/statusbar/notification/collection/NotificationEntry;->row:Lcom/android/systemui/statusbar/notification/row/ExpandableNotificationRow;'
) ORDER BY f.field_descriptor;

-- Host SystemUI APK: confirm the factory declaration and its real caller.
SELECT d.entry_name, m.method_descriptor
FROM class_methods m JOIN dex_files d USING (file_id)
WHERE m.class_descriptor = 'Lcom/android/systemui/shared/plugins/PluginInstance$Factory;'
  AND m.method_name IN ('create', 'getClassLoader')
ORDER BY m.method_descriptor;

SELECT DISTINCT d.entry_name, method, callee
FROM method_calls JOIN dex_files d USING (file_id)
WHERE callee_class = 'Lcom/android/systemui/shared/plugins/PluginInstance$Factory;'
  AND callee_name = 'getClassLoader'
ORDER BY d.entry_name, method;

-- MIUISystemUIPlugin.apk: decode computeTimeoutH for native flags and timing.
SELECT d.entry_name, m.method_descriptor
FROM class_methods m JOIN dex_files d USING (file_id)
WHERE (m.class_descriptor = 'Lcom/android/systemui/miui/volume/MiuiVolumeDialogImpl;'
       AND m.method_name IN ('addColumn', 'vibrateH', 'computeTimeoutH', 'rescheduleTimeoutH'))
   OR (m.class_descriptor = 'Lcom/android/systemui/miui/volume/Util;'
       AND m.method_name = 'isNotificationSingle')
   OR (m.class_descriptor = 'Lmiui/systemui/controlcenter/qs/QSPager;'
       AND m.method_name IN ('<init>', 'distributeTiles'))
   OR (m.class_descriptor = 'Lmiui/systemui/controlcenter/qs/tileview/StandardTileView;'
       AND m.method_name = 'createLabel')
   OR (m.class_descriptor = 'Lmiui/systemui/controlcenter/windowview/ControlCenterWindowViewImpl;'
       AND m.method_name = 'handleMotionEvent')
   OR (m.class_descriptor = 'Lmiui/systemui/controlcenter/windowview/MainPanelHeaderController;'
       AND m.method_name IN ('updateVisibility', 'updateConstraint', 'addClockViews', 'updateClocksAppearance'))
   OR (m.class_descriptor = 'Lmiui/systemui/dagger/PluginComponentFactory;'
       AND m.method_name = 'create')
ORDER BY m.method_descriptor;

-- miui-services.jar: four rotation factories and the native loader calls.
SELECT d.entry_name, m.method_descriptor
FROM class_methods m JOIN dex_files d USING (file_id)
WHERE (m.class_descriptor = 'Lcom/android/server/wm/ScreenRotationAnimationImpl;'
       AND m.method_name LIKE 'createRotation%')
   OR (m.class_descriptor = 'Lcom/android/server/wm/AppTransitionInjector;'
       AND m.method_name = 'createAnimation')
ORDER BY m.method_descriptor;

SELECT DISTINCT method, callee FROM method_calls
WHERE callee_class = 'Lcom/android/server/wm/ScreenRotationAnimationImpl;'
  AND callee_name LIKE 'createRotation%'
ORDER BY method, callee;

-- services.jar: confirm the wake method, including the void return.
SELECT d.entry_name, m.method_descriptor
FROM class_methods m JOIN dex_files d USING (file_id)
WHERE m.class_descriptor = 'Lcom/android/server/power/PowerManagerService;'
  AND m.method_name IN ('wakePowerGroupLocked', 'updateIsPoweredLocked')
ORDER BY m.method_descriptor;

SELECT DISTINCT method, callee FROM method_calls
WHERE method_class = 'Lcom/android/server/power/PowerManagerService;'
  AND method_name = 'updateIsPoweredLocked'
  AND callee_name = 'wakePowerGroupLocked';

-- services.jar: status-bar configuration cache and live Insets source geometry.
SELECT d.entry_name, m.method_descriptor
FROM class_methods m JOIN dex_files d USING (file_id)
WHERE (m.class_descriptor = 'Lcom/android/server/wm/DisplayPolicy;'
       AND m.method_name IN ('getStatusBarHeight', 'getStatusBarHeightForRotation'))
   OR (m.class_descriptor = 'Lcom/android/server/wm/DisplayContent;'
       AND m.method_name = 'calculateDisplayCutoutForRotation')
   OR (m.class_descriptor = 'Lcom/android/server/wm/utils/WmDisplayCutout;'
       AND m.method_name = 'getDisplayCutout')
ORDER BY m.method_descriptor;

SELECT DISTINCT method, callee FROM method_calls
WHERE callee_class = 'Lcom/android/server/wm/DisplayPolicy;'
  AND callee_name IN ('getStatusBarHeight', 'getStatusBarHeightForRotation')
ORDER BY method, callee;

-- MiuiSystemUI.apk: real status-bar window height uses the framework dimension.
SELECT DISTINCT method, callee FROM method_calls
WHERE method_class = 'Lcom/android/systemui/statusbar/window/StatusBarWindowController;'
  AND callee_class = 'Lcom/android/internal/policy/SystemBarUtils;'
  AND callee_name IN ('getStatusBarHeight', 'getStatusBarHeightForRotation')
ORDER BY method, callee;

-- MiuiSystemUI.apk: trace the native HeadsUp delay and accessibility conversion.
SELECT DISTINCT method, callee FROM method_calls
WHERE method_class IN (
  'Lcom/android/systemui/statusbar/policy/HeadsUpManager$HeadsUpEntry;',
  'Lcom/android/systemui/statusbar/phone/HeadsUpManagerPhone$HeadsUpEntryPhone;'
)
  AND callee_name IN ('calculateFinishTime', 'getRecommendedHeadsUpTimeoutMs',
                      'getMiuiFloatTime', 'getRecommendedTimeoutMillis')
ORDER BY method, callee;

SELECT d.entry_name, f.field_descriptor
FROM class_fields f JOIN dex_files d USING (file_id)
WHERE f.field_name IN ('mAutoDismissNotificationDecay', 'mHeadsUpNotificationDecay', 'mAccessibilityMgr',
                       'mMoreIcon', 'mForceHideMoreIcon', 'MAX_DOTS')
  AND f.class_descriptor LIKE 'Lcom/android/systemui/statusbar/%'
ORDER BY f.field_descriptor;

-- MiuiSystemUI.apk: distinguish status bar and notification shelf consumers.
SELECT DISTINCT method, callee FROM method_calls
WHERE callee_class = 'Lcom/android/systemui/statusbar/phone/NotificationIconContainer;'
  AND callee_name IN ('miuiShowNotificationIcons', 'hasMaxNumDot', 'setInNotificationIconShelf')
ORDER BY method, callee;

-- MiuiSystemUI.apk: verify initial and later setting delivery to the status-bar controller.
SELECT DISTINCT method, callee FROM method_calls
WHERE callee_class = 'Lcom/android/systemui/statusbar/policy/NotificationIconObserver$Callback;'
  AND callee_name = 'onNotificationIconChanged'
ORDER BY method, callee;

SELECT d.entry_name, f.field_descriptor
FROM class_fields f JOIN dex_files d USING (file_id)
WHERE (f.class_descriptor = 'Lcom/android/systemui/statusbar/phone/NotificationIconAreaController;'
       AND f.field_name = 'mNotificationIcons')
   OR (f.class_descriptor = 'Lcom/android/systemui/statusbar/phone/NotificationIconContainer;'
       AND f.field_name IN ('MAX_DOTS', 'MAX_STATIC_ICONS', 'MAX_ICONS_ON_LOCKSCREEN'))
ORDER BY f.field_descriptor;

-- MiuiSystemUI.apk: identify the exact peek decision and its full-screen caller.
SELECT DISTINCT method, callee FROM method_calls
WHERE method_class LIKE '%InterruptStateProviderImpl;'
  AND method_name IN ('shouldPeek', 'shouldHeadsUp', 'shouldLaunchFullScreenIntentWhenAdded')
  AND callee_name IN ('shouldPeek', 'shouldHeadsUp', 'shouldSend')
ORDER BY method, callee;

-- MiuiSystemUI.apk: confirm ringer state comes from the native policy update.
SELECT DISTINCT method, callee FROM method_calls
WHERE method_class LIKE '%PhoneStatusBarPolicy;'
  AND callee_name IN ('getRingerModeInternal', 'updateVolumeZen')
ORDER BY method, callee;

SELECT d.entry_name, f.field_descriptor
FROM class_fields f JOIN dex_files d USING (file_id)
WHERE f.class_descriptor = 'Lcom/android/systemui/statusbar/phone/PhoneStatusBarPolicy;'
  AND f.field_name = 'mMuteVisible';
