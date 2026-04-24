using System;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Threading;

[assembly: AssemblyTitle("Nuclr Commander")]
[assembly: AssemblyProduct("Nuclr Commander")]
[assembly: AssemblyDescription("Dual-pane file manager for developers")]
[assembly: AssemblyCompany("nuclr.dev")]
[assembly: AssemblyCopyright("Copyright \u00a9 2026 nuclr.dev")]
[assembly: AssemblyVersion("0.0.1.0")]
[assembly: AssemblyFileVersion("0.0.1.0")]

class Launcher {
    [STAThread]
    static void Main() {
        string dir = Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location);
        string log = Path.Combine(dir, "NuclrCommander.log");
        try {
            string javaw = Path.Combine(dir, "runtime", "bin", "javaw.exe");
            string[] jars = Directory.GetFiles(Path.Combine(dir, "app"), "*.jar");
            string jvmArgs = string.Join(" ",
                "-XX:CICompilerCount=4",
                "-Xms64m",
                "-XX:+UseZGC",
                "-XX:+ZGenerational",
                "-Dsun.java2d.d3d=false",
                "-Dsun.java2d.opengl=true",
                "-Dawt.useSystemAAFontSettings=on",
                "-Dswing.aatext=true",
                "-Dfile.encoding=UTF-8",
                "-XX:+UseStringDeduplication",
                "--add-opens java.desktop/sun.awt=ALL-UNNAMED",
                "--add-opens java.desktop/sun.swing=ALL-UNNAMED",
                "-XX:+ExitOnOutOfMemoryError",
                "-XX:+HeapDumpOnOutOfMemoryError",
                "-Dsun.java2d.uiScale.enabled=true",
                "-Dsun.java2d.ddscale=true",
                "-Dsun.java2d.dpiaware=true",
                "-enableassertions",
                "-splash:data/splash/splash.png"
            );
            var psi = new ProcessStartInfo(javaw) {
                Arguments = jvmArgs + " -jar \"" + jars[0] + "\"",
                WorkingDirectory = dir,
                UseShellExecute = false,
                RedirectStandardOutput = true,
                RedirectStandardError = true
            };
            var proc = Process.Start(psi);
            var t = new Thread(() => {
                using (var writer = new StreamWriter(log, append: false) { AutoFlush = true }) {
                    proc.OutputDataReceived += (s, e) => { if (e.Data != null) writer.WriteLine(e.Data); };
                    proc.ErrorDataReceived  += (s, e) => { if (e.Data != null) writer.WriteLine(e.Data); };
                    proc.BeginOutputReadLine();
                    proc.BeginErrorReadLine();
                    proc.WaitForExit();
                }
            });
            t.IsBackground = false;
            t.Start();
        } catch (Exception ex) {
            File.WriteAllText(log, ex.ToString());
        }
    }
}
