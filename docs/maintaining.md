# 维护说明

本文面向维护者及需要自行配置签名发布的开发者。安装与使用请参阅 [README](../README.md)。

## 自动打包与发布

每次推送到 `main`，GitHub Actions 会自动运行单元测试与 lint，构建并验证 release 签名，然后把 APK 和 `SHA256SUMS` 发布到 [Releases](https://github.com/xiaozixiaobei/hengping/releases) 的公开测试版本。每个版本包含构建编号与源码提交；构建记录和 30 天内的附件也可在 [Actions](https://github.com/xiaozixiaobei/hengping/actions/workflows/android.yml) 查看。可通过 `Run workflow` 手动打包 `main`。

自动构建的 `versionCode` 为 `1000 + github.run_number`，保持递增。更改应用版本时修改 `app/build.gradle.kts` 中的 `versionName`。本机默认 `versionCode` 为 4；如需覆盖已安装的云端版本，构建时通过 `-PversionCode=<更大的整数>` 指定。

仓库管理员需配置两个 Actions Secrets：

| Secret | 内容 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | PKCS12 签名密钥文件的 Base64 编码，密钥别名为 `hengping` |
| `ANDROID_KEYSTORE_PASSWORD` | 密钥库密码与私钥密码，二者相同 |

本机签名构建使用 `ANDROID_KEYSTORE_PATH`（密钥文件绝对路径）和 `ANDROID_KEYSTORE_PASSWORD` 环境变量，然后运行 `./gradlew assembleRelease`。未配置签名时，本机 release 构建仅输出未签名 APK。

正式签名证书的 SHA-256 指纹：

```text
1D:CA:44:74:B2:76:CE:D7:90:FE:50:5C:D7:9B:AB:62:EE:0D:74:6C:33:BF:30:67:0A:12:92:91:B1:B6:3F:18
```

签名密钥和密码不随源码发布。请单独备份并长期保留同一套密钥，以支持已安装应用的后续更新。
