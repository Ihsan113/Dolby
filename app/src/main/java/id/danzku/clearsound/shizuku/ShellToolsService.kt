package id.danzku.clearsound.shizuku

/** Runs only in Shizuku's user service (ADB shell identity), never as root by default. */
class ShellToolsService : IShellTools.Stub() {
    override fun whitelistBattery(packageName: String?): Boolean {
        // No user-controlled shell script strings; only a package ID is accepted.
        if (packageName == null || !Regex("[a-zA-Z][a-zA-Z0-9_.]{2,199}").matches(packageName)) return false
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("cmd", "deviceidle", "whitelist", "+$packageName"))
            val status = process.waitFor()
            process.destroy()
            status == 0
        } catch (_: Exception) {
            false
        }
    }
}
