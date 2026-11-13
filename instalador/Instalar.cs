using System;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Windows.Forms;

internal static class Instalar
{
    private const string Titulo = "Instalador Exemplo CAN";
    private const string Executavel = "Exemplo CAN.exe";

    [STAThread]
    private static int Main(string[] args)
    {
        bool teste = args.Length == 2 && args[0] == "--extrair-para";
        try
        {
            if (!Environment.Is64BitOperatingSystem)
                throw new InvalidOperationException("Esta edição requer Windows de 64 bits.");
            if (teste)
            {
                Extrair(Path.GetFullPath(args[1]));
                return 0;
            }

            if (MessageBox.Show("Instalar o Exemplo CAN neste computador?", Titulo,
                    MessageBoxButtons.OKCancel, MessageBoxIcon.Question) != DialogResult.OK)
                return 0;

            string pasta = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "Programs", "Exemplo CAN", "1.0.0");
            if (!File.Exists(Path.Combine(pasta, Executavel)))
                Extrair(pasta);
            string aplicativo = Path.Combine(pasta, Executavel);
            CriarAtalho(aplicativo, pasta);
            if (MessageBox.Show("Exemplo CAN instalado. Abrir o programa agora?", Titulo,
                    MessageBoxButtons.YesNo, MessageBoxIcon.Information) == DialogResult.Yes)
                Process.Start(aplicativo);
            return 0;
        }
        catch (Exception erro)
        {
            if (!teste) MessageBox.Show(erro.Message, Titulo, MessageBoxButtons.OK, MessageBoxIcon.Error);
            return 1;
        }
    }

    private static void Extrair(string destino)
    {
        string temporaria = destino + ".instalando-" + Guid.NewGuid().ToString("N");
        Directory.CreateDirectory(temporaria);
        string raiz = temporaria + Path.DirectorySeparatorChar;
        using (Stream pacote = Assembly.GetExecutingAssembly().GetManifestResourceStream("ExemploCAN.Pacote"))
        using (var zip = new ZipArchive(pacote, ZipArchiveMode.Read))
        {
            foreach (ZipArchiveEntry entrada in zip.Entries)
            {
                string arquivo = Path.GetFullPath(Path.Combine(temporaria, entrada.FullName));
                if (!arquivo.StartsWith(raiz, StringComparison.OrdinalIgnoreCase))
                    throw new InvalidDataException("Caminho inválido no pacote.");
                if (entrada.Name.Length == 0)
                {
                    Directory.CreateDirectory(arquivo);
                    continue;
                }
                Directory.CreateDirectory(Path.GetDirectoryName(arquivo));
                using (Stream origem = entrada.Open())
                using (var saida = new FileStream(arquivo, FileMode.CreateNew, FileAccess.Write))
                    origem.CopyTo(saida);
            }
        }
        Directory.Move(temporaria, destino);
    }

    private static void CriarAtalho(string aplicativo, string pasta)
    {
        string menu = Environment.GetFolderPath(Environment.SpecialFolder.Programs);
        if (String.IsNullOrWhiteSpace(menu))
            throw new IOException("Não foi possível localizar o menu Iniciar do usuário.");
        Directory.CreateDirectory(menu);
        dynamic shell = Activator.CreateInstance(Type.GetTypeFromProgID("WScript.Shell", true));
        dynamic atalho = shell.CreateShortcut(Path.Combine(menu, "Exemplo CAN.lnk"));
        atalho.TargetPath = aplicativo;
        atalho.WorkingDirectory = pasta;
        atalho.IconLocation = aplicativo + ",0";
        atalho.Save();
    }
}

