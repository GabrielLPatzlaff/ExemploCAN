using System;
using System.Diagnostics;
using System.IO;
using System.Windows.Forms;

internal static class Iniciar
{
    [STAThread]
    private static int Main(string[] args)
    {
        string pasta = AppDomain.CurrentDomain.BaseDirectory;
        bool verificar = args.Length == 1 && args[0] == "--verificar-runtime";
        try
        {
            var inicio = new ProcessStartInfo
            {
                FileName = Path.Combine(pasta, "runtime", "bin", verificar ? "java.exe" : "javaw.exe"),
                Arguments = verificar ? "-version" :
                    "--enable-native-access=ALL-UNNAMED -Xmx512m -jar \"" + Path.Combine(pasta, "app", "ExemploCAN.jar") + "\"",
                WorkingDirectory = pasta,
                UseShellExecute = false,
                CreateNoWindow = true
            };
            using (Process processo = Process.Start(inicio))
            {
                if (!verificar) return 0;
                processo.WaitForExit();
                return processo.ExitCode;
            }
        }
        catch (Exception erro)
        {
            if (!verificar) MessageBox.Show(erro.Message, "Exemplo CAN", MessageBoxButtons.OK, MessageBoxIcon.Error);
            return 1;
        }
    }
}
