# Assinatura do release (RELEASING.md)

O build de **release** gerado pelo CI precisa de uma chave de assinatura
própria, privada, que **nunca** fica no repositório. Sem ela configurada,
o release cai de volta para a chave de debug (builds continuam saindo,
só não ficam assinadas com a chave "de verdade").

## Já existe uma chave gerada para você

Junto com esta entrega, você recebeu (fora do zip do projeto, em
arquivos separados):

- `feather-release.keystore` — a chave em si. **Guarde este arquivo em
  local seguro (ex.: gerenciador de senhas, ou um cofre) e nunca o
  suba a nenhum repositório**, nem mesmo privado.
- `feather-release.keystore.b64` — o mesmo arquivo, convertido para
  texto (base64), pronto para colar no GitHub.
- `credentials.txt` — as senhas geradas para essa chave.

Se preferir gerar a sua própria (por exemplo, se quiser controlar você
mesmo a validade ou os dados do certificado), o comando é:

```bash
keytool -genkeypair -v \
  -keystore feather-release.keystore \
  -alias feather-release \
  -keyalg RSA -keysize 2048 -validity 10950 \
  -dname "CN=Feather Launcher, OU=Personal, O=Feather Launcher, L=Unknown, ST=Unknown, C=BR"
```

## Configurar os secrets no GitHub

No repositório: **Settings → Secrets and variables → Actions → New
repository secret**. Crie estes 4 secrets:

| Nome do secret | Valor |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | Conteúdo do arquivo `feather-release.keystore.b64` (todo o texto, cole inteiro) |
| `RELEASE_STORE_PASSWORD` | O valor de `STORE_PASSWORD` em `credentials.txt` |
| `RELEASE_KEY_ALIAS` | `feather-release` |
| `RELEASE_KEY_PASSWORD` | O valor de `KEY_PASSWORD` em `credentials.txt` (é a mesma senha da store — keystores PKCS12 modernos exigem isso) |

Depois de configurar os 4 secrets, qualquer execução do workflow
(`.github/workflows/build.yml`) já vai gerar o `app-release` assinado
com essa chave de verdade, automaticamente.

## Atenção: isso muda a assinatura do release

Qualquer aparelho que já tenha instalado uma build de **release**
anterior a esta mudança (assinada com a chave de debug) vai precisar
**desinstalar** antes de instalar a nova — Android não permite
"atualizar" um app trocando de assinatura. Builds de **debug**
continuam usando a mesma chave de sempre (`app/keystore/debug.keystore`,
versionada), então essas não são afetadas.

## Se os secrets não estiverem configurados

O `assembleRelease` não falha — o `build.gradle.kts` detecta a ausência
das variáveis de ambiente e usa a chave de debug como fallback,
exatamente como acontecia antes desta mudança. Isso existe para que
forks do repositório, ou você mesmo antes de configurar os secrets,
continuem com um `assembleRelease` funcional.
