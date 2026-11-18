# Privacy · CustoMIUIzer A13

- **Settings and app selection.** The module stores settings on the device. The settings app reads installed apps for pickers and selected features. When connected to the Xposed framework, it synchronizes module settings through libxposed so the scoped host processes can read their options.
- **Backup and restore.** Exports are settings files written to the location you select in Android's document picker. They can contain package names and custom values. The format does not encrypt the contents. If you select a cloud document provider, that provider handles the file under its own terms.
- **Diagnostics.** The current A13 source does not include the original CustoMIUIzer crash report uploader. The module can write diagnostics to framework or device logs. Review and redact logs before attaching them to a public issue.
- **External services.** Release, support, and other external links are handled by the app or service you open. Their own privacy terms apply.

Report a privacy concern through the [A13 repository](https://github.com/thetvplus/customiuizer-a13). Avoid posting private logs or settings exports publicly.
