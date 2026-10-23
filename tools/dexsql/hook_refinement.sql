-- Read-only, on-demand DEXSQL queries. No runtime or CI dependency.
-- Input: services.jar for each A13 ROM. Keep dex_files to distinguish ROMs.
SELECT file_id, source_path, entry_name, class_count
FROM dex_files ORDER BY file_id;

SELECT file_id, descriptor FROM methods
WHERE access_flags IS NOT NULL AND (
    (class_descriptor = 'Lcom/android/server/TelephonyRegistry$Record;'
     AND name = 'matchTelephonyCallbackEvent')
    OR (class_descriptor = 'Lcom/android/server/TelephonyRegistry;'
        AND name IN ('notifyCallState', 'notifyCallStateForPhoneId', 'notifyCallStateForAllSubs'))
    OR class_descriptor = 'Lcom/android/server/VibratorService;'
    OR name = 'doVibratorOn')
ORDER BY descriptor, file_id;

-- H03: same-ROM services.jar screen-off animation bundle. initialize() is obsolete.
SELECT file_id, descriptor, access_flags FROM methods
WHERE access_flags IS NOT NULL
AND class_descriptor = 'Lcom/android/server/display/DisplayPowerController;'
AND name = 'initialize'
ORDER BY descriptor, file_id;

SELECT file_id, field_name, field_descriptor FROM class_fields
WHERE class_descriptor = 'Lcom/android/server/display/DisplayPowerController;'
AND field_name IN ('mColorFadeEnabled', 'mColorFadeFadesConfig', 'mColorFadeOffAnimator')
ORDER BY field_name, file_id;

-- Decode initialize(I)V separately for each file_id: flags must be set before
-- native initialization creates the animator; duration is changed afterward.

SELECT file_id, class_descriptor, field_name, field_descriptor FROM class_fields
WHERE class_descriptor = 'Lcom/android/server/TelephonyRegistry$Record;'
AND field_name IN ('events', 'eventList', 'callingPackage')
ORDER BY field_name, file_id;

SELECT file_id, method, callee FROM method_calls
WHERE method_class = 'Lcom/android/server/TelephonyRegistry;'
AND method_name IN ('notifyCallState', 'notifyCallStateForPhoneId', 'notifyCallStateForAllSubs')
AND callee_class = 'Lcom/android/server/TelephonyRegistry$Record;'
AND callee_name = 'matchTelephonyCallbackEvent'
ORDER BY method, file_id, callee;

-- Input: intact SystemUI from the SAME ROM. Check definitions, not references.
SELECT file_id, descriptor FROM methods
WHERE access_flags IS NOT NULL AND (
    (class_descriptor = 'Lcom/android/systemui/statusbar/notification/row/MiuiNotificationMenuRow;'
     AND name IN ('onClickInfoItem', 'createMenuViews'))
    OR (class_descriptor LIKE '%NotificationUtil;' AND name = 'isHybrid'))
ORDER BY descriptor, file_id;

-- Use get_manifest_components on Settings for CHANNEL_NOTIFICATION_SETTINGS.

-- H04/H05: input = intact services.jar, one or both A13 ROMs.
SELECT file_id, descriptor, access_flags FROM methods
WHERE access_flags IS NOT NULL AND (
    (class_descriptor = 'Lcom/android/server/notification/NotificationManagerService;'
     AND name IN ('showNextToastLocked', 'scheduleDurationReachedLocked'))
    OR (class_descriptor = 'Lcom/android/server/wm/DisplayPolicy;'
        AND name = 'adjustWindowParamsLw')
    OR (class_descriptor = 'Lcom/android/server/vibrator/VibratorManagerService;'
        AND name IN ('systemReady', 'vibrate')))
ORDER BY descriptor, file_id;

SELECT file_id, class_descriptor, field_name, field_descriptor FROM class_fields
WHERE class_descriptor IN ('Lcom/android/server/notification/NotificationManagerService;',
                          'Lcom/android/server/wm/DisplayPolicy;')
AND field_name = 'mAccessibilityManager'
ORDER BY class_descriptor, file_id;

-- Decode scheduleDurationReachedLocked(ToastRecord,Z)V separately for both ROMs.
-- Ordering: native base 2000/3500 -> accessibility(flags=2) -> +250/+333 -> schedule once.
-- Decode adjustWindowParamsLw(WindowState,LayoutParams)V: TYPE_TOAST=2005;
-- native window cap=4100 -> accessibility(flags=2) -> remaining native policy.

-- Input: same-ROM framework. Do not infer framework definitions from SystemUI refs.
SELECT file_id, descriptor FROM methods
WHERE access_flags IS NOT NULL
AND class_descriptor = 'Landroid/os/UserHandle;' AND name = 'getUserHandleForUid'
ORDER BY descriptor, file_id;

-- H04 framework: xaga full ZIP CRC passes. Corrupt veux framework is excluded.
SELECT file_id, descriptor, access_flags FROM methods
WHERE access_flags IS NOT NULL
AND class_descriptor = 'Landroid/view/accessibility/AccessibilityManager;'
AND name = 'getRecommendedTimeoutMillis'
ORDER BY descriptor, file_id;

SELECT file_id, field_name, field_descriptor FROM class_fields
WHERE class_descriptor = 'Landroid/view/WindowManager$LayoutParams;'
AND field_name = 'hideTimeoutMilliseconds'
ORDER BY field_name, file_id;
-- Decode getRecommendedTimeoutMillis(II)I: cached minimum, no Binder call in this method.

-- H06/H07: input = intact A13 services.jar, definitions only.
SELECT file_id, descriptor, access_flags FROM methods
WHERE access_flags IS NOT NULL AND (
    (class_descriptor = 'Lcom/android/server/audio/FocusRequester;'
     AND name IN ('handleFocusLoss', 'frameworkHandleFocusLoss', 'handleFocusGain'))
    OR (class_descriptor = 'Lcom/android/server/audio/AudioService$VolumeController;'
        AND name = 'suppressAdjustment'))
ORDER BY class_descriptor, name, file_id;

SELECT file_id, method, callee FROM method_calls
WHERE method_class = 'Lcom/android/server/audio/FocusRequester;'
AND callee_class = 'Lcom/android/server/audio/FocusRequester;'
AND callee_name = 'frameworkHandleFocusLoss'
ORDER BY file_id, method;

SELECT file_id, field_name, field_descriptor FROM class_fields
WHERE class_descriptor = 'Lcom/android/server/audio/AudioService$VolumeController;'
AND field_name = 'mController'
ORDER BY file_id;

-- Decode both focus methods separately for both ROMs. The outer method updates
-- focus state and notifies policy; only the inner CAN_DUCK branch is suppressed.
-- Loss=-1 native fade delays differ (xaga 1000ms / veux 2000ms); leave them native.
-- Decode suppressAdjustment(IIZ)Z: keep native AudioSystem/long-press state and
-- exceptions, then change only a successful eligible media-stream result.

-- Candidate check: this member is in SAME-ROM miui-services.jar, not services.jar.
-- Input = intact MIUI extension archives. Absence in services.jar is not retirement proof.
SELECT file_id, descriptor, access_flags FROM methods
WHERE access_flags IS NOT NULL
AND class_descriptor = 'Lcom/android/server/wm/WindowProcessUtils;'
AND name = 'getPerceptibleRecentAppList'
ORDER BY file_id, descriptor;
