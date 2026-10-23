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
-- Input: same-ROM framework. Do not infer framework definitions from SystemUI refs.
SELECT file_id, descriptor FROM methods
WHERE access_flags IS NOT NULL
AND class_descriptor = 'Landroid/os/UserHandle;' AND name = 'getUserHandleForUid'
ORDER BY descriptor, file_id;
