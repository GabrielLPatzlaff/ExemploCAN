# Exemplo CAN

Aplicação desktop em **Java Swing** para importar arquivos TXT, selecionar IDs,
filtrar mensagens CAN e exportar resultados em CSV.

## Recursos

- Identificação automática ou manual das colunas do TXT.
- IDs de até oito letras/números e dados HEX CAN clássico.
- Seleção de vários arquivos e cadastro de grupos de válvulas com seus IDs.
- Pesquisa de IDs, paginação e atalhos de teclado.
- Filtro opcional do primeiro byte HEX.
- Ocultação de valores HEX, com opção de recuperar os dados na visualização.
- Exportação CSV e personalização das cores da interface.

## Executar

1. Utilize **JDK 26** e **Apache NetBeans** com suporte a projetos Ant.
2. Abra esta pasta como projeto no NetBeans.
3. Execute a classe `Principal.Principal`.

A biblioteca SQLite JDBC está em `lib`. Não é necessário instalar MySQL.

## Armazenamento

O programa começa vazio e cria seu banco SQLite local automaticamente.
No Windows, os dados ficam em `%LOCALAPPDATA%\Exemplo CAN`.
O repositório contém somente o software, os recursos genéricos e as bibliotecas;
não inclui logs importados, bancos preenchidos ou credenciais.

## Instalador Windows

O script `Empacotar.ps1` gera um instalador com runtime Java incluído:

```powershell
.\Empacotar.ps1 -JdkHome $env:JAVA_HOME -AntHome $env:ANT_HOME
```

O instalador gerado fica em `release` e também começa sem dados de usuário.
