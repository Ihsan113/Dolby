# Shizuku UserService is reflectively loaded in the remote shell process.
-keep class id.danzku.clearsound.shizuku.ShellToolsService { *; }
-keep class id.danzku.clearsound.shizuku.IShellTools* { *; }
-keep class rikka.shizuku.** { *; }
