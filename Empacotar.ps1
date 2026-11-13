param(
    [string]$JdkHome = $env:JAVA_HOME,
    [string]$AntHome = $env:ANT_HOME,
    [string]$RuntimeExistente
)
$ErrorActionPreference = 'Stop'
if (-not $JdkHome -or -not $AntHome) { throw 'Informe JdkHome e AntHome.' }
$raiz = $PSScriptRoot
$java = Join-Path $JdkHome 'bin\java.exe'
$javac = Join-Path $JdkHome 'bin\javac.exe'
$ant = Join-Path $AntHome 'lib\ant-launcher.jar'
if (-not (Test-Path -LiteralPath $java) -or -not (Test-Path -LiteralPath $ant)) { throw 'JDK ou Ant não encontrado.' }
$ferramentas = Join-Path $raiz 'build\ferramentas'
New-Item -ItemType Directory -Path $ferramentas -Force | Out-Null
& $javac -encoding UTF-8 -d $ferramentas (Join-Path $raiz 'instalador\GerarIcone.java')
if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar o gerador de ícone.' }
& $java -cp $ferramentas GerarIcone $raiz
if ($LASTEXITCODE -ne 0) { throw 'Falha ao gerar ícone.' }
& $java "-Dant.home=$AntHome" -cp $ant org.apache.tools.ant.launch.Launcher -quiet -f (Join-Path $raiz 'build.xml') jar
if ($LASTEXITCODE -ne 0) { throw 'Falha na compilação Java.' }
$trabalho = Join-Path $raiz ('build\pacote-' + [Guid]::NewGuid().ToString('N'))
$pacote = Join-Path $trabalho 'pacote'
New-Item -ItemType Directory -Path (Join-Path $pacote 'app') -Force | Out-Null
if ($RuntimeExistente) {
    Copy-Item -LiteralPath $RuntimeExistente -Destination (Join-Path $pacote 'runtime') -Recurse
} else {
    & (Join-Path $JdkHome 'bin\jlink.exe') --add-modules java.desktop,java.sql,java.logging,jdk.unsupported --strip-debug --no-header-files --no-man-pages --output (Join-Path $pacote 'runtime')
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao gerar runtime Java.' }
}
Copy-Item -LiteralPath (Join-Path $raiz 'dist\ExemploCAN.jar') -Destination (Join-Path $pacote 'app')
Copy-Item -LiteralPath (Join-Path $raiz 'dist\lib') -Destination (Join-Path $pacote 'app') -Recurse
$csc = Join-Path $env:SystemRoot 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
if (-not (Test-Path -LiteralPath $csc)) { throw 'Compilador .NET Framework de 64 bits não encontrado.' }
$icone = Join-Path $raiz 'can-exemplo.ico'
& $csc /nologo /target:winexe /platform:x64 /codepage:65001 /reference:System.Windows.Forms.dll ('/win32icon:' + $icone) ('/out:' + (Join-Path $pacote 'Exemplo CAN.exe')) (Join-Path $raiz 'instalador\Iniciar.cs')
if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar iniciador.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = Join-Path $trabalho 'pacote.zip'
[IO.Compression.ZipFile]::CreateFromDirectory($pacote,$zip,[IO.Compression.CompressionLevel]::Fastest,$false)
$release = Join-Path $raiz 'release'
New-Item -ItemType Directory -Path $release -Force | Out-Null
$exe = Join-Path $release 'Instalar Exemplo CAN-1.0.0.exe'
& $csc /nologo /target:winexe /platform:x64 /codepage:65001 /reference:System.Windows.Forms.dll /reference:System.IO.Compression.dll /reference:System.IO.Compression.FileSystem.dll /reference:Microsoft.CSharp.dll ('/win32icon:' + $icone) ('/resource:' + $zip + ',ExemploCAN.Pacote') ('/out:' + $exe) (Join-Path $raiz 'instalador\Instalar.cs')
if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar instalador.' }
Write-Output $exe
